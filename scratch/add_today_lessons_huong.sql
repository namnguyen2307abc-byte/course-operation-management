USE course_operation_management;

-- 1. Cập nhật học viên lớp 1 (class_id = 1) thành ENROLLED
UPDATE enrollments SET status = 'ENROLLED' WHERE class_id = 1;

-- 2. Thêm học viên 2 và 4 vào lớp 1 nếu chưa có
IF NOT EXISTS (SELECT 1 FROM enrollments WHERE class_id = 1 AND student_id = 2)
BEGIN
    INSERT INTO enrollments (class_id, student_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (1, 2, 1, '2026-10-01', 'ENROLLED', N'Ghi danh lớp Piano');
END;

IF NOT EXISTS (SELECT 1 FROM enrollments WHERE class_id = 1 AND student_id = 4)
BEGIN
    INSERT INTO enrollments (class_id, student_id, registered_by_user_id, enrollment_date, status, notes)
    VALUES (1, 4, 1, '2026-10-01', 'ENROLLED', N'Ghi danh lớp Piano');
END;

-- 3. Cập nhật current_students của lớp 1
UPDATE classes 
SET current_students = (SELECT COUNT(*) FROM enrollments WHERE class_id = 1 AND status = 'ENROLLED')
WHERE id = 1;

-- 4. Xóa buổi trùng (nếu có) và thêm 2 buổi học ngày hôm nay (2026-10-13) cho cô giáo Hương (teacher_id = 4)
DELETE FROM attendances WHERE lesson_id IN (SELECT id FROM lessons WHERE teacher_id = 4 AND lesson_date = '2026-10-13');
DELETE FROM lessons WHERE teacher_id = 4 AND lesson_date = '2026-10-13';

-- Ca 1 hôm nay: Slot 2 (09:30 - 11:30)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (1, 1, 4, 5, '2026-10-13', '09:30:00', '11:30:00', N'Buổi 5: Luyện ngón Hanon & Nhạc lý cơ bản', N'Ca học buổi sáng', 'SCHEDULED');

-- Ca 2 hôm nay: Slot 4 (15:30 - 17:30)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (1, 1, 4, 6, '2026-10-13', '15:30:00', '17:30:00', N'Buổi 6: Thực hành tác phẩm Sonatina C Major', N'Ca học buổi chiều', 'SCHEDULED');
