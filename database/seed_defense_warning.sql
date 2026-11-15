-- Companion fixture: 3/20 absences = 15%, the middle warning tier.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF;
DECLARE @tag VARCHAR(18)='$(DemoTag)',@u VARCHAR(30)=LOWER('$(DemoTag)');
DECLARE @now DATETIME2=CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATETIME2),@today DATE;
SET @today=CAST(@now AS DATE);
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM classes WHERE class_code=CONCAT(@tag,'-W')) BEGIN COMMIT; RETURN; END;
DECLARE @parent BIGINT=(SELECT id FROM users WHERE username=CONCAT(@u,'_parent')),
 @staff BIGINT=(SELECT id FROM users WHERE username=CONCAT(@u,'_staff')),
 @teacher BIGINT=(SELECT id FROM users WHERE username=CONCAT(@u,'_teacher')),
 @branch BIGINT=(SELECT id FROM branches WHERE code=@tag),@program BIGINT=(SELECT id FROM programs WHERE code='PIANO');
IF @parent IS NULL OR @branch IS NULL THROW 50001,'Run seed_defense_demo.sql first',1;
DECLARE @room BIGINT=(SELECT id FROM rooms WHERE branch_id=@branch AND room_code='P01');
INSERT courses(program_id,code,name,level,total_sessions,duration_weeks,tuition_fee)
VALUES(@program,CONCAT(@tag,'-P20'),CONCAT(N'[',@tag,N'] Piano 20 buổi - cảnh báo 15%'),N'Cơ bản',20,20,6000000);
DECLARE @course BIGINT=SCOPE_IDENTITY();
INSERT classes(course_id,branch_id,room_id,teacher_id,class_code,class_name,class_type,max_students,current_students,start_date,end_date,schedule_description,status)
VALUES(@course,@branch,@room,@teacher,CONCAT(@tag,'-W'),CONCAT(N'[',@tag,N'] Piano cảnh báo mức giữa'),'GROUP',6,1,DATEADD(DAY,-14,@today),DATEADD(DAY,119,@today),N'13:00 - 15:00','IN_PROGRESS');
DECLARE @class BIGINT=SCOPE_IDENTITY(),@j INT=1;
WHILE @j<=20 BEGIN
 INSERT lessons(class_id,room_id,teacher_id,session_number,lesson_date,start_time,end_time,title,status)
 VALUES(@class,@room,@teacher,@j,DATEADD(DAY,7*(@j-3),@today),'13:00','15:00',CONCAT(N'[',@tag,N'] Buổi ',@j,N' - Piano W'),'SCHEDULED');
 SET @j+=1;
END;
INSERT students(parent_id,full_name,date_of_birth,created_at) VALUES(@parent,CONCAT(N'[',@tag,N'-15] Hà My - cảnh báo vắng 15%'),'2017-03-12',@now);
DECLARE @student BIGINT=SCOPE_IDENTITY();
INSERT enrollments(student_id,class_id,registered_by_user_id,status,notes) VALUES(@student,@class,@staff,'ENROLLED',N'[DEMO] Vắng 3/20 buổi');
DECLARE @enrollment BIGINT=SCOPE_IDENTITY();
INSERT enrollment_requests(student_id,class_id,preferred_course_id,requested_by_user_id,reviewed_by_user_id,enrollment_id,status,placement_requested,created_at,reviewed_at)
VALUES(@student,@class,@course,@parent,@staff,@enrollment,'APPROVED',0,@now,@now);
INSERT invoices(invoice_code,student_id,enrollment_id,original_amount,discount_type,discount_amount,final_amount,status,due_date,notes)
VALUES(CONCAT(@tag,'-INV-15'),@student,@enrollment,6000000,'NONE',0,6000000,'PAID',@today,N'[DEMO FIXTURE] Không phải tiền thật');
DECLARE @invoice BIGINT=SCOPE_IDENTITY();
INSERT tuition_plans(invoice_id,revision,mode,review_required,created_by_id) VALUES(@invoice,1,'FULL',0,@staff);
DECLARE @plan BIGINT=SCOPE_IDENTITY();
INSERT tuition_installments(plan_id,sequence_number,amount_due,due_date) VALUES(@plan,1,6000000,@today);
DECLARE @due BIGINT=SCOPE_IDENTITY();
INSERT payments(invoice_id,payment_code,payment_method,amount,cash_given,change_amount,cashier_id,status,request_key,balance_after,payment_date,note)
VALUES(@invoice,CONCAT(@tag,'-FIXTURE-15'),'CASH_AT_DESK',6000000,6000000,0,@staff,'SUCCESS',CONCAT(@tag,'-FIXTURE-15'),0,@now,N'[DEMO FIXTURE] Không phải tiền thật');
INSERT tuition_allocations(payment_id,installment_id,amount) VALUES(SCOPE_IDENTITY(),@due,6000000);
INSERT attendances(lesson_id,student_id,status,note,marked_at)
SELECT id,@student,'ABSENT',N'[DEMO] 3/20 buổi, cảnh báo mức giữa',@now FROM lessons WHERE class_id=@class AND session_number<=3;
COMMIT;
