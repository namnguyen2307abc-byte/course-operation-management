# Test từ xác nhận xếp lớp đến thanh toán

Các hồ sơ dưới đây được tạo ở trạng thái READY_FOR_ASSIGNMENT, chưa có ghi danh, hóa đơn hay khoản thu. Không dùng QA-SIMPLE-01..04 để bắt đầu luồng này vì các hồ sơ đó đã được xếp lớp trước.

Đăng nhập: http://127.0.0.1/login.html. Mật khẩu chung **Test@123456**.

| Tài khoản | Sử dụng |
|---|---|
| qa_simple_staff | Xác nhận xếp lớp → chốt lịch → thu tiền |
| qa_simple_parent | Xem lịch đóng, QR và biên lai của cả ba học sinh |
| qa_simple_admin | Hủy hồ sơ chưa nhận tiền sau khi liên hệ phụ huynh |

Nên dùng cửa sổ ẩn danh hoặc trình duyệt khác cho phụ huynh để không đổi phiên nhân viên.

## Bộ dữ liệu bắt đầu đúng bước chốt lớp

| Học sinh | Yêu cầu | Lớp cần chọn | Ca test |
|---|---|---|---|
| [E2E-01] Xếp lớp rồi thu tiền mặt | 41 | QA-E2E-01 / [E2E] Lớp 01 | Toàn bộ luồng tiền mặt |
| [E2E-02] Xếp lớp rồi đóng QR | 42 | QA-E2E-02 / [E2E] Lớp 02 | Thanh toán chuyển khoản |
| [E2E-03] Xếp lớp rồi hủy khi chưa đóng | 43 | QA-E2E-03 / [E2E] Lớp 03 | Chốt lớp rồi admin hủy trước khi nhận tiền |

Cả ba học sinh thuộc qa_simple_parent, học khóa **[HỌC PHÍ] Khóa demo 8.000đ**. Mỗi lớp đang mở, sĩ số tối đa 5; trước test không có học viên trong lớp. Tên/mã lớp hiển thị trong danh sách chọn.

## Kịch bản 1 — Xếp lớp → chốt lịch → thu tiền mặt → phụ huynh xem biên lai

1. Đăng nhập **qa_simple_staff**, mở **Đăng ký & xếp lớp**: http://127.0.0.1/pages/course-enrollment.html.
2. Tìm dòng **[E2E-01] Xếp lớp rồi thu tiền mặt**, bấm **Xếp lớp**.
3. Chọn **[E2E] Lớp 01 (QA-E2E-01)**, ghi chú tùy chọn, bấm **Xác nhận xếp lớp**.
4. Mong đợi: hệ thống tạo một lượt ghi danh chờ thanh toán và một hóa đơn 8.000đ; chuyển sang **Quản lý học phí**, cuộn tới đúng hóa đơn. Ghi lại mã hóa đơn mới để tra cứu; mã được hệ thống sinh lúc này, không phải QA-E2E-01.
5. Trên hóa đơn mới, bấm **Chốt lịch đóng**. Giữ mức giảm 0, chọn **3 kỳ**, nhập:
   - Đợt 1: **4000**, hạn hôm nay.
   - Đợt 2: **2000**, hạn sau 30 ngày.
   - Đợt 3: **2000**, hạn sau 60 ngày.
   - Bấm **Xác nhận**. Tổng ba đợt phải bằng 8.000đ; ngày tăng dần. Chưa nhận tiền nên chưa ghi danh chính thức.
6. Bấm **Thanh toán đợt 1** → **Tiền mặt tại quầy**. Số tiền **4000**, tiền khách đưa **5000**, bấm **Xác nhận đã thu tiền**.
   - Nếu hệ thống đã tạo QR do đến hạn trước khi bạn chọn tiền mặt: mở QR đang chờ → Hủy yêu cầu QR chưa thanh toán, rồi thu tiền mặt. Không thu tiền mặt song song với QR chưa rõ kết quả.
7. Mong đợi: đã đóng **4.000đ**, còn **4.000đ**, tiền thối **1.000đ**; có một biên lai; nút tiếp theo là **Thanh toán đợt 2**. Ghi danh chuyển chính thức và sĩ số lớp tăng đúng một lần.
8. Đăng nhập **qa_simple_parent** ở cửa sổ khác → **Học phí & thông báo** → tìm **E2E-01**. Phải thấy đúng lớp, lịch ba đợt, số tiền và biên lai vừa thu. Bấm **Xem biên lai**, thử **In biên lai**.
9. Quay lại nhân viên, thu **2000** đợt 2 rồi **2000** đợt 3 (có thể đóng trước hạn). Mong đợi: tổng đã thu **8.000đ**, còn **0đ**, trạng thái Đã đóng đủ; ba biên lai hiển thị đầy đủ, không phân trang; sĩ số không tăng thêm lần nữa.

## Kịch bản 2 — Xếp lớp → QR → tự cập nhật

1. Làm lại bước 1–5 với học sinh **[E2E-02] Xếp lớp rồi đóng QR**, chọn lớp **QA-E2E-02**. Không dùng lại học sinh E2E-01.
2. Đến Thanh toán đợt 1, chọn **Chuyển khoản QR / PayOS** → **Tạo mã QR**. Hoặc phụ huynh mở cùng hóa đơn để tạo/mở QR.
3. QR PayOS thu tiền thật **4.000đ**; chỉ chuyển tiền nếu bạn chủ động muốn thử giao dịch ngân hàng thật. Ứng dụng/ngrok phải đang chạy để nhận callback.
4. Sau khi nhận webhook hợp lệ: cửa sổ QR tự cập nhật, số đã đóng thành 4.000đ, còn 4.000đ; tạo biên lai, ghi danh chính thức. Phụ huynh xem được cùng kết quả.
5. Nếu ngân hàng đã ghi có nhưng callback chậm: nhân viên kiểm tra tài khoản trung tâm thực nhận, dùng **Xác nhận đã thu tiền**, nhập mã giao dịch, số tiền, lý do và xác nhận kiểm tra. Không dùng thao tác này để giả nhận tiền khi chưa chuyển.
6. Không chuyển tiền thì QR vẫn chờ, không có biên lai thành công và không được tự ghi danh. Muốn chuyển sang ca tiền mặt phải hủy QR chưa trả trước.

## Kịch bản 3 — Xếp lớp → phụ huynh không tiếp tục → admin hủy

1. Làm bước xếp lớp với học sinh **[E2E-03] Xếp lớp rồi hủy khi chưa đóng**, chọn **QA-E2E-03**.
2. Có thể chốt lịch ba đợt như trên nhưng **không thu tiền**. Hệ thống chưa nhận tiền nên vẫn chờ thanh toán.
3. Đăng nhập **qa_simple_admin**, tìm E2E-03 hoặc mã hóa đơn vừa sinh → **Hủy giữ chỗ**.
4. Nhập kết quả đã liên hệ phụ huynh, lý do không tiếp tục; tích xác nhận đã liên hệ và kiểm tra chưa nhận tiền → Xác nhận.
5. Mong đợi: QR chưa trả tự đóng nếu có; hóa đơn và ghi danh thành đã hủy; chỗ chờ được giải phóng; lịch sử người hủy/lý do còn nguyên; không có biên lai thu tiền.
6. Nhân viên và phụ huynh không có nút hủy dành riêng cho admin. Hồ sơ đã nhận tiền không được hủy bằng luồng này.

## Kiểm tra nhanh khi xếp lớp

- Chưa bấm Xác nhận xếp lớp: chưa có hóa đơn; hủy modal không tạo dữ liệu.
- Chọn lớp khác khóa học hoặc lớp đầy: backend phải từ chối.
- Xác nhận lại yêu cầu đã xử lý: không tạo ghi danh/hóa đơn thứ hai.
- Quá hạn đóng tiền: vẫn thanh toán được, không cần gia hạn giữ chỗ. Ca quá hạn 9 ngày sẵn có nằm ở QA-SIMPLE-02/03 trong bộ test đơn giản.

Seed thêm bộ này: `database/seed_enrollment_payment_e2e.sql`. Script chạy lại không reset học sinh đã xếp lớp hoặc khoản thu đã test.
