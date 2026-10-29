-- Demo inventory for existing rooms. Apply facility_business_rules.sql first.
-- Append only: no room/class changes, no repairs, and no deletion of existing inventory.
-- Each row is one instrument or one complete set. Running again adds nothing
-- unless required capacity grows or equipment has become broken/under maintenance.
USE [course_operation_management];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @Today DATE = CONVERT(DATE, SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time');
DECLARE @Now TIME = CONVERT(TIME, SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time');
DECLARE @Required TABLE (
    room_id BIGINT, category VARCHAR(50), required_count INT, item_name NVARCHAR(100)
);

-- Stock enough for the room's capacity and the largest class still using it.
-- Oversized or wrong-subject class assignments remain visible; this seed does not fix them.
INSERT INTO @Required (room_id, category, required_count, item_name)
SELECT r.id, rules.category,
       CASE rules.category
           WHEN 'KEYBOARD' THEN demand.students
           WHEN 'CHESS_SET' THEN (demand.students + 1) / 2
           WHEN 'GUITAR' THEN 2
           ELSE 1
       END,
       rules.item_name
FROM dbo.rooms r
CROSS APPLY (VALUES
    ('KEYBOARD', N'Đàn phím thực hành', 'PIANO'),
    ('CHESS_SET', N'Bộ cờ vua tiêu chuẩn', 'CHESS'),
    ('RESCUE_EQUIPMENT', N'Bộ cứu hộ bể bơi', 'SWIMMING'),
    ('GUITAR', N'Đàn Guitar thực hành', 'GUITAR'),
    ('AUDIO', N'Loa thực hành Guitar', 'GUITAR')
) rules(category, item_name, subject)
OUTER APPLY (
    SELECT MAX(c.max_students) AS max_students
    FROM dbo.classes c
    JOIN dbo.courses co ON co.id = c.course_id
    JOIN dbo.programs p ON p.id = co.program_id
    WHERE UPPER(REPLACE(REPLACE(LTRIM(RTRIM(p.code)), '-', '_'), ' ', '_'))
              IN (rules.subject, CASE WHEN rules.subject = 'SWIMMING' THEN 'SWIM' ELSE rules.subject END)
      AND ((c.room_id = r.id AND c.status <> 'FINISHED') OR EXISTS (
          SELECT 1 FROM dbo.lessons l
          WHERE l.class_id = c.id
            AND (l.room_id = r.id OR (l.room_id IS NULL AND c.room_id = r.id))
            AND l.status = 'SCHEDULED'
            AND (l.lesson_date > @Today OR (l.lesson_date = @Today AND l.end_time > @Now))
      ))
) assigned
CROSS APPLY (VALUES (
    CASE WHEN ISNULL(assigned.max_students, 0) > ISNULL(r.capacity, 1)
         THEN assigned.max_students ELSE ISNULL(r.capacity, 1) END
)) demand(students)
WHERE (rules.subject = 'PIANO' AND r.room_type IN ('PIANO_INDIVIDUAL', 'PIANO_GROUP', 'MULTIPURPOSE_ROOM'))
   OR (rules.subject = 'CHESS' AND r.room_type IN ('CHESS_ROOM', 'THEORY_ROOM', 'MULTIPURPOSE_ROOM'))
   OR (rules.subject = 'SWIMMING' AND r.room_type = 'SWIMMING_POOL')
   OR (rules.subject = 'GUITAR' AND r.room_type IN ('GUITAR_ROOM', 'MULTIPURPOSE_ROOM'));

IF EXISTS (SELECT 1 FROM @Required WHERE required_count < 1 OR required_count > 200)
    THROW 51001, 'Unexpected inventory target. Review room/class capacity before seeding.', 1;

DECLARE @RoomId BIGINT, @Category VARCHAR(50), @RequiredCount INT, @ItemName NVARCHAR(100);
DECLARE @GoodCount INT, @Missing INT, @Slot INT, @Code VARCHAR(50), @Inserted INT = 0;
DECLARE inventory CURSOR LOCAL FAST_FORWARD FOR
    SELECT room_id, category, required_count, item_name FROM @Required ORDER BY room_id, category;
OPEN inventory;
FETCH NEXT FROM inventory INTO @RoomId, @Category, @RequiredCount, @ItemName;
WHILE @@FETCH_STATUS = 0
BEGIN
    SELECT @GoodCount = COUNT(*)
    FROM dbo.equipments WITH (UPDLOCK, HOLDLOCK)
    WHERE room_id = @RoomId AND status = 'GOOD'
      AND (category = @Category OR (@Category = 'KEYBOARD' AND category IN ('GRAND_PIANO', 'UPRIGHT_PIANO')));

    SET @Missing = @RequiredCount - @GoodCount;
    SET @Slot = 1;
    WHILE @Missing > 0
    BEGIN
        SET @Code = CONCAT('FX-', @RoomId, '-', @Category, '-', @Slot);
        IF NOT EXISTS (SELECT 1 FROM dbo.equipments WITH (UPDLOCK, HOLDLOCK) WHERE code = @Code)
        BEGIN
            INSERT INTO dbo.equipments (room_id, name, code, category, status, description)
            VALUES (@RoomId, CONCAT(@ItemName, N' #', @Slot), @Code, @Category, 'GOOD',
                    N'Dữ liệu demo bổ sung cho điều kiện thiết bị khi tạo và xếp lớp. Mỗi dòng là một thiết bị hoặc một bộ đầy đủ.');
            SET @Missing -= 1;
            SET @Inserted += 1;
        END;
        SET @Slot += 1;
    END;
    FETCH NEXT FROM inventory INTO @RoomId, @Category, @RequiredCount, @ItemName;
END;
CLOSE inventory;
DEALLOCATE inventory;

IF EXISTS (
    SELECT 1 FROM @Required required
    WHERE required.required_count > (
        SELECT COUNT(*) FROM dbo.equipments e
        WHERE e.room_id = required.room_id AND e.status = 'GOOD'
          AND (e.category = required.category
            OR (required.category = 'KEYBOARD' AND e.category IN ('GRAND_PIANO', 'UPRIGHT_PIANO')))
    )
)
    THROW 51002, 'Inventory target not satisfied; no changes will be committed.', 1;

COMMIT TRANSACTION;
SELECT @Inserted AS inserted_equipment_count;
GO
