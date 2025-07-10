// src/main/java/com/example/rollback/RollbackRequest.java
package com.example.rollback;

import lombok.Data;

@Data
public class RollbackRequest {
    private String targetVersion;
    private boolean dryRun;
    private String reason;

    // Production safety flags
    private boolean productionApproved = false;
    private boolean emergencyRollback = false;
    private boolean forceDataLoss = false;

    // Approval and tracking
    private String approvedBy;
    private String ticketNumber;
    private String rollbackType = "STANDARD"; // STANDARD, EMERGENCY, HOTFIX

    // Timeout and safety settings
    private int timeoutMinutes = 30;
    private boolean skipValidation = false;
    private boolean createSnapshot = true;
}

