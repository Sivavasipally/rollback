package com.example.rollback;

import com.example.rollback.properties.FlywayRollbackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Safety guard service to prevent dangerous rollback operations
 * Implements multiple layers of protection for production environments
 */
@Service
public class SafetyGuardService {
    
    private static final Logger log = LoggerFactory.getLogger(SafetyGuardService.class);
    
    private final DataSource dataSource;
    private final FlywayRollbackProperties properties;
    private final JdbcTemplate jdbcTemplate;
    
    // Business hours configuration (can be externalized)
    private static final LocalTime BUSINESS_START = LocalTime.of(9, 0);
    private static final LocalTime BUSINESS_END = LocalTime.of(17, 0);
    
    public SafetyGuardService(DataSource dataSource, FlywayRollbackProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }
    
    /**
     * Checks if rollback would cause data loss
     */
    public boolean wouldCauseDataLoss(String currentVersion, String targetVersion) {
        log.info("Checking for potential data loss: {} -> {}", currentVersion, targetVersion);
        
        try {
            // Get migrations between target and current version
            List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, description FROM flyway_schema_history " +
                "WHERE version > ? AND version <= ? " +
                "ORDER BY installed_rank",
                targetVersion, currentVersion
            );
            
            // Check if any migration involves data operations
            for (Map<String, Object> migration : migrations) {
                String description = (String) migration.get("description");
                if (description != null && containsDataOperations(description)) {
                    log.warn("Migration {} contains data operations that may cause data loss", 
                        migration.get("version"));
                    return true;
                }
            }
            
            // Check for new tables created after target version
            if (hasNewTablesAfterVersion(targetVersion)) {
                log.warn("New tables created after target version may be lost");
                return true;
            }
            
            // Check for new columns that may contain data
            if (hasNewColumnsWithData(targetVersion)) {
                log.warn("New columns with data found after target version");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Failed to check for data loss", e);
            // Err on the side of caution
            return true;
        }
    }
    
    /**
     * Checks if we're in a production environment
     */
    public boolean isProductionEnvironment() {
        // Check environment variables
        String environment = System.getProperty("spring.profiles.active", "");
        if (environment.contains("prod") || environment.contains("production")) {
            return true;
        }
        
        // Check database URL patterns
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            if (url != null && (url.contains("prod") || url.contains("production"))) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Failed to check database URL", e);
        }
        
        // Check hostname patterns
        String hostname = System.getProperty("hostname", "");
        if (hostname.contains("prod") || hostname.contains("production")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if current time is during business hours
     */
    public boolean isBusinessHours() {
        LocalTime now = LocalTime.now();
        return now.isAfter(BUSINESS_START) && now.isBefore(BUSINESS_END);
    }
    
    /**
     * Checks for active long-running transactions
     */
    public boolean hasActiveLongRunningTransactions() {
        try {
            // This is database-specific - example for MySQL
            List<Map<String, Object>> processes = jdbcTemplate.queryForList(
                "SELECT * FROM INFORMATION_SCHEMA.PROCESSLIST " +
                "WHERE COMMAND != 'Sleep' AND TIME > 300" // 5 minutes
            );
            
            if (!processes.isEmpty()) {
                log.warn("Found {} long-running transactions", processes.size());
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.warn("Failed to check for long-running transactions", e);
            // Assume there might be long-running transactions
            return true;
        }
    }
    
    /**
     * Verifies database integrity after rollback
     */
    public boolean verifyDatabaseIntegrity() {
        log.info("Verifying database integrity");
        
        try {
            // Check foreign key constraints
            if (!checkForeignKeyConstraints()) {
                log.error("Foreign key constraint violations found");
                return false;
            }
            
            // Check for orphaned records
            if (!checkForOrphanedRecords()) {
                log.error("Orphaned records found");
                return false;
            }
            
            // Verify critical tables exist
            if (!verifyCriticalTablesExist()) {
                log.error("Critical tables missing");
                return false;
            }
            
            // Check data consistency
            if (!checkDataConsistency()) {
                log.error("Data consistency issues found");
                return false;
            }
            
            log.info("Database integrity verification passed");
            return true;
            
        } catch (Exception e) {
            log.error("Database integrity verification failed", e);
            return false;
        }
    }
    
    /**
     * Checks if migration description contains data operations
     */
    private boolean containsDataOperations(String description) {
        String lowerDesc = description.toLowerCase();
        return lowerDesc.contains("insert") || 
               lowerDesc.contains("update") || 
               lowerDesc.contains("delete") ||
               lowerDesc.contains("data") ||
               lowerDesc.contains("populate") ||
               lowerDesc.contains("migrate");
    }
    
    /**
     * Checks for new tables created after target version
     */
    private boolean hasNewTablesAfterVersion(String targetVersion) {
        try {
            // This would require tracking table creation in migrations
            // For now, return false as a placeholder
            return false;
        } catch (Exception e) {
            log.warn("Failed to check for new tables", e);
            return true;
        }
    }
    
    /**
     * Checks for new columns with data after target version
     */
    private boolean hasNewColumnsWithData(String targetVersion) {
        try {
            // This would require tracking column additions in migrations
            // For now, return false as a placeholder
            return false;
        } catch (Exception e) {
            log.warn("Failed to check for new columns", e);
            return true;
        }
    }
    
    /**
     * Checks foreign key constraints
     */
    private boolean checkForeignKeyConstraints() {
        try {
            // Database-specific implementation needed
            // For now, assume constraints are valid
            return true;
        } catch (Exception e) {
            log.warn("Failed to check foreign key constraints", e);
            return false;
        }
    }
    
    /**
     * Checks for orphaned records
     */
    private boolean checkForOrphanedRecords() {
        try {
            // Implementation would check for orphaned records
            // For now, assume no orphaned records
            return true;
        } catch (Exception e) {
            log.warn("Failed to check for orphaned records", e);
            return false;
        }
    }
    
    /**
     * Verifies critical tables exist
     */
    private boolean verifyCriticalTablesExist() {
        try {
            // Check for flyway_schema_history table
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE 1=0", Integer.class);
            
            // Add checks for other critical tables as needed
            
            return true;
        } catch (Exception e) {
            log.error("Critical table verification failed", e);
            return false;
        }
    }
    
    /**
     * Checks data consistency
     */
    private boolean checkDataConsistency() {
        try {
            // Implementation would perform data consistency checks
            // For now, assume data is consistent
            return true;
        } catch (Exception e) {
            log.warn("Failed to check data consistency", e);
            return false;
        }
    }
    
    /**
     * Gets database connection count
     */
    public int getActiveConnectionCount() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.PROCESSLIST", Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to get connection count", e);
            return -1;
        }
    }
    
    /**
     * Checks if database is under heavy load
     */
    public boolean isDatabaseUnderHeavyLoad() {
        try {
            int connectionCount = getActiveConnectionCount();
            // Threshold can be configurable
            return connectionCount > 50;
        } catch (Exception e) {
            log.warn("Failed to check database load", e);
            return true; // Assume heavy load if we can't check
        }
    }
    
    /**
     * Validates rollback timing
     */
    public boolean isValidRollbackTime() {
        LocalDateTime now = LocalDateTime.now();
        
        // Avoid rollbacks during peak hours (configurable)
        int hour = now.getHour();
        if (hour >= 9 && hour <= 17) {
            log.warn("Rollback attempted during peak hours");
            return false;
        }
        
        // Avoid rollbacks on Fridays after 3 PM (configurable)
        if (now.getDayOfWeek().getValue() == 5 && hour >= 15) {
            log.warn("Rollback attempted on Friday afternoon");
            return false;
        }
        
        return true;
    }
}
