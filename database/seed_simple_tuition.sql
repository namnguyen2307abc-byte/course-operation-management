SET NOCOUNT ON; SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM programs WHERE code='QA-SIMPLE') BEGIN COMMIT; RETURN; END;
DECLARE @now DATETIME2=CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATETIME2),@today DATE;
SET @today=CAST(@now AS DATE);
DECLARE @hash VARCHAR(255)='$2a$10$31R3fCJj.x3u74yaJ1kvDOOqEvmV1B0LG.Ct1zhtNsx5w5ktmojMy';
INSERT users(username,password,full_name,phone,role,status,created_at,token_version,must_change_password) VALUES
('qa_simple_parent',@hash,N'[HỌC PHÍ] Phụ huynh demo','0900000000','PARENT','ACTIVE',@now,0,0),
('qa_simple_staff',@hash,N'[HỌC PHÍ] Nhân viên',NULL,'STAFF','ACTIVE',@now,0,0),
('qa_simple_admin',@hash,N'[HỌC PHÍ] Admin',NULL,'ADMIN','ACTIVE',@now,0,0),
('qa_simple_leader',@hash,N'[HỌC PHÍ] Quản lý chi nhánh',NULL,'BRANCH_MANAGER','ACTIVE',@now,0,0),
('qa_simple_other',@hash,N'[HỌC PHÍ] Phụ huynh khác',NULL,'PARENT','ACTIVE',@now,0,0);
DECLARE @parent BIGINT=(SELECT id FROM users WHERE username='qa_simple_parent'),@staff BIGINT=(SELECT id FROM users WHERE username='qa_simple_staff');
INSERT programs(code,name) VALUES('QA-SIMPLE',N'[HỌC PHÍ] Bộ demo đơn giản');DECLARE @program BIGINT=SCOPE_IDENTITY();
INSERT courses(program_id,code,name,level,total_sessions,tuition_fee) VALUES(@program,'QA-SIMPLE-C',N'[HỌC PHÍ] Khóa demo 8.000đ',N'Cơ bản',10,8000);DECLARE @course BIGINT=SCOPE_IDENTITY();
INSERT branches(code,name,address,active,created_at) VALUES('QA-SIMPLE-B',N'[HỌC PHÍ] Cơ sở demo',N'Địa chỉ kiểm thử',1,@now);DECLARE @branch BIGINT=SCOPE_IDENTITY();
DECLARE @i INT=1;
WHILE @i<=5 BEGIN
 DECLARE @suffix VARCHAR(2)=RIGHT('0'+CAST(@i AS VARCHAR(2)),2),@created DATETIME2=CASE @i WHEN 2 THEN DATEADD(DAY,-10,@now) WHEN 3 THEN DATEADD(DAY,-40,@now) ELSE @now END;
 INSERT classes(course_id,branch_id,class_code,class_name,class_type,max_students,current_students,start_date,end_date,status)
 VALUES(@course,@branch,CONCAT('QA-SIMPLE-',@suffix),CONCAT(N'[HỌC PHÍ] Lớp ',@suffix),'GROUP',5,CASE WHEN @i IN(3,4) THEN 1 ELSE 0 END,DATEADD(DAY,7,@today),DATEADD(DAY,120,@today),'OPEN');DECLARE @class BIGINT=SCOPE_IDENTITY();
 INSERT students(parent_id,full_name,created_at) VALUES(@parent,CONCAT(N'[HỌC PHÍ ',@suffix,N'] ',CASE @i WHEN 1 THEN N'Chờ chốt học phí' WHEN 2 THEN N'Chưa đóng - quá hạn 9 ngày' WHEN 3 THEN N'Đã vào học - đợt sau quá hạn' WHEN 4 THEN N'Đã đóng đủ' ELSE N'Chờ xếp lớp' END),@created);DECLARE @student BIGINT=SCOPE_IDENTITY();
 IF @i=5 BEGIN
  INSERT enrollment_requests(student_id,preferred_course_id,placement_requested,requested_by_user_id,status,created_at) VALUES(@student,@course,0,@parent,'READY_FOR_ASSIGNMENT',@now);
 END ELSE BEGIN
  INSERT enrollments(student_id,class_id,registered_by_user_id,enrollment_date,status) VALUES(@student,@class,@staff,@created,CASE WHEN @i IN(3,4) THEN 'ENROLLED' ELSE 'PENDING_PAYMENT' END);DECLARE @enrollment BIGINT=SCOPE_IDENTITY();
  INSERT enrollment_requests(student_id,class_id,preferred_course_id,placement_requested,requested_by_user_id,reviewed_by_user_id,enrollment_id,status,created_at,reviewed_at)
  VALUES(@student,@class,@course,0,@parent,@staff,@enrollment,CASE WHEN @i IN(3,4) THEN 'APPROVED' ELSE 'PENDING_PAYMENT' END,@created,@created);
  INSERT invoices(invoice_code,student_id,enrollment_id,original_amount,discount_type,discount_amount,final_amount,status,due_date,notes,created_at)
  VALUES(CONCAT('QA-SIMPLE-',@suffix),@student,@enrollment,8000,'NONE',0,8000,CASE @i WHEN 3 THEN 'PARTIALLY_PAID' WHEN 4 THEN 'PAID' ELSE 'UNPAID' END,DATEADD(DAY,60,@today),N'[DEMO] Bộ test đơn giản, tiền mặt có sẵn là fixture',@created);DECLARE @invoice BIGINT=SCOPE_IDENTITY();
  INSERT tuition_plans(invoice_id,revision,mode,review_required,created_by_id) VALUES(@invoice,1,CASE @i WHEN 1 THEN 'FULL' ELSE 'INSTALLMENTS' END,0,CASE @i WHEN 1 THEN NULL ELSE @staff END);DECLARE @plan BIGINT=SCOPE_IDENTITY();
  INSERT tuition_installments(plan_id,sequence_number,amount_due,due_date) VALUES(@plan,1,CASE @i WHEN 1 THEN 8000 ELSE 4000 END,CASE @i WHEN 2 THEN DATEADD(DAY,-9,@today) WHEN 3 THEN DATEADD(DAY,-40,@today) ELSE @today END);DECLARE @first BIGINT=SCOPE_IDENTITY(),@second BIGINT,@third BIGINT;
  IF @i<>1 BEGIN
   INSERT tuition_installments(plan_id,sequence_number,amount_due,due_date) VALUES(@plan,2,2000,CASE @i WHEN 3 THEN DATEADD(DAY,-9,@today) ELSE DATEADD(DAY,30,@today) END);SET @second=SCOPE_IDENTITY();
   INSERT tuition_installments(plan_id,sequence_number,amount_due,due_date) VALUES(@plan,3,2000,DATEADD(DAY,60,@today));SET @third=SCOPE_IDENTITY();
  END;
  IF @i IN(3,4) BEGIN
   DECLARE @j INT=1;
   WHILE @j<=CASE @i WHEN 4 THEN 3 ELSE 1 END BEGIN
    DECLARE @amount DECIMAL(12,2)=CASE @j WHEN 1 THEN 4000 ELSE 2000 END;
    INSERT payments(invoice_id,payment_code,payment_method,amount,cash_given,change_amount,payment_date,cashier_id,status,request_key,balance_after,note)
    VALUES(@invoice,CONCAT('DEMO-SIMPLE-',@suffix,'-',@j),'CASH_AT_DESK',@amount,@amount,0,CASE @i WHEN 3 THEN DATEADD(DAY,-30,@now) ELSE @now END,@staff,'SUCCESS',CONCAT('DEMO-SIMPLE-',@suffix,'-',@j),CASE @j WHEN 1 THEN 4000 WHEN 2 THEN 2000 ELSE 0 END,N'[DEMO] Tiền mặt fixture, không phải khoản thu thực');DECLARE @payment BIGINT=SCOPE_IDENTITY();
    INSERT tuition_allocations(payment_id,installment_id,amount) VALUES(@payment,CASE @j WHEN 1 THEN @first WHEN 2 THEN @second ELSE @third END,@amount);SET @j+=1;
   END;
  END;
 END;
 SET @i+=1;
END;
COMMIT;
