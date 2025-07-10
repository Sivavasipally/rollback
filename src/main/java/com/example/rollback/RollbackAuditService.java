package com.example.rollback;

import com.example.rollback.properties.FlywayRollbackProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive audit service for tracking all rollback operations
 * Provides detailed logging, database tracking, and external notification
 */
@Service
public class RollbackAuditService {
    
    private static final Logger log = LoggerFactory.getLogger(RollbackAuditService.class);
    
    private final DataSource dataSource;
    private final FlywayRollbackProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    public RollbackAuditService(DataSource dataSource, FlywayRollbackProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Records a successful rollback operation
     */
    public void recordSuccessfulRollback(String rollbackId, RollbackRequest request, String snapshotId) {
        log.info("Recording successful rollback: {}", rollbackId);
        
        try {
            // Ensure audit table exists
            ensureAuditTableExists();
            
            // Record in database
            jdbcTemplate.update(
                "INSERT INTO " + properties.getAudit().getTableName() + 
                " (rollback_id, version, status, reason, approved_by, ticket_number, " +
                "rollback_type, snapshot_id, performed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rollbackId, 
                request.getTargetVersion(), 
                "SUCCESS",
                request.getReason(),
                request.getApprovedBy(),
                request.getTicketNumber(),
                request.getRollbackType(),
                snapshotId,
                LocalDateTime.now()
            );
            
            // Send notifications if configured
            sendNotifications(rollbackId, request, "SUCCESS", null);
            
        } catch (Exception e) {
            log.error("Failed to record successful rollback", e);
        }
    }
    
    /**
     * Records a failed rollback operation
     */
    public void recordFailedRollback(String rollbackId, RollbackRequest request, Exception exception) {
        log.info("Recording failed rollback: {}", rollbackId);
        
        try {
            // Ensure audit table exists
            ensureAuditTableExists();
            
            // Record in database
            jdbcTemplate.update(
                "INSERT INTO " + properties.getAudit().getTableName() + 
                " (rollback_id, version, status, reason, approved_by, ticket_number, " +
                "rollback_type, error_message, performed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rollbackId, 
                request.getTargetVersion(), 
                "FAILED",
                request.getReason(),
                request.getApprovedBy(),
                request.getTicketNumber(),
                request.getRollbackType(),
                exception.getMessage(),
                LocalDateTime.now()
            );
            
            // Send notifications if configured
            sendNotifications(rollbackId, request, "FAILED", exception.getMessage());
            
        } catch (Exception e) {
            log.error("Failed to record failed rollback", e);
        }
    }
    
    /**
     * Records a dry run
     */
    public void recordDryRun(String rollbackId, RollbackRequest request, List<String> steps) {
        log.info("Recording dry run: {}", rollbackId);
        
        try {
            // Ensure audit table exists
            ensureAuditTableExists();
            
            // Convert steps to JSON
            String stepsJson = objectMapper.writeValueAsString(steps);
            
            // Record in database
            jdbcTemplate.update(
                "INSERT INTO " + properties.getAudit().getTableName() + 
                " (rollback_id, version, status, reason, approved_by, ticket_number, " +
                "rollback_type, details, performed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rollbackId, 
                request.getTargetVersion(), 
                "DRY_RUN",
                request.getReason(),
                request.getApprovedBy(),
                request.getTicketNumber(),
                request.getRollbackType(),
                stepsJson,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Failed to record dry run", e);
        }
    }
    
    /**
     * Ensures the audit table exists
     */
    private void ensureAuditTableExists() {
        try {
            if (!tableExists(properties.getAudit().getTableName())) {
                log.info("Creating audit table: {}", properties.getAudit().getTableName());
                
                // Create audit table
                jdbcTemplate.execute(
                    "CREATE TABLE " + properties.getAudit().getTableName() + " (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "rollback_id VARCHAR(50) NOT NULL, " +
                    "version VARCHAR(50) NOT NULL, " +
                    "status VARCHAR(20) NOT NULL, " +
                    "reason TEXT, " +
                    "approved_by VARCHAR(100), " +
                    "ticket_number VARCHAR(50), " +
                    "rollback_type VARCHAR(20), " +
                    "snapshot_id VARCHAR(100), " +
                    "error_message TEXT, " +
                    "details TEXT, " +
                    "performed_at TIMESTAMP NOT NULL" +
                    ")"
                );
            }
        } catch (Exception e) {
            log.error("Failed to create audit table", e);
        }
    }
    
    /**
     * Checks if a table exists
     */
    private boolean tableExists(String tableName) {
        try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Sends notifications about rollback operations
     */
    private void sendNotifications(String rollbackId, RollbackRequest request, 
                                  String status, String errorMessage) {
        // Implementation would send emails, Slack messages, etc.
        log.info("Sending notifications for rollback: {}", rollbackId);
        
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("rollbackId", rollbackId);
        notificationData.put("targetVersion", request.getTargetVersion());
        notificationData.put("status", status);
        notificationData.put("reason", request.getReason());
        notificationData.put("approvedBy", request.getApprovedBy());
        notificationData.put("ticketNumber", request.getTicketNumber());
        notificationData.put("timestamp", LocalDateTime.now().toString());
        
        if (errorMessage != null) {
            notificationData.put("errorMessage", errorMessage);
        }
        
        // Log notification data for now
        try {
            log.info("Notification data: {}", objectMapper.writeValueAsString(notificationData));
        } catch (Exception e) {
            log.error("Failed to serialize notification data", e);
        }
    }
}
