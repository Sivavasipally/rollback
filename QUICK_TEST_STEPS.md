# 🚀 Quick Testing Steps for H2 Database Rollback Framework

## ⚡ Immediate Testing (5 Minutes)

### Step 1: Start the Application
```bash
# Navigate to project directory
cd d:\GenAi\rollback

# Start the application
mvn spring-boot:run
```

### Step 2: Verify Application Started
- **Application URL**: http://localhost:8080
- **H2 Console**: http://localhost:8080/h2-console
- **Expected**: Application should start without errors

### Step 3: Access H2 Console
1. Open browser: http://localhost:8080/h2-console
2. **Connection Settings**:
   - JDBC URL: `jdbc:h2:mem:testdb`
   - User Name: `sa`
   - Password: `password`
3. Click "Connect"

### Step 4: Verify Initial Database State
Run these queries in H2 Console:

```sql
-- Check Flyway migrations
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Check tables
SHOW TABLES;

-- Check data
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM user_profiles;
SELECT COUNT(*) FROM audit_logs;

-- View sample data
SELECT * FROM users;
SELECT * FROM roles;
```

**Expected Results**:
- 3 migrations: V1.0.0, V1.0.1, V1.0.2
- 5 tables: users, roles, user_roles, user_profiles, audit_logs
- Sample data in all tables

## 🧪 API Testing (10 Minutes)

### Test 1: Check Current Version
```bash
curl -X GET http://localhost:8080/api/v1/rollback/version/current
```

**Expected**: JSON response with current version "1.0.2"

### Test 2: Validate Rollback
```bash
curl -X POST http://localhost:8080/api/v1/rollback/validate ^
  -H "Content-Type: application/json" ^
  -d "{\"targetVersion\": \"1.0.1\", \"reason\": \"Test validation\"}"
```

**Expected**: Validation response showing rollback is possible

### Test 3: Execute Basic Rollback
```bash
curl -X POST http://localhost:8080/api/v1/rollback/basic ^
  -H "Content-Type: application/json" ^
  -d "{\"targetVersion\": \"1.0.1\", \"reason\": \"Remove audit_logs table\"}"
```

**Expected**: Success response

### Test 4: Verify Rollback in H2 Console
```sql
-- Check version changed
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Verify audit_logs table is gone
SHOW TABLES;

-- This should fail
SELECT COUNT(*) FROM audit_logs;
```

**Expected**: 
- Version should be 1.0.1
- audit_logs table should not exist
- Other tables should still exist

### Test 5: Execute Production Rollback
```bash
curl -X POST http://localhost:8080/api/v1/rollback/execute ^
  -H "Content-Type: application/json" ^
  -d "{\"targetVersion\": \"1.0.0\", \"reason\": \"Full rollback test\", \"dryRun\": false, \"productionApproved\": true, \"approvedBy\": \"test@example.com\"}"
```

**Expected**: Success response

### Test 6: Final Verification
```sql
-- Check final state
SHOW TABLES;

-- Should exist
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM roles;
SELECT COUNT(*) FROM user_roles;

-- Should fail (tables don't exist)
SELECT COUNT(*) FROM user_profiles;
SELECT COUNT(*) FROM audit_logs;
```

**Expected**: Only initial tables (users, roles, user_roles) should exist

## 🔍 Troubleshooting

### If Application Won't Start
1. Check Java version: `java -version` (should be 17+)
2. Check Maven: `mvn -version`
3. Clean and rebuild: `mvn clean compile`
4. Check logs for specific errors

### If H2 Console Won't Connect
1. Verify JDBC URL: `jdbc:h2:mem:testdb`
2. Check application.properties for H2 configuration
3. Ensure application is running on port 8080

### If API Calls Fail
1. Check if application is running: http://localhost:8080
2. Verify Content-Type header: `application/json`
3. Check JSON syntax in request body
4. Review application logs for errors

### If Rollback Fails
1. Check undo scripts exist in `src/main/resources/db/migration/`
2. Verify foreign key constraints
3. Check snapshot directory permissions
4. Review Flyway schema history table

## 📋 Success Checklist

- [ ] Application starts without errors
- [ ] H2 Console accessible and connects
- [ ] Initial database state verified (3 migrations, 5 tables)
- [ ] API endpoints respond correctly
- [ ] Basic rollback works (removes audit_logs table)
- [ ] Production rollback works (only initial tables remain)
- [ ] Snapshots are created (check ./flyway-snapshots/ directory)

## 🎯 Expected Timeline

- **Setup**: 2 minutes
- **Database verification**: 2 minutes
- **API testing**: 5 minutes
- **Final verification**: 1 minute
- **Total**: ~10 minutes

## 📝 Notes

- This framework provides production-grade rollback capabilities
- H2 database is used for testing only
- For production, use MySQL/PostgreSQL with proper Flyway Teams license
- All rollback operations are logged and audited
- Snapshots provide data recovery capabilities

---

**If all tests pass, your Flyway Rollback Framework is working correctly!** 🎉
