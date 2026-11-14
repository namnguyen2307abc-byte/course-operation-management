-- Upgrade an existing application database without resetting records.
-- Run before starting the build that supports placement schedule cancellation.
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() NOT IN ('course_operation_management', 'talent_academy_bao_ve')
    THROW 51830, 'Select the application database before running this migration.', 1;
IF OBJECT_ID(N'dbo.placement_schedules', N'U') IS NULL
    THROW 51831, 'The placement_schedules table is required.', 1;

BEGIN TRANSACTION;
IF COL_LENGTH(N'dbo.placement_schedules', N'cancel_reason') IS NULL
    ALTER TABLE dbo.placement_schedules ADD cancel_reason NVARCHAR(500) NULL;
IF COL_LENGTH(N'dbo.placement_schedules', N'cancelled_at') IS NULL
    ALTER TABLE dbo.placement_schedules ADD cancelled_at DATETIME2 NULL;
COMMIT TRANSACTION;
