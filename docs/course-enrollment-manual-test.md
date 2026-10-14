# Kịch bản test tay: tạo lớp và liên kết đăng ký lớp

Đối chiếu luồng trong `Quan_li_tao_lop.pdf`. Giao diện mới dùng mục **Quản lý lớp** trên trang chủ/sidebar, tương ứng với “Tạo & Quản Lý Lớp” trong tài liệu.

## 1. Khởi động và dữ liệu đã kiểm tra

Nhấn đúp `start_all.bat`, đợi cả backend và frontend báo sẵn sàng. Mở http://127.0.0.1:5500/login.html. Sau khi pull/sửa backend, chạy `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -RestartBackend` từ thư mục gốc. Dùng Ctrl+F5 để nạp JS mới.

Snapshot SQL Server `course_operation_management`, ngày **15/10/2026**: 23 tài khoản, 16 học viên, 10 lớp, 16 buổi học, 8 ghi danh, 11 yêu cầu đăng ký. Các số lượng thay đổi sau khi bạn thực hiện các bước ghi dữ liệu bên dưới.

| Mục đích | Tài khoản có sẵn | Dữ liệu liên quan |
|---|---|---|
| Tạo/sửa lớp, xử lý đăng ký | `cashier_mai` hoặc `staff_minh` | Vai trò STAFF |
| Kiểm tra phụ huynh và yêu cầu đang chờ | `parent_lan` | Bé Mai Chi ID 2; yêu cầu #3 đang `READY_FOR_ASSIGNMENT`, khóa PIA-PRE |
| Gửi một yêu cầu mới độc lập | `parent_test_01` | Nguyễn An Chưa Thi Test, ID 13; chưa có yêu cầu trong snapshot |
| Kiểm tra lịch dạy | `teacher_huong` | Giáo viên ID 4 |
| Kiểm tra quản trị | `admin` | Vai trò ADMIN |

Mật khẩu demo của các tài khoản trên theo seed là `123456`; đã xác nhận đăng nhập tài khoản nhân viên khi kiểm tra API. Nếu tài khoản đã đổi mật khẩu, dùng mật khẩu hiện tại.

| Dữ liệu có sẵn | ID / mã | Chi tiết |
|---|---|---|
| Khóa học cho lớp test | 1 / `PIA-PRE` | Piano Mầm Non (Cảm thụ âm nhạc), học phí 3.600.000đ |
| Cơ sở | 1 / `CS01` | Cơ Sở 1 - Cầu Giấy |
| Phòng phù hợp | 2 / `P102` | Phòng Piano Nhóm 1, sức chứa 8 |
| Lớp đối chiếu | 2 / `CL-PIA-PRE-G01` | OPEN, 2 đang học, 0 chờ thanh toán, tối đa 8 → còn 6 chỗ |
| Lớp có giữ chỗ chờ tiền | 4 / `CL-GUI-BASIC-01` | OPEN, 3 đang học, 1 chờ thanh toán, tối đa 8 → còn 4 chỗ |
| Lớp đầy | 1 / `CL-PIA-01` | 1 đang học + 1 chờ thanh toán / tối đa 1 → số chỗ hiển thị 0, không được chọn đăng ký |

Lớp `CL-PIA-01` đã vượt sức chứa trong dữ liệu mẫu cũ; kịch bản dùng nó để kiểm tra chặn lớp đầy. Không xóa ghi danh có sẵn để “sửa” số liệu.

## 2. Luồng chính đúng theo PDF

Thực hiện theo thứ tự; dùng mã khác nếu đã chạy kịch bản trước đó.

### TC01 — Tải danh sách và lọc dữ liệu

1. Đăng nhập `cashier_mai`, mở **Quản lý lớp**.
2. Khi chưa tạo thêm dữ liệu, danh sách có 10 lớp, bộ lọc có 7 khóa học và 2 cơ sở.
3. Chọn khóa PIA-PRE, cơ sở CS01, trạng thái OPEN và nhấn **Lọc dữ liệu**.
4. Kết quả có `CL-PIA-PRE-G01`; số chỗ còn lại là 6. Xóa bộ lọc để xem lại toàn bộ.

### TC02 — Tạo lớp Dự kiến

1. Nhấn **Tạo lớp & Xếp lịch**, nhập:

| Trường | Giá trị |
|---|---|
| Mã lớp | `QA-CE-20261015-A` |
| Tên lớp | `QA Piano Mầm Non - Chủ nhật` |
| Khóa học | PIA-PRE — Piano Mầm Non |
| Cơ sở | CS01 — Cầu Giấy |
| Giáo viên | Cô Vũ Thu Hương (`teacher_huong`) |
| Phòng | P102 — Phòng Piano Nhóm 1 |
| Sĩ số tối đa | 3 |
| Trạng thái | Dự kiến (`PLANNING`) |
| Ngày bắt đầu | 18/10/2026 |
| Các thứ học | Bỏ các thứ mặc định, chỉ chọn **Chủ nhật** |
| Khung giờ | 09:30–11:30 |
| Tổng buổi | 4 |

2. Nhấn **Lưu lớp học**.
3. Mong đợi: thông báo thành công, mã xuất hiện trong danh sách, 0 đang học / 0 chờ tiền / 3 tối đa, còn 3 chỗ; có 4 buổi vào 18/10, 25/10, 01/11, 08/11/2026. Ngày kết thúc tự tính thành 08/11/2026.
4. Tìm bằng mã `QA-CE-20261015-A`, kết hợp bộ lọc PIA-PRE và CS01: chỉ còn lớp này.

Nếu test vào ngày khác, có thể chọn ngày Chủ nhật sắp tới và đối chiếu 4 ngày Chủ nhật liên tiếp. Lớp Dự kiến có thể đã có lịch được sinh; trạng thái Dự kiến vẫn chặn đăng ký.

### TC03 — Phụ huynh chưa thấy lớp Dự kiến

1. Đăng xuất, đăng nhập `parent_lan`.
2. Mở **Đăng ký & xếp lớp**, chọn bé Mai Chi và khóa PIA-PRE; tắt tùy chọn Placement Test.
3. Trong **Lớp đang mở (tham khảo)**, không có `QA-CE-20261015-A`; lớp `CL-PIA-PRE-G01` vẫn xuất hiện.

### TC04 — Đổi sang OPEN và phụ huynh thấy lớp

1. Đăng nhập lại `cashier_mai`, mở Quản lý lớp, tìm mã vừa tạo, nhấn **Sửa**.
2. Chỉ đổi trạng thái sang **Đang mở (OPEN)** rồi lưu. Lớp đã có buổi học nên khung lịch/ngày được khóa để bảo toàn lịch hiện có; đổi trạng thái vẫn lưu bình thường.
3. Đăng nhập `parent_lan`, mở Đăng ký & xếp lớp và nhấn **Làm mới**. Chọn PIA-PRE, không chọn Placement Test.
4. Mong đợi: lớp QA xuất hiện, đúng giáo viên/phòng/lịch, còn 3 chỗ. Chọn lớp và mở xem cơ sở vật chất nếu muốn kiểm tra phần liên kết.

Danh sách lớp của phụ huynh dùng để **tham khảo**. Việc chọn tại đây không tự ghi danh; nhân viên mới quyết định xếp lớp cho yêu cầu.

## 3. Kiểm tra nối sang ghi danh và học phí

### TC05 — Xếp yêu cầu có sẵn vào lớp mới

1. Đăng nhập `cashier_mai`, mở **Đăng ký & xếp lớp / Tư vấn và xếp lớp**.
2. Tìm yêu cầu **#3**, bé **Nguyễn Mai Chi (Bé Bông)**, khóa mong muốn **PIA-PRE**, trạng thái sẵn sàng xếp lớp.
3. Chọn thao tác xếp/duyệt lớp, chọn `QA-CE-20261015-A` và xác nhận.
4. Mong đợi: yêu cầu thành `PENDING_PAYMENT`; tạo ghi danh chờ thanh toán và hóa đơn 3.600.000đ theo học phí khóa học. Ghi lại mã hóa đơn để tra cứu ở **Thu học phí**.
5. Quay về Quản lý lớp: **0 đang học / 1 chờ thanh toán / 3 tối đa → còn 2 chỗ**.
6. Đăng nhập phụ huynh, làm mới: lớp QA cũng hiển thị **2 chỗ**, không hiển thị 3. Yêu cầu của bé Mai Chi hiển thị trạng thái chờ thanh toán.

Bước này thay đổi yêu cầu #3. Nếu nó đã được xử lý trước lúc bạn test, dùng `parent_test_01`, học viên **Nguyễn An Chưa Thi Test (ID 13)**, gửi yêu cầu mới cho PIA-PRE và tắt Placement Test; ghi lại ID mới rồi xử lý yêu cầu đó. Không gửi thêm yêu cầu cho bé Mai Chi khi yêu cầu #3 vẫn đang mở.

Kịch bản này chỉ tra cứu hóa đơn; bạn không cần xác nhận thu tiền để kiểm tra luồng tạo lớp trong PDF.

### TC06 — Lớp đầy và lớp đóng không được chọn

1. Sau TC05, sửa **lớp QA** về sĩ số tối đa **1**. Giá trị này bằng 1 chỗ đang được giữ nên hợp lệ.
2. Mong đợi: 0 đang học + 1 chờ thanh toán / 1 tối đa → còn 0. Lớp biến mất khỏi danh sách có thể chọn của phụ huynh và nhân viên.
3. Tăng lại sĩ số lên 3 → lớp hiện lại với 2 chỗ.
4. Lần lượt đổi lớp QA sang **Đang học (IN_PROGRESS)** và **Đã kết thúc (FINISHED)**: không được chọn đăng ký ở cả hai trạng thái. Quản lý lớp vẫn tìm thấy bằng bộ lọc phù hợp.
5. Đổi lại OPEN nếu muốn tiếp tục test. Không sửa các lớp có sẵn để làm bước này.

## 4. Các trường hợp lỗi và kiểm tra không ảnh hưởng chức năng khác

| Mã | Thao tác | Kết quả mong đợi |
|---|---|---|
| TC07 | Tạo thêm lớp có mã `qa-ce-20261015-a` | Báo mã đã tồn tại, không thêm lớp/buổi học |
| TC08 | Sĩ số tối đa 0; ngày kết thúc trước ngày bắt đầu; giờ kết thúc trước giờ bắt đầu | Chặn lưu, thông báo rõ lỗi |
| TC09 | Phụ huynh/giáo viên truy cập thẳng `pages/class-management.html` | Chuyển về trang chủ; API quản lý lớp cũng từ chối |
| TC10 | Lớp QA đã có buổi học: chỉ sửa tên/trạng thái | Lưu thành công, giữ nguyên số buổi, ngày, giờ, kết quả điểm danh |
| TC11 | Lớp đã có ghi danh/buổi học: thử đổi khóa học hoặc cơ sở | Trường bị khóa trên UI; API cũng chặn nếu gửi trực tiếp |
| TC12 | Đổi giáo viên/phòng trên lớp có lịch | Chỉ buổi SCHEDULED từ hôm nay được đổi phân công; buổi quá khứ/COMPLETED giữ nguyên |
| TC13 | Mở Lịch tuần, Nghỉ học & học bù, Cơ sở, Đánh giá đầu vào, Thu học phí | Các trang vẫn tải dữ liệu; không mất bản ghi sau việc tạo/sửa lớp |

TC12 nên dùng lớp QA mới tạo hoặc dữ liệu test riêng. Nếu muốn kiểm tra đủ lịch sử quá khứ/COMPLETED, dùng môi trường test; bộ test tự động đã bao phủ việc bảo toàn phân công của các buổi này.

## 5. Hủy yêu cầu đăng ký — kiểm tra bổ sung

Database ban đầu có CHECK cũ thiếu `CANCELLED`. Đã áp dụng migration riêng `database/course_enrollment_allow_cancelled.sql` trên máy này ngày 15/10/2026: giữ biểu thức CHECK cũ và chỉ bổ sung trạng thái này, không cập nhật/xóa bản ghi; tổng yêu cầu vẫn là 11. Với database cũ trên máy khác, áp dụng file này trước khi thử hủy.

1. Dùng `parent_test_01` và học viên ID 13 nếu chưa có yêu cầu đang mở.
2. Gửi yêu cầu PIA-PRE, không chọn Placement Test → `READY_FOR_ASSIGNMENT`.
3. Trước khi nhân viên xếp lớp, nhấn **Hủy yêu cầu**.
4. Mong đợi: `CANCELLED`, không phát sinh ghi danh/hóa đơn; không xuất hiện trong hàng đợi xử lý của nhân viên.
5. Yêu cầu đã xếp lớp/chờ thanh toán không có nút tự hủy. Nếu học viên ID 13 đã được dùng ở TC05, chọn một học viên test chưa có yêu cầu đang mở thay vì tạo trùng.

## 6. SQL đọc kết quả sau khi test

Chạy trong SSMS với đúng database. Các truy vấn sau chỉ đọc:

```sql
USE course_operation_management;
SELECT c.id,c.class_code,c.status,c.max_students,c.current_students,
       (SELECT COUNT(*) FROM enrollments e WHERE e.class_id=c.id AND e.status='PENDING_PAYMENT') AS pending_payment
FROM classes c WHERE c.class_code='QA-CE-20261015-A';

SELECT l.id,l.session_number,l.lesson_date,l.start_time,l.end_time,l.status,l.teacher_id,l.room_id
FROM lessons l JOIN classes c ON l.class_id=c.id
WHERE c.class_code='QA-CE-20261015-A' ORDER BY l.lesson_date;

SELECT r.id,r.student_id,r.preferred_course_id,r.class_id,r.status,r.enrollment_id
FROM enrollment_requests r WHERE r.student_id IN (2,13) ORDER BY r.id DESC;

SELECT e.id,e.student_id,e.status FROM enrollments e
JOIN classes c ON e.class_id=c.id WHERE c.class_code='QA-CE-20261015-A';

SELECT i.id,i.invoice_code,i.status,i.final_amount FROM invoices i
JOIN enrollments e ON i.enrollment_id=e.id
JOIN classes c ON e.class_id=c.id WHERE c.class_code='QA-CE-20261015-A';
```

Nếu muốn kết thúc test, đổi lớp QA sang FINISHED sau khi ghi kết quả. Không xóa học viên, hóa đơn hoặc ghi danh có sẵn.
