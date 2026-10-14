# Kế Hoạch Triển Khai: Thời Khóa Biểu Tuần & Điểm Danh Học Viên

## 1. Mục Tiêu & Yêu Cầu
- **Giao diện Thời khóa biểu tuần**: Bảng ma trận Slot x Ngày trong tuần (Thứ 2 đến Chủ nhật), điều hướng tuần (Tuần trước, Tuần hiện tại, Tuần sau, chọn tuần theo ngày).
- **Hiển thị theo Role**:
  - Học sinh/Phụ huynh: Thấy lịch học của mình, trạng thái điểm danh của bản thân (`Có mặt`, `Vắng mặt`, `Chưa diễn ra`).
  - Giáo viên: Thấy lịch dạy của mình, sĩ số và trạng thái điểm danh của ca (`Đã điểm danh`, `Chưa điểm danh`).
  - Admin/Staff: Thấy toàn bộ lịch, có bộ lọc theo cơ sở, giáo viên, lớp học.
- **Modal Điểm danh buổi học (Lesson Attendance)**:
  - Danh sách học viên: STT, Ảnh, MSV, Tên, nút chọn `Có mặt (Present)` / `Vắng mặt (Absent)`, ô nhập lý do.
  - Nút `Lưu Điểm Danh` -> Chuyển sang chế độ đã lưu -> Nút `Chỉnh Sửa`.
- **Cơ chế Khóa Quyền Thời Gian (Time-Lock Guard)**:
  - Giáo viên chỉ được điểm danh và sửa điểm danh trong **đúng ngày diễn ra ca học** (`lessonDate == today`).
  - Qua ngày hôm sau (`lessonDate < today`): **Giáo viên bị khóa toàn bộ quyền sửa**, chỉ **Admin / Staff** mới được phép can thiệp.
- **Hỗ trợ Tạo Lớp & Tự Động Sinh Buổi Học (`Class & Lesson Generator`)**:
  - Cho phép Admin/Staff tạo lớp mới (chọn thứ trong tuần, khung giờ, ngày khai giảng, số buổi) -> tự động sinh các `lessons` vào CSDL và hiện lên thời khóa biểu tuần.
