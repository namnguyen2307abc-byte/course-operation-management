@echo off
chcp 65001 > nul
echo ======================================================================
echo    KHỞI ĐỘNG HỆ THỐNG QUẢN LÝ HỌC PHÍ & THU NGÂN TALENT MANAGEMENT
echo ======================================================================
echo.

set CURRENT_DIR=%~dp0

echo [1/3] Đang khởi động Backend Spring Boot (Port 8080)...
start "Backend Spring Boot - Port 8080" cmd /k "cd /d ""%CURRENT_DIR%com_be"" && mvnw.cmd spring-boot:run"

timeout /t 3 /nobreak > nul

echo [2/3] Đang khởi động Ngrok Webhook Tunnel...
if exist "D:\ngock\ngrok.exe" (
    start "Ngrok Webhook Tunnel" cmd /k "D:\ngock\ngrok.exe http 8080 --url impart-agenda-dispatch.ngrok-free.dev"
) else (
    echo [CẢNH BÁO] Không tìm thấy D:\ngock\ngrok.exe. Vui lòng kiểm tra lại đường dẫn ngrok!
)

timeout /t 2 /nobreak > nul

echo [3/3] Đang khởi động Frontend Web Server (Port 5500)...
start "Frontend Web Server - Port 5500" cmd /k "cd /d ""%CURRENT_DIR%com_fe"" && npx live-server --port=5500"

echo.
echo ======================================================================
echo   TẤT CẢ DỊCH VỤ ĐÃ ĐƯỢC BẬT!
echo   - Backend:  http://localhost:8080/swagger-ui.html
echo   - Webhook:  https://impart-agenda-dispatch.ngrok-free.dev
echo   - Frontend: http://127.0.0.1:5500/pages/tuition-payment.html
echo ======================================================================
timeout /t 5
