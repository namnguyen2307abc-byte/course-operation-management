# Dữ liệu demo tổng hợp

File duy nhất để nạp: `database/seed_demo_tong_hop.sql`.

Gồm `du_lieu_theo_word.sql`, `test_attendence_makeup.sql`, `test_d26_absence_makeup.sql`, `-- Seed data for reviewing Weekly S.sql` và dữ liệu nền mà các file này cần. Phần D26 đã chuyển tên cột/giá trị về đúng schema hiện tại. Các file nguồn được giữ lại để tham khảo; không chạy tiếp từng file sau khi đã nạp bản gộp.

File yêu cầu database đã có schema ứng dụng. File tự bổ sung các cột còn thiếu và khóa ngoại tương ứng trong cùng transaction:

- `placement_schedules.cancel_reason NVARCHAR(500) NULL`, `cancelled_at DATETIME2 NULL`.
- `programs.placement_subject VARCHAR(20) NULL`.
- `enrollment_requests.requested_program_id BIGINT NULL` → `programs.id`.
- `placement_schedules.program_id BIGINT NULL` → `programs.id`.

Không cần chạy riêng `placement_schedule_cancellation.sql` hoặc `database/migrations/20261115_placement_programs.sql` khi dùng file gộp này. Sau phần nạp dữ liệu, file cấu hình nhóm kiểm tra cho các mã môn đã biết: PIANO/GUITAR → DAN, DANCE → MUA, MARTIAL_ARTS → VO, chỉ khi chưa có cấu hình. Giữ nguyên cấu hình tùy chỉnh và thông tin môn của yêu cầu/lịch kiểm tra cũ; không tự suy đoán để điền lại lịch sử.

Dữ liệu được đánh dấu `COMBINED_DEMO_V1`; chạy lại sẽ bỏ qua phần đã nạp để giữ trạng thái demo, nhưng vẫn kiểm tra/thêm các cột và khóa ngoại nêu trên, cập nhật cấu hình môn còn thiếu và bổ sung thu ngân thứ hai nếu thiếu. File không tạo toàn bộ schema từ database trống. Ngày tương đối được tính tại lần nạp đầu tiên, không tự dịch chuyển khi mở ứng dụng.

## Tài khoản

| Vai trò / bộ dữ liệu | Tài khoản | Mật khẩu |
|---|---|---|
| Admin nền | `admin` | `123456` |
| Admin D26 / Weekly | `d26_admin` | `Test@123456` |
| Nhân viên D26 / Weekly | `d26_staff` | `Test@123456` |
| Nhân viên thu ngân thứ hai | `d26_staff_2` | `Test@123456` |
| Phụ huynh D26 / Weekly | `d26_parent_new` | `Test@123456` |
| Phụ huynh chờ kiểm tra đầu vào | `d26_parent_wait` | `Test@123456` |
| Phụ huynh chờ xếp lớp | `d26_parent_ready` | `Test@123456` |
| Phụ huynh test thanh toán | `d26_parent_pay` | `Test@123456` |
| Giáo viên lớp Weekly | `d26_teacher_piano` | `Test@123456` |
| Giáo viên không được phân công lớp Weekly | `d26_teacher_other` | `Test@123456` |
| Giáo viên các lớp D26 | `teacher_huong` | `Test@123456` |
| Giáo viên Piano | `teacher_ha` | `123456` |
| Giáo viên Guitar | `teacher_tuan`, `teacher_dung` | `123456` |
| Giáo viên Múa | `teacher_linh` | `123456` |
| Giáo viên Võ | `teacher_long` | `123456` |
| Nhân viên dữ liệu học bù cũ | `staff_minh` | `123456` |
| Thu ngân dữ liệu nền | `cashier_mai` | `123456` |
| Phụ huynh dữ liệu học bù cũ | `parent_lan`, `parent_hoang`, `parent_dung` | `123456` |

Tổng cộng **21 tài khoản**: 2 Admin, 4 Staff, 8 Teacher và 7 Parent. Mật khẩu trên là mật khẩu ban đầu của bộ demo; nếu đã đổi mật khẩu sau khi nạp thì file chạy lại không đặt lại mật khẩu. Cuối file SQL có truy vấn liệt kê toàn bộ 21 tài khoản của bộ dữ liệu này.

## Kiểm tra thủ công

1. Đăng nhập `d26_teacher_piano`, mở thời khóa biểu, tìm lớp `ATT-MD-DEMO-01`. Lớp có 4 học viên, buổi hôm nay chưa điểm danh. Buổi tuần sau bị khóa cho cả giáo viên, nhân viên và admin.
2. Đăng nhập `d26_parent_new`, xem chuyên cần lớp trên: Bảo An 0%, An Nhiên 8,3%, Minh Quân 16,7% (cảnh báo), Ngọc Hân 25% (nguy cơ cao). Chi tiết tại `docs/DEMO_WEEKLY_ATTENDANCE.md`.
3. Dùng `d26_staff`, `d26_parent_new`, `teacher_huong` kiểm tra đơn xin nghỉ và học bù của bộ D26; dùng `staff_minh` và các phụ huynh dữ liệu cũ kiểm tra các trường hợp còn lại.

D26 dùng lại học viên Nguyễn Bảo An và tạo sẵn lớp `PIA-MD-2701-01` mà kịch bản Word ban đầu dành cho thao tác tạo mới. Vì vậy sau khi nạp gộp, lớp này đã tồn tại. Bộ tổng hợp sau khi bổ sung lớp cô Hương có 21 tài khoản, 13 học viên, 14 lớp, 73 buổi, 21 đơn nghỉ, 17 lượt học bù và 62 bản ghi điểm danh.

## Lớp cô Vũ Thu Hương ngày 16/11/2026

- Mã lớp: `HUONG-1611-AM`, tên: Piano sáng thứ Hai - Cô Vũ Thu Hương.
- Giáo viên: `teacher_huong`, mật khẩu `Test@123456`.
- Cơ sở Cầu Giấy (`CS01`), phòng Piano Nhóm 1 (`P102`), khóa `PIA-CB-12` / trình độ `BEGINNER`.
- Ngày 16/11/2026 có Slot 1 **07:00–09:00** và Slot 2 **09:30–11:30**. Lớp có 12 buổi trong 6 thứ Hai, kết thúc 21/12/2026.
- Ba học viên đã ghi danh: Nguyễn An Nhiên, Trần Minh Khang, Lê Ngọc Anh; phụ huynh `parent_lan` (mật khẩu `123456`). Các buổi mới chưa có bản ghi điểm danh.
- Mở lịch tuần chứa ngày 16/11/2026, lọc cô Vũ Thu Hương và mở điểm danh từng slot. Backend vẫn giữ quy định không điểm danh buổi tương lai.

File chung chuẩn hóa các nhãn trình độ demo cũ về `BEGINNER`, `INTERMEDIATE`, `ADVANCED`; các khóa đang có đều là sơ cấp nên dùng `BEGINNER`. Không tạo bộ môn tổng hợp `PROG-MUSIC`; dữ liệu Piano dùng bộ môn `PIANO`. Khi chạy lại, lớp và học viên của phần bổ sung này không bị tạo trùng. Nếu còn dữ liệu liên kết tới `PROG-MUSIC` hoặc lịch cô Hương/phòng bị trùng, script báo lỗi và rollback để tránh làm sai dữ liệu.

## Test quyền điều chỉnh giữa hai nhân viên

Chạy lại file tổng hợp trên database đã nạp sẽ bổ sung schema còn thiếu nêu trên và `d26_staff_2` nếu chưa có; không reset mật khẩu, lịch đóng hoặc giao dịch đang test. Nếu tên tài khoản đã thuộc role khác, script báo lỗi thay vì đổi quyền.

1. Đăng nhập `d26_staff`, thu một kỳ bằng tiền mặt và ghi lại số biên lai mới. Tài khoản này được gửi yêu cầu điều chỉnh biên lai đó.
2. Đăng xuất, đăng nhập `d26_staff_2`, mở cùng hóa đơn: được xem biên lai nhưng không thấy nút điều chỉnh khoản do `d26_staff` thu.
3. Dùng `d26_staff_2` thu một kỳ khác bằng tiền mặt. Biên lai mới chỉ tài khoản này được đề nghị điều chỉnh; `d26_staff` không được đề nghị cho khoản đó.
4. Đăng nhập `d26_admin`: được đề nghị điều chỉnh mọi khoản tiền mặt hợp lệ. Người gửi không tự duyệt yêu cầu của mình; biên lai đã có yêu cầu hoặc đã điều chỉnh không dùng để gửi yêu cầu mới.

Lần reset này chỉ nạp bộ tổng hợp trên; dữ liệu demo HD/DD và các giao dịch trước reset không được nạp lại. Bộ tổng hợp không tạo hóa đơn hay giao dịch thanh toán; có thể bắt đầu luồng đăng ký/xếp lớp để tạo học phí mới.

Ứng dụng: http://127.0.0.1:5500/login.html — backend: http://127.0.0.1:8080.


## Chính sách ưu đãi học phí (20/11/2026)

- File `database/seed_demo_tong_hop.sql` đã gồm phần tạo bảng `tuition_discount_snapshots` nếu chưa có, kể cả database đã nạp dữ liệu demo.
- Database đang có dữ liệu có thể chỉ chạy `database/migrations/20261120_tuition_discount_snapshots.sql`; không cần reset hoặc nạp lại demo.
- Bảng mới lưu chính sách, điểm test thang 10, mã test, mức giảm, học phí gốc và thời điểm chốt. Không tính lại hóa đơn lịch sử.
- Điểm test nguồn trong `placement_schedules.score` theo thang 100: dưới 70 không giảm; 70–79 giảm 10%; 80–89 giảm 20%; 90–100 giảm 30%. Chỉ dùng kết quả COMPLETED phù hợp với khóa học và hồ sơ đã xếp lớp.
- Đăng ký không qua test: Không giảm / phần trăm nguyên 1–30 / số tiền nguyên từ 1.000đ đến tối đa 30% học phí gốc. Có giảm phải có lý do.
- Tối đa 3 kỳ, mỗi kỳ ít nhất 1.000đ; kỳ đầu ít nhất 30% học phí sau giảm (làm tròn lên đến đồng). Tổng kỳ bằng học phí sau giảm. Hạn đóng tăng dần, từ hôm nay và không sau ngày kết thúc lớp.
- Chốt lịch không tạo giao dịch thu tiền. Giao dịch/biên lai 0đ bị từ chối; thanh toán tiền mặt/PayOS tiếp tục dùng luồng xác nhận riêng.
