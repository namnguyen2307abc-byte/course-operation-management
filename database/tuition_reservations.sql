SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.invoices','reservation_deadline') IS NULL
ALTER TABLE dbo.invoices ADD reservation_deadline DATETIME2 NULL;
IF OBJECT_ID('dbo.tuition_reservation_actions','U') IS NULL
CREATE TABLE dbo.tuition_reservation_actions (
 id BIGINT IDENTITY PRIMARY KEY,
 invoice_id BIGINT NOT NULL REFERENCES dbo.invoices(id),
 actor_id BIGINT NOT NULL REFERENCES dbo.users(id),
 action VARCHAR(20) NOT NULL,
 reason NVARCHAR(500) NOT NULL,
 old_deadline DATETIME2 NULL,
 new_deadline DATETIME2 NULL,
 created_at DATETIME2 NOT NULL
);
COMMIT;
