-- Non-destructive, repeatable upgrade. Run against course_operation_management after init_database.sql.
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.users', 'token_version') IS NULL
    ALTER TABLE dbo.users ADD token_version BIGINT NOT NULL CONSTRAINT DF_users_token_version DEFAULT 0;
IF COL_LENGTH('dbo.users', 'must_change_password') IS NULL
    ALTER TABLE dbo.users ADD must_change_password BIT NOT NULL CONSTRAINT DF_users_must_change_password DEFAULT 0;
IF COL_LENGTH('dbo.students', 'account_id') IS NULL
    ALTER TABLE dbo.students ADD account_id BIGINT NULL CONSTRAINT FK_students_account REFERENCES dbo.users(id);
IF OBJECT_ID('dbo.admin_audit_logs', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.admin_audit_logs (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        actor_id BIGINT NOT NULL,
        actor_username VARCHAR(100) NOT NULL,
        target_id BIGINT NOT NULL,
        target_username VARCHAR(100) NOT NULL,
        action VARCHAR(40) NOT NULL,
        details NVARCHAR(2000) NOT NULL,
        created_at DATETIME2(6) NOT NULL
    );
    CREATE INDEX IX_admin_audit_created ON dbo.admin_audit_logs(created_at DESC, id DESC);
    CREATE INDEX IX_admin_audit_target ON dbo.admin_audit_logs(target_id, created_at DESC);
END;
COMMIT TRANSACTION;
GO
-- Migrate only unambiguous legacy independent-student accounts. Keep parent_id for compatibility.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
UPDATE s SET account_id = s.parent_id
FROM dbo.students s JOIN dbo.users u ON u.id = s.parent_id
WHERE u.role = 'STUDENT' AND s.account_id IS NULL
    AND (SELECT COUNT(*) FROM dbo.students other WHERE other.parent_id = s.parent_id) = 1
    AND NOT EXISTS (SELECT 1 FROM dbo.students linked WHERE linked.account_id = s.parent_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_students_account' AND object_id = OBJECT_ID('dbo.students'))
    CREATE UNIQUE INDEX UX_students_account ON dbo.students(account_id) WHERE account_id IS NOT NULL;
COMMIT TRANSACTION;
GO
