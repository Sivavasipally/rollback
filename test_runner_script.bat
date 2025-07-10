@echo off
echo ==========================================
echo Flyway Rollback Comprehensive Test Suite
echo ==========================================

echo.
echo Step 1: Cleaning previous test artifacts...
if exist flyway-snapshots rmdir /s /q flyway-snapshots
if exist logs rmdir /s /q logs
if exist target\test-snapshots rmdir /s /q target\test-snapshots

echo.
echo Step 2: Creating required directories...
mkdir flyway-snapshots 2>nul
mkdir logs 2>nul
mkdir target\test-snapshots 2>nul

echo.
echo Step 3: Compiling the application...
call mvn clean compile
if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

echo.
echo Step 4: Running unit tests...
call mvn test -Dtest=BasicConfigurationTest,SimpleApplicationTest
if errorlevel 1 (
    echo WARNING: Basic tests failed, but continuing...
)

echo.
echo Step 5: Running Flyway rollback integration tests...
call mvn test -Dtest=FlywayRollbackIntegrationTest
if errorlevel 1 (
    echo WARNING: Integration tests encountered issues
)

echo.
echo Step 6: Running comprehensive rollback tests...
call mvn test -Dtest=ComprehensiveRollbackTest
if errorlevel 1 (
    echo WARNING: Comprehensive tests encountered issues
)

echo.
echo Step 7: Running edge case tests...
call mvn test -Dtest=RollbackEdgeCaseTest
if errorlevel 1 (
    echo WARNING: Edge case tests encountered issues
)

echo.
echo Step 8: Generating test report...
call mvn surefire-report:report-only
echo Test report generated in: target\site\surefire-report.html

echo.
echo ==========================================
echo Test Suite Execution Complete!
echo ==========================================
echo.
echo Summary:
echo - Check target\surefire-reports for detailed test results
echo - Check logs\rollback-audit.log for rollback audit trail
echo - Check flyway-snapshots for created snapshots
echo.
echo To run specific tests:
echo   mvn test -Dtest=TestClassName
echo.
echo To run with debug output:
echo   mvn test -X
echo.
pause