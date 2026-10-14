-- Existing SQL Server databases may still have the old six-state check.
-- Keep its exact expression and add CANCELLED; no records are changed.
USE course_operation_management;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @constraintName SYSNAME = N'CK_enrollment_requests_status';
DECLARE @definition NVARCHAR(MAX);
SELECT @definition = definition FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.enrollment_requests') AND name = @constraintName;

IF @definition IS NULL
BEGIN
    ROLLBACK TRANSACTION;
    THROW 50001, 'Expected enrollment_requests status constraint was not found. Review the database before migrating.', 1;
END;

IF @definition NOT LIKE N'%''CANCELLED''%'
BEGIN
    DECLARE @sql NVARCHAR(MAX) = N'ALTER TABLE dbo.enrollment_requests DROP CONSTRAINT ' + QUOTENAME(@constraintName);
    EXEC sys.sp_executesql @sql;
    SET @sql = N'ALTER TABLE dbo.enrollment_requests WITH CHECK ADD CONSTRAINT '
        + QUOTENAME(@constraintName) + N' CHECK ((' + @definition + N') OR [status] = N''CANCELLED'')';
    EXEC sys.sp_executesql @sql;
END;
COMMIT TRANSACTION;
GO
