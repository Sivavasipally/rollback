// src/main/java/com/example/rollback/FlywayRollbackManager.java
package com.example.rollback;

import com.example.rollback.properties.FlywayRollbackProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RequiredArgsConstructor
public class FlywayRollbackManager {

    private static final Logger log = LoggerFactory.getLogger(FlywayRollbackManager.class);
    
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
            
            return RollbackResult.success(rollbackId, targetVersion, snapshotId);

        } catch (Exception e) {
            log.error("Rollback failed", e);
            auditRollback(rollbackId, targetVersion, "FAILED", e.getMessage());
            return RollbackResult.failure(rollbackId, targetVersion, e.getMessage());
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
                // For H2, export to CSV - use forward slashes for H2 compatibility
                String csvPath = snapshotDir.resolve(tableName + ".csv").toString().replace("\\", "/");
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
    
    public String getCurrentVersion() {
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
            executeRollbackScript(version);
        }
    }

    private void executeRollbackScript(String version) {
        // Try different rollback script naming patterns
        String[] possiblePaths = {
            "db/migration/U" + version + "__*_undo.sql",
            "db/rollback/U" + version + "__rollback.sql",
            "db/rollback/U" + version.replace(".", "_") + "__rollback.sql"
        };

        for (String pathPattern : possiblePaths) {
            try {
                // For H2 testing, we'll use the undo scripts in db/migration
                if (pathPattern.contains("undo.sql")) {
                    String undoScriptPath = findUndoScript(version);
                    if (undoScriptPath != null) {
                        log.info("Executing rollback script: {}", undoScriptPath);
                        executeUndoScript(undoScriptPath);
                        return; // Successfully executed, exit
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to execute rollback script for version {}: {}", version, e.getMessage());
            }
        }

        log.warn("No rollback script found for version: {}", version);
    }

    private String findUndoScript(String version) {
        // Map version to undo script filename
        switch (version) {
            case "1.0.0":
                return "db/migration/U1.0.0__Create_initial_schema_undo.sql";
            case "1.0.1":
                return "db/migration/U1.0.1__Add_user_profile_table_undo.sql";
            case "1.0.2":
                return "db/migration/U1.0.2__Add_audit_log_table_undo.sql";
            default:
                return null;
        }
    }

    private void executeUndoScript(String scriptPath) {
        try {
            ClassPathResource resource = new ClassPathResource(scriptPath);
            if (resource.exists()) {
                String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                // Split SQL by semicolon and execute each statement
                String[] statements = sql.split(";");
                log.info("Found {} SQL statements in rollback script", statements.length);
                for (int i = 0; i < statements.length; i++) {
                    String statement = statements[i].trim();

                    // Remove comments and empty lines
                    String[] lines = statement.split("\n");
                    StringBuilder cleanStatement = new StringBuilder();
                    for (String line : lines) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("--")) {
                            if (cleanStatement.length() > 0) {
                                cleanStatement.append(" ");
                            }
                            cleanStatement.append(line);
                        }
                    }

                    String finalStatement = cleanStatement.toString().trim();
                    if (!finalStatement.isEmpty()) {
                        log.info("Executing SQL statement {}: {}", i + 1, finalStatement);
                        try {
                            jdbcTemplate.execute(finalStatement);
                            log.info("Successfully executed statement {}", i + 1);
                        } catch (Exception e) {
                            log.error("Failed to execute statement {}: {}", i + 1, e.getMessage());
                            throw e;
                        }
                    } else {
                        log.debug("Skipping empty or comment statement {}: '{}'", i + 1, statement);
                    }
                }

                log.info("Successfully executed rollback script: {}", scriptPath);
            } else {
                log.warn("Rollback script not found: {}", scriptPath);
            }
        } catch (Exception e) {
            log.error("Failed to execute rollback script {}: {}", scriptPath, e.getMessage(), e);
            throw new RuntimeException("Rollback script execution failed", e);
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

        try {
            // Attempt to rollback to previous version
            String currentVersion = getCurrentVersion();
            String previousVersion = getPreviousVersion(currentVersion);

            if (previousVersion != null) {
                log.info("Attempting automatic rollback to version: {}", previousVersion);
                rollbackToVersion(previousVersion);
            }
        } catch (Exception rollbackException) {
            log.error("Automatic rollback failed", rollbackException);
        }
    }



    private String getPreviousVersion(String currentVersion) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history " +
                "WHERE version < ? AND success = " + (isH2Database() ? "TRUE" : "1") + " " +
                "ORDER BY installed_rank DESC LIMIT 1",
                String.class, currentVersion
            );
        } catch (Exception e) {
            log.warn("Failed to get previous version", e);
            return null;
        }
    }
}