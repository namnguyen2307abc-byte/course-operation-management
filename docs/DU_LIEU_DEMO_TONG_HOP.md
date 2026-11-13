# Dữ liệu demo tổng hợp

File duy nhất để nạp: `database/seed_demo_tong_hop.sql`.

Gồm `du_lieu_theo_word.sql`, `test_attendence_makeup.sql`, `test_d26_absence_makeup.sql`, `-- Seed data for reviewing Weekly S.sql` và dữ liệu nền mà các file này cần. Phần D26 đã chuyển tên cột/giá trị về đúng schema hiện tại. Các file nguồn được giữ lại để tham khảo; không chạy tiếp từng file sau khi đã nạp bản gộp.

File giữ nguyên schema, nạp trong transaction và đánh dấu `COMBINED_DEMO_V1`; chạy lại sẽ bỏ qua để giữ trạng thái demo. Muốn làm mới toàn bộ phải sao lưu và reset dữ liệu trước. Ngày tương đối được tính tại lần nạp đầu tiên, không tự dịch chuyển khi mở ứng dụng.

## Tài khoản

| Vai trò / bộ dữ liệu | Tài khoản | Mật khẩu |
|---|---|---|
| Admin D26 / Weekly | `d26_admin` | `Test@123456` |
| Nhân viên D26 / Weekly | `d26_staff` | `Test@123456` |
| Phụ huynh D26 / Weekly | `d26_parent_new` | `Test@123456` |
| Giáo viên lớp Weekly | `d26_teacher_piano` | `Test@123456` |
| Giáo viên không được phân công lớp Weekly | `d26_teacher_other` | `Test@123456` |
| Giáo viên các lớp D26 | `teacher_huong` | `Test@123456` |
| Nhân viên dữ liệu học bù cũ | `staff_minh` | `123456` |
| Phụ huynh dữ liệu học bù cũ | `parent_lan`, `parent_hoang`, `parent_dung` | `123456` |

## Kiểm tra thủ công

1. Đăng nhập `d26_teacher_piano`, mở thời khóa biểu, tìm lớp `ATT-MD-DEMO-01`. Lớp có 4 học viên, buổi hôm nay chưa điểm danh. Buổi tuần sau bị khóa cho cả giáo viên, nhân viên và admin.
2. Đăng nhập `d26_parent_new`, xem chuyên cần lớp trên: Bảo An 0%, An Nhiên 8,3%, Minh Quân 16,7% (cảnh báo), Ngọc Hân 25% (nguy cơ cao). Chi tiết tại `docs/DEMO_WEEKLY_ATTENDANCE.md`.
3. Dùng `d26_staff`, `d26_parent_new`, `teacher_huong` kiểm tra đơn xin nghỉ và học bù của bộ D26; dùng `staff_minh` và các phụ huynh dữ liệu cũ kiểm tra các trường hợp còn lại.

D26 dùng lại học viên Nguyễn Bảo An và tạo sẵn lớp `PIA-MD-2701-01` mà kịch bản Word ban đầu dành cho thao tác tạo mới. Vì vậy sau khi nạp gộp, lớp này đã tồn tại. Bộ tổng hợp hiện có 20 tài khoản, 10 học viên, 13 lớp, 61 buổi, 21 đơn nghỉ, 17 lượt học bù và 62 bản ghi điểm danh.

Lần reset này chỉ nạp bộ tổng hợp trên; dữ liệu demo HD/DD và các giao dịch trước reset không được nạp lại. Bộ tổng hợp không tạo hóa đơn hay giao dịch thanh toán; có thể bắt đầu luồng đăng ký/xếp lớp để tạo học phí mới.

Ứng dụng: http://127.0.0.1:5500/login.html — backend: http://127.0.0.1:8080.
