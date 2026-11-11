> **TÀI LIỆU PHIÊN BẢN CŨ:** Quy trình 48 giờ/gia hạn giữ chỗ/đối chiếu riêng đã được bỏ. Xem [nghiệp vụ và kịch bản hiện hành](NGHIEP_VU_HOC_PHI_DON_GIAN_VI.md).

# Kiểm thử thủ công: giữ chỗ, chốt lịch và thu học phí

Ứng dụng: http://127.0.0.1/login.html. Tải lại Ctrl+F5 sau khi cập nhật. Nên dùng các cửa sổ trình duyệt khác nhau hoặc chế độ ẩn danh cho các vai trò để tránh thay phiên đăng nhập.

## Tài khoản

Tất cả mật khẩu: **Test@123456**.

| Tên đăng nhập | Vai trò | Việc cần kiểm tra |
|---|---|---|
| qa_hold_staff | Nhân viên | Xếp lớp, chốt lịch, thu tiền, đề nghị gia hạn đợt sau |
| qa_hold_admin | Admin | Gia hạn/hủy giữ chỗ, duyệt gia hạn, xử lý đối chiếu |
| qa_hold_leader | Quản lý chi nhánh | Cùng nhóm quyền quản lý; duyệt điều chỉnh do admin khác đề nghị |
| qa_hold_parent | Phụ huynh sở hữu toàn bộ học sinh QA-HOLD | Xem lịch đóng, thông báo, QR và biên lai |
| qa_hold_other | Phụ huynh khác | Không được thấy học sinh/hóa đơn QA-HOLD |

Các khoản tiền mặt có sẵn là dữ liệu fixture, không phải tiền đã thu thực tế. Học phí demo **8.000đ**, chia **4.000đ + 2.000đ + 2.000đ**. QR từ PayOS là QR thật: chỉ thử chuyển tiền khi bạn chủ động muốn thực hiện giao dịch thật. Không dùng nút xác nhận thủ công để giả nhận tiền cho hồ sơ thực.

## Dữ liệu sẵn có

Tìm mã trong ô tìm kiếm của **Quản lý học phí**. Phụ huynh vào **Học phí & thông báo**.

| Mã | Trạng thái ban đầu | Mục tiêu |
|---|---|---|
| Học sinh [GIỮ CHỖ 01] Chờ xếp lớp | Yêu cầu READY_FOR_ASSIGNMENT, chưa có hóa đơn | Chạy trọn luồng xếp lớp → tạo hóa đơn |
| QA-HOLD-02 | Giữ chỗ còn hạn, chưa chốt lịch | Chốt ưu đãi/lịch đóng |
| QA-HOLD-03 | Hết hạn, chưa nhận tiền, không QR | Gia hạn giữ chỗ rồi thu đợt đầu |
| QA-HOLD-04 | Hết hạn, chưa nhận tiền, không QR | Hủy giữ chỗ, giải phóng chỗ |
| QA-HOLD-05 | Hết hạn, còn QR chưa thanh toán | Chặn hủy đến khi đóng QR |
| QA-HOLD-06 | Hết hạn, có tiền cần đối chiếu DEMO | Chặn hủy khi ngân hàng chưa rõ kết quả |
| QA-HOLD-07 | Đã đóng 4.000đ, đợt 2 quá hạn 9 ngày | Nhắc nợ, gia hạn đợt sau; vẫn giữ ghi danh |
| QA-HOLD-08 | Còn hạn, đã chốt 3 đợt, chưa đóng | Thanh toán bình thường và phụ huynh xem biên lai |
| QA-HOLD-09 | Đã đóng đủ, có 3 biên lai fixture | Kiểm tra lịch sử/biên lai, không phân trang biên lai |

Các mốc thời gian được tạo tương đối theo ngày seed: hạn đang còn là khoảng 36 giờ sau lúc seed; hạn quá là 3 giờ trước lúc seed; buổi đầu demo là 7 ngày sau ngày seed. Dữ liệu không tự reset kết quả bạn test.

## 1. Xếp lớp và sinh hóa đơn

1. Đăng nhập qa_hold_staff → Đăng ký & xếp lớp → yêu cầu của học sinh **[GIỮ CHỖ 01] Chờ xếp lớp**.
2. Chọn lớp **QA-HOLD-01 / [GIỮ CHỖ] Lớp 01**, xác nhận xếp lớp.
3. Ứng dụng chuyển sang Quản lý học phí, đúng hóa đơn mới.
4. Kiểm tra: chưa thanh toán, chờ chốt lịch, hạn giữ chỗ không quá 48 giờ. Học viên còn PENDING_PAYMENT, đang chiếm một chỗ giữ tạm.
5. Chốt 3 đợt 4.000/2.000/2.000; ngày đợt đầu hôm nay, các đợt sau sau 30/60 ngày. Hạn đợt đầu không vượt hạn giữ chỗ, ngày các đợt tăng dần.
6. Thu đợt đầu bằng tiền mặt 4.000đ. Nếu QR tự động đang chờ, mở QR và hủy QR chưa trả trước khi chọn tiền mặt.
7. Kiểm tra: ghi danh ENROLLED, tổng đã thu 4.000đ, còn 4.000đ, có một biên lai. Sĩ số tăng đúng một lần; phụ huynh thấy cùng khoản thu.

## 2. Chốt lịch và ưu đãi

1. qa_hold_staff tìm QA-HOLD-02, bấm Chốt lịch đóng.
2. Thử giảm theo phần trăm hoặc theo số tiền; nhập lý do nếu có giảm. Kiểm tra tổng các đợt bằng học phí sau ưu đãi.
3. Chọn hạn đợt đầu vượt hạn giữ chỗ: backend phải từ chối. Sau đó dùng hạn hôm nay và ngày các đợt sau hợp lệ để chốt.
4. Sau chốt mới có nút thanh toán. Phụ huynh thấy lịch và số tiền sau ưu đãi; khi đến hạn có thông báo/QR.

## 3. Hết hạn → gia hạn → thu đợt đầu

1. qa_hold_staff tìm QA-HOLD-03: thấy cảnh báo hết hạn, không được thu và không có quyền gia hạn giữ chỗ.
2. qa_hold_admin mở cùng mã → Gia hạn giữ chỗ. Nhập hạn tương lai, tối đa 48 giờ từ lúc xử lý, trước buổi đầu; ví dụ ngày mai lúc 10:00 (nếu vẫn trong 48 giờ).
3. Nhập lý do/kết quả liên hệ phụ huynh, xác nhận.
4. Kiểm tra cảnh báo hết hạn biến mất, lịch đợt đầu cập nhật theo hạn mới, Lịch sử giữ chỗ ghi người xử lý và hạn cũ/mới.
5. qa_hold_staff thu 4.000đ; nếu có QR chờ thì hủy QR chưa trả trước. Kiểm tra học viên được ghi danh và có biên lai.
6. Thử hủy giữ chỗ sau khi đã thu: hệ thống không cho hủy người đã nhận tiền/đã ghi danh.

## 4. Hết hạn → hủy giữ chỗ

1. qa_hold_admin tìm QA-HOLD-04 → Hủy giữ chỗ → nhập lý do phụ huynh không tiếp tục đăng ký.
2. Kiểm tra hóa đơn và ghi danh chuyển CANCELLED; giữ lịch sử thao tác và dữ liệu gốc.
3. Kiểm tra lớp QA-HOLD-04 không còn người PENDING_PAYMENT này giữ chỗ. Sĩ số đã nhập học không bị trừ nhầm.
4. Phụ huynh thấy đã hủy, không có nút thanh toán hay QR thu tiếp; không còn thông báo thu của hóa đơn đã hủy.

## 5. QR chưa rõ kết quả → chưa được hủy chỗ

1. qa_hold_admin tìm QA-HOLD-05, bấm Hủy giữ chỗ: phải báo còn QR/tiền chưa rõ kết quả.
2. Mở QR đang chờ → Hủy yêu cầu QR. Chỉ hủy khi chưa thanh toán và cổng xác nhận hủy.
3. Khi PayOS đã xác nhận CANCELLED, thử lại Hủy giữ chỗ và ghi lý do: lúc này được hủy.
4. Nếu gateway đang lỗi hoặc QR đang UNCERTAIN, không ép hủy giữ chỗ; phải kiểm tra cổng/ngân hàng trước.

## 6. Có tiền cần đối chiếu → chưa được hủy chỗ

1. qa_hold_admin tìm QA-HOLD-06 → thử Hủy giữ chỗ: phải bị chặn.
2. Mục Tiền ngân hàng cần đối chiếu có **DEMO-HOLD-BANK-06**, số tiền 4.500đ khác yêu cầu 4.000đ; ghi chú rõ dữ liệu giả lập.
3. Đây không phải tiền ngân hàng thật. Để đóng riêng hồ sơ demo, dùng **Ghi kết quả xử lý bên ngoài**, ghi rõ “DEMO, không có tiền thật; đóng hồ sơ đối chiếu demo”. Thao tác này không hoàn tiền và không giảm công nợ.
4. Sau khi không còn đối chiếu/QR đang chờ, có thể hủy giữ chỗ. Với hồ sơ thực tế, chỉ ghi đã xử lý bên ngoài khi thật sự đã xử lý, có căn cứ kiểm tra.

## 7. Đợt sau quá hạn, không xóa học viên

1. qa_hold_parent mở QA-HOLD-07: đã đóng 4.000đ, đợt 2 thiếu 2.000đ quá hạn; có thông báo và QR (job cập nhật mỗi phút).
2. qa_hold_staff thấy cảnh báo quá hạn trên 7 ngày; đề nghị Gia hạn kỳ 2 đến ngày mai, nhập lý do.
3. qa_hold_admin duyệt đề nghị. Kiểm tra lịch cũ/mới và lý do được lưu; thông báo cũ được thay theo lịch mới.
4. Học viên vẫn ENROLLED, không mất chỗ hay lịch sử học dù hạn giữ chỗ cũ đã qua.
5. Thu tiếp đợt 2 rồi đợt 3, mỗi lần có biên lai; còn thiếu lần lượt 2.000đ rồi 0đ. Nếu đang có QR và chọn tiền mặt, hủy QR chưa trả trước.

## 8. QR / xác nhận ngân hàng thủ công / lịch sử phụ huynh

1. qa_hold_parent mở QA-HOLD-08, xem lịch đóng và QR đợt đầu 4.000đ. Nếu QR chưa hiện, đợi một vòng job khoảng 1 phút hoặc mở Thanh toán đợt 1 để tạo QR.
2. Với chuyển khoản thật: quét đúng QR và thực hiện giao dịch bạn muốn thử. Webhook hợp lệ sẽ tự tạo biên lai và giảm công nợ; cửa sổ QR tự cập nhật.
3. Nếu ngân hàng đã ghi có nhưng webhook chậm: qa_hold_staff mở cùng QR → Xác nhận đã thu tiền. Nhập đúng mã giao dịch ngân hàng, số tiền, lý do; xác nhận đã kiểm tra tài khoản trung tâm thực nhận. Không chỉ dựa vào ảnh khách gửi.
4. Phụ huynh không có nút xác nhận thủ công. Sau thu, phụ huynh xem/in biên lai của chính mình.
5. Webhook đến muộn cùng mã tham chiếu không tạo khoản thu thứ hai. Khoản khác mã tham chiếu/bất thường được đưa đối chiếu.

## 9. Biên lai, phân trang và phân quyền

1. QA-HOLD-09 có 3 biên lai tiền mặt fixture. Mở Biên lai: chữ mã PT và thời gian/phương thức lớn hơn, không có phân trang biên lai.
2. Nhân viên/phụ huynh không thấy Điều chỉnh thu nhầm. qa_hold_admin có thể lập; qa_hold_leader duyệt. Người lập không tự duyệt. Duyệt chỉ điều chỉnh bản ghi, không chuyển tiền trả khách.
3. Danh sách hóa đơn vẫn 5 mục/trang; đổi bộ lọc về trang đầu. Không còn Lịch sử & báo cáo thu tiền. Link trang thu tiền cũ chuyển về Quản lý học phí.
4. qa_hold_other không thấy học sinh/hóa đơn QA-HOLD. Dán link invoiceId vào tài khoản này không được lộ dữ liệu.

## Chạy lại bộ seed

`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/seed-tuition-reservations.ps1`

Script có thể chạy lại nhưng không reset bộ dữ liệu đã test. Không dùng script để phục hồi hoặc xóa kết quả nghiệp vụ của bạn. Hồ sơ cũ ngoài QA-HOLD không được đổi hạn tự động; hạn giữ chỗ mới áp dụng cho xếp lớp mới và fixture này.
