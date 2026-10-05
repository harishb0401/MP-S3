@echo off
echo ===============================================================================
echo    STARTING MAKKAL NAGAR MUNICIPAL GRIEVANCE PORTAL & REST API
echo ===============================================================================

echo [1/3] Compiling Java backend classes...
if not exist backend\bin mkdir backend\bin
javac -d backend/bin backend/src/model/*.java backend/src/service/*.java backend/src/test/*.java backend/src/Main.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %errorlevel%
)

echo [2/3] Running Automated System Verification Tests...
java -cp backend/bin test.SystemTest

echo [3/3] Starting Full-Stack Server on http://localhost:8080/
start http://localhost:8080/
java -cp backend/bin Main
