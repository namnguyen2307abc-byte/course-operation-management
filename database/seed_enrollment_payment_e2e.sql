-- Start all scenarios before class assignment; do not create invoices/payments here.
SET NOCOUNT ON; SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
DECLARE @now DATETIME2=CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATETIME2);
DECLARE @parent BIGINT=(SELECT id FROM users WHERE username='qa_simple_parent');
DECLARE @course BIGINT=(SELECT id FROM courses WHERE code='QA-SIMPLE-C');
DECLARE @branch BIGINT=(SELECT id FROM branches WHERE code='QA-SIMPLE-B');
IF @parent IS NULL OR @course IS NULL OR @branch IS NULL THROW 50001,'Run seed_simple_tuition.sql first',1;
DECLARE @i INT=1;
WHILE @i<=3 BEGIN
 DECLARE @code VARCHAR(30)=CONCAT('QA-E2E-0',@i);
 DECLARE @name NVARCHAR(150)=CONCAT(N'[E2E-0',@i,N'] ',CASE @i WHEN 1 THEN N'Xếp lớp rồi thu tiền mặt' WHEN 2 THEN N'Xếp lớp rồi đóng QR' ELSE N'Xếp lớp rồi hủy khi chưa đóng' END);
 IF NOT EXISTS(SELECT 1 FROM classes WHERE class_code=@code)
 INSERT classes(course_id,branch_id,class_code,class_name,class_type,max_students,current_students,start_date,end_date,status)
 VALUES(@course,@branch,@code,CONCAT(N'[E2E] Lớp 0',@i),'GROUP',5,0,DATEADD(DAY,7,CAST(@now AS DATE)),DATEADD(DAY,120,CAST(@now AS DATE)),'OPEN');
 IF NOT EXISTS(SELECT 1 FROM students WHERE full_name=@name AND parent_id=@parent) BEGIN
  INSERT students(parent_id,full_name,date_of_birth,created_at) VALUES(@parent,@name,'2018-01-15',@now);
  DECLARE @student BIGINT=SCOPE_IDENTITY();
  INSERT enrollment_requests(student_id,preferred_course_id,placement_requested,requested_by_user_id,status,created_at)
  VALUES(@student,@course,0,@parent,'READY_FOR_ASSIGNMENT',@now);
 END;
 SET @i+=1;
END;
COMMIT;
