-- =============================================================================
-- KỊCH BẢN DỮ LIỆU ĐIỀU HÀNH: QUẢN LÝ XIN NGHỈ - HỌC BÙ - ĐIỂM DANH
-- Hệ quản trị cơ sở dữ liệu: Microsoft SQL Server (database: course_operation_management)
--
-- TẬP TRUNG DUY NHẤT 3 TÀI KHOẢN (Mật khẩu: Test@123456):
--   1. d26_staff        : Giáo vụ cơ sở (STAFF)
--   2. d26_parent_new   : Phụ huynh (PARENT) - Phụ huynh bé Nguyễn Bảo An & Nguyễn Bảo Nam
--   3. teacher_huong    : Giáo viên chuyên môn Piano (TEACHER) - Cô Vũ Thu Hương
--
-- ĐẶC TÍNH:
--   - Cửa sổ ngày động xoay quanh ngày hiện tại GETDATE() (-6 ngày đến +6 ngày).
--   - Đầy đủ 10 trường hợp nghiệp vụ toàn diện của quy trình Xin nghỉ & Học bù.
--   - Chuẩn hóa tag điều phối: [Lần bù: 1/2], [Lần bù: 2/2], [Lần bù: 2/2 - KHÓA].
--   - Không chứa bất kỳ tiền tố test thừa nào trong văn bản giao diện.
--   - Idempotent: An toàn chạy lại nhiều lần bất cứ lúc nào.
-- =============================================================================

USE course_operation_management;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

-- Hash BCrypt chuẩn của mật khẩu Test@123456
DECLARE @BcryptPassword VARCHAR(255) = '$2a$10$Qq6uoup7qgd2LgZGE.uu9uvZ9upjF2U.il/xf53YdJsXEhKyyDw.K';

-- Cửa sổ ngày động quanh ngày hiện tại
DECLARE @Today DATE = CAST(GETDATE() AS DATE);
DECLARE @D_M6  DATE = DATEADD(day, -6, @Today);
DECLARE @D_M5  DATE = DATEADD(day, -5, @Today);
DECLARE @D_M4  DATE = DATEADD(day, -4, @Today);
DECLARE @D_M3  DATE = DATEADD(day, -3, @Today);
DECLARE @D_M2  DATE = DATEADD(day, -2, @Today);
DECLARE @D_M1  DATE = DATEADD(day, -1, @Today);
DECLARE @D_0   DATE = @Today;
DECLARE @D_P1  DATE = DATEADD(day,  1, @Today);
DECLARE @D_P2  DATE = DATEADD(day,  2, @Today);
DECLARE @D_P3  DATE = DATEADD(day,  3, @Today);
DECLARE @D_P4  DATE = DATEADD(day,  4, @Today);
DECLARE @D_P5  DATE = DATEADD(day,  5, @Today);
DECLARE @D_P6  DATE = DATEADD(day,  6, @Today);

-- =============================================================================
-- PHẦN 1: KHỞI TẠO / ĐỒNG BỘ 3 TÀI KHOẢN DUY NHẤT (MẬT KHẨU: Test@123456)
-- =============================================================================

-- 1. Tài khoản Giáo vụ: d26_staff
IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'd26_staff')
    INSERT INTO users (username, password, full_name, email, phone, role, status)
    VALUES ('d26_staff', @BcryptPassword, N'Nguyễn Thanh Mai (Giáo Vụ Cơ Sở)', 'staff.d26@talentacademy.edu.vn', '0901000002', 'STAFF', 'ACTIVE');
ELSE
    UPDATE users SET password = @BcryptPassword, status = 'ACTIVE' WHERE username = 'd26_staff';

-- 2. Tài khoản Phụ huynh: d26_parent_new
IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'd26_parent_new')
    INSERT INTO users (username, password, full_name, email, phone, role, status)
    VALUES ('d26_parent_new', @BcryptPassword, N'Phụ Huynh Trần Thị Lan', 'parent.lan.d26@gmail.com', '0901000004', 'PARENT', 'ACTIVE');
ELSE
    UPDATE users SET password = @BcryptPassword, status = 'ACTIVE' WHERE username = 'd26_parent_new';

-- 3. Tài khoản Giáo viên: teacher_huong
IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher_huong')
    INSERT INTO users (username, password, full_name, email, phone, role, status)
    VALUES ('teacher_huong', @BcryptPassword, N'Cô Vũ Thu Hương (GV Piano)', 'huong.vu@talentacademy.edu.vn', '0912345678', 'TEACHER', 'ACTIVE');
ELSE
    UPDATE users SET password = @BcryptPassword, status = 'ACTIVE' WHERE username = 'teacher_huong';

DECLARE @StaffId   BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'd26_staff');
DECLARE @ParentId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'd26_parent_new');
DECLARE @TeacherId BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'teacher_huong');

-- =============================================================================
-- PHẦN 2: HỌC VIÊN CỦA PHỤ HUYNH d26_parent_new
-- =============================================================================
-- Bé lớn: Nguyễn Bảo An
IF NOT EXISTS (SELECT 1 FROM students WHERE full_name = N'Nguyễn Bảo An' AND parent_id = @ParentId)
    INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentId, N'Nguyễn Bảo An', '2017-05-12', N'Nam', N'Tiểu học Đoàn Thị Điểm', N'Học viên lớp Piano nền tảng');
ELSE
    UPDATE students SET parent_id = @ParentId WHERE full_name = N'Nguyễn Bảo An' AND parent_id = @ParentId;

-- Bé thứ hai: Nguyễn Bảo Nam (giúp phụ huynh kiểm tra đồng thời nhiều trạng thái)
IF NOT EXISTS (SELECT 1 FROM students WHERE full_name = N'Nguyễn Bảo Nam' AND parent_id = @ParentId)
    INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentId, N'Nguyễn Bảo Nam', '2019-10-18', N'Nam', N'Tiểu học Đoàn Thị Điểm', N'Học viên lớp Piano thiếu nhi');
ELSE
    UPDATE students SET parent_id = @ParentId WHERE full_name = N'Nguyễn Bảo Nam' AND parent_id = @ParentId;

DECLARE @StuAnId  BIGINT = (SELECT TOP 1 id FROM students WHERE full_name = N'Nguyễn Bảo An' AND parent_id = @ParentId);
DECLARE @StuNamId BIGINT = (SELECT TOP 1 id FROM students WHERE full_name = N'Nguyễn Bảo Nam' AND parent_id = @ParentId);

-- =============================================================================
-- PHẦN 3: CƠ SỞ, PHÒNG HỌC, CHƯƠNG TRÌNH & KHÓA HỌC
-- =============================================================================
-- Cơ sở Mỹ Đình
IF NOT EXISTS (SELECT 1 FROM branches WHERE code = 'CS-MD')
    INSERT INTO branches (code, name, address, phone, email, status)
    VALUES ('CS-MD', N'Cơ sở Mỹ Đình', N'Tầng 2, Tòa Mỹ Đình Plaza, Nam Từ Liêm, Hà Nội', '02439998811', 'mydinh@talentacademy.edu.vn', 'ACTIVE');
DECLARE @BranchMdId BIGINT = (SELECT TOP 1 id FROM branches WHERE code = 'CS-MD');

-- Cơ sở Cầu Giấy (lựa chọn ca bù khác cơ sở)
IF NOT EXISTS (SELECT 1 FROM branches WHERE code = 'CS01')
    INSERT INTO branches (code, name, address, phone, email, status)
    VALUES ('CS01', N'Cơ sở Cầu Giấy', N'Số 12 Chùa Hà, Cầu Giấy, Hà Nội', '02438887711', 'caugiay@talentacademy.edu.vn', 'ACTIVE');
DECLARE @BranchCgId BIGINT = (SELECT TOP 1 id FROM branches WHERE code = 'CS01');

-- Phòng Piano 201 tại Mỹ Đình
IF NOT EXISTS (SELECT 1 FROM rooms WHERE room_code = 'MD-PIA-201' AND branch_id = @BranchMdId)
    INSERT INTO rooms (branch_id, room_code, room_name, capacity, room_type, status, description)
    VALUES (@BranchMdId, 'MD-PIA-201', N'Phòng Piano 201', 8, 'MUSIC', 'AVAILABLE', N'Phòng học Piano 8 đàn điện tiêu chuẩn');
DECLARE @RoomMd201 BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = 'MD-PIA-201' AND branch_id = @BranchMdId);

-- Phòng Piano 101 tại Cầu Giấy
IF NOT EXISTS (SELECT 1 FROM rooms WHERE room_code = 'P101' AND branch_id = @BranchCgId)
    INSERT INTO rooms (branch_id, room_code, room_name, capacity, room_type, status, description)
    VALUES (@BranchCgId, 'P101', N'Phòng Đàn Piano 101', 8, 'MUSIC', 'AVAILABLE', N'Phòng thực hành Piano Cầu Giấy');
DECLARE @RoomCg101 BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = 'P101' AND branch_id = @BranchCgId);

-- Chương trình Âm nhạc
DECLARE @ProgMusicId BIGINT = (SELECT TOP 1 id FROM programs WHERE code = 'PROG-MUSIC');
IF @ProgMusicId IS NULL
BEGIN
    INSERT INTO programs (code, name, category, status, description)
    VALUES ('PROG-MUSIC', N'Chương Trình Âm Nhạc & Nhạc Cụ', 'MUSIC', 'ACTIVE', N'Đào tạo nhạc cụ Piano, Guitar, Thanh nhạc');
    SET @ProgMusicId = SCOPE_IDENTITY();
END;

-- Khóa học PIA-CB-12 (Piano nền tảng)
DECLARE @CoursePiaId BIGINT = (SELECT TOP 1 id FROM courses WHERE code = 'PIA-CB-12');
IF @CoursePiaId IS NULL
BEGIN
    INSERT INTO courses (program_id, code, name, level, duration_weeks, total_sessions, tuition_fee, description)
    VALUES (@ProgMusicId, 'PIA-CB-12', N'Piano nền tảng', N'Cơ bản', 12, 12, 3600000, N'Khóa học Piano nền tảng trang bị tư thế tay, nhận diện nốt, đọc bản nhạc và thị tấu.');
    SET @CoursePiaId = SCOPE_IDENTITY();
END;

-- =============================================================================
-- PHẦN 4: LỚP HỌC (CLASSES) & GHI DANH (ENROLLMENTS) DO Cô Hương GIẢNG DẠY
-- =============================================================================
-- 1. Lớp chính: PIA-MD-2701-01 (Chiều Thứ 7: 17:00-19:00 Mỹ Đình)
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = 'PIA-MD-2701-01')
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaId, @BranchMdId, @RoomMd201, @TeacherId, 'PIA-MD-2701-01', N'Piano nền tảng - chiều thứ Bảy Mỹ Đình', 'GROUP', 8, 2, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N'Thứ 7 (17:00 - 19:00)', 'OPEN');
ELSE
    UPDATE classes SET teacher_id = @TeacherId, room_id = @RoomMd201, status = 'OPEN' WHERE class_code = 'PIA-MD-2701-01';
DECLARE @ClassMainId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'PIA-MD-2701-01');

-- 2. Lớp song song tiếp nhận ca bù: PIA-MD-2701-02 (Sáng Chủ Nhật: 09:00-11:00 Mỹ Đình)
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = 'PIA-MD-2701-02')
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaId, @BranchMdId, @RoomMd201, @TeacherId, 'PIA-MD-2701-02', N'Piano nền tảng - sáng Chủ Nhật Mỹ Đình', 'GROUP', 8, 2, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N'Chủ Nhật (09:00 - 11:00)', 'OPEN');
ELSE
    UPDATE classes SET teacher_id = @TeacherId, room_id = @RoomMd201, status = 'OPEN' WHERE class_code = 'PIA-MD-2701-02';
DECLARE @ClassParaMdId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'PIA-MD-2701-02');

-- 3. Lớp ca bù khác cơ sở: PIA-CG-2701-01 (Chiều Thứ 5: 17:30-19:30 Cầu Giấy)
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = 'PIA-CG-2701-01')
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaId, @BranchCgId, @RoomCg101, @TeacherId, 'PIA-CG-2701-01', N'Piano nền tảng - chiều thứ Năm Cầu Giấy', 'GROUP', 8, 3, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N'Thứ 5 (17:30 - 19:30)', 'OPEN');
ELSE
    UPDATE classes SET teacher_id = @TeacherId, room_id = @RoomCg101, status = 'OPEN' WHERE class_code = 'PIA-CG-2701-01';
DECLARE @ClassParaCgId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'PIA-CG-2701-01');

-- 4. Lớp đầy chỗ (1/1) để kiểm tra bộ lọc hệ thống loại trừ lớp không còn slot
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = 'PIA-MD-2701-FULL')
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaId, @BranchMdId, @RoomMd201, @TeacherId, 'PIA-MD-2701-FULL', N'Piano nền tảng - sáng Thứ Hai (Kín chỗ)', 'GROUP', 1, 1, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N'Thứ 2 (08:30 - 10:30)', 'OPEN');
DECLARE @ClassFullId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'PIA-MD-2701-FULL');

-- Ghi danh 2 học viên của d26_parent_new vào lớp chính
IF NOT EXISTS (SELECT 1 FROM enrollments WHERE student_id = @StuAnId AND class_id = @ClassMainId)
    INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes)
    VALUES (@StuAnId, @ClassMainId, @ParentId, 'ENROLLED', N'Ghi danh lớp chính khóa');

IF NOT EXISTS (SELECT 1 FROM enrollments WHERE student_id = @StuNamId AND class_id = @ClassMainId)
    INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes)
    VALUES (@StuNamId, @ClassMainId, @ParentId, 'ENROLLED', N'Ghi danh lớp chính khóa');

-- =============================================================================
-- PHẦN 5: LÀM SẠCH VÙNG LỊCH XOAY QUANH HÔM NAY (-6 ĐẾN +6 NGÀY)
-- =============================================================================
DELETE a
FROM attendances a
JOIN lessons l ON a.lesson_id = l.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
  AND l.class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId);

DELETE m
FROM makeup_registrations m
LEFT JOIN lessons lo ON m.original_lesson_id = lo.id
LEFT JOIN lessons lt ON m.target_lesson_id = lt.id
WHERE (lo.lesson_date BETWEEN @D_M6 AND @D_P6 AND lo.class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId))
   OR (lt.lesson_date BETWEEN @D_M6 AND @D_P6 AND lt.class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId));

DELETE ar
FROM absence_requests ar
JOIN lessons l ON ar.lesson_id = l.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
  AND l.class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId);

DELETE FROM lessons
WHERE lesson_date BETWEEN @D_M6 AND @D_P6
  AND class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId);

-- =============================================================================
-- PHẦN 6: TẠO CÁC BUỔI HỌC (LESSONS) CHO LỚP CÔ HƯƠNG TRONG CỬA SỔ LỊCH
-- =============================================================================
-- A. Lớp chính: PIA-MD-2701-01
-- Buổi 1 (-6 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 1, @D_M6, '17:00', '19:00', N'Buổi 1: Giới thiệu đàn Piano & Tư thế bấm phím', N'Làm quen bàn phím, ngồi đúng tư thế tay hình vòm', 'COMPLETED');
DECLARE @L_Main_S1 BIGINT = SCOPE_IDENTITY();

-- Buổi 2 (-4 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 2, @D_M4, '17:00', '19:00', N'Buổi 2: Khóa Sol, Khóa Pha và nốt Đô trung tâm', N'Luyện tập nhận diện nốt nhạc trên khuông nhạc', 'COMPLETED');
DECLARE @L_Main_S2 BIGINT = SCOPE_IDENTITY();

-- Buổi 3 (-3 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 3, @D_M3, '17:00', '19:00', N'Buổi 3: Luyện ngón tay phải & Tiết tấu nốt đen', N'Bài thực hành số 1 giáo trình Alfred', 'COMPLETED');
DECLARE @L_Main_S3 BIGINT = SCOPE_IDENTITY();

-- Buổi 4 (-2 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 4, @D_M2, '17:00', '19:00', N'Buổi 4: Luyện ngón tay trái & Nhịp 4/4', N'Thực hành ghép hai tay chậm từng ô nhịp', 'COMPLETED');
DECLARE @L_Main_S4 BIGINT = SCOPE_IDENTITY();

-- Buổi 5 (-1 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 5, @D_M1, '17:00', '19:00', N'Buổi 5: Ghép hai tay tiểu phẩm Vui Bước', N'Tập trung giữ nhịp phách ổn định', 'COMPLETED');
DECLARE @L_Main_S5 BIGINT = SCOPE_IDENTITY();

-- Buổi 6 (HÔM NAY @D_0) - Buổi học chính khóa chiều tối nay
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 6, @D_0, '17:00', '19:00', N'Buổi 6: Kỹ thuật Legato & Hợp âm Đô trưởng', N'Buổi học chính khóa chiều thứ Bảy của lớp', 'SCHEDULED');
DECLARE @L_Main_S6 BIGINT = SCOPE_IDENTITY();

-- Buổi 6B (HÔM NAY @D_0 23:45 - Kiểm thử khóa giờ điểm danh của giáo viên trước giờ bắt đầu)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 6, @D_0, '23:45', '23:59', N'Buổi 6B: Thực hành biểu diễn cuối ngày', N'Kiểm tra cơ chế khóa giờ điểm danh trước giờ học', 'SCHEDULED');
DECLARE @L_Main_S6B BIGINT = SCOPE_IDENTITY();

-- Buổi 7 (+2 ngày tương lai) - Có đơn xin nghỉ chờ duyệt
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 7, @D_P2, '17:00', '19:00', N'Buổi 7: Dấu hóa thăng, giáng cơ bản', N'Phụ huynh đã nộp đơn xin nghỉ trước giờ quy định', 'SCHEDULED');
DECLARE @L_Main_S7 BIGINT = SCOPE_IDENTITY();

-- Buổi 8 (+4 ngày tương lai)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 8, @D_P4, '17:00', '19:00', N'Buổi 8: Luyện ngón Hanon nâng cao số 1', N'Luyện độ độc lập của các ngón tay', 'SCHEDULED');
DECLARE @L_Main_S8 BIGINT = SCOPE_IDENTITY();

-- B. Lớp song song tiếp nhận học bù: PIA-MD-2701-02 (Cô Hương giảng dạy)
-- Buổi S3 (-2 ngày): Buổi bù đã hoàn thành
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaMdId, @RoomMd201, @TeacherId, 3, @D_M2, '09:00', '11:00', N'Buổi 3: Luyện ngón tay phải & Tiết tấu nốt đen', N'Lớp ghép học bù Chủ Nhật', 'COMPLETED');
DECLARE @L_ParaMd_S3 BIGINT = SCOPE_IDENTITY();

-- Buổi S4 (-1 ngày): Buổi bù bị vắng lần 1
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaMdId, @RoomMd201, @TeacherId, 4, @D_M1, '09:00', '11:00', N'Buổi 4: Luyện ngón tay trái & Nhịp 4/4', N'Lớp tiếp nhận học viên học bù', 'COMPLETED');
DECLARE @L_ParaMd_S4 BIGINT = SCOPE_IDENTITY();

-- Buổi S5 (HÔM NAY @D_0): Tiếp nhận ca học bù hôm nay!
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaMdId, @RoomMd201, @TeacherId, 5, @D_0, '09:00', '11:00', N'Buổi 5: Ghép hai tay tiểu phẩm Vui Bước', N'Buổi học tiếp nhận học viên học bù hôm nay', 'SCHEDULED');
DECLARE @L_ParaMd_S5 BIGINT = SCOPE_IDENTITY();

-- Buổi S6 (+3 ngày tương lai): Ứng viên ca bù chờ xếp
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaMdId, @RoomMd201, @TeacherId, 6, @D_P3, '09:00', '11:00', N'Buổi 6: Kỹ thuật Legato & Hợp âm Đô trưởng', N'Buổi học sẵn sàng tiếp nhận học viên bù', 'SCHEDULED');
DECLARE @L_ParaMd_S6 BIGINT = SCOPE_IDENTITY();

-- C. Lớp khác cơ sở Cầu Giấy (+3 ngày tương lai): Ứng viên ca bù khác cơ sở
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaCgId, @RoomCg101, @TeacherId, 6, @D_P3, '17:30', '19:30', N'Buổi 6: Kỹ thuật Legato & Hợp âm Đô trưởng', N'Lựa chọn học bù khác cơ sở cho học viên', 'SCHEDULED');
DECLARE @L_ParaCg_S6 BIGINT = SCOPE_IDENTITY();

-- D. Lớp đầy chỗ (1/1) để test bộ lọc loại trừ lớp hết chỗ
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassFullId, @RoomMd201, @TeacherId, 6, @D_P3, '08:30', '10:30', N'Buổi 6: Kỹ thuật Legato (Đã đầy chỗ)', N'Lớp đã đủ sĩ số 1/1', 'SCHEDULED');
DECLARE @L_Full_S6 BIGINT = SCOPE_IDENTITY();

-- =============================================================================
-- PHẦN 7: TẠO ĐẦY ĐỦ 10 TÌNH HUỐNG TRÊN 3 TÀI KHOẢN DUY NHẤT
-- =============================================================================

-- CASE 1: ĐƠN XIN NGHỈ PENDING (Chờ duyệt)
-- Thao tác kiểm thử:
--   - d26_parent_new đăng nhập: xem đơn của bé Bảo An, có nút "Hủy đơn" (Cancel eligible request).
--   - teacher_huong hoặc d26_staff đăng nhập: xem đơn, có nút "Duyệt" (Approve) và "Từ chối" (Reject).
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, created_at)
VALUES (
    @StuAnId,
    @L_Main_S7,
    @ParentId,
    N'Học viên Nguyễn Bảo An bị sốt virus theo chỉ định của bác sĩ cần nghỉ ngơi 3 ngày, gia đình xin phép cô Hương cho bé nghỉ buổi 7.',
    'PENDING',
    DATEADD(minute, -25, GETDATE())
);

-- CASE 2: ĐƠN XIN NGHỈ REJECTED (Bị từ chối)
-- Nghiệp vụ: Phụ huynh gửi đơn muộn sau giờ học -> Cô Hương từ chối kèm phản hồi -> Không sinh ca bù.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S4,
    @ParentId,
    N'Gia đình có lịch về quê đột xuất nên xin phép cho bé Bảo Nam vắng mặt.',
    'REJECTED',
    @TeacherId,
    N'Từ chối do phụ huynh gửi đơn sau thời gian diễn ra buổi học và không thông báo trước cho giáo vụ.',
    DATEADD(day, -1, GETDATE()),
    DATEADD(day, -1, GETDATE())
);

-- CASE 3: ĐƠN APPROVED -> CA BÙ PENDING (Chờ giáo vụ xếp lịch)
-- Thao tác kiểm thử:
--   - d26_staff vào mục Quản lý học bù -> Bấm Xếp lịch bù -> Hệ thống lọc gợi ý các buổi cùng trình độ (S6), loại trừ lớp kín chỗ.
--   - d26_parent_new vào xem và chọn ca bù (Select make-up option).
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuAnId,
    @L_Main_S6,
    @ParentId,
    N'Học viên Bảo An trùng lịch thi học kỳ tại trường tiểu học vào chiều Thứ Bảy.',
    'APPROVED',
    @TeacherId,
    N'Cô Hương đồng ý cho bé nghỉ. Chuyển bộ phận giáo vụ sắp xếp ca học bù phù hợp cho học viên.',
    DATEADD(hour, -2, GETDATE()),
    DATEADD(hour, -5, GETDATE())
);
DECLARE @Ar_Pending_Makeup BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Pending_Makeup,
    @StuAnId,
    @L_Main_S6,
    NULL,
    'PENDING',
    N'Đơn xin nghỉ đã duyệt, đang chờ phụ huynh và giáo vụ lựa chọn lịch học bù thích hợp.',
    DATEADD(hour, -2, GETDATE())
);

-- CASE 4: ĐƠN APPROVED -> CA BÙ NO_SLOT (Chưa có lớp phù hợp)
-- Nghiệp vụ: Giáo vụ rà soát chưa có lớp song song phù hợp -> Đánh dấu NO_SLOT, giữ mở để theo dõi.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S5,
    @ParentId,
    N'Bé Bảo Nam bị đau họng và khàn tiếng, xin nghỉ buổi 5 để đi khám chuyên khoa tai mũi họng.',
    'APPROVED',
    @TeacherId,
    N'Đã xác nhận lý do nghỉ ốm của học viên.',
    DATEADD(day, -1, GETDATE()),
    DATEADD(day, -2, GETDATE())
);
DECLARE @Ar_NoSlot BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_NoSlot,
    @StuNamId,
    @L_Main_S5,
    NULL,
    'NO_SLOT',
    N'Chưa có lớp phù hợp: Các lớp cùng trình độ trong tuần hiện chưa mở hoặc đã kín lịch.',
    DATEADD(day, -1, GETDATE())
);

-- CASE 5: ĐƠN APPROVED -> CA BÙ CANCELLED (Gia đình từ chối học bù)
-- Nghiệp vụ: Phụ huynh d26_parent_new báo gia đình bận không tham gia học bù -> Đóng ca bù CANCELLED.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S2,
    @ParentId,
    N'Học viên Bảo Nam có việc gia đình đột xuất xin phép nghỉ học.',
    'APPROVED',
    @TeacherId,
    N'Đã duyệt đơn nghỉ phép.',
    DATEADD(day, -3, GETDATE()),
    DATEADD(day, -4, GETDATE())
);
DECLARE @Ar_Cancelled BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Cancelled,
    @StuNamId,
    @L_Main_S2,
    NULL,
    'CANCELLED',
    N'Gia đình xác nhận bận lịch riêng nên xin phép không tham gia học bù buổi này.',
    DATEADD(day, -3, GETDATE())
);

-- CASE 6: CA BÙ SCHEDULED LẦN 1/2 DIỄN RA HÔM NAY
-- Thao tác kiểm thử:
--   - teacher_huong vào Lịch dạy / Điểm danh hôm nay: thấy bé Bảo An tại lớp S5 có nhãn [Học bù].
--   - Điểm danh Có mặt -> chuyển COMPLETED.
--   - Điểm danh Vắng mặt -> chuyển MISSED (Lần 1).
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuAnId,
    @L_Main_S3,
    @ParentId,
    N'Bé Bảo An cần đi tiêm phòng định kỳ vào chiều Thứ Bảy nên xin nghỉ.',
    'APPROVED',
    @TeacherId,
    N'Cô Hương đồng ý cho học viên học bù ghép vào ca sáng Chủ Nhật.',
    DATEADD(day, -2, GETDATE()),
    DATEADD(day, -3, GETDATE())
);
DECLARE @Ar_Scheduled_Today BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Scheduled_Today,
    @StuAnId,
    @L_Main_S3,
    @L_ParaMd_S5,
    'SCHEDULED',
    N'[Lần bù: 1/2] Đã xếp lịch học bù tại lớp Piano sáng Chủ Nhật Mỹ Đình.',
    DATEADD(day, -1, GETDATE())
);

-- CASE 7: BỎ LỠ CA BÙ LẦN 1/2 -> TRẠNG THÁI MISSED (CÒN 1 LẦN BÙ CUỐI CÙNG)
-- Nghiệp vụ: Đã xếp buổi bù (-1 ngày) nhưng học viên vắng mặt -> Ghi nhận MISSED, tag [Lần bù: 1/2], còn 1 quyền xếp lại.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S3,
    @ParentId,
    N'Học viên Bảo Nam bị sốt nhẹ, xin phép cô Hương nghỉ buổi 3.',
    'APPROVED',
    @TeacherId,
    N'Cô Hương đã duyệt đơn.',
    DATEADD(day, -3, GETDATE()),
    DATEADD(day, -4, GETDATE())
);
DECLARE @Ar_Missed1 BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Missed1,
    @StuNamId,
    @L_Main_S3,
    @L_ParaMd_S4,
    'MISSED',
    N'[Lần bù: 1/2] Học viên vắng mặt tại buổi học bù lần 1 (Còn 1 lần xếp lại lịch bù cuối cùng).',
    DATEADD(day, -1, GETDATE())
);

-- Ghi nhận điểm danh vắng mặt tại buổi bù S4
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at)
VALUES (@L_ParaMd_S4, @StuNamId, 'ABSENT', N'Học viên vắng mặt tại buổi học bù lần 1', DATEADD(day, -1, GETDATE()));

-- CASE 8: CA BÙ SCHEDULED LẦN 2/2 DIỄN RA HÔM NAY (LẦN CUỐI CÙNG)
-- Thao tác kiểm thử:
--   - Bé Bảo Nam được xếp học bù lần 2 tại buổi S5 hôm nay.
--   - teacher_huong điểm danh vắng mặt hôm nay sẽ tự động khóa hủy quyền học bù.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S4,
    @ParentId,
    N'Gia đình có việc gấp cần về quê, xin phép cô Hương nghỉ buổi 4.',
    'APPROVED',
    @TeacherId,
    N'Đã đồng ý cho học viên học bù lần 2.',
    DATEADD(day, -2, GETDATE()),
    DATEADD(day, -3, GETDATE())
);
DECLARE @Ar_Scheduled_Retry2 BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Scheduled_Retry2,
    @StuNamId,
    @L_Main_S4,
    @L_ParaMd_S5,
    'SCHEDULED',
    N'[Lần bù: 2/2] Xếp lịch học bù lần cuối cùng tại lớp Piano Mỹ Đình.',
    DATEADD(day, -1, GETDATE())
);

-- CASE 9: VẮNG MẶT CẢ 2/2 LẦN BÙ -> KHÓA HỦY QUYỀN HỌC BÙ (LOCKED CANCELLED)
-- Nghiệp vụ: Học viên đã vắng mặt cả 2 lần học bù quy định -> Hệ thống tự động khóa vĩnh viễn theo quy chế trung tâm.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuAnId,
    @L_Main_S1,
    @ParentId,
    N'Bé Bảo An bị cảm lạnh ngày khai giảng, xin nghỉ buổi mở đầu.',
    'APPROVED',
    @TeacherId,
    N'Đã duyệt đơn nghỉ học.',
    DATEADD(day, -5, GETDATE()),
    DATEADD(day, -6, GETDATE())
);
DECLARE @Ar_Locked BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Locked,
    @StuAnId,
    @L_Main_S1,
    @L_ParaMd_S4,
    'CANCELLED',
    N'[Lần bù: 2/2 - KHÓA] Tự động khóa quyền học bù do học viên đã vắng mặt cả 2 lần học bù quy định.',
    DATEADD(day, -1, GETDATE())
);

-- Ghi nhận điểm danh vắng mặt tại buổi bù S4 cho bé An
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at)
VALUES (@L_ParaMd_S4, @StuAnId, 'ABSENT', N'Học viên vắng mặt tại buổi học bù lần 2 (Hết lượt)', DATEADD(day, -1, GETDATE()));

-- CASE 10: HOÀN THÀNH HỌC BÙ (COMPLETED)
-- Nghiệp vụ: Học viên Bảo An đã tham gia học bù tại buổi S3 (-2 ngày) và được cô Hương điểm danh Có mặt.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuAnId,
    @L_Main_S2,
    @ParentId,
    N'Học viên bận thi cờ vua cấp trường, xin nghỉ buổi 2.',
    'APPROVED',
    @TeacherId,
    N'Cô Hương đã duyệt đơn.',
    DATEADD(day, -4, GETDATE()),
    DATEADD(day, -5, GETDATE())
);
DECLARE @Ar_Completed BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Completed,
    @StuAnId,
    @L_Main_S2,
    @L_ParaMd_S3,
    'COMPLETED',
    N'[Lần bù: 1/2] Hoàn thành buổi học bù sau khi giáo viên điểm danh Có mặt.',
    DATEADD(day, -2, GETDATE())
);

-- Ghi nhận điểm danh Có mặt tại buổi bù S3
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at)
VALUES (@L_ParaMd_S3, @StuAnId, 'PRESENT', N'Tham gia học bù đầy đủ và hoàn thành bài luyện ngón', DATEADD(day, -2, GETDATE()));

-- Điểm danh một số buổi học chính khóa lịch sử
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at) VALUES
(@L_Main_S1, @StuNamId, 'PRESENT', N'Tham gia học đầy đủ', DATEADD(day, -6, GETDATE())),
(@L_Main_S2, @StuAnId,  'ABSENT',  N'Nghỉ thi cờ vua', DATEADD(day, -4, GETDATE())),
(@L_Main_S2, @StuNamId, 'ABSENT',  N'Nghỉ có việc gia đình', DATEADD(day, -4, GETDATE())),
(@L_Main_S3, @StuAnId,  'ABSENT',  N'Nghỉ tiêm phòng', DATEADD(day, -3, GETDATE())),
(@L_Main_S3, @StuNamId, 'ABSENT',  N'Nghỉ ốm', DATEADD(day, -3, GETDATE())),
(@L_Main_S4, @StuAnId,  'PRESENT', N'Tham gia học đầy đủ', DATEADD(day, -2, GETDATE())),
(@L_Main_S4, @StuNamId, 'ABSENT',  N'Vắng mặt nộp đơn muộn', DATEADD(day, -2, GETDATE())),
(@L_Main_S5, @StuAnId,  'PRESENT', N'Tham gia học đầy đủ', DATEADD(day, -1, GETDATE())),
(@L_Main_S5, @StuNamId, 'ABSENT',  N'Nghỉ đau họng', DATEADD(day, -1, GETDATE()));

-- =============================================================================
-- PHẦN 8: TRUY VẤN TỔNG KẾT BẢO ĐẢM DỮ LIỆU ĐÃ SẴN SÀNG
-- =============================================================================
PRINT N'==> TỔNG KẾT ĐƠN XIN NGHỈ (3 TÀI KHOẢN: d26_staff / d26_parent_new / teacher_huong):';
SELECT 
    ar.id AS absence_id,
    s.full_name AS student_name,
    u_req.username AS requested_by,
    c.class_code,
    l.lesson_date,
    l.title AS lesson_title,
    ar.status AS absence_status,
    ar.reason,
    ar.review_note
FROM absence_requests ar
JOIN students s ON ar.student_id = s.id
JOIN users u_req ON ar.requested_by_user_id = u_req.id
JOIN lessons l ON ar.lesson_id = l.id
JOIN classes c ON l.class_id = c.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
ORDER BY ar.id;

PRINT N'==> TỔNG KẾT CA HỌC BÙ (3 TÀI KHOẢN: d26_staff / d26_parent_new / teacher_huong):';
SELECT 
    mr.id AS makeup_id,
    s.full_name AS student_name,
    lo.title AS original_lesson,
    lo.lesson_date AS original_date,
    ISNULL(lt.title, N'-- Chưa gán buổi bù --') AS target_lesson,
    lt.lesson_date AS target_date,
    mr.status AS makeup_status,
    mr.note
FROM makeup_registrations mr
JOIN students s ON mr.student_id = s.id
JOIN lessons lo ON mr.original_lesson_id = lo.id
LEFT JOIN lessons lt ON mr.target_lesson_id = lt.id
WHERE lo.lesson_date BETWEEN @D_M6 AND @D_P6
ORDER BY mr.id;

PRINT N'==> HOÀN TẤT NẠP DỮ LIỆU KIỂM THỬ CHO 3 TÀI KHOẢN DUY NHẤT!';
GO
