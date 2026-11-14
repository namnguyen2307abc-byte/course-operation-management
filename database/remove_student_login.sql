-- Run on an existing database before starting the build without student logins.
-- Keep user IDs and historical links. Do not guess or change parent ownership.
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() NOT IN ('course_operation_management', 'talent_academy_bao_ve')
    THROW 51820, 'Select the application database before running this migration.', 1;
IF COL_LENGTH('dbo.students', 'account_id') IS NULL
    OR COL_LENGTH('dbo.users', 'token_version') IS NULL
    THROW 51821, 'Run admin_management_upgrade.sql first.', 1;

BEGIN TRANSACTION;
IF EXISTS (
    SELECT 1 FROM dbo.students s
    LEFT JOIN dbo.users account ON account.id = s.account_id
    LEFT JOIN dbo.users parent ON parent.id = s.parent_id
    WHERE (account.role = 'STUDENT' OR parent.role = 'STUDENT')
        AND (parent.id IS NULL OR parent.role IS NULL OR parent.role <> 'PARENT')
)
    THROW 51822, 'A linked profile lacks a valid parent. Confirm the actual guardian before retrying.', 1;

IF COLUMNPROPERTY(OBJECT_ID('dbo.users'), 'role', 'AllowsNull') = 0
    ALTER TABLE dbo.users ALTER COLUMN role VARCHAR(30) NULL;

UPDATE dbo.users
SET role = NULL, status = 'INACTIVE', token_version = token_version + 1
WHERE role = 'STUDENT';

DECLARE @Archived INT = @@ROWCOUNT;
COMMIT TRANSACTION;
SELECT @Archived AS archived_accounts;
