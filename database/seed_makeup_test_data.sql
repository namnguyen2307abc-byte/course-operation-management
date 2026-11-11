-- =============================================================================
-- SEED DATA: DỮ LIỆU MẪU KIỂM THỬ TOÀN DIỆN LUỒNG XIN NGHỈ & HỌC BÙ
-- Tự động neo ngày theo GETDATE() để luôn có lịch tuần này / hôm nay / sắp tới
-- Tài khoản đăng nhập (mật khẩu mặc định theo hệ thống, thường 123456):
--   - Admin : admin
--   - Staff : staff_minh / cashier_mai
--   - GV    : teacher_huong (Piano), teacher_ha (Piano), teacher_tuan (Guitar),
--             teacher_dung (Guitar), teacher_linh (Múa), teacher_long (Võ)
--   - PH    : parent_lan (Bé Bảo Nam #1, Trọng Anh #3, Hải Yến #4),
--             parent_hoang (Bé Ngọc Hân #6), parent_dung (Bé Tuấn Kiệt #7)
-- =============================================================================

USE course_operation_management;
GO

SET NOCOUNT ON;

DECLARE @Today DATE = CAST(GETDATE() AS DATE);
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

-- Chuẩn hóa dữ liệu cũ nếu từng tồn tại trạng thái đã loại bỏ
UPDATE absence_requests SET status = 'APPROVED' WHERE status IN ('EXCUSED', 'SPECIAL_APPROVED');
UPDATE makeup_registrations SET status = 'SCHEDULED' WHERE status = 'REGISTERED';

-- =============================================================================
-- 1. TẠO BUỔI HỌC TUẦN HIỆN TẠI & TUẦN TỚI (NEO THEO @Today)
-- =============================================================================

-- Class 1: Piano 1-1 Bé Bảo Nam (Course 2 - Grade 1, GV Hường id=4, P101 id=1)
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 1 AND lesson_date = @D_M2 AND session_number = 10)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (1, 1, 4, 10, @D_M2, '18:00', '19:30', N'[TEST] Buổi 10: Luyện âm giai & Tiết tấu 4/4', N'Kiểm tra bài cũ + ghép 2 tay', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 1 AND lesson_date = @D_P2 AND session_number = 11)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (1, 1, 4, 11, @D_P2, '18:00', '19:30', N'[TEST] Buổi 11: Hợp âm ba & Đọc bản nhạc mới', N'Chuẩn bị giáo trình Alfred Level 1', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 1 AND lesson_date = @D_P5 AND session_number = 12)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (1, 1, 4, 12, @D_P5, '18:00', '19:30', N'[TEST] Buổi 12: Tổng hợp tác phẩm Grade 1', N'Buổi sắp tới để phụ huynh test gửi đơn mới', 'SCHEDULED');

-- Class 3: Piano Grade 1 - Nhóm Chiều Tối (Course 2 - Grade 1, GV Hà id=8, P102 id=2) - bé Hải Yến (#4)
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 3 AND lesson_date = @D_M1 AND session_number = 10)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (3, 2, 8, 10, @D_M1, '19:30', '21:00', N'[TEST] Buổi 10: Luyện âm giai & Hợp âm (Nhóm tối)', N'Buổi bù lần 1 bị bỏ lỡ', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 3 AND lesson_date = @D_P1 AND session_number = 11)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (3, 2, 8, 11, @D_P1, '19:30', '21:00', N'[TEST] Buổi 11: Hợp âm ba & Đọc bản nhạc mới (Nhóm tối)', N'Buổi song song cùng cấp độ Piano Grade 1', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 3 AND lesson_date = @D_P4 AND session_number = 12)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (3, 2, 8, 12, @D_P4, '19:30', '21:00', N'[TEST] Buổi 12: Tổng hợp tác phẩm Grade 1 (Nhóm tối)', N'Còn chỗ trống cho học viên lớp 1 sang bù', 'SCHEDULED');

-- Class 8: Piano Grade 1 - Cơ Sở Đống Đa (Course 2 - Grade 1, GV Hà id=8, P202 id=5) - Lớp song song còn 0/6 chỗ
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 8 AND lesson_date = @D_P3 AND session_number = 10)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (8, 5, 8, 10, @D_P3, '09:30', '11:00', N'[TEST] Buổi 10: Luyện âm giai Đống Đa', N'Dành cho học viên bù Buổi 10', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 8 AND lesson_date = @D_P4 AND session_number = 11)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (8, 5, 8, 11, @D_P4, '09:30', '11:00', N'[TEST] Buổi 11: Hợp âm ba Đống Đa', N'Dành cho học viên bù Buổi 11 (Class 1 / Class 3)', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 8 AND lesson_date = @D_P6 AND session_number = 12)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (8, 5, 8, 12, @D_P6, '09:30', '11:00', N'[TEST] Buổi 12: Tổng hợp tác phẩm Grade 1 Đống Đa', N'Lựa chọn bù Buổi 12', 'SCHEDULED');

-- Class 4: Guitar Đệm Hát Sáng T7-CN (Course 3 - Cơ bản, GV Tuấn id=5, P104 id=4) - bé Trọng Anh (#3)
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 4 AND lesson_date = @D_0 AND session_number = 8)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (4, 4, 5, 8, @D_0, '16:00', '17:30', N'[TEST] Buổi 8: Quạt chả điệu Slow Surf (Hôm nay)', N'Bé Trọng Anh chuyển sang bù Class 7 hôm nay', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 4 AND lesson_date = @D_P3 AND session_number = 9)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (4, 4, 5, 9, @D_P3, '08:30', '10:00', N'[TEST] Buổi 9: Đệm hát nhịp 3/4 điệu Valse', N'Đang có đơn PENDING xin nghỉ buổi này', 'SCHEDULED');

-- Class 7: Guitar Nhập Môn (Course 3 - Cơ bản, GV Dũng id=9, P104 id=4) - Lớp song song nhận học bù hôm nay & sắp tới
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 7 AND lesson_date = @D_0 AND session_number = 8)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (7, 4, 9, 8, @D_0, '18:00', '19:30', N'[TEST] Buổi 8: Quạt chả điệu Slow Surf (Nhận bù hôm nay)', N'Mở ca tối nay để GV Dũng / Staff test điểm danh học bù', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 7 AND lesson_date = @D_P5 AND session_number = 9)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (7, 4, 9, 9, @D_P5, '18:00', '19:30', N'[TEST] Buổi 9: Đệm hát nhịp 3/4 điệu Valse (Guitar 2)', N'Slot bù cho bé Trọng Anh nếu duyệt đơn Buổi 9', 'SCHEDULED');

-- Class 5: Múa Thiếu Nhi & Ballet (Course 4 - duy nhất 1 lớp, GV Linh id=6, P103 id=3) - bé Ngọc Hân (#6)
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 5 AND lesson_date = @D_M2 AND session_number = 7)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (5, 3, 6, 7, @D_M2, '17:30', '19:00', N'[TEST] Buổi 7: Tổ hợp múa Ballet căn bản', N'Không có lớp song song cùng cấp độ -> test NO_SLOT', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 5 AND lesson_date = @D_P4 AND session_number = 8)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (5, 3, 6, 8, @D_P4, '17:30', '19:00', N'[TEST] Buổi 8: Kỹ thuật xoay cơ bản & Biểu cảm', N'Buổi sắp tới của bé Ngọc Hân', 'SCHEDULED');

-- Class 6: Võ Thuật Nhập Môn (Course 6, GV Long id=7, P203 id=6) - bé Tuấn Kiệt (#7)
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 6 AND lesson_date = @D_M3 AND session_number = 6)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (6, 6, 7, 6, @D_M3, '17:30', '19:00', N'[TEST] Buổi 6: Căn bản tấn pháp & Thể lực', N'Buổi gốc ca bù đã COMPLETED', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 6 AND lesson_date = @D_M1 AND session_number = 7)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (6, 6, 7, 7, @D_M1, '17:30', '19:00', N'[TEST] Buổi 7: Đòn tay thẳng & Phản xạ tự vệ', N'Đã học bù hoàn thành tại buổi này', 'SCHEDULED');

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 6 AND lesson_date = @D_P4 AND session_number = 8)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (6, 6, 7, 8, @D_P4, '17:30', '19:00', N'[TEST] Buổi 8: Bài quyền nhập môn số 1', N'Đang có đơn PENDING xin nghỉ buổi này', 'SCHEDULED');

-- =============================================================================
-- 2. LẤY ID CÁC BUỔI HỌC VỪA ĐẢM BẢO
-- =============================================================================
DECLARE @L_C1_S10 BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 1 AND lesson_date = @D_M2 AND session_number = 10);
DECLARE @L_C1_S11 BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 1 AND lesson_date = @D_P2 AND session_number = 11);
DECLARE @L_C1_S12 BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 1 AND lesson_date = @D_P5 AND session_number = 12);

DECLARE @L_C3_S10 BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 3 AND lesson_date = @D_M1 AND session_number = 10);
DECLARE @L_C3_S11 BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 3 AND lesson_date = @D_P1 AND session_number = 11);

DECLARE @L_C4_S8  BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 4 AND lesson_date = @D_0  AND session_number = 8);
DECLARE @L_C4_S9  BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 4 AND lesson_date = @D_P3 AND session_number = 9);
DECLARE @L_C7_S8  BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 7 AND lesson_date = @D_0  AND session_number = 8);

DECLARE @L_C5_S7  BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 5 AND lesson_date = @D_M2 AND session_number = 7);
DECLARE @L_C6_S6  BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 6 AND lesson_date = @D_M3 AND session_number = 6);
DECLARE @L_C6_S7  BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 6 AND lesson_date = @D_M1 AND session_number = 7);
DECLARE @L_C6_S8  BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 6 AND lesson_date = @D_P4 AND session_number = 8);

-- =============================================================================
-- 3. TẠO DỮ LIỆU ĐƠN XIN NGHỈ & CA HỌC BÙ CHO TẤT CẢ TRẠNG THÁI
-- =============================================================================

-- CASE 1 [PENDING - Mới nhất]: Bé Bảo Nam (#1) xin nghỉ Buổi 11 Class 1 -> chờ GV Hường (hoặc Staff/Admin) duyệt
IF NOT EXISTS (SELECT 1 FROM absence_requests WHERE student_id = 1 AND lesson_id = @L_C1_S11)
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, created_at)
    VALUES (1, @L_C1_S11, 10, N'[TEST-PENDING #1] Bé Bảo Nam bị sốt nhẹ, xin phép cô Hường nghỉ buổi 11 và được xếp học bù ạ.', 'PENDING', DATEADD(minute, -10, GETDATE()));

-- CASE 2 [PENDING]: Bé Trọng Anh (#3) xin nghỉ Buổi 9 Class 4 -> chờ GV Tuấn duyệt (sau đó có Class 7 Buổi 9 nhận bù)
IF NOT EXISTS (SELECT 1 FROM absence_requests WHERE student_id = 3 AND lesson_id = @L_C4_S9)
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, created_at)
    VALUES (3, @L_C4_S9, 10, N'[TEST-PENDING #2] Bé Trọng Anh tham gia thi Olympic Toán ở trường nên xin nghỉ buổi Guitar 9.', 'PENDING', DATEADD(minute, -35, GETDATE()));

-- CASE 3 [PENDING]: Bé Tuấn Kiệt (#7) xin nghỉ Buổi 8 Class 6 -> chờ GV Long duyệt
IF NOT EXISTS (SELECT 1 FROM absence_requests WHERE student_id = 7 AND lesson_id = @L_C6_S8)
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, created_at)
    VALUES (7, @L_C6_S8, 13, N'[TEST-PENDING #3] Gia đình đưa bé đi khám răng định kỳ trùng giờ tập võ, xin thầy cho bé nghỉ buổi 8.', 'PENDING', DATEADD(hour, -2, GETDATE()));

-- CASE 4 [APPROVED -> Makeup PENDING]: Bé Hải Yến (#4) nghỉ Buổi 11 Class 3 -> GV Hà đã duyệt, chờ Giáo vụ/Admin xếp vào Class 8 Buổi 11
DECLARE @ArYen BIGINT = (SELECT TOP 1 id FROM absence_requests WHERE student_id = 4 AND lesson_id = @L_C3_S11);
IF @ArYen IS NULL
BEGIN
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (4, @L_C3_S11, 10, N'[TEST-MAKEUP PENDING] Bé Hải Yến về quê ăn giỗ cùng gia đình, xin nghỉ Buổi 11 lớp Piano tối.', 'APPROVED', 8, N'Cô Hà đồng ý cho bé nghỉ, chuyển Giáo vụ xếp bù vào lớp Đống Đa.', DATEADD(minute, -40, GETDATE()), DATEADD(hour, -3, GETDATE()));
    SET @ArYen = SCOPE_IDENTITY();
END;
IF @ArYen IS NOT NULL AND NOT EXISTS (SELECT 1 FROM makeup_registrations WHERE absence_request_id = @ArYen)
    INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
    VALUES (@ArYen, 4, @L_C3_S11, NULL, 'PENDING', N'[TEST] Đơn đã duyệt, đang chờ Giáo vụ chọn lớp Piano Grade 1 phù hợp.', DATEADD(minute, -40, GETDATE()));

-- CASE 5 [APPROVED -> Makeup NO_SLOT]: Bé Ngọc Hân (#6) nghỉ Buổi 7 Class 5 (Múa Ballet chỉ có 1 lớp) -> NO_SLOT
DECLARE @ArHan BIGINT = (SELECT TOP 1 id FROM absence_requests WHERE student_id = 6 AND lesson_id = @L_C5_S7);
IF @ArHan IS NULL
BEGIN
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (6, @L_C5_S7, 12, N'[TEST-NO_SLOT] Bé Ngọc Hân bị đau họng nhẹ, xin nghỉ Buổi 7 lớp Múa.', 'APPROVED', 6, N'Cô Linh duyệt đơn, chuyển bộ phận học vụ rà soát ca bù.', DATEADD(hour, -5, GETDATE()), DATEADD(hour, -6, GETDATE()));
    SET @ArHan = SCOPE_IDENTITY();
END;
IF @ArHan IS NOT NULL AND NOT EXISTS (SELECT 1 FROM makeup_registrations WHERE absence_request_id = @ArHan)
    INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
    VALUES (@ArHan, 6, @L_C5_S7, NULL, 'NO_SLOT', N'CHƯA CÓ LỚP PHÙ HỢP: Hiện trung tâm chưa có lớp Múa Ballet thiếu nhi song song ở Buổi 7. Phụ huynh vui lòng chờ thông báo mở lớp tăng cường.', DATEADD(hour, -4, GETDATE()));

-- CASE 6 [APPROVED -> Makeup SCHEDULED hôm nay]: Bé Trọng Anh (#3) nghỉ Buổi 8 Class 4 hôm nay -> xếp bù sang Buổi 8 Class 7 (GV Dũng) lúc 18:00-19:30 hôm nay!
DECLARE @ArAnhToday BIGINT = (SELECT TOP 1 id FROM absence_requests WHERE student_id = 3 AND lesson_id = @L_C4_S8);
IF @ArAnhToday IS NULL
BEGIN
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (3, @L_C4_S8, 10, N'[TEST-SCHEDULED HÔM NAY] Bé Trọng Anh thi học kỳ buổi chiều, xin chuyển ca Guitar hôm nay sang lớp tối.', 'APPROVED', 5, N'Thầy Tuấn đã duyệt chuyển ca tối nay cho con.', DATEADD(hour, -8, GETDATE()), DATEADD(day, -1, GETDATE()));
    SET @ArAnhToday = SCOPE_IDENTITY();
END;
IF @ArAnhToday IS NOT NULL AND NOT EXISTS (SELECT 1 FROM makeup_registrations WHERE absence_request_id = @ArAnhToday)
    INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
    VALUES (@ArAnhToday, 3, @L_C4_S8, @L_C7_S8, 'SCHEDULED', N'[RETRY:1][LOCKED:0] Đã xếp học bù sang lớp Guitar Nhập Môn của Thầy Dũng (18:00-19:30 hôm nay).', DATEADD(hour, -7, GETDATE()));

-- CASE 7 [APPROVED -> Makeup MISSED 1/2]: Bé Bảo Nam (#1) nghỉ Buổi 10 Class 1 -> đã xếp Class 3 Buổi 10 hôm qua nhưng vắng (còn 1 lần xếp lại vào Class 8 Buổi 10)
DECLARE @ArNamMissed BIGINT = (SELECT TOP 1 id FROM absence_requests WHERE student_id = 1 AND lesson_id = @L_C1_S10);
IF @ArNamMissed IS NULL
BEGIN
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (1, @L_C1_S10, 10, N'[TEST-MISSED 1/2] Gia đình có việc bận đột xuất, xin nghỉ Buổi 10 Piano.', 'APPROVED', 4, N'Cô Hường đã duyệt đơn.', DATEADD(day, -2, GETDATE()), DATEADD(day, -2, GETDATE()));
    SET @ArNamMissed = SCOPE_IDENTITY();
END;
IF @ArNamMissed IS NOT NULL AND NOT EXISTS (SELECT 1 FROM makeup_registrations WHERE absence_request_id = @ArNamMissed)
    INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
    VALUES (@ArNamMissed, 1, @L_C1_S10, @L_C3_S10, 'MISSED', N'[RETRY:1][LOCKED:0] Bỏ lỡ ca bù ngày hôm qua (Còn tối đa 1 lần xếp bù cuối cùng vào Class 8 Buổi 10).', DATEADD(day, -1, GETDATE()));

-- CASE 8 [APPROVED -> Makeup COMPLETED]: Bé Tuấn Kiệt (#7) nghỉ Buổi 6 Class 6 -> đã bù COMPLETED tại Buổi 7
DECLARE @ArKietDone BIGINT = (SELECT TOP 1 id FROM absence_requests WHERE student_id = 7 AND lesson_id = @L_C6_S6);
IF @ArKietDone IS NULL
BEGIN
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (7, @L_C6_S6, 13, N'[TEST-COMPLETED] Bé bị cảm nhẹ, xin nghỉ Buổi 6 Võ thuật.', 'APPROVED', 7, N'Thầy Long đồng ý cho bé nghỉ.', DATEADD(day, -3, GETDATE()), DATEADD(day, -3, GETDATE()));
    SET @ArKietDone = SCOPE_IDENTITY();
END;
IF @ArKietDone IS NOT NULL AND NOT EXISTS (SELECT 1 FROM makeup_registrations WHERE absence_request_id = @ArKietDone)
    INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
    VALUES (@ArKietDone, 7, @L_C6_S6, @L_C6_S7, 'COMPLETED', N'[RETRY:1][LOCKED:0] Học viên đã đi học bù đầy đủ và hoàn thành nội dung Buổi 6.', DATEADD(day, -1, GETDATE()));

-- CASE 9 [REJECTED]: Bé Bảo Nam (#1) xin nghỉ muộn Buổi 9 (quá khứ) -> Từ chối
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 1 AND lesson_date = @D_M3 AND session_number = 9)
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (1, 1, 4, 9, @D_M3, '18:00', '19:30', N'[TEST] Buổi 9: Luyện ngón Hanon', N'Buổi quá khứ mẫu cho đơn bị từ chối', 'SCHEDULED');
DECLARE @L_C1_S9 BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 1 AND lesson_date = @D_M3 AND session_number = 9);
IF @L_C1_S9 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM absence_requests WHERE student_id = 1 AND lesson_id = @L_C1_S9)
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (1, @L_C1_S9, 10, N'[TEST-REJECTED] Bé về trễ vì tắc đường, xin nghỉ phép sau khi buổi học đã kết thúc.', 'REJECTED', 4, N'Từ chối do báo sau khi buổi học đã kết thúc. Vui lòng báo trước tối thiểu 2 tiếng trước giờ học.', DATEADD(hour, -12, GETDATE()), DATEADD(day, -1, GETDATE()));

PRINT N'--> Hoàn tất khởi tạo dữ liệu test cho luồng Nghỉ học & Học bù + Thời khóa biểu!';
GO
