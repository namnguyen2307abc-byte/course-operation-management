-- Upgrade enrollment_requests for the staff-managed assignment workflow.
-- Safe to run more than once on SQL Server.

IF OBJECT_ID(N'dbo.enrollment_requests', N'U') IS NOT NULL
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys.columns
        WHERE object_id = OBJECT_ID(N'dbo.enrollment_requests')
          AND name = N'class_id'
          AND is_nullable = 0
    )
        ALTER TABLE dbo.enrollment_requests ALTER COLUMN class_id BIGINT NULL;

    IF COL_LENGTH(N'dbo.enrollment_requests', N'preferred_course_id') IS NULL
        ALTER TABLE dbo.enrollment_requests ADD preferred_course_id BIGINT NULL;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.foreign_keys
        WHERE parent_object_id = OBJECT_ID(N'dbo.enrollment_requests')
          AND name = N'FK_enrollment_requests_preferred_course'
    )
        ALTER TABLE dbo.enrollment_requests
            ADD CONSTRAINT FK_enrollment_requests_preferred_course
            FOREIGN KEY (preferred_course_id) REFERENCES dbo.courses(id);

    IF COL_LENGTH(N'dbo.enrollment_requests', N'placement_requested') IS NULL
        ALTER TABLE dbo.enrollment_requests
            ADD placement_requested BIT NULL;

    UPDATE dbo.enrollment_requests
    SET placement_requested = 0
    WHERE placement_requested IS NULL;

    IF EXISTS (
        SELECT 1
        FROM sys.columns
        WHERE object_id = OBJECT_ID(N'dbo.enrollment_requests')
          AND name = N'placement_requested'
          AND is_nullable = 1
    )
        ALTER TABLE dbo.enrollment_requests ALTER COLUMN placement_requested BIT NOT NULL;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.default_constraints dc
        INNER JOIN sys.columns c
            ON c.object_id = dc.parent_object_id
           AND c.column_id = dc.parent_column_id
        WHERE dc.parent_object_id = OBJECT_ID(N'dbo.enrollment_requests')
          AND c.name = N'placement_requested'
    )
        ALTER TABLE dbo.enrollment_requests
            ADD CONSTRAINT DF_enrollment_requests_placement_requested
            DEFAULT 0 FOR placement_requested;

    -- Update check constraint on status column to support all workflow statuses
    DECLARE @ConstraintName NVARCHAR(200);
    SELECT @ConstraintName = cc.name
    FROM sys.check_constraints cc
    JOIN sys.tables t ON cc.parent_object_id = t.object_id
    JOIN sys.columns c ON cc.parent_object_id = c.object_id AND cc.parent_column_id = c.column_id
    WHERE t.name = 'enrollment_requests' AND c.name = 'status';

    IF @ConstraintName IS NOT NULL
    BEGIN
        DECLARE @DropSql NVARCHAR(500) = 'ALTER TABLE dbo.enrollment_requests DROP CONSTRAINT ' + QUOTENAME(@ConstraintName);
        EXEC sp_executesql @DropSql;
    END

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints WHERE name = 'CK_enrollment_requests_status'
    )
        ALTER TABLE dbo.enrollment_requests
            ADD CONSTRAINT CK_enrollment_requests_status
            CHECK (status IN ('PENDING', 'WAITING_PLACEMENT', 'READY_FOR_ASSIGNMENT', 'PENDING_PAYMENT', 'APPROVED', 'REJECTED', 'CANCELLED'));
END
GO
