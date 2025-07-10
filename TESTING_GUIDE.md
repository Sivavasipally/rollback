# 🧪 Comprehensive Testing Guide for Flyway Rollback Framework

## 📋 Overview

This guide provides detailed steps to test the Flyway Rollback Framework using H2 database. The framework includes automated unit tests and manual testing procedures to verify all rollback capabilities.

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- Spring Boot 3.2.0
- H2 Database (included)

### 1. Start the Application

```bash
# Navigate to project directory
cd /path/to/flyway-rollback-project

# Clean and compile
mvn clean compile

# Start the application
mvn spring-boot:run
```

### 2. Verify Application Startup

- **Application URL**: http://localhost:8080
- **H2 Console**: http://localhost:8080/h2-console
- **H2 Connection**:
  - JDBC URL: `jdbc:h2:mem:testdb`
  - Username: `sa`
  - Password: `password`

## 🔬 Automated Testing

### Run Integration Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=FlywayRollbackIntegrationTest

# Run with detailed output
mvn test -Dtest=FlywayRollbackIntegrationTest -Dspring.profiles.active=test
```

### Test Coverage

The automated tests cover:

1. ✅ **Initial Database State Verification**
2. ✅ **Database Snapshot Creation**
3. ✅ **Basic Rollback Operations**
4. ✅ **Production Rollback with Safety Checks**
5. ✅ **Dry Run Rollback Testing**
6. ✅ **Safety Guard Validations**
7. ✅ **Rollback Audit and History**

## 🛠️ Manual Testing

### Step 1: Verify Initial Database State

1. **Open H2 Console**: http://localhost:8080/h2-console
2. **Connect** with credentials above
3. **Run queries**:

```sql
-- Check Flyway schema history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Verify tables exist
SHOW TABLES;

-- Check data
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM user_profiles;
SELECT COUNT(*) FROM audit_logs;

-- View sample data
SELECT * FROM users;
SELECT * FROM roles;
SELECT * FROM user_roles;
```

**Expected Results**:
- 3 migrations applied (V1.0.0, V1.0.1, V1.0.2)
- 5 tables: users, roles, user_roles, user_profiles, audit_logs
- Sample data in all tables

### Step 2: Test API Endpoints

#### 2.1 Check Current Version

```bash
curl -X GET http://localhost:8080/api/v1/rollback/version/current
```

**Expected Response**:
```json
{
  "currentVersion": "1.0.2",
  "isProduction": false,
  "activeConnections": 1,
  "isBusinessHours": true,
  "timestamp": 1702123456789
}
```

#### 2.2 Validate Rollback Request

```bash
curl -X POST http://localhost:8080/api/v1/rollback/validate \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "1.0.1",
    "reason": "Testing rollback validation"
  }'
```

**Expected Response**:
```json
{
  "valid": true,
  "issues": "",
  "currentVersion": "1.0.2",
  "targetVersion": "1.0.1",
  "isProduction": false,
  "isBusinessHours": true
}
```

#### 2.3 Execute Basic Rollback

```bash
curl -X POST http://localhost:8080/api/v1/rollback/basic \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "1.0.1",
    "reason": "Testing basic rollback - removing audit_logs table"
  }'
```

**Expected Response**:
```json
{
  "success": true,
  "rollbackId": "rollback_123456789",
  "targetVersion": "1.0.1",
  "snapshotId": "snapshot_123456789",
  "timestamp": "2024-12-10T14:30:22",
  "resultType": "SUCCESS"
}
```

**Verification in H2 Console**:
```sql
-- Check version
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Verify audit_logs table is gone
SHOW TABLES;

-- This should fail
SELECT COUNT(*) FROM audit_logs;
```

#### 2.4 Execute Production Rollback (Dry Run)

```bash
curl -X POST http://localhost:8080/api/v1/rollback/execute \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "1.0.0",
    "reason": "Testing production rollback dry run",
    "dryRun": true,
    "productionApproved": true,
    "approvedBy": "test@example.com",
    "ticketNumber": "TEST-001"
  }'
```

**Expected Response**:
```json
{
  "success": true,
  "rollbackId": "rollback_123456790",
  "targetVersion": "1.0.0",
  "resultType": "DRY_RUN_SUCCESS",
  "executedSteps": [
    "Step 1: Validate",
    "Step 2: Backup",
    "Step 3: Execute"
  ]
}
```

#### 2.5 Execute Actual Production Rollback

```bash
curl -X POST http://localhost:8080/api/v1/rollback/execute \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "1.0.0",
    "reason": "Testing production rollback to initial state",
    "dryRun": false,
    "productionApproved": true,
    "approvedBy": "test@example.com",
    "ticketNumber": "TEST-002",
    "createSnapshot": true
  }'
```

**Verification in H2 Console**:
```sql
-- Check final state - should only have initial tables
SHOW TABLES;

-- These should exist
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM roles;
SELECT COUNT(*) FROM user_roles;

-- These should fail (tables don't exist)
SELECT COUNT(*) FROM user_profiles;
SELECT COUNT(*) FROM audit_logs;
```

### Step 3: Test System Health and Monitoring

#### 3.1 Check System Health

```bash
curl -X GET http://localhost:8080/api/v1/rollback/health
```

#### 3.2 Check Rollback History

```bash
curl -X GET http://localhost:8080/api/v1/rollback/history
```

### Step 4: Verify Snapshot Creation

```bash
# Check snapshot directories
ls -la ./flyway-snapshots/

# On Windows
dir flyway-snapshots
```

**Expected**: Snapshot directories with timestamps

## 🧪 Advanced Testing Scenarios

### Scenario 1: Test Data Loss Detection

1. **Add custom data**:
```sql
INSERT INTO users (username, email, first_name, last_name) 
VALUES ('testuser2', 'test2@example.com', 'Test', 'User2');
```

2. **Attempt rollback** and verify data loss warning

### Scenario 2: Test Emergency Rollback

```bash
curl -X POST http://localhost:8080/api/v1/rollback/emergency \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "1.0.0",
    "reason": "Emergency rollback test",
    "emergencyRollback": true,
    "approvedBy": "emergency@example.com"
  }'
```

### Scenario 3: Test Rollback Failure Recovery

1. **Simulate failure** by modifying undo script
2. **Attempt rollback**
3. **Verify recovery mechanism**

## 📊 Test Results Verification

### Success Criteria

| Test Case | Expected Result | Verification Method |
|-----------|----------------|-------------------|
| Initial State | All 3 migrations applied | H2 Console: `SELECT * FROM flyway_schema_history` |
| Basic Rollback | Version 1.0.1, no audit_logs | H2 Console: `SHOW TABLES` |
| Production Rollback | Version 1.0.0, only initial tables | H2 Console: Table count verification |
| Dry Run | No actual changes made | Database state unchanged |
| Snapshot Creation | Snapshot files created | File system check |
| Audit Trail | Rollback operations logged | Check audit table |

### Performance Benchmarks

- **Basic Rollback**: < 5 seconds
- **Production Rollback**: < 10 seconds
- **Snapshot Creation**: < 15 seconds
- **Dry Run**: < 3 seconds

## 🐛 Troubleshooting

### Common Issues

#### 1. Application Won't Start
```bash
# Check Java version
java -version

# Check Maven version
mvn -version

# Clean and rebuild
mvn clean compile
```

#### 2. H2 Console Not Accessible
- Verify `spring.h2.console.enabled=true`
- Check application.properties configuration
- Ensure application is running on port 8080

#### 3. Migration Failures
```sql
-- Check Flyway schema history
SELECT * FROM flyway_schema_history WHERE success = FALSE;

-- Check for table locks
SELECT * FROM INFORMATION_SCHEMA.LOCKS;
```

#### 4. Rollback Failures
- Check undo script syntax
- Verify foreign key constraints
- Check snapshot directory permissions

#### 5. API Endpoint Errors
- Verify Content-Type header
- Check JSON syntax
- Review application logs

### Debug Commands

```bash
# Enable debug logging
mvn spring-boot:run -Dlogging.level.com.example=DEBUG

# Check application health
curl http://localhost:8080/actuator/health

# View application logs
tail -f logs/application.log
```

## 📈 Performance Testing

### Load Testing

```bash
# Install Apache Bench (if not available)
# Ubuntu: sudo apt-get install apache2-utils
# macOS: brew install httpie

# Test concurrent rollback requests
ab -n 10 -c 2 -T 'application/json' -p rollback-request.json \
   http://localhost:8080/api/v1/rollback/validate
```

### Memory Usage Monitoring

```bash
# Monitor JVM memory
jstat -gc -t $(pgrep -f spring-boot) 5s

# Check heap usage
jmap -histo $(pgrep -f spring-boot)
```

## ✅ Test Completion Checklist

- [ ] Application starts successfully
- [ ] H2 Console accessible
- [ ] All automated tests pass
- [ ] Basic rollback works
- [ ] Production rollback works
- [ ] Dry run functionality works
- [ ] Snapshots are created
- [ ] Audit trail is maintained
- [ ] API endpoints respond correctly
- [ ] Error handling works properly
- [ ] Performance meets benchmarks

## 🎯 Next Steps

After successful testing:

1. **Review logs** for any warnings or errors
2. **Document any issues** found during testing
3. **Prepare for staging environment** testing
4. **Configure production settings**
5. **Set up monitoring and alerting**

---

**Note**: This testing framework provides comprehensive coverage of all rollback scenarios. For production deployment, ensure you have proper backup procedures and approval workflows in place.
