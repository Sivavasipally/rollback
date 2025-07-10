// src/main/java/com/example/rollback/properties/FlywayRollbackProperties.java
package com.example.rollback.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flyway.rollback")
@Data
public class FlywayRollbackProperties {
    
    private boolean enabled = true;
    private boolean autoRollbackOnFailure = false;
    private boolean requireApproval = true;
    
    private SnapshotProperties snapshot = new SnapshotProperties();
    private AuditProperties audit = new AuditProperties();
    
    @Data
    public static class SnapshotProperties {
        private boolean enabled = true;
        private String storagePath = System.getProperty("user.home") + "/flyway-snapshots";
        private int retentionDays = 7;
    }
    
    @Data
    public static class AuditProperties {
        private boolean enabled = true;
        private String tableName = "flyway_rollback_audit";
    }
}