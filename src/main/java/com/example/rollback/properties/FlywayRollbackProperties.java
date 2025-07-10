// src/main/java/com/example/rollback/properties/FlywayRollbackProperties.java
package com.example.rollback.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "flyway.rollback")
@Data
public class FlywayRollbackProperties {

    private boolean enabled = true;
    private boolean autoRollbackOnFailure = false;
    private boolean requireApproval = true;
    private int rollbackTimeoutMinutes = 30;
    private boolean validateBeforeRollback = true;
    private boolean validateAfterRollback = true;

    private SnapshotProperties snapshot = new SnapshotProperties();
    private AuditProperties audit = new AuditProperties();
    private SafetyProperties safety = new SafetyProperties();
    private NotificationProperties notification = new NotificationProperties();
    private RecoveryProperties recovery = new RecoveryProperties();

    @Data
    public static class SnapshotProperties {
        private boolean enabled = true;
        private String storagePath = System.getProperty("user.home") + "/flyway-snapshots";
        private int retentionDays = 7;
        private boolean compressSnapshots = true;
        private boolean includeData = true;
        private boolean includeSchema = true;
        private int parallelThreads = 4;
        private String snapshotFormat = "AUTO"; // AUTO, CSV, SQL, BINARY
    }

    @Data
    public static class AuditProperties {
        private boolean enabled = true;
        private String tableName = "flyway_rollback_audit";
        private boolean logToFile = true;
        private String logFilePath = "logs/rollback-audit.log";
        private boolean detailedLogging = true;
    }

    @Data
    public static class SafetyProperties {
        private boolean enabled = true;
        private boolean allowBusinessHoursRollback = false;
        private boolean requireTicketNumber = true;
        private boolean requireApprover = true;
        private boolean checkDataLoss = true;
        private boolean checkForeignKeys = true;
        private boolean checkOrphanedRecords = true;
        private int maxConnectionsForRollback = 50;
        private List<String> protectedTables = new ArrayList<>();
        private List<String> protectedSchemas = new ArrayList<>();
    }

    @Data
    public static class NotificationProperties {
        private boolean enabled = true;
        private boolean emailNotifications = false;
        private String emailRecipients = "";
        private boolean slackNotifications = false;
        private String slackWebhook = "";
        private boolean notifyOnSuccess = true;
        private boolean notifyOnFailure = true;
        private boolean notifyOnDryRun = false;
    }

    @Data
    public static class RecoveryProperties {
        private boolean enabled = true;
        private boolean autoRecoveryOnFailure = true;
        private int recoveryAttempts = 3;
        private int recoveryDelaySeconds = 5;
        private boolean restoreFromSnapshot = true;
    }
}