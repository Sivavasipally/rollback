# Manual Testing Script for Flyway Rollback Framework (PowerShell)
# This script provides step-by-step manual testing procedures for Windows

Write-Host "==========================================" -ForegroundColor Green
Write-Host "Flyway Rollback Framework Manual Testing" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green

$BaseUrl = "http://localhost:8080"
$ApiBase = "$BaseUrl/api/v1/rollback"

Write-Host ""
Write-Host "Prerequisites:" -ForegroundColor Yellow
Write-Host "1. Start the Spring Boot application: mvn spring-boot:run"
Write-Host "2. H2 Console available at: $BaseUrl/h2-console"
Write-Host "3. JDBC URL: jdbc:h2:mem:testdb"
Write-Host "4. Username: sa, Password: password"
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 1: Check Current Database Version" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Command:" -ForegroundColor Yellow
Write-Host "Invoke-RestMethod -Uri '$ApiBase/version/current' -Method Get" -ForegroundColor White
Write-Host ""
Write-Host "Expected: Should return current version (1.0.2) and system info" -ForegroundColor Green
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 2: Validate Rollback Request" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Command:" -ForegroundColor Yellow
$validateBody = @{
    targetVersion = "1.0.1"
    reason = "Testing rollback validation"
} | ConvertTo-Json

Write-Host "Invoke-RestMethod -Uri '$ApiBase/validate' -Method Post -ContentType 'application/json' -Body '$validateBody'" -ForegroundColor White
Write-Host ""
Write-Host "Expected: Should return validation result with details" -ForegroundColor Green
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 3: Execute Basic Rollback" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Command:" -ForegroundColor Yellow
$basicRollbackBody = @{
    targetVersion = "1.0.1"
    reason = "Testing basic rollback to remove audit_logs table"
} | ConvertTo-Json

Write-Host "Invoke-RestMethod -Uri '$ApiBase/basic' -Method Post -ContentType 'application/json' -Body '$basicRollbackBody'" -ForegroundColor White
Write-Host ""
Write-Host "Expected: Should rollback to version 1.0.1, removing audit_logs table" -ForegroundColor Green
Write-Host "Verify in H2 Console: audit_logs table should not exist" -ForegroundColor Green
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 4: Check Database State After Rollback" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "H2 Console Queries to run:" -ForegroundColor Yellow
Write-Host "1. SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
Write-Host "2. SHOW TABLES;"
Write-Host "3. SELECT COUNT(*) FROM users;"
Write-Host "4. SELECT COUNT(*) FROM user_profiles;"
Write-Host "5. Try: SELECT COUNT(*) FROM audit_logs; (should fail)"
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 5: Execute Production Rollback with Dry Run" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Command:" -ForegroundColor Yellow
$dryRunBody = @{
    targetVersion = "1.0.0"
    reason = "Testing production rollback dry run"
    dryRun = $true
    productionApproved = $true
    approvedBy = "test@example.com"
    ticketNumber = "TEST-001"
} | ConvertTo-Json

Write-Host "Invoke-RestMethod -Uri '$ApiBase/execute' -Method Post -ContentType 'application/json' -Body '$dryRunBody'" -ForegroundColor White
Write-Host ""
Write-Host "Expected: Should return dry run results without making changes" -ForegroundColor Green
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 6: Execute Actual Production Rollback" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Command:" -ForegroundColor Yellow
$prodRollbackBody = @{
    targetVersion = "1.0.0"
    reason = "Testing production rollback to initial state"
    dryRun = $false
    productionApproved = $true
    approvedBy = "test@example.com"
    ticketNumber = "TEST-002"
    createSnapshot = $true
} | ConvertTo-Json

Write-Host "Invoke-RestMethod -Uri '$ApiBase/execute' -Method Post -ContentType 'application/json' -Body '$prodRollbackBody'" -ForegroundColor White
Write-Host ""
Write-Host "Expected: Should rollback to version 1.0.0, only users, roles, user_roles tables should exist" -ForegroundColor Green
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 7: Check System Health" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Command:" -ForegroundColor Yellow
Write-Host "Invoke-RestMethod -Uri '$ApiBase/health' -Method Get" -ForegroundColor White
Write-Host ""
Write-Host "Expected: Should return system health information" -ForegroundColor Green
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 8: Check Rollback History" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Command:" -ForegroundColor Yellow
Write-Host "Invoke-RestMethod -Uri '$ApiBase/history' -Method Get" -ForegroundColor White
Write-Host ""
Write-Host "Expected: Should return rollback history (if audit table exists)" -ForegroundColor Green
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 9: Verify Final Database State" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "H2 Console Queries to run after all rollbacks:" -ForegroundColor Yellow
Write-Host "1. SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
Write-Host "2. SHOW TABLES;"
Write-Host "3. SELECT * FROM users;"
Write-Host "4. SELECT * FROM roles;"
Write-Host "5. SELECT * FROM user_roles;"
Write-Host "6. Try: SELECT * FROM user_profiles; (should fail after rollback to 1.0.0)"
Write-Host "7. Try: SELECT * FROM audit_logs; (should fail after rollback to 1.0.0)"
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST 10: Check Snapshot Files" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Commands:" -ForegroundColor Yellow
Write-Host "Get-ChildItem -Path './flyway-snapshots/' -Recurse"
Write-Host "Get-ChildItem -Path './target/test-snapshots/' -Recurse"
Write-Host ""
Write-Host "Expected: Should see snapshot directories created during rollbacks" -ForegroundColor Green
Write-Host ""

Write-Host "==========================================" -ForegroundColor Red
Write-Host "TROUBLESHOOTING" -ForegroundColor Red
Write-Host "==========================================" -ForegroundColor Red
Write-Host "If tests fail, check:" -ForegroundColor Yellow
Write-Host "1. Application logs for detailed error messages"
Write-Host "2. H2 Console to verify database state"
Write-Host "3. Snapshot directory permissions and content"
Write-Host "4. Flyway schema history table for migration status"
Write-Host ""
Write-Host "Common Issues:" -ForegroundColor Yellow
Write-Host "- Undo scripts not found: Check classpath:db/migration directory"
Write-Host "- Permission errors: Check snapshot directory write permissions"
Write-Host "- Version conflicts: Check flyway_schema_history table"
Write-Host ""

Write-Host "==========================================" -ForegroundColor Green
Write-Host "Manual Testing Script Complete" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green

# Function to execute a test
function Invoke-RollbackTest {
    param(
        [string]$TestName,
        [string]$Uri,
        [string]$Method = "GET",
        [string]$Body = $null
    )
    
    Write-Host "Executing: $TestName" -ForegroundColor Cyan
    try {
        if ($Body) {
            $result = Invoke-RestMethod -Uri $Uri -Method $Method -ContentType 'application/json' -Body $Body
        } else {
            $result = Invoke-RestMethod -Uri $Uri -Method $Method
        }
        Write-Host "Success: $($result | ConvertTo-Json -Depth 3)" -ForegroundColor Green
    } catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host ""
}

Write-Host ""
Write-Host "Would you like to run automated tests? (y/n): " -NoNewline -ForegroundColor Yellow
$response = Read-Host

if ($response -eq 'y' -or $response -eq 'Y') {
    Write-Host "Running automated tests..." -ForegroundColor Green
    
    # Test 1: Check current version
    Invoke-RollbackTest -TestName "Current Version" -Uri "$ApiBase/version/current"
    
    # Test 2: Validate rollback
    $validateBody = @{
        targetVersion = "1.0.1"
        reason = "Automated test validation"
    } | ConvertTo-Json
    
    Invoke-RollbackTest -TestName "Validate Rollback" -Uri "$ApiBase/validate" -Method "POST" -Body $validateBody
    
    # Test 3: System health
    Invoke-RollbackTest -TestName "System Health" -Uri "$ApiBase/health"
    
    Write-Host "Automated tests completed!" -ForegroundColor Green
}
