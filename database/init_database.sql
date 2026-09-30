IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'course_operation_management')
BEGIN
    CREATE DATABASE course_operation_management;
END
GO

USE course_operation_management;
GO

-- ============================================================================
-- 2. CREATE SCHEMAS & TABLES
-- ============================================================================

CREATE TABLE users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name NVARCHAR(150) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(20),
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    avatar_url NVARCHAR(500),
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE students (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    parent_id BIGINT FOREIGN KEY REFERENCES users(id),
    full_name NVARCHAR(150) NOT NULL,
    date_of_birth DATE,
    gender NVARCHAR(20),
    school_name NVARCHAR(200),
    notes NVARCHAR(500),
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE branches (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name NVARCHAR(200) NOT NULL,
    address NVARCHAR(500) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(150),
    active BIT DEFAULT 1,
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE rooms (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    branch_id BIGINT NOT NULL FOREIGN KEY REFERENCES branches(id),
    room_code VARCHAR(50) NOT NULL,
    room_name NVARCHAR(150) NOT NULL,
    capacity INT NOT NULL,
    room_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) DEFAULT 'AVAILABLE',
    description NVARCHAR(500)
);
GO

CREATE TABLE equipments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    room_id BIGINT NOT NULL FOREIGN KEY REFERENCES rooms(id),
    name NVARCHAR(150) NOT NULL,
    code VARCHAR(50),
    category VARCHAR(50) NOT NULL,
    status VARCHAR(30) DEFAULT 'GOOD',
    serial_number VARCHAR(100),
    description NVARCHAR(500)
);
GO

CREATE TABLE programs (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name NVARCHAR(150) NOT NULL,
    description NVARCHAR(1000)
);
GO

CREATE TABLE courses (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    program_id BIGINT NOT NULL FOREIGN KEY REFERENCES programs(id),
    code VARCHAR(50) NOT NULL UNIQUE,
    name NVARCHAR(150) NOT NULL,
    level NVARCHAR(50),
    duration_weeks INT,
    total_sessions INT,
    tuition_fee DECIMAL(12,2) NOT NULL,
    description NVARCHAR(1000)
);
GO

CREATE TABLE classes (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    course_id BIGINT NOT NULL FOREIGN KEY REFERENCES courses(id),
    branch_id BIGINT NOT NULL FOREIGN KEY REFERENCES branches(id),
    room_id BIGINT FOREIGN KEY REFERENCES rooms(id),
    teacher_id BIGINT FOREIGN KEY REFERENCES users(id),
    class_code VARCHAR(50) NOT NULL UNIQUE,
    class_name NVARCHAR(150) NOT NULL,
    class_type VARCHAR(30) DEFAULT 'GROUP',
    max_students INT NOT NULL,
    current_students INT DEFAULT 0,
    start_date DATE,
    end_date DATE,
    schedule_description NVARCHAR(200),
    status VARCHAR(30) DEFAULT 'OPEN'
);
GO

CREATE TABLE enrollments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    student_id BIGINT NOT NULL FOREIGN KEY REFERENCES students(id),
    class_id BIGINT NOT NULL FOREIGN KEY REFERENCES classes(id),
    registered_by_user_id BIGINT FOREIGN KEY REFERENCES users(id),
    enrollment_date DATETIME2 DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) DEFAULT 'PENDING_PAYMENT',
    notes NVARCHAR(500)
);
GO

CREATE TABLE enrollment_requests (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    student_id BIGINT NOT NULL FOREIGN KEY REFERENCES students(id),
    class_id BIGINT NULL FOREIGN KEY REFERENCES classes(id),
    preferred_course_id BIGINT NULL FOREIGN KEY REFERENCES courses(id),
    placement_requested BIT NOT NULL DEFAULT 0,
    requested_by_user_id BIGINT NOT NULL FOREIGN KEY REFERENCES users(id),
    reviewed_by_user_id BIGINT NULL FOREIGN KEY REFERENCES users(id),
    enrollment_id BIGINT NULL FOREIGN KEY REFERENCES enrollments(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    note NVARCHAR(500),
    review_note NVARCHAR(500),
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME2 NULL,
    CONSTRAINT CK_enrollment_requests_status CHECK (status IN ('PENDING', 'WAITING_PLACEMENT', 'READY_FOR_ASSIGNMENT', 'PENDING_PAYMENT', 'APPROVED', 'REJECTED'))
);
GO

CREATE TABLE lessons (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    class_id BIGINT NOT NULL FOREIGN KEY REFERENCES classes(id),
    room_id BIGINT FOREIGN KEY REFERENCES rooms(id),
    teacher_id BIGINT FOREIGN KEY REFERENCES users(id),
    session_number INT,
    lesson_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    title NVARCHAR(200),
    lesson_note NVARCHAR(1000),
    status VARCHAR(30) DEFAULT 'SCHEDULED'
);
GO

CREATE TABLE attendances (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    lesson_id BIGINT NOT NULL FOREIGN KEY REFERENCES lessons(id),
    student_id BIGINT NOT NULL FOREIGN KEY REFERENCES students(id),
    status VARCHAR(30) DEFAULT 'PRESENT',
    note NVARCHAR(500),
    marked_at DATETIME2 DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT UQ_Lesson_Student UNIQUE (lesson_id, student_id)
);
GO

CREATE TABLE teacher_availabilities (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    teacher_id BIGINT NOT NULL FOREIGN KEY REFERENCES users(id),
    branch_id BIGINT NOT NULL FOREIGN KEY REFERENCES branches(id),
    day_of_week VARCHAR(20),
    available_date DATE,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    note NVARCHAR(255),
    is_booked BIT DEFAULT 0
);
GO

CREATE TABLE absence_requests (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    student_id BIGINT NOT NULL FOREIGN KEY REFERENCES students(id),
    lesson_id BIGINT NOT NULL FOREIGN KEY REFERENCES lessons(id),
    requested_by_user_id BIGINT FOREIGN KEY REFERENCES users(id),
    reason NVARCHAR(1000) NOT NULL,
    status VARCHAR(30) DEFAULT 'PENDING',
    approved_by_teacher_id BIGINT FOREIGN KEY REFERENCES users(id),
    review_note NVARCHAR(500),
    reviewed_at DATETIME2 NULL,
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE makeup_registrations (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    absence_request_id BIGINT FOREIGN KEY REFERENCES absence_requests(id),
    student_id BIGINT NOT NULL FOREIGN KEY REFERENCES students(id),
    original_lesson_id BIGINT NOT NULL FOREIGN KEY REFERENCES lessons(id),
    target_lesson_id BIGINT NULL FOREIGN KEY REFERENCES lessons(id),
    status VARCHAR(30) DEFAULT 'PENDING',
    note NVARCHAR(500),
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE placement_tests (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    student_id BIGINT NOT NULL FOREIGN KEY REFERENCES students(id),
    teacher_id BIGINT NOT NULL FOREIGN KEY REFERENCES users(id),
    program_id BIGINT NOT NULL FOREIGN KEY REFERENCES programs(id),
    test_date DATE NOT NULL,
    rhythm_score FLOAT,
    technique_score FLOAT,
    ear_training_score FLOAT,
    expression_score FLOAT,
    total_score FLOAT,
    teacher_notes NVARCHAR(2000),
    recommended_level NVARCHAR(100),
    recommended_course_id BIGINT FOREIGN KEY REFERENCES courses(id),
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE test_attachments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    placement_test_id BIGINT NOT NULL FOREIGN KEY REFERENCES placement_tests(id),
    file_name NVARCHAR(255) NOT NULL,
    file_url NVARCHAR(1000) NOT NULL,
    media_type VARCHAR(30) NOT NULL,
    duration_seconds INT,
    description NVARCHAR(500),
    uploaded_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE learning_roadmaps (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    student_id BIGINT NOT NULL FOREIGN KEY REFERENCES students(id),
    placement_test_id BIGINT FOREIGN KEY REFERENCES placement_tests(id),
    target_goal NVARCHAR(200) NOT NULL,
    current_level NVARCHAR(100),
    target_level NVARCHAR(100),
    estimated_duration_months INT,
    milestones NVARCHAR(MAX),
    notes NVARCHAR(1000),
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE placement_schedules (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT FOREIGN KEY REFERENCES users(id),
    student_name NVARCHAR(100),
    title NVARCHAR(150) NOT NULL,
    room_name NVARCHAR(100) NOT NULL,
    branch NVARCHAR(150),
    test_date DATETIME2 NOT NULL,
    note NVARCHAR(MAX),
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    score INT,
    recommended_level VARCHAR(30),
    teacher_note NVARCHAR(MAX),
    audio_url VARCHAR(500),
    video_url VARCHAR(500),
    image_url VARCHAR(500),
    record_url VARCHAR(500),
    teacher_id BIGINT FOREIGN KEY REFERENCES users(id),
    evaluated_at DATETIME2,
    created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE invoices (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    invoice_code VARCHAR(50) NOT NULL UNIQUE,
    student_id BIGINT NOT NULL FOREIGN KEY REFERENCES students(id),
    enrollment_id BIGINT FOREIGN KEY REFERENCES enrollments(id),
    original_amount DECIMAL(12,2) NOT NULL,
    discount_type VARCHAR(30) DEFAULT 'NONE',
    discount_amount DECIMAL(12,2) DEFAULT 0,
    discount_reason NVARCHAR(255),
    final_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30) DEFAULT 'UNPAID',
    due_date DATE,
    notes NVARCHAR(500),
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE payments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    invoice_id BIGINT NOT NULL FOREIGN KEY REFERENCES invoices(id),
    payment_code VARCHAR(50) NOT NULL UNIQUE,
    payment_method VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_date DATETIME2 DEFAULT CURRENT_TIMESTAMP,
    cashier_id BIGINT FOREIGN KEY REFERENCES users(id),
    bank_transaction_id VARCHAR(100),
    proof_image_url NVARCHAR(1000),
    note NVARCHAR(500),
    status VARCHAR(30) DEFAULT 'SUCCESS'
);
GO

-- ============================================================================
-- 3. SEED DATA
-- ============================================================================

-- 3.1. NGƯỜI DÙNG HỆ THỐNG (USERS)
-- Mật khẩu mặc định cho tất cả tài khoản: 123456
INSERT INTO users (username, password, full_name, email, phone, role, status) VALUES
('admin', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Quản Trị Viên Hệ Thống', 'admin@talentcenter.edu.vn', '0901234567', 'ADMIN', 'ACTIVE'),
('cashier_mai', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Nguyễn Thanh Mai (Thu Ngân / Giáo Vụ)', 'mai.nguyen@talentcenter.edu.vn', '0934567890', 'STAFF', 'ACTIVE'),
('teacher_huong', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Cô Vũ Thu Hương (GV Piano)', 'huong.vu@talentcenter.edu.vn', '0912345678', 'TEACHER', 'ACTIVE'),
('teacher_tuan', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Thầy Trần Anh Tuấn (GV Guitar)', 'tuan.tran@talentcenter.edu.vn', '0923456789', 'TEACHER', 'ACTIVE'),
('teacher_linh', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Cô Phạm Khánh Linh (GV Múa & Ballet)', 'linh.pham@talentcenter.edu.vn', '0933445566', 'TEACHER', 'ACTIVE'),
('teacher_long', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Thầy Hoàng Phi Long (GV Võ Thuật)', 'long.hoang@talentcenter.edu.vn', '0944556677', 'TEACHER', 'ACTIVE'),
('teacher_ha', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Cô Trần Thu Hà (GV Piano)', 'ha.tran@talentcenter.edu.vn', '0945678901', 'TEACHER', 'ACTIVE'),
('teacher_dung', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Thầy Nguyễn Văn Dũng (GV Guitar)', 'dung.nguyen@talentcenter.edu.vn', '0956789012', 'TEACHER', 'ACTIVE'),
('parent_lan', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Lê Thị Lan', 'lan.le@gmail.com', '0987654321', 'PARENT', 'ACTIVE'),
('parent_hung', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Trần Văn Hưng', 'hung.tran@gmail.com', '0977889900', 'PARENT', 'ACTIVE'),
('parent_hoang', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Đỗ Minh Hoàng', 'hoang.do@gmail.com', '0966778899', 'PARENT', 'ACTIVE'),
('parent_dung', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Vũ Tiến Dũng', 'dung.vu@gmail.com', '0955667788', 'PARENT', 'ACTIVE'),
('parent_bich', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Phạm Thị Bích', 'bich.pham@gmail.com', '0912889911', 'PARENT', 'ACTIVE'),
('parent_nam', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Vũ Văn Nam', 'nam.vu@gmail.com', '0988776655', 'PARENT', 'ACTIVE'),
('parent_trang', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Hoàng Thu Trang', 'trang.hoang@gmail.com', '0936112233', 'PARENT', 'ACTIVE');
GO

-- 3.2. DANH SÁCH HỌC VIÊN (STUDENTS)
INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes) VALUES
((SELECT id FROM users WHERE username = 'parent_lan'), N'Nguyễn Bảo Nam (Bé Bin)', '2016-05-12', N'Nam', N'Tiểu học Thực Nghiệm', N'Thích học đàn Piano cổ điển'),
((SELECT id FROM users WHERE username = 'parent_lan'), N'Nguyễn Mai Chi (Bé Bông)', '2019-08-20', N'Nữ', N'Mầm non Vinschool', N'Học làm quen phím đàn mầm non'),
((SELECT id FROM users WHERE username = 'parent_hung'), N'Trần Hoàng Long (Bé Tí)', '2017-03-15', N'Nam', N'Tiểu học Dịch Vọng B', N'Đăng ký học Guitar đệm hát thiếu nhi'),
((SELECT id FROM users WHERE username = 'parent_hoang'), N'Đỗ Ngọc Hân (Bé Nhím)', '2019-11-05', N'Nữ', N'Mầm non Ánh Sao', N'Có năng khiếu múa, cơ thể dẻo dai tự nhiên'),
((SELECT id FROM users WHERE username = 'parent_dung'), N'Vũ Tuấn Kiệt (Bé Ken)', '2016-07-22', N'Nam', N'Tiểu học Nghĩa Tân', N'Thích rèn luyện thể lực và võ thuật tự vệ'),
((SELECT id FROM users WHERE username = 'parent_bich'), N'Đỗ Minh Khang (Bé Bon)', '2019-09-12', N'Nam', N'Mầm non Sasuke Cầu Giấy', N'Bé nhanh nhẹn, thích học đàn Piano mầm non'),
((SELECT id FROM users WHERE username = 'parent_nam'), N'Lê Tuấn Kiệt (Bé Ken Lê)', '2017-11-08', N'Nam', N'Tiểu học Dịch Vọng A', N'Học làm quen phím đàn và xướng âm Guitar'),
((SELECT id FROM users WHERE username = 'parent_trang'), N'Vũ Quỳnh Anh (Bé Bống)', '2018-04-25', N'Nữ', N'Tiểu học Kim Đồng', N'Đã qua kiểm tra đầu vào năng khiếu Piano Grade 1'),
((SELECT id FROM users WHERE username = 'parent_dung'), N'Phạm Gia Huy (Bé Tom)', '2018-06-18', N'Nam', N'Tiểu học Nghĩa Tân', N'Đăng ký khóa học Piano 1-1 rèn luyện kỹ năng'),
((SELECT id FROM users WHERE username = 'parent_lan'), N'Nguyễn Trọng Anh', '2015-11-20', N'Nam', N'Tiểu học Đoàn Thị Điểm', N'Học Guitar đệm hát'),
((SELECT id FROM users WHERE username = 'parent_lan'), N'Nguyễn Hải Yến', '2017-02-14', N'Nữ', N'Tiểu học Kim Liên', N'Học Piano Sơ cấp');
GO

-- 3.3. CHI NHÁNH & PHÒNG HỌC & THIẾT BỊ (BRANCHES, ROOMS, EQUIPMENTS)
INSERT INTO branches (code, name, address, phone, email, active) VALUES
('CS01', N'Cơ Sở 1 - Cầu Giấy', N'Số 12 Khúc Thừa Dụ, Dịch Vọng, Cầu Giấy, Hà Nội', '0243888999', 'caugiay@talentcenter.edu.vn', 1),
('CS02', N'Cơ Sở 2 - Đống Đa', N'Số 85 Hào Nam, Ô Chợ Dừa, Đống Đa, Hà Nội', '0243777888', 'dongda@talentcenter.edu.vn', 1);

INSERT INTO rooms (branch_id, room_code, room_name, capacity, room_type, status, description) VALUES
(1, 'P101', N'Phòng Piano Biểu Diễn 1', 2, 'PIANO_INDIVIDUAL', 'AVAILABLE', N'Trang bị đàn Grand Piano Yamaha C3X cao cấp cách âm tốt'),
(1, 'P102', N'Phòng Piano Nhóm 1', 8, 'PIANO_GROUP', 'AVAILABLE', N'Trang bị dàn Upright Piano Kawai cho lớp nhóm'),
(1, 'P103', N'Phòng Tập Múa & Ballet 1', 15, 'DANCE_ROOM', 'AVAILABLE', N'Trang bị sàn gỗ đàn hồi lò xo, gương ốp tường toàn phần và gióng múa chuẩn quốc tế'),
(2, 'P201', N'Phòng Guitar & Cảm Âm', 10, 'GUITAR_ROOM', 'AVAILABLE', N'Trang bị giá nhạc, âm ly và đàn guitar acoustic'),
(2, 'P202', N'Phòng Piano Thực Hành 2', 6, 'PIANO_GROUP', 'AVAILABLE', N'Trang bị 3 đàn Upright Piano cho lớp học thực hành'),
(2, 'P203', N'Võ Đường & Thể Lực Đa Năng', 20, 'THEORY_ROOM', 'AVAILABLE', N'Trang bị thảm tập võ Tatami EVA chống trượt giảm chấn, bao cát, bộ đích đấm đá và giáp hộ thân');

INSERT INTO equipments (room_id, name, code, category, status, serial_number, description) VALUES
(1, N'Đàn Đại Dương Cầm Yamaha C3X', 'EQ-P01', 'GRAND_PIANO', 'GOOD', 'YAM-C3X-9988', N'Đàn Grand Piano cơ cao cấp Nhật Bản biểu diễn'),
(2, N'Đàn Upright Piano Kawai K-300', 'EQ-P02', 'UPRIGHT_PIANO', 'GOOD', 'KAW-K300-1122', N'Đàn cơ upright cho học sinh luyện ngón'),
(2, N'Đàn Piano Điện Roland RP-102', 'EQ-P03', 'KEYBOARD', 'GOOD', 'ROL-RP102-3344', N'Đàn phím cảm ứng lực tốt cho lớp sơ cấp'),
(3, N'Hệ Thống Gióng Múa Inox Đôi', 'EQ-D01', 'MIRROR', 'GOOD', 'BARRE-VN-01', N'Gióng múa ballet inox đôi điều chỉnh độ cao theo lứa tuổi'),
(4, N'Đàn Guitar Classic Yamaha C40', 'EQ-G01', 'GUITAR', 'GOOD', 'YAM-C40-5566', N'Đàn guitar thùng dây nylon tập cảm âm cho học viên'),
(5, N'Đàn Upright Piano Yamaha U3H', 'EQ-P04', 'UPRIGHT_PIANO', 'GOOD', 'YAM-U3H-7788', N'Đàn Upright Piano cơ Nhật Bản âm thanh chuẩn'),
(6, N'Thảm Tập Võ Chống Chấn Tatami EVA', 'EQ-M01', 'AUDIO', 'GOOD', 'TATAMI-50M2', N'Thảm ghép xốp EVA cao cấp dày 3cm đạt chuẩn thi đấu võ thuật'),
(6, N'Bộ Đích Đá & Bao Cát Fairtex', 'EQ-M02', 'AUDIO', 'GOOD', 'FTX-BAG-99', N'Trang bị tập luyện đòn tay, đòn chân và thể lực');
GO

-- 3.4. CHƯƠNG TRÌNH & KHÓA HỌC (PROGRAMS & COURSES)
INSERT INTO programs (code, name, description) VALUES
('PIANO', N'Bộ Môn Piano', N'Đào tạo Piano cổ điển, hiện đại và luyện thi chứng chỉ quốc tế ABRSM'),
('GUITAR', N'Bộ Môn Guitar', N'Đào tạo Guitar đệm hát và cổ điển Fingerstyle'),
('DANCE', N'Bộ Môn Múa & Nhảy Nghệ Thuật', N'Đào tạo Múa thiếu nhi, Ballet căn bản, Múa dân gian và Múa đương đại giải phóng hình thể'),
('MARTIAL_ARTS', N'Bộ Môn Võ Thuật', N'Đào tạo Taekwondo, Võ cổ truyền, rèn luyện thể lực, tấn pháp, phản xạ tự vệ và kỷ luật võ đạo');

INSERT INTO courses (program_id, code, name, level, duration_weeks, total_sessions, tuition_fee, description) VALUES
(1, 'PIA-PRE', N'Piano Mầm Non (Cảm thụ âm nhạc)', N'Khởi động', 12, 24, 3600000.00, N'Dành cho bé từ 4-6 tuổi làm quen phím đàn'),
(1, 'PIA-G1', N'Piano Sơ Cấp (Grade 1)', N'Grade 1', 16, 32, 4800000.00, N'Học tư thế ngón, nhịp phách, thị tấu và ghép 2 tay'),
(2, 'GUI-BASIC', N'Guitar Đệm Hát Cơ Bản', N'Cơ bản', 12, 24, 3200000.00, N'Dành cho học viên mới bắt đầu học bấm hợp âm và tỉa ngón'),
(3, 'DAN-KIDS', N'Múa Thiếu Nhi & Ballet Căn Bản', N'Căn bản', 16, 32, 4200000.00, N'Rèn luyện độ dẻo dai khớp xương, phom dáng chuẩn, nhịp điệu và bài múa thiếu nhi sinh động'),
(3, 'DAN-CONTEMP', N'Múa Đương Đại & Biểu Diễn', N'Nâng cao', 20, 40, 5500000.00, N'Kỹ thuật múa nâng cao, cảm thụ giai điệu sâu sắc và biểu diễn sân khấu chuyên nghiệp'),
(4, 'MA-BASIC', N'Võ Thuật Thiếu Nhi - Đai Trắng Nhập Môn', N'Khởi động', 16, 32, 3800000.00, N'Rèn luyện thể lực, tấn pháp căn bản, các thế đấm đá tự vệ và tác phong kỷ luật võ đạo'),
(4, 'MA-INTER', N'Võ Thuật Tự Vệ & Quyền Pháp', N'Trung cấp', 20, 40, 4600000.00, N'Nâng cao phản xạ tự vệ, quyền pháp, bài quyền biểu diễn và nâng đai');
GO

-- 3.5. LỚP HỌC (CLASSES)
INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status) VALUES
(2, 1, 1, (SELECT id FROM users WHERE username = 'teacher_huong'), 'CL-PIA-01', N'Piano 1-1 Bé Bảo Nam', 'ONE_ON_ONE', 1, 1, '2026-10-01', '2027-01-31', N'Thứ 2 & Thứ 5 (18:00 - 19:00)', 'OPEN'),
(1, 1, 2, (SELECT id FROM users WHERE username = 'teacher_huong'), 'CL-PIA-PRE-G01', N'Piano Mầm Non - Nhóm Sáng', 'GROUP', 8, 2, '2026-10-15', '2027-01-15', N'Thứ 7 (09:00 - 10:30)', 'OPEN'),
(2, 1, 2, (SELECT id FROM users WHERE username = 'teacher_ha'), 'CL-PIA-G1-G01', N'Piano Grade 1 - Nhóm Chiều Tối', 'GROUP', 6, 2, '2026-10-01', '2027-01-31', N'Thứ 3 & Thứ 6 (18:00 - 19:30)', 'OPEN'),
(3, 2, 4, (SELECT id FROM users WHERE username = 'teacher_tuan'), 'CL-GUI-BASIC-01', N'Guitar Đệm Hát Sáng T7-CN', 'GROUP', 8, 2, '2026-10-01', '2027-01-31', N'Thứ 7 & CN (09:00 - 10:30)', 'OPEN'),
(4, 1, 3, (SELECT id FROM users WHERE username = 'teacher_linh'), 'CL-DAN-KIDS-01', N'Lớp Múa Thiếu Nhi & Ballet Bé Ngọc Hân', 'GROUP', 12, 1, '2026-10-05', '2027-02-05', N'Thứ 3 & Thứ 6 (17:30 - 19:00)', 'OPEN'),
(6, 2, 6, (SELECT id FROM users WHERE username = 'teacher_long'), 'CL-MA-BASIC-01', N'Lớp Võ Thuật Nhập Môn Bé Tuấn Kiệt', 'GROUP', 15, 1, '2026-10-06', '2027-02-06', N'Thứ 4 & Thứ 7 (18:00 - 19:30)', 'OPEN'),
(3, 2, 4, (SELECT id FROM users WHERE username = 'teacher_dung'), 'CL-GUI-BASIC-02', N'Guitar Nhập Môn Chiều Thứ 7', 'GROUP', 8, 1, '2026-10-10', '2027-02-10', N'Thứ 7 (14:30 - 16:30)', 'OPEN'),
(2, 2, 5, (SELECT id FROM users WHERE username = 'teacher_ha'), 'CL-PIA-G1-G02', N'Piano Grade 1 - Cơ Sở Đống Đa', 'GROUP', 6, 0, '2026-10-20', '2027-02-20', N'Thứ 3 & Thứ 6 (18:30 - 19:30)', 'OPEN');
GO

-- 3.6. GHI DANH HỌC VIÊN (ENROLLMENTS)
INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes) VALUES
(1, 1, (SELECT id FROM users WHERE username = 'parent_lan'), 'ENROLLED', N'Đã hoàn tất học phí khóa Piano Grade 1, xếp lớp thành công'),
(4, 5, (SELECT id FROM users WHERE username = 'parent_hoang'), 'ENROLLED', N'Đã hoàn tất học phí khóa Múa Thiếu Nhi & Ballet Căn Bản sau bài test xếp lớp'),
(5, 6, (SELECT id FROM users WHERE username = 'parent_dung'), 'ENROLLED', N'Đã hoàn tất học phí khóa Võ Thuật Nhập Môn sau bài test thể lực'),
(3, 1, (SELECT id FROM users WHERE username = 'parent_hung'), 'PENDING_PAYMENT', N'Phiếu giữ chỗ tạm thời 24h - Lớp Piano 1-1'),
(10, 4, (SELECT id FROM users WHERE username = 'parent_lan'), 'PENDING_PAYMENT', N'Đăng ký lớp Guitar Đệm Hát Sáng T7-CN'),
(11, 3, (SELECT id FROM users WHERE username = 'parent_lan'), 'ENROLLED', N'Đã ghi danh lớp Piano Sơ Cấp 02');
GO

-- 3.7. YÊU CẦU ĐĂNG KÝ KHÓA HỌC (ENROLLMENT_REQUESTS) - PHỤC VỤ TEST LUỒNG COURSE_ENROLLMENT
-- Đầy đủ các trạng thái: WAITING_PLACEMENT, READY_FOR_ASSIGNMENT, PENDING_PAYMENT, APPROVED, REJECTED
INSERT INTO enrollment_requests (student_id, class_id, preferred_course_id, placement_requested, requested_by_user_id, reviewed_by_user_id, enrollment_id, status, note, review_note, created_at, reviewed_at) VALUES
-- 1. WAITING_PLACEMENT: Chờ làm bài test năng khiếu
(6, NULL, 1, 1, (SELECT id FROM users WHERE username = 'parent_bich'), NULL, NULL, 'WAITING_PLACEMENT', N'Bé Bon 6 tuổi, thích nghe nhạc cổ điển. Gia đình mong muốn kiểm tra cảm âm và độ linh hoạt bàn tay trước khi chọn lớp.', NULL, DATEADD(hour, -5, CURRENT_TIMESTAMP), NULL),
(7, NULL, 3, 1, (SELECT id FROM users WHERE username = 'parent_nam'), NULL, NULL, 'WAITING_PLACEMENT', N'Bé Ken Lê 8 tuổi muốn học Guitar đệm hát. Cần test khả năng bắt nhịp và cảm nhận giai điệu.', NULL, DATEADD(hour, -3, CURRENT_TIMESTAMP), NULL),

-- 2. READY_FOR_ASSIGNMENT: Đã có kết quả test hoặc không cần test, đang chờ nhân viên xếp lớp
(8, NULL, 2, 1, (SELECT id FROM users WHERE username = 'parent_trang'), (SELECT id FROM users WHERE username = 'teacher_huong'), NULL, 'READY_FOR_ASSIGNMENT', N'Bé Bống 7 tuổi đăng ký Piano Sơ Cấp (Grade 1). Đã hoàn thành test đầu vào xuất sắc.', N'Điểm test: 8.8/10. Khớp ngón tay dẻo, cảm âm nốt đơn rất tốt. Khuyên xếp vào lớp Piano Sơ Cấp K01 (Cơ sở 1 Cầu Giấy).', DATEADD(day, -1, CURRENT_TIMESTAMP), DATEADD(hour, -2, CURRENT_TIMESTAMP)),
(2, NULL, 1, 0, (SELECT id FROM users WHERE username = 'parent_lan'), NULL, NULL, 'READY_FOR_ASSIGNMENT', N'Bé Bông 5 tuổi làm quen phím đàn mầm non. Gia đình không cần test năng khiếu, mong muốn học sáng Thứ 7 tại Cầu Giấy.', NULL, DATEADD(hour, -4, CURRENT_TIMESTAMP), NULL),
(9, NULL, 3, 0, (SELECT id FROM users WHERE username = 'parent_dung'), NULL, NULL, 'READY_FOR_ASSIGNMENT', N'Bé Tom 7 tuổi đăng ký khóa Guitar Cơ Bản tại cơ sở 2 Đống Đa, ca học chiều thứ 7.', NULL, DATEADD(hour, -1, CURRENT_TIMESTAMP), NULL),

-- 3. PENDING_PAYMENT: Nhân viên đã duyệt và xếp lớp, đang chờ phụ huynh nộp học phí
(3, 1, 2, 0, (SELECT id FROM users WHERE username = 'parent_hung'), (SELECT id FROM users WHERE username = 'cashier_mai'), 4, 'PENDING_PAYMENT', N'Bé Tí đăng ký học đàn Piano 1-1.', N'Nhân viên Mai đã xếp bé vào lớp CL-PIA-01 (GV Hương). Đang chờ phụ huynh thanh toán học phí qua PayOS hoặc tiền mặt.', DATEADD(day, -2, CURRENT_TIMESTAMP), DATEADD(day, -1, CURRENT_TIMESTAMP)),
(10, 4, 3, 0, (SELECT id FROM users WHERE username = 'parent_lan'), (SELECT id FROM users WHERE username = 'cashier_mai'), 5, 'PENDING_PAYMENT', N'Bé Trọng Anh đăng ký lớp Guitar Đệm Hát.', N'Đã xếp vào lớp CL-GUI-BASIC-01. Hạn đóng học phí 24h.', DATEADD(day, -2, CURRENT_TIMESTAMP), DATEADD(day, -1, CURRENT_TIMESTAMP)),

-- 4. APPROVED: Đã hoàn tất học phí và ghi danh chính thức
(1, 1, 2, 1, (SELECT id FROM users WHERE username = 'parent_lan'), (SELECT id FROM users WHERE username = 'cashier_mai'), 1, 'APPROVED', N'Bé Bảo Nam đăng ký Piano Grade 1.', N'Đã hoàn tất thanh toán hóa đơn INV-2026-001. Ghi danh chính thức.', DATEADD(day, -5, CURRENT_TIMESTAMP), DATEADD(day, -4, CURRENT_TIMESTAMP)),
(4, 5, 4, 1, (SELECT id FROM users WHERE username = 'parent_hoang'), (SELECT id FROM users WHERE username = 'cashier_mai'), 2, 'APPROVED', N'Bé Ngọc Hân đăng ký Múa Thiếu Nhi & Ballet.', N'Đã hoàn tất thanh toán hóa đơn INV-2026-004. Ghi danh chính thức.', DATEADD(day, -5, CURRENT_TIMESTAMP), DATEADD(day, -4, CURRENT_TIMESTAMP)),
(5, 6, 6, 1, (SELECT id FROM users WHERE username = 'parent_dung'), (SELECT id FROM users WHERE username = 'cashier_mai'), 3, 'APPROVED', N'Bé Tuấn Kiệt đăng ký Võ Thuật Nhập Môn.', N'Đã hoàn tất thanh toán hóa đơn INV-2026-005. Ghi danh chính thức.', DATEADD(day, -5, CURRENT_TIMESTAMP), DATEADD(day, -4, CURRENT_TIMESTAMP)),

-- 5. REJECTED: Bị từ chối kèm lý do
(2, NULL, 2, 0, (SELECT id FROM users WHERE username = 'parent_lan'), (SELECT id FROM users WHERE username = 'cashier_mai'), NULL, 'REJECTED', N'Đăng ký khóa Piano Nâng Cao Biểu Diễn.', N'Từ chối do độ tuổi của học viên (5 tuổi) chưa phù hợp với khóa nâng cao yêu cầu kỹ năng phím chuyên sâu. Đề xuất phụ huynh chuyển sang khóa Piano Mầm Non.', DATEADD(day, -3, CURRENT_TIMESTAMP), DATEADD(day, -2, CURRENT_TIMESTAMP));
GO

-- 3.8. BUỔI HỌC (LESSONS) - PHỤC VỤ TEST LUỒNG ATTENDANCE_MAKEUP
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status) VALUES
-- Lớp 1 (CL-PIA-01: Piano 1-1 Bé Bảo Nam - Cô Hương)
(1, 1, 3, 1, '2026-10-05', '18:00:00', '19:00:00', N'Buổi 1: Ôn thế tay C Major', N'Bé giữ phom tay tốt, thị tấu nốt chuẩn', 'SCHEDULED'),
(1, 1, 3, 2, '2026-10-08', '18:00:00', '19:00:00', N'Buổi 2: Ghép 2 tay bài Canon in D', N'Buổi học phụ huynh có đơn xin nghỉ phép do ốm', 'SCHEDULED'),
(1, 1, 3, 3, '2026-10-12', '18:00:00', '19:00:00', N'Buổi 3: Luyện ngón Hanon bài 1 & 2', NULL, 'SCHEDULED'),
(1, 1, 3, 4, '2026-10-15', '18:00:00', '19:00:00', N'Buổi 4: Kỹ thuật Legato & Staccato', NULL, 'SCHEDULED'),

-- Lớp 2 (CL-PIA-PRE-G01: Piano Mầm Non - Nhóm Sáng T7)
(2, 2, 3, 1, '2026-10-10', '09:00:00', '10:30:00', N'Buổi 1: Làm quen phím đàn Đồ - Rê - Mi', N'Các bé làm quen âm thanh và nhịp vỗ tay', 'SCHEDULED'),
(2, 2, 3, 2, '2026-10-17', '09:00:00', '10:30:00', N'Buổi 2: Nhận biết phím đen và phím trắng', NULL, 'SCHEDULED'),

-- Lớp 3 (CL-PIA-G1-G01: Piano Grade 1 - Cô Hà)
(3, 2, 7, 1, '2026-10-06', '18:00:00', '19:30:00', N'Buổi 1: Nhịp 4/4 và Gam Đô Trưởng', N'Ca học có thể tiếp nhận học viên học bù', 'SCHEDULED'),
(3, 2, 7, 2, '2026-10-09', '18:00:00', '19:30:00', N'Buổi 2: Tác phẩm Sonatina Op.36 No.1', NULL, 'SCHEDULED'),
(3, 2, 7, 3, '2026-10-13', '18:00:00', '19:30:00', N'Buổi 3: Kỹ thuật đạp Pedal vang âm', N'Ca học trống 2 chỗ cho ca bù', 'SCHEDULED'),

-- Lớp 4 (CL-GUI-BASIC-01: Guitar Đệm Hát - Thầy Tuấn)
(4, 4, 4, 1, '2026-10-03', '09:00:00', '10:30:00', N'Buổi 1: Tư thế ôm đàn & Bấm hợp âm C - Am', N'Buổi có đơn xin nghỉ phép đột xuất', 'SCHEDULED'),
(4, 4, 4, 2, '2026-10-04', '09:00:00', '10:30:00', N'Buổi 2: Luyện chuyển hợp âm F - G', NULL, 'SCHEDULED'),
(4, 4, 4, 3, '2026-10-10', '09:00:00', '10:30:00', N'Buổi 3: Điệu Disco & Ballad căn bản', N'Ca học nhận học viên học bù', 'SCHEDULED'),

-- Lớp 5 (CL-DAN-KIDS-01: Múa Thiếu Nhi & Ballet - Cô Linh)
(5, 3, 5, 1, '2026-10-06', '17:30:00', '19:00:00', N'Buổi 1: Khởi động ép dẻo & 5 vị trí tay chân Ballet', N'Bé Hân dẻo và tiếp thu phom dáng rất nhanh', 'SCHEDULED'),
(5, 3, 5, 2, '2026-10-09', '17:30:00', '19:00:00', N'Buổi 2: Kỹ thuật xoay Plie & Tendu', N'Bé Hân xin nghỉ do trẹo chân nhẹ', 'SCHEDULED'),

-- Lớp 6 (CL-MA-BASIC-01: Võ Thuật Nhập Môn - Thầy Long)
(6, 6, 6, 1, '2026-10-07', '18:00:00', '19:30:00', N'Buổi 1: Thế tấn trung bình tấn & Đòn đấm thẳng chính diện', N'Bé Kiệt thể lực tốt, tinh thần nghiêm túc', 'SCHEDULED'),
(6, 6, 6, 2, '2026-10-10', '18:00:00', '19:30:00', N'Buổi 2: Đòn đá tống trước Ap Chagi & Bộ thủ tự vệ', NULL, 'SCHEDULED');
GO

-- 3.9. ĐIỂM DANH (ATTENDANCES)
INSERT INTO attendances (lesson_id, student_id, status, note) VALUES
(1, 1, 'PRESENT', N'Học viên đi học đúng giờ, tiếp thu bài tốt'),
(11, 4, 'PRESENT', N'Bé Hân đi học chăm chỉ, phom dáng dẻo'),
(13, 5, 'PRESENT', N'Bé Kiệt thể lực rất tốt, đòn đấm dứt khoát');
GO

-- 3.10. LỊCH RẢNH CỦA GIÁO VIÊN (TEACHER_AVAILABILITIES) - ĐIỀU PHỐI HỌC BÙ
INSERT INTO teacher_availabilities (teacher_id, branch_id, day_of_week, available_date, start_time, end_time, note, is_booked) VALUES
(3, 1, 'SATURDAY', '2026-10-10', '09:00:00', '11:00:00', N'Cô Hương rảnh sáng Thứ 7 dạy bù Piano cá nhân tại P101', 0),
(3, 1, 'THURSDAY', '2026-10-15', '14:00:00', '16:00:00', N'Cô Hương rảnh chiều Thứ 5 dạy bù kỹ thuật ngón Piano', 0),
(4, 2, 'SUNDAY', '2026-10-11', '08:30:00', '10:30:00', N'Thầy Tuấn rảnh sáng Chủ Nhật kèm bù hợp âm Guitar', 0),
(4, 2, 'SATURDAY', '2026-10-17', '14:30:00', '16:30:00', N'Thầy Tuấn rảnh chiều Thứ 7 dạy bù nhịp điệu Guitar', 0),
(5, 1, 'SUNDAY', '2026-10-11', '08:30:00', '10:30:00', N'Cô Linh rảnh sáng Chủ Nhật kiểm tra & bù bài Múa Ballet', 0),
(6, 2, 'SUNDAY', '2026-10-11', '14:30:00', '16:30:00', N'Thầy Long rảnh chiều Chủ Nhật luyện tấn pháp & võ thuật', 0),
(7, 1, 'WEDNESDAY', '2026-10-14', '18:30:00', '20:30:00', N'Cô Hà rảnh tối Thứ 4 dạy bù Piano Sơ cấp', 0);
GO

-- 3.11. ĐƠN XIN NGHỈ HỌC (ABSENCE_REQUESTS)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at) VALUES
-- 1. Đơn PENDING: Chờ giáo viên duyệt
(1, 2, (SELECT id FROM users WHERE username = 'parent_lan'), N'Bé Nam bị sốt siêu vi 39 độ, bác sĩ chỉ định nghỉ ngơi 3 ngày, xin phép cô cho bé nghỉ ca tối Thứ 5.', 'PENDING', NULL, NULL, NULL, DATEADD(hour, -4, CURRENT_TIMESTAMP)),
(3, 7, (SELECT id FROM users WHERE username = 'parent_hung'), N'Gia đình có việc hiếu đột xuất ở quê Nam Định cuối tuần, xin phép thầy cho bé Long nghỉ ca sáng Thứ 7.', 'PENDING', NULL, NULL, NULL, DATEADD(hour, -8, CURRENT_TIMESTAMP)),
(4, 12, (SELECT id FROM users WHERE username = 'parent_hoang'), N'Bé Hân bị trẹo nhẹ cổ chân trong giờ thể dục tại trường, xin phép nghỉ 1 buổi múa để hồi phục khớp.', 'PENDING', NULL, NULL, NULL, DATEADD(hour, -2, CURRENT_TIMESTAMP)),
(5, 14, (SELECT id FROM users WHERE username = 'parent_dung'), N'Trùng lịch tham dự kỳ thi Robothon cấp trường của bé Kiệt vào chiều Thứ 7, xin phép thầy duyệt nghỉ ca võ thuật.', 'PENDING', NULL, NULL, NULL, DATEADD(hour, -1, CURRENT_TIMESTAMP)),

-- 2. Đơn APPROVED: Đã duyệt hợp lệ, chờ hoặc đã xếp ca bù
(1, 1, (SELECT id FROM users WHERE username = 'parent_lan'), N'Bé bị cảm cúm mùa có đơn thuốc của phòng khám, xin phép nghỉ buổi 1.', 'APPROVED', (SELECT id FROM users WHERE username = 'teacher_huong'), N'Đã xác nhận đơn thuốc của bé, đồng ý phê duyệt đơn nghỉ và hỗ trợ xếp lịch học bù.', DATEADD(day, -2, CURRENT_TIMESTAMP), DATEADD(day, -3, CURRENT_TIMESTAMP)),
(4, 11, (SELECT id FROM users WHERE username = 'parent_hoang'), N'Gia đình bận việc gia đình báo trước 24h.', 'APPROVED', (SELECT id FROM users WHERE username = 'teacher_linh'), N'Đồng ý cho bé nghỉ phép đúng quy chế báo trước, xếp ca bù sáng Chủ Nhật.', DATEADD(day, -3, CURRENT_TIMESTAMP), DATEADD(day, -4, CURRENT_TIMESTAMP)),

-- 3. Đơn REJECTED: Từ chối do báo quá muộn hoặc không hợp lệ
(10, 8, (SELECT id FROM users WHERE username = 'parent_lan'), N'Bé ngủ quên không kịp đến lớp Guitar.', 'REJECTED', (SELECT id FROM users WHERE username = 'teacher_tuan'), N'Từ chối do phụ huynh báo sau khi giờ học đã kết thúc 2 tiếng. Quy chế học viện yêu cầu báo trước tối thiểu 12 tiếng.', DATEADD(day, -1, CURRENT_TIMESTAMP), DATEADD(day, -2, CURRENT_TIMESTAMP));
GO

-- 3.12. BẢN GHI ĐĂNG KÝ HỌC BÙ (MAKEUP_REGISTRATIONS)
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at) VALUES
-- 1. Chờ xếp ca bù (target_lesson_id NULL)
(5, 1, 1, NULL, 'PENDING', N'Đang chờ giáo viên và phụ huynh chốt lịch học bù phù hợp trong tuần.', DATEADD(day, -3, CURRENT_TIMESTAMP)),

-- 2. Đã xếp vào ca bù cụ thể (SCHEDULED / REGISTERED)
(6, 4, 11, 12, 'SCHEDULED', N'Đã xếp học bù vào ca Múa của Cô Linh tại P103.', DATEADD(day, -4, CURRENT_TIMESTAMP)),
(NULL, 3, 7, 9, 'REGISTERED', N'Xếp học bù ghép vào ca thực hành Guitar sáng Thứ 7 P201.', DATEADD(day, -1, CURRENT_TIMESTAMP));
GO

-- 3.13. ĐÁNH GIÁ NĂNG KHIẾU (PLACEMENT_TESTS, ATTACHMENTS, ROADMAPS)
INSERT INTO placement_tests (student_id, teacher_id, program_id, test_date, rhythm_score, technique_score, ear_training_score, expression_score, total_score, teacher_notes, recommended_level, recommended_course_id) VALUES
-- 1. Test Piano (Bé Bảo Nam - GV Hương)
(1, (SELECT id FROM users WHERE username = 'teacher_huong'), 1, '2026-09-20', 8.5, 8.0, 9.0, 8.5, 8.5, N'[PIANO] Bé có năng khiếu cảm âm xuất sắc, nghe được nốt đơn và phách chuẩn. Khuyên nên học ngay Piano Grade 1.', N'Grade 1 (Sơ cấp)', 2),

-- 2. Test Múa & Ballet (Bé Đỗ Ngọc Hân - GV Khánh Linh)
(4, (SELECT id FROM users WHERE username = 'teacher_linh'), 3, '2026-09-21', 8.5, 9.5, 8.5, 9.0, 8.9, N'[MÚA & BALLET] Khớp hông mở rất tốt, độ dẻo bẩm sinh xuất sắc (ép dẻo 180 độ), bắt nhịp nhạc nhanh và thần thái rạng rỡ. Đề xuất xếp vào khóa Múa Thiếu Nhi & Ballet Căn Bản (DAN-KIDS).', N'Căn bản (Ballet Kids)', 4),

-- 3. Test Võ Thuật (Bé Vũ Tuấn Kiệt - GV Phi Long)
(5, (SELECT id FROM users WHERE username = 'teacher_long'), 4, '2026-09-22', 9.0, 8.5, 8.5, 9.5, 8.9, N'[VÕ THUẬT] Thể lực sung mãn, sức bật nhảy tốt, tấn pháp trung bình tấn vững chãi, phản xạ đòn nhanh và ý thức kỷ luật võ đạo rất cao. Đề xuất xếp vào lớp Võ Thuật Nhập Môn - Đai Trắng (MA-BASIC).', N'Đai Trắng (Khởi động)', 6),

-- 4. Test Piano Mới (Bé Vũ Quỳnh Anh - GV Hương)
(8, (SELECT id FROM users WHERE username = 'teacher_huong'), 1, '2026-09-28', 9.0, 8.5, 9.0, 8.5, 8.8, N'[PIANO] Phản xạ nhận biết cao độ nốt rất nhanh, ngón tay dài khéo léo, tư thế ngồi chuẩn. Rất phù hợp với chương trình Piano Sơ Cấp Grade 1.', N'Grade 1 (Sơ cấp)', 2);

INSERT INTO test_attachments (placement_test_id, file_name, file_url, media_type, duration_seconds, description) VALUES
(1, N'Video_Dan_Canon_BaoNam.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 'PIANO_VIDEO', 90, N'Video bé Nam thể hiện khả năng bấm phím bài test đầu vào Piano'),
(1, N'Ghi_Am_Cam_Am_Nhip.mp3', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'AUDIO_RECORD', 60, N'Ghi âm bài test xướng âm nốt Đồ - Rê - Mi'),
(2, N'Video_Test_DoDeo_Mua_NgocHan.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4', 'PIANO_VIDEO', 120, N'Video bài test ép dẻo, uốn cầu vồng và chuyển động theo nhịp nhạc của bé Ngọc Hân'),
(3, N'Video_Test_TheLuc_VoThuat_TuanKiet.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4', 'PIANO_VIDEO', 150, N'Video kiểm tra thể lực hít đất, bật cao, tấn pháp và bài test phản xạ của bé Tuấn Kiệt'),
(4, N'Video_Test_Piano_QuynhAnh.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4', 'PIANO_VIDEO', 105, N'Video bài test thị tấu và chạy gam Đô Trưởng của bé Quỳnh Anh');

INSERT INTO learning_roadmaps (student_id, placement_test_id, target_goal, current_level, target_level, estimated_duration_months, milestones, notes) VALUES
(1, 1, N'Đạt chứng chỉ quốc tế ABRSM Piano Grade 1', N'Sơ cấp cơ bản', N'Grade 1 Quốc Tế', 6, N'Giai đoạn 1: Thị tấu và nhạc lý căn bản; Giai đoạn 2: Luyện tác phẩm dự thi ABRSM', N'Lộ trình đào tạo năng khiếu chuyên sâu'),
(4, 2, N'Hoàn thiện kỹ thuật dẻo & Biểu diễn bài múa Ballet thiếu nhi hoàn chỉnh', N'Dẻo tự nhiên', N'Ballet Căn Bản Grade 1', 6, N'Tháng 1-2: Rèn 5 vị trí tay chân và ép dẻo chuẩn an toàn; Tháng 3-4: Luyện xoay và nhảy nhịp điệu; Tháng 5-6: Ghép nhạc và biểu diễn báo cáo', N'Lộ trình bồi dưỡng tài năng múa nghệ thuật'),
(5, 3, N'Nắm vững 10 đòn tự vệ căn bản, bài quyền nhập môn và thi thăng Đai Vàng', N'Thể lực tốt', N'Đai Vàng Sơ Cấp', 6, N'Tháng 1-2: Tấn pháp, bộ thủ và thể lực cardio; Tháng 3-4: Đòn đấm đá liên hoàn & phản xạ tự vệ; Tháng 5-6: Luyện bài quyền số 1 và sát hạch thăng đai', N'Lộ trình rèn luyện võ thuật & thể chất'),
(8, 4, N'Hoàn thành chương trình Piano Grade 1 và biểu diễn báo cáo cuối khóa', N'Có năng khiếu', N'Hoàn thành Grade 1', 4, N'Tháng 1: Nhạc lý & thế ngón chuẩn; Tháng 2: Ghép 2 tay bài ngắn; Tháng 3: Tác phẩm cổ điển; Tháng 4: Báo cáo sân khấu', N'Lộ trình đào tạo tăng tốc tài năng');
GO

-- 3.14. LỊCH & ĐÁNH GIÁ NĂNG KHIẾU THEO MODULE PLACEMENT_TEST
INSERT INTO placement_schedules (user_id, student_name, title, room_name, branch, test_date, note, status, score, recommended_level, teacher_note, video_url, teacher_id, evaluated_at) VALUES
(
    (SELECT id FROM users WHERE username = 'parent_lan'),
    N'Nguyễn Bảo Nam (Bé Bin)',
    N'Khảo sát năng khiếu cảm âm & phím đàn Piano đầu vào',
    N'Phòng Piano Biểu Diễn 1 (P101)',
    N'Cơ Sở 1 - Cầu Giấy',
    DATEADD(day, -5, CURRENT_TIMESTAMP),
    N'Bé 8 tuổi, thích học nhạc cổ điển, đã tự tập một số nốt cơ bản tại nhà.',
    'COMPLETED',
    85,
    'BEGINNER',
    N'Bé có độ nhạy cảm âm xuất sắc, nghe được nốt đơn và bắt nhịp phách rất chuẩn. Ngón tay dẻo và tư thế ngồi tốt. Rất phù hợp với chương trình Piano Sơ Cấp (Grade 1).',
    'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
    (SELECT id FROM users WHERE username = 'teacher_huong'),
    DATEADD(day, -4, CURRENT_TIMESTAMP)
),
(
    (SELECT id FROM users WHERE username = 'parent_trang'),
    N'Vũ Quỳnh Anh (Bé Bống)',
    N'Kiểm tra thị tấu và phản xạ phím đàn Piano Grade 1',
    N'Phòng Piano Nhóm 1 (P102)',
    N'Cơ Sở 1 - Cầu Giấy',
    DATEADD(day, -2, CURRENT_TIMESTAMP),
    N'Gia đình mong muốn kiểm tra xem bé có thể vào thẳng lớp Grade 1 hay cần qua mầm non.',
    'COMPLETED',
    88,
    'BEGINNER',
    N'Bé 7 tuổi tiếp thu rất nhanh, chạy gam Đô Trưởng mượt mà, xướng âm chuẩn xác. Khuyên nên xếp ngay vào lớp Piano Sơ Cấp K01.',
    'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
    (SELECT id FROM users WHERE username = 'teacher_huong'),
    DATEADD(day, -1, CURRENT_TIMESTAMP)
),
(
    (SELECT id FROM users WHERE username = 'parent_bich'),
    N'Đỗ Minh Khang (Bé Bon)',
    N'Đánh giá năng khiếu cảm thụ âm nhạc mầm non',
    N'Phòng Piano Nhóm 1 (P102)',
    N'Cơ Sở 1 - Cầu Giấy',
    DATEADD(day, 2, CURRENT_TIMESTAMP),
    N'Bé 6 tuổi, chuẩn bị tham gia test thử vào sáng Thứ 7 tuần này.',
    'SCHEDULED',
    NULL,
    NULL,
    NULL,
    NULL,
    (SELECT id FROM users WHERE username = 'teacher_huong'),
    NULL
),
(
    (SELECT id FROM users WHERE username = 'parent_nam'),
    N'Lê Tuấn Kiệt (Bé Ken Lê)',
    N'Khảo sát cảm âm & lực bấm ngón đàn Guitar',
    N'Phòng Guitar & Cảm Âm (P201)',
    N'Cơ Sở 2 - Đống Đa',
    DATEADD(day, 3, CURRENT_TIMESTAMP),
    N'Bé 8 tuổi muốn học đệm hát, cần test nhịp phách và khả năng ôm đàn.',
    'SCHEDULED',
    NULL,
    NULL,
    NULL,
    NULL,
    (SELECT id FROM users WHERE username = 'teacher_tuan'),
    NULL
);
GO

-- 3.14. HÓA ĐƠN & THANH TOÁN (INVOICES & PAYMENTS)
INSERT INTO invoices (invoice_code, student_id, enrollment_id, original_amount, discount_type, discount_amount, discount_reason, final_amount, status, due_date, notes) VALUES
-- 1. Các hóa đơn đã thanh toán thành công (PAID)
('INV-2026-001', 1, 1, 4800000.00, 'PARTIAL_DISCOUNT', 960000.00, N'Ưu đãi đăng ký sớm giảm 20%', 3840000.00, 'PAID', '2026-10-01', N'Hóa đơn học phí khóa Piano Grade 1 Bé Bảo Nam'),
('INV-2026-004', 4, 2, 4200000.00, 'PARTIAL_DISCOUNT', 420000.00, N'Ưu đãi học viên test đầu vào đạt xuất sắc giảm 10%', 3780000.00, 'PAID', '2026-10-05', N'Hóa đơn học phí khóa Múa Thiếu Nhi & Ballet Bé Ngọc Hân'),
('INV-2026-005', 5, 3, 3800000.00, 'PARTIAL_DISCOUNT', 380000.00, N'Ưu đãi tân học viên võ thuật giảm 10%', 3420000.00, 'PAID', '2026-10-06', N'Hóa đơn học phí khóa Võ Thuật Nhập Môn Bé Tuấn Kiệt'),

-- 2. Các hóa đơn đang chờ thanh toán (UNPAID) - Phục vụ test thu ngân tại quầy & quét PayOS VietQR
('INV-2026-002', 2, NULL, 3600000.00, 'NONE', 0.00, NULL, 3600000.00, 'UNPAID', DATEADD(day, 3, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí khóa Piano Mầm Non Bé Mai Chi (Bé Bông)'),
('INV-2026-003', 3, 4, 4800000.00, 'NONE', 0.00, NULL, 4800000.00, 'UNPAID', DATEADD(day, 2, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí lớp Piano 1-1 Bé Hoàng Long'),
('INV-2026-006', 6, NULL, 3600000.00, 'NONE', 0.00, NULL, 3600000.00, 'UNPAID', DATEADD(day, 5, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí khóa Piano Mầm Non Bé Đỗ Minh Khang'),
('INV-2026-007', 7, NULL, 3200000.00, 'NONE', 0.00, NULL, 3200000.00, 'UNPAID', DATEADD(day, 5, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí khóa Guitar Đệm Hát Bé Lê Tuấn Kiệt'),
('INV-2026-008', 8, NULL, 4800000.00, 'PARTIAL_DISCOUNT', 480000.00, N'Ưu đãi điểm test xuất sắc giảm 10%', 4320000.00, 'UNPAID', DATEADD(day, 7, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí khóa Piano Grade 1 Bé Vũ Quỳnh Anh'),
('INV-2026-009', 9, NULL, 3200000.00, 'NONE', 0.00, NULL, 3200000.00, 'UNPAID', DATEADD(day, 7, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí lớp Guitar Cơ Bản Bé Phạm Gia Huy'),
('INV-2026-010', 10, 5, 3200000.00, 'NONE', 0.00, NULL, 3200000.00, 'UNPAID', DATEADD(day, 2, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí lớp Guitar Đệm Hát Bé Nguyễn Trọng Anh');

INSERT INTO payments (invoice_id, payment_code, payment_method, amount, payment_date, cashier_id, note, status) VALUES
(1, 'PAY-2026-001', 'CASH_AT_DESK', 3840000.00, CURRENT_TIMESTAMP, (SELECT id FROM users WHERE username = 'cashier_mai'), N'Phụ huynh Lan nộp tiền mặt trực tiếp tại quầy thu ngân cơ sở Cầu Giấy', 'SUCCESS'),
(2, 'PAY-2026-002', 'BANK_TRANSFER', 3780000.00, CURRENT_TIMESTAMP, (SELECT id FROM users WHERE username = 'cashier_mai'), N'Phụ huynh Hoàng chuyển khoản học phí Múa qua VietQR', 'SUCCESS'),
(3, 'PAY-2026-003', 'BANK_TRANSFER', 3420000.00, CURRENT_TIMESTAMP, (SELECT id FROM users WHERE username = 'cashier_mai'), N'Phụ huynh Dũng chuyển khoản học phí Võ Thuật qua VietQR', 'SUCCESS');
GO
