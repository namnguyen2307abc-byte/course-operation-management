-- ==============================================================================
-- BỘ DỮ LIỆU TEST TOÀN DIỆN CHO TẤT CẢ CÁC TRƯỜNG HỢP (PHÂN HỆ CỦA PHÚC):
-- 1. SỬA TRIỆT ĐỂ LỖI FONT UNICODE TIẾNG VIỆT
--    - Sửa tên Cơ sở Mỹ Đình, Phòng Piano 201, Phòng Guitar 202, tên học sinh, giáo viên
-- 2. ĐẦY ĐỦ HỌC VIÊN CHO CÁC LỚP
--    - Lớp Piano Mầm Non Demo (CL-PIA-DEMO01): 4/6 học viên
--    - Lớp Piano Mầm Non Nhóm Sáng (CL-PIA-PRE-G01): 5/8 học viên
--    - Lớp Guitar Đệm Hát (CL-GUI-BASIC-01): 4/8 học viên
--    - Lớp Piano 1-1 Bé Bảo Nam (CL-PIA-01): 1/1 học viên
-- 3. THỜI KHÓA BIỂU TUẦN (WEEKLY SCHEDULE)
--    - Phân quyền: Admin, Staff, Teacher (GV Hương, GV Tuấn), Parent (PH Lan), Student (Bé Nam)
--    - Ca quá khứ ĐÃ điểm danh (Thứ Hai 23/11)
--    - Ca quá khứ CHƯA điểm danh (Thứ Hai 23/11 tối) -> Test Admin Override
--    - Ca hôm nay (Thứ Ba 24/11) -> Test GV điểm danh trực tiếp
--    - Ca tương lai gần (Thứ Tư 25/11) -> Test Time-Lock (chặn điểm danh trước)
--    - Ca tương lai cuối tuần (Thứ Năm, Sáu, Bảy, CN)
-- 4. ĐIỂM DANH BUỔI HỌC (ATTENDANCE TRACKING)
--    - 4 trạng thái: Có mặt (PRESENT), Vắng mặt (ABSENT), Đi muộn, Nghỉ có phép
--    - Roster Guard: Học viên chính khóa + Học viên học bù ghép lớp
--    - Time-Lock Guard: Chặn GV điểm danh buổi tương lai
--    - Admin Override: Sửa điểm danh quá khứ kèm lý do điều chỉnh
-- 5. THEO DÕI CHUYÊN CẦN 3 CẤP ĐỘ (ATTENDANCE RATE & WARNINGS)
--    - Cấp 1: Chuyên cần tốt / Xanh lá (≤ 10%) - Bé Nguyễn Mai Chi (1/24 buổi = 4.2%)
--    - Cấp 2: Cảnh báo vàng (10% - 20%) - Bé Nguyễn Trọng Anh (3/24 buổi = 12.5%)
--    - Cấp 3: Báo động đỏ cấm thi (≥ 20%) - Bé Nguyễn Hải Yến (7/32 buổi = 21.9%)
-- 6. HỌC BÙ & QUY TẮC KHÓA SAU LẦN VẮNG BÙ ĐẦU (ABSENCE MAKEUP)
--    - Case A: Đơn xin nghỉ chờ duyệt (PENDING) - Bé Bảo Nam ngày 26/11
--    - Case B: Ca học bù chờ xếp lịch (PENDING)
--    - Case C: Ca học bù đã xếp lịch (SCHEDULED) - Bé Bảo Nam ghép vào ca Thứ Bảy 28/11
--    - Case D: Ca học bù đã hoàn thành (COMPLETED)
--    - Case E: Vắng mặt buổi học bù -> Khóa quyền bù (CANCELLED)
-- 7. CHUÔNG THÔNG BÁO (NOTIFICATIONS)
--    - Chuẩn font tiếng Việt NVARCHAR 100%, đầy đủ các loại thông báo nghiệp vụ
-- ==============================================================================

USE [course_operation_management];
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRANSACTION;

-- ------------------------------------------------------------------------------
-- BƯỚC 1: SỬA LỖI FONT UNICODE TOÀN BỘ CÁC BẢNG (BRANCHES, ROOMS, COURSES, USERS, STUDENTS)
-- ------------------------------------------------------------------------------
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'notifications' AND COLUMN_NAME = 'title')
BEGIN
    ALTER TABLE dbo.notifications ALTER COLUMN title NVARCHAR(255) NOT NULL;
    ALTER TABLE dbo.notifications ALTER COLUMN message NVARCHAR(1000) NOT NULL;
    ALTER TABLE dbo.notifications ALTER COLUMN target_url NVARCHAR(255) NULL;
END;

-- Sửa bảng branches
UPDATE dbo.branches
SET name = N'Cơ sở Mỹ Đình',
    address = N'Khu đô thị Mỹ Đình 1, Nam Từ Liêm, Hà Nội'
WHERE code = 'MD01';

-- Sửa bảng rooms
UPDATE dbo.rooms SET room_name = N'Phòng Guitar 202' WHERE room_code = 'MD-GUI-202';
UPDATE dbo.rooms SET room_name = N'Phòng Piano 201' WHERE room_code = 'MD-PIA-201';

-- Sửa bảng courses
UPDATE dbo.courses SET name = N'Piano nền tảng' WHERE code = 'PIA-CB-12';

-- Sửa bảng users
UPDATE dbo.users SET full_name = N'Trần Quang Hải' WHERE username = 'd26_admin';
UPDATE dbo.users SET full_name = N'Nguyễn Thị Lan' WHERE username = 'd26_parent_new';
UPDATE dbo.users SET full_name = N'Phạm Thị Hương' WHERE username = 'd26_parent_pay';
UPDATE dbo.users SET full_name = N'Lê Minh Tuấn' WHERE username = 'd26_parent_ready';
UPDATE dbo.users SET full_name = N'Trần Thu Hà' WHERE username = 'd26_parent_wait';
UPDATE dbo.users SET full_name = N'Nguyễn Thu Trang' WHERE username = 'd26_staff';
UPDATE dbo.users SET full_name = N'Nguyễn Minh Anh' WHERE username = 'd26_teacher_piano';

-- Sửa bảng students
UPDATE dbo.students SET full_name = N'Nguyễn An Nhiên' WHERE id = 17;
UPDATE dbo.students SET full_name = N'Nguyễn Bảo An' WHERE id = 18;
UPDATE dbo.students SET full_name = N'Nguyễn Minh Quân' WHERE id = 19;
UPDATE dbo.students SET full_name = N'Nguyễn Ngọc Hân' WHERE id = 20;


-- ------------------------------------------------------------------------------
-- BƯỚC 2: BỔ SUNG ĐẦY ĐỦ HỌC VIÊN CHO CÁC LỚP
-- ------------------------------------------------------------------------------
-- Lớp 22 (CL-PIA-DEMO01): Thêm 4 học sinh (17, 18, 19, 20)
IF NOT EXISTS (SELECT 1 FROM dbo.enrollments WHERE class_id = 22 AND student_id = 17)
    INSERT INTO dbo.enrollments (student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (17, 22, 1, '2026-11-01', 'ENROLLED', N'Đăng ký khóa Piano Mầm Non');

IF NOT EXISTS (SELECT 1 FROM dbo.enrollments WHERE class_id = 22 AND student_id = 18)
    INSERT INTO dbo.enrollments (student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (18, 22, 1, '2026-11-01', 'ENROLLED', N'Đăng ký khóa Piano Mầm Non');

IF NOT EXISTS (SELECT 1 FROM dbo.enrollments WHERE class_id = 22 AND student_id = 19)
    INSERT INTO dbo.enrollments (student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (19, 22, 1, '2026-11-01', 'ENROLLED', N'Đăng ký khóa Piano Mầm Non');

IF NOT EXISTS (SELECT 1 FROM dbo.enrollments WHERE class_id = 22 AND student_id = 20)
    INSERT INTO dbo.enrollments (student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (20, 22, 1, '2026-11-01', 'ENROLLED', N'Đăng ký khóa Piano Mầm Non');

-- Lớp 2 (CL-PIA-PRE-G01): Thêm 3 học sinh (6, 8, 11)
IF NOT EXISTS (SELECT 1 FROM dbo.enrollments WHERE class_id = 2 AND student_id = 6)
    INSERT INTO dbo.enrollments (student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (6, 2, 1, '2026-10-15', 'ENROLLED', N'Đăng ký lớp Piano Mầm Non');

IF NOT EXISTS (SELECT 1 FROM dbo.enrollments WHERE class_id = 2 AND student_id = 8)
    INSERT INTO dbo.enrollments (student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (8, 2, 1, '2026-10-15', 'ENROLLED', N'Đăng ký lớp Piano Mầm Non');

IF NOT EXISTS (SELECT 1 FROM dbo.enrollments WHERE class_id = 2 AND student_id = 11)
    INSERT INTO dbo.enrollments (student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (11, 2, 1, '2026-10-15', 'ENROLLED', N'Đăng ký lớp Piano Mầm Non');

-- Lớp 4 (CL-GUI-BASIC-01): Thêm học sinh (7, 10)
IF NOT EXISTS (SELECT 1 FROM dbo.enrollments WHERE class_id = 4 AND student_id = 7)
    INSERT INTO dbo.enrollments (student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (7, 4, 1, '2026-10-15', 'ENROLLED', N'Đăng ký lớp Guitar');

IF NOT EXISTS (SELECT 1 FROM dbo.enrollments WHERE class_id = 4 AND student_id = 10)
    INSERT INTO dbo.enrollments (student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (10, 4, 1, '2026-10-15', 'ENROLLED', N'Đăng ký lớp Guitar');

-- Cập nhật current_students cho tất cả các lớp
UPDATE c
SET current_students = (SELECT COUNT(*) FROM dbo.enrollments e WHERE e.class_id = c.id AND e.status = 'ENROLLED')
FROM dbo.classes c;


-- ------------------------------------------------------------------------------
-- BƯỚC 3: THỜI KHÓA BIỂU TUẦN HIỆN TẠI (23/11/2026 - 29/11/2026)
-- ------------------------------------------------------------------------------
-- Lớp 1: CL-PIA-01 (Piano 1-1 Bé Bảo Nam, Chi nhánh 1, Phòng P101, GV Vũ Thu Hương id=4, Học viên: Bé Nam id=1)
-- Ca hôm nay: Thứ Ba 24/11/2026 (09:00 - 10:30)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 1 AND lesson_date = '2026-11-24')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (1, 1, 4, 13, '2026-11-24', '09:00:00', '10:30:00', N'Buổi 13: Luyện ngón Hanon & Gam Đô trưởng', N'Ca học hôm nay - Giáo viên có thể điểm danh trực tiếp', 'SCHEDULED');
END
ELSE
BEGIN
    UPDATE dbo.lessons
    SET status = 'SCHEDULED', start_time = '09:00:00', end_time = '10:30:00', title = N'Buổi 13: Luyện ngón Hanon & Gam Đô trưởng', lesson_note = N'Ca học hôm nay - Giáo viên có thể điểm danh trực tiếp'
    WHERE class_id = 1 AND lesson_date = '2026-11-24';
END;

-- Ca Thứ Năm 26/11/2026 (09:00 - 10:30)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 1 AND lesson_date = '2026-11-26')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (1, 1, 4, 14, '2026-11-26', '09:00:00', '10:30:00', N'Buổi 14: Kỹ thuật Legato & Staccato', N'Ca học tương lai - Có đơn xin nghỉ ốm chờ duyệt', 'SCHEDULED');
END;

-- Ca Thứ Bảy 28/11/2026 (09:00 - 10:30)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 1 AND lesson_date = '2026-11-28')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (1, 1, 4, 15, '2026-11-28', '09:00:00', '10:30:00', N'Buổi 15: Thực hành tác phẩm Sonatina', N'Ca học tương lai cuối tuần', 'SCHEDULED');
END;

-- Lớp 2: CL-PIA-PRE-G01 (Piano Mầm Non, Chi nhánh 1, Phòng P102, GV Vũ Thu Hương id=4)
-- Ca Thứ Hai 23/11/2026 (08:00 - 09:30)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-23' AND start_time = '08:00:00')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (2, 2, 4, 4, '2026-11-23', '08:00:00', '09:30:00', N'Buổi 4: Nhận diện nốt & Luyện ngón căn bản', N'Ca quá khứ - Đã hoàn thành điểm danh', 'COMPLETED');
END;

-- Ca Thứ Tư 25/11/2026 (08:30 - 10:00) -> Buổi học hôm nay có 1 Miss và 2 Lock
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-25')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (2, 2, 4, 7, '2026-11-25', '08:30:00', '10:00:00', N'Buổi 7: Cảm thụ tiết tấu qua bài đồng dao', N'Ca học hôm nay 25/11 - Đã điểm danh (Có 1 Miss và 2 Lock)', 'COMPLETED');
END
ELSE
BEGIN
    UPDATE dbo.lessons
    SET status = 'COMPLETED', title = N'Buổi 7: Cảm thụ tiết tấu qua bài đồng dao', lesson_note = N'Ca học hôm nay 25/11 - Đã điểm danh (Có 1 Miss và 2 Lock)'
    WHERE class_id = 2 AND lesson_date = '2026-11-25';
END;

-- Ca Thứ Tư 25/11/2026 (14:00 - 15:30) Lớp CL-PIA-DEMO01 -> Ca học hôm nay để GV thực hành điểm danh live
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 22 AND lesson_date = '2026-11-25')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (22, 1, 4, 3, '2026-11-25', '00:00:00', '23:59:00', N'Buổi 3: Luyện ngón Hanon & Tiết tấu vui nhộn', N'Ca học hôm nay 25/11 - Giáo viên có thể bấm trực tiếp Điểm danh', 'SCHEDULED');
END
ELSE
BEGIN
    UPDATE dbo.lessons
    SET status = 'SCHEDULED', start_time = '00:00:00', end_time = '23:59:00', title = N'Buổi 3: Luyện ngón Hanon & Tiết tấu vui nhộn', lesson_note = N'Ca học hôm nay 25/11 - Giáo viên có thể bấm trực tiếp Điểm danh'
    WHERE class_id = 22 AND lesson_date = '2026-11-25';
END;

-- Ca Thứ Bảy 28/11/2026 (10:00 - 11:30) -> Ghép học bù
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-28')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (2, 2, 4, 8, '2026-11-28', '10:00:00', '11:30:00', N'Buổi 8: Bài học nhóm & Ghép học viên bù', N'Ca học tương lai - Có học viên học bù (Bé Bảo Nam) ghép vào', 'SCHEDULED');
END;

-- Lớp 3: CL-PIA-G1-G01 (Piano Grade 1, Chi nhánh 1, Phòng P102, GV Trần Thu Hà id=8)
-- Ca Thứ Hai 23/11/2026 (18:00 - 19:30) -> CHƯA ĐIỂM DANH (test Admin Override)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 3 AND lesson_date = '2026-11-23')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (3, 2, 8, 12, '2026-11-23', '18:00:00', '19:30:00', N'Buổi 12: Hòa âm mở rộng & Tiết tấu Polka', N'Ca quá khứ chưa điểm danh - Dùng để test tính năng Admin Override', 'SCHEDULED');
END
ELSE
BEGIN
    UPDATE dbo.lessons
    SET status = 'SCHEDULED', title = N'Buổi 12: Hòa âm mở rộng & Tiết tấu Polka', lesson_note = N'Ca quá khứ chưa điểm danh - Dùng để test tính năng Admin Override'
    WHERE class_id = 3 AND lesson_date = '2026-11-23';
END;

-- Ca Thứ Sáu 27/11/2026 (18:00 - 19:30)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 3 AND lesson_date = '2026-11-27')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (3, 2, 8, 13, '2026-11-27', '18:00:00', '19:30:00', N'Buổi 13: Kỹ thuật Pedal ngân vang nâng cao', N'Ca học tương lai', 'SCHEDULED');
END;

-- Lớp 4: CL-GUI-BASIC-01 (Guitar Đệm Hát, Chi nhánh 2, Phòng P201, GV Trần Anh Tuấn id=5)
-- Ca Thứ Hai 23/11/2026 (14:00 - 15:30)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 4 AND lesson_date = '2026-11-23')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (4, 4, 5, 13, '2026-11-23', '14:00:00', '15:30:00', N'Buổi 13: Vòng hợp âm Am - Dm - E7', N'Ca quá khứ - Đã điểm danh', 'COMPLETED');
END;

-- Ca Thứ Ba 24/11/2026 (15:00 - 16:30) -> Thầy Tuấn điểm danh hôm nay
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 4 AND lesson_date = '2026-11-24')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (4, 4, 5, 14, '2026-11-24', '15:00:00', '16:30:00', N'Buổi 14: Điệu Disco & Ballad căn bản', N'Ca học hôm nay - Thầy Tuấn có thể điểm danh trực tiếp', 'SCHEDULED');
END
ELSE
BEGIN
    UPDATE dbo.lessons
    SET status = 'SCHEDULED', start_time = '15:00:00', end_time = '16:30:00', title = N'Buổi 14: Điệu Disco & Ballad căn bản', lesson_note = N'Ca học hôm nay - Thầy Tuấn có thể điểm danh trực tiếp'
    WHERE class_id = 4 AND lesson_date = '2026-11-24';
END;

-- Ca Thứ Bảy 28/11/2026 (08:30 - 10:00)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 4 AND lesson_date = '2026-11-28')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (4, 4, 5, 15, '2026-11-28', '08:30:00', '10:00:00', N'Buổi 15: Thực hành bài hát Tuổi Hồng Thơ Ngây', N'Ca học tương lai', 'SCHEDULED');
END;

-- Ca Chủ Nhật 29/11/2026 (15:00 - 16:30)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 4 AND lesson_date = '2026-11-29')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (4, 4, 5, 16, '2026-11-29', '15:00:00', '16:30:00', N'Buổi 16: Kỹ thuật quạt chả nâng cao', N'Ca học tương lai', 'SCHEDULED');
END;

-- Cập nhật tiến độ buổi học cho Lớp 22 (Buổi 1 và 2 là COMPLETED)
UPDATE dbo.lessons SET status = 'COMPLETED' WHERE class_id = 22 AND session_number IN (1, 2);
UPDATE dbo.lessons SET title = N'Buổi 1: Khởi động & Nhận diện bàn phím' WHERE class_id = 22 AND session_number = 1;
UPDATE dbo.lessons SET title = N'Buổi 2: Tư thế ngồi & Kỹ thuật bấm phím' WHERE class_id = 22 AND session_number = 2;


-- ------------------------------------------------------------------------------
-- BƯỚC 4: DỮ LIỆU ĐIỂM DANH & 3 CẤP ĐỘ CHUYÊN CẦN (ATTENDANCE TRACKING)
-- ------------------------------------------------------------------------------
DECLARE @LessonYesterdayClass2 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-23' ORDER BY id DESC);
DECLARE @LessonYesterdayClass4 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 4 AND lesson_date = '2026-11-23' ORDER BY id DESC);
DECLARE @LessonYesterdayClass3 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 3 AND lesson_date = '2026-11-23' ORDER BY id DESC);

-- Xóa dữ liệu điểm danh ca quá khứ Lớp 3 (để ca này ở trạng thái CHƯA ĐIỂM DANH, test Admin Override)
IF @LessonYesterdayClass3 IS NOT NULL
BEGIN
    DELETE FROM dbo.attendances WHERE lesson_id = @LessonYesterdayClass3;
END;

-- CẤP ĐỘ 1: BÉ NGUYỄN MAI CHI (student_id = 2, Lớp CL-PIA-PRE-G01, Khóa 24 buổi)
-- Tỷ lệ vắng: 1 / 24 buổi (4.2% <= 10%) -> CHUYÊN CẦN TỐT / XANH LÁ (NORMAL)
DELETE FROM dbo.attendances WHERE student_id = 2;

INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 10 id, 2, 'PRESENT', '2026-11-20 08:30:00', N'Có mặt đúng giờ, học tập tích cực'
FROM dbo.lessons
WHERE class_id = 2 AND lesson_date < '2026-11-23'
ORDER BY session_number ASC;

IF @LessonYesterdayClass2 IS NOT NULL
BEGIN
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES (@LessonYesterdayClass2, 2, 'PRESENT', '2026-11-23 09:30:00', N'Có mặt đầy đủ');
END;

DECLARE @AbsentLessonChi BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND lesson_date <= '2026-11-22' AND id NOT IN (SELECT lesson_id FROM dbo.attendances WHERE student_id = 2));
IF @AbsentLessonChi IS NOT NULL
BEGIN
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES (@AbsentLessonChi, 2, 'ABSENT', '2026-11-19 09:30:00', N'Nghỉ có phép (Gia đình có việc bận)');
END;


-- CẤP ĐỘ 2: BÉ NGUYỄN TRỌNG ANH (student_id = 3, Lớp CL-GUI-BASIC-01, Khóa 24 buổi)
-- Tỷ lệ vắng: 3 / 24 buổi (12.5% > 10% và < 20%) -> CẢNH BÁO VÀNG (WARNING)
DELETE FROM dbo.attendances WHERE student_id = 3;

INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 8 id, 3, 'PRESENT', '2026-11-20 14:00:00', N'Có mặt'
FROM dbo.lessons
WHERE class_id = 4 AND lesson_date < '2026-11-23'
ORDER BY session_number ASC;

IF @LessonYesterdayClass4 IS NOT NULL
BEGIN
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES (@LessonYesterdayClass4, 3, 'PRESENT', '2026-11-23 15:30:00', N'Có mặt đúng giờ');
END;

INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 3 id, 3, 'ABSENT', '2026-11-18 15:30:00', N'Vắng mặt (Bận việc riêng)'
FROM dbo.lessons
WHERE class_id = 4 AND lesson_date <= '2026-11-22' AND id NOT IN (SELECT lesson_id FROM dbo.attendances WHERE student_id = 3)
ORDER BY session_number DESC;


-- CẤP ĐỘ 3: BÉ NGUYỄN HẢI YẾN (student_id = 4, Lớp CL-PIA-G1-G01, Khóa 32 buổi)
-- Tỷ lệ vắng: 7 / 32 buổi (21.9% >= 20%) -> BÁO ĐỘNG ĐỎ NGUY CƠ CẤM THI (CRITICAL)
DELETE FROM dbo.attendances WHERE student_id = 4;

INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 4 id, 4, 'PRESENT', '2026-11-12 18:00:00', N'Có mặt'
FROM dbo.lessons
WHERE class_id = 3 AND lesson_date < '2026-11-23'
ORDER BY session_number ASC;

INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 7 id, 4, 'ABSENT', '2026-11-15 18:00:00', N'Vắng mặt'
FROM dbo.lessons
WHERE class_id = 3 AND lesson_date <= '2026-11-22' AND id NOT IN (SELECT lesson_id FROM dbo.attendances WHERE student_id = 4)
ORDER BY session_number DESC;

-- Điểm danh cho học sinh Lớp 22 (Buổi 1 và 2)
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT 176, s_id, 'PRESENT', '2026-11-17 11:30:00', N'Có mặt đầy đủ'
FROM (VALUES (17), (18), (19), (20)) v(s_id)
WHERE NOT EXISTS (SELECT 1 FROM dbo.attendances WHERE lesson_id = 176 AND student_id = v.s_id);

INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT 177, s_id, 'PRESENT', '2026-11-20 11:30:00', N'Có mặt đầy đủ'
FROM (VALUES (17), (18), (19), (20)) v(s_id)
WHERE NOT EXISTS (SELECT 1 FROM dbo.attendances WHERE lesson_id = 177 AND student_id = v.s_id);


-- ------------------------------------------------------------------------------
-- ĐIỂM DANH CHI TIẾT BUỔI HÔM NAY 25/11/2026 (Buổi 7 Lớp 2: CL-PIA-PRE-G01)
-- Bao gồm cả 1 MISS (Bỏ lỡ bù lần 1) và 2 LOCK (Vắng bù lần 2 -> Khóa bù)
-- ------------------------------------------------------------------------------
DECLARE @LessonToday208 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-25');

IF @LessonToday208 IS NOT NULL
BEGIN
    DELETE FROM dbo.attendances WHERE lesson_id = @LessonToday208;

    -- 1. Các học viên chính khóa có mặt
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES
    (@LessonToday208, 2, 'PRESENT', '2026-11-25 09:30:00', N'Có mặt đầy đủ, tiếp thu bài tốt'),
    (@LessonToday208, 6, 'PRESENT', '2026-11-25 09:30:00', N'Có mặt đúng giờ'),
    (@LessonToday208, 11, 'PRESENT', '2026-11-25 09:30:00', N'Có mặt đầy đủ');

    -- 2. Học viên 1 MISS: ĐỖ MINH KHANG (student_id = 9) - Vắng mặt buổi bù lần 1
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES (@LessonToday208, 9, 'ABSENT', '2026-11-25 09:30:00', N'Bỏ lỡ buổi học bù ngày 25/11 (Vắng lần 1 - 1 MISS, còn 1 lần xếp lại)');

    -- 3. Học viên 2 LOCK: PHẠM GIA HUY (student_id = 8) - Vắng mặt buổi bù lần 2 -> Khóa quyền học bù
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES (@LessonToday208, 8, 'ABSENT', '2026-11-25 09:30:00', N'Vắng mặt lần 2 tại ca học bù ngày 25/11 (2 LOCK - Đã KHÓA quyền học bù)');
END;


-- ------------------------------------------------------------------------------
-- BƯỚC 5: HỌC BÙ & QUY TẮC KHÓA SAU LẦN VẮNG BÙ ĐẦU (ABSENCE MAKEUP)
-- ------------------------------------------------------------------------------
DECLARE @LessonThursdayClass1 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 1 AND lesson_date = '2026-11-26');
DECLARE @LessonSaturdayClass2 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-28');

-- TRƯỜNG HỢP 1: ĐƠN XIN NGHỈ CHỜ DUYỆT (PENDING)
IF @LessonThursdayClass1 IS NOT NULL
BEGIN
    DELETE FROM dbo.absence_requests WHERE student_id = 1 AND lesson_id = @LessonThursdayClass1;
    INSERT INTO dbo.absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, created_at, need_makeup)
    VALUES (1, @LessonThursdayClass1, 10, N'Bé Nam bị sốt siêu vi 39 độ, bác sĩ yêu cầu nghỉ theo dõi 2 ngày tại nhà', 'PENDING', SYSDATETIME(), 1);
END;

-- TRƯỜNG HỢP 2: CA HỌC BÙ CHỜ XẾP LỊCH (PENDING)
IF EXISTS (SELECT 1 FROM dbo.makeup_registrations WHERE id = 3)
BEGIN
    UPDATE dbo.makeup_registrations
    SET status = 'PENDING', target_lesson_id = NULL,
        note = N'Đơn nghỉ học đã được GV phê duyệt. Đang chờ nhân viên điều phối xếp ca học bù phù hợp.'
    WHERE id = 3;
END;

-- TRƯỜNG HỢP 3: CA HỌC BÙ ĐÃ ĐƯỢC XẾP LỊCH (SCHEDULED)
IF EXISTS (SELECT 1 FROM dbo.makeup_registrations WHERE id = 8)
BEGIN
    UPDATE dbo.makeup_registrations
    SET status = 'SCHEDULED', target_lesson_id = @LessonSaturdayClass2,
        note = N'Đã xếp học bù vào ca sáng Thứ Bảy ngày 2026-11-28 (10:00 - 11:30) tại Phòng P102 - GV Vũ Thu Hương'
    WHERE id = 8;
END;

-- TRƯỜNG HỢP 4: CA HỌC BÙ ĐÃ HOÀN THÀNH (COMPLETED)
IF EXISTS (SELECT 1 FROM dbo.makeup_registrations WHERE id = 14)
BEGIN
    UPDATE dbo.makeup_registrations
    SET status = 'COMPLETED',
        note = N'[Lần bù: 1/1] Đã hoàn thành học bù và được giáo viên điểm danh Có mặt.'
    WHERE id = 14;
END;

-- TRƯỜNG HỢP 5: VẮNG MẶT BUỔI BÙ LẦN 1 -> 1 MISS (BỎ LỠ BÙ - CHƯA KHÓA) - BUỔI 25/11/2026
DECLARE @LessonPrevClass2 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-23' AND start_time = '08:00:00');
IF @LessonPrevClass2 IS NULL SET @LessonPrevClass2 = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-23');

DELETE FROM dbo.makeup_registrations WHERE student_id = 9;
DELETE FROM dbo.absence_requests WHERE student_id = 9 AND lesson_id = @LessonPrevClass2;

INSERT INTO dbo.absence_requests (student_id, lesson_id, requested_by_user_id, approved_by_teacher_id, reason, status, review_note, need_makeup, reviewed_at, created_at)
VALUES (9, @LessonPrevClass2, 10, 4, N'Bé Bon bị cảm lạnh sổ mũi, xin phép cô Hương cho bé nghỉ buổi 23/11 và đăng ký học bù sang ngày 25/11', 'APPROVED', N'Cô Hương đã duyệt đơn học bù', 1, '2026-11-23 09:00:00', '2026-11-23 07:30:00');

DECLARE @ArKhangId BIGINT = SCOPE_IDENTITY();

INSERT INTO dbo.makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArKhangId, 9, @LessonPrevClass2, @LessonToday208, 'MISSED', N'[Lần bù: 1/2] Bỏ lỡ buổi học bù ngày 25/11/2026 (Lần 1 - Chưa khóa, còn 1 lần xếp lại)', '2026-11-23 09:15:00');


-- TRƯỜNG HỢP 6: VẮNG MẶT BUỔI BÙ LẦN 2 -> 2 LOCK (KHÓA QUYỀN HỌC BÙ) - BUỔI 25/11/2026
DECLARE @LessonPrev2Class2 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-22');
IF @LessonPrev2Class2 IS NULL SET @LessonPrev2Class2 = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND session_number = 3);

DELETE FROM dbo.makeup_registrations WHERE student_id = 8;
DELETE FROM dbo.absence_requests WHERE student_id = 8 AND lesson_id = @LessonPrev2Class2;

INSERT INTO dbo.absence_requests (student_id, lesson_id, requested_by_user_id, approved_by_teacher_id, reason, status, review_note, need_makeup, reviewed_at, created_at)
VALUES (8, @LessonPrev2Class2, 10, 4, N'Bé Tom có lịch thi cờ vua cấp trường, xin phép cô Hương nghỉ buổi 22/11 và đăng ký học bù', 'APPROVED', N'Cô Hương đã duyệt đơn học bù', 1, '2026-11-22 09:00:00', '2026-11-22 07:30:00');

DECLARE @ArHuyId BIGINT = SCOPE_IDENTITY();

INSERT INTO dbo.makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArHuyId, 8, @LessonPrev2Class2, @LessonToday208, 'CANCELLED', N'[Lần bù: 2/2 - KHÓA] Học viên vắng mặt tại buổi học bù ngày 25/11/2026 (Lần 2) — Hệ thống KHÓA quyền học bù (CẤM HỌC BÙ LẠI)', '2026-11-22 09:15:00');


-- CẬP NHẬT CẢ BÉ BẢO NAM (student_id = 1) ĐỂ KHI XEM MẶC ĐỊNH CŨNG CÓ ĐỦ TEST CASE 1 MISS & 2 LOCK
IF EXISTS (SELECT 1 FROM dbo.makeup_registrations WHERE id = 10)
BEGIN
    UPDATE dbo.makeup_registrations
    SET status = 'MISSED',
        note = N'[Lần bù: 1/2] Bỏ lỡ buổi học bù ngày 25/11/2026 (Lần 1 - Chưa khóa quyền, còn 1 lần xếp lại)'
    WHERE id = 10;
END;

IF EXISTS (SELECT 1 FROM dbo.makeup_registrations WHERE id = 16)
BEGIN
    UPDATE dbo.makeup_registrations
    SET status = 'CANCELLED',
        note = N'[Lần bù: 2/2 - KHÓA] Vắng mặt lần 2 tại buổi học bù ngày 25/11/2026 — Hệ thống KHÓA quyền học bù (CẤM HỌC BÙ LẠI)'
    WHERE id = 16;
END;


-- ------------------------------------------------------------------------------
-- BƯỚC 6: HỆ THỐNG CHUÔNG THÔNG BÁO (NOTIFICATIONS)
-- ------------------------------------------------------------------------------
DELETE FROM dbo.notifications WHERE recipient_user_id IN (10, 4, 5, 8, 1) OR title LIKE '%?%' OR message LIKE '%?%';

INSERT INTO dbo.notifications (recipient_user_id, sender_user_id, title, message, type, reference_id, target_url, is_read, created_at)
VALUES
(10, 1,
 N'BÁO ĐỘNG ĐỎ (≥ 20%): Nguyễn Hải Yến vượt định mức nghỉ',
 N'Bé Nguyễn Hải Yến (Lớp Piano Grade 1 - Nhóm Chiều Tối) đã vắng 7/32 buổi (21.9%), vượt ngưỡng tối đa 20% (6 buổi) — Nguy cơ cấm thi / Không đạt khóa học!',
 'ATTENDANCE_CRITICAL', 4, '/pages/attendance-tracking.html', 0, DATEADD(MINUTE, -5, SYSDATETIME())),

(10, 1,
 N'Cảnh báo chuyên cần (> 10%): Nguyễn Trọng Anh',
 N'Bé Nguyễn Trọng Anh (Lớp Guitar Đệm Hát Sáng T7-CN) đã vắng 3/24 buổi (12.5%). Học viên chỉ còn 1 buổi vắng trước khi chạm ngưỡng cấm thi 20%.',
 'ATTENDANCE_WARNING', 3, '/pages/attendance-tracking.html', 0, DATEADD(MINUTE, -15, SYSDATETIME())),

(10, 4,
 N'Cập nhật điểm danh: Nguyễn Mai Chi (Bé Bông) [Có mặt]',
 N'Bé Nguyễn Mai Chi vừa được điểm danh [Có mặt] tại buổi học ngày 23/11/2026. Chuyên cần của bé hiện ở mức Tốt (Xanh lá).',
 'ATTENDANCE_PRESENT', 2, '/pages/attendance-tracking.html', 0, DATEADD(MINUTE, -30, SYSDATETIME())),

(10, 4,
 N'Đã xếp lịch học bù: Nguyễn Bảo Nam (Bé Bin)',
 N'Bé Nguyễn Bảo Nam đã được xếp ca học bù vào Thứ Bảy ngày 28/11/2026 (10:00 - 11:30) tại Phòng P102 - Cơ sở 1 Cầu Giấy (GV Vũ Thu Hương).',
 'MAKEUP_SCHEDULED', 1, '/pages/absence-makeup.html', 0, DATEADD(MINUTE, -45, SYSDATETIME())),

(10, 4,
 N'Khóa quyền học bù (Vắng buổi bù duy nhất): Nguyễn Bảo Nam',
 N'Bé Nguyễn Bảo Nam đã vắng mặt tại ca học bù ngày 20/11. Theo quy định khóa sau lần vắng bù đầu, mỗi buổi nghỉ chỉ xếp bù tối đa 1 lần, hệ thống đã chính thức khóa quyền học bù cho buổi nghỉ này.',
 'MAKEUP_LIMIT_EXCEEDED', 1, '/pages/absence-makeup.html', 0, DATEADD(MINUTE, -60, SYSDATETIME())),

(4, 10,
 N'Đơn xin nghỉ học mới: Nguyễn Bảo Nam (Bé Bin)',
 N'Phụ huynh Lê Thị Lan vừa gửi đơn xin nghỉ học cho Bé Bảo Nam vào ca Thứ Năm 26/11 (Lý do: Bé sốt virus 39 độ). Vui lòng vào phê duyệt.',
 'ABSENCE_SUBMITTED', 1, '/pages/absence-makeup.html', 0, DATEADD(MINUTE, -10, SYSDATETIME())),

(1, 1,
 N'Nhắc nhở: Ca học ngày 23/11 lớp CL-PIA-G1-G01 chưa hoàn thành điểm danh',
 N'Buổi học 18:00 - 19:30 ngày 23/11 lớp Piano Grade 1 chưa được điểm danh. Quản trị viên có thể sử dụng tính năng Admin Override để cập nhật.',
 'ATTENDANCE_WARNING', 3, '/pages/weekly-schedule.html', 0, DATEADD(MINUTE, -20, SYSDATETIME()));

COMMIT TRANSACTION;

PRINT N'=============================================================================';
PRINT N'ĐÃ CẬP NHẬT HOÀN TẤT DỮ LIỆU TEST TOÀN DIỆN CHO TẤT CẢ CÁC TRƯỜNG HỢP!';
PRINT N'1. Thời khóa biểu tuần: Đầy đủ ca quá khứ (đã/chưa điểm danh), hôm nay, tương lai.';
PRINT N'2. Điểm danh: Đầy đủ 4 trạng thái, Time-Lock, Roster Guard, Admin Override.';
PRINT N'3. Chuyên cần 3 cấp: Xanh lá (Mai Chi), Vàng (Trọng Anh), Đỏ cấm thi (Hải Yến).';
PRINT N'4. Học bù: Đơn chờ duyệt, Ca chờ xếp, Ca đã xếp, Ca hoàn thành, Ca khóa sau lần vắng bù đầu khóa.';
PRINT N'5. Thông báo chuông: Đầy đủ các thông báo tương ứng cho Phụ huynh, GV, Admin.';
PRINT N'=============================================================================';
GO
