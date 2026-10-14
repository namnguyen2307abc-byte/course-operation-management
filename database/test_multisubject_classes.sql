-- Local test data for course enrollment. Safe to run more than once.
-- Swimming classes have no room until a pool is configured in the app.
USE course_operation_management;
GO

SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM dbo.branches WHERE code = 'CS01')
       OR NOT EXISTS (SELECT 1 FROM dbo.branches WHERE code = 'CS02')
        THROW 50001, 'Seed branches CS01 and CS02 before running this script.', 1;

    INSERT INTO dbo.programs (code, name, description)
    SELECT seed.code, seed.name, seed.description
    FROM (VALUES
        ('SWIMMING', N'Bơi lội', N'Kỹ thuật bơi và an toàn dưới nước'),
        ('CHESS', N'Cờ vua', N'Tư duy chiến thuật và thi đấu cờ vua'),
        ('GUITAR', N'Guitar', N'Đào tạo guitar từ nhập môn đến đệm hát')
    ) AS seed(code, name, description)
    WHERE NOT EXISTS (SELECT 1 FROM dbo.programs p WHERE p.code = seed.code);

    INSERT INTO dbo.courses
        (program_id, code, name, level, duration_weeks, total_sessions, tuition_fee, description)
    SELECT p.id, seed.code, seed.name, seed.level, seed.duration_weeks,
           seed.total_sessions, seed.tuition_fee, seed.description
    FROM (VALUES
        ('SWIMMING', 'SWIM-BEG', N'Bơi lội nhập môn', N'Sơ cấp', 12, 24, 3000000.00, N'Làm quen với nước, nổi và bơi cơ bản'),
        ('SWIMMING', 'SWIM-INT', N'Bơi lội kỹ thuật', N'Trung cấp', 16, 32, 4200000.00, N'Cải thiện kỹ thuật bơi và sức bền'),
        ('SWIMMING', 'SWIM-ADV', N'Bơi lội nâng cao', N'Nâng cao', 20, 40, 5600000.00, N'Kỹ thuật và thể lực cho học viên đã biết bơi'),
        ('CHESS', 'CHESS-BEG', N'Cờ vua nhập môn', N'Sơ cấp', 12, 24, 2400000.00, N'Quân cờ, luật chơi và khai cuộc cơ bản'),
        ('CHESS', 'CHESS-INT', N'Cờ vua chiến thuật', N'Trung cấp', 16, 32, 3400000.00, N'Chiến thuật trung cuộc và tàn cuộc'),
        ('CHESS', 'CHESS-ADV', N'Cờ vua thi đấu', N'Nâng cao', 20, 40, 4600000.00, N'Phân tích ván đấu và thi đấu nâng cao'),
        ('GUITAR', 'GUI-BASIC', N'Guitar nhập môn', N'Sơ cấp', 12, 24, 2800000.00, N'Hợp âm, nhịp và kỹ thuật tay cơ bản'),
        ('GUITAR', 'GUI-INTER', N'Guitar đệm hát', N'Trung cấp', 16, 32, 3900000.00, N'Đệm hát, chuyển hợp âm và tiết tấu nâng cao')
    ) AS seed(program_code, code, name, level, duration_weeks, total_sessions, tuition_fee, description)
    JOIN dbo.programs p ON p.code = seed.program_code
    WHERE NOT EXISTS (SELECT 1 FROM dbo.courses c WHERE c.code = seed.code);

    INSERT INTO dbo.rooms
        (branch_id, room_code, room_name, capacity, room_type, status, description)
    SELECT b.id, 'TEST-CHESS-ROOM', N'Phòng cờ vua test', 12, 'THEORY_ROOM',
           'AVAILABLE', N'Phòng lý thuyết dùng cho dữ liệu kiểm thử cờ vua'
    FROM dbo.branches b
    WHERE b.code = 'CS01'
      AND NOT EXISTS (
          SELECT 1 FROM dbo.rooms r
          WHERE r.branch_id = b.id AND r.room_code = 'TEST-CHESS-ROOM'
      );

    DECLARE @SeedClasses TABLE (
        class_code VARCHAR(50) PRIMARY KEY,
        course_code VARCHAR(50) NOT NULL,
        branch_code VARCHAR(50) NOT NULL,
        room_code VARCHAR(50) NULL,
        teacher_username VARCHAR(100) NULL,
        class_name NVARCHAR(150) NOT NULL,
        max_students INT NOT NULL,
        start_date DATE NOT NULL,
        end_date DATE NOT NULL,
        schedule_description NVARCHAR(200) NOT NULL
    );

    INSERT INTO @SeedClasses
        (class_code, course_code, branch_code, room_code, teacher_username,
         class_name, max_students, start_date, end_date, schedule_description)
    VALUES
        ('TEST-SWIM-BEG-01', 'SWIM-BEG', 'CS02', NULL, NULL,
         N'Bơi nhập môn - nhóm chiều', 10, '2026-11-02', '2027-02-02', N'Bể bơi CS02 - Thứ 2 & Thứ 4 (17:00 - 18:00)'),
        ('TEST-SWIM-INT-01', 'SWIM-INT', 'CS02', NULL, NULL,
         N'Bơi kỹ thuật - nhóm tối', 8, '2026-11-03', '2027-03-03', N'Bể bơi CS02 - Thứ 3 & Thứ 5 (18:00 - 19:00)'),
        ('TEST-SWIM-ADV-01', 'SWIM-ADV', 'CS02', NULL, NULL,
         N'Bơi nâng cao - cuối tuần', 6, '2026-11-07', '2027-04-07', N'Bể bơi CS02 - Thứ 7 (08:00 - 10:00)'),
        ('TEST-CHESS-BEG-01', 'CHESS-BEG', 'CS01', 'TEST-CHESS-ROOM', NULL,
         N'Cờ vua nhập môn - sáng Thứ 7', 12, '2026-11-07', '2027-02-07', N'Thứ 7 (09:00 - 10:30)'),
        ('TEST-CHESS-INT-01', 'CHESS-INT', 'CS01', 'TEST-CHESS-ROOM', NULL,
         N'Cờ vua chiến thuật - tối Thứ 3', 10, '2026-11-10', '2027-03-10', N'Thứ 3 (18:00 - 19:30)'),
        ('TEST-CHESS-ADV-01', 'CHESS-ADV', 'CS01', 'TEST-CHESS-ROOM', NULL,
         N'Cờ vua thi đấu - chiều Chủ nhật', 8, '2026-11-08', '2027-04-08', N'Chủ nhật (14:00 - 15:30)'),
        ('TEST-PIA-PRE-01', 'PIA-PRE', 'CS01', 'P102', 'teacher_huong',
         N'Piano mầm non - Chủ nhật', 8, '2026-11-08', '2027-02-08', N'Chủ nhật (10:00 - 11:30)'),
        ('TEST-PIA-G1-01', 'PIA-G1', 'CS02', 'P202', 'teacher_ha',
         N'Piano Grade 1 - nhóm chiều', 6, '2026-11-11', '2027-03-11', N'Thứ 4 & Thứ 7 (16:00 - 17:30)'),
        ('TEST-GUI-BEG-01', 'GUI-BASIC', 'CS02', 'P201', NULL,
         N'Guitar nhập môn - nhóm tối', 10, '2026-11-09', '2027-03-09', N'Thứ 2 & Thứ 5 (18:00 - 19:30)'),
        ('TEST-GUI-INT-01', 'GUI-INTER', 'CS02', 'P201', NULL,
         N'Guitar đệm hát - Chủ nhật', 8, '2026-11-08', '2027-04-08', N'Chủ nhật (10:00 - 11:30)');

    IF EXISTS (
        SELECT 1 FROM @SeedClasses s
        LEFT JOIN dbo.courses c ON c.code = s.course_code
        LEFT JOIN dbo.branches b ON b.code = s.branch_code
        LEFT JOIN dbo.rooms r ON r.branch_id = b.id AND r.room_code = s.room_code
        WHERE c.id IS NULL OR b.id IS NULL OR (s.room_code IS NOT NULL AND r.id IS NULL)
    )
        THROW 50002, 'A seed course, branch, or room is missing.', 1;

    INSERT INTO dbo.classes
        (course_id, branch_id, room_id, teacher_id, class_code, class_name,
         class_type, max_students, current_students, start_date, end_date,
         schedule_description, status)
    SELECT c.id, b.id, r.id, u.id, s.class_code, s.class_name,
           'GROUP', s.max_students, 0, s.start_date, s.end_date,
           s.schedule_description, 'OPEN'
    FROM @SeedClasses s
    JOIN dbo.courses c ON c.code = s.course_code
    JOIN dbo.branches b ON b.code = s.branch_code
    LEFT JOIN dbo.rooms r ON r.branch_id = b.id AND r.room_code = s.room_code
    LEFT JOIN dbo.users u ON u.username = s.teacher_username
    WHERE NOT EXISTS (SELECT 1 FROM dbo.classes existing WHERE existing.class_code = s.class_code);

    COMMIT TRANSACTION;

    SELECT cl.class_code, cl.class_name, p.name AS program_name,
           c.name AS course_name, c.level, cl.current_students,
           cl.max_students, cl.status
    FROM dbo.classes cl
    JOIN dbo.courses c ON c.id = cl.course_id
    JOIN dbo.programs p ON p.id = c.program_id
    JOIN @SeedClasses s ON s.class_code = cl.class_code
    ORDER BY p.name, c.level, cl.class_code;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
