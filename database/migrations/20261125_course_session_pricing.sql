-- Run against the database used by the backend. Safe to run more than once.
-- Legacy course prices, class schedules and invoices are intentionally preserved.
IF COL_LENGTH('dbo.courses', 'tuition_per_session') IS NULL
BEGIN
    ALTER TABLE dbo.courses ADD tuition_per_session DECIMAL(12,2) NULL;
END;
GO
