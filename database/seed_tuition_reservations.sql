-- Dedicated local scenarios. Idempotent: never reset outcomes of manual testing.
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM dbo.programs WHERE code='QA-HOLD') BEGIN COMMIT; RETURN; END;
DECLARE @hash VARCHAR(255)='$2a$10$31R3fCJj.x3u74yaJ1kvDOOqEvmV1B0LG.Ct1zhtNsx5w5ktmojMy';
DECLARE @now DATETIME2=CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATETIME2), @today DATE;
SET @today=CAST(@now AS DATE);
INSERT dbo.users(username,password,full_name,role,status,created_at,token_version,must_change_password)
VALUES('qa_hold_parent',@hash,N'[GIỮ CHỖ] Phụ huynh','PARENT','ACTIVE',@now,0,0),
('qa_hold_other',@hash,N'[GIỮ CHỖ] Phụ huynh khác','PARENT','ACTIVE',@now,0,0),
('qa_hold_staff',@hash,N'[GIỮ CHỖ] Nhân viên','STAFF','ACTIVE',@now,0,0),
('qa_hold_admin',@hash,N'[GIỮ CHỖ] Admin','ADMIN','ACTIVE',@now,0,0),
('qa_hold_leader',@hash,N'[GIỮ CHỖ] Quản lý','BRANCH_MANAGER','ACTIVE',@now,0,0);
DECLARE @parent BIGINT=(SELECT id FROM dbo.users WHERE username='qa_hold_parent'), @staff BIGINT=(SELECT id FROM dbo.users WHERE username='qa_hold_staff');
INSERT dbo.programs(code,name) VALUES('QA-HOLD',N'[GIỮ CHỖ] Piano'); DECLARE @program BIGINT=SCOPE_IDENTITY();
INSERT dbo.courses(program_id,code,name,level,total_sessions,tuition_fee) VALUES(@program,'QA-HOLD-C',N'[GIỮ CHỖ] Khóa demo 8.000đ',N'Cơ bản',10,8000);
DECLARE @course BIGINT=SCOPE_IDENTITY();
INSERT dbo.branches(code,name,address,active,created_at) VALUES('QA-HOLD-B',N'[GIỮ CHỖ] Cơ sở demo',N'Dữ liệu kiểm thử',1,@now);
DECLARE @branch BIGINT=SCOPE_IDENTITY(),@i INT=1;
WHILE @i<=9 BEGIN
 DECLARE @suffix VARCHAR(2)=RIGHT('0'+CAST(@i AS VARCHAR(2)),2);
 INSERT dbo.classes(course_id,branch_id,class_code,class_name,class_type,max_students,current_students,start_date,end_date,status)
 VALUES(@course,@branch,CONCAT('QA-HOLD-',@suffix),CONCAT(N'[GIỮ CHỖ] Lớp ',@suffix),'GROUP',5,CASE WHEN @i IN(7,9) THEN 1 ELSE 0 END,DATEADD(DAY,7,@today),DATEADD(DAY,120,@today),'OPEN');
 DECLARE @class BIGINT=SCOPE_IDENTITY();
 INSERT dbo.lessons(class_id,lesson_date,start_time,end_time,session_number,status,title)
 VALUES(@class,DATEADD(DAY,7,@today),'10:00','11:00',1,'SCHEDULED',N'[GIỮ CHỖ] Buổi đầu demo');
 INSERT dbo.students(parent_id,full_name,created_at) VALUES(@parent,CONCAT(N'[GIỮ CHỖ ',@suffix,N'] ',CASE @i
 WHEN 1 THEN N'Chờ xếp lớp' WHEN 2 THEN N'Chờ chốt lịch' WHEN 3 THEN N'Hết hạn - xin gia hạn'
 WHEN 4 THEN N'Hết hạn - hủy chỗ' WHEN 5 THEN N'Hết hạn - QR chưa đóng'
 WHEN 6 THEN N'Tiền cần đối chiếu' WHEN 7 THEN N'Quá hạn đợt sau'
 WHEN 8 THEN N'Thanh toán đúng hạn' ELSE N'Đã đóng đủ' END),@now);
 DECLARE @student BIGINT=SCOPE_IDENTITY();
 IF @i=1 BEGIN
  INSERT dbo.enrollment_requests(student_id,preferred_course_id,placement_requested,requested_by_user_id,status,created_at)
  VALUES(@student,@course,0,@parent,'READY_FOR_ASSIGNMENT',@now);
 END ELSE BEGIN
  INSERT dbo.enrollments(student_id,class_id,registered_by_user_id,enrollment_date,status)
  VALUES(@student,@class,@staff,DATEADD(DAY,-3,@now),CASE WHEN @i IN(7,9) THEN 'ENROLLED' ELSE 'PENDING_PAYMENT' END);
  DECLARE @enrollment BIGINT=SCOPE_IDENTITY();
  INSERT dbo.enrollment_requests(student_id,class_id,preferred_course_id,placement_requested,requested_by_user_id,reviewed_by_user_id,enrollment_id,status,created_at,reviewed_at)
  VALUES(@student,@class,@course,0,@parent,@staff,@enrollment,CASE WHEN @i IN(7,9) THEN 'APPROVED' ELSE 'PENDING_PAYMENT' END,DATEADD(DAY,-3,@now),DATEADD(DAY,-3,@now));
  INSERT dbo.invoices(invoice_code,student_id,enrollment_id,original_amount,discount_type,discount_amount,final_amount,status,due_date,reservation_deadline,notes,created_at)
  VALUES(CONCAT('QA-HOLD-',@suffix),@student,@enrollment,8000,'NONE',0,8000,
   CASE WHEN @i=7 THEN 'PARTIALLY_PAID' WHEN @i=9 THEN 'PAID' ELSE 'UNPAID' END,
   CASE WHEN @i=2 THEN CAST(DATEADD(HOUR,36,@now) AS DATE) ELSE DATEADD(DAY,60,@today) END,
   CASE WHEN @i IN(3,4,6,7,9) THEN DATEADD(HOUR,-3,@now) ELSE DATEADD(HOUR,36,@now) END,
   N'[GIỮ CHỖ] Fixture kiểm thử, khoản tiền mặt seed không phải thu thật',DATEADD(DAY,-3,@now));
  DECLARE @invoice BIGINT=SCOPE_IDENTITY();
  INSERT dbo.tuition_plans(invoice_id,revision,mode,review_required,created_by_id)
  VALUES(@invoice,1,CASE WHEN @i=2 THEN 'FULL' ELSE 'INSTALLMENTS' END,0,CASE WHEN @i=2 THEN NULL ELSE @staff END);
  DECLARE @plan BIGINT=SCOPE_IDENTITY();
  INSERT dbo.tuition_installments(plan_id,sequence_number,amount_due,due_date)
  VALUES(@plan,1,CASE WHEN @i=2 THEN 8000 ELSE 4000 END,CASE WHEN @i=7 THEN DATEADD(DAY,-40,@today) WHEN @i IN(3,4,6) THEN DATEADD(DAY,-1,@today) ELSE @today END);
  DECLARE @first BIGINT=SCOPE_IDENTITY(),@second BIGINT,@third BIGINT;
  IF @i<>2 BEGIN
   INSERT dbo.tuition_installments(plan_id,sequence_number,amount_due,due_date) VALUES(@plan,2,2000,CASE WHEN @i=7 THEN DATEADD(DAY,-9,@today) ELSE DATEADD(DAY,30,@today) END);SET @second=SCOPE_IDENTITY();
   INSERT dbo.tuition_installments(plan_id,sequence_number,amount_due,due_date) VALUES(@plan,3,2000,DATEADD(DAY,60,@today));SET @third=SCOPE_IDENTITY();
  END;
  IF @i IN(7,9) BEGIN
   DECLARE @j INT=1;
   WHILE @j<=CASE WHEN @i=9 THEN 3 ELSE 1 END BEGIN
    DECLARE @amount DECIMAL(12,2)=CASE WHEN @j=1 THEN 4000 ELSE 2000 END;
    INSERT dbo.payments(invoice_id,payment_code,payment_method,amount,cash_given,change_amount,payment_date,cashier_id,status,request_key,balance_after,note)
    VALUES(@invoice,CONCAT('DEMO-HOLD-',@suffix,'-',@j),'CASH_AT_DESK',@amount,@amount,0,DATEADD(DAY,-2,@now),@staff,'SUCCESS',CONCAT('DEMO-HOLD-',@suffix,'-',@j),CASE @j WHEN 1 THEN 4000 WHEN 2 THEN 2000 ELSE 0 END,N'[DEMO] Tiền mặt fixture, không phải khoản thu thực');
    DECLARE @payment BIGINT=SCOPE_IDENTITY();
    INSERT dbo.tuition_allocations(payment_id,installment_id,amount) VALUES(@payment,CASE @j WHEN 1 THEN @first WHEN 2 THEN @second ELSE @third END,@amount);
    SET @j+=1;
   END;
  END;
  IF @i=6 BEGIN
   INSERT dbo.tuition_payment_intents(invoice_id,order_code,plan_revision,expected_amount,initiated_by_id,status,simulated,created_at)
   VALUES(@invoice,7900000000000006,1,4000,@parent,'RECONCILE',1,@now);
   DECLARE @intent BIGINT=SCOPE_IDENTITY();
   INSERT dbo.tuition_bank_events(intent_id,gateway_order_code,reference,received_amount,status,note,received_at)
   VALUES(@intent,7900000000000006,'DEMO-HOLD-BANK-06',4500,'RECONCILE',N'[DEMO] Tiền ngân hàng chưa rõ số tiền; KHÔNG phải giao dịch ngân hàng thật',@now);
  END;
 END;
 SET @i+=1;
END;
COMMIT;
