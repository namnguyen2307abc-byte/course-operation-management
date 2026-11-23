USE [course_operation_management];
GO
SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
-- Gateway receipt audit and installment binding. Legacy reconciliation columns are retained for history; no Admin workflow is enabled.
IF COL_LENGTH(N'dbo.tuition_payment_intents',N'installment_id') IS NULL ALTER TABLE dbo.tuition_payment_intents ADD installment_id BIGINT NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'transfer_content') IS NULL ALTER TABLE dbo.tuition_bank_events ADD transfer_content NVARCHAR(500) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'sender_name') IS NULL ALTER TABLE dbo.tuition_bank_events ADD sender_name NVARCHAR(150) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'sender_account') IS NULL ALTER TABLE dbo.tuition_bank_events ADD sender_account VARCHAR(100) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'sender_bank') IS NULL ALTER TABLE dbo.tuition_bank_events ADD sender_bank NVARCHAR(100) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'transferred_at') IS NULL ALTER TABLE dbo.tuition_bank_events ADD transferred_at DATETIME2 NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'source') IS NULL ALTER TABLE dbo.tuition_bank_events ADD source VARCHAR(30) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'reason') IS NULL ALTER TABLE dbo.tuition_bank_events ADD reason VARCHAR(40) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'resolved_invoice_id') IS NULL ALTER TABLE dbo.tuition_bank_events ADD resolved_invoice_id BIGINT NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'recorded_by_id') IS NULL ALTER TABLE dbo.tuition_bank_events ADD recorded_by_id BIGINT NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'allocated_amount') IS NULL ALTER TABLE dbo.tuition_bank_events ADD allocated_amount DECIMAL(12,2) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'refunded_amount') IS NULL ALTER TABLE dbo.tuition_bank_events ADD refunded_amount DECIMAL(12,2) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'refund_reference') IS NULL ALTER TABLE dbo.tuition_bank_events ADD refund_reference VARCHAR(120) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'resolution_note') IS NULL ALTER TABLE dbo.tuition_bank_events ADD resolution_note NVARCHAR(500) NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'resolved_installment_id') IS NULL ALTER TABLE dbo.tuition_bank_events ADD resolved_installment_id BIGINT NULL;
IF COL_LENGTH(N'dbo.tuition_bank_events',N'expected_amount') IS NULL ALTER TABLE dbo.tuition_bank_events ADD expected_amount DECIMAL(12,2) NULL;
IF OBJECT_ID(N'dbo.FK_bank_event_resolved_invoice',N'F') IS NULL EXEC sys.sp_executesql N'ALTER TABLE dbo.tuition_bank_events ADD CONSTRAINT FK_bank_event_resolved_invoice FOREIGN KEY(resolved_invoice_id) REFERENCES dbo.invoices(id);';
IF OBJECT_ID(N'dbo.FK_bank_event_recorded_by',N'F') IS NULL EXEC sys.sp_executesql N'ALTER TABLE dbo.tuition_bank_events ADD CONSTRAINT FK_bank_event_recorded_by FOREIGN KEY(recorded_by_id) REFERENCES dbo.users(id);';
IF OBJECT_ID(N'dbo.FK_bank_event_resolved_installment',N'F') IS NULL EXEC sys.sp_executesql N'ALTER TABLE dbo.tuition_bank_events ADD CONSTRAINT FK_bank_event_resolved_installment FOREIGN KEY(resolved_installment_id) REFERENCES dbo.tuition_installments(id);';
IF OBJECT_ID(N'dbo.FK_bank_intent_installment',N'F') IS NULL EXEC sys.sp_executesql N'ALTER TABLE dbo.tuition_payment_intents ADD CONSTRAINT FK_bank_intent_installment FOREIGN KEY(installment_id) REFERENCES dbo.tuition_installments(id);';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.tuition_bank_events') AND name=N'UX_bank_event_refund_reference') EXEC sys.sp_executesql N'CREATE UNIQUE INDEX UX_bank_event_refund_reference ON dbo.tuition_bank_events(refund_reference) WHERE refund_reference IS NOT NULL;';
COMMIT;
