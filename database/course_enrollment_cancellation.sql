-- Run once on an existing SQL Server database before using parent cancellation.
-- Safe to rerun; existing Placement schedules remain unlinked.
USE course_operation_management;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.enrollment_requests', N'U') IS NOT NULL
BEGIN
    DECLARE @ConstraintName SYSNAME;
    SELECT TOP 1 @ConstraintName = cc.name
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID(N'dbo.enrollment_requests')
      AND (cc.name = N'CK_enrollment_requests_status' OR cc.definition LIKE N'%status%')
    ORDER BY CASE WHEN cc.name = N'CK_enrollment_requests_status' THEN 0 ELSE 1 END;

    IF @ConstraintName IS NOT NULL
        EXEC(N'ALTER TABLE dbo.enrollment_requests DROP CONSTRAINT ' + QUOTENAME(@ConstraintName));

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.enrollment_requests')
          AND name = N'CK_enrollment_requests_status'
    )
        ALTER TABLE dbo.enrollment_requests
            ADD CONSTRAINT CK_enrollment_requests_status
            CHECK (status IN ('PENDING', 'WAITING_PLACEMENT', 'READY_FOR_ASSIGNMENT',
                              'PENDING_PAYMENT', 'APPROVED', 'REJECTED', 'CANCELLED'));
END

IF OBJECT_ID(N'dbo.placement_schedules', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH(N'dbo.placement_schedules', N'enrollment_request_id') IS NULL
        ALTER TABLE dbo.placement_schedules ADD enrollment_request_id BIGINT NULL;

    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys
        WHERE parent_object_id = OBJECT_ID(N'dbo.placement_schedules')
          AND name = N'FK_placement_schedules_enrollment_request'
    )
        ALTER TABLE dbo.placement_schedules
            ADD CONSTRAINT FK_placement_schedules_enrollment_request
            FOREIGN KEY (enrollment_request_id) REFERENCES dbo.enrollment_requests(id);

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.placement_schedules')
          AND name = N'UX_placement_schedules_enrollment_request'
    )
        CREATE UNIQUE INDEX UX_placement_schedules_enrollment_request
            ON dbo.placement_schedules(enrollment_request_id)
            WHERE enrollment_request_id IS NOT NULL;
END
COMMIT TRANSACTION;
GO
