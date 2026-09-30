-- ============================================================================
-- SCRIPT THÊM LỚP HỌC MỚI CÒN CHỖ TRỐNG ĐỂ KIỂM THỬ ĐĂNG KÝ HỌC
-- Khóa học: Piano Mầm Non (Cảm thụ âm nhạc) - 3.600.000 đ
-- ============================================================================

USE course_operation_management;
GO

-- 1. Thêm lớp mới cho Khóa học Piano Mầm Non (Course ID = 1)
-- Cơ sở 1 - Cầu Giấy (Branch ID = 1)
-- Phòng Piano Nhóm 1 (Room ID = 2)
-- Giáo viên: Cô Vũ Thu Hương (User ID = 2)
-- Sĩ số tối đa: 8 học viên, Hiện có: 0 (Còn trống 8 chỗ)
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = 'CL-PIA-PRE-02')
BEGIN
    INSERT INTO classes (
        course_id, branch_id, room_id, teacher_id, 
        class_code, class_name, class_type, 
        max_students, current_students, 
        start_date, end_date, schedule_description, status
    ) VALUES (
        1, 1, 2, 2,
        'CL-PIA-PRE-02', N'Piano Mầm Non Nhóm Chiều Chủ Nhật', 'GROUP',
        8, 0,
        '2026-10-15', '2027-01-15', N'Chủ Nhật (15:00 - 16:30)', 'OPEN'
    );
    PRINT N'Đã thêm thành công lớp CL-PIA-PRE-02 (Piano Mầm Non Nhóm Chiều Chủ Nhật)!';
END
ELSE
BEGIN
    -- Nếu lớp đã tồn tại, đảm bảo trạng thái OPEN và còn chỗ trống
    UPDATE classes 
    SET status = 'OPEN', current_students = 0, max_students = 8
    WHERE class_code = 'CL-PIA-PRE-02';
    PRINT N'Lớp CL-PIA-PRE-02 đã tồn tại, đã reset sĩ số hiện tại về 0/8 chỗ!';
END
GO

-- 2. Thêm bổ sung 1 lớp cho Khóa học Piano Sơ Cấp Grade 1 (Course ID = 2)
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = 'CL-PIA-G1-02')
BEGIN
    INSERT INTO classes (
        course_id, branch_id, room_id, teacher_id, 
        class_code, class_name, class_type, 
        max_students, current_students, 
        start_date, end_date, schedule_description, status
    ) VALUES (
        2, 2, 4, 10,
        'CL-PIA-G1-02', N'Piano Grade 1 Nhóm Tối Thứ 3 & 6', 'GROUP',
        8, 0,
        '2026-10-20', '2027-02-20', N'Thứ 3 & Thứ 6 (18:30 - 19:30)', 'OPEN'
    );
    PRINT N'Đã thêm thành công lớp CL-PIA-G1-02 (Piano Grade 1 Nhóm Tối Thứ 3 & 6)!';
END
GO

-- Kiểm tra lại danh sách lớp học hiện tại
SELECT 
    c.id AS class_id,
    c.class_code,
    c.class_name,
    crs.name AS course_name,
    b.name AS branch_name,
    r.room_name,
    u.full_name AS teacher_name,
    c.current_students,
    c.max_students,
    (c.max_students - c.current_students) AS available_seats,
    c.schedule_description,
    c.status
FROM classes c
JOIN courses crs ON c.course_id = crs.id
JOIN branches b ON c.branch_id = b.id
LEFT JOIN rooms r ON c.room_id = r.id
LEFT JOIN users u ON c.teacher_id = u.id
ORDER BY c.id;
GO
