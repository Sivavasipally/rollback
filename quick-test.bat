@echo off
echo ==========================================
echo Quick Compilation and Startup Test
echo ==========================================

echo Step 1: Cleaning and compiling...
call mvn clean compile
if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

echo.
echo SUCCESS: Compilation completed!
echo.

echo Step 2: Starting application...
echo Note: This will start the Spring Boot application
echo Press Ctrl+C to stop the application when testing is complete
echo.
echo After startup, you can:
echo 1. Access H2 Console: http://localhost:8080/h2-console
echo    - JDBC URL: jdbc:h2:mem:testdb
echo    - Username: sa
echo    - Password: password
echo.
echo 2. Test API: curl -X GET http://localhost:8080/api/v1/rollback/version/current
echo.
pause

call mvn spring-boot:run
