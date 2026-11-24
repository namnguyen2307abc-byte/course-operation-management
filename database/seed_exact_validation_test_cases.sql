-- ==============================================================================
-- SEED DỮ LIỆU TEST CHUẨN XÁC CHO TOÀN BỘ CÁC TRƯỜNG HỢP VALIDATE NGHIỆP VỤ
-- Bao gồm:
-- 1. Chưa cảnh báo (Xanh lá - Chuyên cần tốt <= 10%)
-- 2. Cảnh báo vàng (Vàng - Cảnh báo chuyên cần 10% - 20%)
-- 3. Cảnh báo đỏ (Đỏ - Báo động đỏ nguy cơ cấm thi >= 20%)
-- 4. Nghỉ 1 buổi học bù bị khóa (Vắng buổi bù - [Lần bù: 1/1 - KHÓA])
-- 5. Vắng buổi học bù duy nhất bị CẤM HỌC BÙ (Khóa quyền bù - CANCELLED [Lần bù: 1/1 - KHÓA])
-- 6. Khắc phục triệt để lỗi font chữ tiếng Việt (sử dụng NVARCHAR và prefix N'')
-- ==============================================================================

SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- 1. Đảm bảo bảng notifications sử dụng NVARCHAR chuẩn xác
ALTER TABLE dbo.notifications ALTER COLUMN title NVARCHAR(255) NOT NULL;
ALTER TABLE dbo.notifications ALTER COLUMN message NVARCHAR(1000) NOT NULL;
ALTER TABLE dbo.notifications ALTER COLUMN target_url NVARCHAR(255) NULL;

-- 2. Dọn sạch các thông báo bị lỗi font dấu hỏi (?) cũ của phụ huynh Lan (id=10) và admin/teacher
DELETE FROM dbo.notifications WHERE recipient_user_id IN (10, 4, 1) OR title LIKE '%?%' OR message LIKE '%?%';

-- 3. Cập nhật dữ liệu điểm danh cho 3 con của Phụ huynh Lan để hiển thị chuẩn 3 cấp độ:
-- ------------------------------------------------------------------------------
-- Trường hợp 1: Bé Nguyễn Mai Chi (student_id = 2, Lớp CL-PIA-PRE-G01, Khóa 24 buổi)
-- Tỷ lệ vắng: 1 / 24 buổi (4.2% <= 10%) -> CHƯA CẢNH BÁO (CHUYÊN CẦN TỐT - XANH LÁ)
DELETE FROM dbo.attendances WHERE student_id = 2;
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 10 id, 2, 'PRESENT', '2026-11-23 08:00:00', N'Học tập tích cực'
FROM dbo.lessons WHERE class_id = 2 AND id != 200 AND id != 201 AND id != 202 AND id != 203
ORDER BY session_number ASC;

-- Ghi nhận 1 buổi vắng (Lesson 200 ngày 22/11/2026)
IF EXISTS (SELECT 1 FROM dbo.lessons WHERE id = 200)
BEGIN
    INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
    VALUES (200, 2, 'ABSENT', '2026-11-22 09:30:00', N'Nghỉ có phép');
END;

-- ------------------------------------------------------------------------------
-- Trường hợp 2: Bé Nguyễn Trọng Anh (student_id = 3, Lớp CL-GUI-BASIC-01, Khóa 24 buổi)
-- Tỷ lệ vắng: 3 / 24 buổi (12.5% > 10% và < 20%) -> CẢNH BÁO VÀNG (WARNING)
DELETE FROM dbo.attendances WHERE student_id = 3;
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 8 id, 3, 'PRESENT', '2026-11-20 08:00:00', N'Có mặt đúng giờ'
FROM dbo.lessons WHERE class_id = 4
ORDER BY session_number ASC;

-- Thêm 3 buổi vắng mặt
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 3 id, 3, 'ABSENT', '2026-11-18 08:00:00', N'Vắng mặt (Bận lịch gia đình)'
FROM dbo.lessons WHERE class_id = 4 AND id NOT IN (SELECT lesson_id FROM dbo.attendances WHERE student_id = 3)
ORDER BY session_number DESC;

-- ------------------------------------------------------------------------------
-- Trường hợp 3: Bé Nguyễn Hải Yến (student_id = 4, Lớp CL-PIA-G1-G01, Khóa 32 buổi)
-- Tỷ lệ vắng: 7 / 32 buổi (21.9% >= 20%) -> BÁO ĐỘNG ĐỎ NGUY CƠ CẤM THI (CRITICAL)
DELETE FROM dbo.attendances WHERE student_id = 4;
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 4 id, 4, 'PRESENT', '2026-11-20 18:00:00', N'Có mặt'
FROM dbo.lessons WHERE class_id = 3
ORDER BY session_number ASC;

-- Thêm 7 buổi vắng mặt (chạm ngưỡng báo động đỏ)
INSERT INTO dbo.attendances (lesson_id, student_id, status, marked_at, note)
SELECT TOP 7 id, 4, 'ABSENT', '2026-11-15 18:00:00', N'Vắng mặt'
FROM dbo.lessons WHERE class_id = 3 AND id NOT IN (SELECT lesson_id FROM dbo.attendances WHERE student_id = 4)
ORDER BY session_number DESC;

-- ------------------------------------------------------------------------------
-- Trường hợp 4 & 5: Quy tắc một lần xếp lịch học bù cho Bé Nguyễn Bảo Nam (student_id = 1)
-- Vắng buổi bù: Vắng buổi bù duy nhất -> CANCELLED ([Lần bù: 1/1 - KHÓA])
-- Khóa quyền bù: Vắng buổi bù duy nhất -> CANCELLED ([Lần bù: 1/1 - KHÓA]) -> CẤM HỌC BÙ!
-- ------------------------------------------------------------------------------

-- Vắng buổi bù: Ca học bù #16 chuyển sang CANCELLED (Không được xếp lại)
UPDATE dbo.makeup_registrations
SET status = 'CANCELLED',
    note = N'[Lần bù: 1/1 - KHÓA] Bỏ lỡ / Vắng mặt tại buổi học bù ngày 2026-11-23 (Không được xếp lại) - Lý do: Bận việc gia đình'
WHERE id = 16;

-- Khóa quyền bù: Ca học bù #10 cập nhật thành CANCELLED / KHÓA (Cấm học bù do đã vắng buổi bù duy nhất)
UPDATE dbo.makeup_registrations
SET status = 'CANCELLED',
    target_lesson_id = 200,
    note = N'[Lần bù: 1/1 - KHÓA] Hủy quyền học bù do học viên đã vắng mặt tại ca học bù duy nhất (ngày 2026-11-22) - Đã hết 1 lần bù cho phép (CẤM HỌC BÙ)'
WHERE id = 10;

-- 4. Chèn danh sách thông báo tiếng Việt chuẩn 100% không lỗi font cho Phụ Huynh Lê Thị Lan (recipient_user_id = 10):
INSERT INTO dbo.notifications (recipient_user_id, sender_user_id, title, message, type, reference_id, target_url, is_read, created_at)
VALUES
-- Thông báo 1: Cảnh báo đỏ cấm thi
(10, 1, 
 N'BÁO ĐỘNG ĐỎ (≥ 20%): Nguyễn Hải Yến vượt định mức nghỉ',
 N'Bé Nguyễn Hải Yến (Lớp Piano Grade 1 - Nhóm Chiều Tối) đã vắng 7/32 buổi (21.9%), chạm/vượt ngưỡng tối đa 20% (6 buổi) — Nguy cơ cấm thi / Không đạt khóa học!',
 'ATTENDANCE_CRITICAL', 4, '/pages/attendance-tracking.html', 0, DATEADD(MINUTE, -10, SYSDATETIME())),

-- Thông báo 2: Cảnh báo vàng
(10, 1,
 N'Cảnh báo chuyên cần (> 10%): Nguyễn Trọng Anh',
 N'Bé Nguyễn Trọng Anh (Lớp Guitar Đệm Hát Sáng T7-CN) đã vắng 3/24 buổi (12.5%). Học viên chỉ còn 1/4 buổi phép trước khi chạm ngưỡng cấm thi 20%.',
 'ATTENDANCE_WARNING', 3, '/pages/attendance-tracking.html', 0, DATEADD(MINUTE, -25, SYSDATETIME())),

-- Thông báo 3: Nghỉ 1 buổi học bù bị khóa (Vắng buổi bù)
(10, 4,
 N'Cảnh báo bỏ lỡ học bù (Lần 1/1): Nguyễn Bảo Nam (Bé Bin)',
 N'Bé Nguyễn Bảo Nam (Bé Bin) đã vắng mặt tại buổi học bù ngày 2026-11-23. Theo quy định, mỗi buổi nghỉ chỉ được xếp bù 1 lần, nên quyền xếp lại đã bị khóa (tối đa 3 lượt bù/khóa).',
 'MAKEUP_LIMIT_EXCEEDED', 16, '/pages/absence-makeup.html', 0, DATEADD(MINUTE, -40, SYSDATETIME())),

-- Thông báo 4: Vắng buổi học bù duy nhất bị CẤM HỌC BÙ (Khóa quyền bù)
(10, 4,
 N'Kết thúc quyền học bù (Đã dùng lần bù duy nhất - CẤM HỌC BÙ): Nguyễn Bảo Nam (Bé Bin)',
 N'Bé Nguyễn Bảo Nam (Bé Bin) đã vắng mặt tại ca học bù duy nhất. Theo quy định, mỗi buổi nghỉ chỉ xếp bù tối đa 1 lần. Do học viên đã vắng mặt buổi bù duy nhất nên hệ thống đã chính thức khóa quyền học bù cho buổi nghỉ này.',
 'MAKEUP_LIMIT_EXCEEDED', 10, '/pages/absence-makeup.html', 0, DATEADD(MINUTE, -55, SYSDATETIME())),

-- Thông báo 5: Cập nhật điểm danh bình thường (Chuyên cần tốt)
(10, 4,
 N'Cập nhật điểm danh: Nguyễn Mai Chi (Bé Bông) (Có mặt)',
 N'Bé Nguyễn Mai Chi (Bé Bông) vừa được điểm danh [Có mặt] tại buổi học ngày 2026-11-23. Chuyên cần của bé hiện đang ở mức tốt.',
 'ATTENDANCE_PRESENT', 201, '/pages/attendance-tracking.html', 0, DATEADD(MINUTE, -70, SYSDATETIME()));

COMMIT TRANSACTION;
