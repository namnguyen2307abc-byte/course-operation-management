# Course Operation Management (COM)

> **Hệ Thống Quản Lý Vận Hành Học Viện Âm Nhạc & Nghệ Thuật (Talent Academy)**  
> | Phiên bản kiến trúc: **Spring Boot 3 (Java 17) & Frontend Web Modular**

---

## 1. Tổng Quan Kiến Trúc Dự Án (Architecture Overview)

Dự án được phân tách độc lập thành 2 phần rõ ràng giữa **Backend API (`com_be`)** và **Frontend Web (`com_fe`)**, sử dụng cơ sở dữ liệu **Microsoft SQL Server (`database`)**:

```text
course-operation-management/
├── com_be/                           # BACKEND: Spring Boot 3 REST API (Java 17, Maven)
│   ├── src/main/java/com/talent/management/
│   │   ├── features/                 # Các phân hệ nghiệp vụ chính (Mỗi feature chuẩn 6 tầng)
│   │   │   ├── branch_facility_enrollment/  # [FEATURE MẪU] Quản lý cơ sở, phòng học & nhạc cụ
│   │   │   ├── attendance_makeup/           # Điểm danh, đơn xin nghỉ học & học bù
│   │   │   ├── placement_test/              # Kiểm tra năng khiếu đầu vào & lộ trình học
│   │   │   ├── tuition_payment/             # Học phí, miễn giảm & thanh toán VietQR
│   │   │   └── auth/                        # Xác thực JWT & phân quyền người dùng
│   │   └── shared/                   # Thành phần dùng chung (Entity, Enum, Common Exception)
│   └── pom.xml                       # Quản lý thư viện Maven (Java 17, Spring Boot 3.3.4)
├── com_fe/                           # FRONTEND: Giao diện người dùng Web (HTML5/CSS3/Vanilla JS)
│   ├── index.html                    # Bảng điều khiển trung tâm (Dashboard)
│   ├── login.html                    # Trang đăng nhập xác thực
│   ├── css/                          # Hệ thống stylesheet hiện đại (style.css)
│   ├── js/                           # Tiện ích gọi API (api.js) & xác thực Token (auth.js)
│   ├── pages/                        # Các trang nghiệp vụ (branch-facility.html,...)
│   └── components/                   # Web Components dùng chung (navbar.html, footer.html)
└── database/                         # DATABASE: Script CSDL SQL Server
    └── init_database.sql             # DDL tạo bảng & dữ liệu mẫu (Seed Data)
```

---

## 2. Tiêu Chuẩn Cấu Trúc 6 Tầng Của Mỗi Phân Hệ (`features/<name>`)

Mỗi phân hệ nghiệp vụ trong `com_be/src/main/java/.../features/` bắt buộc phải tuân thủ đúng cấu trúc chuẩn 6 gói (package) sau:

```text
features/<feature_name>/
├── controller/      # Tầng tiếp nhận HTTP Request & trả về Response JSON (@RestController)
├── service/         # Tầng xử lý nghiệp vụ logic, quản lý giao dịch (@Service, @Transactional)
├── mapper/          # Tầng chuyển đổi dữ liệu hai chiều Entity <-> DTO an toàn (@Component)
├── repository/      # Tầng truy vấn dữ liệu Spring Data JPA (@Repository)
├── dto/             # Đối tượng truyền nhận dữ liệu giữa Client và Server (Request/Response DTO)
└── exception/       # Các ngoại lệ đặc thù của phân hệ (Custom Business Exceptions)
```

### 🎯 Vai Trò & Lợi Ích Của Tầng `mapper`
1. **Tách rời Entity khỏi Presentation Layer**: Ngăn không cho tầng View/Client can thiệp trực tiếp vào JPA Entity, bảo vệ cấu trúc CSDL và các thông tin nhạy cảm.
2. **Loại bỏ Boilerplate Code trong Service**: Giúp Service chỉ tập trung 100% vào logic nghiệp vụ và quy tắc vận hành, không bị phân tán bởi hàng chục dòng lệnh gán setter/getter thủ công.
3. **Kiểm soát Null-Safe & Lazy Loading**: Chủ động kiểm tra `null` trước khi truy xuất các trường liên kết (`room.getBranch() != null`), triệt tiêu hoàn toàn lỗi `NullPointerException` hoặc `LazyInitializationException`.
4. **Tối ưu hóa Unit Test**: Dễ dàng viết kiểm thử độc lập cho Service (mock Mapper) và viết test riêng cho Mapper để xác thực tính toàn vẹn của dữ liệu chuyển đổi.

---

## 3. Luồng Dữ Liệu Xử Lý Nghiệp Vụ Chuẩn (End-to-End Data Flow)

```text
┌─────────────────────────────────────────────────────────────┐
│                 Client (Web com_fe / Postman)               │
└──────────────────────────────┬──────────────────────────────┘
                               │ 1. Gửi HTTP Request (GET/POST/PUT...)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│       Controller (@RestController, @RequestMapping)         │
│  - Nhận tham số URL / Request Body                          │
│  - Validate dữ liệu đầu vào cơ bản                          │
└──────────────────────────────┬──────────────────────────────┘
                               │ 2. Gọi phương thức nghiệp vụ
                               ▼
┌─────────────────────────────────────────────────────────────┐
│             Service Layer (@Service, @Transactional)        │
│  - Thực thi nghiệp vụ (Kiểm tra quyền, trạng thái, ngày giờ)│
│  - Không viết code gán DTO thủ công tại đây                 │
└──────────────┬──────────────────────────────▲───────────────┘
               │ 3. Gọi truy vấn dữ liệu       │ 5. Trả về Entities
               ▼                              │
┌─────────────────────────────────────────────┴───────────────┐
│            Repository (Spring Data JPA @Repository)         │
│  - Derived Query Method (findByBranchId)                    │
│  - JPQL (@Query với JOIN nhiều bảng)                        │
└──────────────────────────────┬──────────────────────────────┘
                               │ 4. Truy vấn CSDL SQL Server
                               ▼
                ┌──────────────────────────────┐
                │   Database: SQL Server       │
                └──────────────────────────────┘
                               │
               ┌───────────────┴──────────────┐
               │ 6. Service gọi Mapper        │
               ▼                              │
┌─────────────────────────────────────────────┴───────────────┐
│             Mapper Component (@Component)                   │
│  - Chuyển đổi Entity sang DTO an toàn (Null-safe)           │
│  - Đóng gói dữ liệu hiển thị cần thiết cho màn hình         │
└──────────────────────────────┬──────────────────────────────┘
                               │ 7. Trả về DTO
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Controller: Trả về Response                 │
│  - ResponseEntity.ok(dtos)                                  │
└──────────────────────────────┬──────────────────────────────┘
                               │ 8. HTTP Response JSON
                               ▼
┌─────────────────────────────────────────────────────────────┐
│           Client Frontend: Render UI lên trình duyệt        │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Minh Họa Triển Khai Thực Tế: Phân Hệ Cơ Sở & CSVC

Phân hệ `branch_facility_enrollment` đã được xây dựng hoàn chỉnh làm **chuẩn mẫu (Template)** cho toàn đội ngũ:

### 1. Spring Data JPA Derived Query Method (Tìm kiếm trực tiếp)
- **Tình huống áp dụng**: Lấy danh sách phòng học theo cơ sở. Bảng `rooms` có khóa ngoại trực tiếp `branch_id`.
- **Mã nguồn Repository**:
  ```java
  public interface RoomRepository extends JpaRepository<Room, Long> {
      // Spring Data JPA tự động phân tích tên hàm để sinh SQL
      List<Room> findByBranchId(Long branchId);
  }
  ```

### 2. JPQL Query với `@Query` (Truy vấn nối nhiều bảng gián tiếp)
- **Tình huống áp dụng**: Lấy danh mục nhạc cụ/đàn Piano theo cơ sở. Bảng `equipments` không có cột `branch_id` mà phải thông qua bảng `rooms`.
- **Mã nguồn Repository**:
  ```java
  public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
      @Query("SELECT e FROM Equipment e JOIN e.room r WHERE r.branch.id = :branchId ORDER BY e.id ASC")
      List<Equipment> findEquipmentsByBranchId(@Param("branchId") Long branchId);
  }
  ```

### 3. Tầng Mapper (`BranchFacilityMapper.java`)
- Chuyển đổi an toàn từ Entity sang DTO:
  ```java
  @Component
  public class BranchFacilityMapper {
      public RoomDto toRoomDto(Room room) {
          if (room == null) return null;
          return RoomDto.builder()
                  .id(room.getId())
                  .branchId(room.getBranch() != null ? room.getBranch().getId() : null)
                  .branchName(room.getBranch() != null ? room.getBranch().getName() : null)
                  .roomCode(room.getRoomCode())
                  .roomName(room.getRoomName())
                  .capacity(room.getCapacity())
                  .roomType(room.getRoomType())
                  .status(room.getStatus())
                  .build();
      }
  }
  ```

### 4. Tầng Service (`BranchFacilityService.java`)
- Service sử dụng Mapper thông qua Dependency Injection gọn gàng:
  ```java
  @Service
  @RequiredArgsConstructor
  @Transactional(readOnly = true)
  public class BranchFacilityService {
      private final RoomRepository roomRepository;
      private final BranchFacilityMapper branchFacilityMapper;

      public List<RoomDto> getRoomsByBranch(Long branchId) {
          return roomRepository.findByBranchId(branchId).stream()
                  .map(branchFacilityMapper::toRoomDto)
                  .toList();
      }
  }
  ```

---

## 5. Hướng Dẫn Dành Cho Thành Viên Nhóm Phát Triển

Khi bạn nhận nhiệm vụ phát triển một trong các tính năng tiếp theo:
1. **`attendance_makeup`**: Điểm danh học viên, đơn xin nghỉ và duyệt đăng ký học bù.
2. **`placement_test`**: Phiếu đánh giá năng khiếu đầu vào, file ghi âm/video và lộ trình học.
3. **`tuition_payment`**: Lập hóa đơn học phí, áp dụng chính sách miễn giảm và thanh toán VietQR.

**Quy trình thực hiện bắt buộc**:
- **Bước 1**: Tạo các DTO Request/Response trong thư mục `dto/`.
- **Bước 2**: Viết phương thức truy vấn trong `repository/` (ưu tiên Derived Query Method cho truy vấn đơn giản và JPQL cho truy vấn phức tạp nhiều bảng).
- **Bước 3**: Viết logic ánh xạ trong `mapper/` (sử dụng class Mapper đã tạo sẵn trong feature, đảm bảo null-safe).
- **Bước 4**: Viết nghiệp vụ trong `service/`, gọi Repository và sử dụng Mapper để trả về DTO.
- **Bước 5**: Viết API Endpoint trong `controller/` để trả về dữ liệu cho Frontend.
- **Bước 6**: Mở file HTML tương ứng trong `com_fe/pages/` để kết nối API hiển thị dữ liệu thực tế.

---

## 6. Hướng Dẫn Khởi Chạy Dự Án

### Bước 1: Khởi Tạo Cơ Sở Dữ Liệu (SQL Server)
- Mở **SQL Server Management Studio (SSMS)**.
- Mở file: `database/init_database.sql`.
- Nhấn **Execute (F5)** để tự động tạo CSDL `course_operation_management` và toàn bộ dữ liệu mẫu (Seed Data).
- Kiểm tra cấu hình kết nối trong file: `com_be/src/main/resources/application.properties` (Mặc định: User `sa`, Password `sa`, Port `1433`).

### Bước 2: Khởi Chạy Backend (`com_be`) - Port 8080
- **Cách 1 (IntelliJ IDEA)**: Mở thư mục `com_be` trong IntelliJ, chạy file `TalentManagementApplication.java`.
- **Cách 2 (Terminal/Command Line)**:
  ```powershell
  cd com_be
  mvn spring-boot:run
  ```
- Tài liệu API trực quan (Swagger UI): [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Bước 3: Khởi Chạy Frontend (`com_fe`) - Port 5500
- **Cách 1 (Visual Studio Code)**: Mở thư mục `com_fe` trong VS Code, cài đặt extension **Live Server**, nhấn chuột phải vào `login.html` hoặc `index.html` và chọn **Open with Live Server**.
- Trình duyệt truy cập: [http://127.0.0.1:5500/](http://127.0.0.1:5500/)
- **Tài khoản đăng nhập kiểm thử (Mật khẩu chung: `123456`)**:
  - `admin` (Quản trị viên toàn hệ thống)
  - `teacher_huong` (Giáo viên Piano)
  - `parent_lan` (Phụ huynh học viên)
  - `cashier_mai` (Nhân viên thu ngân)
