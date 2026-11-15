# Thông báo học phí trên chuông hệ thống

Module học phí dùng chung `NotificationService` và chuông hiện có; dữ liệu lịch đóng và QR vẫn nằm trong module `tuition_payment`.

| Sự kiện | Người nhận | Nội dung và đường dẫn |
|---|---|---|
| Chốt hoặc chốt lại lịch đóng hợp lệ | Phụ huynh của học viên | Tên học viên, mã hóa đơn, số tiền và hạn từng đợt; mở hóa đơn của con |
| Còn tối đa 3 ngày đến hạn | Phụ huynh của học viên | Nhắc sắp đến hạn, đợt đóng và số còn thiếu |
| Đến hạn hôm nay | Phụ huynh của học viên | Nhắc đến hạn; mở hóa đơn để thanh toán hoặc xem QR hiện có |
| Quá hạn | Phụ huynh của học viên | Nhắc quá hạn và số còn thiếu |
| Thu tiền thành công | Phụ huynh và các tài khoản Admin, Nhân viên, Quản lý chi nhánh | Số biên lai, số tiền vừa thu và số còn thiếu; mỗi role mở đúng trang được cấp quyền |

Job học phí chạy sau 30 giây khi khởi động và lặp mỗi 60 giây sau khi hoàn tất lần trước. Chuông tải lại mỗi 15 giây và khi mở. Trang học phí tải lại chuông ngay sau thao tác thành công hoặc khi QR được xác nhận đã trả.

Thông báo nhắc hạn được chống lặp theo người nhận, kỳ đóng, phiên bản lịch và giai đoạn nhắc. Đọc thông báo không làm job gửi lại. Các thông báo học phí riêng đã có trước khi tích hợp cũng được đưa lên chuông một lần khi còn phù hợp. Hóa đơn đã hủy, cần kiểm tra hoặc kỳ đã đóng đủ không sinh nhắc hạn mới.

Thông báo thanh toán được tạo tại giao dịch ghi nhận khoản thu chung, áp dụng cho tiền mặt, xác nhận ngân hàng thủ công và webhook PayOS. Yêu cầu gửi lặp không sinh thêm biên lai hay thông báo thành công. Sự kiện ngân hàng chưa hợp lệ không được coi là đã thu tiền.

## Kiểm tra bằng tay

1. Nhân viên chốt lịch đóng của một hóa đơn chưa thu, đặt kỳ đầu đến hạn hôm nay. Đăng nhập phụ huynh của học viên bằng cửa sổ trình duyệt riêng; kiểm tra chuông có thông báo chốt lịch.
2. Chờ job chạy hoặc mở mục Học phí & thông báo. Kiểm tra chuông có thông báo đến hạn với đúng học viên, kỳ và số tiền. Bấm thông báo phải mở đúng hóa đơn.
3. Mở chuông nhiều lần hoặc tải lại trang; thông báo nhắc không tăng lặp. Đánh dấu đã đọc và kiểm tra lại sau lần job tiếp theo.
4. Thu kỳ đầu bằng tiền mặt, hoặc trả QR theo luồng hiện có. Kiểm tra phụ huynh và nhân viên đều nhận thông báo thành công với cùng số biên lai, số tiền và số còn thiếu.
5. Kiểm tra tài khoản phụ huynh khác không nhận thông báo của gia đình này. Những kỳ đã đóng đủ không nhận thêm nhắc hạn mới.

Kiểm thử tự động nằm trong `TuitionInstallmentFlowTest` (H2 riêng) và `scripts/tuition-installments-ui.test.cjs` (Chrome với API giả lập); không thực hiện khoản chuyển tiền thật.
