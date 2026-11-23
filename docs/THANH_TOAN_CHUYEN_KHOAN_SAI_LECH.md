# Luồng chuyển khoản PayOS hiện tại

Cập nhật 23/11/2026: đã bỏ nghiệp vụ xử lý sai lệch chuyển khoản và màn Admin tương ứng. Tên tệp được giữ để các liên kết tài liệu cũ vẫn truy cập được thông tin cập nhật.

## Thanh toán VietQR Pro

1. Chốt học phí và lịch đóng như hiện tại; số phải thu cố định theo kỳ còn thiếu sớm nhất.
2. Bấm Thanh toán đợt → Thanh toán qua PayOS. Backend xác minh/hủy yêu cầu cũ chưa nhận tiền rồi tạo mã mới, chuyển cùng tab sang trang PayOS.
3. VietQR Pro dùng tài khoản ảo gắn với yêu cầu và số tiền. Nội dung chuyển khoản khác không tự làm giao dịch sai nếu gateway vẫn xác thực đúng mã đơn và số tiền.
4. Webhook có chữ ký hợp lệ hoặc dữ liệu tra cứu đã xác thực mới được dùng ghi nhận. Chỉ ghi đúng số phải thu của yêu cầu/kỳ, không tự phân bổ khoản thiếu/thừa.
5. Thành công: cập nhật học phí, ghi danh khi đủ kỳ đầu, biên lai và thông báo. Gửi lại cùng mã giao dịch không tạo thu lần hai.
6. Hủy hoặc quay về trang học phí: tự xác minh tiền vào rồi kết thúc yêu cầu chưa thu. Lần sau tạo mã mới. Đóng hẳn trình duyệt thì xử lý yêu cầu cũ khi bắt đầu lần thanh toán tiếp theo.

Ứng dụng không đặt hết hạn 5 phút mặc định. URL quay về local là http://127.0.0.1:5500, được cấu hình qua TUITION_PUBLIC_BASE_URL. Không tin tham số trạng thái trên URL để ghi nhận tiền.

## Các chức năng đã gỡ

- Tab/API xử lý sai lệch chuyển khoản của Admin.
- Nhập sao kê thủ công, tìm học viên để phân bổ tiền, duyệt hoàn tiền thừa.
- Tự ghi chuyển thiếu, tạo QR bổ sung từ khoản chuyển thiếu, phân bổ chuyển thừa.
- Thông báo và liên kết đến hàng đợi Admin đã gỡ.

Không thay đổi điều chỉnh thu nhầm tiền mặt, phân quyền thu ngân, ưu đãi, chia kỳ, webhook, chống gửi lặp hoặc xác minh ngân hàng khi hủy.

## Audit và cấu trúc code

BankTransferService/interface + impl xử lý giao dịch gateway đã xác thực; BankTransferPolicy kiểm tra tiền/điều kiện học phí; PaymentIntentLedgerRepository chỉ truy vấn kỳ, lịch và yêu cầu liên quan. PaymentResultService xác thực webhook và điều phối. Controller giữ nhận DTO/quyền/gọi interface. Không thay GlobalExceptionHandler dùng chung.

Nếu gateway trả số tiền/mã đơn/trạng thái bất thường: giữ bản ghi sự kiện, không tăng học phí, không tự tạo biên lai hoặc hoàn tiền. RECONCILE còn lại chỉ là trạng thái audit/chặn ghi nhận không an toàn, không phải hàng đợi Admin. Các cột lịch sử và migration cũ được giữ tương thích database; không xóa giao dịch hoặc lịch sử hoàn/đối soát đã tồn tại.

Tài liệu PayOS: https://payos.vn/docs/cau-hoi-thuong-gap/ và https://payos.vn/docs/api/.
