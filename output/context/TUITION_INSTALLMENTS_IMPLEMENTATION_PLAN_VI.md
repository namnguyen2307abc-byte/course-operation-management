> Cập nhật 11/11/2026: tính năng đã hoàn thành và được đồng bộ theo quy trình trong `AGENTS.md`. Nghiệp vụ hiện hành nằm ở `docs/NGHIEP_VU_HOC_PHI_DON_GIAN_VI.md`; test từ chốt lớp ở `docs/TEST_TU_CHOT_LOP_DEN_THANH_TOAN_VI.md`. Không triển khai lại gia hạn giữ chỗ 48 giờ, màn hình đối chiếu ngân hàng hay khu Quá hạn cần liên hệ riêng. Điều chỉnh khoản thu nhầm hiện là tab riêng có tìm kiếm và phân trang 5 mục. Các kế hoạch bên dưới là nền lịch sử.

# Kế hoạch triển khai thu học phí nhiều đợt qua phụ huynh và tại quầy

Ghi chú ngày 08/11/2026: đã triển khai theo lệnh người dùng trên feature/tuition-installments. Đọc docs/tuition-installments.md để xem cách chạy và test bản hiện tại. Các bước tạo nhánh trong tài liệu này là kế hoạch trước triển khai, cần kiểm tra git status trước khi tiếp tục.

Cập nhật phạm vi ngày 07/11/2026. Chỉ triển khai thu nhiều đợt, thanh toán online/tại quầy và quản lý công nợ theo yêu cầu mới nhất. Phần dừng học/hoàn phí chưa được thầy duyệt và đã được loại khỏi phạm vi.
Ngôn ngữ giao diện và trao đổi: tiếng Việt.
Nhánh bắt buộc khi bắt đầu code: `feature/tuition-installments`.

## 1. Hướng dẫn dành cho ChatGPT tiếp nhận

Đọc hai file theo thứ tự:
1. `PROJECT_CONTEXT_AND_INSTALLMENT_PLAN_VI.md` để hiểu dự án và công việc đã làm.
2. File này để triển khai phạm vi mới nhất.

Đọc lại source và trạng thái Git. Phần trong file context cũ mô tả chỉ thanh toán tại quầy đã được mở rộng theo file này.

Người dùng yêu cầu lập kế hoạch bàn giao trong chat hiện tại. Khi người dùng ở chat mới yêu cầu thực hiện, làm các bước chuẩn bị nhánh bên dưới trước khi sửa code. Không hiểu việc đọc file là yêu cầu tự động merge/push main.

### Chuẩn bị Git bắt buộc

Workspace:
`D:/Ky_7/SBA301/Apartment_management/course-operation-management`

Trạng thái sau khi gỡ hoàn phí ngày 07/11:
- Workspace chính ở main, commit 7d47cba, code tracked khớp main.
- Nhánh feature/enrollment-withdrawal-refund đã xóa local.
- Source, frontend, test, migration/seed và sơ đồ hoàn phí đã được gỡ.
- Worktree .local-run/nam-class-integration hiện ở detached HEAD, main đã được trả về workspace chính.
- Database local vẫn có bảng và dữ liệu demo của phần hoàn phí. Không xóa nếu chưa được yêu cầu phù hợp.
- Tài liệu và output/context được giữ để bàn giao.

Thực hiện khi người dùng yêu cầu bắt đầu:
1. Chạy git status, git worktree list, git branch -avv.
2. Fetch main mới, kiểm tra và cập nhật main khi working tree source sạch.
3. Tạo và chuyển sang nhánh:
   `git switch -c feature/tuition-installments main`
   Nếu dùng máy khác hoặc worktree riêng, lấy từ main/origin/main mới nhất.
4. Nếu nhánh đã tồn tại, kiểm tra lịch sử thay vì ghi đè.
5. Xác nhận tên nhánh trước khi code.
6. Giữ tài liệu và file xuất người dùng. Không dùng blanket git clean/reset.
7. Không lấy lại code hoàn phí đã bỏ làm dependency.

## 2. Mục tiêu và phạm vi

Một hóa đơn có nhiều kỳ và nhiều giao dịch, phụ huynh có hai cách đóng:
- Online trong tài khoản phụ huynh, bằng QR/PayOS.
- Tại trung tâm, nhân viên thu tiền mặt hoặc mở QR tại quầy.

Cả hai kênh dùng chung nghĩa vụ học phí, lịch kỳ, số tiền đã nhận và công nợ.
Không tạo hóa đơn tổng khác nhau cho mỗi kênh.
Mỗi lần thu thành công có biên lai riêng, phụ huynh và nhân viên cùng tra cứu được.

Luồng chính:
Kiểm tra năng lực -> tư vấn và chốt lớp -> chốt học phí và lịch kỳ -> thu đủ kỳ đầu -> ghi danh -> thông báo kỳ sau -> phụ huynh trả online hoặc tại quầy -> cập nhật số dư -> tất toán.

Phạm vi triển khai chỉ gồm thu nhiều đợt, trang học phí phụ huynh, thanh toán tại quầy/QR, công nợ, thông báo và gia hạn. Việc xử lý khoản tiền ngân hàng nhận ngoài dự kiến vẫn cần đối chiếu để không mất dấu tiền; không mở rộng thành toàn bộ module hủy học/hoàn học phí.

## 3. Chính sách đã rõ và các mặc định đề xuất

Đã thống nhất hướng nghiệp vụ:
- Có thanh toán online trong tài khoản phụ huynh và tại quầy.
- Thông báo trong tài khoản phụ huynh khi đến kỳ.
- Thu ngân có thể tìm và thu khoản còn thiếu khi gia đình đến trực tiếp.
- Ghi nhận tiền của hai kênh nhất quán.
- Kiến trúc backend/frontend theo dự án.

Các giá trị sau là đề xuất triển khai, chưa phải chính sách trung tâm được xác nhận riêng:
- Đóng một lần hoặc tối đa ba kỳ.
- Kỳ đầu tối thiểu 50% học phí sau ưu đãi.
- Ngày kỳ sau tăng dần, phù hợp lịch khóa học.
- Nhắc trước hạn ba ngày, đúng ngày hạn, và khi quá hạn.
- Sau bảy ngày quá hạn, đưa vào danh sách quản lý cần xử lý.
- Chưa áp dụng lãi/phạt.
- Chưa tự động đình chỉ học hoặc xóa học viên khỏi lớp vì nợ.
- Bản đầu yêu cầu giao dịch đầu thu đủ ngưỡng kỳ đầu. Hỗ trợ chia nhỏ ở các kỳ sau.
- Nếu muốn thu từng phần nhỏ cả kỳ đầu, phải bổ sung hạn giữ chỗ và xử lý tiền đã nhận khi hết hạn.

Chat mới cần nêu rõ các mặc định này lúc chốt triển khai, không khẳng định người dùng đã duyệt từng tỷ lệ/ngày.
Nếu người dùng đồng ý triển khai theo toàn bộ kế hoạch, áp dụng các mặc định đã được nêu. Nếu họ đổi chính sách, cập nhật thiết kế và test tương ứng.

## 4. Luồng theo vai trò

### 4.1. Chốt lịch và đóng kỳ đầu

1. Nhân viên xử lý yêu cầu xếp lớp hiện có.
2. Hệ thống tạo hoặc mở Invoice của Enrollment PENDING_PAYMENT.
3. Nhân viên chốt ưu đãi và tổng học phí, chọn đóng một lần/nhiều kỳ.
4. Phụ huynh xác nhận phương án thông qua tư vấn tại quầy.
5. Backend kiểm tra tổng kỳ và ngày, lưu lịch.
6. Thu kỳ đầu bằng tiền mặt hoặc QR.
7. Chỉ khi tiền được xác nhận đủ ngưỡng kỳ đầu, chuyển Enrollment sang ENROLLED và cập nhật sĩ số một lần.
8. Hóa đơn còn dư nợ ở PARTIALLY_PAID; phụ huynh xem được kế hoạch và biên lai.

Với tổng học phí bằng 0, dùng luồng miễn học phí có kiểm soát, không tạo giao dịch ngân hàng 0đ.

### 4.2. Phụ huynh đóng online kỳ tiếp theo

1. Đăng nhập -> thông báo trong ứng dụng -> Học phí của con.
2. Chọn học viên/lượt ghi danh, xem tổng phải thu, đã thu, còn thiếu và kỳ đến hạn.
3. Chọn đóng kỳ hiện tại, số tiền được phép thu hoặc tất toán phần còn lại.
4. Backend kiểm tra ownership và số dư, tạo PaymentIntent.
5. Hiển thị QR/PayOS cùng số tiền và mã yêu cầu.
6. Chờ webhook hợp lệ. Trang quay lại từ cổng chỉ để hiển thị và hỏi trạng thái.
7. Backend ghi Payment, allocations và biên lai trong một transaction.
8. Trang phụ huynh cập nhật số dư và thông báo kết quả.

Nếu quay lại trang trước webhook, hiển thị Đang chờ xác nhận.
Giao dịch thất bại/hủy không làm giảm công nợ.
Intent còn chờ không được hiển thị là tiền đã thu.

### 4.3. Gia đình đến trung tâm đóng

1. Nhân viên tìm theo học viên, phụ huynh hoặc mã hóa đơn.
2. Backend trả số dư mới nhất và từng kỳ còn thiếu.
3. Chọn số tiền thu, phân bổ vào kỳ còn thiếu sớm nhất.
4. Tiền mặt: kiểm đếm, nhập tiền khách đưa, tính tiền thối.
5. QR tại quầy: tạo intent giống nghiệp vụ online, chờ xác nhận từ cổng.
6. Biên lai ghi người thu/nguồn xác nhận và số dư sau giao dịch.
7. Phụ huynh thấy cùng khoản thu trong tài khoản.

Không cho dùng nút thu tiền mặt để giả lập xác nhận giao dịch PayOS.
Nếu chuyển khoản trực tiếp ngoài PayOS cần nhập chứng cứ và đối chiếu ngân hàng trước khi ghi nhận thành công theo quyền.
MVP ưu tiên tiền mặt + PayOS; mở thêm kênh ngân hàng thủ công phải định nghĩa quy trình đối chiếu cụ thể.

### 4.4. Nhắc hạn và công nợ

- Job theo múi giờ Việt Nam tạo thông báo theo kỳ và phiên bản lịch.
- Dùng khóa chống trùng thông báo, job chạy lại không gửi nhiều bản cùng sự kiện.
- Phụ huynh chỉ nhận thông báo liên quan hồ sơ mình quản lý.
- Thông báo có link đến hóa đơn được backend kiểm quyền lại.
- Ngày đến hạn còn hiệu lực đến hết ngày, hôm sau còn thiếu mới overdue.
- Đã thu đủ thì ngừng nhắc nợ.
- Lịch được gia hạn làm cập nhật trạng thái công nợ và vô hiệu nội dung nhắc cũ đã lỗi thời.
- Thông báo trong ứng dụng được xem khi đăng nhập. Email/SMS cần yêu cầu và cấu hình riêng.

### 4.5. Đổi hạn đóng

- Nhân viên lập đề nghị từ trao đổi với phụ huynh.
- Quản lý/admin xem lịch cũ và lịch mới, lý do, tiền đã thu.
- Chỉ thay phần nghĩa vụ còn thiếu, không xóa lịch sử hay giảm tổng học phí qua thao tác gia hạn.
- Không sửa số tiền của phần đã tất toán.
- Kiểm tra version/số dư khi duyệt. Giao dịch mới làm đề nghị lỗi thời phải được tính lại.
- Lưu người đề nghị, người duyệt và old/new schedule.
- Giữ kỳ đóng còn phù hợp thời gian khóa, ngoại lệ cần lý do và quyền rõ.

## 5. Thiết kế dữ liệu

Tái dùng:
- Invoice, Payment, Enrollment, EnrollmentRequest, User, Student.
- Thu phí và biên lai hiện có.

Bổ sung theo từng giai đoạn:
- TuitionPlan: một kế hoạch đang có hiệu lực của invoice, mode, version, người tạo.
- TuitionInstallment: thứ tự, số phải thu, ngày đến hạn.
- PaymentAllocation: nối Payment với kỳ và số tiền phân bổ.
- PaymentIntent: orderCode duy nhất, invoice, planVersion, số tiền dự kiến, mục tiêu thu, người tạo, nguồn và trạng thái.
- PlanChangeRequest + chi tiết và lịch sử phiên bản.
- TuitionAdjustment/PaymentReversal cho thay đổi nghĩa vụ hoặc sửa thu sai có duyệt.
- Thông báo học phí có người nhận, loại, đối tượng, hạn, phiên bản và trạng thái đã đọc.

Các tổng cần hiển thị:
- Tổng nghĩa vụ hiện hành.
- Tổng thu thành công sau các điều chỉnh thu hợp lệ.
- Công nợ còn phải thu.
- Số tiền mỗi kỳ đã được phân bổ và còn thiếu.

Một dịch vụ tính tiền chung cung cấp các tổng này cho tất cả màn hình.

Tiền VND dùng BigDecimal, kiểm tra số nguyên ở bước nhận tiền. Tiền lẻ khi chia kỳ dồn kỳ cuối để tổng chính xác.
Đề xuất InvoiceStatus thêm PARTIALLY_PAID. Overdue là cờ tính từ lịch và số dư.
Các migration phải sửa check constraint tương ứng nếu DB có.

## 6. Phân chia backend theo kiến trúc hiện tại

Gốc:
com_be/src/main/java/com/talent/management/

### Entity và repository
- Entity dùng chung ở shared/entity; enum dùng chung ở shared/enums.
- Repository trong features/tuition_payment/repository.
- Không tạo repository truy vấn cùng một ledger ở nhiều feature rồi dùng quy tắc khác nhau.
- Chú ý bean name riêng nếu các repository có simple class name trùng feature khác.

### Controller, DTO và service
Giữ feature tuition_payment, chia theo trách nhiệm:
- TuitionPlanController/Service: tạo kế hoạch và lịch kỳ.
- TuitionPaymentController và PaymentPostingService: ghi nhận, phân bổ, biên lai.
- ReceivableController/Service: danh sách nợ và tổng hợp.
- PlanChangeController/Service: đề nghị và duyệt đổi hạn.
- ParentTuitionController: API ownership cho phụ huynh.
- TuitionNotificationService: thông báo trong ứng dụng.

Tên lớp là định hướng. Tái sử dụng lớp sẵn có trước khi thêm lớp mới.

Controller chỉ bind/validate DTO và phân quyền.
Service quyết định nghiệp vụ và transaction.
Repository làm query/khóa.
Mapper tạo response, tránh trả JPA entity và dữ liệu nội bộ.
Gateway PayOS lo giao tiếp, chữ ký và request/response cổng.

features/course_enrollment vẫn quản lý vòng đời ghi danh:
- Tiền đủ kỳ đầu kích hoạt ghi danh một lần.
- Thu những kỳ sau không kích hoạt lại.

Đọc và sửa tất cả caller của processPayment, gồm POS, webhook, đối soát và các đường demo được hỗ trợ.
Không chỉ thêm endpoint mới rồi để đường thu cũ vẫn đánh dấu PAID toàn bộ sau một kỳ.

## 7. Phân chia frontend

Chỉ chỉnh nguồn com_fe.

Màn hình:
- tuition-payment.html: mở rộng quầy hiện có, thêm lịch kỳ và thu tiếp.
- parent-tuition.html: trang Học phí của con.
- tuition-receivables.html: công nợ và quản lý lịch, nếu tách trang giúp giao diện rõ.
- Menu/thông báo trong auth.js và workspace shell, giữ bố cục sidebar tĩnh.

Mã:
- Logic API phụ huynh đặt trong module parent-tuition.
- Nghiệp vụ hiển thị thu/công nợ đặt trong js/tuition-payment.
- Tách gọi API, state và render DOM, theo cách module thu phí hiện có.
- Kiểm tra script classic/ES module hiện có trước khi import, tránh trùng biến toàn cục.
- Dùng callApi và appPath có sẵn, không rải BASE_URL/token handling ở mọi file.
- Không tính số dư quyết định ở frontend. Frontend hiển thị số tiền do backend xác nhận.
- Chọn kỳ, số tiền phải thu và tổng còn thiếu phải rõ.
- Nút lưu chống bấm trùng; lỗi tải có thử lại; màn hình nhỏ không tràn.
- Input tiền có hướng dẫn rõ. Hiển thị tiền có phân cách, payload là số chuẩn.

Sau thay đổi chạy:
node scripts/sync-frontend.mjs
node scripts/sync-frontend.mjs --check

Mirror gồm root, resources/static, com_be/src/main/webapp và src/main/webapp.

## 8. API định hướng và quyền

Nhân viên/quản lý:
- GET/POST /api/tuition-payment/{invoiceId}/plan
- POST /api/tuition-payment/{invoiceId}/collections
- POST /api/tuition-payment/{invoiceId}/payment-intents
- GET /api/tuition-payment/receivables
- POST /api/tuition-payment/{invoiceId}/plan-change-requests
- POST /api/tuition-payment/plan-change-requests/{id}/review

Phụ huynh:
- GET /api/parent-tuition/invoices
- GET /api/parent-tuition/invoices/{id}
- GET /api/parent-tuition/invoices/{id}/receipts
- POST /api/parent-tuition/invoices/{id}/payment-intents
- GET /api/parent-tuition/payment-intents/{id}
- GET /api/parent-tuition/notifications
- PATCH /api/parent-tuition/notifications/{id}/read

Webhook có đường public hiện có và chữ ký bắt buộc trong môi trường thật.

WebSecurityConfig hiện khóa /api/tuition-payment/** theo role nhân viên.
Route phụ huynh cần khai báo riêng; không mở toàn bộ tuition-payment cho PARENT.
Mọi thao tác parent kiểm tra quan hệ Student.parent ở backend, kể cả receipt, notification và intent.
Nhân viên ghi nhận tiền mặt, quản lý duyệt thay đổi lịch và đối chiếu các khoản ngân hàng ngoài dự kiến. Cổng thanh toán xác nhận QR qua webhook hợp lệ.

## 9. Khóa và xử lý tiền đồng thời

- Khóa invoice trước khi kiểm tra lại số dư và phân bổ.
- Ghi payment, allocation, invoice/enrollment state trong cùng transaction.
- Thống nhất thứ tự khóa invoice/enrollment/class giữa caller để tránh deadlock.
- Unique idempotencyKey/orderCode/bank reference, có scope phù hợp nguồn giao dịch.
- Retry cùng giao dịch trả biên lai cũ; tham số khác với cùng key phải báo xung đột.
- Không giữ transaction database trong lúc gọi mạng PayOS quá lâu. Tạo intent, gọi cổng và cập nhật trạng thái có cách phục hồi khi lỗi giữa chừng.
- Hóa đơn có nhiều intent đang chờ phải quản lý rõ, tránh nhiều QR cho phần dư nợ đã được trả.
- Đổi lịch không được làm mất dấu webhook của intent cũ.
- Sự kiện ngân hàng đã nhận tiền thừa/sai số tiền được ghi nhận để đối chiếu, không bỏ vì không phân bổ được.
- Xác định người tạo intent và nguồn xác nhận, bỏ fallback gán sai cho một nhân viên mặc định.

Khi chuyển từ online sang tiền mặt: kiểm tra lại intent và số dư, hủy intent nếu có thể.
Nếu tiền QR vẫn đến sau đó, xử lý khoản ngoài dự kiến.

## 10. Giới hạn phạm vi đã điều chỉnh

Người dùng cho biết phần dừng học/hoàn học phí chưa được thầy duyệt. Không đưa controller, entity, màn hình, migration, job và test hoàn phí từ workspace cũ vào nhánh mới.

Tính năng thu nhiều đợt bắt đầu từ main sạch, vẫn cần:
- Ghi nhận đúng tiền mặt, QR và số dư theo từng kỳ.
- Theo dõi tiền ngoài dự kiến khi có chuyển khoản thừa hoặc QR đến muộn.
- Lưu lịch sử điều chỉnh/đối chiếu, không làm mất khoản tiền thực nhận.
- Nếu phát sinh trả lại tiền ngoài dự kiến, ghi nhận cần xử lý theo quy trình trung tâm và thống nhất phạm vi trước khi xây dựng chức năng chi hoàn.

Có thể bổ sung module dừng học/hoàn phí sau khi được duyệt ở một yêu cầu riêng.

## 11. Migration và tương thích

1. Backup trước thay đổi.
2. UNPAID cũ -> kế hoạch một kỳ theo số tiền và hạn hiện có.
3. PAID cũ -> kiểm tra tiền thu SUCCESS trước khi tạo allocations.
4. Tiền không khớp hoặc giao dịch bị trùng -> danh sách đối chiếu, không tự tạo giao dịch giả.
5. FAILED/PENDING không làm tăng đã thu.
6. Hóa đơn CANCELLED không được migration làm sống lại hay tạo lịch thu mới.
7. Không tính nhiều lần cùng một khoản thu do join với nhiều allocation.
8. Mỗi query báo cáo, dashboard, receipt và pending search phải hiểu PARTIALLY_PAID.
9. Nếu dùng filtered index SQL Server, khai báo các SET option tương ứng để cả migration và DML chạy đúng.
10. Test SQL Server thật cho thời gian, enum/check và unique index, bên cạnh H2.

## 12. Các giai đoạn triển khai và đầu ra

### Giai đoạn 0 — Nền code và nhánh
Đầu ra: worktree sạch từ main mới nhất, checkout đúng feature/tuition-installments, baseline test xác định và không lẫn code hoàn phí.

### Giai đoạn 1 — Kỳ và ledger
Đầu ra: migration, mô hình, quy tắc tổng tiền/lịch, dịch vụ tính số dư và kế hoạch một lần tương thích dữ liệu cũ.

### Giai đoạn 2 — Thu tại quầy
Đầu ra: thu theo kỳ, thu nhiều kỳ, tất toán, biên lai từng giao dịch, ngưỡng ghi danh, không tăng lại sĩ số.

### Giai đoạn 3 — Phụ huynh và QR
Đầu ra: trang Học phí của con, API ownership, intent/QR/webhook dùng chung dịch vụ ghi tiền.
Kiểm tra cả tiền mặt đồng thời QR và callback đến muộn.

### Giai đoạn 4 — Công nợ và thông báo
Đầu ra: danh sách đến hạn/quá hạn, thông báo trong tài khoản, đề nghị/duyệt gia hạn có version.

### Giai đoạn 5 — Hồi quy và demo
Đầu ra: kiểm thử backend, frontend, SQL Server, dữ liệu demo có prefix riêng và kịch bản theo role.
Chỉ gọi hoàn tất khi cả online và tại quầy cập nhật nhất quán, tính công nợ đúng và dữ liệu cũ hoạt động.

## 13. Bộ kiểm thử chấp nhận

- 6 triệu chia 3 triệu + 1,5 triệu + 1,5 triệu, tổng chính xác.
- Kỳ đầu đủ -> ENROLLED; invoice PARTIALLY_PAID; sĩ số tăng một lần.
- Kỳ hai thu 1 triệu -> còn 500 nghìn, kỳ ba nguyên 1,5 triệu.
- Thu tiếp 2 triệu -> đóng đủ cả hai kỳ còn lại, invoice PAID.
- Phụ huynh đóng online -> quầy thấy số dư mới; quầy thu -> phụ huynh thấy ngay khi tải/cập nhật.
- CashGiven và tiền thối khác paymentAmount được tính đúng.
- Tổng kỳ sai, ngày sai, số tiền âm/0 không hợp lệ, thu vượt số dư -> chặn hoặc đưa tiền ngoài ngân hàng vào đối chiếu đúng quy tắc.
- Webhook gửi lặp, hai thu ngân đồng thời, tiền mặt và QR đồng thời, intent cũ đến muộn.
- Phụ huynh khác không xem/tạo intent/tải biên lai/đọc thông báo của hồ sơ khác.
- Nhắc hạn không trùng, ngày đến hạn chưa overdue, ngày sau mới overdue.
- Gia hạn đang chờ mà có tiền thu mới -> tính lại/version conflict có hướng dẫn.
- Dữ liệu cũ, hóa đơn miễn học phí, lớp/điểm danh/học bù/admin vẫn hoạt động.
- SQL Server không lặp lại lỗi so sánh TIME với DATETIME.

Tái dùng scripts/test-backend.ps1, frontend-integration.test.cjs, class-list-loading.test.cjs,
cùng các test lớp/cơ sở hiện có, bổ sung ca có ý nghĩa cho luồng mới.
Các test UI mock không chứng minh webhook thật đã chạy. Phân biệt kết quả sandbox với giao dịch ngân hàng thật.

## 14. Kịch bản demo cuối cùng

Tài khoản riêng cho phụ huynh A, phụ huynh B, staff và quản lý.

A. Xếp lớp -> chốt lịch 3 kỳ -> thu kỳ đầu tại quầy -> vào học.
B. Đến hạn -> phụ huynh nhận thông báo -> trả kỳ hai online -> quầy thấy biên lai và số dư.
C. Gia đình đến quầy tất toán -> tài khoản phụ huynh thấy không còn nợ.
D. Hồ sơ khác đóng thiếu kỳ hai -> quá hạn -> gửi gia hạn -> quản lý duyệt -> thu tiếp.
E. Mô phỏng QR gửi lặp hoặc tiền mặt và QR đồng thời -> không ghi nhận trùng, khoản ngoài dự kiến được đưa vào đối chiếu.
F. Dùng phụ huynh B kiểm tra quyền dữ liệu.

## 15. Báo cáo khi bàn giao kết quả

Nêu:
- Nhánh và commit hiện tại.
- Những màn hình và luồng đã xong.
- Migration đã chạy ở đâu.
- Kết quả test thực tế và giới hạn sandbox.
- URL local, tài khoản demo, các bước thử.
- Mục còn cần người dùng quyết định nếu có.

Không tự push/merge main chỉ vì có file kế hoạch. Thực hiện khi người dùng yêu cầu.
