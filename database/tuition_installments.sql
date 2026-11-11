-- Additive migration. Existing payment/enrollment records are preserved.
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.payments','request_key') IS NULL ALTER TABLE dbo.payments ADD request_key VARCHAR(100) NULL;
IF COL_LENGTH('dbo.payments','balance_after') IS NULL ALTER TABLE dbo.payments ADD balance_after DECIMAL(12,2) NULL;

IF OBJECT_ID('dbo.tuition_plans','U') IS NULL
BEGIN
CREATE TABLE dbo.tuition_plans (
id BIGINT IDENTITY PRIMARY KEY, invoice_id BIGINT NOT NULL UNIQUE REFERENCES dbo.invoices(id),
revision BIGINT NOT NULL, mode VARCHAR(255) NOT NULL, review_required BIT NOT NULL, review_reason NVARCHAR(500) NULL,
created_by_id BIGINT NULL REFERENCES dbo.users(id)
);
END;

IF OBJECT_ID('dbo.tuition_installments','U') IS NULL
BEGIN
CREATE TABLE dbo.tuition_installments (
id BIGINT IDENTITY PRIMARY KEY, plan_id BIGINT NOT NULL REFERENCES dbo.tuition_plans(id),
sequence_number INT NOT NULL, amount_due DECIMAL(12,2) NOT NULL, due_date DATE NOT NULL,
CONSTRAINT UQ_tuition_installment_sequence UNIQUE(plan_id,sequence_number)
);
END;

IF OBJECT_ID('dbo.tuition_allocations','U') IS NULL
BEGIN
CREATE TABLE dbo.tuition_allocations (
id BIGINT IDENTITY PRIMARY KEY, payment_id BIGINT NOT NULL REFERENCES dbo.payments(id),
installment_id BIGINT NOT NULL REFERENCES dbo.tuition_installments(id), amount DECIMAL(12,2) NOT NULL,
CONSTRAINT UQ_tuition_allocation UNIQUE(payment_id,installment_id)
);
END;

IF OBJECT_ID('dbo.tuition_payment_intents','U') IS NULL
BEGIN
CREATE TABLE dbo.tuition_payment_intents (
id BIGINT IDENTITY PRIMARY KEY, invoice_id BIGINT NOT NULL REFERENCES dbo.invoices(id),
order_code BIGINT NOT NULL UNIQUE, plan_revision BIGINT NOT NULL, expected_amount DECIMAL(12,2) NOT NULL,
initiated_by_id BIGINT NOT NULL REFERENCES dbo.users(id), status VARCHAR(25) NOT NULL, checkout_url NVARCHAR(2000) NULL,
qr_code NVARCHAR(3000) NULL, simulated BIT NOT NULL, created_at DATETIME2 NOT NULL
);
END;

IF OBJECT_ID('dbo.tuition_bank_events','U') IS NULL
BEGIN
CREATE TABLE dbo.tuition_bank_events (
id BIGINT IDENTITY PRIMARY KEY, intent_id BIGINT NULL REFERENCES dbo.tuition_payment_intents(id), gateway_order_code BIGINT NULL,
reference VARCHAR(120) NOT NULL UNIQUE, received_amount DECIMAL(12,2) NOT NULL, status VARCHAR(25) NOT NULL,
note NVARCHAR(500) NULL, payment_id BIGINT NULL REFERENCES dbo.payments(id),
reviewed_by_id BIGINT NULL REFERENCES dbo.users(id), reviewed_at DATETIME2 NULL, received_at DATETIME2 NOT NULL
);
END;

IF OBJECT_ID('dbo.tuition_plan_changes','U') IS NULL
BEGIN
CREATE TABLE dbo.tuition_plan_changes (
id BIGINT IDENTITY PRIMARY KEY, plan_id BIGINT NOT NULL REFERENCES dbo.tuition_plans(id),
installment_id BIGINT NOT NULL REFERENCES dbo.tuition_installments(id), expected_revision BIGINT NOT NULL,
expected_paid DECIMAL(12,2) NOT NULL, old_date DATE NOT NULL, proposed_date DATE NOT NULL, reason NVARCHAR(500) NOT NULL,
status VARCHAR(25) NOT NULL, review_note NVARCHAR(500) NULL, requested_by_id BIGINT NOT NULL REFERENCES dbo.users(id),
reviewed_by_id BIGINT NULL REFERENCES dbo.users(id), created_at DATETIME2 NOT NULL, reviewed_at DATETIME2 NULL
);
END;

IF OBJECT_ID('dbo.tuition_notices','U') IS NULL
BEGIN
CREATE TABLE dbo.tuition_notices (
id BIGINT IDENTITY PRIMARY KEY, recipient_id BIGINT NOT NULL REFERENCES dbo.users(id),
installment_id BIGINT NOT NULL REFERENCES dbo.tuition_installments(id), event_key VARCHAR(180) NOT NULL UNIQUE,
type VARCHAR(25) NOT NULL, revision BIGINT NOT NULL, is_read BIT NOT NULL, created_at DATETIME2 NOT NULL
);
END;

IF OBJECT_ID('dbo.tuition_reversals','U') IS NULL
BEGIN
CREATE TABLE dbo.tuition_reversals (
id BIGINT IDENTITY PRIMARY KEY, payment_id BIGINT NOT NULL UNIQUE REFERENCES dbo.payments(id),
status VARCHAR(25) NOT NULL, reason NVARCHAR(500) NOT NULL, review_note NVARCHAR(500) NULL,
requested_by_id BIGINT NOT NULL REFERENCES dbo.users(id), reviewed_by_id BIGINT NULL REFERENCES dbo.users(id),
created_at DATETIME2 NOT NULL, reviewed_at DATETIME2 NULL
);
END;

IF COL_LENGTH('dbo.tuition_bank_events','gateway_order_code') IS NULL
ALTER TABLE dbo.tuition_bank_events ADD gateway_order_code BIGINT NULL;
ALTER TABLE dbo.tuition_bank_events ALTER COLUMN intent_id BIGINT NULL;

IF COL_LENGTH('dbo.tuition_notices','payment_intent_id') IS NULL
ALTER TABLE dbo.tuition_notices ADD payment_intent_id BIGINT NULL
    CONSTRAINT FK_tuition_notices_payment_intent REFERENCES dbo.tuition_payment_intents(id);

-- Replace only status checks on invoices, preserving other constraints.
DECLARE @drop NVARCHAR(MAX)=N'';
SELECT @drop=@drop+N'ALTER TABLE dbo.invoices DROP CONSTRAINT '+QUOTENAME(name)+N';'
FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.invoices')
AND definition LIKE '%status%';
IF LEN(@drop)>0 EXEC sp_executesql @drop;
ALTER TABLE dbo.invoices WITH CHECK ADD CONSTRAINT CK_invoices_installment_status
CHECK(status IN ('UNPAID','PARTIALLY_PAID','PAID','CANCELLED'));
COMMIT;
GO
-- A separate batch lets SQL Server compile against the newly added column.
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.payments') AND name='UX_payments_request_key')
CREATE UNIQUE INDEX UX_payments_request_key ON dbo.payments(request_key) WHERE request_key IS NOT NULL;
