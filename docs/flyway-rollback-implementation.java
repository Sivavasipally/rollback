// Complete Implementation of Flyway Rollback Framework for Spring Boot

// 1. Main Configuration Class
@Configuration
@EnableConfigurationProperties(FlywayRollbackProperties.class)
@EnableScheduling
@Slf4j
public class FlywayRollbackAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "flyway.rollback.enabled", havingValue = "true", matchIfMissing = true)
    public FlywayRollbackManager flywayRollbackManager(
            DataSource dataSource,
            FlywayRollbackProperties properties) {
        return new FlywayRollbackManager(dataSource, properties);
    }
    
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(FlywayRollbackManager rollbackManager) {
        return flyway -> {
            // Create pre-migration snapshot if enabled
            if (rollbackManager.isSnapshotEnabled()) {
                String snapshotId = rollbackManager.createPreMigrationSnapshot();
                log.info("Created pre-migration snapshot: {}", snapshotId);
            }
            
            try {
                flyway.migrate();
            } catch (Exception e) {
                log.error("Migration failed, initiating automatic rollback", e);
                rollbackManager.handleMigrationFailure(e);
                throw e;
            }
        };
    }
}

// 2. Properties Configuration
@ConfigurationProperties(prefix = "flyway.rollback")
@Data
public class FlywayRollbackProperties {
    
    private boolean enabled = true;
    private boolean autoRollbackOnFailure = false;
    private boolean requireApproval = true;
    private boolean dryRunEnabled = true;
    
    private SnapshotProperties snapshot = new SnapshotProperties();
    private AuditProperties audit = new AuditProperties();
    private SafetyProperties safety = new SafetyProperties();
    
    @Data
    public static class SnapshotProperties {
        private boolean enabled = true;
        private String storagePath = "/var/flyway/snapshots";
        private int retentionDays = 7;
        private boolean compressSnapshots = true;
    }
    
    @Data
    public static class AuditProperties {
        private boolean enabled = true;
        private String tableName = "flyway_rollback_audit";
        private boolean includeUserInfo = true;
    }
    
    @Data
    public static class SafetyProperties {
        private boolean checkActiveConnections = true;
        private boolean checkReplicationLag = true;
        private int maxReplicationLagSeconds = 60;
        private boolean validateDataIntegrity = true;
    }
}

// 3. Enhanced Rollback Manager with Production Features
@Component
@Slf4j
@Transactional
public class FlywayRollbackManager {
    
    private final DataSource dataSource;
    private final FlywayRollbackProperties properties;
    private final SnapshotManager snapshotManager;
    private final RollbackScriptGenerator scriptGenerator;
    private final AuditService auditService;
    private final SafetyValidator safetyValidator;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public FlywayRollbackManager(DataSource dataSource, FlywayRollbackProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.snapshotManager = new SnapshotManager(dataSource, properties.getSnapshot());
        this.scriptGenerator = new RollbackScriptGenerator(dataSource);
        this.auditService = new AuditService(dataSource, properties.getAudit());
        this.safetyValidator = new SafetyValidator(dataSource, properties.getSafety());
    }
    
    public RollbackResult rollbackToVersion(String targetVersion, RollbackOptions options) {
        String rollbackId = UUID.randomUUID().toString();
        log.info("Starting rollback {} to version {}", rollbackId, targetVersion);
        
        RollbackContext context = new RollbackContext(rollbackId, targetVersion, options);
        
        try {
            // Phase 1: Validation
            ValidationResult validation = validateRollback(context);
            if (!validation.isValid()) {
                throw new RollbackValidationException(validation.getErrors());
            }
            
            // Phase 2: Safety Checks
            SafetyCheckResult safetyCheck = safetyValidator.performSafetyCheck(context);
            if (safetyCheck.hasBlockingIssues()) {
                throw new RollbackSafetyException(safetyCheck.getBlockingIssues());
            }
            
            // Phase 3: Generate Rollback Plan
            RollbackPlan plan = generateRollbackPlan(context);
            
            // Phase 4: Dry Run (if enabled)
            if (options.isDryRun() || properties.isDryRunEnabled()) {
                return executeDryRun(context, plan);
            }
            
            // Phase 5: Create Safety Snapshot
            String snapshotId = null;
            if (properties.getSnapshot().isEnabled()) {
                snapshotId = snapshotManager.createSnapshot(
                    String.format("rollback_%s_%s", targetVersion, rollbackId));
            }
            
            // Phase 6: Execute Rollback
            executeRollback(context, plan);
            
            // Phase 7: Verify Rollback
            verifyRollback(context);
            
            // Phase 8: Update Flyway Schema History
            updateFlywaySchemaHistory(context);
            
            // Phase 9: Audit and Notify
            RollbackResult result = RollbackResult.success(rollbackId, targetVersion, snapshotId);
            auditService.auditRollback(context, result);
            eventPublisher.publishEvent(new RollbackCompletedEvent(context, result));
            
            return result;
            
        } catch (Exception e) {
            log.error("Rollback {} failed", rollbackId, e);
            RollbackResult result = RollbackResult.failure(rollbackId, targetVersion, e);
            auditService.auditRollback(context, result);
            eventPublisher.publishEvent(new RollbackFailedEvent(context, result));
            
            // Attempt recovery if snapshot exists
            if (context.getSnapshotId() != null && options.isAutoRecovery()) {
                attemptRecovery(context);
            }
            
            throw new RollbackException("Rollback failed", e);
        }
    }
    
    private void executeRollback(RollbackContext context, RollbackPlan plan) {
        log.info("Executing rollback plan with {} operations", plan.getOperations().size());
        
        Connection connection = null;
        boolean originalAutoCommit = true;
        
        try {
            connection = dataSource.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            
            for (RollbackOperation operation : plan.getOperations()) {
                executeOperation(connection, operation, context);
            }
            
        } catch (SQLException e) {
            throw new RollbackExecutionException("Failed to execute rollback", e);
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                    connection.close();
                } catch (SQLException e) {
                    log.error("Failed to close connection", e);
                }
            }
        }
    }
    
    private void executeOperation(Connection connection, RollbackOperation operation, 
                                  RollbackContext context) throws SQLException {
        
        log.debug("Executing rollback operation: {}", operation.getDescription());
        
        switch (operation.getType()) {
            case DDL:
                executeDDLOperation(connection, operation);
                break;
            case DML:
                executeDMLOperation(connection, operation);
                break;
            case DCL:
                executeDCLOperation(connection, operation);
                break;
            default:
                throw new UnsupportedOperationException(
                    "Operation type not supported: " + operation.getType());
        }
        
        // Record progress
        context.recordProgress(operation);
        
        // Emit progress event
        eventPublisher.publishEvent(
            new RollbackProgressEvent(context, operation));
    }
}

// 4. Advanced DDL Parser and Rollback Generator
@Component
public class DDLRollbackGenerator {
    
    private final DatabaseMetadataExtractor metadataExtractor;
    
    public DDLRollbackGenerator(DataSource dataSource) {
        this.metadataExtractor = new DatabaseMetadataExtractor(dataSource);
    }
    
    public String generateRollback(String ddlStatement, MigrationVersion version) {
        DDLStatement parsed = DDLParser.parse(ddlStatement);
        
        switch (parsed.getOperationType()) {
            case CREATE_TABLE:
                return generateDropTable(parsed);
                
            case ALTER_TABLE:
                return generateAlterTableRollback(parsed, version);
                
            case CREATE_INDEX:
                return generateDropIndex(parsed);
                
            case ADD_CONSTRAINT:
                return generateDropConstraint(parsed);
                
            case DROP_TABLE:
                return generateRecreateTable(parsed, version);
                
            default:
                throw new UnsupportedOperationException(
                    "DDL operation not supported: " + parsed.getOperationType());
        }
    }
    
    private String generateAlterTableRollback(DDLStatement stmt, MigrationVersion version) {
        String tableName = stmt.getTableName();
        TableMetadata historicalMetadata = metadataExtractor.getTableMetadataAtVersion(
            tableName, version.getPreviousVersion());
        
        StringBuilder rollback = new StringBuilder();
        rollback.append("-- Rollback ALTER TABLE ").append(tableName).append("\n");
        
        for (AlterOperation operation : stmt.getAlterOperations()) {
            switch (operation.getType()) {
                case ADD_COLUMN:
                    rollback.append(String.format("ALTER TABLE %s DROP COLUMN %s;\n",
                        tableName, operation.getColumnName()));
                    break;
                    
                case DROP_COLUMN:
                    ColumnMetadata droppedColumn = historicalMetadata.getColumn(
                        operation.getColumnName());
                    if (droppedColumn != null) {
                        rollback.append(String.format(
                            "ALTER TABLE %s ADD COLUMN %s %s %s %s;\n",
                            tableName,
                            droppedColumn.getName(),
                            droppedColumn.getDataType(),
                            droppedColumn.isNullable() ? "NULL" : "NOT NULL",
                            droppedColumn.getDefaultValue() != null ? 
                                "DEFAULT " + droppedColumn.getDefaultValue() : ""
                        ));
                        
                        // Restore data if exists in archive
                        if (metadataExtractor.hasArchivedData(tableName, 
                                droppedColumn.getName(), version)) {
                            rollback.append(generateDataRestoration(
                                tableName, droppedColumn.getName(), version));
                        }
                    }
                    break;
                    
                case MODIFY_COLUMN:
                    ColumnMetadata originalColumn = historicalMetadata.getColumn(
                        operation.getColumnName());
                    if (originalColumn != null) {
                        rollback.append(String.format(
                            "ALTER TABLE %s MODIFY COLUMN %s %s %s %s;\n",
                            tableName,
                            originalColumn.getName(),
                            originalColumn.getDataType(),
                            originalColumn.isNullable() ? "NULL" : "NOT NULL",
                            originalColumn.getDefaultValue() != null ? 
                                "DEFAULT " + originalColumn.getDefaultValue() : ""
                        ));
                    }
                    break;
            }
        }
        
        return rollback.toString();
    }
}

// 5. DML Rollback with Data Preservation
@Component
@Slf4j
public class DMLRollbackGenerator {
    
    private final DataArchiveService archiveService;
    private final DataSource dataSource;
    
    public DMLRollbackGenerator(DataSource dataSource) {
        this.dataSource = dataSource;
        this.archiveService = new DataArchiveService(dataSource);
    }
    
    public String generateRollback(DMLStatement statement, MigrationContext context) {
        switch (statement.getOperationType()) {
            case INSERT:
                return generateDeleteRollback(statement, context);
            case UPDATE:
                return generateUpdateRollback(statement, context);
            case DELETE:
                return generateInsertRollback(statement, context);
            default:
                throw new UnsupportedOperationException(
                    "DML operation not supported: " + statement.getOperationType());
        }
    }
    
    private String generateUpdateRollback(DMLStatement statement, MigrationContext context) {
        String tableName = statement.getTableName();
        List<String> affectedColumns = statement.getAffectedColumns();
        
        // Retrieve archived data before the update
        ArchivedData archivedData = archiveService.getArchivedData(
            tableName, context.getVersion(), context.getExecutionTime());
        
        if (archivedData.isEmpty()) {
            log.warn("No archived data found for table {} at version {}", 
                tableName, context.getVersion());
            return generateGenericUpdateRollback(statement);
        }
        
        StringBuilder rollback = new StringBuilder();
        rollback.append("-- Rollback UPDATE on ").append(tableName).append("\n");
        rollback.append("-- Restoring ").append(archivedData.getRowCount())
                .append(" rows to previous state\n\n");
        
        // Generate batch updates for better performance
        rollback.append("START TRANSACTION;\n\n");
        
        int batchCount = 0;
        for (ArchivedRow row : archivedData.getRows()) {
            rollback.append("UPDATE ").append(tableName).append(" SET ");
            
            List<String> setClauses = new ArrayList<>();
            for (String column : affectedColumns) {
                Object originalValue = row.getColumnValue(column);
                setClauses.add(String.format("%s = %s", 
                    column, formatSqlValue(originalValue)));
            }
            
            rollback.append(String.join(", ", setClauses));
            rollback.append(" WHERE ");
            rollback.append(buildPrimaryKeyCondition(row));
            rollback.append(";\n");
            
            batchCount++;
            if (batchCount % 1000 == 0) {
                rollback.append("\n-- Batch ").append(batchCount / 1000)
                        .append(" completed\n");
            }
        }
        
        rollback.append("\nCOMMIT;\n");
        
        // Add verification query
        rollback.append("\n-- Verification query\n");
        rollback.append("SELECT COUNT(*) AS updated_rows FROM ").append(tableName)
                .append(" WHERE ");
        rollback.append(statement.getWhereClause()).append(";\n");
        
        return rollback.toString();
    }
    
    private String formatSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof String || value instanceof LocalDateTime 
                || value instanceof LocalDate) {
            return "'" + escapeSqlString(value.toString()) + "'";
        } else if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        } else {
            return value.toString();
        }
    }
}

// 6. Production-Ready Snapshot Manager
@Component
@Slf4j
public class SnapshotManager {
    
    private final DataSource dataSource;
    private final SnapshotProperties properties;
    private final SnapshotStorage storage;
    
    public SnapshotManager(DataSource dataSource, SnapshotProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.storage = new SnapshotStorage(properties.getStoragePath());
    }
    
    public String createSnapshot(String snapshotName) {
        String snapshotId = generateSnapshotId();
        SnapshotContext context = new SnapshotContext(snapshotId, snapshotName);
        
        log.info("Creating snapshot: {} ({})", snapshotName, snapshotId);
        
        try (Connection connection = dataSource.getConnection()) {
            // Get database metadata
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            
            // Get all tables
            List<TableInfo> tables = getTablesInfo(connection, metaData, catalog);
            context.setTables(tables);
            
            // Create snapshots for each table
            for (TableInfo table : tables) {
                createTableSnapshot(connection, table, context);
            }
            
            // Save snapshot metadata
            storage.saveSnapshotMetadata(context);
            
            // Compress if enabled
            if (properties.isCompressSnapshots()) {
                storage.compressSnapshot(snapshotId);
            }
            
            log.info("Snapshot {} created successfully with {} tables", 
                snapshotId, tables.size());
            
            return snapshotId;
            
        } catch (Exception e) {
            log.error("Failed to create snapshot", e);
            // Cleanup partial snapshot
            storage.deleteSnapshot(snapshotId);
            throw new SnapshotException("Snapshot creation failed", e);
        }
    }
    
    private void createTableSnapshot(Connection connection, TableInfo table, 
                                     SnapshotContext context) throws SQLException {
        
        String snapshotTableName = String.format("snapshot_%s_%s", 
            context.getSnapshotId(), table.getName());
        
        log.debug("Creating snapshot for table: {}", table.getName());
        
        try (Statement stmt = connection.createStatement()) {
            // Check if we should use storage engine specific features
            if (isMySQL(connection)) {
                // Use MySQL specific optimizations
                stmt.execute(String.format(
                    "CREATE TABLE %s ENGINE=InnoDB ROW_FORMAT=COMPRESSED AS SELECT * FROM %s",
                    snapshotTableName, table.getName()));
            } else if (isPostgreSQL(connection)) {
                // Use PostgreSQL specific optimizations
                stmt.execute(String.format(
                    "CREATE TABLE %s AS SELECT * FROM %s WITH DATA",
                    snapshotTableName, table.getName()));
            } else {
                // Generic approach
                stmt.execute(String.format(
                    "CREATE TABLE %s AS SELECT * FROM %s",
                    snapshotTableName, table.getName()));
            }
            
            // Add indexes for faster restoration
            createSnapshotIndexes(connection, table, snapshotTableName);
            
            // Record snapshot details
            long rowCount = getTableRowCount(connection, snapshotTableName);
            context.addTableSnapshot(table.getName(), snapshotTableName, rowCount);
        }
    }
    
    public void restoreFromSnapshot(String snapshotId, RestoreOptions options) {
        log.info("Restoring from snapshot: {}", snapshotId);
        
        SnapshotContext context = storage.loadSnapshotMetadata(snapshotId);
        
        if (context == null) {
            throw new SnapshotException("Snapshot not found: " + snapshotId);
        }
        
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            
            // Disable foreign key checks during restore
            disableForeignKeyChecks(connection);
            
            try {
                for (TableSnapshot tableSnapshot : context.getTableSnapshots()) {
                    restoreTable(connection, tableSnapshot, options);
                }
                
                connection.commit();
                
                // Re-enable foreign key checks
                enableForeignKeyChecks(connection);
                
                // Verify data integrity
                if (options.isVerifyIntegrity()) {
                    verifyDataIntegrity(connection, context);
                }
                
                log.info("Snapshot {} restored successfully", snapshotId);
                
            } catch (Exception e) {
                connection.rollback();
                enableForeignKeyChecks(connection);
                throw new SnapshotException("Restore failed", e);
            }
            
        } catch (SQLException e) {
            throw new SnapshotException("Failed to restore snapshot", e);
        }
    }
}

// 7. REST Controller with Security and Monitoring
@RestController
@RequestMapping("/api/flyway/rollback")
@Slf4j
@PreAuthorize("hasRole('DBA') or hasRole('ADMIN')")
public class FlywayRollbackController {
    
    private final FlywayRollbackManager rollbackManager;
    private final RollbackApprovalService approvalService;
    private final RollbackMetrics metrics;
    
    @Autowired
    public FlywayRollbackController(FlywayRollbackManager rollbackManager,
                                    RollbackApprovalService approvalService,
                                    MeterRegistry meterRegistry) {
        this.rollbackManager = rollbackManager;
        this.approvalService = approvalService;
        this.metrics = new RollbackMetrics(meterRegistry);
    }
    
    @PostMapping("/execute")
    @Transactional
    public ResponseEntity<RollbackResponse> executeRollback(
            @Valid @RequestBody RollbackRequest request,
            @AuthenticationPrincipal UserDetails user) {
        
        log.info("Rollback request from user: {} to version: {}", 
            user.getUsername(), request.getTargetVersion());
        
        metrics.recordRollbackRequest(request);
        
        try {
            // Check approval if required
            if (rollbackManager.isApprovalRequired() && !request.isEmergency()) {
                ApprovalStatus approval = approvalService.checkApproval(
                    request, user.getUsername());
                
                if (!approval.isApproved()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(RollbackResponse.needsApproval(approval));
                }
            }
            
            // Execute rollback
            RollbackOptions options = RollbackOptions.builder()
                .dryRun(request.isDryRun())
                .createSnapshot(request.isCreateSnapshot())
                .notifyOnCompletion(request.isNotifyOnCompletion())
                .timeout(Duration.ofMinutes(request.getTimeoutMinutes()))
                .build();
            
            RollbackResult result = rollbackManager.rollbackToVersion(
                request.getTargetVersion(), options);
            
            metrics.recordRollbackSuccess(result);
            
            return ResponseEntity.ok(RollbackResponse.success(result));
            
        } catch (RollbackException e) {
            log.error("Rollback failed", e);
            metrics.recordRollbackFailure(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RollbackResponse.failure(e));
        }
    }
    
    @GetMapping("/plan/{targetVersion}")
    public ResponseEntity<RollbackPlanResponse> getRollbackPlan(
            @PathVariable String targetVersion,
            @RequestParam(defaultValue = "false") boolean detailed) {
        
        try {
            RollbackPlan plan = rollbackManager.generateRollbackPlan(
                new RollbackContext(UUID.randomUUID().toString(), targetVersion, null));
            
            return ResponseEntity.ok(RollbackPlanResponse.from(plan, detailed));
            
        } catch (Exception e) {
            log.error("Failed to generate rollback plan", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(RollbackPlanResponse.error(e.getMessage()));
        }
    }
    
    @PostMapping("/dry-run")
    public ResponseEntity<DryRunResponse> performDryRun(
            @Valid @RequestBody RollbackRequest request) {
        
        log.info("Performing dry run for rollback to version: {}", 
            request.getTargetVersion());
        
        try {
            RollbackOptions options = RollbackOptions.builder()
                .dryRun(true)
                .build();
            
            DryRunResult result = rollbackManager.performDryRun(
                request.getTargetVersion(), options);
            
            return ResponseEntity.ok(DryRunResponse.from(result));
            
        } catch (Exception e) {
            log.error("Dry run failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(DryRunResponse.error(e.getMessage()));
        }
    }
    
    @GetMapping("/history")
    public ResponseEntity<List<RollbackHistoryEntry>> getRollbackHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, 
            Sort.by(Sort.Direction.DESC, "executedAt"));
        
        Page<RollbackHistoryEntry> history = rollbackManager.getRollbackHistory(pageable);
        
        return ResponseEntity.ok()
            .header("X-Total-Count", String.valueOf(history.getTotalElements()))
            .body(history.getContent());
    }
}

// 8. Monitoring and Metrics
@Component
public class RollbackMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter rollbackRequests;
    private final Counter rollbackSuccesses;
    private final Counter rollbackFailures;
    private final Timer rollbackDuration;
    
    public RollbackMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.rollbackRequests = Counter.builder("flyway.rollback.requests")
            .description("Total number of rollback requests")
            .register(meterRegistry);
        
        this.rollbackSuccesses = Counter.builder("flyway.rollback.successes")
            .description("Number of successful rollbacks")
            .register(meterRegistry);
        
        this.rollbackFailures = Counter.builder("flyway.rollback.failures")
            .description("Number of failed rollbacks")
            .register(meterRegistry);
        
        this.rollbackDuration = Timer.builder("flyway.rollback.duration")
            .description("Time taken to perform rollback")
            .register(meterRegistry);
    }
    
    public void recordRollbackRequest(RollbackRequest request) {
        rollbackRequests.increment();
        
        meterRegistry.counter("flyway.rollback.requests.byVersion",
            "version", request.getTargetVersion()).increment();
    }
    
    public void recordRollbackSuccess(RollbackResult result) {
        rollbackSuccesses.increment();
        
        rollbackDuration.record(result.getDuration());
        
        meterRegistry.gauge("flyway.rollback.lastSuccess.timestamp",
            System.currentTimeMillis());
    }
    
    public void recordRollbackFailure(Exception e) {
        rollbackFailures.increment();
        
        meterRegistry.counter("flyway.rollback.failures.byType",
            "type", e.getClass().getSimpleName()).increment();
    }
}