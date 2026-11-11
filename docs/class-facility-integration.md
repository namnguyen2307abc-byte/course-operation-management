# Tích hợp quản lý lớp và cơ sở vật chất

Ngày kiểm tra: 29/10/2026. Đã đối chiếu đầy đủ tài liệu hai trang do thành viên gửi với commit `47daec0` trên `class-management`, sau đó tích hợp với chức năng quản trị trên main.

## Đối chiếu nghiệp vụ

| Nhóm yêu cầu đã đối chiếu | Code chịu trách nhiệm | Kiểm thử |
| --- | --- | --- |
| Giáo viên hoạt động, chuyên môn đúng môn; phòng đúng cơ sở, sức chứa, loại phòng và thiết bị | `ClassSchedulingService`, `FacilityRulesService`, các API teachers/rooms của `ClassManagementController` | `ClassSchedulingServiceTest`, `FacilityRulesServiceTest` |
| Kiểm tra từng buổi, giáo viên nghỉ ít nhất 30 phút, phòng không trùng giờ; sinh số buổi và tính ngày kết thúc | `ClassSchedulingService`, `ManagedClassLessonRepository` | Unit test và `ClassManagementPersistenceTest`, gồm ca đúng 30 phút, qua nửa đêm, lịch hủy và lịch dùng phòng/giáo viên mặc định |
| Lớp OPEN/IN_PROGRESS có đủ phân công/lịch; không sửa trực tiếp ngày/khung lịch đã sinh | `ClassManagementService`, API tạo lớp từ lịch tuần dùng cùng service xếp lịch | `ClassManagementServiceTest`, `ScheduleWeeklyFlowTest` |
| Đổi phân công chỉ áp dụng buổi tương lai đủ điều kiện; giữ lịch sử và phòng riêng khi chỉ đổi giáo viên | `ClassManagementService.updateLessonAssignments` | Unit test, kiểm tra lịch đã hoàn thành, đang diễn ra và phòng được phân riêng |
| Sĩ số tính cả chỗ đang chờ thanh toán; kiểm tra phòng mặc định và phòng riêng; khóa đổi khóa học/cơ sở khi đã có liên kết | `ClassManagementService`, các repository ghi danh/lớp/buổi học | Unit test và kiểm thử tích hợp ghi danh |
| Chỉ admin sửa phòng/thiết bị; ADMIN/STAFF/BRANCH_MANAGER/TEACHER xem lịch sử dụng; không thêm giao diện tạo mới | `BranchFacilityController`, `branch-facility.js` | Controller test và kiểm thử trình duyệt theo role |
| Phòng có thiết bị, lớp sử dụng, buổi hiện tại/tương lai, cảnh báo và liên kết sang quản lý lớp | `BranchFacilityServiceImpl`, `FacilityClassRepository`, `FacilityLessonRepository` | `FacilityUsagePersistenceTest`, kiểm thử giao diện |
| Không giảm sức chứa, đổi loại phòng hoặc bảo trì làm vô hiệu phân công còn sử dụng | `BranchFacilityServiceImpl.updateRoom` | `BranchFacilityUpdateTest`, `FacilityUsagePersistenceTest` |
| Thiết bị hỏng vẫn lưu đúng tình trạng, cảnh báo ảnh hưởng, không tự sửa/hủy lịch | `BranchFacilityServiceImpl.updateEquipment` | Kiểm thử service, persistence và giao diện giữ bản nháp khi có lỗi |
| Piano đủ đàn tốt theo sĩ số tối đa; cờ vua đủ bộ theo cặp làm tròn lên; bơi cần bể bơi và cứu hộ tốt; guitar không bắt buộc đủ đàn | `FacilityRulesService` | Kiểm thử tham số hóa về loại phòng, số lượng, thiết bị hỏng/bảo trì và trạng thái cơ sở |

## Các điểm đã sửa khi ghép nhánh

- Xử lý conflict ở năm bản sao trang cơ sở vật chất: giữ nội dung/chức năng mới của nhánh và sidebar tĩnh từ main; đồng bộ lại từ `com_fe`.
- Bỏ việc tải/dựng lại navbar bằng fetch trong trang cơ sở vật chất để giữ sidebar và vị trí cuộn ổn định.
- Modal toàn màn hình trên điện thoại dùng chiều rộng vùng chứa thay vì `100vw`, tránh bị cuộn ngang 15px khi Bootstrap thay đổi padding cho thanh cuộn. Kiểm tra lại cả màn hình 390px và 1440px.
- Nối `teachingSubject` vào DTO/mapper/service/form quản trị tài khoản. `subject` vẫn là bộ môn dùng cho đánh giá đầu vào; chuyên môn cụ thể dùng cho phân công lớp. Client cũ không gửi trường mới thì giá trị hiện có được giữ nguyên. Thay đổi có nhật ký và kiểm thử qua API thật trong H2.

## Database

Database hiện có được sao lưu bằng SQL Server `COPY_ONLY` và xác minh bằng `RESTORE VERIFYONLY` trước khi chạy:

1. `database/class_management_scheduling.sql`: thêm `teaching_subject`, chỉ bổ sung chuyên môn có thể xác định rõ, không đổi `subject`.
2. `database/facility_business_rules.sql`: cập nhật tập giá trị loại phòng/thiết bị và chuẩn hóa các phòng demo bơi có mã chỉ định. Không khởi tạo lại database hoặc xóa lịch sử.

Các file `database/test_*.sql` của nhánh là dữ liệu mẫu tùy chọn, không tự động chạy khi tích hợp lên database đang dùng.

## Kiểm thử

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-backend.ps1
node --test scripts/frontend-integration.test.cjs
node --test scripts/class-scheduling-ui.test.cjs scripts/facility-ui.test.cjs
node scripts/sync-frontend.mjs --check
```

Các bài kiểm thử trình duyệt cần Node.js, module `playwright`, Chrome và frontend phục vụ từ `com_fe` trên cổng 5500 cho bài quản lý lớp; bài cơ sở vật chất tự mở HTTP server riêng. API được giả lập hoàn toàn, không ghi dữ liệu lớp/phòng thật. Có thể đặt `CLASS_TEST_URL`, `PLAYWRIGHT_CHANNEL` và đường dẫn ảnh qua `CLASS_SCREENSHOT_DIR`/`FACILITY_SCREENSHOT_DIR` theo môi trường.

Kết quả sau tích hợp: 188 bài backend, 9 bài frontend đơn vị; bộ kiểm thử giao diện bao gồm lọc phân công, tính ngày kết thúc, lỗi xếp lịch, phân quyền phòng/thiết bị, giữ bản nháp khi conflict, cảnh báo thiết bị hỏng, retry, response cũ và chống lưu trùng. Các file frontend đồng bộ không còn khác biệt.
