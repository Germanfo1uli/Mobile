@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0local-lab5.ps1" -Action Stop
if errorlevel 1 pause
