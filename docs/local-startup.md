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

## Chọn ảnh làm đóng hộp chấm điểm khi dùng Live Server

Live Server có thể theo dõi cả thư mục backend. Khi upload tạo file trong `com_be/uploads`, Live Server reload trang và làm mất bản nháp chấm điểm dù API upload đã thành công.

Trong `.vscode/settings.json` của workspace, giữ các thiết lập hiện có và bổ sung các mục sau vào `liveServer.settings.ignoreFiles`:

```json
{
  "liveServer.settings.ignoreFiles": [
    ".vscode/**", "**/*.scss", "**/*.sass", "**/*.ts", "node_modules/**",
    "**/uploads/**", "com_be/uploads", "uploads", ".local-run", "**/target/**", "**/*.log"
  ]
}
```

Dừng rồi mở lại Live Server để nạp cấu hình, sau đó refresh trình duyệt. Thư mục `.vscode` là cấu hình local và không được Git theo dõi. File upload là dữ liệu runtime, không đưa lên Git. Frontend chỉ báo tải thành công khi backend trả URL file thực tế; khi lỗi vẫn giữ nhận xét và điểm đang nhập, không tạo đường dẫn dự phòng giả.
