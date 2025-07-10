// src/main/java/com/example/rollback/RollbackRequest.java
package com.example.rollback;

import lombok.Data;

@Data
public class RollbackRequest {
    private String targetVersion;
    private boolean dryRun;
    private String reason;
}

