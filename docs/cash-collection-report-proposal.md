# Đề xuất báo cáo thu tiền và kiểm soát tiền nhân viên thu

Ngày đối chiếu: 26/10/2026. Tài liệu đề xuất; chưa di chuyển màn hình hoặc thay đổi nghiệp vụ thanh toán.

## Vị trí chức năng

Nên có trang **Báo cáo thu tiền** trong nhóm **Quản trị hệ thống**, dành cho admin xem toàn trung tâm. Trang **Thu học phí** tiếp tục phục vụ thao tác tại quầy: tìm hóa đơn, thu tiền, xem/in biên lai và lịch sử thu của chính người đang đăng nhập.

Hai trang dùng chung nguồn `payments`/`invoices` và quy tắc tính tiền. Không sao chép dữ liệu hoặc tính doanh thu bằng hai công thức khác nhau. Backend nghiệp vụ thanh toán vẫn nằm trong phân hệ tài chính/thu học phí; việc tách trang không đưa nghiệp vụ thu tiền vào service quản lý tài khoản.

Mục tiêu kiểm soát tiền thu là phù hợp với trung tâm có nhiều nhân viên thu học phí. Phải phân biệt **nhân viên ghi nhận giao dịch**, **người tạo yêu cầu thanh toán online**, **nguồn xác nhận nhận tiền** và **người chịu trách nhiệm bàn giao tiền mặt**.

## Đánh giá hiện tại

Đã có nền tảng hữu ích: lọc ngày và thu ngân, chia tiền mặt/chuyển khoản, tra cứu mã giao dịch-hóa đơn-học viên, xem biên lai; giao dịch lưu số tiền, thời gian và tham chiếu cashier. Nghiệp vụ hiện chưa đủ để đối soát trách nhiệm tiền mặt.

### 1. Giao dịch thất bại đang được tính là thực thu — cần sửa trước

`PaymentRepository.findPaymentsWithFilters()` không lọc trạng thái. `TuitionPaymentServiceImpl.getPaymentHistoryReport()` cộng mọi dòng thành tổng tiền và giao dịch; mapper không trả trạng thái payment, luôn gắn thông điệp thanh toán thành công. Giao diện cho xem biên lai của mọi dòng.

Đối chiếu API và SQL local, riêng `qa_staff_01`, ngày 26/10/2026, mã QA-ADM:

| Nguồn | Thành công | Thất bại | Tổng đang hiển thị là thực thu |
| --- | ---: | ---: | ---: |
| SQL payments | 1.000.000 | 1.300.000 | Thực thu đúng: 1.000.000 |
| API history-report | Tiền mặt 1.000.000 | Bị cộng thành chuyển khoản 1.300.000 | 2.300.000 |

Đây là lỗi báo cáo có sẵn được bộ dữ liệu test làm lộ ra; giao dịch FAILED được thêm để kiểm tra việc loại khỏi thực thu. Báo cáo tổng quan admin hiện dùng SUCCESS nên không mắc cùng lỗi này.

Đề xuất: KPI chỉ cộng SUCCESS; bảng giao dịch hiển thị trạng thái thật và có bộ lọc trạng thái. FAILED/PENDING chỉ xuất hiện khi tra cứu giao dịch tương ứng, không cộng vào tiền thu và không có biên lai thu tiền thành công.

### 2. Gán nhân viên cho giao dịch online chưa chính xác

Webhook gọi `processPayment(request, "PAYOS_GATEWAY")`; nếu không tìm được user tương ứng, `processPayment()` dùng `cashier_mai`. Webhook mô phỏng có hành vi tương tự. Do đó cột thu ngân có thể quy tiền online về một nhân viên không thực hiện thao tác.

Đề xuất lưu riêng người tạo yêu cầu/thu tại quầy, người xác nhận (nếu thủ công) và nguồn CASH/BANK/PAYOS. PayOS tự xác nhận được ghi nhận là nguồn hệ thống, đồng thời giữ người tạo yêu cầu nếu có; không gán mặc định một nhân viên. Lịch sử lọc nhân viên phải bao gồm người từng có giao dịch dù hiện đã khóa hoặc đổi role.

### 3. Cần kiểm soát số tiền và ghi nhận trùng

`processPayment()` nhận `finalAmount`/miễn giảm từ request và gán vào hóa đơn; DTO hiện không có ràng buộc số tiền không âm. Chưa thấy cơ chế khóa hóa đơn/chống xử lý đồng thời đủ để bảo đảm hai request cùng đọc UNPAID không cùng ghi nhận thành công.

Đề xuất server tính/kiểm tra lại tiền phải thu và miễn giảm, yêu cầu lý do/duyệt khi cần theo chính sách trung tâm; khóa hoặc version hóa hóa đơn, định danh giao dịch ngân hàng duy nhất và xử lý webhook lặp an toàn. Xác nhận chuyển khoản thủ công cần bằng chứng/quy trình phù hợp, không coi việc chọn phương thức chuyển khoản là bằng chứng đã nhận tiền.

### 4. Quyền xem báo cáo chưa tách theo mục đích

Nhóm API thu học phí hiện cho ADMIN/STAFF/BRANCH_MANAGER, nhưng history-report nhận tùy ý tham số cashier và không giới hạn dữ liệu theo người gọi. Nhân viên có thể xem tổng của người khác. Giao diện vẫn còn hàm auto-login tài khoản mẫu để demo.

Đề xuất admin xem toàn bộ; nhân viên xem giao dịch của mình, backend tự lấy user từ phiên đăng nhập. Bỏ auto-login mẫu. Quyền quản lý chi nhánh chỉ giới hạn theo chi nhánh khi đã có quan hệ tài khoản-chi nhánh rõ ràng trong dữ liệu.

### 5. Còn thiếu bàn giao/đối soát cuối ca hoặc cuối ngày

Hiện có ghi nhận thu tiền nhưng chưa có thực thể ca thu, bảng kê bàn giao, số tiền đã nộp, người xác nhận và chênh lệch. Vì vậy số thu của nhân viên không chứng minh nhân viên đã bàn giao tiền mặt.

Đề xuất giai đoạn sau: nhân viên lập bảng kê theo ngày/ca; hệ thống tính tiền mặt phải bàn giao; ghi số tiền bàn giao thực tế; admin xác nhận hoặc yêu cầu kiểm tra. Chênh lệch = tiền bàn giao thực tế − tiền mặt phải bàn giao. Tiền chuyển khoản/PayOS vào tài khoản trung tâm được đối soát riêng, không cộng vào tiền mặt nhân viên phải nộp. Các điều chỉnh sau xác nhận cần lưu dấu vết.

### 6. Chi tiết báo cáo cần hoàn thiện

- Bộ lọc ngày nên dùng `[đầu ngày bắt đầu, đầu ngày sau ngày kết thúc)` theo múi giờ trung tâm, tránh bỏ phần giây lẻ sau 23:59:59.
- Phân trang và tổng hợp ở database; tổng KPI dựa trên toàn bộ bộ lọc, không chỉ 5 dòng đang xem.
- Nhật ký quản trị tài khoản và lịch sử thu tiền là hai loại dữ liệu khác nhau; không trộn hai bảng này.
- Không sửa/xóa giao dịch thành công trực tiếp trên báo cáo. Hoàn/hủy/điều chỉnh, nếu bổ sung, phải thành nghiệp vụ có lý do và quyền duyệt riêng.

## Phạm vi triển khai đề xuất

**Giai đoạn 1 — Báo cáo thu tiền cho admin:** sửa lỗi SUCCESS/FAILED và biên lai; chuẩn hóa người ghi nhận/nguồn tiền; giới hạn quyền xem; kiểm tra số tiền/chống ghi nhận trùng; tách trang Báo cáo thu tiền trong menu admin; giữ lịch sử cá nhân ở Thu học phí. Trang mới có bộ lọc ngày/nhân viên/phương thức/trạng thái, KPI thực thu tiền mặt/chuyển khoản/tổng, bảng tổng theo nhân viên và bảng giao dịch chi tiết.

**Giai đoạn 2 — Bàn giao tiền mặt:** thêm bảng kê ngày/ca, số phải nộp/đã nộp/chênh lệch, quy trình nhân viên gửi-admin xác nhận và nhật ký đối soát. Giai đoạn này cần chốt quy tắc ca/ngày, quyền xác nhận và chính sách điều chỉnh trước khi code.

Ưu tiên sửa độ đúng của tiền và người chịu trách nhiệm trước khi đưa số liệu lên trang quản trị mới. Việc di chuyển giao diện đơn thuần không giải quyết sai lệch nghiệp vụ.
