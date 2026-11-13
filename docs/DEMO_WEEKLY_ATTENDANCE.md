# Demo lịch tuần và theo dõi chuyên cần

File dữ liệu: `-- Seed data for reviewing Weekly S.sql`. Bộ này tạo lớp riêng `ATT-MD-DEMO-01`, không chỉnh lớp Word hoặc các lớp HD/DD/D26 đã có. Đã nạp ngày 13/11/2026. Chạy lại giữ nguyên tiến độ, không tự dời ngày hoặc xóa điểm danh.

## Tài khoản

Mật khẩu: `Test@123456`.

| Tài khoản | Mục đích |
|---|---|
| `d26_teacher_piano` | Giáo viên được phân công, xem lịch và điểm danh |
| `d26_parent_new` | Xem lịch, tiến độ và chuyên cần của bốn con |
| `d26_staff` / `d26_admin` | Xem lịch, sửa điểm danh hôm nay/quá khứ; không sửa tương lai |
| `d26_teacher_other` | Giáo viên khác: kiểm tra không được truy cập bảng điểm danh của lớp này |

## Luồng demo

1. Đăng nhập giáo viên, vào Thời khóa biểu, chọn ngày **13/11/2026**, tìm `ATT-MD-DEMO-01` (Lớp Piano - Demo điểm danh). Buổi 11 lúc **07:00–09:00** có bốn học viên, chưa có điểm danh. Theo code hiện tại, giáo viên sửa được trong ngày học sau giờ bắt đầu; không bị khóa ngay khi hết giờ ca.
2. Vào Lớp phụ trách để xem lớp, danh sách bốn học viên và các buổi học. Có thể mở bảng điểm danh từ lịch tuần hoặc chi tiết lớp.
3. Đăng nhập phụ huynh ở cửa sổ riêng, vào Điểm danh. Chọn từng con và đúng lớp `ATT-MD-DEMO-01`, bấm Tra cứu. Trước khi demo lưu điểm danh, kết quả như sau:

| Học viên | Có mặt | Vắng | Chưa điểm danh | Tỷ lệ vắng | Mức |
|---|---:|---:|---:|---:|---|
| Nguyễn Bảo An | 10 | 0 | 2 | 0% | Bình thường |
| Nguyễn An Nhiên | 9 | 1 | 2 | 8,3% | Bình thường |
| Nguyễn Minh Quân | 8 | 2 | 2 | 16,7% | Cảnh báo |
| Nguyễn Ngọc Hân | 7 | 3 | 2 | 25% | Nghiêm trọng |

4. Trở lại giáo viên, lưu buổi hôm nay: ví dụ ba học viên Có mặt, Ngọc Hân Vắng mặt. Xem thông báo lưu thành công, mở chỉnh sửa nếu muốn; phụ huynh tải lại trang để thấy số liệu cập nhật. Việc lưu vắng thực hiện kiểm tra ngưỡng cảnh báo và gửi thông báo theo cơ chế có sẵn. Seed không giả lập các thông báo phát sinh từ thao tác này.
5. Chọn **20/11/2026** trên lịch tuần, mở buổi 12. Giáo viên, Staff và Admin đều chỉ xem, không điểm danh hoặc sửa được ngày tương lai.
6. Mở một buổi lịch sử: giáo viên chỉ xem; Staff/Admin có quyền sửa theo nghiệp vụ hiện tại. Đăng nhập `d26_teacher_other` và thử URL bảng điểm danh của lớp để kiểm tra bị từ chối quyền.

Mười buổi lịch sử có đủ 40 bản ghi để không bị hệ thống tự tính buổi quá khứ chưa điểm danh thành vắng. Tổng khóa học giữ 12 buổi, không thay đổi khóa `PIA-CB-12`. Phòng và bốn đàn Piano được tạo riêng. Bộ này không tạo khoản thu hoặc biên lai học phí.

## Chạy lại

Trong SSMS chọn database `course_operation_management` và chạy toàn bộ file SQL. Nếu đã có marker `ATT_WEEKLY_V1`, script chỉ in thông tin kiểm tra, giữ nguyên dữ liệu đã demo. Nếu cần khôi phục dữ liệu ban đầu hoặc đổi ngày trình diễn, cần reset có chủ đích riêng bộ này; không xóa marker đơn lẻ rồi chạy lại.
