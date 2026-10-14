@echo off
chcp 65001 > nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-local.ps1" %*
if errorlevel 1 (
    echo Khoi dong that bai. Hay xem thong bao loi phia tren va log trong .local-run.
    pause
    exit /b 1
)
echo He thong da san sang. Mo http://127.0.0.1:5500/login.html
pause
