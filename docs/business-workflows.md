# Luồng nghiệp vụ xuyên suốt theo người thực hiện

Tài liệu này thay cách chia theo tính năng trong `data-flow.md` bằng hành trình nghiệp vụ từ đầu đến cuối. Mỗi cột là một người tham gia; đọc từ trên xuống, mũi tên thể hiện bàn giao công việc/thông tin. Cột hệ thống chỉ thể hiện xử lý tự động.

Phạm vi dựa trên code hiện tại. Các tương tác ngoài phần mềm như tham gia test, giao tiền, tham gia lớp là ngữ cảnh nghiệp vụ; không hàm ý project có API hay thông báo tự động cho mọi tương tác đó. Giả định tài khoản, hồ sơ học viên, khóa học và lớp đã có sẵn.

## 1. Từ nhu cầu học đến ghi danh chính thức

Đăng ký test, chọn khóa, xét duyệt, xếp lớp và thanh toán là các bước của cùng một luồng tuyển sinh — ghi danh.

```mermaid
sequenceDiagram
    autonumber
    actor PH as Phụ huynh / học viên
    actor NV as Nhân viên tuyển sinh
    actor GV as Giáo viên
    actor TN as Thu ngân
    participant HT as Hệ thống
    participant PAY as PayOS

    PH->>HT: Chọn học viên, gửi nhu cầu đăng ký học
    alt Cần kiểm tra đầu vào và chưa có kết quả phù hợp
        HT-->>NV: Yêu cầu chờ kết quả test
        NV->>HT: Tạo lịch kiểm tra đầu vào
        GV->>HT: Xem lịch kiểm tra
        PH->>GV: Học viên tham gia kiểm tra theo lịch
        GV->>HT: Lưu điểm, nhận xét, trình độ đề xuất, minh chứng
        Note over NV,HT: Chưa nối dữ liệu chấm test với kết quả dùng để xếp lớp trong code
        NV->>HT: Cập nhật kết quả test cho yêu cầu đăng ký
        Note over NV,HT: Chỉ tiếp tục khi nguồn kết quả xếp lớp có khóa học đề xuất
    else Đã có kết quả test hoặc không yêu cầu test
        PH->>HT: Chọn khóa mong muốn hoặc xem lớp được gợi ý
    end

    NV->>HT: Xem yêu cầu, chọn lớp và xét duyệt
    HT->>HT: Kiểm tra khóa, lớp mở, chỗ trống và ghi danh trùng
    alt Yêu cầu bị từ chối
        NV->>HT: Ghi quyết định từ chối và ghi chú
        PH->>HT: Tra cứu yêu cầu
        HT-->>PH: Kết quả từ chối — kết thúc yêu cầu
    else Yêu cầu được chấp thuận và lớp hợp lệ
        HT->>HT: Tạo ghi danh chờ thanh toán và hóa đơn học phí
        TN->>HT: Tra cứu hóa đơn, áp dụng miễn giảm nếu có
        HT-->>TN: Số tiền cần thu
        TN-->>PH: Cung cấp thông tin học phí và cách thanh toán
        alt Thanh toán tiền mặt
            PH->>TN: Nộp tiền
            TN->>HT: Ghi nhận tiền khách đưa và xác nhận thu
        else Thanh toán qua PayOS
            TN->>HT: Yêu cầu tạo link / QR thanh toán
            HT->>PAY: Tạo giao dịch thanh toán
            PAY-->>HT: Link / QR
            HT-->>TN: Link / QR để cung cấp cho phụ huynh
            TN-->>PH: Link / QR thanh toán
            PH->>PAY: Thực hiện thanh toán
            PAY->>HT: Gửi webhook giao dịch
            HT->>HT: Kiểm tra chữ ký và đối chiếu hóa đơn
        end
        alt Chưa có thanh toán hợp lệ
            HT-->>TN: Chưa hoàn tất thu tiền
            Note over PH,HT: Yêu cầu tiếp tục chờ thanh toán, chưa ghi danh chính thức
        else Thanh toán được ghi nhận
            HT->>HT: Lưu giao dịch, đánh dấu hóa đơn đã trả
            HT->>HT: Ghi danh chính thức, cập nhật yêu cầu và tăng sĩ số
            HT-->>TN: Biên lai và tiền thừa nếu có
            TN-->>PH: Giao biên lai / xác nhận đã thu
            PH->>HT: Tra cứu kết quả đăng ký
            HT-->>PH: Đã được ghi danh vào lớp
            Note over PH,GV: Học viên bắt đầu học tại lớp đã đăng ký
        end
    end
```

Điểm bắt đầu: phụ huynh có nhu cầu cho học viên theo học. Kết quả hoàn thành: học viên được ghi danh chính thức sau thanh toán; nhánh từ chối kết thúc yêu cầu, nhánh chưa thanh toán vẫn đang chờ.

### Giới hạn triển khai cần phân biệt với hành trình nghiệp vụ

- Phụ huynh gửi nhu cầu test trong yêu cầu đăng ký học; nhân viên tạo lịch test bằng thao tác riêng. Gửi yêu cầu không tự sinh lịch test.
- Giáo viên chấm và lưu vào `placement_schedules`, còn chức năng cập nhật kết quả cho ghi danh đọc `placement_tests`. Hiện chưa có đồng bộ giữa hai nguồn; bước tiếp tục xếp lớp chỉ chạy được khi nguồn thứ hai có kết quả phù hợp. Không có thao tác thủ công trên UI được xác nhận để nối hai nguồn này.
- Sơ đồ mô tả đường thanh toán tiền mặt và tích hợp PayOS. Webhook ngân hàng mô phỏng là công cụ demo, không phải một người hay một bước nghiệp vụ thật.
- Các bước cung cấp lịch, học phí, QR và biên lai giữa nhân viên với phụ huynh là bàn giao nghiệp vụ; không khẳng định có email/SMS tự động.
- Ngoài luồng duyệt yêu cầu, API tạo phiếu trực tiếp có thể tạo ghi danh chờ thanh toán và hóa đơn rồi nhập vào cùng đoạn thu tiền. Đây là lối vào khác của cùng luồng ghi danh, không phải nghiệp vụ thanh toán độc lập.
- Khi lớp không hợp lệ, hệ thống báo lỗi; nhân viên cần chọn lại lớp hợp lệ để tiếp tục. Code không có luồng danh sách chờ tự động.

## 2. Từ nhu cầu nghỉ một buổi đến giải quyết việc học bù

Điều kiện bắt đầu: đã có học viên và buổi học. Gửi đơn, duyệt nghỉ, xếp ca bù và điểm danh nối thành một luồng xử lý việc vắng học.

```mermaid
sequenceDiagram
    autonumber
    actor PH as Phụ huynh / học viên
    actor GV as Giáo viên
    actor NV as Nhân viên điều phối
    participant HT as Hệ thống

    PH->>HT: Chọn học viên, buổi cần nghỉ và gửi lý do
    HT->>HT: Kiểm tra học viên, buổi học, quyền và đơn trùng
    HT-->>PH: Đơn đã gửi, chờ duyệt
    GV->>HT: Xem đơn nghỉ và lý do
    GV->>HT: Gửi quyết định xét duyệt
    alt Từ chối đơn nghỉ
        HT->>HT: Lưu trạng thái từ chối và ghi chú
        PH->>HT: Tra cứu kết quả
        HT-->>PH: Đơn bị từ chối, không sinh ca bù
    else Chấp nhận nghỉ và miễn học bù
        HT->>HT: Lưu quyết định miễn bù và lý do
        PH->>HT: Tra cứu kết quả
        HT-->>PH: Được nghỉ, không cần học bù — kết thúc
    else Chấp nhận nghỉ và cần học bù
        HT->>HT: Duyệt đơn và tạo ca bù chờ xếp lịch
        NV->>HT: Xem ca bù cần xử lý và buổi học có thể chọn
        HT-->>NV: Danh sách buổi học bù gợi ý
        NV->>HT: Chọn buổi và xác nhận xếp lịch bù
        HT->>HT: Lưu lịch bù
        PH->>HT: Xem lịch học bù
        HT-->>PH: Buổi học bù đã được xếp
        alt Ca bù bị hủy
            NV->>HT: Hủy ca bù, nhập lý do
            HT->>HT: Lưu trạng thái hủy
            PH->>HT: Tra cứu ca bù
            HT-->>PH: Ca bù đã hủy — kết thúc ca này
        else Học viên tham gia buổi bù
            PH->>GV: Học viên tham gia buổi học bù
            GV->>HT: Điểm danh có mặt
            HT->>HT: Lưu điểm danh, đánh dấu ca bù hoàn thành
            PH->>HT: Tra cứu kết quả học bù
            HT-->>PH: Đã hoàn thành học bù — kết thúc
        end
    end
```

- Vai trò trên sơ đồ là phân công nghiệp vụ điển hình. Code cũng cho một số vai trò nhân sự khác duyệt hoặc điểm danh; không có nghĩa duy nhất giáo viên được làm.
- Điểm danh `PRESENT` tự hoàn tất ca bù đang `SCHEDULED` khớp học viên/buổi học. Có thao tác hoàn tất riêng, đồng thời tạo/cập nhật điểm danh có mặt.
- Nếu học viên chưa được điểm danh có mặt, không suy ra ca bù đã hoàn thành. Project chưa thể hiện luồng tự động xử lý bỏ buổi bù tiếp theo.
- Ca bị hủy kết thúc ca đó; chưa có luồng tự sinh ca thay thế được xác nhận.

## 3. Nghiệp vụ hỗ trợ vận hành

Admin tạo cơ sở, phòng và thiết bị để duy trì danh mục phục vụ vận hành. Admin/người xem báo cáo tra cứu lịch sử thu tiền theo thời gian và thu ngân sau khi các giao dịch được ghi nhận. Đây là công việc hỗ trợ hoặc kiểm soát kết quả của vận hành; không cần tách đăng ký test, ghi danh, thu tiền thành ba hành trình độc lập.

Code hiện chưa có chuỗi hoàn chỉnh từ tạo cơ sở đến tự động lập lớp/lịch học, cũng chưa có hành trình kết thúc khóa hoặc hoàn tiền được xác nhận. Vì vậy không bổ sung các bước này vào hai luồng chính.

## Đối chiếu mã nguồn

Các đường dẫn dưới đây tương đối với `com_be/src/main/java/com/talent/management/`:

- `features/course_enrollment/service/CourseEnrollmentService.java`: yêu cầu đăng ký, khuyến nghị, quyết định, ghi danh chờ thanh toán và hóa đơn.
- `features/placement_test/service/impl/PlacementTestServiceImpl.java`: lịch test, đánh giá và nguồn kết quả đề xuất.
- `features/tuition_payment/service/impl/TuitionPaymentServiceImpl.java`: miễn giảm, thu tiền, webhook, ghi danh chính thức và báo cáo.
- `features/attendance_makeup/service/impl/AbsenceMakeupServiceImpl.java`: đơn nghỉ, quyết định, ca bù, xếp/hủy và điểm danh.

Sơ đồ được đối chiếu mã nguồn; chưa chạy kiểm thử giao dịch thực tế.
