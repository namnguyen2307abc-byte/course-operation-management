USE course_operation_management;
GO

SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.programs', 'placement_subject') IS NULL
    ALTER TABLE dbo.programs ADD placement_subject VARCHAR(20) NULL;

IF COL_LENGTH('dbo.enrollment_requests', 'requested_program_id') IS NULL
    ALTER TABLE dbo.enrollment_requests ADD requested_program_id BIGINT NULL;

IF COL_LENGTH('dbo.placement_schedules', 'program_id') IS NULL
    ALTER TABLE dbo.placement_schedules ADD program_id BIGINT NULL;

IF OBJECT_ID('dbo.FK_enrollment_requests_requested_program', 'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.enrollment_requests
        ADD CONSTRAINT FK_enrollment_requests_requested_program
        FOREIGN KEY (requested_program_id) REFERENCES dbo.programs(id);';

IF OBJECT_ID('dbo.FK_placement_schedules_program', 'F') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.placement_schedules
        ADD CONSTRAINT FK_placement_schedules_program
        FOREIGN KEY (program_id) REFERENCES dbo.programs(id);';

-- Keep existing requests and schedules intact. Configure only known catalog codes.
EXEC sys.sp_executesql N'
    UPDATE dbo.programs
    SET placement_subject = CASE
        WHEN code IN (''PIANO'', ''GUITAR'') THEN ''DAN''
        WHEN code = ''DANCE'' THEN ''MUA''
        WHEN code = ''MARTIAL_ARTS'' THEN ''VO''
    END
    WHERE placement_subject IS NULL
      AND code IN (''PIANO'', ''GUITAR'', ''DANCE'', ''MARTIAL_ARTS'');';

COMMIT TRANSACTION;
