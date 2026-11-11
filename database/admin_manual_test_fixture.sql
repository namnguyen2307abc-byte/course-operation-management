-- Local manual-test fixture. All accounts use Test@123456; only qa_password requires a password change.
-- Owns only qa_* accounts and QA-ADM-* business records. Existing data/passwords are never overwritten.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.admin_audit_logs', 'U') IS NULL
    THROW 51000, 'Run admin_management_upgrade.sql first.', 1;
IF EXISTS (SELECT 1 FROM dbo.admin_audit_logs WHERE action = 'SEED_TEST_DATA' AND details LIKE N'admin-manual-test-v1:%')
BEGIN
    PRINT 'Fixture already installed. Changes made during manual testing have been preserved.';
    RETURN;
END;
IF EXISTS (SELECT 1 FROM dbo.users WHERE username LIKE 'qa[_]%')
    THROW 51001, 'qa_ accounts already exist without this fixture marker. Inspect before installing.', 1;

BEGIN TRANSACTION;
DECLARE @now DATETIME2 = SYSDATETIME(), @today DATE = CONVERT(DATE, SYSDATETIME());
DECLARE @hash VARCHAR(255) = '$2a$10$31R3fCJj.x3u74yaJ1kvDOOqEvmV1B0LG.Ct1zhtNsx5w5ktmojMy';
DECLARE @accounts TABLE (username VARCHAR(100), full_name NVARCHAR(150), role VARCHAR(30), status VARCHAR(30), force_password BIT, subject VARCHAR(100));
INSERT INTO @accounts VALUES
('qa_admin', N'[TEST] Nguyễn Minh Quản - Admin', 'ADMIN', 'ACTIVE', 0, NULL),
('qa_manager', N'[TEST] Trần Thu Hà - Quản lý', 'BRANCH_MANAGER', 'ACTIVE', 0, NULL),
('qa_teacher_linked', N'[TEST] Lê Ngọc Anh - GV có lớp', 'TEACHER', 'ACTIVE', 0, 'DAN'),
('qa_teacher_free', N'[TEST] Phạm Bảo Châu - GV chưa có lớp', 'TEACHER', 'ACTIVE', 0, 'DAN'),
('qa_parent_linked', N'[TEST] Đỗ Thanh Mai - PH có con', 'PARENT', 'ACTIVE', 0, NULL),
('qa_parent_free', N'[TEST] Vũ Hoài Nam - PH chưa có con', 'PARENT', 'ACTIVE', 0, NULL),
('qa_student', N'[TEST] Nguyễn Khánh Linh - Học viên', 'STUDENT', 'ACTIVE', 0, NULL),
('qa_locked', N'[TEST] Bùi Minh Đức - Đang khóa', 'STAFF', 'INACTIVE', 0, NULL),
('qa_password', N'[TEST] Hoàng Phương - Cần đổi mật khẩu', 'STAFF', 'ACTIVE', 1, NULL),
('qa_staff_01', N'[TEST] Nguyễn Thảo Vy', 'STAFF', 'ACTIVE', 0, NULL),
('qa_staff_02', N'[TEST] Trần Đức Huy', 'STAFF', 'ACTIVE', 0, NULL),
('qa_staff_03', N'[TEST] Lê Thu Trang', 'STAFF', 'ACTIVE', 0, NULL),
('qa_staff_04', N'[TEST] Phạm Minh Khang', 'STAFF', 'INACTIVE', 0, NULL),
('qa_staff_05', N'[TEST] Võ Ngọc Bích', 'STAFF', 'ACTIVE', 0, NULL),
('qa_staff_06', N'[TEST] Đặng Quốc Bảo', 'STAFF', 'ACTIVE', 0, NULL),
('qa_staff_07', N'[TEST] Dương Hoàng Yến', 'STAFF', 'ACTIVE', 0, NULL),
('qa_staff_08', N'[TEST] Đinh Gia Hân', 'STAFF', 'INACTIVE', 0, NULL);
INSERT INTO dbo.users (username, password, full_name, email, phone, role, status, subject, must_change_password, token_version, created_at)
SELECT username, @hash, full_name, username + '@example.test', '090800' + RIGHT('0000' + CONVERT(VARCHAR(4), ROW_NUMBER() OVER (ORDER BY username)), 4),
       role, status, subject, force_password, 0, DATEADD(MINUTE, -ROW_NUMBER() OVER (ORDER BY username), @now)
FROM @accounts;

DECLARE @admin BIGINT = (SELECT id FROM dbo.users WHERE username = 'qa_admin');
DECLARE @parent BIGINT = (SELECT id FROM dbo.users WHERE username = 'qa_parent_linked');
DECLARE @teacher BIGINT = (SELECT id FROM dbo.users WHERE username = 'qa_teacher_linked');
DECLARE @studentAccount BIGINT = (SELECT id FROM dbo.users WHERE username = 'qa_student');
DECLARE @cashier BIGINT = (SELECT id FROM dbo.users WHERE username = 'qa_staff_01');
DECLARE @students TABLE (n INT PRIMARY KEY, name NVARCHAR(150));
INSERT INTO @students VALUES
(1,N'[TEST ADMIN] Nguyễn Khánh Linh'),(2,N'[TEST ADMIN] Trần An Nhiên'),
(3,N'[TEST ADMIN] Lê Minh Quân'),(4,N'[TEST ADMIN] Phạm Ngọc Hân'),
(5,N'[TEST ADMIN] Vũ Bảo An'),(6,N'[TEST ADMIN] Đặng Gia Huy'),
(7,N'[TEST ADMIN] Hồ sơ chờ cấp tài khoản A'),(8,N'[TEST ADMIN] Hồ sơ chờ cấp tài khoản B');
INSERT INTO dbo.students (parent_id, account_id, full_name, date_of_birth, gender, school_name, notes, created_at)
SELECT CASE WHEN n <= 7 THEN @parent ELSE NULL END, CASE WHEN n = 1 THEN @studentAccount ELSE NULL END,
       name, DATEADD(YEAR,-10, @today), CASE WHEN n % 2 = 0 THEN N'Nữ' ELSE N'Nam' END,
       N'Trường mẫu để kiểm thử', N'QA-ADM fixture: hồ sơ test, có thể dùng để cấp tài khoản học viên.', @now
FROM @students;
DECLARE @student BIGINT = (SELECT id FROM dbo.students WHERE account_id = @studentAccount);
DECLARE @secondStudent BIGINT = (SELECT id FROM dbo.students WHERE full_name = N'[TEST ADMIN] Trần An Nhiên');

INSERT INTO dbo.branches (code,name,address,phone,email,active,created_at)
VALUES ('QA-ADM-BR',N'[TEST ADMIN] Cơ sở thực hành',N'Địa chỉ giả lập cho kiểm thử','0908009999','qa_branch@example.test',1,@now);
DECLARE @branch BIGINT = SCOPE_IDENTITY();
INSERT INTO dbo.rooms (branch_id,room_code,room_name,capacity,room_type,status,description)
VALUES (@branch,'QA-ADM-R01',N'[TEST ADMIN] Phòng đàn A',12,'PIANO_GROUP','AVAILABLE',N'Dữ liệu test quản trị');
DECLARE @room BIGINT = SCOPE_IDENTITY();
INSERT INTO dbo.equipments (room_id,name,code,category,status,description)
VALUES (@room,N'[TEST ADMIN] Đàn piano','QA-ADM-E01','UPRIGHT_PIANO','GOOD',N'Dữ liệu test quản trị');
INSERT INTO dbo.programs (code,name,description) VALUES ('QA-ADM',N'[TEST ADMIN] Chương trình thực hành',N'Dữ liệu thử dashboard admin');
DECLARE @program BIGINT = SCOPE_IDENTITY();
INSERT INTO dbo.courses (program_id,code,name,level,duration_weeks,total_sessions,tuition_fee,description)
VALUES (@program,'QA-ADM-COURSE',N'[TEST ADMIN] Đàn cơ bản','BEGINNER',12,24,1800000,N'Dữ liệu kiểm thử, không phải khóa học thật');
DECLARE @course BIGINT = SCOPE_IDENTITY();
INSERT INTO dbo.classes (course_id,branch_id,room_id,teacher_id,class_code,class_name,class_type,max_students,current_students,start_date,end_date,schedule_description,status)
VALUES (@course,@branch,@room,@teacher,'QA-ADM-CLASS',N'[TEST ADMIN] Lớp đàn thực hành','GROUP',12,2,DATEADD(DAY,-20,@today),DATEADD(DAY,70,@today),N'Thứ 2 & Thứ 5 (18:00 - 19:00)','OPEN');
DECLARE @class BIGINT = SCOPE_IDENTITY();
INSERT INTO dbo.enrollments (student_id,class_id,registered_by_user_id,enrollment_date,status,notes)
SELECT s.id,@class,@admin,DATEADD(DAY,-14,@now),CASE WHEN n.n <= 4 THEN 'ENROLLED' ELSE 'PENDING_PAYMENT' END,N'QA-ADM test enrollment'
FROM dbo.students s JOIN @students n ON n.name = s.full_name WHERE n.n IN (1,4,5,6);
DECLARE @enrollment BIGINT = (SELECT id FROM dbo.enrollments WHERE student_id=@student AND class_id=@class);

-- Different states populate operational queues without modifying existing requests.
INSERT INTO dbo.enrollment_requests (student_id,class_id,preferred_course_id,placement_requested,requested_by_user_id,enrollment_id,status,note,created_at)
SELECT s.id,CASE WHEN n.n IN (1,4,5,6) THEN @class ELSE NULL END,@course,CASE WHEN n.n=2 THEN 1 ELSE 0 END,@parent,
       CASE WHEN n.n IN (1,4,5,6) THEN e.id ELSE NULL END,
       CASE n.n WHEN 1 THEN 'APPROVED' WHEN 2 THEN 'WAITING_PLACEMENT' WHEN 3 THEN 'READY_FOR_ASSIGNMENT'
           WHEN 4 THEN 'APPROVED' WHEN 5 THEN 'PENDING_PAYMENT' WHEN 6 THEN 'PENDING_PAYMENT' ELSE 'PENDING' END,
       N'QA-ADM test request',DATEADD(DAY,-n.n,@now)
FROM dbo.students s JOIN @students n ON n.name=s.full_name LEFT JOIN dbo.enrollments e ON e.student_id=s.id AND e.class_id=@class WHERE n.n<=7;

INSERT INTO dbo.lessons (class_id,room_id,teacher_id,session_number,lesson_date,start_time,end_time,title,status)
VALUES (@class,@room,@teacher,1,DATEADD(DAY,1,@today),'18:00','19:00',N'[TEST ADMIN] Buổi học chính','SCHEDULED'),
       (@class,@room,@teacher,2,DATEADD(DAY,3,@today),'18:00','19:00',N'[TEST ADMIN] Buổi học tiếp theo','SCHEDULED');
DECLARE @lesson BIGINT = (SELECT id FROM dbo.lessons WHERE class_id=@class AND session_number=1);
INSERT INTO dbo.absence_requests (student_id,lesson_id,requested_by_user_id,reason,status,created_at)
VALUES (@student,@lesson,@parent,N'[TEST ADMIN] Xin nghỉ để kiểm thử hàng đợi','PENDING',@now),
       (@secondStudent,@lesson,@parent,N'[TEST ADMIN] Đơn đã duyệt để kiểm thử ca bù','APPROVED',@now);
DECLARE @absence BIGINT = SCOPE_IDENTITY();
INSERT INTO dbo.makeup_registrations (absence_request_id,student_id,original_lesson_id,status,note,created_at)
VALUES (@absence,@secondStudent,@lesson,'PENDING',N'QA-ADM: chờ xếp ca bù',@now);
INSERT INTO dbo.placement_schedules (user_id,student_name,title,room_name,branch,subject,test_date,note,status,created_at)
VALUES (@parent,N'[TEST ADMIN] Trần An Nhiên',N'[TEST ADMIN] Kiểm tra đàn đầu vào',N'Phòng đàn A',N'Cơ sở thực hành','DAN',DATEADD(HOUR,9,CONVERT(DATETIME2,DATEADD(DAY,1,@today))),N'Dữ liệu test lịch sắp tới','SCHEDULED',@now),
       (@parent,N'[TEST ADMIN] Lê Minh Quân',N'[TEST ADMIN] Kiểm tra đầu vào bổ sung',N'Phòng đàn A',N'Cơ sở thực hành','DAN',DATEADD(HOUR,14,CONVERT(DATETIME2,DATEADD(DAY,2,@today))),N'Dữ liệu test lịch sắp tới','SCHEDULED',@now);

-- Historical successful payments form a visible chart; pending/failed payments do not increase revenue.
DECLARE @i INT = 1, @invoice BIGINT, @amount DECIMAL(12,2);
WHILE @i <= 10
BEGIN
    SET @amount = 300000 + @i * 100000;
    INSERT INTO dbo.invoices (invoice_code,student_id,enrollment_id,original_amount,discount_type,discount_amount,final_amount,status,due_date,notes,created_at)
    VALUES ('QA-ADM-INV-'+RIGHT('00'+CONVERT(VARCHAR(2),@i),2),@student,@enrollment,@amount,'NONE',0,@amount,
        CASE WHEN @i<=7 THEN 'PAID' ELSE 'UNPAID' END,
        DATEADD(DAY,CASE WHEN @i=8 THEN -5 WHEN @i=9 THEN 7 ELSE 14 END,@today),
        N'[TEST ADMIN] Hóa đơn mô phỏng, không giao dịch thật',DATEADD(DAY,-12+@i,@now));
    SET @invoice=SCOPE_IDENTITY();
    IF @i<=7
        INSERT INTO dbo.payments (invoice_id,payment_code,payment_method,amount,cash_given,change_amount,payment_date,cashier_id,note,status)
        VALUES (@invoice,'QA-ADM-PAY-'+CONVERT(VARCHAR(2),@i),CASE WHEN @i%2=0 THEN 'BANK_TRANSFER' ELSE 'CASH_AT_DESK' END,
            @amount,@amount,0,DATEADD(HOUR,10,CONVERT(DATETIME2,DATEADD(DAY,-7+@i,@today))),@cashier,N'[TEST ADMIN] Giao dịch mô phỏng để kiểm thử thống kê','SUCCESS');
    IF @i=10
        INSERT INTO dbo.payments (invoice_id,payment_code,payment_method,amount,payment_date,cashier_id,note,status)
        VALUES (@invoice,'QA-ADM-PAY-FAILED','BANK_TRANSFER',@amount,@now,@cashier,N'[TEST ADMIN] Giao dịch thất bại, không tính vào thực thu','FAILED');
    SET @i=@i+1;
END;
INSERT INTO dbo.admin_audit_logs (actor_id,actor_username,target_id,target_username,action,details,created_at)
VALUES (@admin,'qa_admin',@admin,'qa_admin','SEED_TEST_DATA',N'admin-manual-test-v1: tạo 17 tài khoản, 8 hồ sơ học viên và các bản ghi nghiệp vụ mô phỏng để tự kiểm thử. Không ghi đè dữ liệu hiện có.',@now);
COMMIT TRANSACTION;
SELECT username, role, status, must_change_password FROM dbo.users WHERE username LIKE 'qa[_]%' ORDER BY username;
