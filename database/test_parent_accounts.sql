USE course_operation_management;
GO

SET NOCOUNT ON;

DECLARE @PasswordHash VARCHAR(255) = '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii';

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'parent_test_01')
BEGIN
    INSERT INTO dbo.users (username, password, full_name, email, phone, role, status)
    VALUES ('parent_test_01', @PasswordHash, N'Phụ Huynh Test Piano', 'parent.test01@example.com', '0909000001', 'PARENT', 'ACTIVE');
END;

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'parent_test_02')
BEGIN
    INSERT INTO dbo.users (username, password, full_name, email, phone, role, status)
    VALUES ('parent_test_02', @PasswordHash, N'Phụ Huynh Test Múa', 'parent.test02@example.com', '0909000002', 'PARENT', 'ACTIVE');
END;

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'parent_test_03')
BEGIN
    INSERT INTO dbo.users (username, password, full_name, email, phone, role, status)
    VALUES ('parent_test_03', @PasswordHash, N'Phụ Huynh Test Võ', 'parent.test03@example.com', '0909000003', 'PARENT', 'ACTIVE');
END;

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'parent_test_04')
BEGIN
    INSERT INTO dbo.users (username, password, full_name, email, phone, role, status)
    VALUES ('parent_test_04', @PasswordHash, N'Phụ Huynh Test Chưa Thi', 'parent.test04@example.com', '0909000004', 'PARENT', 'ACTIVE');
END;

DECLARE @ParentPianoId BIGINT = (SELECT id FROM dbo.users WHERE username = 'parent_test_01');
DECLARE @ParentDanceId BIGINT = (SELECT id FROM dbo.users WHERE username = 'parent_test_02');
DECLARE @ParentMartialArtsId BIGINT = (SELECT id FROM dbo.users WHERE username = 'parent_test_03');
DECLARE @ParentNoResultId BIGINT = (SELECT id FROM dbo.users WHERE username = 'parent_test_04');

IF NOT EXISTS (
    SELECT 1 FROM dbo.students
    WHERE parent_id = @ParentPianoId AND full_name = N'Nguyễn Minh Piano Test'
)
BEGIN
    INSERT INTO dbo.students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentPianoId, N'Nguyễn Minh Piano Test', '2017-04-12', N'Nam', N'Tiểu học Test', N'Có kết quả Piano để test class recommendation');
END;

IF NOT EXISTS (
    SELECT 1 FROM dbo.students
    WHERE parent_id = @ParentPianoId AND full_name = N'Nguyễn An Chưa Thi Test'
)
BEGIN
    INSERT INTO dbo.students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentPianoId, N'Nguyễn An Chưa Thi Test', '2019-08-21', N'Nữ', N'Mầm non Test', N'Chưa có Placement Test để kiểm tra luồng dự phòng');
END;

IF NOT EXISTS (
    SELECT 1 FROM dbo.students
    WHERE parent_id = @ParentDanceId AND full_name = N'Trần Hà Múa Test'
)
BEGIN
    INSERT INTO dbo.students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentDanceId, N'Trần Hà Múa Test', '2018-02-15', N'Nữ', N'Tiểu học Test', N'Có kết quả Múa để test class recommendation');
END;

IF NOT EXISTS (
    SELECT 1 FROM dbo.students
    WHERE parent_id = @ParentMartialArtsId AND full_name = N'Lê Nam Võ Test'
)
BEGIN
    INSERT INTO dbo.students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentMartialArtsId, N'Lê Nam Võ Test', '2016-11-10', N'Nam', N'Tiểu học Test', N'Có kết quả Võ để test class recommendation');
END;

IF NOT EXISTS (
    SELECT 1 FROM dbo.students
    WHERE parent_id = @ParentNoResultId AND full_name = N'Phạm Mai Chưa Thi Test'
)
BEGIN
    INSERT INTO dbo.students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentNoResultId, N'Phạm Mai Chưa Thi Test', '2019-05-06', N'Nữ', N'Mầm non Test', N'Chưa có Placement Test');
END;

DECLARE @PianoStudentId BIGINT = (
    SELECT id FROM dbo.students
    WHERE parent_id = @ParentPianoId AND full_name = N'Nguyễn Minh Piano Test'
);
DECLARE @DanceStudentId BIGINT = (
    SELECT id FROM dbo.students
    WHERE parent_id = @ParentDanceId AND full_name = N'Trần Hà Múa Test'
);
DECLARE @MartialArtsStudentId BIGINT = (
    SELECT id FROM dbo.students
    WHERE parent_id = @ParentMartialArtsId AND full_name = N'Lê Nam Võ Test'
);

DECLARE @PianoTeacherId BIGINT = (SELECT id FROM dbo.users WHERE username = 'teacher_huong');
DECLARE @DanceTeacherId BIGINT = (SELECT id FROM dbo.users WHERE username = 'teacher_linh');
DECLARE @MartialArtsTeacherId BIGINT = (SELECT id FROM dbo.users WHERE username = 'teacher_long');
DECLARE @PianoProgramId BIGINT = (SELECT id FROM dbo.programs WHERE code = 'PIANO');
DECLARE @DanceProgramId BIGINT = (SELECT id FROM dbo.programs WHERE code = 'DANCE');
DECLARE @MartialArtsProgramId BIGINT = (SELECT id FROM dbo.programs WHERE code = 'MARTIAL_ARTS');
DECLARE @PianoCourseId BIGINT = (SELECT id FROM dbo.courses WHERE code = 'PIA-G1');
DECLARE @DanceCourseId BIGINT = (SELECT id FROM dbo.courses WHERE code = 'DAN-KIDS');
DECLARE @MartialArtsCourseId BIGINT = (SELECT id FROM dbo.courses WHERE code = 'MA-BASIC');

IF @PianoStudentId IS NOT NULL
   AND @PianoTeacherId IS NOT NULL
   AND @PianoProgramId IS NOT NULL
   AND @PianoCourseId IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.placement_tests WHERE student_id = @PianoStudentId)
BEGIN
    INSERT INTO dbo.placement_tests (
        student_id, teacher_id, program_id, test_date, rhythm_score, technique_score,
        ear_training_score, expression_score, total_score, teacher_notes,
        recommended_level, recommended_course_id
    )
    VALUES (
        @PianoStudentId, @PianoTeacherId, @PianoProgramId, '2026-09-28', 8.0, 8.5,
        8.0, 8.5, 8.3, N'[TEST] Đề xuất học Piano Grade 1.',
        N'Grade 1 (Sơ cấp)', @PianoCourseId
    );
END;

IF @DanceStudentId IS NOT NULL
   AND @DanceTeacherId IS NOT NULL
   AND @DanceProgramId IS NOT NULL
   AND @DanceCourseId IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.placement_tests WHERE student_id = @DanceStudentId)
BEGIN
    INSERT INTO dbo.placement_tests (
        student_id, teacher_id, program_id, test_date, rhythm_score, technique_score,
        ear_training_score, expression_score, total_score, teacher_notes,
        recommended_level, recommended_course_id
    )
    VALUES (
        @DanceStudentId, @DanceTeacherId, @DanceProgramId, '2026-09-28', 8.5, 9.0,
        8.0, 8.5, 8.5, N'[TEST] Đề xuất lớp Múa Thiếu Nhi và Ballet Căn Bản.',
        N'Căn bản (Ballet Kids)', @DanceCourseId
    );
END;

IF @MartialArtsStudentId IS NOT NULL
   AND @MartialArtsTeacherId IS NOT NULL
   AND @MartialArtsProgramId IS NOT NULL
   AND @MartialArtsCourseId IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.placement_tests WHERE student_id = @MartialArtsStudentId)
BEGIN
    INSERT INTO dbo.placement_tests (
        student_id, teacher_id, program_id, test_date, rhythm_score, technique_score,
        ear_training_score, expression_score, total_score, teacher_notes,
        recommended_level, recommended_course_id
    )
    VALUES (
        @MartialArtsStudentId, @MartialArtsTeacherId, @MartialArtsProgramId, '2026-09-28', 8.5, 8.0,
        8.5, 9.0, 8.5, N'[TEST] Đề xuất lớp Võ Thuật Nhập Môn.',
        N'Đai Trắng (Khởi động)', @MartialArtsCourseId
    );
END;

SELECT
    u.username,
    u.full_name AS parent_name,
    s.id AS student_id,
    s.full_name AS student_name,
    CASE WHEN pt.id IS NULL THEN N'CHƯA CÓ KẾT QUẢ' ELSE N'ĐÃ CÓ KẾT QUẢ' END AS placement_status,
    c.code AS recommended_course_code
FROM dbo.users u
JOIN dbo.students s ON s.parent_id = u.id
LEFT JOIN dbo.placement_tests pt ON pt.student_id = s.id
LEFT JOIN dbo.courses c ON c.id = pt.recommended_course_id
WHERE u.username LIKE 'parent_test_%'
ORDER BY u.username, s.id;
GO
