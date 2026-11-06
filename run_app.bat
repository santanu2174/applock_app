@echo off
title LockGuard Mobile Security App
echo ========================================================
echo Starting LockGuard Mobile Security Simulator...
echo ========================================================
start "" "C:\Program Files\Google\Chrome\Application\chrome.exe" "http://localhost:8080/simulator.html"
if %errorlevel% neq 0 (
    start "" "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" "http://localhost:8080/simulator.html"
)
pause

