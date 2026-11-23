# Kịch bản test tay thanh toán học phí 30.000đ

Áp dụng cho khóa **Piano nền tảng**, mã **PIA-CB-12** trong `database/seed_demo_tong_hop.sql`. File chung đặt học phí danh mục thành 30.000đ khi nạp mới hoặc chạy lại; không sửa hóa đơn, kỳ thu, ưu đãi hoặc biên lai đã tạo.

## Chuẩn bị và cách đối chiếu

1. Chạy file SQL chung trên database demo có schema ứng dụng. Không cần reset để đổi riêng giá khóa học. Kiểm tra kết quả cuối file: PIA-CB-12 có `tuition_fee = 30000`.
2. Đăng nhập `d26_parent_new` / `Test@123456`, dùng một học viên chưa có đăng ký đang xử lý hoặc ghi danh trùng lớp. Có thể dùng phụ huynh khác trong file chung hoặc tạo học viên demo mới qua chức năng quản lý học viên nếu tài khoản được cấp quyền. Không dùng lại một hóa đơn đã thu để test độc lập.
3. Gửi yêu cầu **không qua Placement Test** cho khóa PIA-CB-12. Đăng nhập `d26_staff` / `Test@123456`, xử lý và xếp vào lớp thuộc khóa này còn chỗ, chưa kết thúc. Có thể dùng `PIA-MD-2701-01` nếu còn chỗ và còn hạn; nếu không, Admin/Staff tạo lớp phù hợp. Tên lớp không thay thế mã khóa học khi kiểm tra.
4. Mở Quản lý học phí, kiểm tra **học phí gốc của hóa đơn mới là 30.000đ** trước khi chốt. Hóa đơn cũ vẫn giữ giá cũ. Nếu đã có ưu đãi Placement Test hợp lệ, dùng nhóm test ưu đãi bên dưới thay cho giả định không giảm.
5. Cho mỗi ca chuyển khoản độc lập, chuẩn bị **hóa đơn/kỳ chưa thu** tương ứng, ghi mã hóa đơn, học viên, lớp, hạn, số phải thu, mã QR/order và mã giao dịch ngân hàng. Các ca trong bảng không thực hiện nối tiếp trên cùng kỳ, trừ khi ghi rõ.
6. Dùng các cửa sổ thường/ẩn danh hoặc trình duyệt khác nhau cho Parent, Staff và Admin để tránh đổi phiên đăng nhập của nhau. Admin: `d26_admin`; thu ngân thứ hai: `d26_staff_2`; cùng mật khẩu `Test@123456`.

Sau mỗi ca, kiểm tra ở cả Parent và Staff/Admin: số đã ghi nhận, còn thiếu từng kỳ, tổng công nợ, biên lai, trạng thái ghi danh và thông báo liên quan. **Chốt lịch không phải thu tiền**. Trả tiền thừa không được tính thành học phí. Đóng đủ một kỳ chưa có nghĩa là đóng đủ cả khóa.

## 1. Chốt lịch và validation số tiền

Chọn **Không giảm**: học phí phải đóng T = 30.000đ. Dùng các hạn tăng dần, từ hôm nay đến ngày kết thúc lớp.

| Mã | Nhập/thao tác | Kết quả cần thấy |
| --- | --- | --- |
| L01 | Một kỳ 30.000đ | Chốt được; đã thu vẫn 0đ, còn nợ 30.000đ; chưa có biên lai. |
| L02 | Hai kỳ 9.000đ + 21.000đ | Hợp lệ; kỳ đầu đạt tối thiểu 30%. |
| L03 | Ba kỳ 9.000đ + 10.500đ + 10.500đ | Hợp lệ; tổng đúng 30.000đ. |
| L04 | Ba kỳ 8.999đ + 10.501đ + 10.500đ | Từ chối: kỳ đầu phải từ 9.000đ; không lưu lịch một phần. |
| L05 | Bỏ trống tiền kỳ 2 | Báo rõ kỳ 2 phải nhập số tiền. |
| L06 | Kỳ 2 = 0đ, -1đ, 999đ, 1.000,5đ (test riêng từng giá trị) | Từ chối tương ứng: phải dương/tối thiểu 1.000đ/phải là số nguyên. |
| L07 | 9.000đ + 10.500đ + 9.500đ | Báo tổng thiếu 1.000đ so với 30.000đ. |
| L08 | 9.000đ + 10.500đ + 11.500đ | Báo tổng vượt 1.000đ. |
| L09 | 30.000đ + 0đ + 0đ | Không tự đánh dấu đã thanh toán; yêu cầu sửa kỳ hoặc chọn đóng một lần. |
| L10 | Chọn số kỳ ngoài 1–3 qua request sửa tay | Backend từ chối; giao diện chỉ cho 1–3 kỳ. |

## 2. Hạn thanh toán

Gọi D là ngày hiện tại, E là ngày kết thúc lớp. Ba kỳ cần ít nhất ba ngày hợp lệ khác nhau.

| Mã | Nhập/thao tác | Kết quả cần thấy |
| --- | --- | --- |
| N01 | Ba kỳ: D, D+1, D+2; cả ba không sau E | Hợp lệ. |
| N02 | Bỏ trống một hạn | Lỗi tại đúng kỳ bị thiếu. |
| N03 | Chọn ngày trước D hoặc sau E | Từ chối, thông báo rõ giới hạn ngày. |
| N04 | Kỳ 2 bằng/trước kỳ 1; kỳ 3 bằng/trước kỳ 2 | Từ chối đúng kỳ; không lưu lịch. |
| N05 | Ba kỳ: đưa kỳ 1 đến E-2 | Kỳ 2 chỉ còn E-1 và kỳ 3 chỉ còn E; dòng hướng dẫn cập nhật theo ngày đã chọn. |
| N06 | Cố nhập kỳ 1 = E hoặc E-1 khi chọn ba kỳ | Không cho chốt vì không đủ ngày cho các kỳ sau; không hiển thị khoảng ngày đảo ngược rồi cho lưu. |
| N07 | Chọn ba kỳ khi lớp còn dưới ba ngày, hoặc lớp đã kết thúc | Báo rõ không đủ khoảng ngày/không thể chốt; chọn ít kỳ hơn hoặc lớp hợp lệ. |

Dòng hướng dẫn phải có dạng **“Hạn đóng chỉ được chọn từ dd/MM/yyyy đến dd/MM/yyyy”**, phù hợp từng kỳ và cập nhật sau khi đổi ngày/số kỳ. Không dùng ngày cố định trong tài liệu làm điều kiện lâu dài.

## 3. Ưu đãi

Mỗi mức ưu đãi dùng hồ sơ mới/chưa chốt. Với Placement Test, hoàn tất đánh giá thật trong luồng demo, đúng học viên, hồ sơ và khóa đề xuất PIA-CB-12; không chỉ sửa điểm của một bài kiểm tra không liên quan.

| Điểm test thang 10 | Giảm | Tiền giảm | Còn đóng | Kỳ đầu tối thiểu nếu chia kỳ |
| --- | --- | --- | --- | --- |
| Dưới 7 | 0% | 0đ | 30.000đ | 9.000đ |
| Từ 7 đến dưới 8 | 10% | 3.000đ | 27.000đ | 8.100đ |
| Từ 8 đến dưới 9 | 20% | 6.000đ | 24.000đ | 7.200đ |
| Từ 9 đến 10 | 30% | 9.000đ | 21.000đ | 6.300đ |

- Kiểm tra mốc 7, 8, 9, 10: đúng nhóm, không chồng lấn. Điểm lưu tại lịch test theo thang 100 nên các mốc tương ứng là 70, 80, 90, 100.
- Có Placement Test: hiển thị nguồn điểm/mức giảm và khóa chỉnh sửa. Sửa request để đòi mức khác phải bị từ chối; server tự kiểm tra nguồn điểm.
- Không qua test: phần trăm nguyên 1–30; thử 0, âm, 31, 10,5 phải bị từ chối trong lựa chọn có giảm. Muốn 0 phải chọn Không giảm.
- Giảm tiền cố định: 1.000đ và 9.000đ hợp lệ; 999đ, 9.001đ, số âm, số thập phân không hợp lệ. Có giảm phải nhập lý do.
- Chốt xong kiểm tra snapshot ưu đãi; sửa điểm về sau không được tự tính lại tiền của hóa đơn đã chốt/đã thu.

## 4. Test chuyển khoản VietQR Pro hiện tại

Mỗi ca dùng hóa đơn/kỳ chưa thu riêng, trừ ca gửi lặp. Với lịch không giảm 9.000đ + 10.500đ + 10.500đ:

| Mã | Thao tác | Kết quả |
| --- | --- | --- |
| CK01 | Thanh toán đúng tài khoản ảo và 9.000đ | Kỳ 1 đủ, tổng còn 21.000đ, tạo một biên lai và thông báo. |
| CK02 | Đúng tài khoản ảo/số tiền nhưng nội dung khác | Nếu gateway xác nhận đúng đơn thì vẫn ghi nhận một lần; không có hàng đợi Admin. |
| CK03 | Thử nhập số tiền khác số yêu cầu trong ngân hàng | VietQR Pro có thể từ chối hoặc hoàn lại; không coi là ca chuyển thiếu/thừa được ứng dụng nhận để phân bổ. Không cố thực hiện giao dịch sai để test. |
| CK04 | Bấm Hủy trên PayOS và quay về | Hệ thống tự kết thúc yêu cầu chưa thu, không mở popup kiểm tra. Bấm thanh toán lần sau tạo mã mới. |
| CK05 | Back về hệ thống khi chưa thu | Tự kiểm tra/hủy lần trước; không có nút Kiểm tra thanh toán PayOS. |
| CK06 | Tải lại sau thanh toán thành công | Số dư, biên lai không tăng lặp. Chỉ khi cả ba kỳ đủ thì hóa đơn toàn khóa mới đóng đủ. |
| CK07 | Đăng nhập Admin/Staff/Parent | Không có tab/API nghiệp vụ nhập sao kê, phân bổ hoặc hoàn tiền chuyển khoản. Chức năng điều chỉnh tiền mặt vẫn giữ. |

Chỉ dùng môi trường kiểm thử để gửi callback sai số tiền, sai chữ ký, sai mã đơn hoặc gửi lặp. Backend phải giữ dấu vết/chặn khoản bất thường, không ghi thu sai và không tạo chức năng duyệt sai lệch. Không có ca demo chuyển thiếu/thừa thật như tài liệu cũ.

## 6. Tiền mặt và điều chỉnh thu nhầm

Dùng một hóa đơn riêng; đừng dùng lại kỳ đã thu trong nhóm chuyển khoản.

| Mã | Thao tác | Kết quả cần thấy |
| --- | --- | --- |
| TM01 | Thu kỳ còn 9.000đ | Số tiền thanh toán cố định 9.000đ; sửa request để thu khác số kỳ còn thiếu bị từ chối. |
| TM02 | Khách đưa đúng 9.000đ | Thu 9.000đ, trả lại 0đ. |
| TM03 | Khách đưa 8.999đ hoặc số thập phân | Không xác nhận được; lỗi thiếu tiền/số nguyên. |
| TM04 | Khách đưa 10.000đ | Thu học phí 9.000đ, trả lại 1.000đ; không cộng tiền khách đưa thành học phí. |
| TM05 | Khách đưa dư 9.050đ | Từ chối: khoản đưa dư không ghép được từ các mệnh giá từ 200đ. |
| TM06 | Khách đưa 9.100đ | Tiền khách đưa ghép được; tiền thừa thô 100đ làm tròn lên 200đ; hiển thị trung tâm hỗ trợ 100đ. |
| TM07 | Khách đưa 9.300đ | Trả lại 400đ, trung tâm hỗ trợ 100đ; biên lai học phí vẫn 9.000đ. |
| TM08 | d26_staff thu tiền mặt rồi mở biên lai | Chỉ Staff này hoặc Admin được đề nghị điều chỉnh giao dịch đó. |
| TM09 | d26_staff_2 mở biên lai TM08 | Xem được nhưng không được đề nghị điều chỉnh; sửa request cũng bị chặn. |
| TM10 | d26_staff_2 tự thu một giao dịch tiền mặt khác | Chính Staff2 được đề nghị khoản mới; Staff1 không được. |
| TM11 | Admin duyệt yêu cầu hợp lệ do Staff gửi | Lưu lịch sử điều chỉnh, công nợ cập nhật đúng; biên lai gốc đánh dấu đã đảo, không xóa lịch sử. |
| TM12 | Người gửi tự duyệt / đề nghị lặp / điều chỉnh biên lai PayOS | Bị chặn theo quyền/trạng thái/phương thức, không đảo tiền hai lần. |

Các ca 100đ/300đ tiền thừa ở trên kiểm tra **chính sách làm tròn hiện tại**, không yêu cầu trả lại những mệnh giá này. Trả đúng tiền là ngoại lệ được chấp nhận; tiền khách đưa vẫn phải nguyên đồng. Không áp dụng làm tròn tiền thừa của thu ngân cho hoàn tiền chuyển khoản.

## Biên bản kết quả

### Gia hạn trực tiếp từng kỳ (23/11/2026)

- Staff và Admin đều có nút **Gia hạn kỳ N** cho kỳ chưa có khoản thu; Parent không có quyền. Kỳ đã thu không hiện nút và backend cũng từ chối.
- Khoảng ngày mới: từ ngày lớn nhất của hôm nay, hạn hiện tại + 1 ngày, hạn các kỳ trước + 1 ngày; đến ngày nhỏ nhất của hạn kỳ sau - 1 ngày và ngày kết thúc lớp. Không còn khoảng hợp lệ thì hiển thị lý do, không cho gia hạn.
- Test ngày quá khứ, bằng hạn hiện tại, bằng/sau kỳ sau hoặc quá ngày kết thúc lớp: frontend và backend đều từ chối, hiển thị **Hạn đóng mới chỉ được chọn từ ... đến ...**.
- Chọn ngày hợp lệ và nhập lý do → **hạn đổi ngay khi Staff/Admin xác nhận**, không gửi đề nghị và không chờ người khác duyệt. Tiền phải đóng, tiền đã thu và biên lai giữ nguyên.
- Backend xác minh/đóng yêu cầu PayOS chưa thu ngoài transaction DB; sau đó khóa hóa đơn và kiểm tra lại công nợ, phiên bản lịch và ngày cũ. Nếu có tiền vào, lịch thay đổi hoặc chưa hủy được QR thì không lưu hạn mới.
- Giao diện không hiển thị lịch sử gia hạn; backend vẫn lưu ngày cũ, ngày mới, người thực hiện và lý do. Đề nghị PENDING cũ không tự áp dụng và không cản gia hạn trực tiếp.
- API mới: POST `/api/tuition-payment/{invoiceId}/due-date`. API tạo đề nghị cũ chuyển thành adapter áp dụng trực tiếp; API duyệt cũ trả lỗi GONE và không đổi dữ liệu. Không thay đổi luồng duyệt điều chỉnh thu nhầm tiền mặt.

Với mỗi mã ca, ghi: ngày test, tài khoản, học viên, mã lớp/hóa đơn/kỳ, số dư trước, mã giao dịch/biên lai, số dư sau, kết quả đạt/không đạt, ảnh màn hình/lỗi cụ thể. Khi kiểm tra chống trùng, so sánh cả số lượng biên lai và tổng tiền. Không reset database giữa những bước liên tiếp của một ca.
