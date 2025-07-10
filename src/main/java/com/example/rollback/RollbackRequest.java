// src/main/java/com/example/rollback/RollbackRequest.java
package com.example.rollback;

import lombok.Data;

@Data
public class RollbackRequest {
    private String targetVersion;
    private boolean dryRun;
    private String reason;
}

// src/main/java/com/example/rollback/RollbackResult.java
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