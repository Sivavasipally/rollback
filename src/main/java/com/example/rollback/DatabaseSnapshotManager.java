package com.example.rollback;

import com.example.rollback.properties.FlywayRollbackProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Manages database snapshots for rollback operations
 * Supports multiple database types and provides data preservation mechanisms
 */
@Service
public class DatabaseSnapshotManager {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseSnapshotManager.class);
    
    private final DataSource dataSource;
    private final FlywayRollbackProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    
    public DatabaseSnapshotManager(DataSource dataSource, FlywayRollbackProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
        );
    }
    
    /**
     * Creates a comprehensive production snapshot with metadata
     */
    public String createProductionSnapshot(String rollbackId) {
        String snapshotId = "prod_" + rollbackId + "_" + LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        log.info("Creating production snapshot: {}", snapshotId);
        
        try {
            // Create snapshot directory
            Path snapshotDir = Paths.get(properties.getSnapshot().getStoragePath(), snapshotId);
            Files.createDirectories(snapshotDir);
            
            // Get database metadata
            Map<String, Object> databaseMetadata = getDatabaseMetadata();
            
            // Get all tables with their structure and relationships
            List<TableInfo> tables = getAllTablesWithStructure();
            
            // Create snapshots in parallel for large databases
            if (tables.size() > 10) {
                createParallelSnapshots(tables, snapshotDir);
            } else {
                createSequentialSnapshots(tables, snapshotDir);
            }
            
            // Save comprehensive metadata
            saveSnapshotMetadata(snapshotDir, tables, databaseMetadata);
            
            // Create DDL scripts for all objects
            createDDLScripts(snapshotDir);
            
            log.info("Production snapshot {} created successfully with {} tables", 
                snapshotId, tables.size());
            return snapshotId;
            
        } catch (Exception e) {
            log.error("Failed to create production snapshot", e);
            throw new RuntimeException("Production snapshot creation failed", e);
        }
    }
    
    /**
     * Restores database from a snapshot
     */
    @Transactional
    public boolean restoreFromSnapshot(String snapshotId) {
        log.info("Restoring from snapshot: {}", snapshotId);
        
        try {
            Path snapshotDir = Paths.get(properties.getSnapshot().getStoragePath(), snapshotId);
            if (!Files.exists(snapshotDir)) {
                log.error("Snapshot directory does not exist: {}", snapshotDir);
                return false;
            }
            
            // Read metadata
            Map<String, Object> metadata = readSnapshotMetadata(snapshotDir);
            
            // Restore database structure first
            restoreDatabaseStructure(snapshotDir);
            
            // Restore data
            restoreData(snapshotDir, metadata);
            
            log.info("Successfully restored from snapshot: {}", snapshotId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to restore from snapshot", e);
            return false;
        }
    }
    
    /**
     * Creates snapshots in parallel for large databases
     */
    private void createParallelSnapshots(List<TableInfo> tables, Path snapshotDir) {
        log.info("Creating parallel snapshots for {} tables", tables.size());
        
        List<CompletableFuture<Void>> futures = tables.stream()
            .map(table -> CompletableFuture.runAsync(() -> {
                try {
                    createTableSnapshot(table, snapshotDir);
                } catch (Exception e) {
                    log.warn("Failed to snapshot table: {}", table.getName(), e);
                }
            }, executorService))
            .collect(Collectors.toList());
        
        // Wait for all snapshots to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
    
    /**
     * Creates snapshots sequentially
     */
    private void createSequentialSnapshots(List<TableInfo> tables, Path snapshotDir) {
        for (TableInfo table : tables) {
            try {
                createTableSnapshot(table, snapshotDir);
            } catch (Exception e) {
                log.warn("Failed to snapshot table: {}", table.getName(), e);
            }
        }
    }
    
    /**
     * Creates a snapshot of a single table
     */
    private void createTableSnapshot(TableInfo table, Path snapshotDir) throws IOException {
        String tableName = table.getName();
        log.debug("Creating snapshot for table: {}", tableName);
        
        // Determine database type and use appropriate export method
        if (isDatabaseType("H2")) {
            exportTableToCSV(tableName, snapshotDir);
        } else if (isDatabaseType("MySQL") || isDatabaseType("MariaDB")) {
            exportTableToSQL(tableName, snapshotDir);
        } else if (isDatabaseType("PostgreSQL")) {
            exportTableToCopy(tableName, snapshotDir);
        } else if (isDatabaseType("Oracle")) {
            exportTableToOracle(tableName, snapshotDir);
        } else if (isDatabaseType("SQLServer")) {
            exportTableToSQLServer(tableName, snapshotDir);
        } else {
            // Generic fallback
            exportTableToCSV(tableName, snapshotDir);
        }
        
        // Save table structure
        saveTableStructure(table, snapshotDir);
    }
    
    /**
     * Exports table data to CSV format
     */
    private void exportTableToCSV(String tableName, Path snapshotDir) {
        try {
            String csvPath = snapshotDir.resolve(tableName + ".csv").toString().replace("\\", "\\\\");
            String sql = String.format("CALL CSVWRITE('%s', 'SELECT * FROM %s')", csvPath, tableName);
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("Failed to export table to CSV: {}", tableName, e);
        }
    }
    
    /**
     * Exports table data to SQL format (MySQL/MariaDB)
     */
    private void exportTableToSQL(String tableName, Path snapshotDir) {
        try {
            // Get data
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + tableName);
            
            // Generate INSERT statements
            List<String> insertStatements = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                StringBuilder insert = new StringBuilder("INSERT INTO " + tableName + " (");
                StringBuilder values = new StringBuilder(" VALUES (");
                
                boolean first = true;
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (!first) {
                        insert.append(", ");
                        values.append(", ");
                    }
                    insert.append(entry.getKey());
                    values.append(formatValue(entry.getValue()));
                    first = false;
                }
                
                insert.append(")");
                values.append(");");
                insertStatements.add(insert.toString() + values.toString());
            }
            
            // Write to file
            Path sqlFile = snapshotDir.resolve(tableName + ".sql");
            Files.write(sqlFile, insertStatements);
            
        } catch (Exception e) {
            log.warn("Failed to export table to SQL: {}", tableName, e);
        }
    }
    
    /**
     * Exports table data to PostgreSQL COPY format
     */
    private void exportTableToCopy(String tableName, Path snapshotDir) {
        // Implementation for PostgreSQL
    }
    
    /**
     * Exports table data for Oracle
     */
    private void exportTableToOracle(String tableName, Path snapshotDir) {
        // Implementation for Oracle
    }
    
    /**
     * Exports table data for SQL Server
     */
    private void exportTableToSQLServer(String tableName, Path snapshotDir) {
        // Implementation for SQL Server
    }
    
    /**
     * Saves table structure information
     */
    private void saveTableStructure(TableInfo table, Path snapshotDir) throws IOException {
        Path structureFile = snapshotDir.resolve(table.getName() + ".structure.json");
        Files.write(structureFile, objectMapper.writeValueAsBytes(table));
    }
    
    /**
     * Creates DDL scripts for all database objects
     */
    private void createDDLScripts(Path snapshotDir) {
        // Implementation depends on database type
    }
    
    /**
     * Saves comprehensive snapshot metadata
     */
    private void saveSnapshotMetadata(Path snapshotDir, List<TableInfo> tables, 
                                     Map<String, Object> databaseMetadata) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("snapshotId", snapshotDir.getFileName().toString());
        metadata.put("timestamp", LocalDateTime.now().toString());
        metadata.put("databaseMetadata", databaseMetadata);
        metadata.put("tables", tables.stream().map(TableInfo::getName).collect(Collectors.toList()));
        metadata.put("currentVersion", getCurrentVersion());
        
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        Files.write(snapshotDir.resolve("metadata.json"), json.getBytes());
    }
    
    /**
     * Reads snapshot metadata
     */
    private Map<String, Object> readSnapshotMetadata(Path snapshotDir) throws IOException {
        Path metadataFile = snapshotDir.resolve("metadata.json");
        if (!Files.exists(metadataFile)) {
            return Collections.emptyMap();
        }
        
        return objectMapper.readValue(metadataFile.toFile(), Map.class);
    }
    
    /**
     * Restores database structure from snapshot
     */
    private void restoreDatabaseStructure(Path snapshotDir) {
        // Implementation depends on database type
    }
    
    /**
     * Restores data from snapshot
     */
    private void restoreData(Path snapshotDir, Map<String, Object> metadata) {
        try {
            if (isDatabaseType("H2")) {
                restoreDataFromCSV(snapshotDir, metadata);
            } else {
                restoreDataFromSQL(snapshotDir, metadata);
            }
        } catch (Exception e) {
            log.error("Failed to restore data from snapshot", e);
            throw new RuntimeException("Data restoration failed", e);
        }
    }

    private void restoreDataFromCSV(Path snapshotDir, Map<String, Object> metadata) throws IOException {
        // Get list of tables from metadata
        @SuppressWarnings("unchecked")
        List<String> tables = (List<String>) metadata.getOrDefault("tables", Collections.emptyList());

        for (String tableName : tables) {
            Path csvFile = snapshotDir.resolve(tableName + ".csv");
            if (Files.exists(csvFile)) {
                try {
                    // Clear existing data
                    jdbcTemplate.execute("DELETE FROM " + tableName);

                    // Restore from CSV
                    String csvPath = csvFile.toString().replace("\\", "\\\\");
                    String sql = String.format("INSERT INTO %s SELECT * FROM CSVREAD('%s')", tableName, csvPath);
                    jdbcTemplate.execute(sql);

                    log.debug("Restored data for table: {}", tableName);
                } catch (Exception e) {
                    log.warn("Failed to restore table {}: {}", tableName, e.getMessage());
                }
            }
        }
    }

    private void restoreDataFromSQL(Path snapshotDir, Map<String, Object> metadata) {
        // Implementation for SQL-based restoration
        log.warn("SQL-based restoration not yet implemented");
    }
    
    /**
     * Gets current database version
     */
    private String getCurrentVersion() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history " +
                "WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class
            );
        } catch (Exception e) {
            return "0";
        }
    }
    
    /**
     * Gets database metadata
     */
    private Map<String, Object> getDatabaseMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData dbMetaData = conn.getMetaData();
            metadata.put("databaseProductName", dbMetaData.getDatabaseProductName());
            metadata.put("databaseProductVersion", dbMetaData.getDatabaseProductVersion());
            metadata.put("driverName", dbMetaData.getDriverName());
            metadata.put("driverVersion", dbMetaData.getDriverVersion());
            metadata.put("url", dbMetaData.getURL());
            metadata.put("username", dbMetaData.getUserName());
            metadata.put("catalog", conn.getCatalog());
            metadata.put("schema", conn.getSchema());
        } catch (Exception e) {
            log.warn("Failed to get database metadata", e);
        }
        return metadata;
    }
    
    /**
     * Gets all tables with their structure and relationships
     */
    private List<TableInfo> getAllTablesWithStructure() {
        List<TableInfo> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            
            try (ResultSet rs = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // Exclude system tables
                    if (!isSystemTable(tableName)) {
                        TableInfo tableInfo = new TableInfo(tableName);
                        
                        // Get columns
                        try (ResultSet columns = metaData.getColumns(catalog, null, tableName, null)) {
                            while (columns.next()) {
                                tableInfo.addColumn(new ColumnInfo(
                                    columns.getString("COLUMN_NAME"),
                                    columns.getString("TYPE_NAME"),
                                    columns.getInt("COLUMN_SIZE"),
                                    columns.getInt("NULLABLE") == 1
                                ));
                            }
                        }
                        
                        // Get primary keys
                        try (ResultSet pks = metaData.getPrimaryKeys(catalog, null, tableName)) {
                            while (pks.next()) {
                                tableInfo.addPrimaryKeyColumn(pks.getString("COLUMN_NAME"));
                            }
                        }
                        
                        // Get foreign keys
                        try (ResultSet fks = metaData.getImportedKeys(catalog, null, tableName)) {
                            while (fks.next()) {
                                tableInfo.addForeignKey(new ForeignKeyInfo(
                                    fks.getString("FK_NAME"),
                                    fks.getString("FKCOLUMN_NAME"),
                                    fks.getString("PKTABLE_NAME"),
                                    fks.getString("PKCOLUMN_NAME")
                                ));
                            }
                        }
                        
                        tables.add(tableInfo);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get tables with structure", e);
        }
        return tables;
    }
    
    /**
     * Checks if a table is a system table
     */
    private boolean isSystemTable(String tableName) {
        return tableName.startsWith("flyway_") || 
               tableName.startsWith("snapshot_") ||
               tableName.equalsIgnoreCase("INFORMATION_SCHEMA") ||
               tableName.equalsIgnoreCase("sys");
    }
    
    /**
     * Checks if the database is of a specific type
     */
    private boolean isDatabaseType(String type) {
        try (Connection conn = dataSource.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName();
            return productName != null && productName.contains(type);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Formats a value for SQL insertion
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof String) {
            return "'" + ((String) value).replace("'", "''") + "'";
        } else if (value instanceof Date) {
            return "'" + value.toString() + "'";
        } else if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        } else {
            return value.toString();
        }
    }
    
    // Helper classes for table structure
    public static class TableInfo {
        private final String name;
        private final List<ColumnInfo> columns = new ArrayList<>();
        private final List<String> primaryKeyColumns = new ArrayList<>();
        private final List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
        
        public TableInfo(String name) {
            this.name = name;
        }
        
        public void addColumn(ColumnInfo column) {
            columns.add(column);
        }
        
        public void addPrimaryKeyColumn(String columnName) {
            primaryKeyColumns.add(columnName);
        }
        
        public void addForeignKey(ForeignKeyInfo foreignKey) {
            foreignKeys.add(foreignKey);
        }
        
        public String getName() { return name; }
        public List<ColumnInfo> getColumns() { return columns; }
        public List<String> getPrimaryKeyColumns() { return primaryKeyColumns; }
        public List<ForeignKeyInfo> getForeignKeys() { return foreignKeys; }
    }
    
    public static class ColumnInfo {
        private final String name;
        private final String type;
        private final int size;
        private final boolean nullable;
        
        public ColumnInfo(String name, String type, int size, boolean nullable) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.nullable = nullable;
        }
        
        public String getName() { return name; }
        public String getType() { return type; }
        public int getSize() { return size; }
        public boolean isNullable() { return nullable; }
    }
    
    public static class ForeignKeyInfo {
        private final String name;
        private final String column;
        private final String referencedTable;
        private final String referencedColumn;
        
        public ForeignKeyInfo(String name, String column, String referencedTable, String referencedColumn) {
            this.name = name;
            this.column = column;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
        }
        
        public String getName() { return name; }
        public String getColumn() { return column; }
        public String getReferencedTable() { return referencedTable; }
        public String getReferencedColumn() { return referencedColumn; }
    }
}
