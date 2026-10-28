-- Add the room/equipment types used by facility rules. Run on an existing SQL Server DB.
-- This script does not add inventory, move classes, or alter attendance history.
USE [course_operation_management];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF EXISTS (
    SELECT 1 FROM sys.check_constraints c
    WHERE ((c.parent_object_id = OBJECT_ID(N'dbo.rooms') AND c.definition LIKE N'%room_type%')
        OR (c.parent_object_id = OBJECT_ID(N'dbo.equipments') AND c.definition LIKE N'%category%'))
      AND (SELECT COUNT(DISTINCT d.referenced_minor_id) FROM sys.sql_expression_dependencies d
           WHERE d.referencing_id = c.object_id AND d.referenced_minor_id > 0) > 1
)
    THROW 51000, 'A custom multi-column check exists; review its enum values manually before migrating.', 1;

DECLARE @dropChecks NVARCHAR(MAX) = N'';
SELECT @dropChecks += N'ALTER TABLE dbo.rooms DROP CONSTRAINT ' + QUOTENAME(name) + N';'
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.rooms')
  AND definition LIKE N'%room_type%';
EXEC sys.sp_executesql @dropChecks;

ALTER TABLE dbo.rooms WITH CHECK ADD CONSTRAINT CK_rooms_room_type
CHECK (room_type IN ('PIANO_INDIVIDUAL', 'PIANO_GROUP', 'DANCE_ROOM', 'THEORY_ROOM',
                    'GUITAR_ROOM', 'MULTIPURPOSE_ROOM', 'CHESS_ROOM', 'SPORTS_AREA', 'SWIMMING_POOL'));

SET @dropChecks = N'';
SELECT @dropChecks += N'ALTER TABLE dbo.equipments DROP CONSTRAINT ' + QUOTENAME(name) + N';'
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.equipments')
  AND definition LIKE N'%category%';
EXEC sys.sp_executesql @dropChecks;

ALTER TABLE dbo.equipments WITH CHECK ADD CONSTRAINT CK_equipments_category
CHECK (category IN ('GRAND_PIANO', 'UPRIGHT_PIANO', 'KEYBOARD', 'GUITAR', 'AUDIO', 'MIRROR',
                   'CHESS_SET', 'SPORTS_EQUIPMENT', 'RESCUE_EQUIPMENT', 'OTHER'));

-- Only these known demo rooms are swimming pools. Do not reclassify other sports areas.
UPDATE dbo.rooms SET room_type = 'SWIMMING_POOL'
WHERE room_code IN ('DEMO-SWM-03', 'DEMO-SWM-01', 'DEMO-SWM-02') AND room_type = 'SPORTS_AREA';

COMMIT TRANSACTION;
GO
