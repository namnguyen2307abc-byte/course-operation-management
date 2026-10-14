# Sơ đồ luồng dữ liệu nghiệp vụ — Course Operation Management

Đối chiếu controller, service và entity trong mã nguồn ngày 05/10/2026. Sơ đồ mô tả các luồng đã xuất hiện trong code, không coi mô tả trong README hoặc Swagger là chức năng đã hoàn thiện. Các vai trò trên sơ đồ là tác nhân nghiệp vụ; xem ghi chú để phân biệt với quyền API thực tế.

Quy ước: hình chữ nhật = tác nhân; hình bo góc = tiến trình; hình trụ = kho dữ liệu. Mũi tên mang tên dữ liệu được truyền, không biểu diễn thứ tự thời gian. Các bảng được nhóm theo nghiệp vụ để sơ đồ dễ đọc.

## 1. Sơ đồ ngữ cảnh

```mermaid
flowchart LR
    PH[Phụ huynh / học viên]
    NV[Nhân viên / thu ngân]
    GV[Giáo viên]
    QT[Admin / quản lý cơ sở]
    SYS((Hệ thống COM))
    PAY[PayOS]
    PH -->|Thông tin tài khoản, yêu cầu ghi danh, đơn nghỉ| SYS
    SYS -->|JWT, lớp gợi ý, trạng thái đơn, thông tin học phí| PH
    NV -->|Lịch test, quyết định xếp lớp, lịch bù, thông tin thu tiền| SYS
    SYS -->|Danh sách chờ xử lý, hóa đơn, biên lai| NV
    GV -->|Điểm test, nhận xét, quyết định duyệt nghỉ, điểm danh| SYS
    SYS -->|Lịch test, đơn nghỉ, kết quả xử lý| GV
    QT -->|Thông tin cơ sở, phòng, thiết bị; bộ lọc báo cáo| SYS
    SYS -->|Danh mục, lịch sử thu tiền, thống kê| QT
    SYS -->|Thông tin tạo giao dịch| PAY
    PAY -->|Link thanh toán, QR, webhook giao dịch| SYS
```

## 2. DFD tổng quan các phân hệ

```mermaid
flowchart TB
    U[Người sử dụng]
    P1(1. Tài khoản)
    P2(2. Cơ sở vật chất)
    P3(3. Kiểm tra đầu vào)
    P4(4. Đăng ký khóa học)
    P5(5. Học phí và thanh toán)
    P6(6. Nghỉ học, học bù, điểm danh)
    D1[(users / students)]
    D2[(branches / rooms / equipments)]
    D3[(placement_schedules / placement_tests)]
    D4[(courses / classes / enrollments / enrollment_requests)]
    D5[(invoices / payments)]
    D6[(lessons / absence_requests / makeup_registrations / attendances)]
    U -->|Tài khoản| P1
    P1 -->|JWT và vai trò| U
    D1 -->|Thông tin tài khoản| P1
    P1 -->|Tài khoản mới| D1
    U -->|Thông tin cơ sở, phòng, thiết bị| P2
    P2 -->|Bản ghi mới| D2
    D2 -->|Danh mục tra cứu| P2
    P2 -->|Danh sách và kết quả tạo| U
    U -->|Lịch test, điểm và nhận xét| P3
    P3 -->|Lịch và đánh giá| D3
    D3 -->|Lịch, kết quả đã lưu| P3
    P3 -->|Lịch và kết quả test| U
    P3 -->|Khuyến nghị đọc từ placement_tests| P4
    U -->|Yêu cầu đăng ký, quyết định xếp lớp| P4
    D1 -->|Học viên thuộc phụ huynh| P4
    D4 -->|Khóa, lớp, sĩ số, yêu cầu hiện có| P4
    P4 -->|Yêu cầu và ghi danh chờ thanh toán| D4
    P4 -->|Hóa đơn UNPAID| D5
    P4 -->|Trạng thái yêu cầu và mã hóa đơn| U
    U -->|Thông tin thanh toán, miễn giảm, bộ lọc| P5
    D5 -->|Hóa đơn, giao dịch| P5
    P5 -->|Hóa đơn PAID và giao dịch SUCCESS| D5
    P5 -->|Ghi danh ENROLLED, yêu cầu APPROVED, sĩ số tăng| D4
    P5 -->|QR, biên lai, báo cáo| U
    U -->|Đơn nghỉ, quyết định duyệt, lịch bù, điểm danh| P6
    D1 -->|Học viên và quan hệ phụ huynh| P6
    D6 -->|Buổi học, đơn nghỉ, ca bù, điểm danh| P6
    P6 -->|Đơn nghỉ, ca bù và điểm danh cập nhật| D6
    P6 -->|Trạng thái và danh sách buổi học| U
```

## 3. Tài khoản và hồ sơ học viên

```mermaid
flowchart LR
    U[Người dùng]
    A(1.1 Đăng nhập / đăng ký)
    D[(users)]
    J(1.2 Phát hành JWT)
    H(1.3 API hồ sơ học viên — stub)
    U -->|Username, password; thông tin đăng ký| A
    D -->|Tài khoản, trạng thái, vai trò| A
    A -->|Tài khoản mới nếu username chưa tồn tại| D
    A -->|Danh tính và vai trò| J
    J -->|JWT và thông tin người dùng| U
    U -->|Thông tin học viên / ID / parentId| H
    H -->|DTO tối giản hoặc danh sách rỗng| U
```

- `login()` hiện kiểm tra trạng thái tài khoản nhưng **không so khớp password**; username chưa có sẽ được tạo tự động, vai trò suy ra từ username. Sơ đồ vì vậy không có bước xác minh mật khẩu thành công.
- `register()` dùng lại tài khoản nếu username đã tồn tại rồi phát hành token.
- `getUsersByRole`, `createStudent`, `getAllStudents`, `getStudentById` trong `AuthService` là stub; `createStudent` chưa ghi vào `students`. Các phân hệ khác vẫn đọc dữ liệu học viên có sẵn qua repository.

Nguồn: `features/auth/service/AuthService.java`, `features/auth/service/JwtService.java`.

## 4. Quản lý cơ sở, phòng học và thiết bị

```mermaid
flowchart LR
    A[Admin]
    U[Người tra cứu]
    P1(2.1 Tạo cơ sở)
    P2(2.2 Tạo phòng)
    P3(2.3 Tạo thiết bị)
    P4(2.4 Tra cứu và lọc)
    B[(branches)]
    R[(rooms)]
    E[(equipments)]
    A -->|Thông tin cơ sở| P1
    P1 -->|Cơ sở mới| B
    P1 -->|Thông tin đã tạo| A
    A -->|Thông tin phòng và branchId| P2
    B -->|Cơ sở tham chiếu| P2
    P2 -->|Phòng thuộc cơ sở| R
    P2 -->|Thông tin đã tạo| A
    A -->|Thông tin thiết bị và roomId| P3
    R -->|Phòng tham chiếu| P3
    P3 -->|Thiết bị thuộc phòng| E
    P3 -->|Thông tin đã tạo| A
    U -->|branchId, danh mục thiết bị| P4
    B -->|Danh sách cơ sở| P4
    R -->|Phòng theo cơ sở| P4
    E -->|Thiết bị qua liên kết phòng| P4
    P4 -->|Danh sách / lựa chọn cho dropdown| U
```

Hiện có API tạo và tra cứu; chưa có API sửa/xóa trong `BranchFacilityController`. GET cơ sở được mở công khai; POST yêu cầu ADMIN.

Nguồn: `features/branch_facility_enrollment/controller/BranchFacilityController.java`, `service/impl/BranchFacilityServiceImpl.java`.

## 5. Lập lịch và đánh giá kiểm tra đầu vào

```mermaid
flowchart TB
    N[Nhân viên / quản lý / admin / giáo viên]
    G[Giáo viên / admin]
    P1(3.1 Tạo và tra cứu lịch test)
    P2(3.2 Upload minh chứng)
    P3(3.3 Lưu đánh giá)
    P4(3.4 Lấy khuyến nghị mới nhất)
    DS[(placement_schedules)]
    F[(Tệp trong thư mục uploads)]
    DT[(placement_tests)]
    C[(students / courses)]
    EN[Phân hệ đăng ký khóa học]
    N -->|Tên học viên, lịch, cơ sở, phòng; bộ lọc| P1
    P1 -->|Lịch SCHEDULED| DS
    DS -->|Lịch và đánh giá đã lưu| P1
    P1 -->|Danh sách / chi tiết lịch| N
    G -->|Audio, video, ảnh, bản ghi| P2
    P2 -->|Tệp tải lên| F
    P2 -->|URL tệp| G
    G -->|scheduleId, điểm, mức đề xuất, nhận xét, URL| P3
    DS -->|Lịch test hiện có| P3
    P3 -->|Đánh giá và trạng thái COMPLETED| DS
    P3 -->|Kết quả đánh giá đã lưu| G
    EN -->|studentId| P4
    C -->|Học viên và khóa học tham chiếu| P4
    DT -->|Bài test mới nhất và recommendedCourse| P4
    P4 -->|Điểm, trình độ, khóa gợi ý hoặc chưa có kết quả| EN
```

**Điểm chưa nối trong code:** `saveAssessment()` chỉ ghi `placement_schedules`, trong khi `getLatestRecommendation()` đọc `placement_tests`. Không có thao tác đồng bộ giữa hai bảng trong service này. Vì vậy, chấm xong trên màn hình lịch test chưa tự tạo kết quả mà luồng ghi danh đang cần.

Controller của phân hệ cho ADMIN, TEACHER, STAFF, BRANCH_MANAGER; thao tác chấm chỉ cho TEACHER/ADMIN. Có hàm lọc lịch theo email phụ huynh nhưng không đồng nghĩa PARENT được gọi controller này.

Nguồn: `features/placement_test/service/impl/PlacementTestServiceImpl.java`, `controller/PlacementTestController.java`, `shared/service/FileStorageService.java`.

## 6. Đăng ký khóa học và xếp lớp

```mermaid
flowchart TB
    PH[Phụ huynh]
    NV[Nhân viên / quản lý / admin]
    P1(4.1 Tra cứu khóa và lớp gợi ý)
    P2(4.2 Tiếp nhận yêu cầu)
    P3(4.3 Đồng bộ kết quả test và xét duyệt)
    S[(students)]
    C[(courses / classes)]
    T[(placement_tests)]
    R[(enrollment_requests)]
    E[(enrollments)]
    I[(invoices)]
    PH -->|childId, courseId| P1
    S -->|Học viên thuộc phụ huynh| P1
    C -->|Khóa học, lớp OPEN, chỗ còn lại| P1
    T -->|Khóa học đề xuất từ test| P1
    P1 -->|Lớp gợi ý và lớp đang mở| PH
    PH -->|childId, placementRequested, preferredCourseId, ghi chú| P2
    S -->|Quan hệ phụ huynh - học viên| P2
    R -->|Yêu cầu đang mở để kiểm tra trùng| P2
    T -->|Khuyến nghị hiện có| P2
    P2 -->|WAITING_PLACEMENT hoặc READY_FOR_ASSIGNMENT| R
    P2 -->|Trạng thái tiếp nhận| PH
    NV -->|requestId, quyết định, classId, ghi chú| P3
    R -->|Yêu cầu cần xử lý| P3
    T -->|Khuyến nghị khi refresh hoặc duyệt| P3
    C -->|Khóa, trạng thái lớp, sĩ số| P3
    E -->|Ghi danh hiện có và chỗ đã giữ| P3
    P3 -->|REJECTED hoặc READY_FOR_ASSIGNMENT hoặc PENDING_PAYMENT| R
    P3 -->|Ghi danh PENDING_PAYMENT khi chấp thuận| E
    P3 -->|Hóa đơn UNPAID khi chấp thuận| I
    P3 -->|Kết quả xử lý và mã hóa đơn| NV
    R -->|Lịch sử yêu cầu| P1
    P1 -->|Trạng thái yêu cầu của phụ huynh| PH
```

- Không yêu cầu test: phải chọn khóa, yêu cầu vào `READY_FOR_ASSIGNMENT`.
- Có yêu cầu test: có khóa đề xuất thì sẵn sàng xếp lớp, chưa có thì `WAITING_PLACEMENT`. Gửi yêu cầu này **không tự tạo lịch test**.
- Duyệt: kiểm tra lớp OPEN, chỗ trống, đúng khóa, chưa ghi danh trùng. Tạo ghi danh và hóa đơn chờ thanh toán; chưa tăng sĩ số tại bước này.
- Từ chối: cập nhật `REJECTED`, không tạo hóa đơn và ghi danh.
- Thanh toán thành công mới cập nhật `ENROLLED` và tăng sĩ số.

Nguồn: `features/course_enrollment/service/CourseEnrollmentService.java`.

## 7. Học phí, miễn giảm và thanh toán

```mermaid
flowchart TB
    NV[Thu ngân / người thao tác]
    Q[Admin / người xem báo cáo]
    PAY[PayOS]
    P0(5.0 Tạo phiếu đăng ký trực tiếp)
    P1(5.1 Tra cứu hóa đơn và miễn giảm)
    P2(5.2 Tạo VietQR / link PayOS)
    P3(5.3 Tiếp nhận webhook PayOS)
    P4(5.4 Ghi nhận thanh toán)
    P5(5.5 Tra cứu trạng thái, lịch sử và báo cáo)
    P6(5.6 Mô phỏng webhook ngân hàng)
    S[(students / classes / courses)]
    I[(invoices)]
    D[(payments)]
    E[(enrollments / enrollment_requests / classes)]
    NV -->|studentId, classId, ghi chú| P0
    S -->|Học viên, lớp, học phí| P0
    P0 -->|Ghi danh PENDING_PAYMENT| E
    P0 -->|Hóa đơn UNPAID| I
    P0 -->|Phiếu chờ thanh toán| NV
    NV -->|Từ khóa, invoiceId, miễn giảm và lý do| P1
    I -->|Học phí gốc và trạng thái| P1
    P1 -->|Miễn giảm và số tiền cuối| I
    P1 -->|Hóa đơn sau miễn giảm| NV
    NV -->|invoiceId, số tiền| P2
    I -->|Thông tin hóa đơn| P2
    P2 -->|Thông tin miễn giảm và số tiền cho link| I
    P2 -->|Yêu cầu tạo giao dịch| PAY
    PAY -->|Link thanh toán và QR| P2
    P2 -->|QR / link và nội dung chuyển khoản| NV
    PAY -->|Payload giao dịch và chữ ký| P3
    I -->|Hóa đơn đối chiếu| P3
    D -->|Giao dịch đã ghi nhận| P3
    P3 -->|Dữ liệu thanh toán sau kiểm tra chữ ký| P4
    NV -->|Phương thức, tiền khách đưa, thông tin miễn giảm| P4
    NV -->|invoiceId, số tiền mô phỏng| P6
    P6 -->|Thông tin giao dịch giả lập| P4
    I -->|Hóa đơn chưa thanh toán| P4
    E -->|Ghi danh liên quan và sĩ số hiện tại| P4
    P4 -->|PAID| I
    P4 -->|Giao dịch SUCCESS| D
    P4 -->|ENROLLED, APPROVED nếu có, tăng sĩ số| E
    P4 -->|Biên lai và tiền thừa| NV
    NV -->|invoiceId để polling| P5
    Q -->|Thời gian, thu ngân, từ khóa| P5
    I -->|Trạng thái và số tiền hóa đơn| P5
    D -->|Giao dịch thu tiền| P5
    P5 -->|Trạng thái và biên lai| NV
    P5 -->|Lịch sử, doanh thu và KPI| Q
```

- Ba đầu vào cùng dùng `processPayment()`: thao tác thu tiền trực tiếp, webhook PayOS, webhook mô phỏng.
- Sinh VietQR không tự chứng minh đã nhận tiền. API `bank-webhook-simulate` là giả lập, không phải thông báo từ ngân hàng thật.
- PayOS có mã tích hợp tạo link và kiểm tra chữ ký webhook; sơ đồ không khẳng định môi trường hiện tại đã cấu hình và vận hành giao dịch thật.
- `createDemoReservation()` tạo ghi danh và hóa đơn có hạn thanh toán sau một tháng. Không thể hiện tự hủy sau 24 giờ vì implementation hiện tại không làm việc đó.
- Cập nhật yêu cầu ghi danh sang `APPROVED` nằm trong `try/catch`; nếu thao tác này lỗi, code ghi log và tiếp tục.
- `/api/tuition-payment/**` hiện được `permitAll()` trong cấu hình bảo mật. Vai trò thu ngân/admin ở đây mô tả người sử dụng nghiệp vụ, không phải giới hạn truy cập đã được áp dụng đầy đủ.

Nguồn: `features/tuition_payment/service/impl/TuitionPaymentServiceImpl.java`, `gateway/PayOSGateway.java`, `shared/config/WebSecurityConfig.java`.

## 8. Xin nghỉ, duyệt nghỉ, xếp học bù và điểm danh

```mermaid
flowchart TB
    PH[Phụ huynh / người gửi đơn]
    GV[Giáo viên / nhân sự có quyền duyệt và điểm danh]
    NV[Nhân viên / quản lý / admin]
    P1(6.1 Tiếp nhận và tra cứu đơn nghỉ)
    P2(6.2 Xét duyệt đơn nghỉ)
    P3(6.3 Tra cứu, xếp hoặc hủy lịch bù)
    P4(6.4 Điểm danh / hoàn tất học bù)
    S[(students)]
    L[(lessons)]
    A[(absence_requests)]
    M[(makeup_registrations)]
    D[(attendances)]
    PH -->|studentId, lessonId, lý do; bộ lọc tra cứu| P1
    S -->|Học viên và quan hệ phụ huynh| P1
    L -->|Buổi học xin nghỉ| P1
    A -->|Đơn hiện có và trạng thái| P1
    P1 -->|Đơn PENDING| A
    P1 -->|Đơn và kết quả tra cứu| PH
    GV -->|APPROVED / EXCUSED / REJECTED, ghi chú| P2
    A -->|Đơn PENDING| P2
    P2 -->|Quyết định, người duyệt, thời gian| A
    P2 -->|Ca bù PENDING chỉ khi APPROVED| M
    P2 -->|Kết quả duyệt| GV
    NV -->|makeupId, buổi học đích hoặc lý do hủy| P3
    L -->|Buổi sắp tới, ưu tiên cùng khóa| P3
    M -->|Ca bù và buổi học gốc| P3
    P3 -->|SCHEDULED hoặc CANCELLED| M
    P3 -->|Danh sách buổi và kết quả xếp / hủy| NV
    GV -->|studentId, lessonId, trạng thái điểm danh hoặc makeupId| P4
    L -->|Buổi học được điểm danh| P4
    M -->|Ca bù liên quan| P4
    D -->|Bản ghi điểm danh hiện có| P4
    P4 -->|Bản ghi điểm danh cập nhật| D
    P4 -->|COMPLETED khi đủ điều kiện| M
    P4 -->|Kết quả điểm danh / hoàn tất| GV
    PH -->|Bộ lọc ca bù| P3
    P3 -->|Lịch và trạng thái ca bù được phép xem| PH
```

- Khi gửi đơn: kiểm tra học viên, buổi học, quan hệ con của phụ huynh và trùng đơn `PENDING`/`APPROVED`.
- Chỉ đơn `PENDING` được duyệt. `EXCUSED` và `REJECTED` cần ghi chú; chỉ `APPROVED` sinh ca bù.
- Danh sách buổi bù ưu tiên cùng khóa; nếu rỗng, code fallback sang các buổi sắp tới khác. Không diễn giải thành bảo đảm mọi ca đều cùng khóa hoặc đã kiểm tra đủ điều kiện sức chứa/trùng lịch.
- Xếp lịch không cho dùng chính buổi đã nghỉ, không xếp lại ca `COMPLETED`/`CANCELLED`.
- Điểm danh có thể hoàn tất ca bù liên quan; API hoàn tất học bù riêng còn ghi điểm danh `PRESENT`.

Nguồn: `features/attendance_makeup/service/impl/AbsenceMakeupServiceImpl.java`.

## 9. Đường đi kỹ thuật của dữ liệu

```mermaid
flowchart LR
    UI[Frontend com_fe]
    SEC(JWT filter và phân quyền theo endpoint)
    CT(Controller và validation DTO)
    SV(Service xử lý nghiệp vụ)
    RP(Repository JPA)
    DB[(SQL Server)]
    MP(Mapper / tạo Response DTO)
    UI -->|HTTP, JSON hoặc multipart, Bearer token nếu có| SEC
    SEC -->|Request và danh tính| CT
    CT -->|Request DTO / tham số| SV
    SV -->|Truy vấn / entity cần lưu| RP
    RP -->|SQL| DB
    DB -->|Dữ liệu| RP
    RP -->|Entity| SV
    SV -->|Kết quả nghiệp vụ| MP
    MP -->|Response DTO| CT
    CT -->|HTTP response / JSON| UI
```

Đây là sơ đồ kỹ thuật bổ sung, không thay thế DFD nghiệp vụ. Không phải endpoint nào cũng yêu cầu JWT; service có thể tự tạo DTO thay vì dùng mapper riêng. Tài liệu được kiểm tra bằng đọc mã nguồn, chưa xác nhận qua chạy toàn bộ ứng dụng và giao dịch PayOS.
