SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM dbo.programs WHERE code='QA-INST') BEGIN COMMIT; RETURN; END;
DECLARE @hash VARCHAR(255)='$2a$10$31R3fCJj.x3u74yaJ1kvDOOqEvmV1B0LG.Ct1zhtNsx5w5ktmojMy', @now DATETIME2=SYSDATETIME(), @today DATE=CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATE);
INSERT dbo.users(username,password,full_name,role,status,created_at,token_version,must_change_password)
VALUES('qa_inst_parent',@hash,N'[DEMO KỲ] Phụ huynh','PARENT','ACTIVE',@now,0,0),
('qa_inst_other',@hash,N'[DEMO KỲ] Phụ huynh khác','PARENT','ACTIVE',@now,0,0),
('qa_inst_staff',@hash,N'[DEMO KỲ] Nhân viên','STAFF','ACTIVE',@now,0,0),
('qa_inst_admin',@hash,N'[DEMO KỲ] Quản lý','ADMIN','ACTIVE',@now,0,0);
DECLARE @parent BIGINT=(SELECT id FROM dbo.users WHERE username='qa_inst_parent'),
@staff BIGINT=(SELECT id FROM dbo.users WHERE username='qa_inst_staff');
INSERT dbo.programs(code,name) VALUES('QA-INST',N'[DEMO KỲ] Piano');
DECLARE @program BIGINT=SCOPE_IDENTITY();
INSERT dbo.courses(program_id,code,name,level,total_sessions,tuition_fee)
VALUES(@program,'QA-INST-C',N'[DEMO KỲ] Khóa 10 buổi',N'Cơ bản',10,6000000);
DECLARE @course BIGINT=SCOPE_IDENTITY();
INSERT dbo.branches(code,name,address,active,created_at)
VALUES('QA-INST-B',N'[DEMO KỲ] Cơ sở',N'Địa chỉ mô phỏng',1,@now);
DECLARE @branch BIGINT=SCOPE_IDENTITY();
DECLARE @i INT=1;
WHILE @i<=4 BEGIN
 INSERT dbo.classes(course_id,branch_id,class_code,class_name,class_type,max_students,current_students,start_date,end_date,status)
 VALUES(@course,@branch,CONCAT('QA-INST-',@i),CONCAT(N'[DEMO KỲ] Lớp ',@i),'GROUP',10,0,@today,DATEADD(DAY,120,@today),'OPEN');
 DECLARE @class BIGINT=SCOPE_IDENTITY();
 INSERT dbo.students(parent_id,full_name,created_at)
 VALUES(@parent,CASE @i WHEN 1 THEN N'[DEMO KỲ] Minh An · Thu tại quầy'
 WHEN 2 THEN N'[DEMO KỲ] Khánh Linh · Quá hạn'
 WHEN 3 THEN N'[DEMO KỲ] Thanh Hà · QR' ELSE N'[DEMO KỲ] Bảo Nam · Thu đồng thời' END,@now);
 DECLARE @student BIGINT=SCOPE_IDENTITY();
 INSERT dbo.enrollments(student_id,class_id,registered_by_user_id,enrollment_date,status)
 VALUES(@student,@class,@staff,@now,'PENDING_PAYMENT');
 DECLARE @enrollment BIGINT=SCOPE_IDENTITY();
 INSERT dbo.enrollment_requests(student_id,class_id,preferred_course_id,placement_requested,requested_by_user_id,reviewed_by_user_id,
 enrollment_id,status,created_at,reviewed_at)
 VALUES(@student,@class,@course,0,@parent,@staff,@enrollment,'PENDING_PAYMENT',@now,@now);
 INSERT dbo.invoices(invoice_code,student_id,enrollment_id,original_amount,discount_type,discount_amount,final_amount,status,due_date,notes,created_at)
 VALUES(CONCAT('QA-INST-INV-',@i),@student,@enrollment,6000000,'NONE',0,6000000,'UNPAID',DATEADD(DAY,60,@today),N'[DEMO KỲ] Không giao dịch thật',@now);
 SET @i+=1;
END;
COMMIT;
