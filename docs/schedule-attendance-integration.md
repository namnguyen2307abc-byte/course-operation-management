# Tích hợp lịch tuần và điểm danh

Hai nhánh `Schedule-Weekly` và `attendance_tracking` cùng bắt đầu từ commit `25d5f2b`.

- Lịch tuần dùng bốn slot: 07:00–09:00, 09:30–11:30, 13:00–15:00, 15:30–17:30. Đây là khung gợi ý; lịch cũ vẫn được hiển thị theo giờ thực tế.
- Tạo lớp tiếp tục dùng `ClassSchedulingService` chung với quản lý lớp để kiểm tra giáo viên, phòng, thiết bị, xung đột lịch và sinh buổi học.
- `AttendanceTrackingService` xử lý bảng điểm danh, quyền xem, khóa thời gian, học bù và cảnh báo. Giáo viên chỉ sửa buổi mình phụ trách, đúng ngày, sau giờ bắt đầu; giáo vụ/quản lý xử lý ngoại lệ. Buổi bị hủy không được điểm danh.
- Chỉ nhận PRESENT/ABSENT mới; giữ lịch sử trạng thái cũ. Mốc cảnh báo: ≤10% bình thường, >10% cảnh báo, ≥20% nghiêm trọng. Cảnh báo không tự động hủy ghi danh.
- Trang lớp phụ trách chỉ dành cho giáo viên/nhân viên/quản lý; phụ huynh xem con qua API điểm danh có kiểm tra quan hệ.
- Thông báo học phí được phát từ `PaymentPostingService` chung cho tiền mặt và PayOS, phản ánh số tiền từng lần và số dư. Chỉ thông báo nhập học khi đủ kỳ đầu; callback/gửi thu lặp không tạo thêm thông báo.

## Database

Chạy `database/schedule_attendance_notifications.sql` trên SQL Server sau các migration học phí hiện có (và sau `init_database.sql` khi tạo database mới). Script có thể chạy lại, chỉ thêm bảng/index `notifications`, không xóa dữ liệu. Trạng thái MISSED lưu trong cột VARCHAR hiện có của `makeup_registrations`.

## Kiểm tra

Backend: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-backend.ps1` (H2 riêng).

Frontend: chạy các `scripts/*test.cjs`, gồm `schedule-attendance-ui.test.cjs`; Chrome/Playwright dùng `CLASS_BOOTSTRAP_CACHE` trỏ tới thư mục có hai file Bootstrap 5.3.0 `bootstrap.min.css` và `bootstrap.bundle.min.js`.

Đồng bộ frontend bằng `node scripts/sync-frontend.mjs`; xác minh bằng `node scripts/sync-frontend.mjs --check`.
