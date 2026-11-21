# Quy trình làm việc với các nhánh

Khi người dùng yêu cầu đồng bộ/merge/push một nhánh, thực hiện theo quy trình người dùng đã thống nhất:

1. Xác định đúng nhánh nguồn, kiểm tra thay đổi local và remote. Giữ nguyên tài liệu cá nhân, file tham khảo và dữ liệu runtime không thuộc thay đổi cần đẩy.
2. Kiểm tra, commit và push code lên nhánh nguồn trước.
3. Fetch `origin`, merge `origin/main` mới nhất vào nhánh nguồn. Xử lý conflict theo nghiệp vụ của cả hai phía; không bỏ chức năng của thành viên khác để hết conflict.
4. Chạy kiểm thử và build phù hợp trên nhánh nguồn; sửa lỗi rồi commit/push nhánh nguồn.
5. Chuyển sang `main`, cập nhật main mới nhất rồi merge nhánh nguồn vào main. Xử lý conflict và kiểm tra kết quả tích hợp; chạy lại các kiểm tra cần thiết khi kết quả merge có thay đổi.
6. Push `main`, kiểm tra commit local/remote trùng nhau; kết thúc trên nhánh `main` nếu người dùng không chỉ định khác.

Lần yêu cầu merge/push sau tiếp tục dùng quy trình này trong phạm vi người dùng đã giao. Không force-push hoặc reset bỏ thay đổi của người khác. Nếu main không có thay đổi mới hoặc merge không phát sinh conflict, báo đúng thực tế.

# Kiểm tra dự án

- Frontend gốc: `com_fe`. Đồng bộ bằng `node scripts/sync-frontend.mjs`; kiểm tra bằng `node scripts/sync-frontend.mjs --check`.
- Backend: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-backend.ps1` dùng H2 riêng, không chạy test phá dữ liệu SQL Server local.
- Frontend: các file `scripts/*test.cjs`; kiểm tra Chrome/Playwright khi thay đổi giao diện.
- Build backend với JDK 17/21 và thư mục build riêng dưới `.local-run` nếu jar hiện tại đang chạy.
- Không đưa log, token, khóa, database backup, `.local-run`, hoặc PDF tham khảo cá nhân lên Git. Bộ dữ liệu demo SQL/đặc tả nghiệp vụ thuộc tính năng có thể commit.

# Quy tắc tổ chức backend đã thống nhất

- Controller nhận/validate DTO, kiểm tra quyền và gọi interface service; không truy vấn database hoặc tính tiền trong controller.
- Service nghiệp vụ chính có interface trong `service`, implementation trong `service/impl`. Chia theo trách nhiệm thực tế; không thêm facade/service chỉ để chuyển tiếp lời gọi.
- Service điều phối nghiệp vụ, quyền trên dữ liệu và transaction. Repository phụ trách truy cập dữ liệu, truy vấn, tổng hợp, phân trang và khóa; không đặt JPQL/SQL hoặc EntityManager trong service mới.
- Mapper chỉ chuyển đổi dữ liệu, không gọi repository, tính ưu đãi hoặc quyết định trạng thái nghiệp vụ. Policy nhỏ/formatter không bắt buộc có interface và impl.
- Gateway phụ trách giao tiếp dịch vụ ngoài và xác thực dữ liệu cổng. Không giữ khóa database trong lúc chờ HTTP; kết quả không rõ phải giữ trạng thái UNCERTAIN/RECONCILE, không tự ghi nhận thành công hoặc thất bại.
- Dùng BigDecimal cho tiền Java; tên DTO/phương thức/biến mô tả rõ ý nghĩa. Dùng enum cho trạng thái mới; giữ nguyên giá trị API/database khi refactor.
- Lỗi nghiệp vụ có mã lỗi và status rõ, được chuyển thành response tại exception handler riêng của phân hệ. Khi sửa thanh toán, giữ nguyên GlobalExceptionHandler và response security dùng chung để không đổi hợp đồng lỗi/validation của phân hệ khác. Không import helper tạo lỗi từ service khác; không trả stack trace hoặc thông báo lỗi kỹ thuật nội bộ cho người dùng.
- Khi dọn API cũ, kiểm tra caller và giữ adapter tương thích nếu còn dùng; không bỏ endpoint hay thay nghiệp vụ trong một lần refactor cấu trúc.
- Refactor thanh toán phải giữ quyền sở hữu, chống gửi lặp, chữ ký webhook, kiểm tra số tiền/phiên bản lịch, khóa đồng thời, lịch sử điều chỉnh và thông báo. Test bằng H2, Chrome và build; không giao dịch thật hoặc sửa SQL Server nếu chưa được yêu cầu.
