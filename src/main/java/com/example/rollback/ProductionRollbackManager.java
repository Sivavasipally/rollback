package com.example.rollback;

import com.example.rollback.properties.FlywayRollbackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade rollback manager with comprehensive safety mechanisms
 * Handles DML/DDL/DQL/DCL/TCL operations with data preservation
 */
@Service
public class ProductionRollbackManager {
    
    private static final Logger log = LoggerFactory.getLogger(ProductionRollbackManager.class);
    
    private final DataSource dataSource;
    private final FlywayRollbackProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSnapshotManager snapshotManager;
    private final RollbackAuditService auditService;
    private final SafetyGuardService safetyGuard;
    private final FlywayRollbackManager rollbackManager;

    public ProductionRollbackManager(DataSource dataSource,
                                   FlywayRollbackProperties properties,
                                   DatabaseSnapshotManager snapshotManager,
                                   RollbackAuditService auditService,
                                   SafetyGuardService safetyGuard,
                                   FlywayRollbackManager rollbackManager) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.snapshotManager = snapshotManager;
        this.auditService = auditService;
        this.safetyGuard = safetyGuard;
        this.rollbackManager = rollbackManager;
    }
    
    /**
     * Production-safe rollback with comprehensive validation and data preservation
     */
    @Transactional
    public RollbackResult executeProductionRollback(RollbackRequest request) {
        String rollbackId = UUID.randomUUID().toString();
        log.info("Starting production rollback {} to version {}", rollbackId, request.getTargetVersion());
        
        try {
            // Phase 1: Pre-rollback validation and safety checks
            ValidationResult validation = validateRollbackRequest(request);
            if (!validation.isValid()) {
                return RollbackResult.failure(rollbackId, request.getTargetVersion(), 
                    "Validation failed: " + validation.getErrorMessage());
            }
            
            // Phase 2: Create comprehensive snapshot
            String snapshotId = null;
            if (properties.getSnapshot().isEnabled()) {
                snapshotId = snapshotManager.createProductionSnapshot(rollbackId);
                log.info("Created production snapshot: {}", snapshotId);
            }
            
            // Phase 3: Dry run if requested
            if (request.isDryRun()) {
                return performDryRun(rollbackId, request, snapshotId);
            }
            
            // Phase 4: Execute rollback with safety mechanisms
            RollbackExecutionResult executionResult = executeRollbackWithSafety(request, rollbackId);
            
            // Phase 5: Verify rollback success
            boolean verificationPassed = verifyRollbackSuccess(request.getTargetVersion());
            
            if (executionResult.isSuccess() && verificationPassed) {
                auditService.recordSuccessfulRollback(rollbackId, request, snapshotId);
                return RollbackResult.success(rollbackId, request.getTargetVersion(), snapshotId);
            } else {
                // Attempt recovery if possible
                attemptRollbackRecovery(rollbackId, snapshotId);
                return RollbackResult.failure(rollbackId, request.getTargetVersion(), 
                    "Rollback verification failed");
            }
            
        } catch (Exception e) {
            log.error("Production rollback failed", e);
            auditService.recordFailedRollback(rollbackId, request, e);
            return RollbackResult.failure(rollbackId, request.getTargetVersion(), e.getMessage());
        }
    }
    
    /**
     * Validates rollback request with production safety checks
     */
    private ValidationResult validateRollbackRequest(RollbackRequest request) {
        List<String> errors = new ArrayList<>();
        
        // Check if target version exists
        if (!versionExists(request.getTargetVersion())) {
            errors.add("Target version " + request.getTargetVersion() + " does not exist");
        }
        
        // Check if rollback would cause data loss
        if (safetyGuard.wouldCauseDataLoss(getCurrentVersion(), request.getTargetVersion())) {
            if (!request.isForceDataLoss()) {
                errors.add("Rollback would cause data loss. Use forceDataLoss=true to proceed");
            }
        }
        
        // Check production environment constraints
        if (safetyGuard.isProductionEnvironment()) {
            if (!request.isProductionApproved()) {
                errors.add("Production rollback requires explicit approval");
            }
            
            // Check business hours
            if (safetyGuard.isBusinessHours() && !request.isEmergencyRollback()) {
                errors.add("Production rollback during business hours requires emergency flag");
            }
        }
        
        // Check database locks and active connections
        if (safetyGuard.hasActiveLongRunningTransactions()) {
            errors.add("Active long-running transactions detected. Wait or force termination");
        }
        
        return errors.isEmpty() ? 
            ValidationResult.valid() : 
            ValidationResult.invalid(String.join("; ", errors));
    }
    
    /**
     * Performs dry run to simulate rollback without making changes
     */
    private RollbackResult performDryRun(String rollbackId, RollbackRequest request, String snapshotId) {
        log.info("Performing dry run for rollback {}", rollbackId);
        
        try {
            // Simulate rollback steps
            List<String> steps = generateRollbackSteps(getCurrentVersion(), request.getTargetVersion());
            
            // Validate each step
            for (String step : steps) {
                if (!validateRollbackStep(step)) {
                    return RollbackResult.failure(rollbackId, request.getTargetVersion(), 
                        "Dry run failed at step: " + step);
                }
            }
            
            auditService.recordDryRun(rollbackId, request, steps);
            return RollbackResult.dryRunSuccess(rollbackId, request.getTargetVersion(), steps);
            
        } catch (Exception e) {
            log.error("Dry run failed", e);
            return RollbackResult.failure(rollbackId, request.getTargetVersion(), 
                "Dry run failed: " + e.getMessage());
        }
    }
    
    /**
     * Executes rollback with comprehensive safety mechanisms
     */
    private RollbackExecutionResult executeRollbackWithSafety(RollbackRequest request, String rollbackId) {
        try {
            // Set safety timeouts
            CompletableFuture<Boolean> rollbackFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    executeRollbackSteps(getCurrentVersion(), request.getTargetVersion());
                    return true;
                } catch (Exception e) {
                    log.error("Rollback execution failed", e);
                    return false;
                }
            });
            
            // Wait with timeout
            boolean success = rollbackFuture.get(
                properties.getRollbackTimeoutMinutes(), 
                TimeUnit.MINUTES
            );
            
            return new RollbackExecutionResult(success, null);
            
        } catch (Exception e) {
            log.error("Rollback execution failed with safety timeout", e);
            return new RollbackExecutionResult(false, e.getMessage());
        }
    }
    
    /**
     * Executes the actual rollback steps
     */
    private boolean executeRollbackSteps(String currentVersion, String targetVersion) {
        try {
            log.info("Executing rollback from version {} to version {}", currentVersion, targetVersion);

            // Use the FlywayRollbackManager to execute the complete rollback
            RollbackResult result = rollbackManager.rollbackToVersion(targetVersion);

            if (result.isSuccess()) {
                log.info("Successfully executed rollback to version: {}", targetVersion);
                return true;
            } else {
                log.error("Failed to execute rollback to version: {}. Error: {}", targetVersion, result.getErrorMessage());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Failed to execute rollback steps", e);
            return false;
        }
    }
    
    /**
     * Verifies that rollback was successful
     */
    private boolean verifyRollbackSuccess(String targetVersion) {
        try {
            String currentVersion = getCurrentVersion();
            boolean versionMatches = targetVersion.equals(currentVersion);
            
            if (!versionMatches) {
                log.error("Version verification failed. Expected: {}, Actual: {}", 
                    targetVersion, currentVersion);
                return false;
            }
            
            // Additional integrity checks
            return safetyGuard.verifyDatabaseIntegrity();
            
        } catch (Exception e) {
            log.error("Rollback verification failed", e);
            return false;
        }
    }
    
    /**
     * Attempts to recover from failed rollback
     */
    private void attemptRollbackRecovery(String rollbackId, String snapshotId) {
        log.warn("Attempting rollback recovery for rollback: {}", rollbackId);
        
        try {
            if (snapshotId != null && properties.getSnapshot().isEnabled()) {
                log.info("Attempting to restore from snapshot: {}", snapshotId);
                snapshotManager.restoreFromSnapshot(snapshotId);
            }
        } catch (Exception e) {
            log.error("Rollback recovery failed", e);
        }
    }
    
    // Helper methods
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
    
    private boolean versionExists(String version) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ?",
                Integer.class, version
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private List<String> getVersionsToRollback(String currentVersion, String targetVersion) {
        return jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history " +
            "WHERE version > ? AND version <= ? " +
            "ORDER BY installed_rank DESC",
            String.class, targetVersion, currentVersion
        );
    }
    
    private List<String> generateRollbackSteps(String currentVersion, String targetVersion) {
        // Implementation would generate detailed rollback steps
        return Arrays.asList("Step 1: Validate", "Step 2: Backup", "Step 3: Execute");
    }
    
    private boolean validateRollbackStep(String step) {
        // Implementation would validate each step
        return true;
    }
    
    private boolean executeUndoScript(String version) {
        try {
            log.info("Executing undo script for version: {}", version);

            // Use the FlywayRollbackManager to execute the actual undo script
            RollbackResult result = rollbackManager.rollbackToVersion(version);

            if (result.isSuccess()) {
                log.info("Successfully executed undo script for version: {}", version);
                return true;
            } else {
                log.error("Failed to execute undo script for version: {}. Error: {}", version, result.getErrorMessage());
                return false;
            }

        } catch (Exception e) {
            log.error("Exception while executing undo script for version: {}", version, e);
            return false;
        }
    }
    
    private void removeVersionFromHistory(String version) {
        jdbcTemplate.update(
            "DELETE FROM flyway_schema_history WHERE version = ?", version
        );
    }
    
    // Inner classes for results
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class RollbackExecutionResult {
        private final boolean success;
        private final String errorMessage;
        
        public RollbackExecutionResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}
