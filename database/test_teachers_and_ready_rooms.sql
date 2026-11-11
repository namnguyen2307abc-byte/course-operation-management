-- Demo only. Apply class_management_scheduling.sql and facility_business_rules.sql first.
-- Adds 2 teachers per subject and 1 fully equipped spare room per subject/active branch.
-- Demo accounts use password 123456. Never use this password for real accounts.
-- Existing accounts, rooms, classes, lessons and assignments are not updated or deleted.
USE [course_operation_management];
GO
SET XACT_ABORT ON;
SET NOCOUNT ON;
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.users', N'teaching_subject') IS NULL
    THROW 51100, 'Run class_management_scheduling.sql before this demo seed.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.branches WHERE active = 1)
    THROW 51101, 'No active branch available for demo rooms.', 1;

DECLARE @PasswordHash VARCHAR(255) = '$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii';
DECLARE @Teachers TABLE (
    username VARCHAR(100), full_name NVARCHAR(150), department VARCHAR(100), teaching_subject VARCHAR(100)
);
INSERT INTO @Teachers VALUES
('demo_teacher_piano_01', N'Cô Nguyễn Minh Anh (Piano Demo)', 'DAN', 'PIANO'),
('demo_teacher_piano_02', N'Thầy Trần Quốc Bảo (Piano Demo)', 'DAN', 'PIANO'),
('demo_teacher_guitar_01', N'Thầy Lê Hoàng Nam (Guitar Demo)', 'DAN', 'GUITAR'),
('demo_teacher_guitar_02', N'Cô Phạm Thu Hà (Guitar Demo)', 'DAN', 'GUITAR'),
('demo_teacher_swimming_01', N'Thầy Nguyễn Đức Huy (Bơi Demo)', 'SWIMMING', 'SWIMMING'),
('demo_teacher_swimming_02', N'Cô Vũ Ngọc Lan (Bơi Demo)', 'SWIMMING', 'SWIMMING'),
('demo_teacher_chess_01', N'Thầy Đỗ Quang Minh (Cờ vua Demo)', 'CHESS', 'CHESS'),
('demo_teacher_chess_02', N'Cô Trần Khánh Linh (Cờ vua Demo)', 'CHESS', 'CHESS');

IF EXISTS (
    SELECT 1 FROM @Teachers seed JOIN dbo.users u ON u.username = seed.username
    WHERE u.role <> 'TEACHER' OR ISNULL(u.teaching_subject, '') <> seed.teaching_subject
)
    THROW 51102, 'Demo username already belongs to a different role or subject. No accounts were overwritten.', 1;

INSERT INTO dbo.users (username, password, full_name, email, role, status, subject, teaching_subject)
SELECT seed.username, @PasswordHash, seed.full_name, CONCAT(seed.username, '@example.com'),
       'TEACHER', 'ACTIVE', seed.department, seed.teaching_subject
FROM @Teachers seed
WHERE NOT EXISTS (SELECT 1 FROM dbo.users u WITH (UPDLOCK, HOLDLOCK) WHERE u.username = seed.username);

DECLARE @Subjects TABLE (
    code VARCHAR(10), room_name NVARCHAR(100), room_type VARCHAR(50), capacity INT
);
INSERT INTO @Subjects VALUES
('PIA', N'Phòng Piano thực hành dự phòng', 'PIANO_GROUP', 12),
('GUI', N'Phòng Guitar thực hành dự phòng', 'GUITAR_ROOM', 12),
('CHS', N'Phòng Cờ vua dự phòng', 'CHESS_ROOM', 16),
('SWM', N'Bể bơi thực hành dự phòng', 'SWIMMING_POOL', 12);

IF EXISTS (SELECT 1 FROM dbo.branches b CROSS JOIN @Subjects s
           WHERE b.active = 1 AND LEN(CONCAT('DEMO-READY-', s.code, '-', b.code)) > 50)
    THROW 51103, 'Branch code too long for demo room code.', 1;

INSERT INTO dbo.rooms (branch_id, room_code, room_name, capacity, room_type, status, description)
SELECT b.id, CONCAT('DEMO-READY-', s.code, '-', b.code), CONCAT(s.room_name, ' - ', b.code),
       s.capacity, s.room_type, 'AVAILABLE',
       N'Phòng demo có thiết bị phù hợp để kiểm thử tạo lớp, xếp lịch và phân công giáo viên. Chưa gán lịch tự động.'
FROM dbo.branches b CROSS JOIN @Subjects s
WHERE b.active = 1 AND NOT EXISTS (
    SELECT 1 FROM dbo.rooms r WITH (UPDLOCK, HOLDLOCK)
    WHERE r.branch_id = b.id AND r.room_code = CONCAT('DEMO-READY-', s.code, '-', b.code)
);

DECLARE @Numbers TABLE (n INT PRIMARY KEY);
INSERT INTO @Numbers VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),(13),(14),(15),(16);

-- One inventory row is one instrument, one complete chess set, or one rescue set.
INSERT INTO dbo.equipments (room_id, name, code, category, status, description)
SELECT r.id, CONCAT(items.item_name, N' #', n.n), CONCAT('RD-', r.id, '-', items.category, '-', n.n),
       items.category, 'GOOD', N'Thiết bị demo cho phòng dự phòng; mỗi dòng là một thiết bị hoặc bộ đầy đủ.'
FROM dbo.branches b CROSS JOIN @Subjects s
JOIN dbo.rooms r ON r.room_code = CONCAT('DEMO-READY-', s.code, '-', b.code) AND r.branch_id = b.id
CROSS APPLY (VALUES
    ('PIA', 'KEYBOARD', 12, N'Đàn phím thực hành'),
    ('GUI', 'GUITAR', 2, N'Đàn Guitar thực hành'),
    ('GUI', 'AUDIO', 1, N'Loa thực hành Guitar'),
    ('CHS', 'CHESS_SET', 8, N'Bộ cờ vua tiêu chuẩn'),
    ('SWM', 'RESCUE_EQUIPMENT', 1, N'Bộ cứu hộ bể bơi'),
    ('SWM', 'SPORTS_EQUIPMENT', 2, N'Bảng tập bơi')
) items(subject_code, category, quantity, item_name)
JOIN @Numbers n ON n.n <= items.quantity
WHERE b.active = 1 AND items.subject_code = s.code
  AND NOT EXISTS (
      SELECT 1 FROM dbo.equipments e WITH (UPDLOCK, HOLDLOCK)
      WHERE e.code = CONCAT('RD-', r.id, '-', items.category, '-', n.n)
  );

COMMIT TRANSACTION;
GO
