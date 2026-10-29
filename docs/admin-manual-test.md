# Tự kiểm thử chức năng quản trị

## Tài khoản

Đăng nhập ở `http://127.0.0.1:5500/login.html`.

- Tài khoản quản trị hiện có: `admin` / `123456`.
- Tất cả tài khoản mẫu `qa_` bên dưới dùng **`Test@123456`** lúc vừa tạo dữ liệu.
- Nếu bạn đổi mật khẩu trong quá trình test, mật khẩu mới của bạn được giữ lại. Chạy lại script không đặt lại mật khẩu hoặc các thao tác bạn đã thực hiện.
- Trong Quản lý tài khoản, nhập **qa_** vào ô tìm kiếm và bấm **Tìm kiếm** để lọc riêng nhóm dữ liệu test.

| Tên đăng nhập | Vai trò / trạng thái ban đầu | Dùng để test |
| --- | --- | --- |
| `qa_admin` | Admin hoạt động | Toàn bộ 3 trang admin; không thể tự khóa hoặc tự hạ quyền |
| `qa_manager` | Quản lý hoạt động | Không được truy cập API quản trị tài khoản |
| `qa_teacher_free` | Giáo viên chưa có lớp | Đổi role thành công khi không có liên kết |
| `qa_teacher_linked` | Giáo viên có lớp | Chi tiết có lớp; đổi role bị chặn vì còn liên kết |
| `qa_parent_free` | Phụ huynh chưa có con | Sửa thông tin, đổi role |
| `qa_parent_linked` | Phụ huynh có hồ sơ con | Xem liên kết; đổi role bị chặn |
| `qa_student` | Học viên đã gắn hồ sơ | Đăng nhập và chỉ xem lịch/hồ sơ của mình |
| `qa_locked` | Nhân viên đang khóa | Đăng nhập bị từ chối; dùng admin mở khóa rồi thử lại |
| `qa_password` | Nhân viên cần đổi mật khẩu | Được chuyển tới trang đổi mật khẩu sau đăng nhập |
| `qa_created` | Nhân viên được cấp qua API | Bắt buộc đổi mật khẩu; có nhật ký CREATE_USER thực |
| `qa_staff_01` đến `qa_staff_08` | Nhân viên; `_04` và `_08` đang khóa | Tìm kiếm, phân trang, lọc, sửa, đổi quyền, khóa và đặt lại mật khẩu |

Tổng cộng **18 tài khoản**, trong đó ban đầu 3 tài khoản bị khóa. Họ tên có nhãn `[TEST]`, email dùng miền dành riêng cho dữ liệu mô phỏng `example.test`.

## Các ca test gợi ý

1. **Danh sách**: đăng nhập `qa_admin`, mở Quản lý tài khoản, tìm **qa_**. Có 18 kết quả trên 4 trang, mỗi trang tối đa 5 dòng. Chọn **Đã khóa** ở bộ lọc Trạng thái sẽ thấy 3 tài khoản mẫu. Thử lọc role, tìm username/email/số điện thoại, xóa bộ lọc. Nhật ký quản trị cũng hiển thị 5 dòng/trang.
2. **Cấp tài khoản**: tạo username mới của bạn, chọn role STAFF, mật khẩu tạm tối thiểu 8 ký tự. Kiểm tra tài khoản xuất hiện và có nhật ký cấp mới. Đăng xuất, đăng nhập tài khoản vừa cấp: hệ thống yêu cầu đổi mật khẩu trước khi vào nghiệp vụ.
3. **Khóa phiên đang mở**: mở `qa_staff_02` ở trình duyệt/phiên riêng, đồng thời dùng admin khóa tài khoản này. Khi phiên nhân viên gọi API tiếp theo, hệ thống yêu cầu đăng nhập lại. Mở khóa bằng admin; phiên cũ vẫn không được dùng lại, cần đăng nhập mới.
4. **Đổi role**: đổi `qa_teacher_free` sang STAFF phải thành công. Đổi `qa_teacher_linked` sang STAFF phải bị từ chối vì còn lớp/lịch dạy. Đổi `qa_parent_linked` sang STAFF phải bị từ chối vì còn hồ sơ con. Mọi thay đổi thành công có lý do trong nhật ký.
5. **Tài khoản học viên**: tạo tài khoản STUDENT, tìm hồ sơ bằng cụm **Hồ sơ chờ cấp**, chọn một hồ sơ mẫu chưa có tài khoản. Không chọn hồ sơ thì không cấp được; một hồ sơ không thể gắn hai tài khoản.
6. **Đặt lại mật khẩu**: chọn `qa_staff_03`, đặt mật khẩu tạm mới và nhập lý do. Mật khẩu cũ/phiên cũ không còn dùng được. Đăng nhập bằng mật khẩu tạm mới sẽ được yêu cầu đổi mật khẩu.
7. **Bảo vệ admin**: mở chi tiết chính tài khoản admin đang đăng nhập. Nút khóa/đổi role/reset mật khẩu của chính mình bị vô hiệu; đổi mật khẩu của mình bằng menu Đổi mật khẩu.
8. **Nhật ký**: lọc theo Cấp tài khoản / Đổi vai trò / Khóa-mở khóa / Sửa thông tin / Đặt lại mật khẩu. Các ví dụ đã được thực hiện bằng API thật trên `qa_staff_07`, `qa_password`, `qa_created`. Từ chi tiết tài khoản có thể mở nhật ký chỉ của tài khoản đó.
9. **Tổng quan**: thử Hôm nay, 7 ngày gần nhất, Tháng này và Tùy chọn. Chỉ số thực thu và biểu đồ thay đổi theo kỳ; các tổng hiện tại và hàng đợi không thay đổi theo bộ lọc thời gian.
10. **Chuyển trang**: bấm lần lượt các mục sidebar. Khung sidebar và khoảng chừa bên trái xuất hiện ngay khi trang tải; menu giữ vị trí cuộn và không dựng lại sau khi API xác minh phiên trả về. Bấm mục đang đứng không tải lại trang/làm mất bộ lọc.

## Dữ liệu nghiệp vụ mô phỏng

Ngoài tài khoản, fixture thêm 8 hồ sơ học viên (1 đã có tài khoản, các hồ sơ khác có thể dùng để cấp thêm), 1 cơ sở/phòng/thiết bị/chương trình/khóa/lớp, 4 ghi danh, 7 yêu cầu đăng ký ở nhiều trạng thái, 2 buổi học sắp tới, 2 đơn nghỉ, 1 ca bù chờ xếp và 2 lịch kiểm tra sắp tới.

Có 10 hóa đơn, 7 giao dịch thành công và 1 giao dịch thất bại. Tại thời điểm vừa tạo, riêng bộ mẫu đóng góp:

- Tiền thực thu trong 7 ngày gần nhất: **4.900.000 đồng**.
- Học phí chưa thanh toán: **3.600.000 đồng**.
- Trong đó quá hạn: **1.100.000 đồng**.
- Giao dịch thất bại **1.300.000 đồng** không được cộng vào tiền thực thu.

Các số trên cộng vào dữ liệu hiện có, không thay thế chúng. Sau khi bạn thử thu tiền hoặc đổi trạng thái, số liệu sẽ thay đổi tương ứng. Đây là bản ghi mô phỏng trong SQL Server local; không tạo thanh toán PayOS thật.

## Tạo lại ở máy khác

Sau khi áp dụng `database/admin_management_upgrade.sql` và chạy backend, tại thư mục gốc:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\seed-admin-demo.ps1
```

Script dùng SQL Server `localhost`, database `course_operation_management`, Windows Authentication và backend `http://127.0.0.1:8080`. Có thể truyền `-Server`, `-Database`, `-ApiBase` nếu môi trường khác.

`database/admin_manual_test_fixture.sql` thêm 17 tài khoản và dữ liệu nghiệp vụ trong một transaction. Script PowerShell cấp thêm `qa_created` và thực hiện các thao tác API để có nhật ký thật, rồi ghi mốc hoàn tất. Khi chạy lại, không ghi đè dữ liệu hiện có hoặc đặt lại kết quả bạn đang test.
