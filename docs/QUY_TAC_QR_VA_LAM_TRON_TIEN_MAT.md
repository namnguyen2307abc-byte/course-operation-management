# QR và làm tròn tiền trả lại

Số tiền thanh toán cố định theo số còn thiếu của kỳ đầu tiên chưa hoàn tất. Backend đối chiếu trên dữ liệu mới nhất; sửa số tiền qua API bị từ chối.

## Ưu đãi và giao diện học phí

- Phần trăm giảm chỉ nhận số nguyên từ 0 đến 100. Giảm theo số tiền chỉ nhận số nguyên từ 1.000đ đến học phí gốc; cả frontend và backend từ chối số âm, thập phân và vượt giới hạn. Không có ưu đãi thì dùng 0%.
- Trang học phí bỏ bộ lọc “Cần đối chiếu”, nút gia hạn từng kỳ và mục đề nghị/duyệt gia hạn. Dữ liệu lịch sử và các kiểm tra ngăn ghi nhận sai tiền tại backend vẫn được giữ.
- Nhân viên chỉ được đề nghị điều chỉnh khoản tiền mặt do chính tài khoản mình thu; admin được đề nghị mọi khoản tiền mặt hợp lệ. Backend lấy người thu từ giao dịch lưu trong database, không tin dữ liệu người thu từ trình duyệt. Người gửi không được tự duyệt đề nghị của mình.
- Tài khoản `d26_staff_2` được thêm trong `database/seed_demo_tong_hop.sql` để test quyền giữa hai nhân viên. Chạy lại file gộp chỉ bổ sung tài khoản nếu chưa có, không reset giao dịch đang demo.

## Tiền mặt

- Tiền khách đưa là số nguyên, không nhỏ hơn khoản cần thu.
- Đưa đúng khoản cần thu được chấp nhận, kể cả khoản lẻ như 4.701đ.
- Đưa dư phải ghép được từ các mệnh giá từ 200đ trở lên. Với các mệnh giá 200đ và 500đ, đó là các bội số 100đ, trừ riêng 100đ và 300đ.
- Tiền trả lại được làm tròn **lên** mức nhỏ nhất ghép được từ các mệnh giá trên. Hệ thống không kiểm kê số lượng từng tờ tiền trong quầy.
- `payments.amount` là học phí ghi nhận để giảm công nợ; `change_amount` là tiền thực trả; `cash_rounding_amount` là khoản trung tâm hỗ trợ. Tiền thực thu = `amount - cash_rounding_amount` = `cash_given - change_amount`.
- Biên lai, thẻ thực thu, dashboard và báo cáo thu tiền dùng đúng các giá trị này. Đề nghị điều chỉnh được duyệt sẽ loại cả khoản thu và hỗ trợ khỏi báo cáo, khôi phục đúng học phí phải đóng.

| Cần thu | Khách đưa | Thực trả | Hỗ trợ | Thực thu |
|---:|---:|---:|---:|---:|
| 4.500 | 5.000 | 500 | 0 | 4.500 |
| 4.700 | 5.000 | 400 | 100 | 4.600 |
| 4.850 | 5.000 | 200 | 50 | 4.800 |
| 4.701 | 4.701 | 0 | 0 | 4.701 |

## Vòng đời QR

- Không có nút “Mở QR đang chờ”. Chọn “Thanh toán đợt N” và tạo QR mới. QR cũ chưa trả được đóng tại PayOS trước khi phát hành mã khác.
- Đóng hộp QR hoặc nhấn Escape gửi lệnh hủy và đợi xác nhận. Rời trang/đóng tab gửi yêu cầu `fetch keepalive`; trình duyệt bị tắt đột ngột hoặc mất mạng có thể không gửi được.
- PayOS được truyền `expiredAt` với thời hạn mặc định 300 giây; job backend kiểm tra yêu cầu quá hạn mỗi 30 giây. Có thể cấu hình `tuition.qr-lifetime-seconds` cho cả hai phía backend/gateway.
- Không hủy chỉ vì chuyển tab hoặc mở ứng dụng ngân hàng. Trang vẫn tự kiểm tra kết quả khi QR đang mở.
- Hủy lặp không phát sinh giao dịch. Nếu tiền đến khi đang hủy, giữ kết quả PAID; khi webhook chậm, tra cứu gateway có giao dịch ngân hàng để khôi phục. Không thu tiền mặt tiếp, kể cả khi kỳ sau có số tiền bằng kỳ vừa đóng.
- Kết quả ngân hàng chưa rõ thì giữ chặn tạo mã/thu lại; giao dịch tới muộn của QR đã hủy hoặc sai phiên bản lịch được lưu để xác minh, không tự giảm công nợ thêm lần nữa.

## Nâng cấp database

Chạy `database/migrations/20261114_cash_rounding.sql` trên database hiện có trước khi chạy bản backend mới với `ddl-auto=validate`. Migration chỉ thêm cột hỗ trợ làm tròn, mặc định 0 cho giao dịch cũ; không reset dữ liệu. Schema khởi tạo `database/init_database.sql` cũng đã bổ sung cột này.

Sau khi cập nhật code, Rerun backend trong IntelliJ và Ctrl+F5 frontend. Không cần đổi URL ngrok/webhook.
