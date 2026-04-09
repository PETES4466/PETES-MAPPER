@echo off
setlocal

title PETES-MAPPER One-Click Preview

REM Move to repo root (this .bat lives in QUICK_START_WINDOWS)
cd /d "%~dp0.."

if not exist "frontend\package.json" (
  echo.
  echo [ERROR] Could not find frontend\package.json
  echo Make sure this .bat stays inside QUICK_START_WINDOWS under project root.
  pause
  exit /b 1
)

cd /d "frontend"

echo =====================================================
echo PETES-MAPPER - One Click Start
echo =====================================================
echo 1) Installing dependencies if needed...
call npm install
if errorlevel 1 (
  echo.
  echo [ERROR] npm install failed.
  echo Please install Node.js LTS from https://nodejs.org/
  pause
  exit /b 1
)

echo.
echo 2) Starting app...
echo Chrome URL: http://localhost:3000
start "" "http://localhost:3000"
call npm run start

pause
endlocal
