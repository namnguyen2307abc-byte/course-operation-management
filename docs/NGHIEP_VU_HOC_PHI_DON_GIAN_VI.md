# Nghiệp vụ học phí thống nhất — bản đơn giản

Tài liệu này thay thế quy trình giữ chỗ 48 giờ và đối chiếu ngân hàng riêng trong tài liệu cũ.

## Quy trình

1. Nhân viên xác nhận xếp lớp → hệ thống tạo hóa đơn chưa thanh toán, ghi danh ở trạng thái chờ đóng tiền.
2. Nhân viên chốt ưu đãi, đóng một lần hoặc tối đa 3 đợt, số tiền/hạn từng đợt theo trao đổi phụ huynh. Không còn hạn giữ chỗ 48 giờ.
3. Đóng đủ đợt đầu → ghi danh chính thức. Các lần thu sau cùng hóa đơn; mỗi giao dịch thành công có biên lai riêng.
4. Đến hạn/quá hạn: vẫn cho đóng tiền. Không yêu cầu gia hạn giữ chỗ, không tự hủy, không xóa khỏi lớp. Thông tin quá hạn hiển thị trên từng hóa đơn; dữ liệu quá hạn từ **7 ngày** vẫn có API dành cho module thông báo tích hợp sau. Không có khu vực Quá hạn cần liên hệ riêng trong giao diện.
5. Admin xem hóa đơn/học phí, đợt thiếu và điện thoại phụ huynh; tự liên hệ để giải quyết. Hiện chưa gửi thông báo admin qua module của thành viên khác.
6. Nếu phụ huynh không tiếp tục và trung tâm chưa nhận tiền: chỉ **ADMIN** được Hủy giữ chỗ, nhập kết quả liên hệ/lý do và xác nhận đã liên hệ, kiểm tra chưa nhận tiền.
7. Khi hủy, hệ thống tự đóng QR chưa trả tại cổng trước, rồi đánh dấu hóa đơn/ghi danh đã hủy, giải phóng chỗ chờ và giữ lịch sử. Nếu cổng báo đã có tiền hoặc chưa rõ kết quả, không âm thầm hủy.
8. Người đã đóng tiền/đã ghi danh chính thức không đi qua nút hủy này. Quá hạn các đợt sau chỉ liên hệ, thu tiếp; không tự hủy ghi danh.

## Thanh toán ngân hàng

- Không còn màn hình Tiền ngân hàng cần đối chiếu hay API phân bổ/ghi xử lý bên ngoài riêng.
- QR đúng số tiền + webhook xác thực → tự tạo khoản thu/biên lai, giảm công nợ.
- Ngân hàng đã ghi có nhưng webhook chậm/lỗi → nhân viên kiểm tra tài khoản trung tâm, dùng **Xác nhận đã thu tiền**, nhập mã ngân hàng, số tiền và lý do. Phụ huynh không có quyền này.
- Callback có số tiền bất thường không tự giảm công nợ. Admin mở QR có trạng thái Cần kiểm tra ngân hàng và dùng cùng thao tác xác nhận số tiền thực nhận sau khi kiểm tra, không có quy trình đối chiếu riêng. Khoản vượt số dư hoặc không xác định hóa đơn không được cộng tiền tùy ý.
- Dữ liệu webhook, xác minh chữ ký, mã ngân hàng duy nhất và khóa hóa đơn vẫn giữ để không mất thông tin/chống thu trùng. Đây là kiểm tra kỹ thuật, không phải phân hệ đối chiếu cho người dùng.

## Chờ ghép module thông báo

API **GET /api/tuition-payment/overdue-follow-ups**, chỉ ADMIN:

`invoiceId, invoiceCode, student, parent, phone, sequence, dueDate, remaining, overdueDays`

Ngưỡng hiện tại: >=7 ngày, còn thiếu và hóa đơn đã chốt/chưa hủy. Module thông báo có thể dùng khóa `invoiceId + sequence + dueDate` để chống gửi trùng. API chỉ cung cấp dữ liệu; không tự gọi điện/gửi tin, không tự hủy hay thay trạng thái.

## Tài khoản demo mới

URL: http://127.0.0.1/login.html. Tất cả mật khẩu **Test@123456**.

| Tài khoản | Vai trò |
|---|---|
| qa_simple_staff | Xếp lớp, chốt lịch, thu tiền |
| qa_simple_admin | Xem danh sách cần liên hệ; hủy sau khi liên hệ |
| qa_simple_leader | Quản lý chi nhánh; không có quyền hủy dành riêng cho admin |
| qa_simple_parent | Phụ huynh sở hữu học sinh demo |
| qa_simple_other | Phụ huynh khác, không thấy dữ liệu demo |

Số **0900000000** là số demo để kiểm tra hiển thị, không dùng để gọi thật. Học phí demo **8.000đ = 4.000 + 2.000 + 2.000**. Khoản tiền mặt sẵn có là fixture, không phải khoản thu thật. QR PayOS là QR thật: chỉ chuyển tiền nếu bạn chủ động muốn thử giao dịch thật.

## Kịch bản tự test

### A. Xếp lớp → chốt lịch → thanh toán

- qa_simple_staff vào Đăng ký & xếp lớp, chọn học sinh **[HỌC PHÍ 05] Chờ xếp lớp**, xếp lớp **QA-SIMPLE-05**.
- Kết quả: mở hóa đơn mới ở Quản lý học phí; không có hạn 48 giờ/gia hạn giữ chỗ.
- Chốt 3 đợt 4.000/2.000/2.000, hạn đợt đầu hôm nay, các đợt sau tăng dần và trong thời gian khóa; thu đợt đầu.
- Đóng đủ đợt đầu chuyển ghi danh chính thức đúng một lần; phụ huynh xem cùng lịch và biên lai.

### B. Chốt lịch từ dữ liệu sẵn

- qa_simple_staff tìm **QA-SIMPLE-01** → Chốt lịch đóng; thử giảm theo % hoặc số tiền và lý do.
- Trước chốt không thu; sau chốt có thanh toán theo đợt. Không có nút gia hạn giữ chỗ hay màn hình đối chiếu riêng.

### C. Quá hạn đợt đầu nhưng vẫn đóng được

- **QA-SIMPLE-02** chưa đóng, đợt đầu quá hạn 9 ngày.
- qa_simple_admin tìm hóa đơn này trong mục Học phí, xem cảnh báo quá hạn và số điện thoại demo. Chưa có thông báo admin qua module khác.
- Phụ huynh vẫn mở QR/đóng tiền; staff vẫn thu được, không cần gia hạn. Nếu chọn tiền mặt và đang có QR chờ thì đóng QR chưa trả trước.
- Sau thu đủ 4.000đ → ghi danh chính thức, còn 4.000đ, có biên lai. Hồ sơ rời danh sách quá hạn đợt đầu.

### D. Admin liên hệ rồi hủy hồ sơ chưa đóng

- Dùng **QA-SIMPLE-02 trước khi thực hiện C**, hoặc dùng học sinh 05 sau xếp lớp nhưng chưa nhận tiền. C và D là hai kết quả lựa chọn cho cùng hồ sơ, không thực hiện nối tiếp sau khi đã thu.
- Staff/Quản lý chi nhánh không thấy nút hủy; gọi API hủy bằng tài khoản đó cũng bị chặn.
- qa_simple_admin bấm Hủy giữ chỗ; chưa tích xác nhận liên hệ thì không gửi được.
- Nhập kết quả liên hệ, tích đã liên hệ và chưa nhận tiền, xác nhận. QR chưa trả tự đóng; hóa đơn và ghi danh thành đã hủy; lịch sử có người/lý do/thời điểm; chỗ chờ được giải phóng.
- Không cần bấm gia hạn/hủy QR riêng nếu cổng đang hoạt động bình thường. Nếu cổng không xác nhận được trạng thái, kiểm tra ngân hàng trước, không ép hủy.

### E. Quá hạn đợt sau, không hủy người đã học

- **QA-SIMPLE-03** đã đóng 4.000đ và ghi danh, đợt 2 quá hạn 9 ngày.
- Admin xem thông tin quá hạn ngay trên hóa đơn; không có nút Hủy giữ chỗ trên học viên đã nhận tiền.
- Thu tiếp 2.000đ → còn 2.000đ; thu đợt cuối → hết nợ. Ghi danh/lịch sử vẫn giữ, mỗi lần thu có biên lai riêng.
- Đổi hạn đóng theo đợt (nếu cần và vẫn dùng chức năng đề nghị lịch đóng) là việc riêng; không bắt buộc để được thanh toán quá hạn.

### F. Biên lai và giới hạn quyền

- **QA-SIMPLE-04** đã đóng đủ và có 3 biên lai. Phụ huynh xem/in được, chữ mã phiếu/ngày/phương thức dễ đọc, không phân trang biên lai.
- qa_simple_other không thấy hóa đơn/học sinh của qa_simple_parent.
- Nhân viên/phụ huynh không thấy điều chỉnh thu nhầm; quyền điều chỉnh đã có trước không thay đổi.

## Seed và dữ liệu cũ

Chạy `sqlcmd -S localhost -E -C -d course_operation_management -b -f 65001 -i database/seed_simple_tuition.sql`.

Seed mới có tiền tố QA-SIMPLE, không reset kết quả và không đụng hồ sơ cũ. Bộ QA-HOLD và tài liệu gia hạn 48 giờ là dữ liệu/tài liệu phiên bản trước; không dùng chúng làm hướng dẫn nghiệp vụ hiện hành. Lịch sử gia hạn/hủy cũ được giữ để tra cứu.

## Tab Điều chỉnh khoản thu nhầm

Admin/Quản lý chi nhánh mở Quản lý học phí → tab Điều chỉnh khoản thu nhầm ở đầu trang. Tab có số đề nghị chờ duyệt, tìm theo mã phiếu/tên học viên/người lập/lý do (hỗ trợ không dấu), lọc Chờ duyệt/Đã duyệt/Đã từ chối và phân trang 5 đề nghị/trang. Quyền và kiểm tra người lập không tự duyệt vẫn do backend thực thi. Nhân viên/phụ huynh không có tab này. Không còn khu vực Quá hạn cần liên hệ riêng; thông tin quá hạn vẫn có trong tab Học phí.
