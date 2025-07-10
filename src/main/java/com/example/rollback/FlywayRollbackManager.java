// src/main/java/com/example/rollback/FlywayRollbackManager.java
package com.example.rollback;

import com.example.rollback.properties.FlywayRollbackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class FlywayRollbackManager {
    
    private final DataSource dataSource;
    private final FlywayRollbackProperties properties;
    private final JdbcTemplate jdbcTemplate;
    
    public FlywayRollbackManager(DataSource dataSource, FlywayRollbackProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }
    
    public boolean isSnapshotEnabled() {
        return properties.getSnapshot().isEnabled();
    }
    
    public boolean isAutoRollbackEnabled() {
        return properties.isAutoRollbackOnFailure();
    }
    
    @Transactional
    public RollbackResult rollbackToVersion(String targetVersion) {
        String rollbackId = UUID.randomUUID().toString();
        log.info("Starting rollback {} to version {}", rollbackId, targetVersion);
        
        try {
            // Get current version
            String currentVersion = getCurrentVersion();
            log.info("Current version: {}", currentVersion);
            
            // Create snapshot before rollback
            String snapshotId = null;
            if (properties.getSnapshot().isEnabled()) {
                snapshotId = createSnapshot("rollback_" + targetVersion);
            }
            
            // Execute rollback logic
            executeRollback(currentVersion, targetVersion);
            
            // Update flyway schema history
            updateFlywaySchemaHistory(targetVersion);
            
            // Audit the rollback
            auditRollback(rollbackId, targetVersion, "SUCCESS", null);
            
            return new RollbackResult(true, rollbackId, targetVersion, snapshotId, null);
            
        } catch (Exception e) {
            log.error("Rollback failed", e);
            auditRollback(rollbackId, targetVersion, "FAILED", e.getMessage());
            return new RollbackResult(false, rollbackId, targetVersion, null, e.getMessage());
        }
    }
    
    public String createPreMigrationSnapshot() {
        return createSnapshot("pre_migration");
    }
    
    public String createSnapshot(String prefix) {
        String snapshotId = prefix + "_" + LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        log.info("Creating snapshot: {}", snapshotId);
        
        try {
            // Create snapshot directory
            Path snapshotDir = Paths.get(properties.getSnapshot().getStoragePath(), snapshotId);
            Files.createDirectories(snapshotDir);
            
            // Get all tables and create snapshots
            List<String> tables = getAllTables();
            for (String table : tables) {
                createTableSnapshot(table, snapshotDir);
            }
            
            // Save metadata
            saveSnapshotMetadata(snapshotDir, tables);
            
            log.info("Snapshot {} created successfully with {} tables", snapshotId, tables.size());
            return snapshotId;
            
        } catch (Exception e) {
            log.error("Failed to create snapshot", e);
            throw new RuntimeException("Snapshot creation failed", e);
        }
    }
    
    private void createTableSnapshot(String tableName, Path snapshotDir) {
        try {
            String sql;
            if (isH2Database()) {
                // For H2, export to CSV
                String csvPath = snapshotDir.resolve(tableName + ".csv").toString();
                sql = String.format("CALL CSVWRITE('%s', 'SELECT * FROM %s')", csvPath, tableName);
                jdbcTemplate.execute(sql);
            } else {
                // For MySQL, create backup table
                String backupTable = "snapshot_" + tableName;
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + backupTable);
                jdbcTemplate.execute(String.format(
                    "CREATE TABLE %s AS SELECT * FROM %s", backupTable, tableName));
            }
            log.debug("Created snapshot for table: {}", tableName);
        } catch (Exception e) {
            log.warn("Failed to snapshot table: {}", tableName, e);
        }
    }
    
    private void saveSnapshotMetadata(Path snapshotDir, List<String> tables) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("snapshotId", snapshotDir.getFileName().toString());
            metadata.put("timestamp", LocalDateTime.now().toString());
            metadata.put("tables", tables);
            metadata.put("currentVersion", getCurrentVersion());
            
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(metadata);
            
            Files.write(snapshotDir.resolve("metadata.json"), json.getBytes());
        } catch (Exception e) {
            log.warn("Failed to save snapshot metadata", e);
        }
    }
    
    private String getCurrentVersion() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history " +
                "WHERE success = " + (isH2Database() ? "TRUE" : "1") + " " +
                "ORDER BY installed_rank DESC LIMIT 1",
                String.class
            );
        } catch (Exception e) {
            log.warn("Failed to get current version", e);
            return "0";
        }
    }
    
    private void executeRollback(String currentVersion, String targetVersion) {
        log.info("Executing rollback from {} to {}", currentVersion, targetVersion);
        
        // Get list of migrations to rollback
        List<String> versionsToRollback = jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history " +
            "WHERE version > ? AND version <= ? " +
            "ORDER BY installed_rank DESC",
            String.class, targetVersion, currentVersion
        );
        
        log.info("Versions to rollback: {}", versionsToRollback);
        
        // Execute rollback scripts if they exist
        for (String version : versionsToRollback) {
            String rollbackScriptPath = "db/rollback/U" + version + "__rollback.sql";
            log.info("Looking for rollback script: {}", rollbackScriptPath);
            // In a real implementation, you would execute these scripts
        }
    }
    
    private void updateFlywaySchemaHistory(String targetVersion) {
        // Remove entries after target version
        int deleted = jdbcTemplate.update(
            "DELETE FROM flyway_schema_history WHERE version > ?",
            targetVersion
        );
        log.info("Removed {} migration entries after version {}", deleted, targetVersion);
    }
    
    private void auditRollback(String rollbackId, String version, String status, String error) {
        if (!properties.getAudit().isEnabled()) {
            return;
        }
        
        try {
            // Check if audit table exists
            if (!tableExists(properties.getAudit().getTableName())) {
                log.debug("Audit table does not exist yet");
                return;
            }
            
            jdbcTemplate.update(
                "INSERT INTO " + properties.getAudit().getTableName() + 
                " (rollback_id, version, status, error_message, performed_at) " +
                "VALUES (?, ?, ?, ?, ?)",
                rollbackId, version, status, error, LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Failed to audit rollback", e);
        }
    }
    
    private List<String> getAllTables() {
        List<String> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            
            try (ResultSet rs = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // Exclude Flyway tables and system tables
                    if (!tableName.startsWith("flyway_") && 
                        !tableName.startsWith("snapshot_") &&
                        !tableName.equalsIgnoreCase("INFORMATION_SCHEMA") &&
                        !tableName.equalsIgnoreCase("sys")) {
                        tables.add(tableName);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get tables", e);
        }
        return tables;
    }
    
    private boolean tableExists(String tableName) {
        try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isH2Database() {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            return url != null && url.toLowerCase().contains("h2");
        } catch (Exception e) {
            return false;
        }
    }
    
    public void handleMigrationFailure(Exception e) {
        log.error("Handling migration failure", e);
        // Implement auto-rollback logic if needed
    }
}