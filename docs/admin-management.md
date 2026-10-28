# Quản trị hệ thống

Nhánh triển khai: `feature/admin-management`.

Bộ tài khoản và hướng dẫn test từng chức năng: [admin-manual-test.md](admin-manual-test.md).

## Chạy trên database hiện có

1. Chạy `database/admin_management_upgrade.sql` trên database `course_operation_management`. Script có thể chạy lại; chỉ thêm cột, bảng và index, không xóa bảng hoặc dữ liệu. Không dùng lại `init_database.sql` cho database hiện có vì script khởi tạo xóa các bảng.
2. Chạy `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -RestartBackend` tại thư mục gốc.
3. Đăng nhập bằng tài khoản admin hiện có tại `http://127.0.0.1:5500/login.html`. Mở **Tổng quan quản trị**, **Quản lý tài khoản** hoặc **Nhật ký quản trị** từ trang chủ/thanh bên.

Database mới: chạy script khởi tạo của dự án, sau đó chạy script nâng cấp admin trước khi khởi động bằng `ddl-auto=validate`.

## Quy tắc truy cập

- Backend giữ 6 role: ADMIN, BRANCH_MANAGER, STAFF, TEACHER, PARENT, STUDENT. Thu ngân hiện dùng STAFF.
- Chỉ ADMIN đang hoạt động được gọi `/api/admin/**`. Mọi thao tác được kiểm tra ở server; dữ liệu role trong localStorage không cấp quyền API.
- Login kiểm tra mật khẩu BCrypt, không tự tạo tài khoản. Đăng ký công khai chỉ cấp role PARENT và từ chối username/email trùng.
- JWT phải có chữ ký, còn hạn và đúng `tokenVersion`. Không còn chấp nhận username hoặc `AUTH_TOKEN_...` thay cho JWT.
- Token cũ bị vô hiệu hóa sau khóa/mở khóa, đổi role, đặt lại mật khẩu hoặc tự đổi mật khẩu. Sau nâng cấp, phiên cũ chưa có claim version cần đăng nhập lại.
- Tài khoản cấp mới/đặt lại mật khẩu phải đổi mật khẩu trước khi sử dụng API nghiệp vụ. Admin cung cấp mật khẩu tạm qua kênh phù hợp; hệ thống không tự gửi email/SMS.
- Không tự khóa hoặc đổi role của mình. Các thao tác quản trị khóa tập admin theo cùng thứ tự trước khi khóa tài khoản đích để bảo vệ admin cuối cùng khi có yêu cầu đồng thời.
- Không xóa cứng tài khoản. Khóa tài khoản giữ nguyên lớp, học phí, điểm danh và các liên kết lịch sử.
- Đổi role giáo viên có liên kết lớp/lịch dạy/đánh giá hoặc phụ huynh có con bị từ chối; phản hồi giải thích cần xử lý liên kết trước. Phân hệ này không chuyển liên kết lịch sử tự động.
- STUDENT phải liên kết một hồ sơ `students.account_id`. Index duy nhất có điều kiện bảo đảm một tài khoản chỉ gắn một hồ sơ. Quan hệ `parent_id` vẫn giữ nguyên cho phụ huynh. Nâng cấp chỉ tự liên kết dữ liệu STUDENT cũ khi có đúng một hồ sơ, không đoán theo tên.
- API học phí yêu cầu ADMIN/STAFF/BRANCH_MANAGER; webhook PayOS vẫn công khai và dùng cơ chế xác minh chữ ký hiện có. API mô phỏng thanh toán và sửa font chỉ dành cho ADMIN.

## Trách nhiệm các tầng

`features/admin_management/controller`: nhận tham số/DTO, validation, kiểm tra quyền, trả HTTP.

`dto/request`, `dto/response`: hợp đồng dữ liệu; không trả entity User, password hoặc tokenVersion trong API quản trị.

`service` và `service/impl`: quy tắc nghiệp vụ, transaction, bảo vệ liên kết và điều phối ghi nhật ký. Nhật ký và thay đổi tài khoản nằm trong cùng transaction; ghi nhật ký thất bại thì thay đổi không được lưu.

`repository`: Specification cho lọc/phân trang, pessimistic lock cho mutation và truy vấn tổng hợp bằng SQL/JPA. `mapper`: chuyển User sang DTO an toàn.

Entity dùng chung: `User`, `Student`, `AdminAuditLog` trong `shared/entity`. Xác thực tiếp tục thuộc `features/auth`.

Frontend nguồn nằm ở `com_fe`. HTML chứa cấu trúc trang/biểu mẫu; `js/admin/admin-service.js` dùng `callApi()` cho transport và tiện ích trình bày; các file dashboard/users/form/audit điều khiển màn hình tương ứng. CSS riêng kế thừa thiết kế chung. Chạy `node scripts/sync-frontend.mjs` sau khi sửa frontend; `node scripts/sync-frontend.mjs --check` xác nhận các bản chạy được đồng bộ.

Sidebar được đưa vào HTML ngay lúc đồng bộ từ `components/navbar.html`. `auth.js` sẵn sàng trong head, `workspace-shell.js` cá nhân hóa menu trước nội dung chính. `has-workspace-sidebar` có sẵn từ HTML để tránh thay đổi bố cục sau API. Việc cập nhật thông tin phiên không xóa/dựng lại menu nếu người dùng và role vẫn giữ nguyên; vị trí cuộn của sidebar được lưu trong sessionStorage theo tài khoản. Các thay đổi component phải chạy script đồng bộ để cập nhật mọi trang.

## API chính

| Phương thức | Endpoint | Nhiệm vụ |
| --- | --- | --- |
| GET | `/api/admin/dashboard?from=&to=` | Tổng quan và biểu đồ thực thu |
| GET | `/api/admin/users?keyword=&role=&status=&page=&size=` | Danh sách tài khoản |
| GET | `/api/admin/users/{id}` | Chi tiết, lớp, hồ sơ con/học viên |
| GET | `/api/admin/users/student-options?keyword=` | Tối đa 50 hồ sơ chưa gắn tài khoản |
| POST | `/api/admin/users` | Cấp tài khoản, yêu cầu đổi mật khẩu |
| PUT | `/api/admin/users/{id}` | Sửa thông tin liên hệ/bộ môn |
| PATCH | `/api/admin/users/{id}/role` | Đổi role, yêu cầu lý do |
| PATCH | `/api/admin/users/{id}/status` | Khóa/mở khóa, yêu cầu lý do |
| POST | `/api/admin/users/{id}/reset-password` | Đặt lại mật khẩu, yêu cầu lý do |
| GET | `/api/admin/audit-logs` | Lọc theo người dùng/thao tác/tài khoản đích/ngày |
| GET | `/api/auth/me` | Thông tin phiên hiện tại |
| POST | `/api/auth/change-password` | Tự đổi mật khẩu, thay token |

## Ý nghĩa số liệu

- Khoảng ngày mặc định: đầu tháng đến hôm nay, theo Asia/Ho_Chi_Minh. Cho phép tối đa 367 ngày, tính cả hai đầu.
- Chỉ tiền thực thu, số giao dịch thành công và biểu đồ ngày áp dụng khoảng ngày. Giao dịch đúng ngày kết thúc được tính đến trước 00:00 ngày tiếp theo, kể cả phần giây lẻ.
- Thực thu cộng `payments.amount` với trạng thái SUCCESS; không cộng FAILED/PENDING hoặc coi tiền khách đưa là doanh thu.
- Chưa thu cộng `invoices.finalAmount` của UNPAID. Quá hạn là tập con có `dueDate` trước ngày hôm nay.
- Các tổng tài khoản, hồ sơ học viên, giáo viên hoạt động, khóa học, lớp OPEN, lượt ENROLLED, cơ sở, phòng, thiết bị và hàng đợi phản ánh trạng thái hiện tại của toàn trung tâm. Tổng tài khoản theo role bao gồm tài khoản đã khóa.
- Học viên là số hồ sơ trong `students`, không phải số tài khoản role STUDENT. Lượt ghi danh có thể lớn hơn số học viên vì một người học nhiều lớp.
- Nhật ký bắt đầu từ lần triển khai này; không tạo lịch sử giả cho dữ liệu cũ. Không lưu mật khẩu/hash/token vào nhật ký.

## Kiểm thử

`AdminManagementIntegrationTest` dùng H2 riêng, đi qua security filter, controller, service và repository thật. Bao gồm login/đăng ký, chặn role khác, token giả/cũ, bắt buộc đổi mật khẩu, cấp/khóa/mở khóa/đổi quyền/reset, liên kết học viên, phân trang, nhật ký, thống kê và hai admin đổi quyền đồng thời.

Chạy kiểm thử với JDK 17/21 và cấu hình H2 cho toàn bộ suite; không dùng SQL Server thật cho test `contextLoads`. Xem `scripts/test-backend.ps1`. Frontend: `node --test scripts/frontend-integration.test.cjs`.
