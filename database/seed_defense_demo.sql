-- SQLCMD variable DemoTag (e.g. HD261111). Add-only, atomic and safe to re-run.
-- Password for these synthetic accounts only: Test@123456.
SET NOCOUNT ON; SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
DECLARE @tag VARCHAR(18)='$(DemoTag)', @u VARCHAR(30)=LOWER('$(DemoTag)');
DECLARE @now DATETIME2=CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATETIME2);
DECLARE @today DATE=CAST(@now AS DATE);
IF @tag LIKE '%[^A-Z0-9]%' OR LEN(@tag)<6 THROW 50001,'Invalid demo tag',1;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM courses WHERE code=CONCAT(@tag,'-P1'))
BEGIN
 COMMIT; PRINT 'Demo already exists. Existing progress has been retained.'; RETURN;
END;
DECLARE @hash VARCHAR(255)='$2a$10$31R3fCJj.x3u74yaJ1kvDOOqEvmV1B0LG.Ct1zhtNsx5w5ktmojMy';
INSERT users(username,password,full_name,email,phone,role,status,subject,teaching_subject,created_at,token_version,must_change_password)
SELECT CONCAT(@u,'_',suffix),@hash,CONCAT(N'[',@tag,N'] ',label),CONCAT(@u,'_',suffix,'@example.invalid'),phone,role,'ACTIVE',subject,teaching,@now,0,0
FROM (VALUES
 ('admin',N'Quản trị viên','ADMIN','0909000001',NULL,NULL),
 ('staff',N'Giáo vụ / thu ngân','STAFF','0909000002',NULL,NULL),
 ('leader',N'Quản lý trung tâm','BRANCH_MANAGER','0909000003',NULL,NULL),
 ('teacher',N'Cô Thu Hà - Piano','TEACHER','0909000004','DAN','PIANO'),
 ('teacher2',N'Thầy Minh - Piano học bù','TEACHER','0909000005','DAN','PIANO'),
 ('parent',N'Phụ huynh Nguyễn Lan','PARENT','0909000006',NULL,NULL),
 ('other',N'Phụ huynh Trần Mai','PARENT','0909000007',NULL,NULL),
 ('student',N'Học viên Minh Châu','STUDENT','0909000008',NULL,NULL)
) v(suffix,label,role,phone,subject,teaching);
DECLARE @parent BIGINT=(SELECT id FROM users WHERE username=CONCAT(@u,'_parent')),
 @other BIGINT=(SELECT id FROM users WHERE username=CONCAT(@u,'_other')),
 @staff BIGINT=(SELECT id FROM users WHERE username=CONCAT(@u,'_staff')),
 @teacher BIGINT=(SELECT id FROM users WHERE username=CONCAT(@u,'_teacher')),
 @teacher2 BIGINT=(SELECT id FROM users WHERE username=CONCAT(@u,'_teacher2'));
DECLARE @program BIGINT=(SELECT id FROM programs WHERE code='PIANO');
IF @program IS NULL BEGIN
 INSERT programs(code,name) VALUES('PIANO',N'Piano'); SET @program=SCOPE_IDENTITY();
END;
INSERT courses(program_id,code,name,level,total_sessions,duration_weeks,tuition_fee,description)
VALUES(@program,CONCAT(@tag,'-P1'),CONCAT(N'[',@tag,N'] Piano cơ bản - 10 buổi'),N'Cơ bản',10,10,6000000,N'[DEMO] Khóa chính: 6 triệu, chia tối đa 3 đợt'),
 (@program,CONCAT(@tag,'-QR'),CONCAT(N'[',@tag,N'] Piano thử QR - 4.000đ'),N'Cơ bản',10,10,4000,N'[DEMO] Chỉ tạo QR khi người demo chủ động chọn; không có giao dịch thật trong seed');
DECLARE @course BIGINT=(SELECT id FROM courses WHERE code=CONCAT(@tag,'-P1')),
 @qrCourse BIGINT=(SELECT id FROM courses WHERE code=CONCAT(@tag,'-QR'));
INSERT branches(code,name,address,phone,active,created_at)
VALUES(@tag,CONCAT(N'[',@tag,N'] Cơ sở trình diễn'),N'Địa chỉ giả lập phục vụ bảo vệ đồ án','0909000099',1,@now);
DECLARE @branch BIGINT=SCOPE_IDENTITY();
INSERT rooms(branch_id,room_code,room_name,capacity,room_type,status,description)
VALUES(@branch,'P01',N'Phòng Piano A',6,'PIANO_GROUP','AVAILABLE',N'6 đàn tốt, dùng lớp chính'),
 (@branch,'P02',N'Phòng Piano B',6,'PIANO_GROUP','AVAILABLE',N'6 đàn tốt, dùng lớp học bù'),
 (@branch,'P03',N'Phòng cần bảo trì',6,'PIANO_GROUP','MAINTENANCE',N'[DEMO] Không nhận phân công mới');
DECLARE @r1 BIGINT=(SELECT id FROM rooms WHERE branch_id=@branch AND room_code='P01'),
 @r2 BIGINT=(SELECT id FROM rooms WHERE branch_id=@branch AND room_code='P02');
DECLARE @j INT=1;
WHILE @j<=6 BEGIN
 INSERT equipments(room_id,name,code,category,status,description)
 VALUES(@r1,CONCAT(N'Đàn A',@j),CONCAT(@tag,'-A',@j),'KEYBOARD','GOOD',N'[DEMO] Thiết bị mẫu'),
 (@r2,CONCAT(N'Đàn B',@j),CONCAT(@tag,'-B',@j),'KEYBOARD','GOOD',N'[DEMO] Thiết bị mẫu');
 SET @j+=1;
END;
-- A/Q: future classes for registration; B/C: today's attendance and makeup.
DECLARE @classes TABLE(k VARCHAR(1),id BIGINT);
DECLARE @k INT=1;
WHILE @k<=6 BEGIN
 DECLARE @letter VARCHAR(1)=SUBSTRING('ABCDFQ',@k,1);
 DECLARE @start DATE=CASE WHEN @letter IN('B','C') THEN DATEADD(DAY,-14,@today) ELSE DATEADD(DAY,1,@today) END;
 INSERT classes(course_id,branch_id,room_id,teacher_id,class_code,class_name,class_type,max_students,current_students,start_date,end_date,schedule_description,status)
 VALUES(CASE WHEN @letter='Q' THEN @qrCourse ELSE @course END,@branch,
  CASE WHEN @letter IN('C','D','Q') THEN @r2 WHEN @letter='F' THEN NULL ELSE @r1 END,
  CASE WHEN @letter IN('C','D','Q') THEN @teacher2 WHEN @letter='F' THEN NULL ELSE @teacher END,
  CONCAT(@tag,'-',@letter),CONCAT(N'[',@tag,N'] ',CASE @letter WHEN 'A' THEN N'Piano khai giảng mới' WHEN 'B' THEN N'Piano đang học' WHEN 'C' THEN N'Piano lớp nhận học bù' WHEN 'D' THEN N'Piano 1-1 đã đủ chỗ' WHEN 'F' THEN N'Piano dự kiến - chờ phân công' ELSE N'Piano thử QR 4.000đ' END),
  CASE WHEN @letter='D' THEN 'ONE_ON_ONE' ELSE 'GROUP' END,CASE WHEN @letter='D' THEN 1 ELSE 6 END,
  0,@start,DATEADD(DAY,63,@start),CASE WHEN @letter IN('C','Q') THEN N'09:30 - 11:30' WHEN @letter='D' THEN N'13:00 - 15:00' ELSE N'07:00 - 09:00' END,
  CASE WHEN @letter='F' THEN 'PLANNING' WHEN @letter IN('B','C') THEN 'IN_PROGRESS' ELSE 'OPEN' END);
 DECLARE @cid BIGINT=SCOPE_IDENTITY(); INSERT @classes VALUES(@letter,@cid);
 IF @letter<>'F' BEGIN
  SET @j=1;
  WHILE @j<=10 BEGIN
   INSERT lessons(class_id,room_id,teacher_id,session_number,lesson_date,start_time,end_time,title,status,lesson_note)
   SELECT @cid,room_id,teacher_id,@j,DATEADD(DAY,7*(@j-1),@start),
    CASE WHEN @letter IN('C','Q') THEN '09:30' WHEN @letter='D' THEN '13:00' ELSE '07:00' END,
    CASE WHEN @letter IN('C','Q') THEN '11:30' WHEN @letter='D' THEN '15:00' ELSE '09:00' END,
    CONCAT(N'[',@tag,N'] Buổi ',@j,N' - Piano ',@letter),'SCHEDULED',N'[DEMO] Các buổi quá khứ là dữ liệu mô phỏng quá trình đã học'
   FROM classes WHERE id=@cid;
   SET @j+=1;
  END;
 END;
 SET @k+=1;
END;
DECLARE @a BIGINT=(SELECT id FROM @classes WHERE k='A'),@b BIGINT=(SELECT id FROM @classes WHERE k='B'),
 @c BIGINT=(SELECT id FROM @classes WHERE k='C'),@d BIGINT=(SELECT id FROM @classes WHERE k='D');
DECLARE @people TABLE(n INT,id BIGINT);
SET @j=1;
WHILE @j<=14 BEGIN
 DECLARE @name NVARCHAR(120)=CASE @j
 WHEN 1 THEN N'Minh An - bắt đầu đăng ký' WHEN 2 THEN N'Bảo Ngọc - chờ chấm đầu vào'
 WHEN 3 THEN N'Gia Hân - sẵn sàng xếp lớp QR' WHEN 4 THEN N'Minh Châu - điểm danh và xin nghỉ'
 WHEN 5 THEN N'Bảo Lâm - cảnh báo vắng 30%' WHEN 6 THEN N'Tuấn Kiệt - công nợ đợt 2'
 WHEN 7 THEN N'Mai Chi - hủy đơn đang chờ' WHEN 8 THEN N'Gia Huy - học bù hôm nay'
 WHEN 9 THEN N'Khánh Linh - đơn chờ giáo vụ duyệt' WHEN 10 THEN N'Đức Anh - học viên lớp nhận bù'
 WHEN 11 THEN N'Hoàng Nam - lớp 1-1 đủ chỗ' WHEN 12 THEN N'Ngọc Anh - quá hạn chưa đóng lần đầu'
 WHEN 13 THEN N'Phương Anh - khoản thu nhập nhầm' ELSE N'Thảo Nhi - con phụ huynh khác' END;
 INSERT students(parent_id,account_id,full_name,date_of_birth,gender,school_name,created_at)
 VALUES(CASE WHEN @j=14 THEN @other ELSE @parent END,
 CASE WHEN @j=4 THEN (SELECT id FROM users WHERE username=CONCAT(@u,'_student')) ELSE NULL END,
 CONCAT(N'[',@tag,N'-',RIGHT('0'+CAST(@j AS VARCHAR(2)),2),N'] ',@name),'2017-05-15',CASE WHEN @j IN(1,5,6,8,10,11) THEN N'Nam' ELSE N'Nữ' END,N'[DEMO] Trường năng khiếu',@now);
 DECLARE @sid BIGINT=SCOPE_IDENTITY(); INSERT @people VALUES(@j,@sid);
 IF @j IN(2,3) BEGIN
  INSERT enrollment_requests(student_id,preferred_course_id,placement_requested,requested_by_user_id,status,note,created_at)
  VALUES(@sid,CASE WHEN @j=3 THEN @qrCourse ELSE NULL END,CASE WHEN @j=2 THEN 1 ELSE 0 END,@parent,
   CASE WHEN @j=2 THEN 'WAITING_PLACEMENT' ELSE 'READY_FOR_ASSIGNMENT' END,N'[DEMO] Hồ sơ dự phòng nếu cần rút gọn phần đầu',@now);
  IF @j=2 INSERT placement_schedules(user_id,enrollment_request_id,student_name,title,room_name,branch,subject,test_date,status,created_at)
  VALUES(@parent,SCOPE_IDENTITY(),CONCAT(N'[',@tag,N'] Bảo Ngọc'),CONCAT(N'[',@tag,N'] Test Piano đầu vào'),N'Phòng Piano A',CONCAT(N'[',@tag,N'] Cơ sở trình diễn'),'DAN',DATEADD(MINUTE,-30,@now),'SCHEDULED',@now);
 END;
 IF @j BETWEEN 4 AND 13 BEGIN
  DECLARE @class BIGINT=CASE WHEN @j IN(12,13) THEN @a WHEN @j=11 THEN @d WHEN @j=10 THEN @c ELSE @b END;
  INSERT enrollments(student_id,class_id,registered_by_user_id,enrollment_date,status,notes)
  VALUES(@sid,@class,@staff,DATEADD(DAY,-14,@now),CASE WHEN @j=12 THEN 'PENDING_PAYMENT' ELSE 'ENROLLED' END,N'[DEMO] Ghi danh mẫu phục vụ các chặng sau');
  DECLARE @eid BIGINT=SCOPE_IDENTITY();
  INSERT enrollment_requests(student_id,class_id,preferred_course_id,placement_requested,requested_by_user_id,reviewed_by_user_id,enrollment_id,status,created_at,reviewed_at)
  VALUES(@sid,@class,@course,0,@parent,@staff,@eid,CASE WHEN @j=12 THEN 'PENDING_PAYMENT' ELSE 'APPROVED' END,DATEADD(DAY,-14,@now),DATEADD(DAY,-14,@now));
  INSERT invoices(invoice_code,student_id,enrollment_id,original_amount,discount_type,discount_amount,final_amount,status,due_date,notes,created_at)
  VALUES(CONCAT(@tag,'-INV-',@j),@sid,@eid,6000000,'NONE',0,6000000,
   CASE WHEN @j=12 THEN 'UNPAID' WHEN @j=6 THEN 'PARTIALLY_PAID' ELSE 'PAID' END,DATEADD(DAY,28,@today),N'[DEMO] Tiền và biên lai fixture, không phải khoản thu thật',DATEADD(DAY,-14,@now));
  DECLARE @invoice BIGINT=SCOPE_IDENTITY();
  INSERT tuition_plans(invoice_id,revision,mode,review_required,created_by_id) VALUES(@invoice,1,CASE WHEN @j IN(6,12) THEN 'INSTALLMENTS' ELSE 'FULL' END,0,@staff);
  DECLARE @plan BIGINT=SCOPE_IDENTITY();
  INSERT tuition_installments(plan_id,sequence_number,amount_due,due_date)
  VALUES(@plan,1,CASE WHEN @j IN(6,12) THEN 3000000 ELSE 6000000 END,DATEADD(DAY,-14,@today));
  DECLARE @first BIGINT=SCOPE_IDENTITY();
  IF @j IN(6,12) INSERT tuition_installments(plan_id,sequence_number,amount_due,due_date)
  VALUES(@plan,2,1500000,DATEADD(DAY,-8,@today)),(@plan,3,1500000,DATEADD(DAY,14,@today));
  IF @j<>12 BEGIN
   DECLARE @amount DECIMAL(12,2)=CASE WHEN @j=6 THEN 3000000 ELSE 6000000 END;
   INSERT payments(invoice_id,payment_code,payment_method,amount,cash_given,change_amount,payment_date,cashier_id,status,request_key,balance_after,note)
   VALUES(@invoice,CONCAT(@tag,'-FIXTURE-',@j),'CASH_AT_DESK',@amount,@amount,0,DATEADD(DAY,-14,@now),@staff,'SUCCESS',CONCAT(@tag,'-FIXTURE-',@j),6000000-@amount,N'[DEMO FIXTURE] Không phải tiền thật; mục 13 cố ý nhập nhầm để demo điều chỉnh');
   DECLARE @pay BIGINT=SCOPE_IDENTITY();
   INSERT tuition_allocations(payment_id,installment_id,amount) VALUES(@pay,@first,@amount);
  END;
 END;
 SET @j+=1;
END;
-- Reconcile synthetic class counts with actual enrolled demo rows.
UPDATE cl SET current_students=(SELECT COUNT(*) FROM enrollments e WHERE e.class_id=cl.id AND e.status='ENROLLED')
FROM classes cl JOIN @classes x ON x.id=cl.id;
-- Three recorded sessions: 04 has today's mark left blank for live teacher demo.
INSERT attendances(lesson_id,student_id,status,note,marked_at)
SELECT l.id,p.id,CASE WHEN p.n=5 THEN 'ABSENT' ELSE 'PRESENT' END,N'[DEMO] Lịch sử 2 buổi đã học',@now
FROM @people p JOIN enrollments e ON e.student_id=p.id JOIN lessons l ON l.class_id=e.class_id
WHERE p.n BETWEEN 4 AND 11 AND l.session_number<=CASE WHEN p.n=5 THEN 3 ELSE 2 END;
DECLARE @orig BIGINT=(SELECT id FROM lessons WHERE class_id=@b AND session_number=3),
 @future BIGINT=(SELECT id FROM lessons WHERE class_id=@b AND session_number=4);
INSERT absence_requests(student_id,lesson_id,requested_by_user_id,reason,status,approved_by_teacher_id,review_note,reviewed_at,created_at)
SELECT p.id,CASE WHEN p.n=5 THEN (SELECT id FROM lessons WHERE class_id=@b AND session_number=1) WHEN p.n=8 THEN @orig ELSE @future END,@parent,
 CASE p.n WHEN 5 THEN N'[DEMO] Nghỉ ốm, chờ xếp bù' WHEN 7 THEN N'[DEMO] Phụ huynh có thể hủy đơn này' WHEN 8 THEN N'[DEMO] Đã xếp sang lớp C học bù hôm nay' ELSE N'[DEMO] Nhờ giáo vụ duyệt thay giáo viên' END,
 CASE WHEN p.n IN(5,8) THEN 'APPROVED' ELSE 'PENDING' END,
 CASE WHEN p.n IN(5,8) THEN @teacher ELSE NULL END,N'[DEMO] Hồ sơ mẫu',CASE WHEN p.n IN(5,8) THEN @now ELSE NULL END,DATEADD(HOUR,-p.n,@now)
FROM @people p WHERE p.n IN(5,7,8,9);
INSERT makeup_registrations(absence_request_id,student_id,original_lesson_id,target_lesson_id,status,note,created_at)
SELECT ar.id,p.id,ar.lesson_id,CASE WHEN p.n=8 THEN (SELECT id FROM lessons WHERE class_id=@c AND session_number=3) ELSE NULL END,
 CASE WHEN p.n=8 THEN 'SCHEDULED' ELSE 'PENDING' END,N'[DEMO] Ca bù lần 1',@now
FROM @people p JOIN absence_requests ar ON ar.student_id=p.id WHERE p.n IN(5,8);
INSERT tuition_notices(recipient_id,installment_id,event_key,type,revision,is_read,created_at)
SELECT @parent,ti.id,CONCAT(@parent,'-',ti.id,'-1-OVERDUE'),'OVERDUE',1,0,@now
FROM tuition_installments ti JOIN tuition_plans tp ON tp.id=ti.plan_id JOIN invoices i ON i.id=tp.invoice_id
WHERE i.invoice_code=CONCAT(@tag,'-INV-6') AND ti.sequence_number=2;
INSERT notifications(recipient_user_id,sender_user_id,title,message,type,reference_id,target_url,is_read,created_at)
SELECT CASE WHEN ar.status='PENDING' THEN @teacher ELSE @parent END,@staff,
 CONCAT(N'[',@tag,N'] ',CASE WHEN ar.status='PENDING' THEN N'Có đơn xin nghỉ chờ xử lý' ELSE N'Đơn xin nghỉ đã được duyệt' END),
 N'[DEMO] Bấm thông báo để vào đúng chi tiết đơn.',CASE WHEN ar.status='PENDING' THEN 'ABSENCE_CREATED' ELSE 'ABSENCE_APPROVED' END,
 ar.id,CONCAT('/pages/absence-makeup.html?tab=ABSENCE&absenceId=',ar.id),0,@now
FROM absence_requests ar JOIN @people p ON p.id=ar.student_id;
UPDATE i SET due_date=(SELECT MAX(ti.due_date) FROM tuition_installments ti JOIN tuition_plans tp ON tp.id=ti.plan_id WHERE tp.invoice_id=i.id)
FROM invoices i JOIN @people p ON p.id=i.student_id;
COMMIT;
SELECT @tag AS demo_tag,@today AS demo_date,'Created synthetic demo; existing data retained' AS result;
