#!/bin/bash

# Manual Testing Script for Flyway Rollback Framework
# This script provides step-by-step manual testing procedures

echo "=========================================="
echo "Flyway Rollback Framework Manual Testing"
echo "=========================================="

BASE_URL="http://localhost:8080"
API_BASE="$BASE_URL/api/v1/rollback"

echo ""
echo "Prerequisites:"
echo "1. Start the Spring Boot application: mvn spring-boot:run"
echo "2. H2 Console available at: $BASE_URL/h2-console"
echo "3. JDBC URL: jdbc:h2:mem:testdb"
echo "4. Username: sa, Password: password"
echo ""

echo "=========================================="
echo "TEST 1: Check Current Database Version"
echo "=========================================="
echo "Command:"
echo "curl -X GET $API_BASE/version/current"
echo ""
echo "Expected: Should return current version (1.0.2) and system info"
echo ""

echo "=========================================="
echo "TEST 2: Validate Rollback Request"
echo "=========================================="
echo "Command:"
cat << 'EOF'
curl -X POST http://localhost:8080/api/v1/rollback/validate \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "1.0.1",
    "reason": "Testing rollback validation"
  }'
EOF
echo ""
echo "Expected: Should return validation result with details"
echo ""

echo "=========================================="
echo "TEST 3: Execute Basic Rollback"
echo "=========================================="
echo "Command:"
cat << 'EOF'
curl -X POST http://localhost:8080/api/v1/rollback/basic \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "1.0.1",
    "reason": "Testing basic rollback to remove audit_logs table"
  }'
EOF
echo ""
echo "Expected: Should rollback to version 1.0.1, removing audit_logs table"
echo "Verify in H2 Console: audit_logs table should not exist"
echo ""

echo "=========================================="
echo "TEST 4: Check Database State After Rollback"
echo "=========================================="
echo "H2 Console Queries to run:"
echo "1. SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
echo "2. SHOW TABLES;"
echo "3. SELECT COUNT(*) FROM users;"
echo "4. SELECT COUNT(*) FROM user_profiles;"
echo "5. Try: SELECT COUNT(*) FROM audit_logs; (should fail)"
echo ""

echo "=========================================="
echo "TEST 5: Execute Production Rollback with Dry Run"
echo "=========================================="
echo "Command:"
cat << 'EOF'
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
EOF
echo ""
echo "Expected: Should return dry run results without making changes"
echo ""

echo "=========================================="
echo "TEST 6: Execute Actual Production Rollback"
echo "=========================================="
echo "Command:"
cat << 'EOF'
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
EOF
echo ""
echo "Expected: Should rollback to version 1.0.0, only users, roles, user_roles tables should exist"
echo ""

echo "=========================================="
echo "TEST 7: Check System Health"
echo "=========================================="
echo "Command:"
echo "curl -X GET $API_BASE/health"
echo ""
echo "Expected: Should return system health information"
echo ""

echo "=========================================="
echo "TEST 8: Check Rollback History"
echo "=========================================="
echo "Command:"
echo "curl -X GET $API_BASE/history"
echo ""
echo "Expected: Should return rollback history (if audit table exists)"
echo ""

echo "=========================================="
echo "TEST 9: Verify Final Database State"
echo "=========================================="
echo "H2 Console Queries to run after all rollbacks:"
echo "1. SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
echo "2. SHOW TABLES;"
echo "3. SELECT * FROM users;"
echo "4. SELECT * FROM roles;"
echo "5. SELECT * FROM user_roles;"
echo "6. Try: SELECT * FROM user_profiles; (should fail after rollback to 1.0.0)"
echo "7. Try: SELECT * FROM audit_logs; (should fail after rollback to 1.0.0)"
echo ""

echo "=========================================="
echo "TEST 10: Check Snapshot Files"
echo "=========================================="
echo "Commands:"
echo "ls -la ./flyway-snapshots/"
echo "ls -la ./target/test-snapshots/ (if running tests)"
echo ""
echo "Expected: Should see snapshot directories created during rollbacks"
echo ""

echo "=========================================="
echo "TROUBLESHOOTING"
echo "=========================================="
echo "If tests fail, check:"
echo "1. Application logs for detailed error messages"
echo "2. H2 Console to verify database state"
echo "3. Snapshot directory permissions and content"
echo "4. Flyway schema history table for migration status"
echo ""
echo "Common Issues:"
echo "- Undo scripts not found: Check classpath:db/migration directory"
echo "- Permission errors: Check snapshot directory write permissions"
echo "- Version conflicts: Check flyway_schema_history table"
echo ""

echo "=========================================="
echo "Manual Testing Script Complete"
echo "=========================================="
