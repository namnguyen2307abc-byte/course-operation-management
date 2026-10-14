# Chạy ổn định trên Windows

Nhấn đúp `start_all.bat`. Script build backend bằng JDK 17/21, chạy Java nền và chỉ báo sẵn sàng khi API đọc được database. Frontend có sẵn ở cổng 5500 sẽ được dùng tiếp; nếu chưa có, script chạy máy chủ Node từ `com_fe`.

Mở http://127.0.0.1:5500/login.html, đăng nhập và tải lại trang chức năng.

## Sau khi sửa code hoặc pull Git

Từ thư mục gốc dự án chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -RestartBackend
```

Để chỉ bật lại bản đã build khi backend dừng:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -SkipBuild
```

Script chỉ dừng backend do chính nó tạo khi dùng `-RestartBackend`; nếu cổng bị ứng dụng khác chiếm, script báo lỗi.

## Database và log

Cấu hình kết nối lấy từ `com_be/src/main/resources/application.properties` và các biến `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` nếu đã thiết lập. Chế độ khởi động này dùng `ddl-auto=validate` và tắt dữ liệu mẫu (`app.seed.enabled=false`), phù hợp với database hiện có. Khi chưa cài schema, thực hiện bước thiết lập database trong README trước.

Log nằm trong `.local-run/backend.log` và `.local-run/backend-error.log`. Nếu build thất bại hoặc SQL Server không kết nối được, script báo lỗi thay vì thông báo hệ thống đã bật thành công.

Build phục vụ tiến trình nền nằm trong `.local-run/build`, độc lập với `com_be/target` mà IDE dùng. Runtime, PID và log được Git bỏ qua.

Ngrok cho webhook thanh toán có thể chạy riêng khi cần kiểm thử thanh toán. Các nghiệp vụ đọc dữ liệu, học vụ và lịch tuần chỉ cần backend, frontend và SQL Server.
