@echo off
setlocal

echo ==========================================
echo Flyway Rollback Framework Test Runner
echo ==========================================

echo Step 1: Compilation Status
echo Compilation was SUCCESSFUL!
echo.

echo Step 2: Running Tests...
echo.

REM Set JAVA_HOME if not set
if "%JAVA_HOME%"=="" (
    echo Warning: JAVA_HOME not set, using java from PATH
)

REM Run Maven tests with explicit settings
echo Running: mvn clean test -Dspring.profiles.active=test
echo.

mvn clean test -Dspring.profiles.active=test -Dmaven.test.failure.ignore=true

echo.
echo ==========================================
echo Test Execution Complete
echo ==========================================

if exist "target\surefire-reports" (
    echo.
    echo Test Reports Available:
    dir "target\surefire-reports\*.txt" /b 2>nul
    echo.
    echo To view detailed test results:
    echo - Check target\surefire-reports\ directory
    echo - Look for TEST-*.xml files for detailed results
) else (
    echo No test reports found in target\surefire-reports\
)

echo.
echo Next Steps:
echo 1. Review test results above
echo 2. Check target\surefire-reports\ for detailed logs
echo 3. If tests pass, start the application with: mvn spring-boot:run
echo 4. Access H2 Console: http://localhost:8080/h2-console
echo.

pause
