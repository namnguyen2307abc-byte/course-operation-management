> Cập nhật 11/11/2026: tính năng đã hoàn thành và được đồng bộ theo quy trình trong `AGENTS.md`. Nghiệp vụ hiện hành nằm ở `docs/NGHIEP_VU_HOC_PHI_DON_GIAN_VI.md`; test từ chốt lớp ở `docs/TEST_TU_CHOT_LOP_DEN_THANH_TOAN_VI.md`. Không triển khai lại gia hạn giữ chỗ 48 giờ, màn hình đối chiếu ngân hàng hay khu Quá hạn cần liên hệ riêng. Điều chỉnh khoản thu nhầm hiện là tab riêng có tìm kiếm và phân trang 5 mục. Các kế hoạch bên dưới là nền lịch sử.

# Context bàn giao dự án và kế hoạch thu nhiều đợt, quản lý công nợ

Cập nhật ngày 08/11/2026. Ngôn ngữ trao đổi và giao diện là tiếng Việt.

## 1. Phạm vi mới nhất

Người dùng yêu cầu bỏ phần hoàn học phí vì thầy chưa duyệt. Code, menu, màn hình, test, migration, seed, hình sơ đồ và nhánh local của tính năng này đã được gỡ.
Chỉ tiếp tục chức năng thầy đã duyệt: thu nhiều đợt, công nợ, thông báo trong tài khoản phụ huynh, thanh toán online QR/PayOS hoặc tại quầy và gia hạn lịch đóng.
Đọc file TUITION_INSTALLMENTS_IMPLEMENTATION_PLAN_VI.md cùng thư mục để biết kế hoạch chi tiết.
Tính năng thu nhiều đợt đã được triển khai trên feature/tuition-installments theo lệnh người dùng ngày 08/11/2026. Xem docs/tuition-installments.md để chạy migration, cấu hình và test. Phần kế hoạch gốc bên dưới chỉ còn là mô tả nền trước triển khai.

Các bảng enrollment_withdrawals, tuition_refunds, withdrawal_actions, cột enrollments.withdrawal_effective_date và dữ liệu demo QA-WD vẫn còn trong database local. Thao tác xóa DB đã bị bộ xét duyệt tự động từ chối vì người dùng chỉ yêu cầu xóa code và nhánh. Không tự xóa những bảng hoặc dữ liệu này ở chat sau nếu chưa có yêu cầu phù hợp. Code main không sử dụng chúng.

## 2. Dự án và luồng nghiệp vụ

Tên miền nghiệp vụ: trung tâm đào tạo năng khiếu đa bộ môn.
Phụ huynh đưa con đến kiểm tra đầu vào -> giáo viên đề xuất khóa -> nhân viên tư vấn và chốt lớp -> thu tiền mặt hoặc QR -> ghi danh -> học và điểm danh/nghỉ học bù.
Kỳ đầu thường đóng ở quầy. Các kỳ sau dự kiến thông báo tới tài khoản phụ huynh, phụ huynh chọn online hoặc tới quầy.
Hai kênh dùng chung hóa đơn, lịch kỳ và số dư.
Người dùng phụ trách thanh toán, muốn nghiệp vụ có business rule nằm trong luồng dữ liệu chính để bảo vệ theo swimlane.

## 3. Workspace và Git

Workspace Windows:
D:/Ky_7/SBA301/Apartment_management/course-operation-management

Remote:
https://github.com/namnguyen2307abc-byte/course-operation-management.git

Trạng thái sau dọn code:
- Workspace chính ở feature/tuition-installments, tách từ main 7d47cba. Code tính năng mới đang modified/untracked, chưa merge/push main.
- Nhánh local feature/enrollment-withdrawal-refund đã xóa. Nhánh này trước đây chưa được push.
- Code hoàn phí đã gỡ. Các thay đổi đang có là phần thu nhiều đợt mới.
- Worktree .local-run/nam-class-integration ở detached HEAD 7d47cba, được giữ làm nơi kiểm thử tích hợp trước đó.
- Nhánh class-management ở feb6345 đã được push. Main chứa các bản sửa tải danh sách lớp và thông báo đăng ký của Nam.
- Còn tài liệu PDF, output/ và scratch/ của người dùng. Không xóa những mục đó để làm sạch Git.

Khi được người dùng yêu cầu bắt đầu code:
1. Kiểm tra lại git status, git worktree list, git log và fetch main.
2. Dùng nền main mới nhất.
3. Tạo và chuyển sang feature/tuition-installments trước khi sửa tính năng.
4. Nếu nhánh đã tồn tại, kiểm tra lịch sử trước khi dùng.
5. Không tự merge/push main chỉ vì đọc context.
6. Không dùng blanket git clean hoặc reset lên tài liệu người dùng.

## 4. Kiến trúc backend

- com_be, Spring Boot 3.3.4, Maven.
- Java target 17, local dùng JDK 21 hoặc 17.
- SQL Server, Spring Data JPA/Hibernate.
- Spring Security + JWT, xác định người dùng qua CurrentUserService.
- Package gốc com.talent.management.
- shared/entity chứa JPA entity dùng chung.
- shared/enums chứa enum nghiệp vụ dùng chung.
- features/<feature>/controller nhận HTTP, validate DTO, phân quyền.
- features/<feature>/dto/request và dto/response định nghĩa dữ liệu vào/ra.
- features/<feature>/service và service/impl chứa nghiệp vụ và giao dịch.
- features/<feature>/repository truy cập DB.
- Một số phân hệ có mapper riêng. Tuân theo cách chia hiện có, không nhét xử lý tiền vào controller.
- Tên repository trùng nhau ở các feature đã có bean name riêng; giữ quy ước để tránh xung đột Spring.

Các feature:
auth, admin_management, course_management, course_enrollment, placement_test,
class_management, branch_facility_enrollment, attendance_makeup, tuition_payment.

Role thực tế:
ADMIN, BRANCH_MANAGER, STAFF, TEACHER, PARENT, STUDENT.
CASHIER có xuất hiện trong một số label frontend nhưng không có trong enum Role backend hiện tại.

Mô hình hiện chưa có liên kết đầy đủ giữa tài khoản nhân viên/quản lý và một chi nhánh cụ thể. Phân quyền BRANCH_MANAGER hiện chủ yếu theo role như các module khác. Nếu bổ sung giới hạn chi nhánh, cần mô hình và quy tắc riêng, tránh khẳng định hệ thống đã có.

## 5. Kiến trúc frontend

- Nguồn chỉnh sửa chuẩn là com_fe, HTML/CSS/JavaScript thuần.
- com_fe/pages chứa màn hình.
- com_fe/js/api.js có callApi, thêm bearer token, xử lý lỗi.
- com_fe/js/auth.js quản lý thông tin đăng nhập, menu theo role và appPath.
- com_fe/js/workspace-shell.js chạy đồng bộ sau sidebar tĩnh để hạn chế giật khi chuyển trang.
- com_fe/js/tuition-payment là module quầy thu phí có các file service.js, main.js, modal-payment.js, mapper/render helpers...
- Phần hoàn phí có API riêng trong com_fe/js/tuition-payment/refund-api.js.
- Phần đơn dừng học có com_fe/js/course-enrollment/withdrawal-api.js và withdrawal-page.js.
- Không giả định toàn frontend cùng dùng ES modules. service.js của thu phí có export, các file hoàn phí hiện được load như classic scripts.

Sau sửa frontend:
node scripts/sync-frontend.mjs
node scripts/sync-frontend.mjs --check

Script lấy com_fe và đồng bộ tới:
- root,
- com_be/src/main/resources/static,
- com_be/src/main/webapp,
- src/main/webapp.

Chỉnh com_fe trước rồi sync, tránh sửa riêng từng mirror.

API base hiện phụ thuộc port trong api.js. Chạy cùng backend ở http://127.0.0.1/ tránh frontend port lẻ tự gọi nhầm localhost:8080.
Không sửa BASE_URL đại trà khi chưa xác định môi trường chạy.

## 6. Nghiệp vụ hiện có đã đối chiếu code

### Đăng ký, kiểm tra và xếp lớp

CourseEnrollmentController:
- Phụ huynh gửi yêu cầu cho học viên thuộc mình.
- Nếu yêu cầu kiểm tra đầu vào mà chưa có kết quả -> WAITING_PLACEMENT.
- Giáo viên lưu kết quả kiểm tra gắn yêu cầu, đề xuất khóa -> READY_FOR_ASSIGNMENT.
- Nhân viên/quản lý/admin chọn lớp OPEN, đúng khóa, còn chỗ.
- Backend kiểm tra lại chỗ và ghi danh trùng.
- Tạo Enrollment PENDING_PAYMENT và Invoice UNPAID.
- Hóa đơn hiện đặt hạn thanh toán sau một tháng. Đây chưa phải cơ chế hết hạn giữ chỗ.
- Frontend chuyển tới thu học phí với invoiceId.
- Chỗ khả dụng có tính cả lượt PENDING_PAYMENT.

PlacementTestServiceImpl đã có cầu nối cập nhật EnrollmentRequest. Một số tài liệu cũ nói chưa nối là lỗi thời.

### Quản lý lớp và cơ sở

Đã có:
- Sinh lịch buổi theo thứ/ngày/giờ/số buổi, tính ngày kết thúc.
- Giáo viên đúng chuyên môn teachingSubject, khác subject bộ môn rộng của kiểm tra đầu vào.
- Phòng đúng cơ sở, loại phòng, sức chứa, trạng thái và dụng cụ.
- Phát hiện trùng phòng, trùng lịch giáo viên và khoảng nghỉ giáo viên tối thiểu 30 phút.
- Giữ lịch sử phân công, giới hạn sửa các lịch đã có.
- Cập nhật cơ sở vật chất có kiểm tra lớp đang sử dụng.
Không đề xuất lại các chức năng này như chưa có.

### Thu học phí hiện tại

Các file chính:
com_be/src/main/java/com/talent/management/shared/entity/Invoice.java
com_be/src/main/java/com/talent/management/shared/entity/Payment.java
com_be/src/main/java/com/talent/management/features/tuition_payment/service/impl/TuitionPaymentServiceImpl.java
com_be/src/main/java/com/talent/management/features/tuition_payment/gateway/PayOSGateway.java

Invoice:
originalAmount, discountAmount, discountType, finalAmount, dueDate, enrollment.
InvoiceStatus chỉ UNPAID, PAID, CANCELLED.

Payment:
invoice, amount, paymentMethod, paymentCode, cashier, paymentDate,
cashGiven, changeAmount, bankTransactionId, status.
PaymentStatus gồm SUCCESS/PENDING/FAILED.

Code processPayment hiện:
- Lấy hóa đơn.
- Chặn hóa đơn PAID.
- Có thể cập nhật số tiền cuối và ưu đãi theo request.
- Đặt invoice PAID sau một lần thu.
- Tạo Payment SUCCESS bằng finalAmount.
- Enrollment chuyển ENROLLED.
- EnrollmentRequest chuyển APPROVED.
- Cập nhật sĩ số.
Khi làm thu nhiều đợt cần kiểm tra lại cập nhật sĩ số, bảo đảm đủ kỳ đầu chỉ ghi danh và tăng sĩ số một lần.

Điểm bắt buộc sửa khi làm thu nhiều đợt:
- Phân biệt học phí cuối cùng của cả hóa đơn và số tiền thu của một giao dịch.
- Không dùng finalAmount để biểu thị tiền của một kỳ.
- Không thay đổi tổng học phí khi tạo QR chỉ thu một phần.
- Chỉ chuyển trạng thái ghi danh và tăng sĩ số một lần khi đạt ngưỡng kỳ đầu.
- Tất cả đường vào tiền mặt, QR/PayOS, webhook và job đối soát dùng một nghiệp vụ ghi nhận tiền chung.

PayOS hiện có verifyWebhookSignature, nhưng code xử lý hiện vẫn suy hóa đơn từ description/orderCode và có fallback người thu.
Code tạo link có thể thay finalAmount theo customAmount.
Chưa có entity payment intent gắn orderCode duy nhất, expectedAmount, invoice và người tạo đủ để làm thu nhiều đợt an toàn.
Phải đọc code hiện tại, không mặc định gateway đã đầy đủ idempotency/khóa/chống ghi nhận sai số tiền.

API thu phí đang dành cho nhân viên/quản lý/admin. Phụ huynh chưa có cổng thanh toán trực tuyến riêng.
Báo cáo thu tiền hiện có trong trang thu học phí. Trước đây đã phát hiện lọc giao dịch thất bại chưa đúng. Các thay đổi báo cáo lớn từng bị người dùng yêu cầu rollback. Không tự đưa cả dự án báo cáo/bàn giao tiền mặt trở lại.

### Điểm danh và nghỉ/học bù

Có luồng xin nghỉ từng buổi và xếp học bù.
Một buổi có thể COMPLETED, SCHEDULED, CANCELLED.
Vắng cá nhân không đồng nghĩa được hoàn tiền.
Buổi bù thay thế buổi gốc cần tránh tính phí hai lần.
DEFERRED trong học bù hiện là hoãn một ca bù, không phải chức năng bảo lưu toàn khóa.


## 7. Hướng triển khai thu nhiều đợt

Kế hoạch chuẩn nằm ở TUITION_INSTALLMENTS_IMPLEMENTATION_PLAN_VI.md.
Nhánh phải là feature/tuition-installments lấy từ main sạch.
Entity dự kiến gồm kế hoạch kỳ, kỳ đóng, phân bổ tiền, payment intent, đề nghị đổi lịch và thông báo.
Các giá trị 50% kỳ đầu, tối đa ba kỳ, nhắc trước ba ngày và danh sách xử lý sau bảy ngày quá hạn là mặc định đề xuất cần được chốt với người dùng.
Không lấy lại module dừng học/hoàn phí đã bỏ.
Vẫn phải lưu và đối chiếu tiền ngân hàng nhận ngoài dự kiến, bảo đảm không mất dấu tiền hoặc ghi nhận trùng.

## 8. Môi trường và kiểm thử

Backend com_be, JDK 17/21, Maven, SQL Server local database course_operation_management.
Có thể dùng Windows integrated auth:
sqlcmd -S localhost -E -C -d course_operation_management -b -i <script.sql>
Không đưa bí mật DB/PayOS thật vào context hoặc commit.

Ứng dụng local dùng cổng 80, http://127.0.0.1/.
PID nằm trong .local-run/backend-http80.pid; phải xác minh tiến trình trước khi dừng.
Bản main sau dọn được build vào .local-run/main-clean-build.
Khởi động với app.seed.enabled=false và spring.jpa.hibernate.ddl-auto=validate để không tự thay DB.

Lệnh kiểm tra:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-backend.ps1
node scripts/sync-frontend.mjs --check
node --test scripts/frontend-integration.test.cjs scripts/class-list-loading.test.cjs
node --test scripts/class-scheduling-ui.test.cjs scripts/facility-ui.test.cjs

Chrome tests cần playwright và Chrome, class test cần CLASS_TEST_URL trỏ server frontend tương ứng.
Test UI mock không thay kiểm tra SQL Server.
Main cùng class-management đã qua 188 backend và 20 frontend khi merge Nam.
Lượt dọn code chạy lại build main và 12 frontend chung/tải danh sách, cùng smoke local.
Không tuyên bố test lịch sử vừa được chạy lại nếu chưa thực hiện.

Cổng 8080 từng thuộc dự án api-gateway khác. Không dừng ứng dụng đó.
Chạy frontend cùng backend cổng 80 để tránh api.js chọn nhầm backend khi dùng port lẻ.

## 9. Tài liệu

RDS-Document.docx (1).pdf là tài liệu nghiệp vụ trung tâm. Sơ đồ Course Registration and Enrollment Process ở trang 8.
Report 1 - RDS-Document.pdf là mẫu nghiệp vụ khách sạn, không dùng làm business rule trung tâm.
File Class_and_Facibility trước đó đã được đọc và xóa theo yêu cầu; docs/class-facility-integration.md giữ mô tả các quy tắc lớp/cơ sở.

## 10. Lời nhắn dùng ở chat mới

Hãy đọc context này và TUITION_INSTALLMENTS_IMPLEMENTATION_PLAN_VI.md, đối chiếu code và trạng thái Git.
Khi tôi yêu cầu thực hiện, tạo và chuyển sang feature/tuition-installments từ main mới nhất trước khi code.
Chỉ làm thu nhiều đợt, thông báo cho phụ huynh, thanh toán online/tại quầy và quản lý công nợ theo kế hoạch.
Giữ cấu trúc backend/frontend, kiểm tra SQL Server và các luồng hiện có.

## Trạng thái triển khai ngày 08/11/2026

- Đã bổ sung tuition_plans/installments/allocations/payment_intents/bank_events/plan_changes/notices/reversals.
- ParentTuitionController và trang parent-tuition.html cho phụ huynh.
- TuitionInstallmentController và tuition-receivables.html cho quầy, quản lý công nợ, gia hạn và đối chiếu.
- Đường thu cũ chuyển đến màn hình thu chung. processPayment dùng dịch vụ ghi tiền chung.
- Gateway chỉ mô phỏng khi cấu hình riêng bật; không tự fallback khi PayOS lỗi.
- Migration database/tuition_installments.sql đã áp dụng và kiểm tra chạy lại trên SQL Server local, đã backup trước.
- Demo prefix QA-INST, tài khoản qa_inst_parent/qa_inst_other/qa_inst_staff/qa_inst_admin cùng mật khẩu Test@123456.
- Local cổng 80 bật tuition.simulation.enabled=true. Cần thông tin merchant và webhook công khai để kiểm thử chuyển tiền thật.
- docs/tuition-installments.md chứa hướng dẫn và các chính sách đang áp dụng.
- Dữ liệu demo Bảo Nam đã được tất toán bằng kiểm thử thu đồng thời; các hồ sơ khác dùng để tự test.
- Có bộ kiểm thử JPA nhiều kỳ, Chrome UI và smoke SQL Server gồm hai yêu cầu thu cuối đồng thời. Đọc kết quả mới nhất ở .local-run/installment-tests.log và test artifacts, không tự coi mọi kết quả lịch sử là vừa chạy.
