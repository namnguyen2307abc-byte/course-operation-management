-- =============================================================================
-- SEED DATA: DỮ LIỆU MẪU KIỂM THỬ TOÀN DIỆN LUỒNG XIN NGHỈ & HỌC BÙ
-- Ngày thực thi mẫu: Tháng 10/2026 (Tuần từ 12/10/2026 đến 18/10/2026)
-- =============================================================================

USE course_operation_management;
GO

SET NOCOUNT ON;

PRINT N'--> Bắt đầu khởi tạo dữ liệu mẫu cho luồng Xin Nghỉ & Học Bù...';

-- 1. ĐẢM BẢO CÁC BUỔI HỌC (LESSONS) CHO TUẦN NÀY VÀ TUẦN TỚI
-- Class 3: Piano Grade 1 - Nhóm Chiều Tối (Course Grade 1, GV Hà id=8, Phòng P102 id=2)
-- Cần có Buổi 3 (hôm nay 14/10/2026 để test điểm danh học bù) và Buổi 4 (17/10/2026 để test xếp lịch bù)
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 3 AND session_number = 3 AND lesson_date = '2026-10-14')
BEGIN
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (3, 2, 8, 3, '2026-10-14', '18:00', '19:30', N'Buổi 3: Gam Đô trưởng & Hợp âm căn bản', N'Thực hành đàn 2 tay kết hợp', 'SCHEDULED');
    PRINT N'   [+] Đã tạo Buổi 3 Lớp 3 (Hôm nay 14/10)';
END;

IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 3 AND session_number = 4 AND lesson_date = '2026-10-17')
BEGIN
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (3, 2, 8, 4, '2026-10-17', '18:00', '19:30', N'Buổi 4: Luyện ngón & Thị tấu tác phẩm Grade 1', N'Chuẩn bị sách Czerny Op.599', 'SCHEDULED');
    PRINT N'   [+] Đã tạo Buổi 4 Lớp 3 (Thứ 7 17/10)';
END;

-- Class 8: Piano Grade 1 - Cơ Sở Đống Đa (Course Grade 1, GV Hà id=8, Phòng P202 id=5)
-- Thêm Buổi 4 (Chủ nhật 18/10/2026 để có thêm lựa chọn cùng Grade 1 còn chỗ)
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 8 AND session_number = 4 AND lesson_date = '2026-10-18')
BEGIN
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (8, 5, 8, 4, '2026-10-18', '09:30', '11:00', N'Buổi 4: Kỹ thuật Legato và Staccato', N'Học viên mang tai nghe cá nhân', 'SCHEDULED');
    PRINT N'   [+] Đã tạo Buổi 4 Lớp 8 (Chủ nhật 18/10)';
END;

-- Class 5: Múa Thiếu Nhi & Ballet Căn Bản (Course Căn bản, GV Linh id=6, Phòng P103 id=3)
-- Thêm Buổi 4 (Thứ 6 16/10/2026 để test trường hợp không có lớp múa nào khác cùng cấp độ)
IF NOT EXISTS (SELECT 1 FROM lessons WHERE class_id = 5 AND session_number = 4 AND lesson_date = '2026-10-16')
BEGIN
    INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
    VALUES (5, 3, 6, 4, '2026-10-16', '17:30', '19:00', N'Buổi 4: Tổ hợp vũ đạo cơ bản & Uốn dẻo', N'Trang phục múa ballet đầy đủ', 'SCHEDULED');
    PRINT N'   [+] Đã tạo Buổi 4 Lớp 5 (Thứ 6 16/10)';
END;

-- Lấy ID các buổi học chính xác
DECLARE @LessonPianoOrigId BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 1 AND session_number = 4 AND lesson_date = '2026-10-15');
DECLARE @LessonPianoTargetId BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 3 AND session_number = 4 AND lesson_date = '2026-10-17');
DECLARE @LessonTodayTargetId BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 3 AND session_number = 3 AND lesson_date = '2026-10-14');
DECLARE @LessonPastOrigId BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 1 AND session_number = 3 AND lesson_date = '2026-10-12');
DECLARE @LessonDanceOrigId BIGINT = (SELECT TOP 1 id FROM lessons WHERE class_id = 5 AND session_number = 4 AND lesson_date = '2026-10-16');

-- 2. KỊCH BẢN 1: ĐƠN MỚI CHỜ GIÁO VIÊN DUYỆT (PENDING)
-- Học viên: Nguyễn Bảo Nam (id=1, Mẹ: parent_lan id=10)
-- Xin nghỉ Buổi 4 lớp Piano 1-1 (ngày 15/10/2026, GV Hương id=4)
IF NOT EXISTS (SELECT 1 FROM absence_requests WHERE student_id = 1 AND lesson_id = @LessonPianoOrigId)
BEGIN
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (1, @LessonPianoOrigId, 10, 
            N'Bé Nam bị sốt siêu vi, bác sĩ chỉ định nghỉ ngơi 2 ngày. Kính nhờ cô Hương duyệt đơn xin phép nghỉ buổi 4 ngày mai giúp gia đình ạ.', 
            'PENDING', NULL, NULL, NULL, DATEADD(hour, -2, GETDATE()));
    PRINT N'   [+] Kịch bản 1: Đã tạo đơn PENDING của bé Bảo Nam (#1) chờ GV Hương duyệt';
END;

-- 3. KỊCH BẢN 2: ĐƠN ĐÃ DUYỆT -> CA BÙ ĐANG CHỜ GIÁO VỤ XẾP LỊCH (PENDING MAKEUP)
-- Học viên: Nguyễn Mai Chi (id=2, Mẹ: parent_lan id=10)
-- Đã được GV Hương duyệt, tạo ca học bù PENDING
DECLARE @AbsenceChiId BIGINT = (SELECT TOP 1 id FROM absence_requests WHERE student_id = 2 AND lesson_id = @LessonPianoOrigId);
IF @AbsenceChiId IS NULL
BEGIN
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (2, @LessonPianoOrigId, 10, 
            N'Bé Mai Chi có lịch thi đấu đàn cờ vua tại trường ngày 15/10, xin phép cô cho bé nghỉ buổi 4 để tham gia thi đấu.', 
            'APPROVED', 4, N'Cô Hương đã nhận được thông tin. Chúc con thi đấu đạt kết quả cao nhé!', GETDATE(), DATEADD(day, -1, GETDATE()));
    SET @AbsenceChiId = SCOPE_IDENTITY();

    INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
    VALUES (@AbsenceChiId, 2, @LessonPianoOrigId, NULL, 'PENDING', 
            N'Tự động tạo ca học bù sau khi giáo viên duyệt đơn nghỉ của bé Mai Chi. Chờ giáo vụ xếp lớp.', GETDATE());
    PRINT N'   [+] Kịch bản 2: Đã tạo ca học bù PENDING của bé Mai Chi (#2) chờ Giáo vụ xếp lớp';
END;

-- 4. KỊCH BẢN 3: CA HỌC BÙ KHÔNG CÓ LỚP PHÙ HỢP -> ĐÃ BÁO PHỤ HUYNH (NO_SLOT)
-- Học viên: Đỗ Ngọc Hân (id=6, Bố: parent_hoang id=12)
-- Nghỉ buổi 4 Lớp Múa 5 (không có lớp múa nào khác cùng buổi 4)
DECLARE @AbsenceHanId BIGINT = (SELECT TOP 1 id FROM absence_requests WHERE student_id = 6 AND lesson_id = @LessonDanceOrigId);
IF @AbsenceHanId IS NULL AND @LessonDanceOrigId IS NOT NULL
BEGIN
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (6, @LessonDanceOrigId, 12, 
            N'Bé Hân bị cảm cúm sốt nhẹ, gia đình xin phép cho bé nghỉ buổi 4 ngày 16/10.', 
            'APPROVED', 6, N'Cô Linh đã duyệt đơn, chúc con mau khỏe nhé!', GETDATE(), DATEADD(day, -1, GETDATE()));
    SET @AbsenceHanId = SCOPE_IDENTITY();

    INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
    VALUES (@AbsenceHanId, 6, @LessonDanceOrigId, NULL, 'NO_SLOT', 
            N'CHƯA CÓ LỚP PHÙ HỢP: Buổi 4 là buổi uốn dẻo và kiểm tra tổ hợp quan trọng. Hiện chưa có lớp cùng cấp độ. Kính mong phụ huynh đưa bé tham gia lớp gốc nếu có thể, hoặc liên hệ giáo vụ để bảo lưu chuyển khóa sau.', GETDATE());
    PRINT N'   [+] Kịch bản 3: Đã tạo ca học bù NO_SLOT của bé Ngọc Hân (#6)';
END;

-- 5. KỊCH BẢN 4: CA HỌC BÙ ĐÃ XẾP VÀO HÔM NAY (SCHEDULED) ĐỂ TEST GIÁO VIÊN ĐIỂM DANH
-- Học viên: Trần Hoàng Long (Bé Tí, id=5, Bố: parent_hung id=11)
-- Buổi gốc: Lesson 3 ngày 12/10 -> Xếp bù vào Buổi 3 Lớp 3 hôm nay (14/10/2026, GV Hà id=8)
DECLARE @AbsenceLongId BIGINT = (SELECT TOP 1 id FROM absence_requests WHERE student_id = 5 AND lesson_id = @LessonPastOrigId);
IF @AbsenceLongId IS NULL AND @LessonPastOrigId IS NOT NULL
BEGIN
    INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
    VALUES (5, @LessonPastOrigId, 11, 
            N'Gia đình có việc hiếu ở quê nên xin phép nghỉ buổi 3 ngày 12/10.', 
            'APPROVED', 4, N'Đã tiếp nhận và duyệt đơn nghỉ.', DATEADD(day, -2, GETDATE()), DATEADD(day, -3, GETDATE()));
    SET @AbsenceLongId = SCOPE_IDENTITY();
END;

IF @AbsenceLongId IS NOT NULL AND @LessonTodayTargetId IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM makeup_registrations WHERE absence_request_id = @AbsenceLongId)
    BEGIN
        INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
        VALUES (@AbsenceLongId, 5, @LessonPastOrigId, @LessonTodayTargetId, 'SCHEDULED', 
                N'Đã xếp học bù vào ca tối nay của Cô Hà tại phòng P102.', DATEADD(day, -1, GETDATE()));
        PRINT N'   [+] Kịch bản 4: Đã tạo ca học bù SCHEDULED của bé Trần Hoàng Long vào buổi tối nay (14/10) để test điểm danh';
    END;
END;

PRINT N'--> Hoàn tất khởi tạo dữ liệu kiểm thử!';
GO
