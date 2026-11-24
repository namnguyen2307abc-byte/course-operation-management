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
        UNION ALL SELECT 12, DATEADD(DAY, 7, @Today)
        OPTION (MAXRECURSION 10);

        INSERT dbo.lessons(class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time,
            title, lesson_note, status)
        SELECT @ClassId, NULL, @Teacher, session_number, lesson_date,
            CASE WHEN session_number = 11 THEN CONVERT(TIME, '07:00') ELSE CONVERT(TIME, '09:30') END,
            CASE WHEN session_number = 11 THEN CONVERT(TIME, '08:30') ELSE CONVERT(TIME, '11:00') END,
            CONCAT(N'Buổi ', session_number, N': Lớp Piano - Demo điểm danh'),
            CASE WHEN session_number <= 10 THEN N'Buổi lịch sử dùng để demo báo cáo chuyên cần.'
                 WHEN session_number = 11 THEN N'Buổi hôm nay: để trống attendance để giáo viên thực hành điểm danh.'
                 ELSE N'Buổi tương lai: dùng để demo trạng thái khóa điểm danh với giáo viên.' END,
            'SCHEDULED'
        FROM @Lessons;

        -- Resolve inserted lesson IDs by unique class/session number.
        UPDATE x SET lesson_id = l.id
        FROM @Lessons x JOIN dbo.lessons l ON l.class_id = @ClassId AND l.session_number = x.session_number;

        -- Attendance profiles for sessions 1..10:
        -- Bao An: 0 absences (safe); An Nhien: 2 (warning); Minh Quan: 4 (critical);
        -- Ngoc Han: 1 absence (low warning boundary). Sessions 11 and 12 stay unmarked.
        INSERT dbo.attendances(lesson_id, student_id, status, note, marked_at)
        SELECT x.lesson_id, s.id,
            CASE
                WHEN s.full_name = N'Nguyễn An Nhiên' AND x.session_number IN (2, 7) THEN 'ABSENT'
                WHEN s.full_name = N'Nguyễn Minh Quân' AND x.session_number IN (1, 3, 6, 9) THEN 'ABSENT'
                WHEN s.full_name = N'Nguyễn Ngọc Hân' AND x.session_number = 5 THEN 'ABSENT'
                ELSE 'PRESENT'
            END,
            CASE
                WHEN (s.full_name = N'Nguyễn An Nhiên' AND x.session_number IN (2, 7))
                  OR (s.full_name = N'Nguyễn Minh Quân' AND x.session_number IN (1, 3, 6, 9))
                  OR (s.full_name = N'Nguyễn Ngọc Hân' AND x.session_number = 5)
                THEN N'Nghỉ demo để minh họa thống kê vắng.'
                ELSE N'Có mặt - dữ liệu demo.'
            END,
            DATEADD(MINUTE, 15, CONVERT(DATETIME2, x.lesson_date))
        FROM @Lessons x
        CROSS JOIN dbo.students s
        WHERE x.session_number <= 10
          AND s.parent_id = @Parent
          AND s.full_name IN (N'Nguyễn Bảo An', N'Nguyễn An Nhiên', N'Nguyễn Minh Quân', N'Nguyễn Ngọc Hân');

        INSERT dbo.attendance_weekly_demo_installations(batch_key, installed_at, class_code,
            first_lesson_date, today_lesson_date, future_lesson_date)
        VALUES('ATT_WEEKLY_V1', @Now, @ClassCode, DATEADD(DAY, -70, @Today), @Today, DATEADD(DAY, 7, @Today));
        PRINT N'Đã cài lớp ATT-MD-DEMO-01 với roster 4 học viên và 12 buổi.';
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

SELECT i.class_code, i.first_lesson_date, i.today_lesson_date, i.future_lesson_date,
       u.username AS assigned_teacher, N'Test@123456' AS initial_password,
       N'd26_teacher_other / Test@123456 (kiểm tra giáo viên không phụ trách)' AS other_teacher_test_account
FROM dbo.attendance_weekly_demo_installations i
JOIN dbo.classes c ON c.class_code = i.class_code
JOIN dbo.users u ON u.id = c.teacher_id
WHERE i.batch_key = 'ATT_WEEKLY_V1';
SELECT c.class_code, l.session_number, l.lesson_date, l.start_time, l.end_time,
       COUNT(a.id) AS attendance_rows,
       CASE WHEN l.session_number = 11 THEN N'Hôm nay: giáo viên thực hành lưu attendance'
            WHEN l.session_number = 12 THEN N'Tương lai: giáo viên chỉ được xem'
            ELSE N'Lịch sử: dùng đối chiếu báo cáo' END AS demo_case
FROM dbo.attendance_weekly_demo_installations i
JOIN dbo.classes c ON c.class_code = i.class_code
JOIN dbo.lessons l ON l.class_id = c.id
LEFT JOIN dbo.attendances a ON a.lesson_id = l.id
WHERE i.batch_key = 'ATT_WEEKLY_V1'
GROUP BY c.class_code, l.session_number, l.lesson_date, l.start_time, l.end_time
ORDER BY l.session_number;
SELECT s.full_name,
       SUM(CASE WHEN a.status = 'PRESENT' THEN 1 ELSE 0 END) AS present_count,
       SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END) AS absent_count,
       N'Buổi 11 hôm nay chưa điểm danh; buổi 12 tương lai chưa điểm danh' AS note
FROM dbo.attendance_weekly_demo_installations i
JOIN dbo.classes c ON c.class_code = i.class_code
JOIN dbo.enrollments e ON e.class_id = c.id AND e.status = 'ENROLLED'
JOIN dbo.students s ON s.id = e.student_id
LEFT JOIN dbo.lessons l ON l.class_id = c.id AND l.session_number <= 10
LEFT JOIN dbo.attendances a ON a.lesson_id = l.id AND a.student_id = s.id
WHERE i.batch_key = 'ATT_WEEKLY_V1'
GROUP BY s.full_name
ORDER BY s.full_name;
GO
