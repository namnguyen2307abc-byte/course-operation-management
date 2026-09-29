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
DROP TABLE IF EXISTS placement_schedules;
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
    subject VARCHAR(50),
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

CREATE TABLE placement_schedules (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    student_name NVARCHAR(150) NOT NULL,
    title NVARCHAR(255) NOT NULL,
    room_name NVARCHAR(100) NOT NULL,
    branch NVARCHAR(150) NOT NULL,
    subject VARCHAR(50) NOT NULL,
    test_date DATETIME2 NOT NULL,
    note NVARCHAR(1000),
    status VARCHAR(30) DEFAULT 'SCHEDULED',
    score INT,
    recommended_level VARCHAR(30),
    teacher_note NVARCHAR(2000),
    audio_url NVARCHAR(1000),
    video_url NVARCHAR(1000),
    image_url NVARCHAR(1000),
    record_url NVARCHAR(1000),
    evaluated_by BIGINT FOREIGN KEY REFERENCES users(id),
    evaluated_at DATETIME2,
    parent_id BIGINT FOREIGN KEY REFERENCES users(id),
    created_at DATETIME2 DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME2 DEFAULT CURRENT_TIMESTAMP
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

INSERT INTO users (username, password, full_name, email, phone, role, subject, status) VALUES
('admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Quản Trị Viên Hệ Thống', 'admin@talentcenter.edu.vn', '0901234567', 'ADMIN', NULL, 'ACTIVE'),
('teacher_huong', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Cô Vũ Thu Hương (GV Đàn)', 'huong.vu@talentcenter.edu.vn', '0912345678', 'TEACHER', 'DAN', 'ACTIVE'),
('teacher_tuan', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Thầy Trần Anh Tuấn (GV Võ)', 'tuan.tran@talentcenter.edu.vn', '0923456789', 'TEACHER', 'VO', 'ACTIVE'),
('teacher_hung', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Cô Nguyễn Mai Phương (GV Múa)', 'phuong.nguyen@talentcenter.edu.vn', '0933445566', 'TEACHER', 'MUA', 'ACTIVE'),
('cashier_mai', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Nguyễn Thanh Mai (Thu Ngân)', 'mai.nguyen@talentcenter.edu.vn', '0934567890', 'STAFF', NULL, 'ACTIVE'),
('parent_lan', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Phụ Huynh Lê Thị Lan', 'lan.le@gmail.com', '0987654321', 'PARENT', NULL, 'ACTIVE'),
('parent_hung', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Phụ Huynh Trần Văn Hưng', 'hung.tran@gmail.com', '0977889900', 'PARENT', NULL, 'ACTIVE'),
('parent_hoang', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Phụ Huynh Đỗ Minh Hoàng', 'hoang.do@gmail.com', '0966778899', 'PARENT', NULL, 'ACTIVE'),
('parent_dung', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Phụ Huynh Vũ Tiến Dũng', 'dung.vu@gmail.com', '0955667788', 'PARENT', NULL, 'ACTIVE');

INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes) VALUES
(6, N'Nguyễn Bảo Nam (Bé Bin)', '2016-05-12', N'Nam', N'Tiểu học Thực Nghiệm', N'Thích học Đàn Piano & Organ'),
(6, N'Nguyễn Mai Chi (Bé Bông)', '2019-08-20', N'Nữ', N'Mầm non Vinschool', N'Học làm quen phím đàn mầm non'),
(7, N'Trần Hoàng Long (Bé Tí)', '2017-03-15', N'Nam', N'Tiểu học Dịch Vọng B', N'Đăng ký học Đàn Guitar đệm hát thiếu nhi'),
(8, N'Đỗ Ngọc Hân (Bé Nhím)', '2019-11-05', N'Nữ', N'Mầm non Ánh Sao', N'Có năng khiếu múa, cơ thể dẻo dai tự nhiên'),
(9, N'Vũ Tuấn Kiệt (Bé Ken)', '2016-07-22', N'Nam', N'Tiểu học Nghĩa Tân', N'Thích rèn luyện thể lực và võ thuật tự vệ');

INSERT INTO branches (code, name, address, phone, email, active) VALUES
('CS01', N'Cơ Sở 1 - Cầu Giấy', N'Số 12 Khúc Thừa Dụ, Dịch Vọng, Cầu Giấy, Hà Nội', '0243888999', 'caugiay@talentcenter.edu.vn', 1),
('CS02', N'Cơ Sở 2 - Đống Đa', N'Số 85 Hào Nam, Ô Chợ Dừa, Đống Đa, Hà Nội', '0243777888', 'dongda@talentcenter.edu.vn', 1);

INSERT INTO rooms (branch_id, room_code, room_name, capacity, room_type, status, description) VALUES
(1, 'P101', N'Phòng Đàn Biểu Diễn 1', 2, 'PIANO_INDIVIDUAL', 'AVAILABLE', N'Trang bị đàn Grand Piano Yamaha C3X cao cấp cách âm tốt'),
(1, 'P102', N'Phòng Đàn Nhóm 1', 8, 'PIANO_GROUP', 'AVAILABLE', N'Trang bị đàn Upright Piano & Keyboard cho lớp nhóm'),
(1, 'P103', N'Phòng Tập Múa & Ballet 1', 15, 'DANCE_STUDIO', 'AVAILABLE', N'Trang bị sàn gỗ đàn hồi lò xo, gương ốp tường toàn phần và gióng múa chuẩn quốc tế'),
(2, 'P201', N'Phòng Đàn & Cảm Âm', 10, 'GUITAR_ROOM', 'AVAILABLE', N'Trang bị giá nhạc, âm ly và đàn guitar acoustic'),
(2, 'P202', N'Phòng Đàn Thực Hành 2', 4, 'PIANO_GROUP', 'AVAILABLE', N'Trang bị đàn Upright Piano cho lớp học thực hành'),
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
('DAN', N'Bộ Môn Đàn (Piano, Guitar, Organ)', N'Đào tạo nhạc cụ, cảm thụ âm nhạc, nhịp phách, kỹ thuật ngón và thị tấu'),
('MUA', N'Bộ Môn Múa & Nhảy Nghệ Thuật', N'Đào tạo Múa thiếu nhi, Ballet căn bản, Múa dân gian và Múa đương đại giải phóng hình thể'),
('VO', N'Bộ Môn Võ Thuật', N'Đào tạo Taekwondo, Võ cổ truyền, rèn luyện thể lực, tấn pháp, phản xạ tự vệ và kỷ luật võ đạo');

INSERT INTO courses (program_id, code, name, level, duration_weeks, total_sessions, tuition_fee, description) VALUES
(1, 'DAN-PRE', N'Đàn Mầm Non (Cảm Thụ Âm Nhạc)', N'Khởi động', 12, 24, 3600000.00, N'Dành cho bé từ 4-6 tuổi làm quen phím đàn và xướng âm'),
(1, 'DAN-G1', N'Đàn Sơ Cấp (Grade 1)', N'Grade 1', 16, 32, 4800000.00, N'Học tư thế ngón, nhịp phách, thị tấu và ghép 2 tay tác phẩm'),
(2, 'MUA-KIDS', N'Múa Thiếu Nhi & Ballet Căn Bản', N'Căn bản', 16, 32, 4200000.00, N'Rèn luyện độ dẻo dai khớp xương, phom dáng chuẩn, nhịp điệu và bài múa thiếu nhi sinh động'),
(2, 'MUA-CONTEMP', N'Múa Đương Đại & Biểu Diễn', N'Nâng cao', 20, 40, 5500000.00, N'Kỹ thuật múa nâng cao, cảm thụ giai điệu sâu sắc và biểu diễn sân khấu chuyên nghiệp'),
(3, 'VO-BASIC', N'Võ Thuật Thiếu Nhi - Đai Trắng Nhập Môn', N'Khởi động', 16, 32, 3800000.00, N'Rèn luyện thể lực, tấn pháp căn bản, các thế đấm đá tự vệ và tác phong kỷ luật võ đạo'),
(3, 'VO-INTER', N'Võ Thuật Tự Vệ & Quyền Pháp', N'Trung cấp', 20, 40, 4600000.00, N'Nâng cao phản xạ tự vệ, quyền pháp, bài quyền biểu diễn và nâng đai');

INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status) VALUES
(2, 1, 1, 2, 'CL-DAN-01', N'Lớp Đàn Sơ Cấp 1-1 Bé Bảo Nam', 'ONE_ON_ONE', 1, 1, '2026-10-01', '2027-01-31', N'Thứ 2 & Thứ 5 (18:00 - 19:00)', 'OPEN'),
(1, 1, 2, 2, 'CL-DAN-PRE-G01', N'Lớp Đàn Mầm Non - Nhóm Sáng', 'GROUP', 8, 1, '2026-10-15', '2027-01-15', N'Thứ 7 (09:00 - 10:30)', 'OPEN'),
(2, 2, 5, 2, 'CL-DAN-G1-G02', N'Lớp Đàn Grade 1 - Nhóm Tối', 'GROUP', 8, 0, '2026-10-20', '2027-02-20', N'Thứ 3 & Thứ 6 (18:30 - 19:30)', 'OPEN'),
(3, 1, 3, 4, 'CL-MUA-KIDS-01', N'Lớp Múa Thiếu Nhi & Ballet Bé Ngọc Hân', 'GROUP', 12, 1, '2026-10-05', '2027-02-05', N'Thứ 3 & Thứ 6 (17:30 - 19:00)', 'OPEN'),
(5, 2, 6, 3, 'CL-VO-BASIC-01', N'Lớp Võ Thuật Nhập Môn Bé Tuấn Kiệt', 'GROUP', 15, 1, '2026-10-06', '2027-02-06', N'Thứ 4 & Thứ 7 (18:00 - 19:30)', 'OPEN');

INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes) VALUES
(1, 1, 6, 'ENROLLED', N'Đã hoàn tất học phí khóa Đàn Grade 1, xếp lớp thành công'),
(2, 2, 6, 'PENDING_PAYMENT', N'Phiếu giữ chỗ tạm thời 24h - Lớp Đàn Mầm Non'),
(3, 1, 7, 'PENDING_PAYMENT', N'Phiếu giữ chỗ tạm thời 24h - Lớp Đàn 1-1'),
(4, 4, 8, 'ENROLLED', N'Đã hoàn tất học phí khóa Múa Thiếu Nhi & Ballet Căn Bản sau bài test xếp lớp'),
(5, 5, 9, 'ENROLLED', N'Đã hoàn tất học phí khóa Võ Thuật Nhập Môn sau bài test thể lực');

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status) VALUES
(1, 1, 2, 1, '2026-10-05', '18:00:00', '19:00:00', N'Buổi 1: Ôn thế tay C Major', N'Bé giữ phom tay tốt', 'SCHEDULED'),
(1, 1, 2, 2, '2026-10-08', '18:00:00', '19:00:00', N'Buổi 2: Ghép 2 tay bài Canon in D', NULL, 'SCHEDULED'),
(4, 3, 4, 1, '2026-10-06', '17:30:00', '19:00:00', N'Buổi 1: Khởi động ép dẻo & 5 vị trí tay chân Ballet', N'Bé Hân dẻo và tiếp thu phom dáng rất nhanh', 'SCHEDULED'),
(5, 6, 3, 1, '2026-10-07', '18:00:00', '19:30:00', N'Buổi 1: Thế tấn trung bình tấn & Đòn đấm thẳng chính diện', N'Bé Kiệt thể lực tốt, tinh thần nghiêm túc', 'SCHEDULED');

INSERT INTO attendances (lesson_id, student_id, status, note) VALUES
(1, 1, 'PRESENT', N'Học viên đi học đúng giờ, tiếp thu tốt');

INSERT INTO teacher_availabilities (teacher_id, branch_id, day_of_week, available_date, start_time, end_time, note, is_booked) VALUES
(2, 1, 'SATURDAY', '2026-10-10', '09:00:00', '11:00:00', N'Ca rảnh sáng Thứ 7 dạy bù Đàn', 0),
(4, 1, 'SUNDAY', '2026-10-11', '08:30:00', '10:30:00', N'Ca rảnh sáng Chủ Nhật kiểm tra năng khiếu Múa', 0),
(3, 2, 'SUNDAY', '2026-10-11', '14:30:00', '16:30:00', N'Ca rảnh chiều Chủ Nhật test thể lực & võ thuật', 0);

INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note) VALUES
(1, 2, 6, N'Bé Nam bị sốt siêu vi cần nghỉ ca tối Thứ 5', 'PENDING', NULL, NULL),
(1, 1, 6, N'Gia đình có việc bận đột xuất', 'APPROVED', 2, N'Đã duyệt cho bé nghỉ và xếp lịch bù');

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note) VALUES
(2, 1, 1, 2, 'REGISTERED', N'Xếp học bù ca thực hành thứ 7');

-- ============================================================================
-- PLACEMENT TESTS & LỊCH ĐÁNH GIÁ NĂNG KHIẾU (3 BỘ MÔN: ĐÀN, MÚA, VÕ)
-- ============================================================================
INSERT INTO placement_schedules (student_name, title, room_name, branch, subject, test_date, note, status, score, recommended_level, teacher_note, audio_url, video_url, image_url, evaluated_by, evaluated_at, parent_id) VALUES
(N'Nguyễn Hoàng Anh', N'Đánh Giá Năng Khiếu Đàn Đầu Vào', N'Phòng Đàn 101', N'Cơ sở 1 - Cầu Giấy', 'DAN', DATEADD(day, 1, CAST(CURRENT_TIMESTAMP AS DATETIME2)), N'Học viên 10 tuổi, đã tự tập organ 6 tháng ở nhà.', 'SCHEDULED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 6),
(N'Lê Bảo Ngọc', N'Khảo Sát Năng Khiếu Múa & Độ Dẻo', N'Phòng Múa 201', N'Cơ sở 2 - Đống Đa', 'MUA', DATEADD(day, -2, CAST(CURRENT_TIMESTAMP AS DATETIME2)), N'Bé 7 tuổi, phụ huynh muốn định hướng học Múa đương đại.', 'COMPLETED', 9, 'INTERMEDIATE', N'Bé có độ dẻo tự nhiên rất tốt, cảm thụ âm nhạc nhanh và biểu cảm sân khấu tự tin. Đề xuất xếp vào lớp Múa Trung Cấp.', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500', 4, DATEADD(day, -2, CAST(CURRENT_TIMESTAMP AS DATETIME2)), 6),
(N'Trần Minh Khôi', N'Kiểm Tra Thể Lực & Phản Xạ Võ Thuật', N'Võ Đường 301', N'Cơ sở 1 - Cầu Giấy', 'VO', DATEADD(day, -1, CAST(CURRENT_TIMESTAMP AS DATETIME2)), N'Học viên 12 tuổi, có nguyện vọng rèn luyện thể lực và tự vệ.', 'COMPLETED', 8, 'BEGINNER', N'Thể lực và sức bền tốt, tấn pháp cơ bản vững vàng, kỷ luật nghiêm túc. Đề xuất học khóa Võ Thuật Nhập Môn Đai Trắng.', NULL, 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4', NULL, 3, DATEADD(day, -1, CAST(CURRENT_TIMESTAMP AS DATETIME2)), 7),
(N'Phạm Quỳnh Chi', N'Đánh Giá Cảm Âm & Nhịp Phách Đàn', N'Phòng Đàn 102', N'Cơ sở 1 - Cầu Giấy', 'DAN', DATEADD(day, 2, CAST(CURRENT_TIMESTAMP AS DATETIME2)), N'Bé 6 tuổi làm quen với nhạc cụ.', 'SCHEDULED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 6);

INSERT INTO placement_tests (student_id, teacher_id, program_id, test_date, rhythm_score, technique_score, ear_training_score, expression_score, total_score, teacher_notes, recommended_level, recommended_course_id) VALUES
(1, 2, 1, '2026-09-20', 8.5, 8.0, 9.0, 8.5, 8.5, N'[ĐÀN] Bé có năng khiếu cảm âm xuất sắc, nghe được nốt đơn và phách chuẩn. Khuyên nên học ngay Đàn Grade 1.', N'Grade 1 (Sơ cấp)', 2),
(4, 4, 2, '2026-09-21', 8.5, 9.5, 8.5, 9.0, 8.9, N'[MÚA] Khớp hông mở rất tốt, độ dẻo bẩm sinh xuất sắc (ép dẻo dọc/ngang 180 độ), bắt nhịp nhạc nhanh và thần thái rạng rỡ. Đề xuất xếp vào khóa Múa Thiếu Nhi & Ballet Căn Bản (MUA-KIDS).', N'Căn bản (Ballet Kids)', 3),
(5, 3, 3, '2026-09-22', 9.0, 8.5, 8.5, 9.5, 8.9, N'[VÕ THUẬT] Thể lực sung mãn, sức bật nhảy tốt, tấn pháp trung bình tấn vững chãi, phản xạ đòn nhanh và ý thức kỷ luật võ đạo rất cao. Đề xuất xếp vào lớp Võ Thuật Nhập Môn - Đai Trắng (VO-BASIC).', N'Đai Trắng (Khởi động)', 5);

INSERT INTO test_attachments (placement_test_id, file_name, file_url, media_type, duration_seconds, description) VALUES
(1, N'Video_Dan_Canon_BaoNam.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 'PIANO_VIDEO', 90, N'Video bé Nam thể hiện khả năng bấm phím bài test đầu vào Đàn'),
(1, N'Ghi_Am_Cam_Am_Nhip.mp3', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'AUDIO_RECORD', 60, N'Ghi âm bài test xướng âm nốt Đồ - Rê - Mi'),
(2, N'Video_Test_DoDeo_Mua_NgocHan.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4', 'DANCE_VIDEO', 120, N'Video bài test ép dẻo, uốn cầu vồng và chuyển động theo nhịp nhạc của bé Ngọc Hân'),
(3, N'Video_Test_TheLuc_VoThuat_TuanKiet.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4', 'MARTIAL_ARTS_VIDEO', 150, N'Video kiểm tra thể lực hít đất, bật cao, tấn pháp và bài test phản xạ của bé Tuấn Kiệt');

INSERT INTO learning_roadmaps (student_id, placement_test_id, target_goal, current_level, target_level, estimated_duration_months, milestones, notes) VALUES
(1, 1, N'Đạt chứng chỉ nghệ thuật Đàn Grade 1', N'Sơ cấp cơ bản', N'Grade 1 Quốc Tế', 6, N'Giai đoạn 1: Thị tấu và nhạc lý căn bản; Giai đoạn 2: Luyện tác phẩm hoàn chỉnh', N'Lộ trình đào tạo năng khiếu chuyên sâu'),
(4, 2, N'Hoàn thiện kỹ thuật dẻo & Biểu diễn bài múa Ballet thiếu nhi hoàn chỉnh', N'Dẻo tự nhiên', N'Ballet Căn Bản Grade 1', 6, N'Tháng 1-2: Rèn 5 vị trí tay chân và ép dẻo chuẩn an toàn; Tháng 3-4: Luyện xoay và nhảy nhịp điệu; Tháng 5-6: Ghép nhạc và biểu diễn báo cáo', N'Lộ trình bồi dưỡng tài năng múa nghệ thuật'),
(5, 3, N'Nắm vững 10 đòn tự vệ căn bản, bài quyền nhập môn và thi thăng Đai Vàng', N'Thể lực tốt', N'Đai Vàng Sơ Cấp', 6, N'Tháng 1-2: Tấn pháp, bộ thủ và thể lực cardio; Tháng 3-4: Đòn đấm đá liên hoàn & phản xạ tự vệ; Tháng 5-6: Luyện bài quyền số 1 và sát hạch thăng đai', N'Lộ trình rèn luyện võ thuật & thể chất');

INSERT INTO invoices (invoice_code, student_id, enrollment_id, original_amount, discount_type, discount_amount, discount_reason, final_amount, status, due_date, notes) VALUES
('INV-2026-001', 1, 1, 4800000.00, 'PARTIAL_DISCOUNT', 960000.00, N'Ưu đãi đăng ký sớm giảm 20%', 3840000.00, 'PAID', '2026-10-01', N'Hóa đơn học phí khóa Đàn Grade 1'),
('INV-2026-002', 2, 2, 3600000.00, 'NONE', 0.00, NULL, 3600000.00, 'UNPAID', DATEADD(day, 1, CAST(CURRENT_TIMESTAMP AS DATE)), N'Phiếu giữ chỗ 24h - Đàn Mầm Non'),
('INV-2026-003', 3, 3, 4800000.00, 'NONE', 0.00, NULL, 4800000.00, 'UNPAID', DATEADD(day, 1, CAST(CURRENT_TIMESTAMP AS DATE)), N'Phiếu giữ chỗ 24h - Đàn 1-1'),
('INV-2026-004', 4, 4, 4200000.00, 'PARTIAL_DISCOUNT', 420000.00, N'Ưu đãi học viên test đầu vào đạt xuất sắc giảm 10%', 3780000.00, 'PAID', '2026-10-05', N'Hóa đơn học phí khóa Múa Thiếu Nhi & Ballet'),
('INV-2026-005', 5, 5, 3800000.00, 'PARTIAL_DISCOUNT', 380000.00, N'Ưu đãi tân học viên võ thuật giảm 10%', 3420000.00, 'PAID', '2026-10-06', N'Hóa đơn học phí khóa Võ Thuật Nhập Môn');

INSERT INTO payments (invoice_id, payment_code, payment_method, amount, payment_date, cashier_id, note, status) VALUES
(1, 'PAY-2026-001', 'CASH_AT_DESK', 3840000.00, CURRENT_TIMESTAMP, 5, N'Phụ huynh Lan nộp tiền mặt trực tiếp tại quầy thu ngân cơ sở Cầu Giấy', 'SUCCESS'),
(4, 'PAY-2026-002', 'BANK_TRANSFER', 3780000.00, CURRENT_TIMESTAMP, 5, N'Phụ huynh Hoàng chuyển khoản học phí Múa qua VietQR', 'SUCCESS'),
(5, 'PAY-2026-003', 'BANK_TRANSFER', 3420000.00, CURRENT_TIMESTAMP, 5, N'Phụ huynh Dũng chuyển khoản học phí Võ Thuật qua VietQR', 'SUCCESS');
GO


