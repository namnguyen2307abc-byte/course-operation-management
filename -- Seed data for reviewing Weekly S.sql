-- Seed data for reviewing Weekly Schedule + Attendance Tracking.
-- Run the user's du_lieu_theo_word.sql first, then run this file in SSMS.
-- Creates an isolated class with 4 students and 12 lessons:
-- 10 historical lessons with varied attendance, today's editable lesson,
-- and one future locked lesson. Does not modify the source Word demo class.
-- Reruns are idempotent: an installation marker prevents resetting demo data.
USE [course_operation_management];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF DB_NAME() <> N'course_operation_management'
    THROW 51700, 'Wrong database. Run this file in course_operation_management.', 1;
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
 OR OBJECT_ID(N'dbo.students', N'U') IS NULL
 OR OBJECT_ID(N'dbo.courses', N'U') IS NULL
 OR OBJECT_ID(N'dbo.branches', N'U') IS NULL
 OR OBJECT_ID(N'dbo.classes', N'U') IS NULL
 OR OBJECT_ID(N'dbo.lessons', N'U') IS NULL
 OR OBJECT_ID(N'dbo.enrollments', N'U') IS NULL
 OR OBJECT_ID(N'dbo.attendances', N'U') IS NULL
    THROW 51701, 'Required application tables are missing. Install the application schema first.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @LockResult INT;
    EXEC @LockResult = sys.sp_getapplock
        @Resource = N'ATTENDANCE_WEEKLY_DEMO_V1',
        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 15000;
    IF @LockResult < 0 THROW 51702, 'Another attendance demo installation is running. Retry later.', 1;

    IF OBJECT_ID(N'dbo.attendance_weekly_demo_installations', N'U') IS NULL
        CREATE TABLE dbo.attendance_weekly_demo_installations (
            batch_key VARCHAR(80) NOT NULL PRIMARY KEY,
            installed_at DATETIME2 NOT NULL,
            class_code VARCHAR(50) NOT NULL,
            first_lesson_date DATE NOT NULL,
            today_lesson_date DATE NOT NULL,
            future_lesson_date DATE NOT NULL
        );

    IF EXISTS (SELECT 1 FROM dbo.attendance_weekly_demo_installations WHERE batch_key = 'ATT_WEEKLY_V1')
    BEGIN
        PRINT N'Dữ liệu attendance demo đã được cài. Giữ nguyên các thay đổi đã demo; không nạp lại.';
    END
    ELSE
    BEGIN
        DECLARE @Now DATETIME2 = CONVERT(DATETIME2, SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time');
        DECLARE @Today DATE = CONVERT(DATE, @Now);
        DECLARE @Teacher BIGINT = (SELECT id FROM dbo.users WHERE username = 'd26_teacher_piano' AND role = 'TEACHER' AND status = 'ACTIVE');
        IF EXISTS (SELECT 1 FROM dbo.users WHERE username = 'd26_teacher_other')
            THROW 51706, 'Reserved username d26_teacher_other already exists. Existing account was preserved; choose another unused username in this demo script.', 1;
        INSERT dbo.users(username, password, full_name, email, role, subject, teaching_subject,
            status, token_version, must_change_password, created_at)
        VALUES('d26_teacher_other', '$2a$10$U9rS8H8giwtd3tJo7XlXG.tkjsA8opaNfJZqihlijddbmKw2k76Ju',
            N'Trần Giáo Viên Khác', 'd26_teacher_other@talent.example', 'TEACHER', 'GUITAR', 'GUITAR',
            'ACTIVE', 0, 0, @Now);
        DECLARE @Parent BIGINT = (SELECT id FROM dbo.users WHERE username = 'd26_parent_new' AND role = 'PARENT' AND status = 'ACTIVE');
        DECLARE @Course BIGINT = (SELECT id FROM dbo.courses WHERE code = 'PIA-CB-12');
        DECLARE @Branch BIGINT = (SELECT id FROM dbo.branches WHERE code = 'MD01' AND active = 1);
        DECLARE @ClassCode VARCHAR(50) = 'ATT-MD-DEMO-01';

        IF @Teacher IS NULL OR @Parent IS NULL OR @Course IS NULL OR @Branch IS NULL
            THROW 51703, 'Run du_lieu_theo_word.sql first; required d26 accounts, Piano course, or branch were not found.', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.courses WHERE id = @Course AND total_sessions = 12)
            THROW 51709, 'The demo requires PIA-CB-12 to have 12 sessions. Existing course was preserved.', 1;
        IF (SELECT COUNT(*) FROM dbo.students WHERE parent_id = @Parent AND full_name IN
            (N'Nguyễn Bảo An', N'Nguyễn An Nhiên', N'Nguyễn Minh Quân', N'Nguyễn Ngọc Hân')) <> 4
            THROW 51704, 'Expected four presentation students under d26_parent_new. No attendance data was installed.', 1;
        IF EXISTS (SELECT 1 FROM dbo.classes WHERE class_code = @ClassCode)
            THROW 51705, 'ATT-MD-DEMO-01 already exists without this install marker. Existing class was preserved.', 1;

        DECLARE @ClassId BIGINT;
        INSERT dbo.classes(course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type,
            max_students, current_students, start_date, end_date, schedule_description, status)
        VALUES(@Course, @Branch, NULL, @Teacher, @ClassCode, N'Lớp Piano - Demo điểm danh', 'GROUP',
            4, 4, DATEADD(DAY, -70, @Today), DATEADD(DAY, 7, @Today),
            N'Lớp dữ liệu test: 10 buổi lịch sử, 1 buổi hôm nay, 1 buổi tương lai.', 'OPEN');
        SET @ClassId = SCOPE_IDENTITY();

        INSERT dbo.enrollments(student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
        SELECT s.id, @ClassId, @Parent, @Now, 'ENROLLED', N'Dữ liệu demo Weekly Schedule / Attendance Tracking'
        FROM dbo.students s
        WHERE s.parent_id = @Parent AND s.full_name IN
            (N'Nguyễn Bảo An', N'Nguyễn An Nhiên', N'Nguyễn Minh Quân', N'Nguyễn Ngọc Hân');

        DECLARE @Lessons TABLE(session_number INT PRIMARY KEY, lesson_date DATE NOT NULL, lesson_id BIGINT NULL);
        ;WITH N AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM N WHERE n < 10)
        INSERT @Lessons(session_number, lesson_date)
        SELECT n, DATEADD(DAY, -7 * (11 - n), @Today) FROM N
        UNION ALL SELECT 11, @Today
        UNION ALL SELECT 12, DATEADD(DAY, 7, @Today);

        -- Dedicated room: do not occupy or change the Word/D26 demonstration rooms.
        IF EXISTS (SELECT 1 FROM dbo.rooms WHERE room_code = 'ATT-MD-ROOM-01')
            THROW 51707, 'Reserved attendance room already exists without installation marker.', 1;
        INSERT dbo.rooms(branch_id, room_code, room_name, capacity, room_type, status, description)
        VALUES(@Branch, 'ATT-MD-ROOM-01', N'Phòng Piano - Demo chuyên cần', 4,
            'PIANO_GROUP', 'AVAILABLE', N'Phòng riêng cho lớp ATT-MD-DEMO-01.');
        DECLARE @RoomId BIGINT = SCOPE_IDENTITY();
        UPDATE dbo.classes SET room_id = @RoomId WHERE id = @ClassId;
        DECLARE @Device INT = 1;
        WHILE @Device <= 4
        BEGIN
            INSERT dbo.equipments(room_id, name, code, category, status, description)
            VALUES(@RoomId, N'Đàn Piano demo số ' + CONVERT(NVARCHAR(2), @Device),
                'ATT-MD-KEY-' + CONVERT(VARCHAR(2), @Device), 'KEYBOARD', 'GOOD',
                N'Thiết bị mẫu cho lớp điểm danh riêng.');
            SET @Device += 1;
        END;

        -- Only a lesson date that the teacher/room can accommodate is installed.
        -- The existing Word and absence/makeup lessons remain untouched.
        IF EXISTS (
            SELECT 1 FROM dbo.lessons l JOIN dbo.classes c ON c.id = l.class_id
            JOIN @Lessons d ON d.lesson_date = l.lesson_date
            WHERE l.status <> 'CANCELLED'
              AND COALESCE(l.teacher_id, c.teacher_id) = @Teacher
              AND l.start_time < CONVERT(TIME, '09:30')
              AND l.end_time > CONVERT(TIME, '06:30'))
            THROW 51708, 'Piano teacher conflicts with the demo slot or its 30-minute break. No existing lesson was moved.', 1;

        INSERT dbo.lessons(class_id, room_id, teacher_id, session_number, lesson_date,
            start_time, end_time, title, lesson_note, status)
        SELECT @ClassId, @RoomId, @Teacher, d.session_number, d.lesson_date,
            '07:00', '09:00',
            N'Buổi ' + CONVERT(NVARCHAR(2), d.session_number) + N': Piano thực hành - chuyên cần',
            CASE WHEN d.session_number <= 10 THEN N'Lịch sử điểm danh mẫu; không phải giao dịch học phí.'
                 WHEN d.session_number = 11 THEN N'Buổi hôm nay: để trống cho giáo viên tự điểm danh.'
                 ELSE N'Buổi tương lai: mọi vai trò chỉ được xem, không được sửa điểm danh.' END,
            CASE WHEN d.session_number <= 10 THEN 'COMPLETED' ELSE 'SCHEDULED' END
        FROM @Lessons d;
        UPDATE d SET lesson_id = l.id
        FROM @Lessons d JOIN dbo.lessons l ON l.class_id = @ClassId AND l.session_number = d.session_number;

        -- Denominator is the existing PIA-CB-12 course (12 sessions).
        -- 0 / 1 / 2 / 3 absences yield NORMAL / NORMAL / WARNING / CRITICAL.
        DECLARE @History TABLE(student_id BIGINT PRIMARY KEY, absent_sessions INT NOT NULL);
        INSERT @History(student_id, absent_sessions)
        SELECT id, CASE full_name WHEN N'Nguyễn Bảo An' THEN 0 WHEN N'Nguyễn An Nhiên' THEN 1
                      WHEN N'Nguyễn Minh Quân' THEN 2 ELSE 3 END
        FROM dbo.students WHERE parent_id = @Parent AND full_name IN
            (N'Nguyễn Bảo An', N'Nguyễn An Nhiên', N'Nguyễn Minh Quân', N'Nguyễn Ngọc Hân');
        INSERT dbo.attendances(lesson_id, student_id, status, note, marked_at)
        SELECT d.lesson_id, h.student_id,
            CASE WHEN d.session_number <= h.absent_sessions THEN 'ABSENT' ELSE 'PRESENT' END,
            CASE WHEN d.session_number <= h.absent_sessions THEN N'Vắng trong dữ liệu chuyên cần mẫu.'
                 ELSE N'Có mặt trong dữ liệu chuyên cần mẫu.' END,
            DATEADD(HOUR, 9, CONVERT(DATETIME2, d.lesson_date))
        FROM @Lessons d CROSS JOIN @History h WHERE d.session_number <= 10;

        INSERT dbo.attendance_weekly_demo_installations(batch_key, installed_at, class_code,
            first_lesson_date, today_lesson_date, future_lesson_date)
        VALUES('ATT_WEEKLY_V1', @Now, @ClassCode, DATEADD(DAY, -70, @Today), @Today, DATEADD(DAY, 7, @Today));
        PRINT N'Đã tạo lớp riêng: 4 học viên, 12 buổi học và 40 bản ghi điểm danh lịch sử.';
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

SELECT batch_key, class_code, first_lesson_date, today_lesson_date, future_lesson_date
FROM dbo.attendance_weekly_demo_installations WHERE batch_key = 'ATT_WEEKLY_V1';
SELECT u.username, u.role, N'Test@123456' AS initial_password
FROM dbo.users u WHERE u.username IN ('d26_staff','d26_admin','d26_parent_new','d26_teacher_piano','d26_teacher_other');
SELECT l.id AS lesson_id, l.session_number, l.lesson_date, l.start_time, l.end_time, l.status,
    (SELECT COUNT(*) FROM dbo.attendances a WHERE a.lesson_id = l.id) AS attendance_records
FROM dbo.lessons l JOIN dbo.classes c ON c.id = l.class_id
WHERE c.class_code = 'ATT-MD-DEMO-01' ORDER BY l.session_number;
SELECT s.full_name, SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END) AS absent_sessions,
    CAST(SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END) * 100.0 / 12 AS DECIMAL(5,1)) AS absent_percent
FROM dbo.students s JOIN dbo.enrollments e ON e.student_id = s.id
JOIN dbo.classes c ON c.id = e.class_id
LEFT JOIN dbo.lessons l ON l.class_id = c.id
LEFT JOIN dbo.attendances a ON a.lesson_id = l.id AND a.student_id = s.id
WHERE c.class_code = 'ATT-MD-DEMO-01' GROUP BY s.id, s.full_name;
GO
