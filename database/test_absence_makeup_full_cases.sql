-- =============================================================================
-- DỮ LIỆU KIỂM THỬ TOÀN DIỆN: XIN NGHỈ - HỌC BÙ - ĐIỂM DANH - LỊCH TUẦN
-- Hệ quản trị: Microsoft SQL Server (database: course_operation_management)
-- Yêu cầu tiên quyết: Đã chạy database/init_database.sql
-- Đặc tính: Làm mới cửa sổ lịch từ -6 đến +6 ngày quanh GETDATE(), chạy lại an toàn.
--
-- TÀI KHOẢN ĐĂNG NHẬP (mật khẩu mặc định: 123456):
--   - Admin   : admin
--   - Staff   : staff_minh / cashier_mai
--   - Teacher : teacher_huong (Piano), teacher_ha (Piano),
--               teacher_tuan (Guitar), teacher_dung (Guitar),
--               teacher_linh (Múa), teacher_long (Võ)
--   - Parent  : parent_lan (Bảo Nam, Trọng Anh, Hải Yến),
--               parent_hoang (Ngọc Hân), parent_dung (Vũ Tuấn Kiệt)
-- =============================================================================

USE course_operation_management;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

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
-- 0. TRA CỨU ID ĐỘNG
-- =============================================================================
DECLARE @TeacherHuongId BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'teacher_huong');
DECLARE @TeacherHaId    BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'teacher_ha');
DECLARE @TeacherTuanId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'teacher_tuan');
DECLARE @TeacherDungId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'teacher_dung');
DECLARE @TeacherLinhId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'teacher_linh');
DECLARE @TeacherLongId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'teacher_long');

DECLARE @ParentLanId    BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'parent_lan');
DECLARE @ParentHoangId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'parent_hoang');
DECLARE @ParentDungId   BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'parent_dung');

DECLARE @StuNamId       BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N'%Nguyễn Bảo Nam%');
DECLARE @StuAnhId       BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N'%Nguyễn Trọng Anh%');
DECLARE @StuYenId       BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N'%Nguyễn Hải Yến%');
DECLARE @StuHanId       BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N'%Đỗ Ngọc Hân%');
DECLARE @StuKietId      BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N'%Vũ Tuấn Kiệt%');

IF @StuNamId IS NULL OR @StuAnhId IS NULL OR @StuYenId IS NULL OR @StuHanId IS NULL OR @StuKietId IS NULL
    THROW 50001, N'Thiếu dữ liệu học viên gốc. Vui lòng chạy database/init_database.sql trước.', 1;

DECLARE @Class1Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'CL-PIA-01');
DECLARE @Class3Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'CL-PIA-G1-G01');
DECLARE @Class4Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'CL-GUI-BASIC-01');
DECLARE @Class5Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'CL-DAN-KIDS-01');
DECLARE @Class6Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'CL-MA-BASIC-01');
DECLARE @Class7Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'CL-GUI-BASIC-02');
DECLARE @Class8Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'CL-PIA-G1-G02');

DECLARE @RoomP101       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = 'P101');
DECLARE @RoomP102       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = 'P102');
DECLARE @RoomP103       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = 'P103');
DECLARE @RoomP201       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = 'P201');
DECLARE @RoomP202       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = 'P202');
DECLARE @RoomP203       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = 'P203');

DECLARE @CoursePiaG1Id  BIGINT = (SELECT TOP 1 id FROM courses WHERE code = 'PIA-G1');
DECLARE @BranchCs2Id    BIGINT = (SELECT TOP 1 id FROM branches WHERE code = 'CS02');

-- =============================================================================
-- 1. CHUẨN HÓA TAG CŨ & DỌN CỬA SỔ LỊCH HIỆN TẠI (-6 ĐẾN +6 NGÀY)
-- =============================================================================
UPDATE makeup_registrations
SET note = REPLACE(note, N'[RETRY:1][LOCKED:0] ', N'[Lần bù: 1/2] ')
WHERE note LIKE N'%[RETRY:1][LOCKED:0]%';

-- Lớp Piano Grade 1 đã đủ sĩ số (1/1) dùng kiểm tra bộ lọc lớp đầy chỗ
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = 'CL-PIA-G1-FULL')
BEGIN
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaG1Id, @BranchCs2Id, @RoomP202, @TeacherHaId, 'CL-PIA-G1-FULL', N'Piano Grade 1 - Nhóm Sáng Đống Đa', 'GROUP', 1, 1, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N'Thứ 5 (14:00 - 15:30)', 'OPEN');
END;
DECLARE @ClassFullId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = 'CL-PIA-G1-FULL');

DELETE a
FROM attendances a
JOIN lessons l ON a.lesson_id = l.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
  AND l.class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId);

DELETE m
FROM makeup_registrations m
LEFT JOIN lessons lo ON m.original_lesson_id = lo.id
LEFT JOIN lessons lt ON m.target_lesson_id = lt.id
WHERE (lo.lesson_date BETWEEN @D_M6 AND @D_P6 AND lo.class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId))
   OR (lt.lesson_date BETWEEN @D_M6 AND @D_P6 AND lt.class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId));

DELETE ar
FROM absence_requests ar
JOIN lessons l ON ar.lesson_id = l.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
  AND l.class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId);

DELETE FROM lessons
WHERE lesson_date BETWEEN @D_M6 AND @D_P6
  AND class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId);

-- =============================================================================
-- 2. TẠO BUỔI HỌC PHỤC VỤ TỪNG TÌNH HUỐNG NGHIỆP VỤ
-- =============================================================================

-- --- A. PIANO GRADE 1 (Bảo Nam 1-1, Hải Yến nhóm tối, Lớp bù Đống Đa, Lớp đầy 1/1) ---
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 8, @D_M3, '18:00', '19:00', N'Buổi 8: Luyện ngón Hanon & Gam Đô trưởng', N'Thực hành ghép hai tay chậm', 'SCHEDULED');
DECLARE @L_C1_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 9, @D_M2, '18:00', '19:00', N'Buổi 9: Kỹ thuật Legato & Staccato cơ bản', N'Ôn bài tập trang 18 giáo trình Alfred', 'SCHEDULED');
DECLARE @L_C1_S9 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 10, @D_0, '23:50', '23:59', N'Buổi 10: Ôn tập tiết tấu 4/4 & Thị tấu', N'Buổi học cuối ngày để kiểm tra khóa giờ điểm danh', 'SCHEDULED');
DECLARE @L_C1_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 11, @D_P1, '18:00', '19:00', N'Buổi 11: Hợp âm ba & Đọc bản nhạc mới', N'Học viên duy nhất đã chuyển sang lớp bù', 'SCHEDULED');
DECLARE @L_C1_S11 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 12, @D_P2, '18:00', '19:00', N'Buổi 12: Luyện chuyển hợp âm trưởng - thứ', N'Đang có đơn xin nghỉ chờ duyệt', 'SCHEDULED');
DECLARE @L_C1_S12 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 13, @D_P5, '18:00', '19:00', N'Buổi 13: Tổng hợp tác phẩm Grade 1', N'Buổi học sắp tới', 'SCHEDULED');
DECLARE @L_C1_S13 BIGINT = SCOPE_IDENTITY();

-- Class 3 (Hải Yến)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class3Id, @RoomP102, @TeacherHaId, 8, @D_M2, '18:00', '19:30', N'Buổi 8: Luyện ngón Hanon & Gam Đô trưởng', N'Thực hành ghép hai tay theo nhịp', 'COMPLETED');
DECLARE @L_C3_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class3Id, @RoomP102, @TeacherHaId, 10, @D_M2, '19:30', '21:00', N'Buổi 10: Ôn tập tiết tấu 4/4 & Thị tấu', N'Luyện phản xạ đọc nốt khóa Sol và Pha', 'SCHEDULED');
DECLARE @L_C3_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class3Id, @RoomP102, @TeacherHaId, 11, @D_P3, '19:30', '21:00', N'Buổi 11: Hợp âm ba & Đọc bản nhạc mới', N'Cùng bài #11 với lớp 1-1', 'SCHEDULED');
DECLARE @L_C3_S11 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class3Id, @RoomP102, @TeacherHaId, 12, @D_P5, '17:30', '19:00', N'Buổi 12: Diễn tấu tiểu phẩm cổ điển', N'Buổi chính thức của lớp Nhóm Tối', 'SCHEDULED');
DECLARE @L_C3_S12 BIGINT = SCOPE_IDENTITY();

-- Class 8 (Lớp nhận bù Đống Đa)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 10, @D_0, '00:05', '23:55', N'Buổi 10: Ôn tập tiết tấu 4/4 (Đống Đa)', N'Ca học bù hôm nay tại cơ sở Đống Đa', 'SCHEDULED');
DECLARE @L_C8_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 8, @D_P3, '09:30', '11:00', N'Buổi 8: Luyện ngón Hanon (Đống Đa)', N'Sẵn sàng nhận học viên bù Buổi 8', 'SCHEDULED');
DECLARE @L_C8_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 11, @D_P4, '09:30', '11:00', N'Buổi 11: Hợp âm ba & Đọc bản nhạc (Đống Đa)', N'Ca học sáng phù hợp ghép bù đúng bài #11', 'SCHEDULED');
DECLARE @L_C8_S11_OK BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 11, @D_P5, '17:30', '19:00', N'Buổi 11: Hợp âm ba (Ca chiều Đống Đa)', N'Trùng khung giờ 17:30-19:00 với lớp C3', 'SCHEDULED');
DECLARE @L_C8_S11_CONFLICT BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 12, @D_P6, '09:30', '11:00', N'Buổi 12: Diễn tấu tiểu phẩm cổ điển (Đống Đa)', N'Lớp cùng cấp độ Grade 1 còn chỗ', 'SCHEDULED');
DECLARE @L_C8_S12 BIGINT = SCOPE_IDENTITY();

-- ClassFull (Lớp 1/1 đã kín chỗ)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassFullId, @RoomP202, @TeacherHaId, 11, @D_P4, '14:00', '15:30', N'Buổi 11: Hợp âm ba (Nhóm 1/1)', N'Lớp đã đủ sĩ số tối đa', 'SCHEDULED');
DECLARE @L_CFULL_S11 BIGINT = SCOPE_IDENTITY();

-- --- B. GUITAR CƠ BẢN (Trọng Anh Class 4 & Lớp bù Class 7) ---
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 5, @D_M6, '09:00', '10:30', N'Buổi 5: Tập bấm hợp âm C - Am - F - G', N'Luyện chuyển thế bấm sạch dây', 'COMPLETED');
DECLARE @L_C4_S5 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 6, @D_M5, '09:00', '10:30', N'Buổi 6: Điệu Ballad & Tỉa dây p-i-m-a', N'Giữ nhịp đều với metronome 70 BPM', 'COMPLETED');
DECLARE @L_C4_S6 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 7, @D_M4, '09:00', '10:30', N'Buổi 7: Quạt chả điệu Slow Rock', N'Thực hành đệm ca khúc mẫu', 'COMPLETED');
DECLARE @L_C4_S7 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 8, @D_M3, '09:00', '10:30', N'Buổi 8: Đệm hát nhịp 3/4 điệu Valse', N'Nhấn mạnh phách 1 và dậm nhịp chân', 'COMPLETED');
DECLARE @L_C4_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 9, @D_M1, '09:00', '10:30', N'Buổi 9: Kỹ thuật chặn phím Barre sơ cấp', N'Làm quen hợp âm Fm và Bm', 'SCHEDULED');
DECLARE @L_C4_S9 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 10, @D_0, '16:00', '17:30', N'Buổi 10: Quạt chả điệu Slow Surf', N'Thực hành ghép câu hát với hợp âm', 'SCHEDULED');
DECLARE @L_C4_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 11, @D_0, '00:05', '23:55', N'Buổi 11: Tổng duyệt ghép nhịp bài hát', N'Kiểm tra cuối tuần theo nhóm', 'SCHEDULED');
DECLARE @L_C4_S11 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class7Id, @RoomP201, @TeacherDungId, 8, @D_M1, '18:00', '19:30', N'Buổi 8: Đệm hát nhịp 3/4 điệu Valse (Chiều T7)', N'Ôn luyện điệu Valse theo nhóm nhỏ', 'COMPLETED');
DECLARE @L_C7_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class7Id, @RoomP201, @TeacherDungId, 10, @D_0, '00:05', '23:55', N'Buổi 10: Quạt chả điệu Slow Surf (Chiều T7)', N'Ca học chiều nay nhận học viên học bù', 'SCHEDULED');
DECLARE @L_C7_S10 BIGINT = SCOPE_IDENTITY();

-- --- C. MÚA BALLET CĂN BẢN (Ngọc Hân - Không có lớp song song cùng trình độ) ---
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class5Id, @RoomP103, @TeacherLinhId, 7, @D_M2, '17:30', '19:00', N'Buổi 7: Tổ hợp múa Ballet căn bản & Thăng bằng', N'Rèn tư thế lưng thẳng và mũi chân', 'SCHEDULED');
DECLARE @L_C5_S7 BIGINT = SCOPE_IDENTITY();

-- --- D. VÕ THUẬT NHẬP MÔN (Vũ Tuấn Kiệt Class 6) ---
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 4, @D_M6, '17:30', '19:00', N'Buổi 4: Tấn pháp cơ bản & Khởi động khớp', N'Luyện trung bình tấn 3 phút', 'COMPLETED');
DECLARE @L_C6_S4 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 5, @D_M5, '17:30', '19:00', N'Buổi 5: Đòn đấm thẳng & Kỹ thuật gạt đỡ', N'Thực hành theo cặp với bao đích', 'COMPLETED');
DECLARE @L_C6_S5 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 6, @D_M4, '17:30', '19:00', N'Buổi 6: Đòn đá tống trước Ap Chagi', N'Giữ thăng bằng trụ và thu chân chuẩn', 'COMPLETED');
DECLARE @L_C6_S6 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 7, @D_M3, '17:30', '19:00', N'Buổi 7: Phản xạ tự vệ cơ bản', N'Luyện tình huống thoát hiểm tầm gần', 'SCHEDULED');
DECLARE @L_C6_S7 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 8, @D_M1, '17:30', '19:00', N'Buổi 8: Phối hợp đòn tay chân liên hoàn', N'Ôn tập trước khi vào bài quyền số 1', 'COMPLETED');
DECLARE @L_C6_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 9, @D_M3, '19:15', '20:30', N'Buổi 9: Luyện thể lực & Dẻo dai', N'Chạy bền và ép dẻo cuối giờ', 'SCHEDULED');
DECLARE @L_C6_S9 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 10, @D_M2, '17:30', '19:00', N'Buổi 10: Bài quyền nhập môn số 1 (Phần 1)', N'Học 10 động tác mở đầu bài quyền', 'SCHEDULED');
DECLARE @L_C6_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 11, @D_0, '00:05', '23:55', N'Buổi 11: Bài quyền nhập môn số 1 (Hoàn thiện)', N'Tổng hợp toàn bài quyền nhập môn', 'SCHEDULED');
DECLARE @L_C6_S11 BIGINT = SCOPE_IDENTITY();

-- =============================================================================
-- 3. INSERT ĐƠN XIN NGHỈ & CA HỌC BÙ ĐẦY ĐỦ TRẠNG THÁI
-- =============================================================================

-- Case 1: Đơn PENDING (Bảo Nam - Buổi 12)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, created_at)
VALUES (@StuNamId, @L_C1_S12, @ParentLanId, N'Bé Bảo Nam có lịch khám răng định kỳ cùng gia đình, xin phép cô Hương nghỉ buổi 12.', 'PENDING', DATEADD(minute, -15, GETDATE()));

-- Case 2: Đơn REJECTED (Tuấn Kiệt - Buổi 9)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuKietId, @L_C6_S9, @ParentDungId, N'Bé về quê trễ nên xin nghỉ phép bổ sung sau buổi học.', 'REJECTED', @TeacherLongId, N'Từ chối do phụ huynh báo sau khi buổi học đã kết thúc không có lý do đột xuất.', DATEADD(day, -2, GETDATE()), DATEADD(day, -2, GETDATE()));

-- Case 3: Đơn APPROVED -> Ca bù PENDING (Hải Yến - Buổi 11)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuYenId, @L_C3_S11, @ParentLanId, N'Bé Hải Yến về quê giỗ ông cùng gia đình, xin nghỉ Buổi 11 lớp Piano tối.', 'APPROVED', @TeacherHaId, N'Cô Hà đồng ý cho bé nghỉ, chuyển Giáo vụ xếp lớp bù phù hợp.', DATEADD(hour, -2, GETDATE()), DATEADD(hour, -4, GETDATE()));
DECLARE @ArYenPending BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArYenPending, @StuYenId, @L_C3_S11, NULL, 'PENDING', N'Đơn đã phê duyệt, đang chờ Giáo vụ sắp xếp lịch học bù.', DATEADD(hour, -2, GETDATE()));

-- Case 4: Đơn APPROVED -> Ca bù NO_SLOT (Ngọc Hân - Buổi 7 Múa Ballet)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuHanId, @L_C5_S7, @ParentHoangId, N'Bé Ngọc Hân bị đau họng nhẹ, xin phép cô Linh nghỉ buổi múa.', 'APPROVED', @TeacherLinhId, N'Cô Linh đồng ý cho bé nghỉ và chuyển bộ phận học vụ rà soát lớp.', DATEADD(day, -1, GETDATE()), DATEADD(day, -2, GETDATE()));
DECLARE @ArHanNoSlot BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArHanNoSlot, @StuHanId, @L_C5_S7, NULL, 'NO_SLOT', N'CHƯA CÓ LỚP PHÙ HỢP: Hiện trung tâm chưa có lớp Múa Ballet thiếu nhi căn bản song song ở Buổi 7.', DATEADD(day, -1, GETDATE()));

-- Case 5: Đơn APPROVED -> Ca bù CANCELLED (Trọng Anh - Gia đình không sắp xếp được lịch)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuAnhId, @L_C4_S9, @ParentLanId, N'Bé Trọng Anh bận ôn thi học kỳ trên trường, xin nghỉ buổi Guitar 9.', 'APPROVED', @TeacherTuanId, N'Thầy Tuấn đồng ý cho bé nghỉ.', DATEADD(day, -1, GETDATE()), DATEADD(day, -1, GETDATE()));
DECLARE @ArAnhCancelled BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArAnhCancelled, @StuAnhId, @L_C4_S9, NULL, 'CANCELLED', N'Gia đình xác nhận bận lịch thi cả tuần nên không tham gia học bù buổi này.', DATEADD(hour, -10, GETDATE()));

-- Case 6: Đơn APPROVED -> Ca bù SCHEDULED Lần 1/2 hôm nay (Trọng Anh -> Class 7)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuAnhId, @L_C4_S10, @ParentLanId, N'Bé Trọng Anh trùng lịch ngoại khóa buổi chiều, xin chuyển ca học hôm nay.', 'APPROVED', @TeacherTuanId, N'Đã duyệt chuyển sang lớp thầy Dũng dạy bù.', DATEADD(hour, -6, GETDATE()), DATEADD(day, -1, GETDATE()));
DECLARE @ArAnhScheduledToday BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArAnhScheduledToday, @StuAnhId, @L_C4_S10, @L_C7_S10, 'SCHEDULED', N'[Lần bù: 1/2] Đã xếp lịch học bù tại lớp Guitar Nhập Môn Chiều Thứ 7 (Thầy Dũng).', DATEADD(hour, -5, GETDATE()));

-- Case 7: Đơn APPROVED -> Ca bù SCHEDULED Lần 1/2 lớp 1-1 (Bảo Nam chuyển buổi 11 sang Class 3)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuNamId, @L_C1_S11, @ParentLanId, N'Xin chuyển buổi Piano 1-1 ngày mai sang học ghép cùng nhóm tối.', 'APPROVED', @TeacherHuongId, N'Cô Hương đồng ý chuyển bù.', DATEADD(hour, -5, GETDATE()), DATEADD(hour, -8, GETDATE()));
DECLARE @ArNamHide11 BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArNamHide11, @StuNamId, @L_C1_S11, @L_C3_S11, 'SCHEDULED', N'[Lần bù: 1/2] Đã xếp học bù tại lớp Piano Grade 1 - Nhóm Chiều Tối.', DATEADD(hour, -4, GETDATE()));

-- Case 8: Đơn APPROVED -> Ca bù MISSED Lần 1/2 (Bảo Nam vắng tại C3-S8 ngày @D_M2)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuNamId, @L_C1_S8, @ParentLanId, N'Bé Bảo Nam bị sốt nhẹ nên xin nghỉ Buổi 8 Piano.', 'APPROVED', @TeacherHuongId, N'Cô Hương đã duyệt đơn.', DATEADD(day, -3, GETDATE()), DATEADD(day, -3, GETDATE()));
DECLARE @ArNamMissed BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArNamMissed, @StuNamId, @L_C1_S8, @L_C3_S8, 'MISSED', N'[Lần bù: 1/2] Bỏ lỡ / Vắng mặt tại buổi học bù (Còn tối đa 1 lần xếp lại cuối cùng).', DATEADD(day, -2, GETDATE()));

-- Case 9: Đơn APPROVED -> Ca bù SCHEDULED Lần 2/2 hôm nay (Hải Yến -> Class 8)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuYenId, @L_C3_S10, @ParentLanId, N'Gia đình có việc bận đột xuất, xin nghỉ Buổi 10 Piano.', 'APPROVED', @TeacherHaId, N'Cô Hà đã duyệt đơn.', DATEADD(day, -2, GETDATE()), DATEADD(day, -2, GETDATE()));
DECLARE @ArYenRetry2 BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArYenRetry2, @StuYenId, @L_C3_S10, @L_C8_S10, 'SCHEDULED', N'[Lần bù: 2/2] Xếp lịch học bù lần cuối tại lớp Piano Grade 1 - Cơ Sở Đống Đa.', DATEADD(day, -1, GETDATE()));

-- Case 10: Đơn APPROVED -> Ca bù CANCELLED Khóa 2/2 (Trọng Anh đã vắng đủ 2/2 lần)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuAnhId, @L_C4_S8, @ParentLanId, N'Bé Trọng Anh tham gia thi Olympic Toán cấp trường, xin nghỉ Buổi 8.', 'APPROVED', @TeacherTuanId, N'Thầy Tuấn duyệt đơn.', DATEADD(day, -3, GETDATE()), DATEADD(day, -3, GETDATE()));
DECLARE @ArAnhLocked2 BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArAnhLocked2, @StuAnhId, @L_C4_S8, @L_C7_S8, 'CANCELLED', N'[Lần bù: 2/2 - KHÓA] Hủy quyền học bù do học viên đã vắng mặt cả 2/2 lần học bù quy định.', DATEADD(day, -1, GETDATE()));

-- Case 11: Đơn APPROVED -> Ca bù COMPLETED (Tuấn Kiệt đã hoàn thành tại C6-S8)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuKietId, @L_C6_S7, @ParentDungId, N'Bé Tuấn Kiệt xin nghỉ Buổi 7 Võ thuật do trùng lịch tiêm chủng.', 'APPROVED', @TeacherLongId, N'Thầy Long đồng ý.', DATEADD(day, -3, GETDATE()), DATEADD(day, -3, GETDATE()));
DECLARE @ArKietCompleted BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArKietCompleted, @StuKietId, @L_C6_S7, @L_C6_S8, 'COMPLETED', N'[Lần bù: 1/2] Tự động hoàn thành sau khi giáo viên điểm danh Có mặt.', DATEADD(day, -1, GETDATE()));

-- =============================================================================
-- 4. INSERT DỮ LIỆU ĐIỂM DANH LỊCH SỬ (ATTENDANCES)
-- =============================================================================
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at) VALUES
(@L_C3_S8,   @StuNamId,  'ABSENT',  N'Học viên vắng mặt tại buổi học bù lần 1/2', DATEADD(day, -2, GETDATE())),
(@L_C7_S8,   @StuAnhId,  'ABSENT',  N'Học viên vắng mặt tại buổi học bù lần 2/2', DATEADD(day, -1, GETDATE())),
(@L_C4_S5,   @StuAnhId,  'ABSENT',  N'Nghỉ có việc gia đình', DATEADD(day, -6, GETDATE())),
(@L_C4_S6,   @StuAnhId,  'ABSENT',  N'Nghỉ ốm', DATEADD(day, -5, GETDATE())),
(@L_C4_S7,   @StuAnhId,  'ABSENT',  N'Nghỉ bận lịch trường', DATEADD(day, -4, GETDATE())),
(@L_C4_S8,   @StuAnhId,  'ABSENT',  N'Nghỉ thi học sinh giỏi', DATEADD(day, -3, GETDATE())),
(@L_C6_S8,   @StuKietId, 'PRESENT', N'Tham gia học bù đầy đủ', DATEADD(day, -1, GETDATE())),
(@L_C6_S4,   @StuKietId, 'ABSENT',  N'Nghỉ ốm', DATEADD(day, -6, GETDATE())),
(@L_C6_S5,   @StuKietId, 'ABSENT',  N'Nghỉ có phép', DATEADD(day, -5, GETDATE())),
(@L_C6_S6,   @StuKietId, 'ABSENT',  N'Nghỉ việc gia đình', DATEADD(day, -4, GETDATE()));

-- =============================================================================
-- 5. KIỂM TRA NHANH DỮ LIỆU SAU KHI NẠP
-- =============================================================================
SELECT ar.id, s.full_name AS student_name, c.class_code, l.lesson_date, l.title, ar.status, ar.reason
FROM absence_requests ar
JOIN students s ON ar.student_id = s.id
JOIN lessons l ON ar.lesson_id = l.id
JOIN classes c ON l.class_id = c.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
ORDER BY ar.id;

SELECT mr.id, s.full_name AS student_name, lo.title AS original_lesson, lt.title AS target_lesson, mr.status, mr.note
FROM makeup_registrations mr
JOIN students s ON mr.student_id = s.id
JOIN lessons lo ON mr.original_lesson_id = lo.id
LEFT JOIN lessons lt ON mr.target_lesson_id = lt.id
WHERE lo.lesson_date BETWEEN @D_M6 AND @D_P6
ORDER BY mr.id;
GO
