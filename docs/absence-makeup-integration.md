# Tích hợp nhánh attendance_makeup

- Bỏ quyết định miễn học bù và thao tác chuyển kỳ sau cho hồ sơ mới. Enum cũ vẫn giữ để đọc lịch sử, không tạo mới các trạng thái đó.
- Phụ huynh/học viên chỉ hủy đơn của mình khi PENDING. Giáo vụ/quản lý có thể hủy đơn chờ; giáo viên chỉ xử lý đơn của lớp/buổi mình phụ trách. Đơn đã duyệt/từ chối không được hủy bằng thao tác này.
- Thao tác hủy đơn PENDING xóa đơn theo thiết kế của nhánh nguồn; thông báo hủy còn trong lịch sử thông báo. Hủy và xét duyệt cùng khóa bản ghi để tránh duyệt một đơn đang bị xóa.
- Giáo viên phụ trách, nhân viên, admin và quản lý được duyệt; phụ huynh/học viên không được duyệt. Đơn được duyệt tạo hồ sơ học bù chờ xếp lịch; từ chối phải có lý do.
- Danh sách ưu tiên PENDING, sau đó mới nhất trước. Chuông thông báo dẫn tới đúng tab và chi tiết đơn/ca bù; trường hợp đã hủy quay về danh sách.
- Sĩ số từng buổi lấy danh sách đã ENROLLED, trừ học viên chuyển đi và cộng học viên học bù đến. PENDING_PAYMENT chưa được đưa vào danh sách điểm danh.
- Giữ nghiệp vụ mới của nhánh nguồn: buổi quá khứ chưa điểm danh hiển thị vắng với ghi chú “Quá khứ chưa điểm danh”; đây là trạng thái hiển thị, không tự tạo bản ghi điểm danh. Giáo vụ/quản lý có thể cập nhật khi cần xác minh.
- Lịch tuần tiếp tục dùng `ClassSchedulingService` chung; điểm danh ủy quyền sang `AttendanceTrackingService`, không nhân đôi phần xử lý trong lịch tuần.

Không cần thay đổi schema mới ngoài migration thông báo đã có trên main. Không chạy lại file seed lên dữ liệu đang dùng nếu không muốn nạp thêm dữ liệu thử.

Kiểm thử: `scripts/test-backend.ps1` dùng H2 riêng; `scripts/schedule-attendance-ui.test.cjs` có kịch bản deep link, hủy đơn từ phụ huynh trên mobile và quyền duyệt của nhân viên. Các bộ hồi quy học phí, lớp học và cơ sở tiếp tục được chạy khi tích hợp.
