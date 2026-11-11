> **TÀI LIỆU PHIÊN BẢN CŨ:** Quy trình 48 giờ/gia hạn giữ chỗ/đối chiếu riêng đã được bỏ. Xem [nghiệp vụ và kịch bản hiện hành](NGHIEP_VU_HOC_PHI_DON_GIAN_VI.md).

# Đã triển khai chính sách giữ chỗ

Bản đề xuất dưới đây đã được triển khai cho xếp lớp mới. Hạn giữ chỗ, gia hạn/hủy có lịch sử, chặn hủy khi còn tiền/QR chưa rõ kết quả đã hoạt động. Hồ sơ cũ không bị gán hạn hồi tố. Hướng dẫn test đầy đủ: [MANUAL_TEST_GIU_CHO_HOC_PHI_VI.md](MANUAL_TEST_GIU_CHO_HOC_PHI_VI.md).

# Đề xuất xếp lớp, chốt học phí và hạn đóng

## Hiện trạng đã đối chiếu code

- Nhân viên xác nhận xếp lớp trong CourseEnrollmentService: tạo Enrollment PENDING_PAYMENT và Invoice UNPAID ngay trong cùng giao dịch; hóa đơn lấy học phí của khóa học.
- Xếp lớp mới có hạn giữ chỗ tối đa 48 giờ, giới hạn trước ngày bắt đầu lớp tương lai; khi chốt lịch còn kiểm tra trước buổi học đầu tiên đã xếp. Hạn hóa đơn là hạn kỳ cuối sau khi chốt.
- Chỗ trống hiện tính cả học viên đã ghi danh và người đang PENDING_PAYMENT. Hết hạn giữ chỗ hiển thị cảnh báo/chặn thu và chờ quản lý gia hạn/hủy; không tự xóa người giữ chỗ.
- Sau khi đóng đủ kỳ đầu, PaymentPostingService chuyển sang ENROLLED, tăng sĩ số một lần. Các kỳ sau theo dõi trên cùng hóa đơn, từng khoản thu có biên lai riêng.

## Luồng đã triển khai

1. Xếp lớp sau đánh giá đầu vào và tư vấn: tạo phiếu học phí chờ chốt, trạng thái ghi danh chờ thanh toán. Đây là nghĩa vụ học phí nội bộ, không khẳng định là hóa đơn thuế.
2. Mở Quản lý học phí, chốt học phí/ưu đãi và phương án một lần hoặc nhiều đợt. Trước khi chốt không phát hành yêu cầu thu.
3. Hạn đợt đầu: thỏa thuận tại quầy; gợi ý tối đa 48 giờ sau xếp lớp và trước buổi đầu học viên sẽ tham gia. Lớp sắp học hoặc đang học phải chọn hạn phù hợp thực tế, không sinh hạn trong quá khứ.
4. Phân biệt hạn giữ chỗ với hạn các kỳ sau. Hết hạn giữ chỗ mà chưa nhận tiền thì đưa vào danh sách quản lý xử lý: liên hệ phụ huynh, duyệt gia hạn hoặc hủy giữ chỗ. Không tự giải phóng khi đang có chuyển khoản chưa đối chiếu hay đã nhận một phần tiền.
5. Đóng đủ đợt đầu: ghi danh chính thức. Các đợt còn lại theo lịch chốt, có nhắc trước 3 ngày, đến hạn, quá hạn và QR cho đợt còn thiếu sớm nhất.
6. Quá hạn kỳ sau: nhắc nợ; quá 7 ngày đưa quản lý liên hệ. Không tự xóa khỏi lớp, mất lịch sử điểm danh hoặc coi hóa đơn là đã hủy.

Chính sách 48 giờ đã được người dùng duyệt và triển khai; việc hủy giữ chỗ vẫn do quản lý quyết định, không tự xóa hồ sơ.

## Giao diện và quyền đã cập nhật

- Một điểm thu tiền: Quản lý học phí. Xếp lớp mở thẳng hóa đơn tại đây. Trang cũ chuyển về đây, bao gồm link báo cáo cũ. Lịch sử thu và biên lai nằm trực tiếp trên từng hóa đơn.
- Mã phiếu hiển thị PT-yyyyMMdd-xxxxxx, dựa ngày thu và ID bản ghi, ổn định và không cắt UUID để tránh trùng. Mã kỹ thuật, mã ngân hàng và quan hệ dữ liệu giữ nguyên.
- Hóa đơn, thông báo, gia hạn, đối chiếu và điều chỉnh 5 mục/trang; biên lai hiển thị đầy đủ không phân trang; lọc đưa về trang đầu; mở từ invoiceId chọn đúng trang chứa hóa đơn.
- Điều chỉnh khoản thu nhầm chỉ ADMIN/BRANCH_MANAGER được xem, lập và duyệt ở cả API và giao diện. Không có role LEADER riêng trong hệ thống.
- Người lập không tự duyệt: cần một quản lý khác. Duyệt điều chỉnh chỉ loại khoản tiền mặt nhập sai khỏi số đã thu; không chuyển tiền trả khách hay xóa biên lai gốc.

## Tham khảo bố trí và nghiệp vụ

- Stripe: mã chứng từ có quy tắc/prefix và trình tự dễ tra cứu: https://support.stripe.com/questions/customize-your-invoice-number?locale=en-GB
- Square: hóa đơn có lịch nhiều đợt và nhắc theo hạn; biên lai sau khi thanh toán: https://developer.squareup.com/docs/invoices-api/create-publish-invoices
- Square: lịch sử giao dịch cho phép lọc và mở chi tiết từng giao dịch: https://js.squareup.com/help/us/en/article/5145-transaction-search
