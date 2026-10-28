-- Run once on the existing SQL Server database. Safe to run again.
-- subject is the existing Placement Test department; teaching_subject is the exact class subject.
USE [course_operation_management];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.users', N'teaching_subject') IS NULL
    ALTER TABLE dbo.users ADD teaching_subject VARCHAR(100) NULL;

COMMIT TRANSACTION;
GO

-- Copy only explicit specialisms; never turn DAN/MUSIC into both PIANO and GUITAR.
UPDATE dbo.users
SET teaching_subject = UPPER(LTRIM(RTRIM(subject)))
WHERE role = 'TEACHER' AND teaching_subject IS NULL
  AND UPPER(LTRIM(RTRIM(subject))) IN ('PIANO', 'GUITAR', 'CHESS', 'SWIMMING', 'DANCE', 'MARTIAL_ARTS');

-- Existing seed account: this teacher teaches the Piano classes in init_database.sql.
UPDATE dbo.users
SET teaching_subject = 'PIANO'
WHERE username = 'teacher_huong' AND role = 'TEACHER' AND teaching_subject IS NULL;

-- For other teachers, set the exact subject after confirming their qualification, for example:
-- UPDATE dbo.users SET teaching_subject = 'GUITAR' WHERE username = 'your_guitar_teacher';
-- Leave subject unchanged so Placement Test keeps its existing department filters.
