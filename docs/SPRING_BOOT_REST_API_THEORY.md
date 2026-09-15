# Cẩm Nang Lý Thuyết Chuyên Sâu: Spring Boot & RESTful API

> **Tài liệu đào tạo nội bộ dự án: Course Operation Management (COM)**  
> **Dành cho**: Thành viên nhóm phát triển, người mới tiếp cận Spring Boot và các lập trình viên chuyển từ mô hình Servlet/JSP/JDBC sang Spring Boot REST API hiện đại.

---

## 📑 Mục Lục
1. [Phần 1: Nền Tảng Lý Thuyết Spring Boot Cốt Lõi](#phần-1-nền-tảng-lý-thuyết-spring-boot-cốt-lõi)
   - [1.1. Lịch sử & Vì sao Spring Boot ra đời?](#11-lịch-sử--vì-sao-spring-boot-ra-đời)
   - [1.2. Trái tim của Spring: IoC và Dependency Injection (DI)](#12-trái-tim-của-spring-ioc-inversion-of-control--di-dependency-injection)
   - [1.3. Spring Bean, Vòng đời & Các Stereotype Annotation](#13-spring-bean-là-gì-vòng-đời--các-annotation-nhận-diện)
   - [1.4. Vòng đời 1 HTTP Request trong Spring Boot (DispatcherServlet)](#14-vòng-đời-của-1-http-request-trong-spring-boot-dispatcherservlet)
   - [1.5. Spring Data JPA & Hibernate (Cơ chế ORM)](#15-spring-data-jpa--hibernate-cơ-chế-orm)
2. [Phần 2: Nền Tảng Lý Thuyết RESTful API Chuẩn Doanh Nghiệp](#phần-2-nền-tảng-lý-thuyết-restful-api-chuẩn-doanh-nghiệp)
   - [2.1. REST là gì & 6 ràng buộc kiến trúc](#21-rest-là-gì)
   - [2.2. Quy tắc đặt tên URI / Endpoint chuẩn RESTful](#22-quy-tắc-đặt-tên-uri--endpoint-chuẩn-restful-cực-kỳ-quan-trọng)
   - [2.3. Bảng phân biệt HTTP Methods: Idempotent vs Safe](#23-bảng-phân-biệt-các-http-methods-idempotent-vs-safe)
   - [2.4. Bảng tra cứu mã trạng thái HTTP (Status Codes)](#24-bảng-tra-cứu-mã-trạng-thái-http-status-codes-chuẩn-doanh-nghiệp)
3. [Phần 3: Bảng Đối Chiếu Toàn Diện Với Kỹ Thuật Cũ (JDBC - Servlet - JSP)](#phần-3-bảng-đối-chiếu-toàn-diện-với-kỹ-thuật-cũ-jdbc---servlet---jsp)
   - [3.1. Tầng truy vấn: JDBC thuần vs Spring Data JPA](#1-tầng-truy-vấn-dữ-liệu-jdbc-thuần-vs-spring-data-jpa)
   - [3.2. Tầng điều khiển: HttpServlet vs @RestController](#2-tầng-điều-khiển-httpservlet-vs-restcontroller)
   - [3.3. Tầng giao diện: JSP vs RESTful API + Client-Side Rendering](#3-tầng-giao-diện-jsp-server-side-rendering-vs-restful-api--client-side-rendering)
4. [Phần 4: Bản Đồ Chuyển Đổi Tư Duy (Mindset Shift)](#phần-4-bản-đồ-chuyển-đổi-tư-duy-mindset-shift)

---

# PHẦN 1: NỀN TẢNG LÝ THUYẾT SPRING BOOT CỐT LÕI

## 1.1. Lịch Sử & Vì Sao Spring Boot Ra Đời?

Trước khi có Spring Boot, các lập trình viên Java Enterprise (J2EE/Spring Framework truyền thống) phải đối mặt với **"Cơn ác mộng cấu hình" (Configuration Hell)**:
- Để kết nối CSDL và dựng 1 web app đơn giản, bạn phải viết hàng trăm dòng cấu hình trong các file XML (`web.xml`, `applicationContext.xml`, `dispatcher-servlet.xml`, `hibernate.cfg.xml`).
- Xung đột thư viện (Dependency Hell): Tìm phiên bản Spring 4 nào chạy được với Hibernate nào và tương thích với Tomcat nào là một cực hình.
- Phải tự cài Tomcat bên ngoài máy chủ, xuất file `.war` rồi copy thủ công vào thư mục `webapps`.

👉 **Spring Boot ra đời với triết lý: "Convention over Configuration" (Quy ước hơn cấu hình) & "Opinionated Defaults" (Cung cấp cấu hình chuẩn sẵn có)**:
- **Tự động cấu hình (Auto-Configuration)**: Nếu Spring Boot thấy có file `mssql-jdbc` trong project, nó tự động hiểu bạn muốn kết nối SQL Server và tự cấu hình `DataSource`.
- **Tomcat nhúng (Embedded Server)**: Web server (Tomcat/Jetty) được nhúng thẳng vào file `.jar`. Bạn chỉ cần bấm nút **Run** trong IDE hoặc gõ `java -jar app.jar` là web server tự bật lên ở cổng 8080.

---

## 1.2. Trái Tim Của Spring: IoC (Inversion of Control) & DI (Dependency Injection)

Đây là 2 khái niệm quan trọng nhất mà bất kỳ ai học Spring cũng phải nắm vững.

### A. Lập trình truyền thống (Không có IoC):
Khi Class A cần Class B, Class A sẽ **tự tạo** Class B bằng từ khóa `new`:
```java
public class BranchFacilityService {
    // Tự new đối tượng -> Bị gắn chặt (Tightly Coupled)
    private BranchRepository branchRepository = new BranchRepositoryImpl(); 
}
```
* **Nhược điểm**: Nếu ngày mai constructor của `BranchRepositoryImpl` đổi tham số, bạn phải vào sửa lại code ở hàng chục nơi. Bạn **không thể viết Unit Test** để giả lập (mock) repository được.

### B. Lập trình hiện đại với Spring IoC & DI:
- **IoC (Đảo ngược quyền điều khiển)**: Bạn không tự `new` đối tượng nữa. Việc tạo ra đối tượng, quản lý vòng đời, hủy đối tượng được giao toàn quyền cho **Spring IoC Container** (hoặc `ApplicationContext`).
- **DI (Tiêm phụ thuộc)**: Khi Service cần Repository, Spring IoC Container sẽ tự động "tiêm" (inject) một phiên bản của Repository vào Service:

```java
@Service
@RequiredArgsConstructor // Lombok tự tạo Constructor nhận các trường final
public class BranchFacilityServiceImpl implements BranchFacilityService {

    // Spring tự động tìm Bean BranchRepository trong Container và "tiêm" vào đây!
    private final BranchRepository branchRepository; 
}
```

---

## 1.3. Spring Bean Là Gì? Vòng Đời & Các Annotation Nhận Diện

### A. Spring Bean là gì?
> **Spring Bean** đơn giản là một **đối tượng Java (Java Object)** được tạo ra, cấu hình và quản lý hoàn toàn bởi **Spring IoC Container**.

### B. Các Annotation đánh dấu Bean (Stereotype Annotations):
Mọi class có gắn các annotation sau đều được Spring nhận diện là một Bean:

| Annotation | Tầng Kiến Trúc | Nhiệm Vụ Cụ Thể |
| :--- | :--- | :--- |
| **`@Component`** | Tầng Tiện ích chung | Đánh dấu class chung chung là 1 Bean (ví dụ: `BranchFacilityMapper`, Helper class). |
| **`@RestController`** | Tầng Web / API | Kết hợp giữa `@Controller` và `@ResponseBody`. Nhận HTTP Request và tự động trả về dữ liệu thô (JSON/XML), không chuyển hướng sang file HTML/JSP. |
| **`@Service`** | Tầng Nghiệp vụ (Business) | Đánh dấu class chứa logic nghiệp vụ, tính toán, kiểm tra quyền hạn, quản lý transaction. |
| **`@Repository`** | Tầng CSDL (Data Access) | Đánh dấu tầng truy vấn CSDL. Tự động bắt các ngoại lệ SQL của CSDL và chuyển hóa thành ngoại lệ của Spring (`DataAccessException`). |
| **`@Configuration` & `@Bean`** | Tầng Cấu hình | Dùng trên class cấu hình để khai báo thủ công các Bean của bên thứ 3 (ví dụ cấu hình bảo mật `SecurityFilterChain`, OpenAPI Swagger...). |

### C. Bean Scope (Phạm vi sống của Bean):
- **Singleton (Mặc định - Chiếm 95% dự án)**: Spring chỉ tạo duy nhất **1 instance** của Bean trong toàn bộ vòng đời ứng dụng. Mọi nơi inject vào đều dùng chung instance này $\rightarrow$ Tiết kiệm RAM tối đa.
- **Prototype**: Mỗi lần có nơi gọi hoặc inject, Spring lại tạo ra **1 instance mới**.
- **Request / Session**: Chỉ tồn tại trong vòng đời của 1 HTTP Request hoặc 1 phiên đăng nhập.

---

## 1.4. Vòng Đời Của 1 HTTP Request Trong Spring Boot (DispatcherServlet)

Khi người dùng từ trình duyệt gửi một request (ví dụ: `GET /api/branches/1/rooms`):

```text
Trình duyệt Client (gửi request)
   │
   ▼
[1. Embedded Tomcat Port 8080]
   │
   ▼
[2. Filter Chain (Bảo mật: JwtAuthenticationFilter)] ---> (Chưa đăng nhập -> Chặn lại 401)
   │
   ▼
[3. DispatcherServlet] (Nhạc trưởng điều phối)
   │
   ├─► Tìm Controller phù hợp qua HandlerMapping
   │
   ▼
[4. Controller Advice / Interceptor] (Tiền xử lý)
   │
   ▼
[5. BranchFacilityController] (@PathVariable Long branchId)
   │
   ▼
[6. BranchFacilityService] (Kiểm tra nghiệp vụ, @Transactional)
   │
   ▼
[7. RoomRepository] (Chạy câu lệnh SQL lấy danh sách)
   │
   ▼
[8. Database SQL Server] (Trả kết quả rows)
   │
   ▼
[9. BranchFacilityMapper] (Biến Entity -> RoomResponse DTO)
   │
   ▼
[10. DispatcherServlet chuyển RoomResponse thành chuỗi JSON qua thư viện Jackson]
   │
   ▼
Trình duyệt Client nhận JSON 200 OK
```

---

## 1.5. Spring Data JPA & Hibernate (Cơ Chế ORM)

### A. ORM (Object-Relational Mapping) là gì?
- Trong CSDL quan hệ, dữ liệu nằm ở các **Bảng (Tables), Cột (Columns), Khóa ngoại (Foreign Keys)**.
- Trong Java, dữ liệu nằm ở các **Đối tượng (Objects), Thuộc tính (Fields), Quan hệ (Has-a, Is-a)**.
- **ORM** (với đại diện xuất sắc nhất là **Hibernate**) là cây cầu nối tự động dịch chuyển dữ liệu qua lại giữa hai thế giới này.

### B. Các Annotation ORM cốt lõi:
- `@Entity` & `@Table(name = "rooms")`: Đánh dấu class Java này map với bảng `rooms`.
- `@Id` & `@GeneratedValue(strategy = GenerationType.IDENTITY)`: Khóa chính tự tăng (Identity trong SQL Server).
- `@ManyToOne(fetch = FetchType.LAZY)`: Quan hệ nhiều - một. `LAZY` nghĩa là chỉ khi nào gọi `room.getBranch()` thì mới query bảng branch, giúp tối ưu bộ nhớ.
- `@JoinColumn(name = "branch_id")`: Tên cột khóa ngoại trong database.

### C. Cơ chế Derived Query Method:
Spring Data JPA đọc **tên hàm** trong interface để tự suy luận ra câu lệnh SQL:
- `findByBranchId(Long branchId)` $\rightarrow$ `SELECT * FROM rooms WHERE branch_id = ?`
- `findByActiveTrue()` $\rightarrow$ `SELECT * FROM branches WHERE active = 1`
- `findByRoomNameContainingIgnoreCase(String name)` $\rightarrow$ `SELECT * FROM rooms WHERE LOWER(room_name) LIKE LOWER('%' + ? + '%')`

---

# PHẦN 2: NỀN TẢNG LÝ THUYẾT RESTFUL API CHUẨN DOANH NGHIỆP

## 2.1. REST Là Gì?
**REST (Representational State Transfer)** không phải là một công nghệ hay ngôn ngữ, mà là **một phong cách kiến trúc (Architectural Style)** do Roy Fielding đề xuất năm 2000 để thiết kế các hệ thống phân tán qua mạng internet.

### 6 Ràng Buộc Cốt Lõi Của REST:
1. **Client - Server tách biệt**: Giao diện (Client) và Dữ liệu/Lưu trữ (Server) hoàn toàn độc lập. Bạn có thể đập đi xây lại toàn bộ giao diện mà Backend không cần sửa 1 dòng code.
2. **Stateless (Phi trạng thái)**: Server không bao giờ lưu biến Session (như `HttpSession` kiểu cũ). Mỗi request từ Client gửi lên bắt buộc phải mang theo chìa khóa định danh (Token JWT).
3. **Cacheable (Khả năng lưu bộ nhớ đệm)**: Response phải thông báo rõ ràng dữ liệu này có được phép lưu cache ở trình duyệt/CDN không để giảm tải server.
4. **Uniform Interface (Giao diện đồng nhất)**: Cách đặt URL, sử dụng HTTP method, và mã lỗi phải tuân theo một quy tắc chuẩn thống nhất.
5. **Layered System (Hệ thống phân tầng)**: Client không cần biết mình đang kết nối trực tiếp với Server ứng dụng hay đang đi qua Gateway, Load Balancer, Reverse Proxy.
6. **Code on Demand (Tùy chọn)**: Cho phép server gửi code (như JavaScript) về cho client thực thi.

---

## 2.2. Quy Tắc Đặt Tên URI / Endpoint Chuẩn RESTful (Cực Kỳ Quan Trọng!)

Nhiều người mới bắt đầu thường mang tư duy gọi hàm kiểu RPC đặt vào REST API (Ví dụ: `/getRooms`, `/createNewBranch`, `/deleteEquipment?id=5`). **Đó là SAI chuẩn REST**.

👉 **Quy tắc vàng của REST**: 
- **URI đại diện cho TÀI NGUYÊN (Danh từ số nhiều - Noun)**.
- **HÀNH ĐỘNG được quyết định bởi HTTP METHOD (Động từ - Verb)**.

### So sánh cách đặt tên:
| Mục đích nghiệp vụ | ❌ Đặt Sai (Kiểu RPC/Servlet cũ) | ✅ Đặt Chuẩn RESTful |
| :--- | :--- | :--- |
| Lấy danh sách cơ sở | `GET /getAllBranches` | `GET /api/branches` |
| Lấy chi tiết cơ sở số 1 | `GET /getBranchById?id=1` | `GET /api/branches/1` |
| Tạo mới 1 cơ sở | `POST /createBranch` | `POST /api/branches` |
| Cập nhật cơ sở số 1 | `POST /updateBranch` | `PUT /api/branches/1` |
| Xóa cơ sở số 1 | `GET /deleteBranch?id=1` | `DELETE /api/branches/1` |
| Lấy danh sách phòng thuộc cơ sở 1 | `GET /getRoomsByBranchId?branchId=1` | `GET /api/branches/1/rooms` |
| Lấy đàn số 5 của cơ sở 1 | `GET /getEquipment?branchId=1&eqId=5` | `GET /api/branches/1/equipments/5` |

---

## 2.3. Bảng Phân Biệt Các HTTP Methods: Idempotent vs Safe

| Method | Thao Tác CRUD | An Toàn (Safe)? | Bất Biến (Idempotent)? | Mô Tả Nghiệp Vụ |
| :--- | :--- | :---: | :---: | :--- |
| **`GET`** | **R**ead | **CÓ** | **CÓ** | Chỉ đọc dữ liệu, gọi 1 lần hay 100 lần thì CSDL vẫn nguyên vẹn. |
| **`POST`** | **C**reate | KHÔNG | KHÔNG | Tạo mới bản ghi. Gọi 5 lần sẽ sinh ra 5 bản ghi mới trong CSDL. |
| **`PUT`** | **U**pdate (Toàn bộ) | KHÔNG | **CÓ** | Ghi đè toàn bộ thông tin tài nguyên. Gọi 1 lần hay 10 lần với cùng payload thì kết quả cuối cùng vẫn như nhau. |
| **`PATCH`** | **U**pdate (Một phần) | KHÔNG | KHÔNG | Chỉ sửa đổi một vài trường cụ thể (ví dụ chỉ đổi mỗi trạng thái `status: "ACTIVE"`). |
| **`DELETE`** | **D**elete | KHÔNG | **CÓ** | Xóa tài nguyên. Xóa lần 1 thì bản ghi mất, xóa các lần tiếp theo thì bản ghi vẫn đã mất. |

> **Khái niệm Idempotent (Tính bất biến)**: Một phương thức được gọi là *Idempotent* nếu việc bạn thực thi nó 1 lần hay nhiều lần liên tiếp với cùng dữ liệu đầu vào thì trạng thái của hệ thống vẫn cho ra **kết quả giống hệt nhau**.

---

## 2.4. Bảng Tra Cứu Mã Trạng Thái HTTP (Status Codes) Chuẩn Doanh Nghiệp

### Nhóm 2xx: Thành Công (Success)
- **`200 OK`**: Yêu cầu thành công, trả về dữ liệu (dùng cho GET, PUT).
- **`201 Created`**: Tạo mới thành công (dùng cho POST), thường đi kèm đối tượng vừa tạo.
- **`204 No Content`**: Xử lý thành công nhưng cố tình không trả về nội dung gì (dùng cho DELETE).

### Nhóm 4xx: Lỗi Do Phía Client (Client Error)
- **`400 Bad Request`**: Dữ liệu gửi lên sai định dạng, thiếu trường bắt buộc, vi phạm validation (ví dụ: tuổi âm, email sai format).
- **`401 Unauthorized`**: **Chưa đăng nhập** (Thiếu hoặc sai JWT Token trong Header).
- **`403 Forbidden`**: **Đã đăng nhập nhưng không có quyền** (Ví dụ: Học viên cố tình gọi API xóa cơ sở của Admin).
- **`404 Not Found`**: Không tìm thấy tài nguyên theo ID yêu cầu trong database.
- **`409 Conflict`**: Xung đột dữ liệu (Ví dụ: Tạo tài khoản nhưng username/email đã tồn tại).

### Nhóm 5xx: Lỗi Do Phía Server (Server Error)
- **`500 Internal Server Error`**: Code Backend bị crash ngoài ý muốn (lỗi `NullPointerException`, CSDL bị sập...).

---

# PHẦN 3: BẢNG ĐỐI CHIẾU TOÀN DIỆN VỚI KỸ THUẬT CŨ (JDBC - SERVLET - JSP)

### 1. Tầng Truy Vấn Dữ Liệu: JDBC Thuần vs Spring Data JPA

#### ❌ Thời kỳ JDBC thuần (Mệt mỏi và rủi ro):
```java
// Bạn phải tự quản lý từng dòng: mở kết nối, tạo statement, duyệt result set, đóng kết nối
Connection conn = null;
PreparedStatement ps = null;
ResultSet rs = null;
List<Room> list = new ArrayList<>();
try {
    conn = DriverManager.getConnection("jdbc:sqlserver://localhost:1433;databaseName=...", "sa", "123");
    ps = conn.prepareStatement("SELECT * FROM rooms WHERE branch_id = ?");
    ps.setLong(1, branchId);
    rs = ps.executeQuery();
    while (rs.next()) {
        Room r = new Room();
        r.setId(rs.getLong("id"));
        r.setRoomName(rs.getString("room_name"));
        r.setCapacity(rs.getInt("capacity"));
        list.add(r);
    }
} catch (SQLException e) {
    e.printStackTrace();
} finally {
    // Quên đóng một trong 3 cái này là bị tràn kết nối (Connection Leak) sập web!
    if (rs != null) rs.close();
    if (ps != null) ps.close();
    if (conn != null) conn.close();
}
```

#### ✅ Thời kỳ Spring Data JPA (Chỉ 1 dòng interface!):
```java
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByBranchId(Long branchId);
}
```
* **Sự vượt trội**: Không cần viết `SELECT`, không cần mở/đóng kết nối (HikariCP tự làm), không lo SQL Injection, tự động map cột thành Object!

---

### 2. Tầng Điều Khiển: HttpServlet vs `@RestController`

#### ❌ Thời kỳ Servlet (Ép kiểu thủ công, forward trang):
```java
@WebServlet("/rooms")
public class RoomServlet extends HttpServlet {
    private RoomService roomService = new RoomServiceImpl(); // Tự new!

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String branchIdRaw = req.getParameter("branchId");
        if (branchIdRaw == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Thiếu id");
            return;
        }
        Long branchId = Long.parseLong(branchIdRaw); // Tự parse kiểu!
        
        List<Room> rooms = roomService.getRoomsByBranch(branchId);
        req.setAttribute("rooms", rooms);
        req.getRequestDispatcher("/views/rooms.jsp").forward(req, resp); // Dính chặt vào JSP
    }
}
```

#### ✅ Thời kỳ Spring Boot `@RestController` (Rõ ràng, tinh gọn):
```java
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchFacilityController {

    private final BranchFacilityService branchFacilityService; // Spring tự tiêm!

    @GetMapping("/{branchId}/rooms")
    public ResponseEntity<List<RoomResponse>> getRoomsByBranch(@PathVariable Long branchId) {
        // Tự động map branchId từ URL
        // Tự động chuyển List<RoomResponse> thành JSON trả về client!
        return ResponseEntity.ok(branchFacilityService.getRoomsByBranch(branchId));
    }
}
```

---

### 3. Tầng Giao Diện: JSP (Server-Side Rendering) vs RESTful API + Client-Side Rendering

```text
[MÔ HÌNH CŨ: JSP]
Client (Trình duyệt) ──────── Bấm nút lọc ────────► Server (Tomcat)
                                                          │
                                                    Chạy Servlet
                                                          │
                                                    Biên dịch file JSP
                                                          │
                                                    Ghép mã HTML to đùng
Client (Trình duyệt) ◄── Nhận lại cả trang HTML ─────┘
(MÀN HÌNH BỊ NHÁY TRẮNG, TỐN BĂNG THÔNG VÌ PHẢI TẢI LẠI CẢ HEADER/FOOTER)

─────────────────────────────────────────────────────────────────────────────

[MÔ HÌNH MỚI: REST API + MODERN WEB (Dự án COM)]
Client (Trình duyệt) ──── fetch('/api/branches/1/rooms') ───► Server (Spring Boot)
                                                                    │
                                                              Chạy Service
                                                                    │
                                                              Chỉ lấy dữ liệu
Client (Trình duyệt) ◄── Chỉ nhận vài KB dữ liệu JSON ──────────────┘
(CLIENT DÙNG JS VẼ THÊM DÒNG VÀO BẢNG, MÀN HÌNH KHÔNG RELOAD, NHẸ VÀ MƯỢT MÀ!)
```

---

# PHẦN 4: BẢN ĐỒ CHUYỂN ĐỔI TƯ DUY (MINDSET SHIFT)

| Khi bạn nghĩ đến khái niệm cũ... | ...Trong Spring Boot & REST API bạn sẽ dùng: |
| :--- | :--- |
| `web.xml` | Các file cấu hình Java với `@Configuration` hoặc `application.properties`. |
| `HttpServlet` (`doGet`, `doPost`) | `@RestController`, `@GetMapping`, `@PostMapping`. |
| `request.getParameter("id")` | `@PathVariable` (lấy từ đường dẫn) hoặc `@RequestParam` (lấy từ query `?id=1`). |
| Đọc form gửi lên qua request | `@RequestBody @Valid CreateBranchRequest request`. |
| `new MyService()` | `@Autowired` hoặc `@RequiredArgsConstructor` (Dependency Injection). |
| `Connection`, `PreparedStatement` | `JpaRepository<Entity, ID>`. |
| File hiển thị `.jsp` | File HTML tĩnh gọi API qua hàm `fetch()` / `api.js` nhận dữ liệu JSON. |
| `HttpSession.setAttribute("user", ...)` | Token **JWT (JSON Web Token)** lưu ở `localStorage` của trình duyệt. |
