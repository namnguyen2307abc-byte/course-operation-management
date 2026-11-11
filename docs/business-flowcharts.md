# Flowchart nghiệp vụ xuyên suốt

Mỗi ô ghi người thực hiện và công việc. Hình thoi là điểm quyết định; đọc theo mũi tên từ đầu đến cuối. Các bước giao tiếp, tham gia test và tham gia học là ngữ cảnh nghiệp vụ, không hàm ý có thông báo tự động trong phần mềm.

## 1. Từ nhu cầu học đến ghi danh chính thức

```mermaid
flowchart TD
    A([Bắt đầu: có nhu cầu học]) --> B[Phụ huynh: chọn học viên và gửi yêu cầu đăng ký]
    B --> C{Cần test và chưa có kết quả phù hợp?}
    C -->|Có| D[Nhân viên: tạo lịch test]
    D --> E[Học viên: tham gia test]
    E --> F[Giáo viên: chấm điểm, nhận xét và đề xuất trình độ]
    F --> G[Nhân viên: cập nhật kết quả cho yêu cầu đăng ký]
    G --> H{Có kết quả dùng để xếp lớp?}
    H -->|Chưa| W([Chờ kết quả hợp lệ để tiếp tục])
    H -->|Có| J[Nhân viên: xem yêu cầu và xét duyệt]
    C -->|Không| I[Phụ huynh: chọn khóa mong muốn hoặc dùng kết quả đã có]
    I --> J
    J --> K{Chấp thuận yêu cầu?}
    K -->|Không| L[Nhân viên: ghi lý do từ chối]
    L --> M([Kết thúc: yêu cầu bị từ chối])
    K -->|Có| N[Nhân viên: chọn lớp phù hợp]
    N --> O{Lớp mở, còn chỗ, đúng khóa và không ghi danh trùng?}
    O -->|Không| N
    O -->|Có| P[Hệ thống: tạo ghi danh chờ thanh toán và hóa đơn]
    P --> Q[Thu ngân: tra cứu học phí, áp dụng miễn giảm nếu có]
    Q --> R[Thu ngân: cung cấp số tiền và cách thanh toán]
    R --> S{Phương thức thanh toán?}
    S -->|Tiền mặt| T[Phụ huynh: nộp tiền cho thu ngân]
    T --> U[Thu ngân: ghi nhận và xác nhận thu tiền]
    S -->|PayOS| V[Thu ngân: tạo và cung cấp link hoặc QR]
    V --> X[Phụ huynh: thực hiện thanh toán]
    X --> Y[PayOS: gửi webhook giao dịch]
    Y --> Z[Hệ thống: kiểm tra chữ ký và đối chiếu hóa đơn]
    U --> AA{Thanh toán được ghi nhận?}
    Z --> AA
    AA -->|Chưa| AB([Tiếp tục chờ thanh toán])
    AA -->|Có| AC[Hệ thống: lưu giao dịch và đánh dấu hóa đơn đã trả]
    AC --> AD[Hệ thống: ghi danh chính thức và tăng sĩ số lớp]
    AD --> AE[Thu ngân: giao biên lai hoặc xác nhận thu tiền]
    AE --> AF[Phụ huynh: xem kết quả đăng ký lớp]
    AF --> AG([Kết thúc: học viên được ghi danh vào lớp])
```

Lưu ý triển khai: bước chấm test lưu vào `placement_schedules`, còn cập nhật kết quả ghi danh đọc `placement_tests`; code hiện chưa đồng bộ hai nguồn. Do đó nhánh chờ kết quả là điểm dừng thực tế nếu nguồn xếp lớp chưa có kết quả. Việc gửi yêu cầu có test không tự tạo lịch test. Trạng thái chờ không phải kết thúc thành công; tiếp tục khi có kết quả hoặc thanh toán hợp lệ. Sơ đồ thể hiện phân công nghiệp vụ điển hình, không phải bảng phân quyền API.

## 2. Từ xin nghỉ đến giải quyết học bù

```mermaid
flowchart TD
    A([Bắt đầu: học viên cần nghỉ một buổi]) --> B[Phụ huynh: chọn buổi học và gửi lý do nghỉ]
    B --> C{Thông tin hợp lệ và không trùng đơn?}
    C -->|Không| B
    C -->|Có| D[Hệ thống: lưu đơn chờ duyệt]
    D --> E[Giáo viên: xem lý do và xét duyệt]
    E --> F{Quyết định?}
    F -->|Từ chối| G[Giáo viên: nhập lý do từ chối]
    G --> H[Phụ huynh: xem kết quả từ chối]
    H --> I([Kết thúc: đơn bị từ chối, không tạo ca bù])
    F -->|Cho nghỉ, miễn bù| J[Giáo viên: ghi lý do miễn học bù]
    J --> K[Phụ huynh: xem kết quả miễn bù]
    K --> L([Kết thúc: được nghỉ, không cần học bù])
    F -->|Cho nghỉ, cần bù| M[Hệ thống: duyệt đơn và tạo ca bù chờ xếp lịch]
    M --> N[Nhân viên: xem ca bù và các buổi học gợi ý]
    N --> O[Nhân viên: chọn buổi và lưu lịch bù]
    O --> P[Phụ huynh: xem lịch học bù]
    P --> Q{Ca bù bị hủy?}
    Q -->|Có| R[Nhân viên: ghi lý do và xác nhận hủy]
    R --> S([Kết thúc ca bù: đã hủy])
    Q -->|Không| T[Học viên: tham gia buổi học bù]
    T --> U[Giáo viên: điểm danh]
    U --> V{Điểm danh có mặt?}
    V -->|Chưa| W([Ca bù chưa hoàn thành])
    V -->|Có| X[Hệ thống: lưu điểm danh và hoàn tất ca bù]
    X --> Y[Phụ huynh: xem kết quả học bù]
    Y --> Z([Kết thúc: đã hoàn thành học bù])
```

Project chưa thể hiện luồng tự động tạo ca thay thế sau khi hủy hoặc vắng buổi bù; vì vậy không tự nối các nhánh này về bước xếp lịch mới. Giáo viên là người xét duyệt và điểm danh điển hình; code còn cho phép các vai trò nhân sự khác thực hiện một số thao tác này.

Nguồn đối chiếu: các service trong `features/course_enrollment`, `features/placement_test`, `features/tuition_payment` và `features/attendance_makeup` thuộc backend. Đã đọc code, chưa kiểm thử hành trình bằng giao dịch thực tế.
