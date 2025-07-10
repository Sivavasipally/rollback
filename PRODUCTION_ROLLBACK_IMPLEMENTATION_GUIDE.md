# Production-Grade Flyway Rollback Framework for Spring Boot

## Overview

This comprehensive framework provides enterprise-grade database rollback capabilities for Spring Boot applications using Flyway. It handles DML/DDL/DQL/DCL/TCL operations with production-safe mechanisms for real-time data preservation and structure rollback.

## Key Features

### 🔒 Production Safety
- **Multi-layer validation** before rollback execution
- **Business hours protection** with emergency override
- **Data loss detection** and prevention
- **Active transaction monitoring**
- **Database integrity verification**

### 📊 Comprehensive Auditing
- **Complete rollback history** tracking
- **Detailed operation logging**
- **External notification support** (Email, Slack)
- **Compliance reporting**

### 💾 Advanced Snapshot Management
- **Multi-database support** (MySQL, PostgreSQL, Oracle, SQL Server, H2)
- **Parallel snapshot creation** for large databases
- **Compressed storage** with configurable retention
- **Point-in-time recovery** capabilities

### 🛡️ Safety Guards
- **Environment detection** (Production vs Development)
- **Foreign key constraint validation**
- **Orphaned record detection**
- **Critical table protection**

## Architecture Components

### Core Classes

1. **ProductionRollbackManager** - Main orchestrator with safety mechanisms
2. **DatabaseSnapshotManager** - Handles data preservation and restoration
3. **SafetyGuardService** - Implements protection mechanisms
4. **RollbackAuditService** - Comprehensive audit and notification
5. **FlywayRollbackProperties** - Configuration management

### REST API Endpoints

```
POST /api/v1/rollback/execute     - Production rollback with full safety
POST /api/v1/rollback/basic       - Basic rollback for development
POST /api/v1/rollback/validate    - Validate rollback without execution
POST /api/v1/rollback/emergency   - Emergency rollback with minimal validation
GET  /api/v1/rollback/version/current - Get current database version
GET  /api/v1/rollback/history     - Get rollback history
GET  /api/v1/rollback/health      - System health for rollback operations
```

## Configuration

### Application Properties

```yaml
flyway:
  rollback:
    enabled: true
    auto-rollback-on-failure: false
    require-approval: true
    rollback-timeout-minutes: 30
    validate-before-rollback: true
    validate-after-rollback: true
    
    snapshot:
      enabled: true
      storage-path: "/var/lib/flyway-snapshots"
      retention-days: 30
      compress-snapshots: true
      include-data: true
      include-schema: true
      parallel-threads: 4
      snapshot-format: "AUTO"
    
    audit:
      enabled: true
      table-name: "flyway_rollback_audit"
      log-to-file: true
      log-file-path: "logs/rollback-audit.log"
      detailed-logging: true
    
    safety:
      enabled: true
      allow-business-hours-rollback: false
      require-ticket-number: true
      require-approver: true
      check-data-loss: true
      check-foreign-keys: true
      check-orphaned-records: true
      max-connections-for-rollback: 50
      protected-tables:
        - "critical_user_data"
        - "financial_transactions"
      protected-schemas:
        - "production_schema"
    
    notification:
      enabled: true
      email-notifications: true
      email-recipients: "dba-team@company.com,ops-team@company.com"
      slack-notifications: true
      slack-webhook: "https://hooks.slack.com/services/..."
      notify-on-success: true
      notify-on-failure: true
      notify-on-dry-run: false
    
    recovery:
      enabled: true
      auto-recovery-on-failure: true
      recovery-attempts: 3
      recovery-delay-seconds: 5
      restore-from-snapshot: true
```

## Implementation Steps

### 1. Flyway Teams/Enterprise Setup

**Important**: For production rollback capabilities, you need **Flyway Teams** or **Flyway Enterprise**.

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>10.0.0</version>
</dependency>
<!-- For Teams/Enterprise features -->
<dependency>
    <groupId>com.redgate.flyway</groupId>
    <artifactId>flyway-teams</artifactId>
    <version>10.0.0</version>
</dependency>
```

### 2. Migration Script Structure

```
src/main/resources/db/migration/
├── V1.0.0__Initial_schema.sql
├── U1.0.0__Initial_schema_undo.sql
├── V1.0.1__Add_users_table.sql
├── U1.0.1__Add_users_table_undo.sql
├── V1.0.2__Add_user_data.sql
└── U1.0.2__Add_user_data_undo.sql
```

### 3. Undo Script Best Practices

#### DDL Rollback Example
```sql
-- V1.0.1__Add_users_table.sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- U1.0.1__Add_users_table_undo.sql
DROP TABLE IF EXISTS users;
```

#### DML Rollback Example
```sql
-- V1.0.2__Add_user_data.sql
INSERT INTO users (username, email) VALUES 
('admin', 'admin@company.com'),
('user1', 'user1@company.com');

-- U1.0.2__Add_user_data_undo.sql
DELETE FROM users WHERE username IN ('admin', 'user1');
```

#### Complex Data Migration Rollback
```sql
-- V1.0.3__Migrate_user_profiles.sql
-- Create backup table first
CREATE TABLE user_profiles_backup AS SELECT * FROM user_profiles;

-- Perform migration
UPDATE user_profiles SET 
    full_name = CONCAT(first_name, ' ', last_name),
    updated_at = NOW()
WHERE full_name IS NULL;

-- U1.0.3__Migrate_user_profiles_undo.sql
-- Restore from backup
DELETE FROM user_profiles;
INSERT INTO user_profiles SELECT * FROM user_profiles_backup;
DROP TABLE user_profiles_backup;
```

### 4. Security Configuration

```java
@Configuration
@EnableWebSecurity
public class RollbackSecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/rollback/execute").hasRole("DBA")
                .requestMatchers("/api/v1/rollback/emergency").hasRole("EMERGENCY_RESPONDER")
                .requestMatchers("/api/v1/rollback/basic").hasAnyRole("DEVELOPER", "DBA")
                .requestMatchers("/api/v1/rollback/**").hasRole("USER")
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        
        return http.build();
    }
}
```

## Usage Examples

### 1. Production Rollback Request

```bash
curl -X POST http://localhost:8080/api/v1/rollback/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "targetVersion": "1.0.5",
    "reason": "Critical bug in version 1.0.6 causing data corruption",
    "productionApproved": true,
    "approvedBy": "john.doe@company.com",
    "ticketNumber": "INCIDENT-12345",
    "rollbackType": "EMERGENCY",
    "dryRun": false,
    "createSnapshot": true
  }'
```

### 2. Dry Run Validation

```bash
curl -X POST http://localhost:8080/api/v1/rollback/validate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "targetVersion": "1.0.5",
    "reason": "Testing rollback feasibility"
  }'
```

### 3. Emergency Rollback

```bash
curl -X POST http://localhost:8080/api/v1/rollback/emergency \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "targetVersion": "1.0.4",
    "reason": "Production outage - immediate rollback required",
    "emergencyRollback": true,
    "approvedBy": "emergency.responder@company.com"
  }'
```

## Database Support Matrix

| Database | Snapshot Support | Undo Scripts | Parallel Processing | Notes |
|----------|------------------|--------------|-------------------|-------|
| MySQL    | ✅ Full          | ✅ Yes       | ✅ Yes            | Recommended |
| PostgreSQL | ✅ Full        | ✅ Yes       | ✅ Yes            | Excellent support |
| Oracle   | ✅ Full          | ✅ Yes       | ✅ Yes            | Enterprise ready |
| SQL Server | ✅ Full        | ✅ Yes       | ✅ Yes            | Full compatibility |
| H2       | ✅ Limited       | ✅ Yes       | ❌ No             | Development only |

## Monitoring and Alerting

### Metrics Exposed

- `rollback.executions.total` - Total rollback executions
- `rollback.executions.success` - Successful rollbacks
- `rollback.executions.failed` - Failed rollbacks
- `rollback.duration.seconds` - Rollback execution time
- `rollback.snapshots.created` - Snapshots created
- `rollback.data.loss.detected` - Data loss detections

### Health Checks

```java
@Component
public class RollbackHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        // Check rollback system health
        return Health.up()
            .withDetail("snapshotStorage", "Available")
            .withDetail("auditSystem", "Operational")
            .withDetail("safetyGuards", "Active")
            .build();
    }
}
```

## Best Practices

### 1. Migration Design
- **Always create undo scripts** alongside migration scripts
- **Test undo scripts** in development environments
- **Use transactions** where possible
- **Backup critical data** before destructive operations

### 2. Production Deployment
- **Require approval** for production rollbacks
- **Schedule during maintenance windows**
- **Monitor system health** before and after
- **Have recovery plans** ready

### 3. Data Preservation
- **Create snapshots** before major changes
- **Verify data integrity** after rollbacks
- **Maintain audit trails** for compliance
- **Test restore procedures** regularly

## Troubleshooting

### Common Issues

1. **Snapshot Creation Fails**
   - Check disk space in snapshot directory
   - Verify database permissions
   - Review parallel thread configuration

2. **Rollback Validation Errors**
   - Check foreign key constraints
   - Verify undo script syntax
   - Review data dependencies

3. **Performance Issues**
   - Reduce parallel threads for snapshots
   - Optimize undo scripts
   - Consider maintenance windows

### Recovery Procedures

1. **Failed Rollback Recovery**
   ```bash
   # Restore from snapshot
   curl -X POST /api/v1/rollback/restore \
     -d '{"snapshotId": "prod_rollback_20241210_143022"}'
   ```

2. **Manual Intervention**
   ```sql
   -- Check rollback status
   SELECT * FROM flyway_rollback_audit 
   WHERE rollback_id = 'your-rollback-id';
   
   -- Manual cleanup if needed
   DELETE FROM flyway_schema_history 
   WHERE version > 'target-version';
   ```

## Conclusion

This framework provides enterprise-grade rollback capabilities that handle the complexities of production database operations while maintaining data integrity and system safety. The multi-layered approach ensures that rollbacks are performed safely with comprehensive audit trails and recovery mechanisms.

For production use, ensure you have:
- ✅ Flyway Teams/Enterprise license
- ✅ Comprehensive testing in staging
- ✅ Proper security configurations
- ✅ Monitoring and alerting setup
- ✅ Trained operations team
