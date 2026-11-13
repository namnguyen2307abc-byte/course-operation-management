# Demo hội đồng: từ đăng ký đến học phí, đi học và học bù

Bộ dữ liệu **HD261111**, tạo theo ngày **11/11/2026** trên database local `course_operation_management`. Chỉ dữ liệu mang nhãn này là bộ trình diễn mới. Các khoản thu có sẵn ghi rõ DEMO FIXTURE, không phải tiền thật. Không có nghiệp vụ hoàn học phí trong luồng hiện tại.

## 1. Chuẩn bị và tài khoản

Địa chỉ bản main đã build và chạy để kiểm tra: **http://127.0.0.1/login.html** (cổng 80). Frontend/backend cùng địa chỉ này. Backend IntelliJ ở cổng 8080 được giữ nguyên; cần restart/rebuild IntelliJ sau khi đổi branch nếu muốn demo qua Live Server 5500 → backend 8080.

Mật khẩu chung cho **8 tài khoản mẫu mới**: **`Test@123456`**.

| Tài khoản | Vai trò | Dùng khi demo |
|---|---|---|
| `hd261111_parent` | Phụ huynh Nguyễn Lan | Đăng ký con, theo dõi học phí, QR/biên lai, lịch và đơn nghỉ |
| `hd261111_staff` | Nhân viên giáo vụ / thu ngân | Tạo lịch test, xếp lớp, chốt học phí, thu tiền và xử lý đơn |
| `hd261111_teacher` | Cô Thu Hà, Piano | Chấm đầu vào, điểm danh lớp B/W, duyệt đơn của lớp |
| `hd261111_teacher2` | Thầy Minh, Piano | Điểm danh lớp C có học viên học bù |
| `hd261111_admin` | Admin | Quản lý tài khoản, cơ sở, hủy hồ sơ chưa đóng và duyệt điều chỉnh |
| `hd261111_leader` | Quản lý trung tâm | Lập/duyệt điều chỉnh khoản thu với người khác |
| `hd261111_other` | Phụ huynh Trần Mai | Chỉ có Thảo Nhi; kiểm tra không thấy con/học phí của Nguyễn Lan |
| `hd261111_student` | Học viên Minh Châu | Xem lịch và chuyên cần của chính Minh Châu |

Nên mở các cửa sổ/profile trình duyệt riêng cho phụ huynh, nhân viên và giáo viên. Các tab chung profile dùng chung localStorage, đổi tài khoản ở tab này sẽ ảnh hưởng tab kia.

## 2. Dữ liệu để tìm nhanh

Tìm bằng **HD261111** tại những màn hình có tìm kiếm. Tài khoản phụ huynh Nguyễn Lan gom 14 hồ sơ để thử nhiều trường hợp, không mô phỏng một gia đình thật; phụ huynh Trần Mai có 1 học viên. Có 7 lớp, 3 khóa Piano, 70 buổi học, 3 phòng, 12 đàn và 11 hóa đơn mẫu.

| Nhãn học viên | Trạng thái ban đầu / mục đích |
|---|---|
| **01 – Minh An** | Chưa đăng ký: dùng luồng chính từ đầu |
| **02 – Bảo Ngọc** | WAITING_PLACEMENT, đã có lịch test: dự phòng để chấm điểm ngay |
| **03 – Gia Hân** | READY_FOR_ASSIGNMENT cho khóa 4.000đ: demo QR riêng |
| **04 – Minh Châu** | Đã đóng đủ, lớp B: điểm danh hôm nay và gửi đơn nghỉ mới |
| **05 – Bảo Lâm** | Vắng 3/10 buổi = **30%, nghiêm trọng**; một ca bù chờ xếp |
| **06 – Tuấn Kiệt** | Đã đóng 3 triệu, còn 3 triệu; đợt 2 quá hạn 8 ngày |
| **07 – Mai Chi** | Có đơn PENDING cho 18/11: phụ huynh có thể hủy |
| **08 – Gia Huy** | Đã xếp học bù lớp C, buổi 3 ngày 11/11: demo hoàn thành bù |
| **09 – Khánh Linh** | Có đơn PENDING cho 18/11: nhân viên duyệt thay giáo viên |
| **10 – Đức Anh** | Học viên chính thức lớp C; phân biệt với Gia Huy học bù |
| **11 – Hoàng Nam** | Lớp D tối đa 1 người, đã đủ chỗ |
| **12 – Ngọc Anh** | Chưa đóng lần đầu, quá hạn: admin liên hệ rồi hủy hồ sơ |
| **13 – Phương Anh** | Có biên lai tiền mặt nhập nhầm: demo điều chỉnh khoản thu |
| **14 – Thảo Nhi** | Thuộc `hd261111_other`, không thuộc phụ huynh Nguyễn Lan |
| **15 – Hà My** | Vắng 3/20 buổi = **15%, cảnh báo mức giữa**, lớp W |

| Mã lớp | Nội dung |
|---|---|
| **HD261111-A** | OPEN, khóa chính 6.000.000đ / 10 buổi, khai giảng 12/11; còn chỗ để xếp Minh An |
| **HD261111-B** | IN_PROGRESS, 6 học viên chính thức, buổi 3 hôm nay **07:00–09:00**; Cô Thu Hà |
| **HD261111-C** | IN_PROGRESS, buổi 3 hôm nay **09:30–11:30**; Thầy Minh; nhận học bù |
| **HD261111-D** | OPEN, 1/1 học viên, không còn chỗ; dùng test chặn lớp đầy |
| **HD261111-F** | PLANNING, chưa phân công giáo viên/phòng/lịch: dùng demo quản lý lớp |
| **HD261111-Q** | OPEN, khóa thử QR **4.000đ**, còn chỗ; dùng xếp Gia Hân |
| **HD261111-W** | Khóa 20 buổi, **13:00–15:00**, Cô Thu Hà; dùng cảnh báo 15% |

Lớp B/C: buổi 1 ngày 28/10, buổi 2 ngày 04/11, buổi 3 ngày 11/11, buổi 4 ngày **18/11**, buổi 5 ngày **25/11**. Lớp C học muộn hơn lớp B nên buổi 4 lớp C là ứng viên phù hợp cho học bù buổi 4 lớp B.

## 3. Luồng chính: làm trực tiếp từ đầu (10–15 phút)

### A. Phụ huynh gửi yêu cầu

1. Đăng nhập `hd261111_parent` → **Đăng ký & xếp lớp**.
2. Chọn **[HD261111-01] Minh An**, chọn nhu cầu kiểm tra đầu vào/Placement Test, ghi chú “Demo hội đồng: tư vấn Piano phù hợp năng lực”. Gửi yêu cầu.
3. Mong đợi: **Chờ đánh giá đầu vào / WAITING_PLACEMENT**, chưa có hóa đơn, chưa có ghi danh chính thức.
4. Gửi thêm một lần cho cùng học viên để giải thích hệ thống chặn yêu cầu đang xử lý trùng. Không cần tạo yêu cầu khác.

### B. Giáo vụ xếp lịch, giáo viên chấm

1. `hd261111_staff` → Đăng ký & xếp lớp → tìm Minh An → tạo lịch test gắn đúng yêu cầu. Chọn bộ môn Đàn/Piano, phòng Piano A, cơ sở HD261111 và thời gian demo thích hợp.
2. `hd261111_teacher` → **Đánh giá đầu vào** → mở lịch của Minh An → chấm **80/100**, trình độ **BEGINNER/Cơ bản**.
3. Khóa gợi ý: **[HD261111] Piano cơ bản - 10 buổi**, mã **HD261111-P1**. Ghi nhận xét và lưu.
4. Mong đợi: lịch test **COMPLETED**, yêu cầu đăng ký chuyển **READY_FOR_ASSIGNMENT**, có thông báo để giáo vụ xếp lớp.
5. Nếu muốn rút gọn: dùng **02 – Bảo Ngọc** vì đã có lịch test; vẫn chọn khóa P1 khi chấm.

### C. Xếp lớp sinh hóa đơn

1. `hd261111_staff` → tìm yêu cầu Minh An → chọn lớp **HD261111-A** và xác nhận xếp lớp.
2. Mong đợi: sinh **một hóa đơn 6.000.000đ**, yêu cầu/ghi danh ở trạng thái **PENDING_PAYMENT**.
3. Học viên chưa được tính là đã nhập học chính thức trước khi đủ điều kiện đóng kỳ đầu. Phiếu học phí mới chưa chốt lịch đóng.
4. Qua **Quản lý học phí**, tìm Minh An. Có thể mở lại trang đăng ký để cho thầy thấy liên kết giữa yêu cầu → ghi danh → hóa đơn.

### D. Chốt ưu đãi và thu đợt đầu

1. Chọn **Chốt lịch đóng**. Loại ưu đãi **Phần trăm**, giá trị **10**, lý do “Ưu đãi tuyển sinh demo”.
2. Kiểm tra sau ưu đãi: **5.400.000đ**.
3. Chia 3 đợt:

| Đợt | Số tiền | Hạn đóng gợi ý cho bộ này |
|---|---:|---|
| 1 | 2.700.000đ | 11/11/2026 |
| 2 | 1.350.000đ | 11/12/2026 |
| 3 | 1.350.000đ | 11/01/2027 |

4. Thử thay tổng các đợt thành số khác 5,4 triệu hoặc đợt đầu nhỏ hơn 50% để trình bày ràng buộc; sau đó sửa đúng và chốt.
5. Nhấn **Thanh toán đợt 1** → **Tiền mặt**, số thu **2.700.000đ**, khách đưa **3.000.000đ** → xác nhận.
6. Mong đợi: tiền thừa **300.000đ**; đã thu **2.700.000đ**, còn thiếu **2.700.000đ**; hóa đơn **PARTIALLY_PAID**; ghi danh **ENROLLED**. Nút thanh toán chuyển sang **đợt 2**.
7. Mở biên lai: mã PT-ngày-số thứ tự dễ đọc. Đóng/mở lại trang để cho thấy số liệu được lưu, không chỉ thay đổi trên giao diện.

### E. Phụ huynh xem kết quả

1. `hd261111_parent` → **Học phí & thông báo** → chọn Minh An.
2. Đối chiếu 3 kỳ, khoản vừa đóng, số dư và biên lai. Mở **Lịch học** để thấy lớp A và các buổi sắp tới.
3. Lớp A khai giảng ngày 12/11: không cho giáo viên điểm danh trước ngày. Chuyển sang hồ sơ đã học sẵn ở phần dưới để demo chặng tiếp theo trong cùng buổi bảo vệ.

## 4. Đi học → xin nghỉ → duyệt → học bù (8–10 phút)

### F. Điểm danh buổi hôm nay

1. `hd261111_teacher` → **Thời khóa biểu** → tuần có ngày 11/11 → lớp **B**, buổi 3.
2. Bảng điểm danh gồm học viên chính và loại Gia Huy khỏi buổi gốc vì đã chuyển sang lớp C học bù. Chọn đủ trạng thái cho mọi học viên trong bảng, để **Minh Châu có mặt**; lưu.
3. `hd261111_parent` → **Điểm danh** → Minh Châu → lớp B: xem lịch sử vừa lưu và thông báo điểm danh.
4. Mở một buổi quá khứ/tương lai bằng tài khoản giáo viên và thử sửa để trình bày **time-lock**. Giáo viên chỉ sửa đúng ngày, sau giờ bắt đầu; admin/giáo vụ xử lý ngoại lệ. Buổi bị hủy không được điểm danh.

### G. Phụ huynh xin nghỉ một buổi sắp tới

1. `hd261111_parent` → **Viết đơn** → chọn **Minh Châu**, lớp B, **buổi 4 ngày 18/11**, lý do “Gia đình có lịch khám sức khỏe”. Gửi.
2. Mong đợi: PENDING; gửi trùng cùng học viên/buổi bị chặn. Đơn chờ được ưu tiên lên đầu danh sách.
3. `hd261111_teacher` hoặc `hd261111_staff`: mở **chuông thông báo** → bấm thông báo mới → vào đúng chi tiết đơn. Duyệt.
4. Mong đợi: đơn APPROVED và tự sinh hồ sơ học bù **PENDING**. Phụ huynh không tự duyệt; giáo viên khác không có quyền duyệt đơn ngoài lớp mình.

### H. Giáo vụ xếp lớp bù

1. `hd261111_staff` → **Xem đơn → Lịch học bù** → Minh Châu → Xếp lớp.
2. Chọn **HD261111-C, buổi 4 ngày 18/11, 09:30–11:30** trong danh sách phù hợp.
3. Mong đợi: SCHEDULED, có lớp/buổi mục tiêu, thông báo cho phụ huynh và giáo viên. Lịch học của con phản ánh buổi bù.
4. Nêu các ràng buộc đã có: đúng khóa/nội dung buổi phù hợp, buổi chưa diễn ra, còn chỗ, không trùng lịch học viên. Không lựa chọn lớp tùy ý ngoài danh sách đề xuất.

### I. Hoàn thành buổi học bù ngay hôm nay bằng hồ sơ chuẩn bị sẵn

1. Dùng hồ sơ **08 – Gia Huy**, đã được xếp buổi 3 lớp C ngày 11/11.
2. `hd261111_teacher2` → Thời khóa biểu → lớp C, buổi 3 → bảng gồm **Đức Anh** (chính thức) và **Gia Huy** (Học bù).
3. Điểm danh đủ bảng, chọn Gia Huy **PRESENT**, lưu.
4. `hd261111_staff`/phụ huynh mở lại ca bù Gia Huy: **COMPLETED**. Không cần bấm “hoàn thành” giả trước khi có điểm danh.
5. Phân biệt rõ: phần G/H là một đơn mới sẽ diễn ra 18/11; phần I dùng hồ sơ của ngày hôm nay để minh họa bước cuối, không giả lập đã tới ngày 18/11.

## 5. Các nhánh nghiệp vụ bổ sung để thầy hỏi sâu

| Kịch bản | Tài khoản và thao tác | Kết quả cần trình bày |
|---|---|---|
| **Hủy đơn PENDING** | Parent → Mai Chi 07 → đơn 18/11 → Hủy và xác nhận | Đơn chờ bị xóa theo thiết kế hiện tại, có thông báo hủy; không còn để giáo viên duyệt. Đơn APPROVED không có thao tác này |
| **Nhân viên duyệt thay** | Staff → Khánh Linh 09 → đơn PENDING → Duyệt | Tự tạo ca bù chờ xếp, thông báo cho phụ huynh/giáo viên; không cần mượn tài khoản giáo viên |
| **Ba mức chuyên cần** | Parent → Điểm danh: Minh Châu 04, Hà My 15, Bảo Lâm 05 | Bình thường **0%**; WARNING **15% (3/20)**; CRITICAL **30% (3/10)**. Cảnh báo không tự hủy ghi danh |
| **Thu đợt sau quá hạn** | Staff → Quản lý học phí → **HD261111-INV-6**, Tuấn Kiệt | Đã thu 3 triệu/còn 3 triệu; thu đợt 2 **1,5 triệu** → còn **1,5 triệu**, vẫn ở lớp; parent xem biên lai thứ hai |
| **Hủy hồ sơ chưa đóng** | Admin → **HD261111-INV-12**, Ngọc Anh → hủy hồ sơ sau liên hệ | Phải nhập lý do/xác nhận đã liên hệ và kiểm tra chưa nhận tiền. Hóa đơn/ghi danh/yêu cầu bị hủy; không dùng cho hồ sơ đã có khoản thu |
| **Điều chỉnh thu nhầm** | Leader → **HD261111-INV-13**, Phương Anh → mở biên lai tiền mặt, lập đề nghị. Admin → tab **Điều chỉnh khoản thu** → tìm và duyệt | Người lập không tự duyệt; biên lai cũ còn lịch sử “đã điều chỉnh”, công nợ tăng lại. Ghi danh không bị âm thầm xóa. Đây là sửa sổ thu, không phải hoàn tiền cho phụ huynh |
| **Riêng tư dữ liệu** | Đăng nhập `hd261111_other` và `hd261111_student` | Other chỉ thấy Thảo Nhi, không thấy các con của Lan; student chỉ thấy hồ sơ Minh Châu |
| **Lớp đầy / lớp dự kiến** | Parent/Staff xem lớp D và F | D không được xếp thêm vì 1/1; F chưa được hiển thị như lớp đang mở |
| **Cơ sở vật chất** | Admin → cơ sở HD261111 → P01/P02/P03, thiết bị | P01/P02 có 6 đàn tốt/phòng; P03 bảo trì không nhận phân công mới. Thử tăng sĩ số quá 6/thiếu đàn để thấy chặn nghiệp vụ |
| **Quản lý lớp** | Staff → sửa lớp F, chọn teacher, P01, ngày 13/11, chỉ Thứ Sáu 07:00–09:00, 10 buổi; chuyển OPEN | Sinh lịch, kiểm tra phù hợp môn/phòng/thiết bị và xung đột. Không chọn thời gian trùng lớp đang dùng cùng giáo viên/phòng |
| **Quản lý tài khoản** | Admin → tìm `hd261111_other`, khóa; thử đăng nhập, sau đó mở lại | Tài khoản khóa không đăng nhập; có nhật ký quản trị. Không khóa tài khoản admin đang sử dụng |

Không demo điều chỉnh khoản thu trước khi đã trình bày số dư ban đầu của Phương Anh. Không dùng Bảo Lâm để điểm danh lại toàn bộ thành PRESENT nếu còn cần trình bày cảnh báo 30%.

## 6. QR / PayOS: một nhánh demo riêng

1. Staff xếp **03 – Gia Hân** vào **HD261111-Q**. Mở học phí, không giảm giá; chốt **2.000đ hôm nay + 2.000đ ngày 15/11**.
2. Parent hoặc staff mở Thanh toán đợt 1 → chuyển khoản/QR. Cho thấy đúng số tiền, tài khoản nhận và trạng thái đang chờ.
3. Nếu muốn chuyển thật để kiểm tra webhook: chỉ dùng mã nhỏ 2.000đ này, người demo chủ động quyết định. Seed không tạo tiền thật và không tự xác nhận đã nhận tiền.
4. Tunnel phải trỏ về **đúng backend đang phục vụ QR**. Bản demo hiện chạy cổng 80: chạy `ngrok http 80`, rồi đăng ký URL ngrok bằng script `scripts/register-payos-webhook.py` như trước. Nếu dùng IntelliJ đã rebuild tại 8080 thì tunnel dùng `ngrok http 8080`.
5. Webhook nhận thành công mới tự cập nhật giao dịch, ghi danh, biên lai và nút đợt 2. Không chuyển lại lần nữa chỉ vì màn hình cập nhật chậm.
6. Trường hợp webhook lỗi: chỉ dùng nút xác nhận thủ công khi đã thật sự kiểm tra tiền ghi có, nhập mã giao dịch và lý do; không dùng thao tác này để giả dữ liệu “đã nhận”. Nếu không chuyển thật, dừng ở màn hình QR rồi đóng QR chưa thanh toán. Luồng tiền mặt ở phần D vẫn demo đầy đủ các bước sau thu.

## 7. Chạy lại, kiểm tra và ngày demo

Từ thư mục gốc project:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\seed-defense-demo.ps1 -Tag HD261111
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-defense-demo.ps1 -Tag HD261111 -BaseUrl http://127.0.0.1
```

Seed chạy lại cùng Tag **không reset tiến độ** và không thu tiền thêm. Script verify chủ yếu dành cho trạng thái ban đầu; sau khi bạn demo thu tiền/hủy đơn, các kiểm tra số lượng ban đầu có thể không còn đúng.

**Nếu bảo vệ vào ngày khác:** tạo Tag mới, ví dụ `HD261112`. Tài khoản trở thành `hd261112_parent`…; ngày học hôm nay/ngày mai và công nợ được tính lại theo ngày seed. Tên học viên, lớp, số tiền và thứ tự kịch bản giữ cùng ý nghĩa; chọn ngày thực tế hiện trên màn hình thay cho các mốc 11/11–18/11 trong tài liệu này.

Không dùng `init_database.sql` để chuẩn bị demo lại vì đó là script khởi tạo lại database. Không xóa các batch đã có người thực hành; tạo Tag mới để có bộ sạch.

## 8. Lời dẫn ngắn cho hội đồng

“Phụ huynh gửi nhu cầu học; giáo vụ và giáo viên đánh giá rồi xếp đúng lớp. Xếp lớp mới tạo nghĩa vụ học phí, chưa đồng nghĩa đã được nhập học. Sau khi chốt lịch và thu đủ kỳ đầu, hệ thống mới xác nhận ghi danh. Những kỳ còn lại trở thành công nợ có hạn đóng và biên lai riêng. Khi học viên đi học, lịch, điểm danh, đơn nghỉ và học bù dùng lại đúng hồ sơ ghi danh đó, có phân quyền và các ràng buộc về thời gian, sĩ số, lớp phù hợp và lịch trùng.”
