@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
if errorlevel 1 (
  echo.
  echo CLYVR could not launch. Ensure JDK 21 is installed and on PATH.
  pause
)
