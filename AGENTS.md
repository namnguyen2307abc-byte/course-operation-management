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
