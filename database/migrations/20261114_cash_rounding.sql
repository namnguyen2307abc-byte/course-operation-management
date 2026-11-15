-- Additive migration. Existing receipts keep a zero rounding subsidy.
USE [course_operation_management];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.payments','cash_rounding_amount') IS NULL
 ALTER TABLE dbo.payments ADD cash_rounding_amount DECIMAL(12,2) NOT NULL
  CONSTRAINT DF_payments_cash_rounding_amount DEFAULT(0) WITH VALUES;
COMMIT;
GO
