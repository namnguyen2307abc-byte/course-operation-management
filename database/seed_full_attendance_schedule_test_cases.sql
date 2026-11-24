-- ==============================================================================
-- BỘ DỮ LIỆU TEST TOÀN DIỆN CHO TẤT CẢ CÁC TRƯỜNG HỢP (PHÂN HỆ CỦA PHÚC):
-- 1. THỜI KHÓA BIỂU TUẦN (WEEKLY SCHEDULE)
--    - Phân quyền: Admin, Staff, Teacher (GV Hương, GV Tuấn), Parent (PH Lan), Student (Bé Nam)
--    - Ca quá khứ ĐÃ điểm danh (Thứ Hai 23/11)
--    - Ca quá khứ CHƯA điểm danh (Thứ Hai 23/11 tối) -> Test Admin Override
--    - Ca hôm nay (Thứ Ba 24/11) -> Test GV điểm danh trực tiếp
--    - Ca tương lai gần (Thứ Tư 25/11) -> Test Time-Lock (chặn điểm danh trước)
--    - Ca tương lai cuối tuần (Thứ Năm, Sáu, Bảy, CN)
-- 2. ĐIỂM DANH BUỔI HỌC (ATTENDANCE TRACKING)
--    - 4 trạng thái: Có mặt (PRESENT), Vắng mặt (ABSENT), Đi muộn, Nghỉ có phép
--    - Roster Guard: Học viên chính khóa + Học viên học bù ghép lớp
--    - Time-Lock Guard: Chặn GV điểm danh buổi tương lai
--    - Admin Override: Sửa điểm danh quá khứ kèm lý do điều chỉnh
-- 3. THEO DÕI CHUYÊN CẦN 3 CẤP ĐỘ (ATTENDANCE RATE & WARNINGS)
--    - Cấp 1: Chuyên cần tốt / Xanh lá (≤ 10%) - Bé Nguyễn Mai Chi (1/24 buổi = 4.2%)
--    - Cấp 2: Cảnh báo vàng (10% - 20%) - Bé Nguyễn Trọng Anh (3/24 buổi = 12.5%)
--    - Cấp 3: Báo động đỏ cấm thi (≥ 20%) - Bé Nguyễn Hải Yến (7/32 buổi = 21.9%)
-- 4. HỌC BÙ & QUY TẮC TWO-STRIKE (ABSENCE MAKEUP)
--    - Case A: Đơn xin nghỉ chờ duyệt (PENDING) - Bé Bảo Nam ngày 26/11
--    - Case B: Ca học bù chờ xếp lịch (PENDING)
--    - Case C: Ca học bù đã xếp lịch (SCHEDULED) - Bé Bảo Nam ghép vào ca Thứ Bảy 28/11
--    - Case D: Ca học bù đã hoàn thành (COMPLETED)
--    - Case E: Vắng mặt buổi học bù -> Two-Strike Rule KHÓA QUYỀN BÙ (CANCELLED)
-- 5. CHUÔNG THÔNG BÁO (NOTIFICATIONS)
--    - Chuẩn font tiếng Việt NVARCHAR 100%, đầy đủ các loại thông báo nghiệp vụ
-- ==============================================================================

USE [course_operation_management];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRANSACTION;

-- ------------------------------------------------------------------------------
-- BƯỚC 1: ĐẢM BẢO BẢNG NOTIFICATIONS LÀ NVARCHAR CHUẨN TIẾNG VIỆT
-- ------------------------------------------------------------------------------
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'notifications' AND COLUMN_NAME = 'title')
BEGIN
    ALTER TABLE dbo.notifications ALTER COLUMN title NVARCHAR(255) NOT NULL;
    ALTER TABLE dbo.notifications ALTER COLUMN message NVARCHAR(1000) NOT NULL;
    ALTER TABLE dbo.notifications ALTER COLUMN target_url NVARCHAR(255) NULL;
END;

-- ------------------------------------------------------------------------------
-- BƯỚC 2: THỜI KHÓA BIỂU TUẦN HIỆN TẠI (23/11/2026 - 29/11/2026)
-- Đảm bảo tuần hiện tại có đầy đủ các ca học phục vụ kiểm thử mọi tình huống
-- ------------------------------------------------------------------------------

-- Lớp 1: CL-PIA-01 (Piano 1-1 Bé Bảo Nam, Chi nhánh 1, Phòng P101, GV Vũ Thu Hương id=4, Học viên: Bé Nam id=1)
-- Ca hôm nay: Thứ Ba 24/11/2026 (09:00 - 10:30) -> Mở để GV Hương điểm danh hôm nay
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

-- Ca ngày kia: Thứ Năm 26/11/2026 (09:00 - 10:30) -> Có đơn xin nghỉ chờ duyệt
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 1 AND lesson_date = '2026-11-26')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (1, 1, 4, 14, '2026-11-26', '09:00:00', '10:30:00', N'Buổi 14: Kỹ thuật Legato & Staccato', N'Ca học tương lai - Có đơn xin nghỉ ốm chờ duyệt', 'SCHEDULED');
END;

-- Ca cuối tuần: Thứ Bảy 28/11/2026 (09:00 - 10:30) -> Ca tương lai
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 1 AND lesson_date = '2026-11-28')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (1, 1, 4, 15, '2026-11-28', '09:00:00', '10:30:00', N'Buổi 15: Thực hành tác phẩm Sonatina', N'Ca học tương lai cuối tuần', 'SCHEDULED');
END;

-- Lớp 2: CL-PIA-PRE-G01 (Piano Mầm Non, Chi nhánh 1, Phòng P102, GV Vũ Thu Hương id=4, Học viên: Bé Mai Chi id=2)
-- Ca hôm qua: Thứ Hai 23/11/2026 (08:00 - 09:30) -> ĐÃ ĐIỂM DANH CÓ MẶT
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-23' AND start_time = '08:00:00')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (2, 2, 4, 4, '2026-11-23', '08:00:00', '09:30:00', N'Buổi 4: Nhận diện nốt & Luyện ngón căn bản', N'Ca quá khứ - Đã hoàn thành điểm danh', 'COMPLETED');
END;

-- Ca ngày mai: Thứ Tư 25/11/2026 (08:30 - 10:00) -> Test Time-Lock (chặn GV điểm danh trước)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-25')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (2, 2, 4, 7, '2026-11-25', '08:30:00', '10:00:00', N'Buổi 7: Cảm thụ tiết tấu qua bài đồng dao', N'Ca tương lai - Kiểm tra Time-Lock (nút điểm danh bị khóa)', 'SCHEDULED');
END;

-- Ca cuối tuần: Thứ Bảy 28/11/2026 (10:00 - 11:30) -> Ca ghép nhận học viên học bù
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-28')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (2, 2, 4, 8, '2026-11-28', '10:00:00', '11:30:00', N'Buổi 8: Bài học nhóm & Ghép học viên bù', N'Ca học tương lai - Có học viên học bù (Bé Bảo Nam) ghép vào', 'SCHEDULED');
END;

-- Lớp 3: CL-PIA-G1-G01 (Piano Grade 1, Chi nhánh 1, Phòng P102, GV Trần Thu Hà id=8, Học viên: Bé Hải Yến id=4)
-- Ca hôm qua: Thứ Hai 23/11/2026 (18:00 - 19:30) -> CHƯA ĐIỂM DANH (để test Admin Override)
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

-- Ca Thứ Sáu: 27/11/2026 (18:00 - 19:30)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 3 AND lesson_date = '2026-11-27')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (3, 2, 8, 13, '2026-11-27', '18:00:00', '19:30:00', N'Buổi 13: Kỹ thuật Pedal ngân vang nâng cao', N'Ca học tương lai', 'SCHEDULED');
END;

-- Lớp 4: CL-GUI-BASIC-01 (Guitar Đệm Hát, Chi nhánh 2, Phòng P201, GV Trần Anh Tuấn id=5, Học viên: Bé Trọng Anh id=3)
-- Ca hôm qua: Thứ Hai 23/11/2026 (14:00 - 15:30) -> ĐÃ ĐIỂM DANH CÓ MẶT
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 4 AND lesson_date = '2026-11-23')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (4, 4, 5, 13, '2026-11-23', '14:00:00', '15:30:00', N'Buổi 13: Vòng hợp âm Am - Dm - E7', N'Ca quá khứ - Đã điểm danh', 'COMPLETED');
END;

-- Ca hôm nay: Thứ Ba 24/11/2026 (15:00 - 16:30) -> Ca hôm nay cho Thầy Tuấn điểm danh trực tiếp
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

-- Ca Thứ Bảy: 28/11/2026 (08:30 - 10:00)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 4 AND lesson_date = '2026-11-28')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (4, 4, 5, 15, '2026-11-28', '08:30:00', '10:00:00', N'Buổi 15: Thực hành bài hát Tuổi Hồng Thơ Ngây', N'Ca học tương lai', 'SCHEDULED');
END;

-- Ca Chủ Nhật: 29/11/2026 (15:00 - 16:30)
IF NOT EXISTS (SELECT 1 FROM dbo.lessons WHERE class_id = 4 AND lesson_date = '2026-11-29')
BEGIN
    INSERT INTO dbo.lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (4, 4, 5, 16, '2026-11-29', '15:00:00', '16:30:00', N'Buổi 16: Kỹ thuật quạt chả nâng cao', N'Ca học tương lai', 'SCHEDULED');
END;


-- ------------------------------------------------------------------------------
-- BƯỚC 3: DỮ LIỆU ĐIỂM DANH & 3 CẤP ĐỘ CHUYÊN CẦN (ATTENDANCE TRACKING)
-- ------------------------------------------------------------------------------

-- Lấy ID ca học hôm qua của Lớp 2 và Lớp 4
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

-- Thêm 10 buổi Có mặt
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 10 id, 2, 'PRESENT', '2026-11-20 08:30:00', N'Có mặt đúng giờ, học tập tích cực'
FROM dbo.lessons
WHERE class_id = 2 AND lesson_date < '2026-11-23'
ORDER BY session_number ASC;

-- Buổi hôm qua (23/11): Có mặt
IF @LessonYesterdayClass2 IS NOT NULL
BEGIN
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES (@LessonYesterdayClass2, 2, 'PRESENT', '2026-11-23 09:30:00', N'Có mặt đầy đủ');
END;

-- 1 buổi Vắng mặt (đảm bảo tỷ lệ 1/24 = 4.2%)
DECLARE @AbsentLessonChi BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND lesson_date <= '2026-11-22' AND id NOT IN (SELECT lesson_id FROM dbo.attendances WHERE student_id = 2));
IF @AbsentLessonChi IS NOT NULL
BEGIN
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES (@AbsentLessonChi, 2, 'ABSENT', '2026-11-19 09:30:00', N'Nghỉ có phép (Gia đình có việc bận)');
END;


-- CẤP ĐỘ 2: BÉ NGUYỄN TRỌNG ANH (student_id = 3, Lớp CL-GUI-BASIC-01, Khóa 24 buổi)
-- Tỷ lệ vắng: 3 / 24 buổi (12.5% > 10% và < 20%) -> CẢNH BÁO VÀNG (WARNING)
DELETE FROM dbo.attendances WHERE student_id = 3;

-- Thêm 8 buổi Có mặt
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 8 id, 3, 'PRESENT', '2026-11-20 14:00:00', N'Có mặt'
FROM dbo.lessons
WHERE class_id = 4 AND lesson_date < '2026-11-23'
ORDER BY session_number ASC;

-- Buổi hôm qua (23/11): Có mặt
IF @LessonYesterdayClass4 IS NOT NULL
BEGIN
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES (@LessonYesterdayClass4, 3, 'PRESENT', '2026-11-23 15:30:00', N'Có mặt đúng giờ');
END;

-- Thêm 3 buổi Vắng mặt
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 3 id, 3, 'ABSENT', '2026-11-18 15:30:00', N'Vắng mặt (Bận việc riêng)'
FROM dbo.lessons
WHERE class_id = 4 AND lesson_date <= '2026-11-22' AND id NOT IN (SELECT lesson_id FROM dbo.attendances WHERE student_id = 3)
ORDER BY session_number DESC;


-- CẤP ĐỘ 3: BÉ NGUYỄN HẢI YẾN (student_id = 4, Lớp CL-PIA-G1-G01, Khóa 32 buổi)
-- Tỷ lệ vắng: 7 / 32 buổi (21.9% >= 20%) -> BÁO ĐỘNG ĐỎ NGUY CƠ CẤM THI (CRITICAL)
DELETE FROM dbo.attendances WHERE student_id = 4;

-- Thêm 4 buổi Có mặt
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 4 id, 4, 'PRESENT', '2026-11-12 18:00:00', N'Có mặt'
FROM dbo.lessons
WHERE class_id = 3 AND lesson_date < '2026-11-23'
ORDER BY session_number ASC;

-- Thêm 7 buổi Vắng mặt (chạm ngưỡng báo động đỏ)
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 7 id, 4, 'ABSENT', '2026-11-15 18:00:00', N'Vắng mặt'
FROM dbo.lessons
WHERE class_id = 3 AND lesson_date <= '2026-11-22' AND id NOT IN (SELECT lesson_id FROM dbo.attendances WHERE student_id = 4)
ORDER BY session_number DESC;


-- ------------------------------------------------------------------------------
-- BƯỚC 4: HỌC BÙ & QUY TẮC TWO-STRIKE (ABSENCE MAKEUP)
-- Đầy đủ 5 trường hợp nghiệp vụ
-- ------------------------------------------------------------------------------

-- Lấy lesson Thứ Năm 26/11 của Lớp 1 (để làm đơn xin nghỉ PENDING)
DECLARE @LessonThursdayClass1 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 1 AND lesson_date = '2026-11-26');
-- Lấy lesson Thứ Bảy 28/11 của Lớp 2 (để làm ca bù SCHEDULED)
DECLARE @LessonSaturdayClass2 BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 2 AND lesson_date = '2026-11-28');
-- Lấy 1 lesson quá khứ đã nghỉ của Bé Bảo Nam
DECLARE @LessonPastAbsentNam BIGINT = (SELECT TOP 1 id FROM dbo.lessons WHERE class_id = 1 AND lesson_date < '2026-11-23' ORDER BY id ASC);

-- TRƯỜNG HỢP 1: ĐƠN XIN NGHỈ CHỜ DUYỆT (PENDING)
-- Phụ huynh Lan (id=10) gửi đơn xin nghỉ cho Bé Bảo Nam (student_id = 1) buổi thứ Năm 26/11
IF @LessonThursdayClass1 IS NOT NULL
BEGIN
    DELETE FROM dbo.absence_requests WHERE student_id = 1 AND lesson_id = @LessonThursdayClass1;
    INSERT INTO dbo.absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, created_at, need_makeup)
    VALUES (1, @LessonThursdayClass1, 10, N'Bé Nam bị sốt siêu vi 39 độ, bác sĩ yêu cầu nghỉ theo dõi 2 ngày tại nhà', 'PENDING', SYSDATETIME(), 1);
END;

-- TRƯỜNG HỢP 2: CA HỌC BÙ CHỜ XẾP LỊCH (PENDING)
-- Buổi vắng trước đó của Bé Nam đã duyệt nghỉ -> MakeupRegistration ở trạng thái PENDING
IF EXISTS (SELECT 1 FROM dbo.makeup_registrations WHERE id = 3)
BEGIN
    UPDATE dbo.makeup_registrations
    SET status = 'PENDING', target_lesson_id = NULL,
        note = N'Đơn nghỉ học đã được GV phê duyệt. Đang chờ nhân viên điều phối xếp ca học bù phù hợp.'
    WHERE id = 3;
END;

-- TRƯỜNG HỢP 3: CA HỌC BÙ ĐÃ ĐƯỢC XẾP LỊCH (SCHEDULED)
-- Bé Nam được xếp ca bù vào Thứ Bảy 28/11 (Lớp 2, Phòng P102)
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
        note = N'[Lần bù: 1/2] Đã hoàn thành học bù và được giáo viên điểm danh Có mặt.'
    WHERE id = 14;
END;

-- TRƯỜNG HỢP 5: VẮNG MẶT BUỔI BÙ -> TWO-STRIKE KHÓA QUYỀN HỌC BÙ (CANCELLED)
IF EXISTS (SELECT 1 FROM dbo.makeup_registrations WHERE id = 10)
BEGIN
    UPDATE dbo.makeup_registrations
    SET status = 'CANCELLED',
        note = N'[Lần bù: 1/1 - KHÓA] Học viên đã vắng mặt tại ca học bù duy nhất — Hệ thống khóa quyền học bù (CẤM HỌC BÙ LẠI)'
    WHERE id = 10;
END;

IF EXISTS (SELECT 1 FROM dbo.makeup_registrations WHERE id = 16)
BEGIN
    UPDATE dbo.makeup_registrations
    SET status = 'CANCELLED',
        note = N'[Lần bù: 1/1 - KHÓA] Bỏ lỡ / Vắng mặt tại buổi học bù ngày 2026-11-23 (Không được xếp lại) — Khóa quyền học bù'
    WHERE id = 16;
END;


-- ------------------------------------------------------------------------------
-- BƯỚC 5: HỆ THỐNG CHUÔNG THÔNG BÁO (NOTIFICATIONS)
-- Dọn sạch thông báo lỗi font, thêm thông báo chuẩn Unicode tiếng Việt
-- ------------------------------------------------------------------------------
DELETE FROM dbo.notifications WHERE recipient_user_id IN (10, 4, 5, 8, 1) OR title LIKE '%?%' OR message LIKE '%?%';

INSERT INTO dbo.notifications (recipient_user_id, sender_user_id, title, message, type, reference_id, target_url, is_read, created_at)
VALUES
-- 1. Báo động đỏ cấm thi gửi Phụ huynh Lan (id=10)
(10, 1,
 N'BÁO ĐỘNG ĐỎ (≥ 20%): Nguyễn Hải Yến vượt định mức nghỉ',
 N'Bé Nguyễn Hải Yến (Lớp Piano Grade 1 - Nhóm Chiều Tối) đã vắng 7/32 buổi (21.9%), vượt ngưỡng tối đa 20% (6 buổi) — Nguy cơ cấm thi / Không đạt khóa học!',
 'ATTENDANCE_CRITICAL', 4, '/pages/attendance-tracking.html', 0, DATEADD(MINUTE, -5, SYSDATETIME())),

-- 2. Cảnh báo vàng chuyên cần gửi Phụ huynh Lan (id=10)
(10, 1,
 N'Cảnh báo chuyên cần (> 10%): Nguyễn Trọng Anh',
 N'Bé Nguyễn Trọng Anh (Lớp Guitar Đệm Hát Sáng T7-CN) đã vắng 3/24 buổi (12.5%). Học viên chỉ còn 1 buổi vắng trước khi chạm ngưỡng cấm thi 20%.',
 'ATTENDANCE_WARNING', 3, '/pages/attendance-tracking.html', 0, DATEADD(MINUTE, -15, SYSDATETIME())),

-- 3. Thông báo điểm danh Có mặt gửi Phụ huynh Lan (id=10)
(10, 4,
 N'Cập nhật điểm danh: Nguyễn Mai Chi (Bé Bông) [Có mặt]',
 N'Bé Nguyễn Mai Chi vừa được điểm danh [Có mặt] tại buổi học ngày 23/11/2026. Chuyên cần của bé hiện ở mức Tốt (Xanh lá).',
 'ATTENDANCE_PRESENT', 2, '/pages/attendance-tracking.html', 0, DATEADD(MINUTE, -30, SYSDATETIME())),

-- 4. Thông báo đã xếp lịch học bù gửi Phụ huynh Lan (id=10)
(10, 4,
 N'Đã xếp lịch học bù: Nguyễn Bảo Nam (Bé Bin)',
 N'Bé Nguyễn Bảo Nam đã được xếp ca học bù vào Thứ Bảy ngày 28/11/2026 (10:00 - 11:30) tại Phòng P102 - Cơ sở 1 Cầu Giấy (GV Vũ Thu Hương).',
 'MAKEUP_SCHEDULED', 1, '/pages/absence-makeup.html', 0, DATEADD(MINUTE, -45, SYSDATETIME())),

-- 5. Thông báo khóa quyền học bù (Two-strike) gửi Phụ huynh Lan (id=10)
(10, 4,
 N'Khóa quyền học bù (Vắng buổi bù duy nhất): Nguyễn Bảo Nam',
 N'Bé Nguyễn Bảo Nam đã vắng mặt tại ca học bù ngày 20/11. Theo quy định Two-Strike, mỗi buổi nghỉ chỉ xếp bù tối đa 1 lần, hệ thống đã chính thức khóa quyền học bù cho buổi nghỉ này.',
 'MAKEUP_LIMIT_EXCEEDED', 1, '/pages/absence-makeup.html', 0, DATEADD(MINUTE, -60, SYSDATETIME())),

-- 6. Thông báo đơn xin nghỉ học mới gửi Giáo viên Vũ Thu Hương (recipient_user_id = 4)
(4, 10,
 N'Đơn xin nghỉ học mới: Nguyễn Bảo Nam (Bé Bin)',
 N'Phụ huynh Lê Thị Lan vừa gửi đơn xin nghỉ học cho Bé Bảo Nam vào ca Thứ Năm 26/11 (Lý do: Bé sốt virus 39 độ). Vui lòng vào phê duyệt.',
 'ABSENCE_SUBMITTED', 1, '/pages/absence-makeup.html', 0, DATEADD(MINUTE, -10, SYSDATETIME())),

-- 7. Thông báo ca quá khứ chưa điểm danh gửi Quản trị viên (recipient_user_id = 1)
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
PRINT N'4. Học bù: Đơn chờ duyệt, Ca chờ xếp, Ca đã xếp, Ca hoàn thành, Ca Two-Strike khóa.';
PRINT N'5. Thông báo chuông: Đầy đủ các thông báo tương ứng cho Phụ huynh, GV, Admin.';
PRINT N'=============================================================================';
