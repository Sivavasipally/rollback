package com.example.rollback;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class RollbackResult {
    private boolean success;
    private String rollbackId;
    private String targetVersion;
    private String snapshotId;
    private String errorMessage;
    private LocalDateTime timestamp;
    private String resultType; // SUCCESS, FAILURE, DRY_RUN_SUCCESS
    private List<String> executedSteps;
    private long executionTimeMs;

    // Factory methods for different result types
    public static RollbackResult success(String rollbackId, String targetVersion, String snapshotId) {
        return new RollbackResult(true, rollbackId, targetVersion, snapshotId, null,
            LocalDateTime.now(), "SUCCESS", null, 0);
    }

    public static RollbackResult failure(String rollbackId, String targetVersion, String errorMessage) {
        return new RollbackResult(false, rollbackId, targetVersion, null, errorMessage,
            LocalDateTime.now(), "FAILURE", null, 0);
    }

    public static RollbackResult dryRunSuccess(String rollbackId, String targetVersion, List<String> steps) {
        return new RollbackResult(true, rollbackId, targetVersion, null, null,
            LocalDateTime.now(), "DRY_RUN_SUCCESS", steps, 0);
    }
}
