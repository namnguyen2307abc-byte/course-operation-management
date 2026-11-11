> Nghiệp vụ hiện hành đã đơn giản hóa; xem [NGHIEP_VU_HOC_PHI_DON_GIAN_VI.md](NGHIEP_VU_HOC_PHI_DON_GIAN_VI.md). Không dùng quy trình giữ chỗ 48 giờ hoặc đối chiếu riêng ở các ghi chú cũ bên dưới.

# Thu học phí theo kỳ

Nhánh triển khai: `feature/tuition-installments`.

## Cài đặt

1. Backup SQL Server trước khi cập nhật.
2. Chạy `database/tuition_installments.sql`. Script có thể chạy lại, thêm bảng kỳ, phân bổ, intent, đối chiếu, gia hạn, thông báo và đề nghị đảo bản ghi tiền mặt.
3. Build backend với JDK 17/21, chạy với `--app.seed.enabled=false --spring.jpa.hibernate.ddl-auto=validate`.
4. Chỉ bật `--tuition.simulation.enabled=true` ở môi trường demo. Mô phỏng được ghi rõ và chỉ ADMIN gọi API mô phỏng. QR mô phỏng không yêu cầu chuyển tiền thật.
5. Để dùng PayOS thật, tắt simulation, cấu hình client-id/api-key/checksum-key qua môi trường hiện có, `tuition.public-base-url` là URL frontend công khai và cấu hình webhook công khai về `/api/tuition-payment/payos-webhook`.
6. Gateway lỗi mạng không tự chuyển sang mô phỏng. Trạng thái UNCERTAIN cần kiểm tra/hủy đơn ở cổng trước khi tạo lại.

Nguồn API PayOS: https://payos.vn/docs/api/ và https://payos.vn/docs/du-lieu-tra-ve/webhook/.
URL quay lại chỉ hiển thị kết quả và hỏi trạng thái. Webhook có chữ ký hợp lệ mới ghi nhận tiền.

### PayOS thật qua ngrok

1. Chạy backend phục vụ cả giao diện ở cổng 80, với `--tuition.simulation.enabled=false`.
2. Chạy `ngrok http 80`, lấy HTTPS origin được cấp (không kèm đường dẫn).
3. Khởi động backend với `--tuition.public-base-url=https://<ten-mien-ngrok>` để return/cancel URL trỏ về cùng hệ thống.
4. Chạy `python scripts/register-payos-webhook.py https://<ten-mien-ngrok>`. Script đọc merchant credentials từ cấu hình/môi trường hiện có, không in khóa. Chỉ hoàn tất khi PayOS trả xác nhận thành công. Đăng ký lại khi URL tunnel thay đổi.
5. Đăng nhập tại `http://127.0.0.1/login.html`, chọn **Thanh toán đợt N → Chuyển khoản → Tạo mã QR**. Quét mã trong ứng dụng ngân hàng. QR này thu tiền thật; dùng hồ sơ và số tiền đã xác nhận.
6. Sau khi ngân hàng báo thành công và webhook hợp lệ đến, hệ thống ghi giao dịch/biên lai, phân bổ tiền và giảm công nợ. Cửa sổ QR tự hỏi trạng thái mỗi 4 giây, tự đóng và cập nhật đợt tiếp theo. Không cần bấm kiểm tra kết quả.

### Nhắc hạn, QR và xác nhận ngân hàng thủ công

- Job mỗi phút tạo thông báo trước hạn 3 ngày, đến hạn và quá hạn. Khi đến hạn, job tạo/reuse QR cho đợt còn thiếu sớm nhất, gắn vào thông báo đúng tài khoản phụ huynh. Không cần nhân viên gửi link thủ công. Việc tạo QR không ghi nhận khoản thu.
- QR đang chờ được tái sử dụng, không tạo link mới mỗi lần chạy job. QR đã hủy không được tái phát hành qua cùng thông báo; phụ huynh có thể chủ động mở thanh toán lại.
- Phụ huynh vào **Học phí & thông báo**, xem QR, lịch các đợt, số tiền còn thiếu và biên lai từng lần thu của các con thuộc mình. Đã đóng đủ đợt thì thông báo cũ không còn hiển thị. Nếu PayOS lỗi, thông báo vẫn được giữ và có lối mở thanh toán/liên hệ trung tâm.
- Nếu ngân hàng đã ghi có mà webhook chưa đến, STAFF/ADMIN/BRANCH_MANAGER mở QR → **Xác nhận đã thu tiền**. Bắt buộc mã tham chiếu ngân hàng, số tiền khớp QR, lý do và xác nhận đã kiểm tra tài khoản ngân hàng trung tâm thực nhận; không chỉ dựa vào ảnh khách gửi.
- Backend lưu người xác nhận, thời điểm và lý do. Xác nhận này ghi nhận phương thức chuyển khoản, không phải tiền mặt. Phụ huynh không có API xác nhận thủ công.
- Khóa hóa đơn và mã tham chiếu duy nhất chống ghi nhận trùng. Webhook đến sau cùng mã tham chiếu chỉ trả lại giao dịch đã có. Nếu webhook mang mã tham chiếu khác cho QR đã xử lý, đưa vào đối chiếu, không cộng thêm tiền tự động.
- Chỉ xác nhận thủ công QR thật ở trạng thái PENDING/UNCERTAIN; sai số tiền hoặc đã cần đối chiếu phải đi qua luồng đối chiếu của quản lý.
- Nhân viên thu tiền mặt khi có QR đang chờ phải mở và hủy QR trước. Nút “Chọn tất toán” đã bỏ; số tiền nhập vẫn không được vượt công nợ.

Tunnel và backend phải cùng chạy để nhận callback. Callback trùng cùng tham chiếu không thu hai lần; sai số tiền hoặc không khớp số dư được đưa vào đối chiếu. Callback giả bị từ chối. Dữ liệu mẫu có chữ ký PayOS gửi khi đăng ký URL chỉ kiểm tra kết nối, không tạo khoản thu hay hồ sơ đối chiếu.

Kiểm tra kết nối/QR thật và kiểm thử callback tự động không đồng nghĩa đã chuyển tiền ngân hàng thật. Giao dịch thật chỉ được xác nhận khi PayOS gửi webhook cho đúng khoản tiền đã chuyển.

## Chính sách bản này

- Tối đa ba kỳ, tổng bằng học phí sau ưu đãi, kỳ đầu tối thiểu 50%.
- Chốt ưu đãi trước khi chia kỳ; đã thu tiền thì khóa chỉnh ưu đãi thông thường.
- Ngày kỳ tăng dần, từ hôm nay và trong thời gian khóa nếu lớp có ngày kết thúc.
- Lần thu đầu cần đủ kỳ đầu; kỳ sau cho đóng một phần, nhiều kỳ hoặc tất toán.
- Phân bổ theo thứ tự kỳ. Chỉ tiền SUCCESS chưa bị đảo mới giảm công nợ.
- Đủ kỳ đầu kích hoạt ghi danh và tăng sĩ số một lần. Thu các kỳ sau không tăng lại.
- Job nhắc hạn mỗi phút, thông báo trước ba ngày/đúng hạn/quá hạn. Mốc quá hạn là ngày sau hạn ở múi giờ Việt Nam; từ bảy ngày quá hạn đưa vào diện cần quản lý xử lý.
- Không tính lãi/phạt, không tự xóa hoặc đình chỉ học vì nợ.
- Gia hạn cần quản lý duyệt, có lịch cũ/mới và version. Có tiền mới trong lúc chờ duyệt thì đề nghị cũ cần lập lại.
- Đảo thu sai áp dụng bản ghi tiền mặt, cần người quản lý khác người đề nghị duyệt. Dữ liệu thu gốc được giữ và đánh dấu đã đảo trong biên lai. Ghi danh và lịch sử học không bị tự xóa.

## Màn hình và quyền

- Nhân viên/quản lý/admin: `/pages/tuition-receivables.html`.
- Phụ huynh: `/pages/parent-tuition.html`, chỉ dữ liệu học viên đang thuộc mình.
- Quầy thu học phí cũ tiếp tục tra cứu và báo cáo, nút thu tiền mở màn hình dùng chung theo kỳ.
- Biên lai từng lần có số tiền, người thu hoặc nguồn PayOS, tiền thối và số dư tại lúc giao dịch. Biên lai cũ chưa có snapshot số dư được ghi rõ.
- QR chờ xử lý được lưu và mở lại được sau khi tải trang.
- Tiền ngân hàng sai số tiền/vượt số dư được lưu ở danh sách đối chiếu. Quản lý có thể phân bổ sau xác minh hoặc ghi kết quả xử lý ngoài ứng dụng.
- Bản ghi đối chiếu có người xử lý, thời điểm và ghi chú. Không có module dừng học/chi hoàn tự động.

## Dữ liệu cũ

Khi hóa đơn được đọc lần đầu hoặc job xử lý, dịch vụ tạo kế hoạch một kỳ từ dữ liệu cũ và phân bổ các giao dịch thành công hợp lệ. Hóa đơn PAID nhưng tiền không khớp, hoặc thu vượt nghĩa vụ, được đánh dấu cần đối chiếu và chặn thu tiếp. Không tạo giao dịch giả để khớp.
Các bảng hoàn phí cũ trong database local không được tính năng này sử dụng.

## Demo local

Chạy ứng dụng trước rồi:
`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/seed-tuition-installments.ps1`

Tài khoản demo, cùng mật khẩu `Test@123456`:
- `qa_inst_parent`: phụ huynh.
- `qa_inst_other`: phụ huynh không sở hữu hồ sơ demo.
- `qa_inst_staff`: thu ngân.
- `qa_inst_admin`: quản lý/admin và nút mô phỏng QR.

Hồ sơ:
1. Minh An: chưa đóng, lịch 3 triệu + 1,5 triệu + 1,5 triệu. Thu đầu 3 triệu, kỳ hai thu 1 triệu, lần cuối 2 triệu để thấy phân bổ 500 nghìn và 1,5 triệu.
2. Khánh Linh: đã đóng kỳ đầu, kỳ hai quá hạn. Phụ huynh thấy thông báo; staff đề nghị gia hạn; admin duyệt.
3. Thanh Hà: đã đóng kỳ đầu. Parent tạo QR cho kỳ hai. Khi chạy PayOS thật, chuyển khoản sẽ được webhook xác nhận tự động. Chỉ trong môi trường bật simulation, admin mới có nút Mô phỏng nhận tiền; parent kiểm tra biên lai và số dư.
4. Bảo Nam: dùng kiểm thử thu đồng thời. Có thể đã tất toán do smoke test, không reset dữ liệu khi chạy seed lại.

Seed chỉ tạo bộ có prefix QA-INST và không xóa kết quả đã test.

## Kiểm thử

- Backend: `scripts/test-backend.ps1` (H2 biệt lập).
- Unit frontend: `node --test scripts/frontend-integration.test.cjs scripts/class-list-loading.test.cjs`.
- Chrome UI: `node --test scripts/tuition-installments-ui.test.cjs` (Playwright và Chrome).
- `node scripts/sync-frontend.mjs --check`.
- Cần smoke trên SQL Server thật cho migration, đọc hóa đơn cũ, thu, replay idempotency và hai yêu cầu đồng thời.
- Mô phỏng không chứng minh giao dịch PayOS thật. Kết nối thật cần webhook URL công khai và merchant credentials hợp lệ.

## Giữ chỗ và dữ liệu kiểm thử mới

- Chạy thêm migration `database/tuition_reservations.sql` trước khi khởi động bản có giữ chỗ.
- Xếp lớp mới giữ chỗ tối đa 48 giờ; trước buổi học đã xếp. Quá hạn chờ quản lý gia hạn/hủy, không tự xóa học viên. Không hủy khi đã có tiền hoặc còn QR/đối chiếu chưa rõ kết quả.
- Biên lai không phân trang; các danh sách khác vẫn 5 mục/trang. Đã bỏ trang báo cáo thu tiền cũ khỏi giao diện.
- Bộ demo mới: `scripts/seed-tuition-reservations.ps1`; tài khoản và các bước: [MANUAL_TEST_GIU_CHO_HOC_PHI_VI.md](MANUAL_TEST_GIU_CHO_HOC_PHI_VI.md).
