USE course_operation_management;
GO

SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'parent_test_05')
BEGIN
    INSERT INTO dbo.users (username, password, full_name, email, phone, role, status)
    VALUES (
        'parent_test_05',
        '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii',
        N'Phụ Huynh Demo Ghi Danh',
        'parent.test05@example.com',
        '0909000005',
        'PARENT',
        'ACTIVE'
    );
END;

DECLARE @ParentId BIGINT = (SELECT id FROM dbo.users WHERE username = 'parent_test_05');

IF NOT EXISTS (
    SELECT 1 FROM dbo.students
    WHERE parent_id = @ParentId AND full_name = N'Nguyễn An Demo Ghi Danh'
)
BEGIN
    INSERT INTO dbo.students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentId, N'Nguyễn An Demo Ghi Danh', '2018-06-15', N'Nữ', N'Tiểu học Demo', N'Học viên test luồng đăng ký lớp');
END;

COMMIT TRANSACTION;

SELECT u.id AS parent_id, u.username, u.role, s.id AS student_id, s.full_name AS student_name
FROM dbo.users u
JOIN dbo.students s ON s.parent_id = u.id
WHERE u.username = 'parent_test_05' AND s.full_name = N'Nguyễn An Demo Ghi Danh';
