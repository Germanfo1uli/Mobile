@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0local-lab5.ps1" -Action Start
if errorlevel 1 pause
