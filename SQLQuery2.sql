IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'course_operation_management')
BEGIN
    CREATE DATABASE course_operation_management;
END
GO

USE course_operation_management;
GO

DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS invoices;
DROP TABLE IF EXISTS learning_roadmaps;
DROP TABLE IF EXISTS test_attachments;
DROP TABLE IF EXISTS placement_tests;
DROP TABLE IF EXISTS makeup_registrations;
DROP TABLE IF EXISTS absence_requests;
DROP TABLE IF EXISTS teacher_availabilities;
DROP TABLE IF EXISTS attendances;
DROP TABLE IF EXISTS lessons;
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS classes;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS programs;
DROP TABLE IF EXISTS equipments;
DROP TABLE IF EXISTS rooms;
DROP TABLE IF EXISTS branches;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS users;
GO

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
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
);
GO

CREATE TABLE makeup_registrations (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    absence_request_id BIGINT FOREIGN KEY REFERENCES absence_requests(id),
    student_id BIGINT NOT NULL FOREIGN KEY REFERENCES students(id),
    original_lesson_id BIGINT NOT NULL FOREIGN KEY REFERENCES lessons(id),
    target_lesson_id BIGINT NOT NULL FOREIGN KEY REFERENCES lessons(id),
    status VARCHAR(30) DEFAULT 'REGISTERED',
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

INSERT INTO users (username, password, full_name, email, phone, role, status) VALUES
('admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Quản Trị Viên Hệ Thống', 'admin@talentcenter.edu.vn', '0901234567', 'ADMIN', 'ACTIVE'),
('teacher_huong', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Cô Vũ Thu Hương (GV Piano)', 'huong.vu@talentcenter.edu.vn', '0912345678', 'TEACHER', 'ACTIVE'),
('teacher_tuan', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Thầy Trần Anh Tuấn (GV Guitar)', 'tuan.tran@talentcenter.edu.vn', '0923456789', 'TEACHER', 'ACTIVE'),
('cashier_mai', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Nguyễn Thanh Mai (Thu Ngân)', 'mai.nguyen@talentcenter.edu.vn', '0934567890', 'STAFF', 'ACTIVE'),
('parent_lan', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Phụ Huynh Lê Thị Lan', 'lan.le@gmail.com', '0987654321', 'PARENT', 'ACTIVE');

INSERT INTO users (username, password, full_name, email, phone, role, status) VALUES
('admin', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Quản Trị Viên Hệ Thống', 'admin@talentcenter.edu.vn', '0901234567', 'ADMIN', 'ACTIVE'),
('teacher_huong', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Cô Vũ Thu Hương (GV Piano)', 'huong.vu@talentcenter.edu.vn', '0912345678', 'TEACHER', 'ACTIVE'),
('teacher_tuan', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Thầy Trần Anh Tuấn (GV Guitar)', 'tuan.tran@talentcenter.edu.vn', '0923456789', 'TEACHER', 'ACTIVE'),
('teacher_linh', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Cô Phạm Khánh Linh (GV Múa Nghệ Thuật)', 'linh.pham@talentcenter.edu.vn', '0933445566', 'TEACHER', 'ACTIVE'),
('teacher_long', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Thầy Hoàng Phi Long (GV Võ Thuật)', 'long.hoang@talentcenter.edu.vn', '0944556677', 'TEACHER', 'ACTIVE'),
('cashier_mai', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Nguyễn Thanh Mai (Thu Ngân)', 'mai.nguyen@talentcenter.edu.vn', '0934567890', 'STAFF', 'ACTIVE'),
('parent_lan', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Lê Thị Lan', 'lan.le@gmail.com', '0987654321', 'PARENT', 'ACTIVE'),
('parent_hung', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Phụ Huynh Trần Văn Hưng', 'hung.tran@gmail.com', '0977889900', 'PARENT', 'ACTIVE'),
('parent_hoang', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Đỗ Minh Hoàng', 'hoang.do@gmail.com', '0966778899', 'PARENT', 'ACTIVE'),
('parent_dung', '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii', N'Phụ Huynh Vũ Tiến Dũng', 'dung.vu@gmail.com', '0955667788', 'PARENT', 'ACTIVE');

INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes) VALUES
(7, N'Nguyễn Bảo Nam (Bé Bin)', '2016-05-12', N'Nam', N'Tiểu học Thực Nghiệm', N'Thích học đàn Piano cổ điển'),
(7, N'Nguyễn Mai Chi (Bé Bông)', '2019-08-20', N'Nữ', N'Mầm non Vinschool', N'Học làm quen phím đàn mầm non'),
(8, N'Trần Hoàng Long (Bé Tí)', '2017-03-15', N'Nam', N'Tiểu học Dịch Vọng B', N'Đăng ký học Guitar đệm hát thiếu nhi'),
(9, N'Đỗ Ngọc Hân (Bé Nhím)', '2019-11-05', N'Nữ', N'Mầm non Ánh Sao', N'Có năng khiếu múa, cơ thể dẻo dai tự nhiên'),
(10, N'Vũ Tuấn Kiệt (Bé Ken)', '2016-07-22', N'Nam', N'Tiểu học Nghĩa Tân', N'Thích rèn luyện thể lực và võ thuật tự vệ');

INSERT INTO branches (code, name, address, phone, email, active) VALUES
('CS01', N'Cơ Sở 1 - Cầu Giấy', N'Số 12 Khúc Thừa Dụ, Dịch Vọng, Cầu Giấy, Hà Nội', '0243888999', 'caugiay@talentcenter.edu.vn', 1),
('CS02', N'Cơ Sở 2 - Đống Đa', N'Số 85 Hào Nam, Ô Chợ Dừa, Đống Đa, Hà Nội', '0243777888', 'dongda@talentcenter.edu.vn', 1);

INSERT INTO rooms (branch_id, room_code, room_name, capacity, room_type, status, description) VALUES
(1, 'P101', N'Phòng Piano Biểu Diễn 1', 2, 'PIANO_INDIVIDUAL', 'AVAILABLE', N'Trang bị đàn Grand Piano Yamaha C3X cao cấp cách âm tốt'),
(1, 'P102', N'Phòng Piano Nhóm 1', 8, 'PIANO_GROUP', 'AVAILABLE', N'Trang bị đàn Upright Piano Kawai cho lớp nhóm'),
(1, 'P103', N'Phòng Tập Múa & Ballet 1', 15, 'DANCE_STUDIO', 'AVAILABLE', N'Trang bị sàn gỗ đàn hồi lò xo, gương ốp tường toàn phần và gióng múa chuẩn quốc tế'),
(2, 'P201', N'Phòng Guitar & Cảm Âm', 10, 'GUITAR_ROOM', 'AVAILABLE', N'Trang bị giá nhạc, âm ly và đàn guitar acoustic'),
(2, 'P202', N'Phòng Piano Thực Hành 2', 4, 'PIANO_GROUP', 'AVAILABLE', N'Trang bị 3 đàn Upright Piano cho lớp học thực hành'),
(2, 'P203', N'Võ Đường & Thể Lực Đa Năng', 20, 'MARTIAL_ARTS_DOJO', 'AVAILABLE', N'Trang bị thảm tập võ Tatami EVA chống trượt giảm chấn, bao cát, bộ đích đấm đá và giáp hộ thân');

INSERT INTO equipments (room_id, name, code, category, status, serial_number, description) VALUES
(1, N'Đàn Đại Dương Cầm Yamaha C3X', 'EQ-P01', 'GRAND_PIANO', 'GOOD', 'YAM-C3X-9988', N'Đàn Grand Piano cơ cao cấp Nhật Bản biểu diễn'),
(2, N'Đàn Upright Piano Kawai K-300', 'EQ-P02', 'UPRIGHT_PIANO', 'GOOD', 'KAW-K300-1122', N'Đàn cơ upright cho học sinh luyện ngón'),
(2, N'Đàn Piano Điện Roland RP-102', 'EQ-P03', 'KEYBOARD', 'GOOD', 'ROL-RP102-3344', N'Đàn phím cảm ứng lực tốt cho lớp sơ cấp'),
(3, N'Hệ Thống Gióng Múa Inox Đôi', 'EQ-D01', 'BALLET_BARRE', 'GOOD', 'BARRE-VN-01', N'Gióng múa ballet inox đôi điều chỉnh độ cao theo lứa tuổi'),
(4, N'Đàn Guitar Classic Yamaha C40', 'EQ-G01', 'GUITAR', 'GOOD', 'YAM-C40-5566', N'Đàn guitar thùng dây nylon tập cảm âm cho học viên'),
(5, N'Đàn Upright Piano Yamaha U3H', 'EQ-P04', 'UPRIGHT_PIANO', 'GOOD', 'YAM-U3H-7788', N'Đàn Upright Piano cơ Nhật Bản âm thanh chuẩn'),
(6, N'Thảm Tập Võ Chống Chấn Tatami EVA', 'EQ-M01', 'TATAMI_MAT', 'GOOD', 'TATAMI-50M2', N'Thảm ghép xốp EVA cao cấp dày 3cm đạt chuẩn thi đấu võ thuật'),
(6, N'Bộ Đích Đá & Bao Cát Fairtex', 'EQ-M02', 'PUNCHING_BAG', 'GOOD', 'FTX-BAG-99', N'Trang bị tập luyện đòn tay, đòn chân và thể lực');

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

INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status) VALUES
(2, 1, 1, 2, 'CL-PIA-01', N'Piano 1-1 Bé Bảo Nam', 'ONE_ON_ONE', 1, 1, '2026-10-01', '2027-01-31', N'Thứ 2 & Thứ 5 (18:00 - 19:00)', 'OPEN'),
(1, 1, 2, 2, 'CL-PIA-PRE-G01', N'Piano Mầm Non - Nhóm Sáng', 'GROUP', 8, 1, '2026-10-15', '2027-01-15', N'Thứ 7 (09:00 - 10:30)', 'OPEN'),
(2, 2, 5, 2, 'CL-PIA-G1-G02', N'Piano Grade 1 - Nhóm Tối', 'GROUP', 8, 0, '2026-10-20', '2027-02-20', N'Thứ 3 & Thứ 6 (18:30 - 19:30)', 'OPEN'),
(4, 1, 3, 4, 'CL-DAN-KIDS-01', N'Lớp Múa Thiếu Nhi & Ballet Bé Ngọc Hân', 'GROUP', 12, 1, '2026-10-05', '2027-02-05', N'Thứ 3 & Thứ 6 (17:30 - 19:00)', 'OPEN'),
(6, 2, 6, 5, 'CL-MA-BASIC-01', N'Lớp Võ Thuật Nhập Môn Bé Tuấn Kiệt', 'GROUP', 15, 1, '2026-10-06', '2027-02-06', N'Thứ 4 & Thứ 7 (18:00 - 19:30)', 'OPEN');

INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes) VALUES
(1, 1, 7, 'ENROLLED', N'Đã hoàn tất học phí khóa Piano Grade 1, xếp lớp thành công'),
(2, 2, 7, 'PENDING_PAYMENT', N'Phiếu giữ chỗ tạm thời 24h - Lớp Piano Mầm Non'),
(3, 1, 8, 'PENDING_PAYMENT', N'Phiếu giữ chỗ tạm thời 24h - Lớp Piano 1-1'),
(4, 4, 9, 'ENROLLED', N'Đã hoàn tất học phí khóa Múa Thiếu Nhi & Ballet Căn Bản sau bài test xếp lớp'),
(5, 5, 10, 'ENROLLED', N'Đã hoàn tất học phí khóa Võ Thuật Nhập Môn sau bài test thể lực');

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status) VALUES
(1, 1, 2, 1, '2026-10-05', '18:00:00', '19:00:00', N'Buổi 1: Ôn thế tay C Major', N'Bé giữ phom tay tốt', 'SCHEDULED'),
(1, 1, 2, 2, '2026-10-08', '18:00:00', '19:00:00', N'Buổi 2: Ghép 2 tay bài Canon in D', NULL, 'SCHEDULED'),
(4, 3, 4, 1, '2026-10-06', '17:30:00', '19:00:00', N'Buổi 1: Khởi động ép dẻo & 5 vị trí tay chân Ballet', N'Bé Hân dẻo và tiếp thu phom dáng rất nhanh', 'SCHEDULED'),
(5, 6, 5, 1, '2026-10-07', '18:00:00', '19:30:00', N'Buổi 1: Thế tấn trung bình tấn & Đòn đấm thẳng chính diện', N'Bé Kiệt thể lực tốt, tinh thần nghiêm túc', 'SCHEDULED');

INSERT INTO attendances (lesson_id, student_id, status, note) VALUES
(1, 1, 'PRESENT', N'Học viên đi học đúng giờ, tiếp thu tốt');

INSERT INTO teacher_availabilities (teacher_id, branch_id, day_of_week, available_date, start_time, end_time, note, is_booked) VALUES
(2, 1, 'SATURDAY', '2026-10-10', '09:00:00', '11:00:00', N'Ca rảnh sáng Thứ 7 dạy bù Piano', 0),
(4, 1, 'SUNDAY', '2026-10-11', '08:30:00', '10:30:00', N'Ca rảnh sáng Chủ Nhật kiểm tra năng khiếu Múa', 0),
(5, 2, 'SUNDAY', '2026-10-11', '14:30:00', '16:30:00', N'Ca rảnh chiều Chủ Nhật test thể lực & võ thuật', 0);

INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note) VALUES
(1, 2, 7, N'Bé Nam bị sốt siêu vi cần nghỉ ca tối Thứ 5', 'PENDING', NULL, NULL),
(1, 1, 7, N'Gia đình có việc bận đột xuất', 'APPROVED', 2, N'Đã duyệt cho bé nghỉ và xếp lịch bù');

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note) VALUES
(2, 1, 1, 2, 'REGISTERED', N'Xếp học bù ca thực hành thứ 7');

-- ============================================================================
-- PLACEMENT TESTS & ĐÁNH GIÁ NĂNG KHIẾU THEO TỪNG BỘ MÔN (PIANO, MÚA, VÕ THUẬT)
-- Tiêu chí chấm Piano: Nhịp phách, Kỹ thuật ngón, Cảm âm, Biểu cảm
-- Tiêu chí chấm Múa: Độ dẻo & Uyển chuyển, Cảm thụ âm nhạc, Phom dáng & Tư thế, Thần thái biểu diễn
-- Tiêu chí chấm Võ Thuật: Thể lực & Sức bền, Tấn pháp & Kỹ thuật đòn, Tốc độ & Phản xạ, Kỷ luật & Võ đạo
-- ============================================================================
INSERT INTO placement_tests (student_id, teacher_id, program_id, test_date, rhythm_score, technique_score, ear_training_score, expression_score, total_score, teacher_notes, recommended_level, recommended_course_id) VALUES
-- 1. Bài test Piano (Bé Bảo Nam - GV Hương)
(1, 2, 1, '2026-09-20', 8.5, 8.0, 9.0, 8.5, 8.5, N'[PIANO] Bé có năng khiếu cảm âm xuất sắc, nghe được nốt đơn và phách chuẩn. Khuyên nên học ngay Piano Grade 1.', N'Grade 1 (Sơ cấp)', 2),

-- 2. Bài test Múa & Ballet (Bé Đỗ Ngọc Hân - GV Khánh Linh)
-- rhythm_score: Cảm thụ âm nhạc (8.5) | technique_score: Độ dẻo dai (9.5) | ear_training_score: Phom dáng & Tư thế (8.5) | expression_score: Thần thái biểu diễn (9.0)
(4, 4, 3, '2026-09-21', 8.5, 9.5, 8.5, 9.0, 8.9, N'[MÚA & BALLET] Khớp hông mở rất tốt, độ dẻo bẩm sinh xuất sắc (ép dẻo dọc/ngang 180 độ), bắt nhịp nhạc nhanh và thần thái rạng rỡ. Đề xuất xếp vào khóa Múa Thiếu Nhi & Ballet Căn Bản (DAN-KIDS).', N'Căn bản (Ballet Kids)', 4),

-- 3. Bài test Võ Thuật (Bé Vũ Tuấn Kiệt - GV Phi Long)
-- rhythm_score: Thể lực & Sức bền (9.0) | technique_score: Tấn pháp & Đòn thế (8.5) | ear_training_score: Tốc độ & Phản xạ (8.5) | expression_score: Kỷ luật & Tinh thần võ đạo (9.5)
(5, 5, 4, '2026-09-22', 9.0, 8.5, 8.5, 9.5, 8.9, N'[VÕ THUẬT] Thể lực sung mãn, sức bật nhảy tốt, tấn pháp trung bình tấn vững chãi, phản xạ đòn nhanh và ý thức kỷ luật võ đạo rất cao. Đề xuất xếp vào lớp Võ Thuật Nhập Môn - Đai Trắng (MA-BASIC).', N'Đai Trắng (Khởi động)', 6);

-- Đính kèm media bài thi
INSERT INTO test_attachments (placement_test_id, file_name, file_url, media_type, duration_seconds, description) VALUES
(1, N'Video_Dan_Canon_BaoNam.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 'PIANO_VIDEO', 90, N'Video bé Nam thể hiện khả năng bấm phím bài test đầu vào Piano'),
(1, N'Ghi_Am_Cam_Am_Nhip.mp3', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'AUDIO_RECORD', 60, N'Ghi âm bài test xướng âm nốt Đồ - Rê - Mi'),
(2, N'Video_Test_DoDeo_Mua_NgocHan.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4', 'DANCE_VIDEO', 120, N'Video bài test ép dẻo, uốn cầu vồng và chuyển động theo nhịp nhạc của bé Ngọc Hân'),
(3, N'Video_Test_TheLuc_VoThuat_TuanKiet.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4', 'MARTIAL_ARTS_VIDEO', 150, N'Video kiểm tra thể lực hít đất, bật cao, tấn pháp và bài test phản xạ của bé Tuấn Kiệt');

-- Lộ trình học tập cá nhân hóa sau bài test
INSERT INTO learning_roadmaps (student_id, placement_test_id, target_goal, current_level, target_level, estimated_duration_months, milestones, notes) VALUES
(1, 1, N'Đạt chứng chỉ quốc tế ABRSM Piano Grade 1', N'Sơ cấp cơ bản', N'Grade 1 Quốc Tế', 6, N'Giai đoạn 1: Thị tấu và nhạc lý căn bản; Giai đoạn 2: Luyện tác phẩm dự thi ABRSM', N'Lộ trình đào tạo năng khiếu chuyên sâu'),
(4, 2, N'Hoàn thiện kỹ thuật dẻo & Biểu diễn bài múa Ballet thiếu nhi hoàn chỉnh', N'Dẻo tự nhiên', N'Ballet Căn Bản Grade 1', 6, N'Tháng 1-2: Rèn 5 vị trí tay chân và ép dẻo chuẩn an toàn; Tháng 3-4: Luyện xoay và nhảy nhịp điệu; Tháng 5-6: Ghép nhạc và biểu diễn báo cáo', N'Lộ trình bồi dưỡng tài năng múa nghệ thuật'),
(5, 3, N'Nắm vững 10 đòn tự vệ căn bản, bài quyền nhập môn và thi thăng Đai Vàng', N'Thể lực tốt', N'Đai Vàng Sơ Cấp', 6, N'Tháng 1-2: Tấn pháp, bộ thủ và thể lực cardio; Tháng 3-4: Đòn đấm đá liên hoàn & phản xạ tự vệ; Tháng 5-6: Luyện bài quyền số 1 và sát hạch thăng đai', N'Lộ trình rèn luyện võ thuật & thể chất');

-- Hóa đơn & Thanh toán
INSERT INTO invoices (invoice_code, student_id, enrollment_id, original_amount, discount_type, discount_amount, discount_reason, final_amount, status, due_date, notes) VALUES
('INV-2026-001', 1, 1, 4800000.00, 'PARTIAL_DISCOUNT', 960000.00, N'Ưu đãi đăng ký sớm giảm 20%', 3840000.00, 'PAID', '2026-10-01', N'Hóa đơn học phí khóa Piano Grade 1'),
('INV-2026-002', 2, 2, 3600000.00, 'NONE', 0.00, NULL, 3600000.00, 'UNPAID', DATEADD(day, 1, CAST(CURRENT_TIMESTAMP AS DATE)), N'Phiếu giữ chỗ 24h - Piano Mầm Non'),
('INV-2026-003', 3, 3, 4800000.00, 'NONE', 0.00, NULL, 4800000.00, 'UNPAID', DATEADD(day, 1, CAST(CURRENT_TIMESTAMP AS DATE)), N'Phiếu giữ chỗ 24h - Piano 1-1'),
('INV-2026-004', 4, 4, 4200000.00, 'PARTIAL_DISCOUNT', 420000.00, N'Ưu đãi học viên test đầu vào đạt xuất sắc giảm 10%', 3780000.00, 'PAID', '2026-10-05', N'Hóa đơn học phí khóa Múa Thiếu Nhi & Ballet'),
('INV-2026-005', 5, 5, 3800000.00, 'PARTIAL_DISCOUNT', 380000.00, N'Ưu đãi tân học viên võ thuật giảm 10%', 3420000.00, 'PAID', '2026-10-06', N'Hóa đơn học phí khóa Võ Thuật Nhập Môn');

INSERT INTO payments (invoice_id, payment_code, payment_method, amount, payment_date, cashier_id, note, status) VALUES
(1, 'PAY-2026-001', 'CASH_AT_DESK', 3840000.00, CURRENT_TIMESTAMP, 6, N'Phụ huynh Lan nộp tiền mặt trực tiếp tại quầy thu ngân cơ sở Cầu Giấy', 'SUCCESS'),
(4, 'PAY-2026-002', 'BANK_TRANSFER', 3780000.00, CURRENT_TIMESTAMP, 6, N'Phụ huynh Hoàng chuyển khoản học phí Múa qua VietQR', 'SUCCESS'),
(5, 'PAY-2026-003', 'BANK_TRANSFER', 3420000.00, CURRENT_TIMESTAMP, 6, N'Phụ huynh Dũng chuyển khoản học phí Võ Thuật qua VietQR', 'SUCCESS');
GO