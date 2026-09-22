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

INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes) VALUES
(5, N'Nguyễn Bảo Nam (Bé Bin)', '2016-05-12', N'Nam', N'Tiểu học Thực Nghiệm', N'Thích học đàn Piano cổ điển'),
(5, N'Nguyễn Mai Chi (Bé Bông)', '2019-08-20', N'Nữ', N'Mầm non Vinschool', N'Học làm quen phím đàn mầm non');

INSERT INTO branches (code, name, address, phone, email, active) VALUES
('CS01', N'Cơ Sở 1 - Cầu Giấy', N'Số 12 Khúc Thừa Dụ, Dịch Vọng, Cầu Giấy, Hà Nội', '0243888999', 'caugiay@talentcenter.edu.vn', 1),
('CS02', N'Cơ Sở 2 - Đống Đa', N'Số 85 Hào Nam, Ô Chợ Dừa, Đống Đa, Hà Nội', '0243777888', 'dongda@talentcenter.edu.vn', 1);

INSERT INTO rooms (branch_id, room_code, room_name, capacity, room_type, status, description) VALUES
(1, 'P101', N'Phòng Piano Biểu Diễn 1', 2, 'PIANO_INDIVIDUAL', 'AVAILABLE', N'Trang bị đàn Grand Piano Yamaha C3X cao cấp cách âm tốt'),
(1, 'P102', N'Phòng Piano Nhóm 1', 8, 'PIANO_GROUP', 'AVAILABLE', N'Trang bị đàn Upright Piano Kawai cho lớp nhóm'),
(2, 'P201', N'Phòng Guitar & Cảm Âm', 10, 'GUITAR_ROOM', 'AVAILABLE', N'Trang bị giá nhạc, âm ly và đàn guitar acoustic'),
(2, 'P202', N'Phòng Piano Thực Hành 2', 4, 'PIANO_GROUP', 'AVAILABLE', N'Trang bị 3 đàn Upright Piano cho lớp học thực hành');

INSERT INTO equipments (room_id, name, code, category, status, serial_number, description) VALUES
(1, N'Đàn Đại Dương Cầm Yamaha C3X', 'EQ-P01', 'GRAND_PIANO', 'GOOD', 'YAM-C3X-9988', N'Đàn Grand Piano cơ cao cấp Nhật Bản biểu diễn'),
(2, N'Đàn Upright Piano Kawai K-300', 'EQ-P02', 'UPRIGHT_PIANO', 'GOOD', 'KAW-K300-1122', N'Đàn cơ upright cho học sinh luyện ngón'),
(2, N'Đàn Piano Điện Roland RP-102', 'EQ-P03', 'KEYBOARD', 'GOOD', 'ROL-RP102-3344', N'Đàn phím cảm ứng lực tốt cho lớp sơ cấp'),
(3, N'Đàn Guitar Classic Yamaha C40', 'EQ-G01', 'GUITAR', 'GOOD', 'YAM-C40-5566', N'Đàn guitar thùng dây nylon tập cảm âm cho học viên'),
(4, N'Đàn Upright Piano Yamaha U3H', 'EQ-P04', 'UPRIGHT_PIANO', 'GOOD', 'YAM-U3H-7788', N'Đàn Upright Piano cơ Nhật Bản âm thanh chuẩn');

INSERT INTO programs (code, name, description) VALUES
('PIANO', N'Bộ Môn Piano', N'Đào tạo Piano cổ điển, hiện đại và luyện thi chứng chỉ quốc tế ABRSM'),
('GUITAR', N'Bộ Môn Guitar', N'Đào tạo Guitar đệm hát và cổ điển Fingerstyle');

INSERT INTO courses (program_id, code, name, level, duration_weeks, total_sessions, tuition_fee, description) VALUES
(1, 'PIA-PRE', N'Piano Mầm Non (Cảm thụ âm nhạc)', N'Khởi động', 12, 24, 3600000.00, N'Dành cho bé từ 4-6 tuổi làm quen phím đàn'),
(1, 'PIA-G1', N'Piano Sơ Cấp (Grade 1)', N'Grade 1', 16, 32, 4800000.00, N'Học tư thế ngón, nhịp phách, thị tấu và ghép 2 tay');

INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status) VALUES
(2, 1, 1, 2, 'CL-PIA-01', N'Piano 1-1 Bé Bảo Nam', 'ONE_ON_ONE', 1, 1, '2026-10-01', '2027-01-31', N'Thứ 2 & Thứ 5 (18:00 - 19:00)', 'OPEN');

INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes) VALUES
(1, 1, 5, 'ENROLLED', N'Đã hoàn tất học phí, xếp lớp thành công');

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status) VALUES
(1, 1, 2, 1, '2026-10-05', '18:00:00', '19:00:00', N'Buổi 1: Ôn thế tay C Major', N'Bé giữ phom tay tốt', 'SCHEDULED'),
(1, 1, 2, 2, '2026-10-08', '18:00:00', '19:00:00', N'Buổi 2: Ghép 2 tay bài Canon in D', NULL, 'SCHEDULED');

INSERT INTO attendances (lesson_id, student_id, status, note) VALUES
(1, 1, 'PRESENT', N'Học viên đi học đúng giờ, tiếp thu tốt');

INSERT INTO teacher_availabilities (teacher_id, branch_id, day_of_week, available_date, start_time, end_time, note, is_booked) VALUES
(2, 1, 'SATURDAY', '2026-10-10', '09:00:00', '11:00:00', N'Ca rảnh sáng Thứ 7 dạy bù Piano', 0);

INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note) VALUES
(1, 2, 5, N'Bé Nam bị sốt siêu vi cần nghỉ ca tối Thứ 5', 'PENDING', NULL, NULL),
(1, 1, 5, N'Gia đình có việc bận đột xuất', 'APPROVED', 2, N'Đã duyệt cho bé nghỉ và xếp lịch bù');

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note) VALUES
(2, 1, 1, 2, 'REGISTERED', N'Xếp học bù ca thực hành thứ 7');

INSERT INTO placement_tests (student_id, teacher_id, program_id, test_date, rhythm_score, technique_score, ear_training_score, expression_score, total_score, teacher_notes, recommended_level, recommended_course_id) VALUES
(1, 2, 1, '2026-09-20', 8.5, 8.0, 9.0, 8.5, 8.5, N'Bé có năng khiếu cảm âm xuất sắc, nghe được nốt đơn và phách chuẩn. Khuyên nên học ngay Piano Grade 1.', N'Grade 1', 2);

INSERT INTO test_attachments (placement_test_id, file_name, file_url, media_type, duration_seconds, description) VALUES
(1, N'Video_Dan_Canon_BaoNam.mp4', 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', 'PIANO_VIDEO', 90, N'Video bé Nam thể hiện khả năng bấm phím bài test đầu vào'),
(1, N'Ghi_Am_Cam_Am_Nhip.mp3', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'AUDIO_RECORD', 60, N'Ghi âm bài test xướng âm nốt Đồ - Rê - Mi');

INSERT INTO learning_roadmaps (student_id, placement_test_id, target_goal, current_level, target_level, estimated_duration_months, milestones, notes) VALUES
(1, 1, N'Đạt chứng chỉ quốc tế ABRSM Piano Grade 1', N'Sơ cấp cơ bản', N'Grade 1 Quốc Tế', 6, N'Giai đoạn 1: Thị tấu và nhạc lý căn bản; Giai đoạn 2: Luyện tác phẩm dự thi ABRSM', N'Lộ trình đào tạo năng khiếu chuyên sâu');

INSERT INTO invoices (invoice_code, student_id, enrollment_id, original_amount, discount_type, discount_amount, discount_reason, final_amount, status, due_date, notes) VALUES
('INV-2026-001', 1, 1, 4800000.00, 'PARTIAL_DISCOUNT', 960000.00, N'Ưu đãi đăng ký sớm giảm 20%', 3840000.00, 'PAID', '2026-10-01', N'Hóa đơn học phí khóa Piano Grade 1');

INSERT INTO payments (invoice_id, payment_code, payment_method, amount, payment_date, cashier_id, note, status) VALUES
(1, 'PAY-2026-001', 'CASH_AT_DESK', 3840000.00, CURRENT_TIMESTAMP, 4, N'Phụ huynh Lan nộp tiền mặt trực tiếp tại quầy thu ngân cơ sở Cầu Giấy', 'SUCCESS');
GO