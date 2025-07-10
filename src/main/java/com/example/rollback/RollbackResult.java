package com.example.rollback;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RollbackResult {
    private boolean success;
    private String rollbackId;
    private String targetVersion;
    private String snapshotId;
    private String errorMessage;
}
