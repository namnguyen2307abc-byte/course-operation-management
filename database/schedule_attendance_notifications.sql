-- Additive SQL Server migration for Schedule-Weekly / attendance_tracking.
-- Existing attendance history and payment data are retained.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.notifications', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.notifications (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        recipient_user_id BIGINT NOT NULL REFERENCES dbo.users(id),
        sender_user_id BIGINT NULL REFERENCES dbo.users(id),
        title NVARCHAR(255) NOT NULL,
        message NVARCHAR(1000) NOT NULL,
        type VARCHAR(50) NOT NULL,
        reference_id BIGINT NULL,
        target_url VARCHAR(255) NULL,
        is_read BIT NOT NULL CONSTRAINT DF_notifications_is_read DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_notifications_created_at DEFAULT SYSDATETIME()
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.notifications')
               AND name = N'IX_notifications_recipient_created')
    CREATE INDEX IX_notifications_recipient_created
        ON dbo.notifications(recipient_user_id, created_at DESC) INCLUDE (is_read);
-- makeup_registrations.status is VARCHAR(30), not a database enum.
-- MISSED is supported without rewriting existing status values.
COMMIT TRANSACTION;
