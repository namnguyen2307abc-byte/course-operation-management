# Course Operation Management (COM)

> **Hệ Thống Quản Lý Vận Hành Học Viện Âm Nhạc & Nghệ Thuật (Talent Academy)**  
> | Phiên bản kiến trúc: **Spring Boot 3 (Java 17) & Frontend Web Modular**

---

## 📑 Mục Lục
1. [Tổng Quan Kiến Trúc Dự Án](#1-tổng-quan-kiến-trúc-dự-án-architecture-overview)
2. [Kiến Trúc Backend Chuẩn Enterprise (`com_be`)](#2-kiến-trúc-backend-chuẩn-enterprise-com_be)
3. [Luồng Dữ Liệu Xử Lý Nghiệp Vụ Chuẩn](#3-luồng-dữ-liệu-xử-lý-nghiệp-vụ-chuẩn-end-to-end-data-flow)
4. [Minh Họa Triển Khai Thực Tế: Phân Hệ Cơ Sở & CSVC](#4-minh-họa-triển-khai-thực-tế-phân-hệ-cơ-sở--csvc)
5. [Cấu Trúc & Cơ Chế Hoạt Động Cụ Thể Của Từng File Frontend (`com_fe`)](#5-cấu-trúc--cơ-chế-hoạt-động-cụ-thể-của-từng-file-frontend-com_fe)
6. [Nền Tảng Lý Thuyết Cốt Lõi: Spring Boot & RESTful API](#6-nền-tảng-lý-thuyết-cốt-lõi-spring-boot--restful-api-cho-người-mới)
7. [So Sánh Chuyên Sâu: Spring Boot REST API vs Java Web Truyền Thống (Servlet, JSP, JDBC)](#7-so-sánh-chuyên-sâu-spring-boot-rest-api-vs-java-web-truyền-thống-servlet-jsp-jdbc)
8. [Hướng Dẫn Dành Cho Thành Viên Nhóm Phát Triển](#8-hướng-dẫn-dành-cho-thành-viên-nhóm-phát-triển)
9. [Hướng Dẫn Khởi Chạy Dự Án](#9-hướng-dẫn-khởi-chạy-dự-án)

---

## 1. Tổng Quan Kiến Trúc Dự Án (Architecture Overview)

Dự án được thiết kế theo mô hình **Tách Rời Hoàn Toàn (Decoupled Architecture)** giữa **Backend API (`com_be`)** và **Frontend Web Client (`com_fe`)**, sử dụng hệ quản trị cơ sở dữ liệu quan hệ **Microsoft SQL Server (`database`)**:

```text
course-operation-management/
├── com_be/                           # BACKEND: Spring Boot 3 REST API (Java 17, Maven)
│   ├── src/main/java/com/talent/management/
│   │   ├── features/                 # Các phân hệ nghiệp vụ chính (Feature-driven / Vertical Slice)
│   │   │   ├── branch_facility_enrollment/  # [FEATURE MẪU HOÀN THIỆN] Cơ sở, phòng học, nhạc cụ
│   │   │   ├── attendance_makeup/           # [KHUNG SKELETON] Điểm danh, đơn nghỉ học & học bù
│   │   │   ├── placement_test/              # [KHUNG SKELETON] Test năng khiếu & lộ trình phát triển
│   │   │   ├── tuition_payment/             # [KHUNG SKELETON] Hóa đơn học phí & thanh toán VietQR
│   │   │   └── auth/                        # Xác thực JWT, quản lý tài khoản & phân quyền
│   │   └── shared/                   # Dữ liệu & tiện ích dùng chung (Entity, Enum, PageResponse, Exception)
│   └── pom.xml                       # Quản lý thư viện Maven (Java 17, Spring Boot 3.3.4)
├── com_fe/                           # FRONTEND: Giao diện Web Client Modular (HTML5/CSS3/Vanilla JS)
│   ├── index.html                    # Bảng điều khiển trung tâm (Dashboard)
│   ├── login.html                    # Trang đăng nhập xác thực
│   ├── css/                          # Design System stylesheet hiện đại (style.css)
│   ├── js/                           # Tiện ích gọi API (api.js) & xác thực Token (auth.js)
│   ├── pages/                        # Các trang nghiệp vụ chi tiết
│   └── components/                   # Web Components dùng chung (navbar.html, footer.html)
└── database/                         # DATABASE: Script CSDL SQL Server
    └── init_database.sql             # DDL tạo bảng & dữ liệu mẫu (Seed Data) chuẩn UTF-8 BOM
```

---

## 2. Kiến Trúc Backend Chuẩn Enterprise (`com_be`)

Mỗi phân hệ nghiệp vụ trong `com_be/src/main/java/.../features/` tuân thủ kiến trúc chuẩn Enterprise:

```text
features/<feature_name>/
├── controller/         # Tầng tiếp nhận HTTP Request & trả về Response JSON (@RestController)
├── dto/                # Phân tách 2 chiều dữ liệu rành mạch (CQRS mindset)
│   ├── request/        # Nhận dữ liệu từ Client với Bean Validation (@NotBlank, @NotNull, @Min, @Size...)
│   └── response/       # Trả dữ liệu hiển thị (Response đầy đủ, OptionResponse nhẹ cho Dropdown)
├── mapper/             # Tầng chuyển đổi dữ liệu hai chiều Entity <-> DTO an toàn (@Component)
├── repository/         # Tầng truy vấn Spring Data JPA (Derived Query Method & JPQL @Query)
├── service/            # Interface định nghĩa hợp đồng nghiệp vụ (Loose coupling)
│   └── impl/           # Cài đặt chi tiết nghiệp vụ (@Service, @Transactional, @Slf4j)
```

### 🎯 Ý Nghĩa Thiết Kế Của Từng Tầng:
1. **Controller Layer**: Chỉ làm nhiệm vụ tiếp nhận HTTP request, validate dữ liệu đầu vào qua `@Valid`, điều hướng sang Service và đóng gói kết quả trả về `ResponseEntity<T>`. Tuyệt đối không chứa logic nghiệp vụ hay câu lệnh SQL tại đây.
2. **DTO Layer (Request & Response tách biệt)**:
   - **`dto/request/`**: Nhận dữ liệu từ form/JSON người dùng gửi lên. Tích hợp kiểm tra ràng buộc dữ liệu (Bean Validation).
   - **`dto/response/`**: Chỉ chọn lọc những thông tin Frontend cần hiển thị, ẩn các trường nhạy cảm của CSDL (mật khẩu, cờ hệ thống), và cung cấp `OptionResponse` siêu nhẹ chỉ gồm `id`, `name` cho các dropdown `<select>`.
3. **Mapper Layer**: Chuyển đổi an toàn 2 chiều giữa JPA Entity và DTO. Xử lý các trường hợp liên kết quan hệ null-safe, ngăn ngừa hoàn toàn lỗi `NullPointerException` hoặc `LazyInitializationException`.
4. **Service Layer (Interface + Impl)**:
   - **Interface**: Định nghĩa bản hợp đồng nghiệp vụ (Contract). Giúp Controller không phụ thuộc vào code cài đặt cụ thể (Dependency Inversion Principle), tạo điều kiện viết Unit Test độc lập (Mocking).
   - **Service Impl**: Nằm trong package con `service/impl`, thực thi các quy tắc kiểm tra logic, phân quyền và quản lý giao dịch CSDL với `@Transactional`.
5. **Repository Layer**: Kế thừa `JpaRepository<Entity, ID>`, sử dụng phương thức truy vấn dựa trên tên hàm (**Derived Query Method**) hoặc truy vấn đối tượng **JPQL (`@Query`)**.

---

## 3. Luồng Dữ Liệu Xử Lý Nghiệp Vụ Chuẩn (End-to-End Data Flow)

```text
┌─────────────────────────────────────────────────────────────┐
│                 Client (Web com_fe / Postman)               │
└──────────────────────────────┬──────────────────────────────┘
                               │ 1. Gửi HTTP Request (GET/POST/PUT/DELETE)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│       Controller (@RestController, @RequestMapping)         │
│  - Nhận tham số URL / Request Body                          │
│  - Tự động parse JSON sang Request DTO & @Valid             │
└──────────────────────────────┬──────────────────────────────┘
                               │ 2. Gọi phương thức nghiệp vụ qua Service Interface
                               ▼
┌─────────────────────────────────────────────────────────────┐
│             Service Layer (Interface -> Impl)               │
│  - Thực thi nghiệp vụ logic, kiểm tra quyền hạn, điều kiện  │
│  - Quản lý giao dịch (@Transactional)                       │
└──────────────┬──────────────────────────────▲───────────────┘
               │ 3. Gọi truy vấn dữ liệu       │ 5. Trả về JPA Entities
               ▼                              │
┌─────────────────────────────────────────────┴───────────────┐
│            Repository (Spring Data JPA @Repository)         │
│  - Derived Query Method (findByBranchId)                    │
│  - JPQL (@Query với JOIN các bảng)                          │
└──────────────────────────────┬──────────────────────────────┘
                               │ 4. Thực thi SQL tới Database
                               ▼
                ┌──────────────────────────────┐
                │   Database: SQL Server       │
                └──────────────────────────────┘
                               │
               ┌───────────────┴──────────────┐
               │ 6. Service đưa Entity vào Mapper
               ▼                              │
┌─────────────────────────────────────────────┴───────────────┐
│             Mapper Component (@Component)                   │
│  - Chuyển đổi Entity sang Response DTO an toàn (Null-safe)  │
│  - Bọc dữ liệu hiển thị tối ưu cho UI                       │
└──────────────────────────────┬──────────────────────────────┘
                               │ 7. Trả về Response DTO
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Controller: Trả về Response                 │
│  - ResponseEntity.ok(responseDto)                           │
└──────────────────────────────┬──────────────────────────────┘
                               │ 8. Trả về HTTP Response JSON
                               ▼
┌─────────────────────────────────────────────────────────────┐
│           Client Frontend: Render UI lên trình duyệt        │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Minh Họa Triển Khai Thực Tế: Phân Hệ Cơ Sở & CSVC

Phân hệ `branch_facility_enrollment` đã được triển khai hoàn chỉnh làm **mẫu chuẩn (Reference Template)**:

### 1. Spring Data JPA Derived Query Method (Tìm kiếm trực tiếp)
- **Áp dụng**: Lấy danh sách phòng học theo cơ sở khi bảng `rooms` có khóa ngoại trực tiếp `branch_id`:
  ```java
  public interface RoomRepository extends JpaRepository<Room, Long> {
      List<Room> findByBranchId(Long branchId);
  }
  ```

### 2. JPQL Query với `@Query` (Truy vấn nối nhiều bảng gián tiếp)
- **Áp dụng**: Lấy danh mục nhạc cụ/đàn theo cơ sở khi bảng `equipments` không có cột `branch_id` mà liên kết thông qua `rooms`:
  ```java
  public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
      @Query("SELECT e FROM Equipment e JOIN e.room r WHERE r.branch.id = :branchId ORDER BY e.id ASC")
      List<Equipment> findEquipmentsByBranchId(@Param("branchId") Long branchId);

      @Query("SELECT e FROM Equipment e JOIN e.room r WHERE r.branch.id = :branchId AND e.category = :category ORDER BY e.id ASC")
      List<Equipment> findEquipmentsByBranchIdAndCategory(
              @Param("branchId") Long branchId,
              @Param("category") EquipmentCategory category
      );
  }
  ```

### 3. Tầng Mapper (`BranchFacilityMapper.java`)
- Chuyển đổi an toàn từ Entity sang Response DTO và Request DTO sang Entity:
  ```java
  @Component
  public class BranchFacilityMapper {
      public RoomResponse toRoomResponse(Room room) {
          if (room == null) return null;
          return RoomResponse.builder()
                  .id(room.getId())
                  .branchId(room.getBranch() != null ? room.getBranch().getId() : null)
                  .branchName(room.getBranch() != null ? room.getBranch().getName() : null)
                  .roomCode(room.getRoomCode())
                  .roomName(room.getRoomName())
                  .capacity(room.getCapacity())
                  .roomType(room.getRoomType())
                  .status(room.getStatus())
                  .description(room.getDescription())
                  .build();
      }
  }
  ```

### 4. Tầng Service: Interface & Implementation
- **Interface [BranchFacilityService.java]**:
  ```java
  public interface BranchFacilityService {
      List<BranchResponse> getAllBranches();
      List<BranchOptionResponse> getBranchOptions();
      List<RoomResponse> getRoomsByBranch(Long branchId);
      List<RoomOptionResponse> getRoomOptions(Long branchId);
      List<EquipmentResponse> getEquipmentsByBranch(Long branchId, EquipmentCategory category);
      BranchResponse createBranch(CreateBranchRequest request);
      RoomResponse createRoom(CreateRoomRequest request);
      EquipmentResponse createEquipment(CreateEquipmentRequest request);
  }
  ```
- **Implementation [BranchFacilityServiceImpl.java]**:
  ```java
  @Service
  @RequiredArgsConstructor
  @Transactional(readOnly = true)
  public class BranchFacilityServiceImpl implements BranchFacilityService {
      private final RoomRepository roomRepository;
      private final BranchFacilityMapper branchFacilityMapper;

      @Override
      public List<RoomResponse> getRoomsByBranch(Long branchId) {
          return roomRepository.findByBranchId(branchId).stream()
                  .map(branchFacilityMapper::toRoomResponse)
                  .toList();
      }
  }
  ```

---

## 5. Cấu Trúc & Cơ Chế Hoạt Động Cụ Thể Của Từng File Frontend (`com_fe`)

Thư mục `com_fe` là giao diện Web Client độc lập, chạy trên trình duyệt người dùng và giao tiếp với Backend qua REST API:

```text
com_fe/
├── index.html                    # Dashboard chính: Tổng quan số liệu, lối tắt vào 4 phân hệ
├── login.html                    # Trang đăng nhập xác thực tài khoản người dùng
├── components/
│   ├── navbar.html               # Thanh điều hướng trên cùng (Logo, Menu, Thông tin User, Nút Đăng xuất)
│   └── footer.html               # Chân trang (Bản quyền, Hotline hỗ trợ, Địa chỉ học viện)
├── pages/
│   ├── branch-facility.html      # Phân hệ 1: Quản lý Cơ sở, Phòng học & Thiết bị Đàn Piano
│   ├── attendance-makeup.html    # Phân hệ 2: Điểm danh học viên, Xin nghỉ học & Đăng ký học bù
│   ├── placement-test.html       # Phân hệ 3: Phiếu test đầu vào, Đính kèm audio/video & Lộ trình học
│   └── tuition-payment.html      # Phân hệ 4: Lập hóa đơn học phí, Miễn giảm phí & Thanh toán VietQR
├── js/
│   ├── api.js                    # Thư viện gọi HTTP API tập trung (fetch wrapper, tự gắn Bearer Token)
│   └── auth.js                   # Quản lý phiên đăng nhập, phân quyền người dùng & tự phục hồi dữ liệu
└── css/
    └── style.css                 # Hệ thống CSS Design System (Màu sắc, Glassmorphism, Responsive, Animations)
```

### 🔍 Nhiệm Vụ Cụ Thể Của Từng File:

| Tên File | Vai Trò & Cơ Chế Hoạt Động Chi Tiết |
| :--- | :--- |
| **`login.html`** | - Tiếp nhận `username` và `password` từ người dùng.<br>- Bắt sự kiện submit form, gọi hàm `login(username, password)` trong `auth.js`.<br>- Khi API `/api/auth/login` trả về thành công, lưu JWT Token và thông tin User vào `localStorage`, sau đó chuyển hướng ngay về `index.html`.<br>- Hiển thị thông báo lỗi trực quan nếu sai mật khẩu hoặc tài khoản bị khóa. |
| **`index.html`** | - Bảng điều khiển trung tâm (Dashboard) sau khi đăng nhập thành công.<br>- Gọi `requireAuth()` từ `auth.js` ngay khi mở trang: Nếu chưa đăng nhập thì tự động chuyển hướng về `login.html`.<br>- Tải động `components/navbar.html` và `components/footer.html`.<br>- Gọi API `/api/branches` để hiển thị thống kê tổng quan cơ sở, danh sách lối tắt điều hướng tới 4 phân hệ chính. |
| **`pages/branch-facility.html`** | - Giao diện quản lý phân hệ Cơ sở & CSVC.<br>- Chứa 3 Tab chức năng: **Danh sách cơ sở**, **Phòng học âm nhạc**, và **Trang thiết bị nhạc cụ (Đàn Piano/Guitar)**.<br>- Tích hợp dropdown lọc phòng học và thiết bị theo từng cơ sở (gọi `/api/branches/{id}/rooms` và `/api/branches/{id}/equipments`).<br>- Cung cấp bộ lọc theo danh mục nhạc cụ (Grand Piano, Upright Piano, Digital Piano, Phụ kiện). |
| **`pages/attendance-makeup.html`** | - Giao diện quản lý Điểm danh & Học bù.<br>- Hiển thị lịch dạy/học theo tuần, bảng điểm danh chuyên cần của học viên.<br>- Cho phép học viên/phụ huynh gửi đơn xin nghỉ học có lý do.<br>- Cung cấp giao diện cho giáo viên duyệt đơn và tìm các ca học trống phù hợp để xếp lớp học bù. |
| **`pages/placement-test.html`** | - Giao diện kiểm tra năng khiếu đầu vào & thiết lập lộ trình học.<br>- Tiếp nhận hồ sơ test của học viên mới, cho phép giáo viên nhập điểm thẩm âm, tiết tấu, cảm thụ âm nhạc.<br>- Hỗ trợ xem các file ghi âm, video clip bài biểu diễn của học viên.<br>- Tự động đề xuất khóa học và lộ trình đào tạo phù hợp theo kết quả đánh giá. |
| **`pages/tuition-payment.html`** | - Giao diện nghĩa vụ đóng học phí.<br>- Hiển thị danh sách hóa đơn theo trạng thái (Chờ thanh toán, Đã thanh toán, Quá hạn).<br>- Tích hợp tính năng áp dụng chính sách học bổng, mã giảm giá khuyến học.<br>- Tự động tạo mã thanh toán **VietQR động** chứa số tiền và nội dung chuyển khoản để phụ huynh quét mã chuyển khoản nhanh. |
| **`components/navbar.html`** | - Component thanh điều hướng chung cho toàn bộ ứng dụng.<br>- Tự động chèn thông tin người dùng đang đăng nhập (`[ROLE] Họ và tên`), Avatar.<br>- Cung cấp nút **Đăng xuất** an toàn (xóa token khỏi `localStorage` và chuyển về `login.html`).<br>- Tự động highlight phân hệ đang được mở. |
| **`components/footer.html`** | - Chân trang chuẩn hóa hiển thị bản quyền Học viện Talent Academy, thông tin liên hệ và hotline kỹ thuật. |
| **`js/api.js`** | - Module HTTP Client bọc hàm `fetch()` nguyên bản thành hàm `callApi(endpoint, options)`.<br>- **Tự động gắn Header**: `Authorization: Bearer <JWT_TOKEN>` vào mỗi request gửi lên Backend.<br>- **Tự động xử lý lỗi 401 Unauthorized**: Nếu token hết hạn hoặc không hợp lệ, tự động xóa phiên và đưa người dùng về trang đăng nhập.<br>- Tự động cấu hình `Content-Type: application/json` và phân tích kết quả JSON trả về. |
| **`js/auth.js`** | - Module kiểm soát phiên làm việc (Session & Security) phía Client.<br>- Cung cấp các hàm: `login()`, `logout()`, `getToken()`, `getCurrentUser()`, `isAuthenticated()`, `requireAuth()`.<br>- Tích hợp cơ chế **Self-Healing (`sanitizeUserData`)**: Tự động phát hiện và làm sạch chuỗi ký tự lỗi font (Mojibake) lưu trong cache trình duyệt để luôn hiển thị tên tiếng Việt có dấu chuẩn đẹp. |
| **`css/style.css`** | - Hệ thống CSS Design System hoàn chỉnh, thuần CSS3 hiện đại (Vanilla CSS) không phụ thuộc framework ngoài.<br>- Sử dụng CSS Variables để quản lý đồng bộ bảng màu (Primary, Secondary, Dark theme, Accents).<br>- Áp dụng hiệu ứng kính mờ thời thượng (Glassmorphism), bóng đổ mềm mại (box-shadow) và hiệu ứng chuyển động mượt mà (smooth transitions, hover scale).<br>- Hỗ trợ hiển thị co giãn chuẩn Responsive trên mọi kích thước màn hình (Desktop, Tablet, Mobile). |

---

## 6. Nền Tảng Lý Thuyết Cốt Lõi: Spring Boot & RESTful API (Cho Người Mới)

### 🌿 Spring Boot là gì?
**Spring Boot** là một framework mã nguồn mở của Java, được xây dựng trên nền tảng của **Spring Framework**, nhằm đơn giản hóa tối đa quá trình khởi tạo, cấu hình và triển khai ứng dụng Java Enterprise:
1. **Khắc phục "Cơn ác mộng XML" (Configuration Hell)**: Các phiên bản Spring truyền thống yêu cầu hàng tá file cấu hình XML phức tạp (`beans.xml`, `web.xml`, `applicationContext.xml`). Spring Boot loại bỏ hoàn toàn điều này nhờ cơ chế **Tự động cấu hình (Auto-Configuration)** dựa trên các Annotation (`@SpringBootApplication`, `@Configuration`).
2. **Nhúng sẵn Máy chủ Web (Embedded Tomcat)**: Bạn không cần cài đặt Tomcat hay GlassFish bên ngoài rồi deploy file WAR. Spring Boot chứa sẵn máy chủ web bên trong, ứng dụng chạy độc lập thông qua một hàm `main()` chuẩn duy nhất.
3. **Quản lý Thư viện qua Starters**: Cung cấp các gói phụ thuộc tổng hợp (như `spring-boot-starter-web`, `spring-boot-starter-data-jpa`) tự động tối ưu phiên bản tương thích của các thư viện con, ngăn ngừa xung đột dependency.

### 🧩 Các Khái Niệm Nền Tảng Trong Spring Boot:
- **IoC (Inversion of Control - Đảo ngược điều khiển)**: Trong lập trình thông thường, bạn tự tạo đối tượng (`new BranchService()`). Với IoC, quyền khởi tạo và quản lý vòng đời của đối tượng được giao hoàn toàn cho **Spring IoC Container**.
- **DI (Dependency Injection - Tiêm phụ thuộc)**: Cơ chế mà Spring tự động đưa (tiêm) đối tượng phụ thuộc vào nơi cần dùng mà không cần lập trình viên tự khởi tạo bằng tay (sử dụng `@Autowired` hoặc cú pháp hiện đại `@RequiredArgsConstructor` của Lombok).
- **Spring Bean**: Là bất kỳ đối tượng Java nào được khởi tạo, lắp ráp và quản lý bởi Spring Container (thông qua các annotation nhận diện: `@Component`, `@Service`, `@Repository`, `@RestController`, `@Configuration`).
- **Spring Data JPA & ORM**: Cơ chế ánh xạ các bảng CSDL quan hệ thành các Class Java (`@Entity`), loại bỏ nhu cầu viết các câu truy vấn SQL thô ráp, tự động sinh ra các hàm CRUD cơ bản mà không cần viết một dòng code cài đặt nào.

---

### 🌐 RESTful API là gì?
**REST (Representational State Transfer)** là phong cách kiến trúc phần mềm tiêu chuẩn cho các dịch vụ web phân tán, sử dụng giao thức HTTP để truyền nhận dữ liệu:
1. **Phi Trạng Thái (Statelessness)**: Mỗi request gửi từ Client lên Server phải chứa đầy đủ mọi thông tin cần thiết để Server hiểu và xử lý (ví dụ Header `Authorization: Bearer <token>`). Server không lưu lại phiên làm việc (Session) của người dùng trong bộ nhớ RAM, giúp hệ thống mở rộng quy mô (Scale-out) dễ dàng trên hàng trăm server mà không bị lệch session.
2. **Định dạng dữ liệu JSON**: Thay vì trả về mã HTML nguyên trang, REST API chỉ trả về dữ liệu thô dạng chuỗi JSON nhẹ, dễ đọc và tương thích với mọi ngôn ngữ lập trình (JavaScript, Swift, Kotlin, Python, C#).
3. **Các Phương Thức HTTP (HTTP Methods / Verbs) Chuẩn**:
   - `GET`: Lấy dữ liệu (Read) - Phương thức an toàn, không làm thay đổi trạng thái dữ liệu trên server.
   - `POST`: Tạo mới tài nguyên (Create) - Gửi dữ liệu trong Request Body, server trả về mã `201 Created`.
   - `PUT`: Cập nhật toàn bộ tài nguyên (Full Update) - Ghi đè tài nguyên hiện có.
   - `PATCH`: Cập nhật một phần tài nguyên (Partial Update) - Chỉ sửa đổi một vài trường cụ thể.
   - `DELETE`: Xóa tài nguyên (Delete) - Trả về mã `204 No Content` khi xóa thành công.
4. **Mã Trạng Thái HTTP (HTTP Status Codes) Phổ Biến**:
   - `200 OK`: Yêu cầu thực hiện thành công, có dữ liệu trả về.
   - `201 Created`: Tạo mới tài nguyên thành công.
   - `204 No Content`: Thao tác thành công nhưng không có dữ liệu trả về (thường dùng cho DELETE).
   - `400 Bad Request`: Dữ liệu gửi lên không hợp lệ (sai định dạng, thiếu trường bắt buộc).
   - `401 Unauthorized`: Chưa xác thực danh tính (chưa đăng nhập hoặc token không hợp lệ).
   - `403 Forbidden`: Đã đăng nhập nhưng không đủ quyền hạn để truy cập tài nguyên.
   - `404 Not Found`: Không tìm thấy tài nguyên với ID tương ứng.
   - `500 Internal Server Error`: Lỗi phát sinh ngoài ý muốn từ phía Backend Server.

---

## 7. So Sánh Chuyên Sâu: Spring Boot REST API vs Java Web Truyền Thống (Servlet, JSP, JDBC)

Để giúp các thành viên chuyển từ tư duy học tập cơ bản sang phát triển ứng dụng thực tế chuyên nghiệp, bảng dưới đây so sánh toàn diện từng thành phần giữa hai thế hệ công nghệ:

| Tiêu Chí So Sánh | Java Web Truyền Thống (Servlet + JSP + JDBC) | Spring Boot 3 REST API + Modern Web (COM Project) | Lợi Thế Vượt Trội Của Spring Boot & REST API |
| :--- | :--- | :--- | :--- |
| **Tầng Giao Diện (Presentation)** | **JSP (JavaServer Pages)**:<br>- Mã Java nhúng lẫn lộn trong HTML qua thẻ `<% ... %>`, JSTL và EL.<br>- Cơ chế **Server-Side Rendering (SSR)**: Server phải biên dịch JSP thành Servlet rồi dựng toàn bộ trang HTML trước khi gửi về client.<br>- Mỗi lần click nút hay lọc dữ liệu là toàn bộ trang web bị reload trắng màn hình. | **Tách Rời Hoàn Toàn (HTML5/CSS3/JS)**:<br>- Server chỉ đóng vai trò cung cấp REST API trả về dữ liệu thô dạng JSON.<br>- Client tự sử dụng JavaScript để render giao diện động ngay trên trình duyệt.<br>- Chuyển tab, lọc dữ liệu không cần tải lại trang (Single Page mindset). | **Trải nghiệm người dùng (UX) mượt mà tối đa**.<br>Tách rời hoàn toàn đội ngũ Frontend và Backend: Mỗi bên có thể code độc lập, kiểm thử riêng biệt mà không phụ thuộc vào nhau. 1 Backend có thể phục vụ cùng lúc cả Web, Mobile App (iOS/Android) và Hệ thống bên thứ 3. |
| **Tầng Điều Khiển (Controller)** | **HttpServlet**:<br>- Phải kế thừa `HttpServlet` và override hàm `doGet()`, `doPost()`.<br>- Lấy tham số thủ công: `request.getParameter("id")`.<br>- Tự ép kiểu: `Long.parseLong(...)`.<br>- Đẩy dữ liệu ra view bằng `request.setAttribute("list", list)`.<br>- Chuyển trang thủ công bằng `RequestDispatcher.forward()`. | **`@RestController` + `@RequestMapping`**:<br>- Viết hàm như hàm Java bình thường.<br>- Tự động parse URL path: `@PathVariable Long id`.<br>- Tự động parse JSON Body sang Object: `@RequestBody CreateBranchRequest request`.<br>- Tự động kiểm tra dữ liệu: `@Valid`.<br>- Trả về kết quả bọc trong `ResponseEntity<T>`. | **Giảm 80% code thừa (Boilerplate code)**.<br>Tự động hóa hoàn toàn việc mapping kiểu dữ liệu, validate dữ liệu đầu vào và chuyển đổi định dạng JSON hai chiều. |
| **Tầng Truy Vấn CSDL (Data Access)** | **JDBC thuần (Java Database Connectivity)**:<br>- Mở kết nối `Connection = DriverManager.getConnection()`.<br>- Tạo `PreparedStatement`, viết câu lệnh SQL thô bằng chuỗi String.<br>- Dùng vòng lặp `while (rs.next())` đọc từng cột gán vào đối tượng.<br>- Bắt buộc phải nhớ đóng `Connection`, `PreparedStatement`, `ResultSet` trong khối `finally` để tránh rò rỉ bộ nhớ (Memory leak/Connection leak). | **Spring Data JPA & Hibernate (ORM)**:<br>- Khai báo Entity (`@Entity`, `@Table`) phản ánh cấu trúc bảng CSDL.<br>- Khai báo Interface kế thừa `JpaRepository<Entity, ID>`.<br>- Tự động có sẵn mọi hàm CRUD cơ bản (`save`, `findById`, `findAll`, `deleteById`).<br>- Tự động quản lý Connection Pool tối ưu hiệu năng cao (**HikariCP**). | **Triệt tiêu hoàn toàn code truy vấn lặp đi lặp lại**.<br>Loại bỏ rủi ro rò rỉ kết nối CSDL, an toàn tuyệt đối trước các cuộc tấn công SQL Injection và nâng cao tốc độ phát triển tính năng gấp nhiều lần. |
| **Quản Lý Vòng Đời & Phụ Thuộc (Object Management)** | **Khởi Tạo Thủ Công (Tightly Coupled)**:<br>- Muốn dùng class nào phải tự gõ `new BranchService()`, `new BranchDao()`.<br>- Khi một class thay đổi constructor, hàng loạt class khác bị lỗi biên dịch theo.<br>- Rất khó viết Unit Test vì các lớp bị gắn chặt vào nhau, không thể thay thế bằng Mock object. | **Spring IoC & Dependency Injection (Loosely Coupled)**:<br>- Đánh dấu các lớp bằng `@Service`, `@Component`, `@Repository`.<br>- Spring tự động khởi tạo và tiêm phụ thuộc thông qua `@RequiredArgsConstructor`.<br>- Lập trình hướng giao diện (Programming to Interface): Controller chỉ biết đến `BranchFacilityService` interface mà không cần quan tâm class cài đặt cụ thể. | **Kiến trúc linh hoạt, dễ bảo trì và mở rộng**.<br>Dễ dàng thay đổi logic hoặc thay thế các module mà không làm ảnh hưởng đến tầng khác. Hỗ trợ viết Unit Test tự động cực kỳ thuận tiện. |
| **Đóng Gói & Triển Khai (Deployment)** | **File WAR & Máy chủ ngoài**:<br>- Phải build ứng dụng thành file `.war`.<br>- Cài đặt và cấu hình máy chủ web Apache Tomcat hoặc WildFly độc lập trên máy chủ vật lý.<br>- Copy file war vào thư mục `webapps` của Tomcat rồi khởi động server. | **File JAR Độc Lập (Fat JAR / Uber JAR)**:<br>- Đóng gói toàn bộ mã nguồn, thư viện phụ thuộc và cả máy chủ Tomcat nhúng vào **một file `.jar` duy nhất**.<br>- Chạy ở bất cứ nơi đâu có cài Java chỉ bằng câu lệnh: `java -jar talent-management.jar`. | **Triển khai thần tốc, sẵn sàng cho môi trường Cloud & DevOps**.<br>Dễ dàng đóng gói thành Docker Container, tích hợp quy trình tự động hóa CI/CD và triển khai lên AWS, Azure, Google Cloud hoặc Kubernetes. |

---

## 8. Hướng Dẫn Dành Cho Thành Viên Nhóm Phát Triển

Khi bạn nhận nhiệm vụ phát triển một trong các tính năng tiếp theo:
1. **`attendance_makeup`**: Điểm danh học viên, đơn xin nghỉ và duyệt đăng ký học bù.
2. **`placement_test`**: Phiếu đánh giá năng khiếu đầu vào, file ghi âm/video và lộ trình học.
3. **`tuition_payment`**: Lập hóa đơn học phí, áp dụng chính sách miễn giảm và thanh toán VietQR.

Khung thư mục chuẩn (Skeleton) cho cả 3 phân hệ đã được dựng sẵn 100% bao gồm: `controller/`, `dto/request/`, `dto/response/`, `mapper/`, `repository/`, `service/` (Interface) và `service/impl/`.

### 🛠️ Quy Trình Thực Hiện Từng Bước:
- **Bước 1 (Định nghĩa DTO)**: Bổ sung các trường dữ liệu và ràng buộc validation (`@NotBlank`, `@NotNull`, `@Min`) vào các file Request và Response trong thư mục `dto/request/` và `dto/response/`.
- **Bước 2 (Viết Repository)**: Mở file trong `repository/` để khai báo các phương thức truy vấn:
  - Dùng **Derived Query Method** cho các truy vấn đơn giản (ví dụ: `findByStatus`, `findByStudentId`).
  - Dùng **JPQL (`@Query`)** cho các truy vấn nối nhiều bảng (ví dụ: `SELECT a FROM Attendance a JOIN a.enrollment e WHERE ...`).
- **Bước 3 (Viết Mapper)**: Bổ sung logic chuyển đổi trong file `mapper/` để convert an toàn giữa Entity và DTO (luôn kiểm tra `null` trước khi truy xuất các quan hệ con).
- **Bước 4 (Khai báo Interface & Viết Service Impl)**:
  - Khai báo chữ ký hàm nghiệp vụ trong file Interface `service/<Feature>Service.java`.
  - Viết code thực thi logic trong `service/impl/<Feature>ServiceImpl.java`, đánh dấu `@Service`, `@RequiredArgsConstructor` và `@Transactional`.
- **Bước 5 (Viết Controller)**: Tạo các API Endpoint tương ứng trong `controller/` (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`), trả về kết quả qua `ResponseEntity.ok(...)`.
- **Bước 6 (Kết nối Giao Diện)**: Mở file HTML tương ứng trong `com_fe/pages/` để gọi API qua hàm `callApi(endpoint)` và render dữ liệu thực tế lên màn hình.
- **Bước 7 (Kiểm tra biên dịch)**: Chạy lệnh `mvn compile -DskipTests` tại thư mục `com_be` để đảm bảo dự án luôn **BUILD SUCCESS**.

---

## 9. Hướng Dẫn Khởi Chạy Dự Án

### Bước 1: Khởi Tạo Cơ Sở Dữ Liệu (SQL Server)
1. Mở **SQL Server Management Studio (SSMS)**.
2. Mở file: `database/init_database.sql` (file đã được lưu chuẩn UTF-8 with BOM hiển thị tiếng Việt hoàn hảo).
3. Nhấn **Execute (F5)** để tự động tạo CSDL `course_operation_management` và toàn bộ dữ liệu mẫu (Seed Data).
4. Kiểm tra cấu hình kết nối trong file: `com_be/src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=course_operation_management;encrypt=true;trustServerCertificate=true
   spring.datasource.username=sa
   spring.datasource.password=sa
   ```

### Bước 2: Khởi Chạy Backend (`com_be`) - Port 8080
- **Cách 1 (IntelliJ IDEA)**: Mở project, tìm file `TalentManagementApplication.java` (`com.talent.management`), nhấn nút **Run** (Shift + F10).
- **Cách 2 (Terminal / Command Line)**:
  ```powershell
  cd d:\Ky_7\SBA301\Apartment_management\course-operation-management\com_be
  mvn spring-boot:run
  ```
- **Tài liệu API trực quan (Swagger UI)**: Truy cập trình duyệt tại [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) để xem toàn bộ danh sách API và test trực tiếp.

### Bước 3: Khởi Chạy Frontend (`com_fe`) - Port 5500
- **Sử dụng Visual Studio Code**: Mở thư mục `course-operation-management`, cài đặt extension **Live Server** (nếu chưa có).
- Nhấp chuột phải vào file `com_fe/login.html` (hoặc `com_fe/index.html`) $\rightarrow$ Chọn **Open with Live Server**.
- Trình duyệt sẽ tự động mở tại địa chỉ: [http://127.0.0.1:5500/login.html](http://127.0.0.1:5500/login.html).

### 👥 Danh Sách Tài Khoản Thử Nghiệm (Mật Khẩu Chung: `123456`)
| Tài Khoản (Username) | Mật Khẩu | Vai Trò (Role) | Họ Và Tên Hiển Thị |
| :--- | :--- | :--- | :--- |
| `admin` | `123456` | `ADMIN` | Quản Trị Viên Hệ Thống |
| `teacher_huong` | `123456` | `TEACHER` | Cô Vũ Thu Hương (GV Piano) |
| `teacher_tuan` | `123456` | `TEACHER` | Thầy Trần Anh Tuấn (GV Guitar) |
| `cashier_mai` | `123456` | `CASHIER` | Nguyễn Thanh Mai (Thu Ngân) |
| `parent_lan` | `123456` | `PARENT` | Phụ Huynh Lê Thị Lan |
