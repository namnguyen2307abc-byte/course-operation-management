USE [course_operation_management];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;
-- Snapshot policy at tuition confirmation; no historical invoice is recalculated.
IF OBJECT_ID(N'dbo.tuition_discount_snapshots',N'U') IS NULL
BEGIN
 CREATE TABLE dbo.tuition_discount_snapshots (
  invoice_id BIGINT NOT NULL PRIMARY KEY,
  source VARCHAR(30) NOT NULL,
  placement_test_id BIGINT NULL,
  placement_score DECIMAL(5,2) NULL,
  percentage INT NOT NULL,
  method VARCHAR(30) NOT NULL,
  discount_value DECIMAL(12,2) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  original_amount DECIMAL(12,2) NOT NULL,
  reason NVARCHAR(255) NULL,
  policy_version VARCHAR(40) NOT NULL,
  confirmed_at DATETIME2 NOT NULL,
  CONSTRAINT FK_tuition_discount_snapshot_invoice FOREIGN KEY(invoice_id) REFERENCES dbo.invoices(id)
 );
END;
COMMIT;
