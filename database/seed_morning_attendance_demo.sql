-- Add-only, date-relative synthetic fixture. Existing attendance is preserved.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
DECLARE @now datetime2=CONVERT(datetime2,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time');
DECLARE @today date=CONVERT(date,@now),@tag varchar(12)='DD'+CONVERT(varchar(6),@now,12);
IF DB_NAME()<>'course_operation_management' THROW 51800,'Unexpected database.',1;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM classes WHERE class_code=@tag+'-AM') BEGIN COMMIT; PRINT 'Morning fixture exists. Progress retained.'; RETURN; END;
DECLARE @parent bigint=(SELECT id FROM users WHERE username='hd261111_parent'),
 @staff bigint=(SELECT id FROM users WHERE username='hd261111_staff'),
 @course bigint=(SELECT id FROM courses WHERE code='HD261111-P1'),
 @branch bigint=(SELECT id FROM branches WHERE code='HD261111');
IF @parent IS NULL OR @staff IS NULL OR @course IS NULL OR @branch IS NULL
 THROW 51801,'Load HD261111 defense fixture first.',1;
DECLARE @username varchar(40)=LOWER(@tag)+'_teacher';
IF EXISTS(SELECT 1 FROM users WHERE username=@username AND role<>'TEACHER') THROW 51802,'Teacher username collision.',1;
IF NOT EXISTS(SELECT 1 FROM users WHERE username=@username)
 INSERT users(username,password,full_name,email,phone,role,status,subject,teaching_subject,created_at,token_version,must_change_password)
 SELECT @username,password,N'['+@tag+N'] Thầy An - điểm danh sáng',@username+'@example.invalid','0909111201',
 'TEACHER','ACTIVE','DAN','PIANO',@now,0,0 FROM users WHERE id=@parent;
DECLARE @teacher bigint=(SELECT id FROM users WHERE username=@username);
INSERT rooms(branch_id,room_code,room_name,capacity,room_type,status,description)
 VALUES(@branch,@tag+'-P',N'['+@tag+N'] Phòng Piano sáng',6,'PIANO_GROUP','AVAILABLE',N'[DEMO] Phòng riêng, không đổi lịch cũ');
DECLARE @room bigint=SCOPE_IDENTITY(),@n int=1;
WHILE @n<=6 BEGIN
 INSERT equipments(room_id,name,code,category,status,description)
 VALUES(@room,N'Đàn Piano mẫu '+CONVERT(nvarchar(2),@n),@tag+'-KB-'+CONVERT(varchar(2),@n),'KEYBOARD','GOOD',N'[DEMO] Thiết bị thực hành');
 SET @n+=1;
END;
INSERT classes(course_id,branch_id,room_id,teacher_id,class_code,class_name,class_type,max_students,current_students,start_date,end_date,schedule_description,status)
 VALUES(@course,@branch,@room,@teacher,@tag+'-AM',N'['+@tag+N'] Piano - test điểm danh sáng','GROUP',6,3,@today,DATEADD(day,63,@today),N'Sáng nay 07:00–09:00 và 09:30–11:30; các buổi sau 09:30–11:30','IN_PROGRESS');
DECLARE @class bigint=SCOPE_IDENTITY();
SET @n=1;
WHILE @n<=10 BEGIN
 INSERT lessons(class_id,room_id,teacher_id,session_number,lesson_date,start_time,end_time,title,status,lesson_note)
 VALUES(@class,@room,@teacher,@n,CASE WHEN @n<=2 THEN @today ELSE DATEADD(day,7*(@n-2),@today) END,
 CASE WHEN @n=1 THEN '07:00' ELSE '09:30' END,CASE WHEN @n=1 THEN '09:00' ELSE '11:30' END,
 N'['+@tag+N'] Buổi '+CONVERT(nvarchar(2),@n)+N' - thực hành điểm danh','SCHEDULED',N'[DEMO] Chưa điểm danh, để tự test');
 SET @n+=1;
END;
SET @n=1;
WHILE @n<=3 BEGIN
 INSERT students(parent_id,full_name,date_of_birth,gender,school_name,notes,created_at)
 VALUES(@parent,N'['+@tag+N'-'+CONVERT(nvarchar(2),@n)+N'] '+CASE @n WHEN 1 THEN N'An Nhiên' WHEN 2 THEN N'Minh Khang' ELSE N'Ngọc Anh' END,
 '2017-06-15',CASE WHEN @n=2 THEN N'Nam' ELSE N'Nữ' END,N'Trường demo',N'[DEMO] Học viên test điểm danh sáng',@now);
 DECLARE @student bigint=SCOPE_IDENTITY();
 INSERT enrollments(student_id,class_id,registered_by_user_id,enrollment_date,status,notes)
 VALUES(@student,@class,@staff,@now,'ENROLLED',N'[DEMO FIXTURE] Đã đóng học phí giả lập, không phải tiền thật');
 DECLARE @enrollment bigint=SCOPE_IDENTITY();
 INSERT enrollment_requests(student_id,class_id,preferred_course_id,requested_by_user_id,reviewed_by_user_id,enrollment_id,status,placement_requested,created_at,reviewed_at)
 VALUES(@student,@class,@course,@parent,@staff,@enrollment,'APPROVED',0,@now,@now);
 INSERT invoices(invoice_code,student_id,enrollment_id,original_amount,discount_type,discount_amount,final_amount,status,due_date,notes)
 VALUES(@tag+'-INV-'+CONVERT(varchar(2),@n),@student,@enrollment,6000000,'NONE',0,6000000,'PAID',@today,N'[DEMO FIXTURE] Không phải tiền thật');
 DECLARE @invoice bigint=SCOPE_IDENTITY();
 INSERT tuition_plans(invoice_id,revision,mode,review_required,created_by_id) VALUES(@invoice,1,'FULL',0,@staff);
 DECLARE @plan bigint=SCOPE_IDENTITY();
 INSERT tuition_installments(plan_id,sequence_number,amount_due,due_date) VALUES(@plan,1,6000000,@today);
 DECLARE @due bigint=SCOPE_IDENTITY();
 INSERT payments(invoice_id,payment_code,payment_method,amount,cash_given,change_amount,cashier_id,status,request_key,balance_after,payment_date,note)
 VALUES(@invoice,@tag+'-FIXTURE-'+CONVERT(varchar(2),@n),'CASH_AT_DESK',6000000,6000000,0,@staff,'SUCCESS',@tag+'-FIXTURE-'+CONVERT(varchar(2),@n),0,@now,N'[DEMO FIXTURE] Không phải tiền thật');
 INSERT tuition_allocations(payment_id,installment_id,amount) VALUES(SCOPE_IDENTITY(),@due,6000000);
 SET @n+=1;
END;
COMMIT;
SELECT @tag+'-AM' class_code,@username teacher_username,'Test@123456' demo_password;
SELECT id,session_number,lesson_date,start_time,end_time FROM lessons WHERE class_id=@class AND lesson_date=@today;
