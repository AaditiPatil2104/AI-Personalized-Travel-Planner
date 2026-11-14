@echo off
setlocal
cd /d "%~dp0"

title YatraAI - Personalized Travel Planner

echo ========================================================
echo        YATRAAI - PERSONALIZED TRAVEL PLANNER
 echo ========================================================
echo.
echo Project folder: %CD%
echo.

echo Checking Java...
java -version
if errorlevel 1 (
    echo ERROR: Java was not found. Please install Java 17 or newer.
    pause
    exit /b 1
)

if not exist "target\ai-travel-planner-1.0.0.jar" (
    echo ERROR: Application JAR not found.
    pause
    exit /b 1
)

echo Starting Spring Boot application...
echo.
start "YatraAI Browser" powershell -NoProfile -WindowStyle Hidden -Command "while(-not (Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet)){Start-Sleep -Seconds 1}; Start-Process 'http://localhost:8080'"
java -jar "target\ai-travel-planner-1.0.0.jar"
set "EXITCODE=%errorlevel%"
echo.
echo ========================================================
if not "%EXITCODE%"=="0" echo APPLICATION STOPPED WITH AN ERROR.
if "%EXITCODE%"=="0" echo APPLICATION STOPPED.
echo ========================================================
pause
exit /b %EXITCODE%
