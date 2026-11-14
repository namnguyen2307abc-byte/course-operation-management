-- DU LIEU DEMO TONG HOP: Word + attendance/makeup + D26 + Weekly Schedule.
-- Tu chua du lieu nen; khong can file SQL ben ngoai.
-- Chi nap du lieu, KHONG xoa database/schema. Reset du lieu la thao tac rieng.
-- D26 dung lai Bao An va lop PIA-MD-2701-01 cua kich ban Word.
-- Mat khau d26_* va teacher_huong: Test@123456; tai khoan nen con lai: 123456.
-- Chi nap mot lan (COMBINED_DEMO_V1); cac ngay tuong doi tinh tai ngay nap.
USE [course_operation_management];
GO
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lockResult int;
 EXEC @lockResult=sys.sp_getapplock @Resource=N'COMBINED_DEMO_V1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @lockResult<0 THROW 51800,'Cannot acquire combined demo seed lock.',1;
 IF OBJECT_ID(N'dbo.combined_demo_installations',N'U') IS NULL
  CREATE TABLE dbo.combined_demo_installations(seed_key varchar(80) NOT NULL PRIMARY KEY,installed_at datetime2 NOT NULL DEFAULT SYSDATETIME());
 IF NOT EXISTS(SELECT 1 FROM dbo.combined_demo_installations WHERE seed_key='COMBINED_DEMO_V1')
 BEGIN

-- ================= 1. WORD =================
EXEC sys.sp_executesql N'-- SQL Server 2016+. Run this COMPLETE UTF-8 file in SSMS on the local presentation machine.
-- Matches D:/S5/SWP/data_demo.docx: three happy cases, unchanged accounts and input values.
-- Target: the EXISTING course_operation_management database used by the application.
-- Requires the integrated application schema (main 5e6d751 or compatible).
-- No DROP, DELETE, application configuration changes, or real payment transactions.
-- On first installation, ONLY the seven explicitly listed d26 accounts receive Test@123456.
-- Their display names become natural names; unrelated accounts and business records are preserved.
-- Rerunning does NOT reset passwords, requests, classes, or facility edits.


SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
SET LOCK_TIMEOUT 15000;

IF DB_NAME()<>N''course_operation_management''
    THROW 51600,''Wrong database. This file must run in course_operation_management.'',1;
IF OBJECT_ID(N''dbo.notifications'',N''U'') IS NULL
 OR OBJECT_ID(N''dbo.admin_audit_logs'',N''U'') IS NULL
 OR OBJECT_ID(N''dbo.tuition_plans'',N''U'') IS NULL
 OR COL_LENGTH(N''dbo.users'',N''teaching_subject'') IS NULL
 OR COL_LENGTH(N''dbo.users'',N''token_version'') IS NULL
 OR COL_LENGTH(N''dbo.users'',N''must_change_password'') IS NULL
 OR COL_LENGTH(N''dbo.placement_schedules'',N''recommended_course_id'') IS NULL
 OR COL_LENGTH(N''dbo.invoices'',N''reservation_deadline'') IS NULL
    THROW 51601,''Application schema is not current. Use the integrated main schema before importing; do not reset the database.'',1;

BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult INT;
EXEC @LockResult=sys.sp_getapplock @Resource=N''WORD_PRESENTATION_202701_V1'',
 @LockMode=''Exclusive'',@LockOwner=''Transaction'',@LockTimeout=15000;
IF @LockResult<0 THROW 51602,''Another installation is running. Retry later.'',1;

IF OBJECT_ID(N''dbo.word_presentation_installations'',N''U'') IS NULL
    CREATE TABLE dbo.word_presentation_installations (
        batch_key VARCHAR(80) NOT NULL PRIMARY KEY,
        installed_at DATETIME2 NOT NULL,
        first_class_date DATE NOT NULL,
        class_code VARCHAR(50) NOT NULL
    );

IF EXISTS(SELECT 1 FROM dbo.word_presentation_installations WHERE batch_key=''WORD_202701_V1'')
BEGIN
    PRINT N''File đã được cài. Giữ nguyên kết quả đã thao tác, không nạp lại hoặc đặt lại mật khẩu.'';
END
ELSE
BEGIN
    DECLARE @Now DATETIME2=CONVERT(DATETIME2,SYSDATETIMEOFFSET() AT TIME ZONE ''SE Asia Standard Time'');
    IF @Now>=CONVERT(DATETIME2,''2027-01-02T17:00:00'',126)
        THROW 51603,''The start date in the Word document is no longer in the future. Update the demo dates; past classes must not bypass validation.'',1;

    DECLARE @Accounts TABLE(username VARCHAR(100) PRIMARY KEY,full_name NVARCHAR(150),role VARCHAR(30),subject VARCHAR(100),teaching_subject VARCHAR(100));
    INSERT @Accounts VALUES
    (''d26_admin'',N''Trần Quang Hải'',''ADMIN'',NULL,NULL),
    (''d26_staff'',N''Nguyễn Thu Trang'',''STAFF'',NULL,NULL),
    (''d26_parent_new'',N''Nguyễn Thị Lan'',''PARENT'',NULL,NULL),
    (''d26_parent_wait'',N''Trần Thu Hà'',''PARENT'',NULL,NULL),
    (''d26_parent_ready'',N''Lê Minh Tuấn'',''PARENT'',NULL,NULL),
    (''d26_parent_pay'',N''Phạm Thị Hương'',''PARENT'',NULL,NULL),
    (''d26_teacher_piano'',N''Nguyễn Minh Anh'',''TEACHER'',''DAN'',''PIANO'');

    IF EXISTS(SELECT 1 FROM @Accounts a JOIN dbo.users u ON u.username=a.username WHERE u.role<>a.role)
        THROW 51604,''A reserved d26 username belongs to a different role. No existing account was overwritten.'',1;
    IF EXISTS(SELECT 1 FROM dbo.users WHERE username=''d26_teacher_piano''
        AND teaching_subject IS NOT NULL AND teaching_subject<>''PIANO'')
        THROW 51618,''The reserved Piano teacher has another specialism. Existing teaching assignments were preserved.'',1;
    -- BCrypt for Test@123456, verified with the application''s BCryptPasswordEncoder.
    DECLARE @Hash VARCHAR(255)=''$2a$10$U9rS8H8giwtd3tJo7XlXG.tkjsA8opaNfJZqihlijddbmKw2k76Ju'';
    INSERT dbo.users(username,password,full_name,email,role,subject,teaching_subject,status,token_version,must_change_password,created_at)
    SELECT a.username,@Hash,a.full_name,a.username+''@talent.example'',a.role,a.subject,a.teaching_subject,''ACTIVE'',0,0,@Now
    FROM @Accounts a WHERE NOT EXISTS(SELECT 1 FROM dbo.users u WHERE u.username=a.username);
    UPDATE u SET password=@Hash,full_name=a.full_name,status=''ACTIVE'',must_change_password=0,
        token_version=COALESCE(u.token_version,0)+1,
        subject=CASE WHEN a.role=''TEACHER'' THEN a.subject ELSE u.subject END,
        teaching_subject=CASE WHEN a.role=''TEACHER'' THEN a.teaching_subject ELSE u.teaching_subject END
    FROM dbo.users u JOIN @Accounts a ON a.username=u.username;

    DECLARE @Parent BIGINT=(SELECT id FROM dbo.users WHERE username=''d26_parent_new'');
    DECLARE @Teacher BIGINT=(SELECT id FROM dbo.users WHERE username=''d26_teacher_piano'');
    DECLARE @Children TABLE(full_name NVARCHAR(150) PRIMARY KEY,birthday DATE,gender NVARCHAR(20));
    INSERT @Children VALUES
    (N''Nguyễn Bảo An'',''20170514'',N''Nam''),
    (N''Nguyễn An Nhiên'',''20180920'',N''Nữ''),
    (N''Nguyễn Minh Quân'',''20160208'',N''Nam''),
    (N''Nguyễn Ngọc Hân'',''20190316'',N''Nữ'');
    IF EXISTS(SELECT s.full_name FROM dbo.students s JOIN @Children c ON c.full_name=s.full_name
        WHERE s.parent_id=@Parent GROUP BY s.full_name HAVING COUNT(*)>1)
        THROW 51605,''The parent has duplicate student names. Resolve the ambiguity before importing.'',1;
    INSERT dbo.students(parent_id,full_name,date_of_birth,gender,school_name,notes,created_at)
    SELECT @Parent,c.full_name,c.birthday,c.gender,N''Trường Tiểu học Mỹ Đình'',
        N''Gia đình ưu tiên học năng khiếu cuối tuần tại cơ sở Mỹ Đình.'',@Now
    FROM @Children c WHERE NOT EXISTS(SELECT 1 FROM dbo.students s WHERE s.parent_id=@Parent AND s.full_name=c.full_name);
    DECLARE @Child BIGINT=(SELECT id FROM dbo.students WHERE parent_id=@Parent AND full_name=N''Nguyễn Bảo An'');
    IF EXISTS(SELECT 1 FROM dbo.enrollment_requests WHERE student_id=@Child
        AND status IN (''PENDING'',''WAITING_PLACEMENT'',''READY_FOR_ASSIGNMENT'',''PENDING_PAYMENT''))
     OR EXISTS(SELECT 1 FROM dbo.enrollments WHERE student_id=@Child AND status IN (''ENROLLED'',''PENDING_PAYMENT''))
        THROW 51606,''Bao An already has an active request or enrollment. Existing records were preserved; use an unused presentation database.'',1;
    IF EXISTS(SELECT 1 FROM dbo.classes WHERE class_code=''PIA-MD-2701-01'')
        THROW 51607,''PIA-MD-2701-01 already exists. This file does not delete a class you have already created.'',1;

    IF NOT EXISTS(SELECT 1 FROM dbo.programs WHERE code=''PIANO'')
        INSERT dbo.programs(code,name,description) VALUES(''PIANO'',N''Piano'',N''Đào tạo kỹ năng chơi đàn Piano theo trình độ.'');
    IF NOT EXISTS(SELECT 1 FROM dbo.courses WHERE code=''PIA-CB-12'')
        INSERT dbo.courses(program_id,code,name,level,duration_weeks,total_sessions,tuition_fee,description)
        SELECT id,''PIA-CB-12'',N''Piano nền tảng'',''BEGINNER'',6,12,2400000,
            N''Làm quen phím đàn, tư thế, đọc nốt, nhịp cơ bản và phối hợp hai tay; mỗi học viên một đàn.''
        FROM dbo.programs WHERE code=''PIANO'';
    IF EXISTS(SELECT 1 FROM dbo.courses c JOIN dbo.programs p ON p.id=c.program_id WHERE c.code=''PIA-CB-12''
        AND (c.name<>N''Piano nền tảng'' OR p.code<>''PIANO'' OR c.tuition_fee<>2400000))
        THROW 51608,''PIA-CB-12 has different business data. No existing course was overwritten.'',1;
    IF NOT EXISTS(SELECT 1 FROM dbo.branches WHERE code=''MD01'')
        INSERT dbo.branches(code,name,address,email,active,created_at)
        VALUES(''MD01'',N''Cơ sở Mỹ Đình'',N''Khu đô thị Mỹ Đình, Hà Nội'',''mydinh@talent.example'',1,@Now);
    IF EXISTS(SELECT 1 FROM dbo.branches WHERE code=''MD01'' AND (name<>N''Cơ sở Mỹ Đình'' OR COALESCE(active,0)<>1))
        THROW 51609,''MD01 is inactive or has a different name. No existing branch was overwritten.'',1;
    DECLARE @Branch BIGINT=(SELECT id FROM dbo.branches WHERE code=''MD01'');

    DECLARE @Rooms TABLE(code VARCHAR(50) PRIMARY KEY,name NVARCHAR(150),type VARCHAR(50),description NVARCHAR(500));
    INSERT @Rooms VALUES
    (''MD-PIA-201'',N''Phòng Piano 201'',''PIANO_GROUP'',N''Phòng cách âm 8 chỗ với đàn piano điện, mỗi học viên một đàn khi thực hành.''),
    (''MD-GUI-202'',N''Phòng Guitar 202'',''GUITAR_ROOM'',N''Phòng thực hành Guitar 8 chỗ, có đàn mẫu và loa nghe mẫu. Học viên sử dụng đàn cá nhân.'');
    IF EXISTS(SELECT r.room_code FROM dbo.rooms r JOIN @Rooms v ON v.code=r.room_code GROUP BY r.room_code HAVING COUNT(*)>1)
        THROW 51610,''A room code in the Word document is duplicated. Resolve the ambiguity before importing.'',1;
    INSERT dbo.rooms(branch_id,room_code,room_name,capacity,room_type,status,description)
    SELECT @Branch,v.code,v.name,8,v.type,''AVAILABLE'',v.description FROM @Rooms v
    WHERE NOT EXISTS(SELECT 1 FROM dbo.rooms r WHERE r.room_code=v.code);
    IF EXISTS(SELECT 1 FROM dbo.rooms r JOIN @Rooms v ON v.code=r.room_code
        WHERE r.branch_id<>@Branch OR r.room_type<>v.type OR r.status<>''AVAILABLE'' OR r.capacity<8 OR r.room_name<>v.name)
        THROW 51611,''A Word room has different data, is unavailable, or has fewer than eight seats. No existing room was overwritten.'',1;
    DECLARE @PianoRoom BIGINT=(SELECT id FROM dbo.rooms WHERE room_code=''MD-PIA-201'');
    DECLARE @GuitarRoom BIGINT=(SELECT id FROM dbo.rooms WHERE room_code=''MD-GUI-202'');
    DECLARE @Numbers TABLE(n INT PRIMARY KEY);
    INSERT @Numbers VALUES(1),(2),(3),(4),(5),(6),(7),(8);
    DECLARE @Equipment TABLE(code VARCHAR(50) PRIMARY KEY,room_id BIGINT,name NVARCHAR(150),category VARCHAR(50),description NVARCHAR(500));
    INSERT @Equipment
    SELECT ''MD-PIA-201-KB-''+RIGHT(''0''+CONVERT(VARCHAR(2),n),2),@PianoRoom,
        N''Đàn piano điện số ''+RIGHT(''0''+CONVERT(NVARCHAR(2),n),2),''KEYBOARD'',N''Đàn piano điện kèm ghế, phục vụ một học viên trong giờ thực hành.'' FROM @Numbers;
    INSERT @Equipment VALUES
    (''MD-GUI-202-AU-01'',@GuitarRoom,N''Loa nghe mẫu số 01'',''AUDIO'',N''Loa phục vụ nghe hợp âm, luyện tiết tấu và thực hành đệm hát.''),
    (''MD-GUI-202-GT-01'',@GuitarRoom,N''Đàn guitar mẫu số 01'',''GUITAR'',N''Đàn mẫu dành cho giáo viên hướng dẫn.''),
    (''MD-GUI-202-GT-02'',@GuitarRoom,N''Đàn guitar mẫu số 02'',''GUITAR'',N''Đàn dự phòng cho giờ thực hành.'');
    IF EXISTS(SELECT e.code FROM dbo.equipments e JOIN @Equipment v ON v.code=e.code GROUP BY e.code HAVING COUNT(*)>1)
        THROW 51612,''An equipment code is duplicated. Resolve the ambiguity before importing.'',1;
    IF EXISTS(SELECT 1 FROM dbo.equipments e JOIN @Equipment v ON v.code=e.code
        WHERE e.room_id<>v.room_id OR e.category<>v.category OR e.status<>''GOOD'' OR e.name<>v.name)
        THROW 51613,''An equipment item has different data or is not in good condition. Existing equipment was preserved.'',1;
    INSERT dbo.equipments(room_id,code,name,category,status,description)
    SELECT v.room_id,v.code,v.name,v.category,''GOOD'',v.description FROM @Equipment v
    WHERE NOT EXISTS(SELECT 1 FROM dbo.equipments e WHERE e.code=v.code);
    IF EXISTS(SELECT 1 FROM dbo.equipments WHERE serial_number=''AU-202-001'' AND code<>''MD-GUI-202-AU-01'')
        THROW 51614,''The serial number AU-202-001 in the Word case is already used by another device.'',1;

    -- Reserve availability, not the class itself: the presenter must create the class in the UI.
    DECLARE @Dates TABLE(lesson_date DATE PRIMARY KEY);
    INSERT @Dates VALUES(''20270102''),(''20270109''),(''20270116''),(''20270123'');
    IF EXISTS(SELECT 1 FROM dbo.lessons l JOIN dbo.classes c ON c.id=l.class_id JOIN @Dates d ON d.lesson_date=l.lesson_date
        WHERE l.status<>''CANCELLED'' AND COALESCE(l.teacher_id,c.teacher_id)=@Teacher
        AND l.start_time<CONVERT(TIME,''19:30'') AND l.end_time>CONVERT(TIME,''16:30''))
        THROW 51615,''Nguyen Minh Anh has a conflicting lesson or insufficient 30-minute break during a Word session. No existing schedule was moved.'',1;
    IF EXISTS(SELECT 1 FROM dbo.lessons l JOIN dbo.classes c ON c.id=l.class_id JOIN @Dates d ON d.lesson_date=l.lesson_date
        WHERE l.status<>''CANCELLED'' AND COALESCE(l.room_id,c.room_id)=@PianoRoom
        AND l.start_time<CONVERT(TIME,''19:00'') AND l.end_time>CONVERT(TIME,''17:00''))
        THROW 51616,''MD-PIA-201 has a conflicting lesson during a Word session. No existing schedule was moved.'',1;
    IF EXISTS(SELECT 1 FROM dbo.classes c WHERE c.max_students>8 AND (
        (c.room_id=@GuitarRoom AND c.status<>''FINISHED'') OR EXISTS(
            SELECT 1 FROM dbo.lessons l WHERE l.class_id=c.id AND l.status=''SCHEDULED''
            AND COALESCE(l.room_id,c.room_id)=@GuitarRoom
            AND (l.lesson_date>CONVERT(DATE,@Now)
                OR (l.lesson_date=CONVERT(DATE,@Now) AND l.end_time>CONVERT(TIME,@Now))))))
        THROW 51617,''The Guitar room serves a class with more than eight students; the Word edit to capacity eight would be blocked.'',1;

    INSERT dbo.word_presentation_installations VALUES(''WORD_202701_V1'',@Now,''20270102'',''PIA-MD-2701-01'');
    PRINT N''Đã chuẩn bị dữ liệu khớp ba happy case trong data_demo.docx.'';
END;
COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

SELECT DB_NAME() AS database_name,batch_key,installed_at,first_class_date,class_code
FROM dbo.word_presentation_installations WHERE batch_key=''WORD_202701_V1'';
SELECT username,full_name,role,status,N''Test@123456 (mật khẩu lúc cài lần đầu)'' AS initial_password
FROM dbo.users WHERE username IN (''d26_admin'',''d26_staff'',''d26_parent_new'',''d26_parent_wait'',''d26_parent_ready'',''d26_parent_pay'',''d26_teacher_piano'')
ORDER BY role,username;
SELECT N''Tạo lớp'' AS scenario,
    CASE WHEN EXISTS(SELECT 1 FROM dbo.classes WHERE class_code=''PIA-MD-2701-01'')
        THEN N''Đã có lớp: case tạo lớp đã được thực hiện, không tự xóa để chạy lại.''
        ELSE N''Chưa có lớp: tạo trên giao diện theo Word, không đổi mã hoặc lịch.'' END AS readiness
UNION ALL
SELECT N''Gửi đơn cho Bảo An'',CASE WHEN EXISTS(
    SELECT 1 FROM dbo.enrollment_requests q JOIN dbo.students s ON s.id=q.student_id JOIN dbo.users p ON p.id=s.parent_id
    WHERE p.username=''d26_parent_new'' AND s.full_name=N''Nguyễn Bảo An''
    AND q.status IN (''PENDING'',''WAITING_PLACEMENT'',''READY_FOR_ASSIGNMENT'',''PENDING_PAYMENT'')) OR EXISTS(
    SELECT 1 FROM dbo.enrollments e JOIN dbo.students s ON s.id=e.student_id JOIN dbo.users p ON p.id=s.parent_id
    WHERE p.username=''d26_parent_new'' AND s.full_name=N''Nguyễn Bảo An'' AND e.status IN (''ENROLLED'',''PENDING_PAYMENT''))
    THEN N''Đã có đơn đang xử lý: không tự đặt lại kết quả đã demo.''
    ELSE N''Chưa có đơn đang xử lý: đăng nhập d26_parent_new và thực hiện theo Word.'' END;
';

-- ================= 2. DU LIEU NEN =================
EXEC sys.sp_executesql N'SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
DECLARE @U TABLE(username varchar(100),full_name nvarchar(150),role varchar(30),subject varchar(50),teaching varchar(50));
INSERT @U VALUES
(''admin'',N''Quản Trị Viên Hệ Thống'',''ADMIN'',NULL,NULL),
(''staff_minh'',N''Trần Quang Minh - Giáo vụ'',''STAFF'',NULL,NULL),
(''cashier_mai'',N''Nguyễn Thanh Mai - Thu ngân'',''STAFF'',NULL,NULL),
(''teacher_huong'',N''Cô Vũ Thu Hương (GV Piano)'',''TEACHER'',''DAN'',''PIANO''),
(''teacher_ha'',N''Cô Trần Thu Hà (GV Piano)'',''TEACHER'',''DAN'',''PIANO''),
(''teacher_tuan'',N''Thầy Trần Anh Tuấn (GV Guitar)'',''TEACHER'',''DAN'',''GUITAR''),
(''teacher_dung'',N''Thầy Nguyễn Văn Dũng (GV Guitar)'',''TEACHER'',''DAN'',''GUITAR''),
(''teacher_linh'',N''Cô Phạm Khánh Linh (GV Múa)'',''TEACHER'',''MUA'',''DANCE''),
(''teacher_long'',N''Thầy Hoàng Phi Long (GV Võ)'',''TEACHER'',''VO'',''MARTIAL_ARTS''),
(''parent_lan'',N''Phụ Huynh Lê Thị Lan'',''PARENT'',NULL,NULL),
(''parent_hoang'',N''Phụ Huynh Đỗ Minh Hoàng'',''PARENT'',NULL,NULL),
(''parent_dung'',N''Phụ Huynh Vũ Tiến Dũng'',''PARENT'',NULL,NULL);
IF EXISTS(SELECT 1 FROM @U x JOIN users u ON u.username=x.username WHERE u.role<>x.role) THROW 51901,''Existing username has incompatible role.'',1;
INSERT users(username,password,full_name,email,role,status,subject,teaching_subject,created_at,token_version,must_change_password)
SELECT x.username,''$2a$10$Os4DiltWhuUOg06C.uqvOujXvqJ4.UD5LQbNM4.yBXHB4hHkv/zii'',x.full_name,x.username+''@talentacademy.edu.vn'',x.role,''ACTIVE'',x.subject,x.teaching,GETDATE(),0,0
FROM @U x WHERE NOT EXISTS(SELECT 1 FROM users u WHERE u.username=x.username);
DECLARE @S TABLE(parent varchar(100),name nvarchar(150),birthday date);
INSERT @S VALUES (''parent_lan'',N''Nguyễn Bảo Nam (Bé Bin)'',''2016-05-12''),(''parent_lan'',N''Nguyễn Trọng Anh'',''2015-11-20''),
(''parent_lan'',N''Nguyễn Hải Yến'',''2017-02-14''),(''parent_hoang'',N''Đỗ Ngọc Hân (Bé Nhím)'',''2019-11-05''),(''parent_dung'',N''Vũ Tuấn Kiệt (Bé Ken)'',''2016-07-22'');
INSERT students(parent_id,full_name,date_of_birth,school_name,notes,created_at)
SELECT u.id,x.name,x.birthday,N''Trường demo'',N''[MAKEUP TEST] Dữ liệu gốc cho test_attendence_makeup.sql'',GETDATE()
FROM @S x JOIN users u ON u.username=x.parent WHERE NOT EXISTS(SELECT 1 FROM students s WHERE s.full_name=x.name AND s.parent_id=u.id);
INSERT branches(code,name,address,active,created_at)
SELECT x.code,x.name,N''Địa chỉ mẫu phục vụ kiểm thử'',1,GETDATE() FROM (VALUES(''CS01'',N''Cơ Sở 1 - Cầu Giấy''),(''CS02'',N''Cơ Sở 2 - Đống Đa'')) x(code,name)
WHERE NOT EXISTS(SELECT 1 FROM branches b WHERE b.code=x.code);
DECLARE @R TABLE(branch varchar(30),code varchar(30),name nvarchar(150),capacity int,type varchar(40));
INSERT @R VALUES(''CS01'',''P101'',N''Phòng Piano Biểu Diễn 1'',2,''PIANO_INDIVIDUAL''),(''CS01'',''P102'',N''Phòng Piano Nhóm 1'',8,''PIANO_GROUP''),
(''CS01'',''P103'',N''Phòng Tập Múa & Ballet 1'',15,''DANCE_ROOM''),(''CS02'',''P201'',N''Phòng Guitar & Cảm Âm'',10,''GUITAR_ROOM''),
(''CS02'',''P202'',N''Phòng Piano Thực Hành 2'',6,''PIANO_GROUP''),(''CS02'',''P203'',N''Võ Đường & Thể Lực'',20,''THEORY_ROOM'');
INSERT rooms(branch_id,room_code,room_name,capacity,room_type,status,description)
SELECT b.id,x.code,x.name,x.capacity,x.type,''AVAILABLE'',N''[MAKEUP TEST] Phòng kiểm thử'' FROM @R x JOIN branches b ON b.code=x.branch
WHERE NOT EXISTS(SELECT 1 FROM rooms r WHERE r.room_code=x.code);
INSERT programs(code,name)
SELECT x.code,x.name FROM (VALUES(''PIANO'',N''Bộ môn Piano''),(''GUITAR'',N''Bộ môn Guitar''),(''DANCE'',N''Bộ môn Múa''),(''MARTIAL_ARTS'',N''Bộ môn Võ'')) x(code,name)
WHERE NOT EXISTS(SELECT 1 FROM programs p WHERE p.code=x.code);
DECLARE @C TABLE(program varchar(30),code varchar(30),name nvarchar(150),level nvarchar(50),weeks int,sessions int,fee decimal(12,2));
INSERT @C VALUES(''PIANO'',''PIA-G1'',N''Piano Sơ Cấp (Grade 1)'',N''Grade 1'',16,32,4800000),
(''GUITAR'',''GUI-BASIC'',N''Guitar Đệm Hát Cơ Bản'',N''Cơ bản'',12,24,3200000),
(''DANCE'',''DAN-KIDS'',N''Múa Thiếu Nhi & Ballet Căn Bản'',N''Căn bản'',16,32,4200000),
(''MARTIAL_ARTS'',''MA-BASIC'',N''Võ Thuật Thiếu Nhi - Đai Trắng Nhập Môn'',N''Khởi động'',16,32,3800000);
INSERT courses(program_id,code,name,level,duration_weeks,total_sessions,tuition_fee,description)
SELECT p.id,x.code,x.name,x.level,x.weeks,x.sessions,x.fee,N''[MAKEUP TEST] Khóa học kiểm thử'' FROM @C x JOIN programs p ON p.code=x.program
WHERE NOT EXISTS(SELECT 1 FROM courses c WHERE c.code=x.code);
DECLARE @CL TABLE(code varchar(50),course varchar(30),branch varchar(30),room varchar(30),teacher varchar(100),name nvarchar(150),type varchar(30),capacity int);
INSERT @CL VALUES
(''CL-PIA-01'',''PIA-G1'',''CS01'',''P101'',''teacher_huong'',N''Piano 1-1 Bé Bảo Nam'',''ONE_ON_ONE'',1),
(''CL-PIA-G1-G01'',''PIA-G1'',''CS01'',''P102'',''teacher_ha'',N''Piano Grade 1 - Nhóm Chiều Tối'',''GROUP'',6),
(''CL-GUI-BASIC-01'',''GUI-BASIC'',''CS02'',''P201'',''teacher_tuan'',N''Guitar Đệm Hát Sáng T7-CN'',''GROUP'',8),
(''CL-DAN-KIDS-01'',''DAN-KIDS'',''CS01'',''P103'',''teacher_linh'',N''Lớp Múa Thiếu Nhi & Ballet Bé Ngọc Hân'',''GROUP'',12),
(''CL-MA-BASIC-01'',''MA-BASIC'',''CS02'',''P203'',''teacher_long'',N''Lớp Võ Thuật Nhập Môn Bé Tuấn Kiệt'',''GROUP'',15),
(''CL-GUI-BASIC-02'',''GUI-BASIC'',''CS02'',''P201'',''teacher_dung'',N''Guitar Nhập Môn Chiều Thứ 7'',''GROUP'',8),
(''CL-PIA-G1-G02'',''PIA-G1'',''CS02'',''P202'',''teacher_ha'',N''Piano Grade 1 - Cơ Sở Đống Đa'',''GROUP'',6);
INSERT classes(course_id,branch_id,room_id,teacher_id,class_code,class_name,class_type,max_students,current_students,start_date,end_date,schedule_description,status)
SELECT c.id,b.id,r.id,u.id,x.code,x.name,x.type,x.capacity,0,DATEADD(day,-30,CONVERT(date,GETDATE())),DATEADD(day,100,CONVERT(date,GETDATE())),N''[MAKEUP TEST] Lịch tương đối trong file test'',''OPEN''
FROM @CL x JOIN courses c ON c.code=x.course JOIN branches b ON b.code=x.branch JOIN rooms r ON r.room_code=x.room JOIN users u ON u.username=x.teacher
WHERE NOT EXISTS(SELECT 1 FROM classes cl WHERE cl.class_code=x.code);
DECLARE @E TABLE(student nvarchar(150),class varchar(50));
INSERT @E VALUES(N''Nguyễn Bảo Nam (Bé Bin)'',''CL-PIA-01''),(N''Nguyễn Trọng Anh'',''CL-GUI-BASIC-01''),(N''Nguyễn Hải Yến'',''CL-PIA-G1-G01''),
(N''Đỗ Ngọc Hân (Bé Nhím)'',''CL-DAN-KIDS-01''),(N''Vũ Tuấn Kiệt (Bé Ken)'',''CL-MA-BASIC-01'');
INSERT enrollments(student_id,class_id,registered_by_user_id,enrollment_date,status,notes)
SELECT s.id,c.id,s.parent_id,GETDATE(),''ENROLLED'',N''[MAKEUP TEST] Ghi danh mẫu phục vụ xin nghỉ và điểm danh''
FROM @E x JOIN students s ON s.full_name=x.student JOIN classes c ON c.class_code=x.class
WHERE NOT EXISTS(SELECT 1 FROM enrollments e WHERE e.student_id=s.id AND e.class_id=c.id);
UPDATE c SET current_students=(SELECT COUNT(*) FROM enrollments e WHERE e.class_id=c.id AND e.status=''ENROLLED'')
FROM classes c JOIN @CL x ON x.code=c.class_code;
';

-- ================= 3. ATTENDANCE MAKEUP =================
EXEC sys.sp_executesql N'-- =============================================================================
-- DỮ LIỆU KIỂM THỬ TOÀN DIỆN: XIN NGHỈ - HỌC BÙ - ĐIỂM DANH - LỊCH TUẦN
-- Hệ quản trị: Microsoft SQL Server (database: course_operation_management)
-- Yêu cầu tiên quyết: Đã chạy database/init_database.sql
-- Đặc tính: Làm mới cửa sổ lịch từ -6 đến +6 ngày quanh GETDATE(), chạy lại an toàn.
--
-- TÀI KHOẢN ĐĂNG NHẬP (mật khẩu mặc định: 123456):
--   - Admin   : admin
--   - Staff   : staff_minh / cashier_mai
--   - Teacher : teacher_huong (Piano), teacher_ha (Piano),
--               teacher_tuan (Guitar), teacher_dung (Guitar),
--               teacher_linh (Múa), teacher_long (Võ)
--   - Parent  : parent_lan (Bảo Nam, Trọng Anh, Hải Yến),
--               parent_hoang (Ngọc Hân), parent_dung (Vũ Tuấn Kiệt)
-- =============================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @Today DATE = CAST(GETDATE() AS DATE);
DECLARE @D_M6  DATE = DATEADD(day, -6, @Today);
DECLARE @D_M5  DATE = DATEADD(day, -5, @Today);
DECLARE @D_M4  DATE = DATEADD(day, -4, @Today);
DECLARE @D_M3  DATE = DATEADD(day, -3, @Today);
DECLARE @D_M2  DATE = DATEADD(day, -2, @Today);
DECLARE @D_M1  DATE = DATEADD(day, -1, @Today);
DECLARE @D_0   DATE = @Today;
DECLARE @D_P1  DATE = DATEADD(day,  1, @Today);
DECLARE @D_P2  DATE = DATEADD(day,  2, @Today);
DECLARE @D_P3  DATE = DATEADD(day,  3, @Today);
DECLARE @D_P4  DATE = DATEADD(day,  4, @Today);
DECLARE @D_P5  DATE = DATEADD(day,  5, @Today);
DECLARE @D_P6  DATE = DATEADD(day,  6, @Today);

-- =============================================================================
-- 0. TRA CỨU ID ĐỘNG
-- =============================================================================
DECLARE @TeacherHuongId BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''teacher_huong'');
DECLARE @TeacherHaId    BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''teacher_ha'');
DECLARE @TeacherTuanId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''teacher_tuan'');
DECLARE @TeacherDungId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''teacher_dung'');
DECLARE @TeacherLinhId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''teacher_linh'');
DECLARE @TeacherLongId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''teacher_long'');

DECLARE @ParentLanId    BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''parent_lan'');
DECLARE @ParentHoangId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''parent_hoang'');
DECLARE @ParentDungId   BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''parent_dung'');

DECLARE @StuNamId       BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N''%Nguyễn Bảo Nam%'');
DECLARE @StuAnhId       BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N''%Nguyễn Trọng Anh%'');
DECLARE @StuYenId       BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N''%Nguyễn Hải Yến%'');
DECLARE @StuHanId       BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N''%Đỗ Ngọc Hân%'');
DECLARE @StuKietId      BIGINT = (SELECT TOP 1 id FROM students WHERE full_name LIKE N''%Vũ Tuấn Kiệt%'');

IF @StuNamId IS NULL OR @StuAnhId IS NULL OR @StuYenId IS NULL OR @StuHanId IS NULL OR @StuKietId IS NULL
    THROW 50001, N''Thiếu dữ liệu học viên gốc. Vui lòng chạy database/init_database.sql trước.'', 1;

DECLARE @Class1Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''CL-PIA-01'');
DECLARE @Class3Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''CL-PIA-G1-G01'');
DECLARE @Class4Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''CL-GUI-BASIC-01'');
DECLARE @Class5Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''CL-DAN-KIDS-01'');
DECLARE @Class6Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''CL-MA-BASIC-01'');
DECLARE @Class7Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''CL-GUI-BASIC-02'');
DECLARE @Class8Id       BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''CL-PIA-G1-G02'');

DECLARE @RoomP101       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = ''P101'');
DECLARE @RoomP102       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = ''P102'');
DECLARE @RoomP103       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = ''P103'');
DECLARE @RoomP201       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = ''P201'');
DECLARE @RoomP202       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = ''P202'');
DECLARE @RoomP203       BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = ''P203'');

DECLARE @CoursePiaG1Id  BIGINT = (SELECT TOP 1 id FROM courses WHERE code = ''PIA-G1'');
DECLARE @BranchCs2Id    BIGINT = (SELECT TOP 1 id FROM branches WHERE code = ''CS02'');

-- =============================================================================
-- 1. CHUẨN HÓA TAG CŨ & DỌN CỬA SỔ LỊCH HIỆN TẠI (-6 ĐẾN +6 NGÀY)
-- =============================================================================
UPDATE makeup_registrations
SET note = REPLACE(note, N''[RETRY:1][LOCKED:0] '', N''[Lần bù: 1/2] '')
WHERE note LIKE N''%[RETRY:1][LOCKED:0]%'';

-- Lớp Piano Grade 1 đã đủ sĩ số (1/1) dùng kiểm tra bộ lọc lớp đầy chỗ
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = ''CL-PIA-G1-FULL'')
BEGIN
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaG1Id, @BranchCs2Id, @RoomP202, @TeacherHaId, ''CL-PIA-G1-FULL'', N''Piano Grade 1 - Nhóm Sáng Đống Đa'', ''GROUP'', 1, 1, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N''Thứ 5 (14:00 - 15:30)'', ''OPEN'');
END;
DECLARE @ClassFullId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''CL-PIA-G1-FULL'');

DELETE a
FROM attendances a
JOIN lessons l ON a.lesson_id = l.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
  AND l.class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId);

DELETE m
FROM makeup_registrations m
LEFT JOIN lessons lo ON m.original_lesson_id = lo.id
LEFT JOIN lessons lt ON m.target_lesson_id = lt.id
WHERE (lo.lesson_date BETWEEN @D_M6 AND @D_P6 AND lo.class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId))
   OR (lt.lesson_date BETWEEN @D_M6 AND @D_P6 AND lt.class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId));

DELETE ar
FROM absence_requests ar
JOIN lessons l ON ar.lesson_id = l.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
  AND l.class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId);

DELETE FROM lessons
WHERE lesson_date BETWEEN @D_M6 AND @D_P6
  AND class_id IN (@Class1Id, @Class3Id, @Class4Id, @Class5Id, @Class6Id, @Class7Id, @Class8Id, @ClassFullId);

-- =============================================================================
-- 2. TẠO BUỔI HỌC PHỤC VỤ TỪNG TÌNH HUỐNG NGHIỆP VỤ
-- =============================================================================

-- --- A. PIANO GRADE 1 (Bảo Nam 1-1, Hải Yến nhóm tối, Lớp bù Đống Đa, Lớp đầy 1/1) ---
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 8, @D_M3, ''18:00'', ''19:00'', N''Buổi 8: Luyện ngón Hanon & Gam Đô trưởng'', N''Thực hành ghép hai tay chậm'', ''SCHEDULED'');
DECLARE @L_C1_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 9, @D_M2, ''18:00'', ''19:00'', N''Buổi 9: Kỹ thuật Legato & Staccato cơ bản'', N''Ôn bài tập trang 18 giáo trình Alfred'', ''SCHEDULED'');
DECLARE @L_C1_S9 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 10, @D_0, ''23:50'', ''23:59'', N''Buổi 10: Ôn tập tiết tấu 4/4 & Thị tấu'', N''Buổi học cuối ngày để kiểm tra khóa giờ điểm danh'', ''SCHEDULED'');
DECLARE @L_C1_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 11, @D_P1, ''18:00'', ''19:00'', N''Buổi 11: Hợp âm ba & Đọc bản nhạc mới'', N''Học viên duy nhất đã chuyển sang lớp bù'', ''SCHEDULED'');
DECLARE @L_C1_S11 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 12, @D_P2, ''18:00'', ''19:00'', N''Buổi 12: Luyện chuyển hợp âm trưởng - thứ'', N''Đang có đơn xin nghỉ chờ duyệt'', ''SCHEDULED'');
DECLARE @L_C1_S12 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class1Id, @RoomP101, @TeacherHuongId, 13, @D_P5, ''18:00'', ''19:00'', N''Buổi 13: Tổng hợp tác phẩm Grade 1'', N''Buổi học sắp tới'', ''SCHEDULED'');
DECLARE @L_C1_S13 BIGINT = SCOPE_IDENTITY();

-- Class 3 (Hải Yến)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class3Id, @RoomP102, @TeacherHaId, 8, @D_M2, ''18:00'', ''19:30'', N''Buổi 8: Luyện ngón Hanon & Gam Đô trưởng'', N''Thực hành ghép hai tay theo nhịp'', ''COMPLETED'');
DECLARE @L_C3_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class3Id, @RoomP102, @TeacherHaId, 10, @D_M2, ''19:30'', ''21:00'', N''Buổi 10: Ôn tập tiết tấu 4/4 & Thị tấu'', N''Luyện phản xạ đọc nốt khóa Sol và Pha'', ''SCHEDULED'');
DECLARE @L_C3_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class3Id, @RoomP102, @TeacherHaId, 11, @D_P3, ''19:30'', ''21:00'', N''Buổi 11: Hợp âm ba & Đọc bản nhạc mới'', N''Cùng bài #11 với lớp 1-1'', ''SCHEDULED'');
DECLARE @L_C3_S11 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class3Id, @RoomP102, @TeacherHaId, 12, @D_P5, ''17:30'', ''19:00'', N''Buổi 12: Diễn tấu tiểu phẩm cổ điển'', N''Buổi chính thức của lớp Nhóm Tối'', ''SCHEDULED'');
DECLARE @L_C3_S12 BIGINT = SCOPE_IDENTITY();

-- Class 8 (Lớp nhận bù Đống Đa)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 10, @D_0, ''00:05'', ''23:55'', N''Buổi 10: Ôn tập tiết tấu 4/4 (Đống Đa)'', N''Ca học bù hôm nay tại cơ sở Đống Đa'', ''SCHEDULED'');
DECLARE @L_C8_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 8, @D_P3, ''09:30'', ''11:00'', N''Buổi 8: Luyện ngón Hanon (Đống Đa)'', N''Sẵn sàng nhận học viên bù Buổi 8'', ''SCHEDULED'');
DECLARE @L_C8_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 11, @D_P4, ''09:30'', ''11:00'', N''Buổi 11: Hợp âm ba & Đọc bản nhạc (Đống Đa)'', N''Ca học sáng phù hợp ghép bù đúng bài #11'', ''SCHEDULED'');
DECLARE @L_C8_S11_OK BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 11, @D_P5, ''17:30'', ''19:00'', N''Buổi 11: Hợp âm ba (Ca chiều Đống Đa)'', N''Trùng khung giờ 17:30-19:00 với lớp C3'', ''SCHEDULED'');
DECLARE @L_C8_S11_CONFLICT BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class8Id, @RoomP202, @TeacherHaId, 12, @D_P6, ''09:30'', ''11:00'', N''Buổi 12: Diễn tấu tiểu phẩm cổ điển (Đống Đa)'', N''Lớp cùng cấp độ Grade 1 còn chỗ'', ''SCHEDULED'');
DECLARE @L_C8_S12 BIGINT = SCOPE_IDENTITY();

-- ClassFull (Lớp 1/1 đã kín chỗ)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassFullId, @RoomP202, @TeacherHaId, 11, @D_P4, ''14:00'', ''15:30'', N''Buổi 11: Hợp âm ba (Nhóm 1/1)'', N''Lớp đã đủ sĩ số tối đa'', ''SCHEDULED'');
DECLARE @L_CFULL_S11 BIGINT = SCOPE_IDENTITY();

-- --- B. GUITAR CƠ BẢN (Trọng Anh Class 4 & Lớp bù Class 7) ---
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 5, @D_M6, ''09:00'', ''10:30'', N''Buổi 5: Tập bấm hợp âm C - Am - F - G'', N''Luyện chuyển thế bấm sạch dây'', ''COMPLETED'');
DECLARE @L_C4_S5 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 6, @D_M5, ''09:00'', ''10:30'', N''Buổi 6: Điệu Ballad & Tỉa dây p-i-m-a'', N''Giữ nhịp đều với metronome 70 BPM'', ''COMPLETED'');
DECLARE @L_C4_S6 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 7, @D_M4, ''09:00'', ''10:30'', N''Buổi 7: Quạt chả điệu Slow Rock'', N''Thực hành đệm ca khúc mẫu'', ''COMPLETED'');
DECLARE @L_C4_S7 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 8, @D_M3, ''09:00'', ''10:30'', N''Buổi 8: Đệm hát nhịp 3/4 điệu Valse'', N''Nhấn mạnh phách 1 và dậm nhịp chân'', ''COMPLETED'');
DECLARE @L_C4_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 9, @D_M1, ''09:00'', ''10:30'', N''Buổi 9: Kỹ thuật chặn phím Barre sơ cấp'', N''Làm quen hợp âm Fm và Bm'', ''SCHEDULED'');
DECLARE @L_C4_S9 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 10, @D_0, ''16:00'', ''17:30'', N''Buổi 10: Quạt chả điệu Slow Surf'', N''Thực hành ghép câu hát với hợp âm'', ''SCHEDULED'');
DECLARE @L_C4_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class4Id, @RoomP201, @TeacherTuanId, 11, @D_0, ''00:05'', ''23:55'', N''Buổi 11: Tổng duyệt ghép nhịp bài hát'', N''Kiểm tra cuối tuần theo nhóm'', ''SCHEDULED'');
DECLARE @L_C4_S11 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class7Id, @RoomP201, @TeacherDungId, 8, @D_M1, ''18:00'', ''19:30'', N''Buổi 8: Đệm hát nhịp 3/4 điệu Valse (Chiều T7)'', N''Ôn luyện điệu Valse theo nhóm nhỏ'', ''COMPLETED'');
DECLARE @L_C7_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class7Id, @RoomP201, @TeacherDungId, 10, @D_0, ''00:05'', ''23:55'', N''Buổi 10: Quạt chả điệu Slow Surf (Chiều T7)'', N''Ca học chiều nay nhận học viên học bù'', ''SCHEDULED'');
DECLARE @L_C7_S10 BIGINT = SCOPE_IDENTITY();

-- --- C. MÚA BALLET CĂN BẢN (Ngọc Hân - Không có lớp song song cùng trình độ) ---
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class5Id, @RoomP103, @TeacherLinhId, 7, @D_M2, ''17:30'', ''19:00'', N''Buổi 7: Tổ hợp múa Ballet căn bản & Thăng bằng'', N''Rèn tư thế lưng thẳng và mũi chân'', ''SCHEDULED'');
DECLARE @L_C5_S7 BIGINT = SCOPE_IDENTITY();

-- --- D. VÕ THUẬT NHẬP MÔN (Vũ Tuấn Kiệt Class 6) ---
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 4, @D_M6, ''17:30'', ''19:00'', N''Buổi 4: Tấn pháp cơ bản & Khởi động khớp'', N''Luyện trung bình tấn 3 phút'', ''COMPLETED'');
DECLARE @L_C6_S4 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 5, @D_M5, ''17:30'', ''19:00'', N''Buổi 5: Đòn đấm thẳng & Kỹ thuật gạt đỡ'', N''Thực hành theo cặp với bao đích'', ''COMPLETED'');
DECLARE @L_C6_S5 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 6, @D_M4, ''17:30'', ''19:00'', N''Buổi 6: Đòn đá tống trước Ap Chagi'', N''Giữ thăng bằng trụ và thu chân chuẩn'', ''COMPLETED'');
DECLARE @L_C6_S6 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 7, @D_M3, ''17:30'', ''19:00'', N''Buổi 7: Phản xạ tự vệ cơ bản'', N''Luyện tình huống thoát hiểm tầm gần'', ''SCHEDULED'');
DECLARE @L_C6_S7 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 8, @D_M1, ''17:30'', ''19:00'', N''Buổi 8: Phối hợp đòn tay chân liên hoàn'', N''Ôn tập trước khi vào bài quyền số 1'', ''COMPLETED'');
DECLARE @L_C6_S8 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 9, @D_M3, ''19:15'', ''20:30'', N''Buổi 9: Luyện thể lực & Dẻo dai'', N''Chạy bền và ép dẻo cuối giờ'', ''SCHEDULED'');
DECLARE @L_C6_S9 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 10, @D_M2, ''17:30'', ''19:00'', N''Buổi 10: Bài quyền nhập môn số 1 (Phần 1)'', N''Học 10 động tác mở đầu bài quyền'', ''SCHEDULED'');
DECLARE @L_C6_S10 BIGINT = SCOPE_IDENTITY();

INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@Class6Id, @RoomP203, @TeacherLongId, 11, @D_0, ''00:05'', ''23:55'', N''Buổi 11: Bài quyền nhập môn số 1 (Hoàn thiện)'', N''Tổng hợp toàn bài quyền nhập môn'', ''SCHEDULED'');
DECLARE @L_C6_S11 BIGINT = SCOPE_IDENTITY();

-- =============================================================================
-- 3. INSERT ĐƠN XIN NGHỈ & CA HỌC BÙ ĐẦY ĐỦ TRẠNG THÁI
-- =============================================================================

-- Case 1: Đơn PENDING (Bảo Nam - Buổi 12)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, created_at)
VALUES (@StuNamId, @L_C1_S12, @ParentLanId, N''Bé Bảo Nam có lịch khám răng định kỳ cùng gia đình, xin phép cô Hương nghỉ buổi 12.'', ''PENDING'', DATEADD(minute, -15, GETDATE()));

-- Case 2: Đơn REJECTED (Tuấn Kiệt - Buổi 9)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuKietId, @L_C6_S9, @ParentDungId, N''Bé về quê trễ nên xin nghỉ phép bổ sung sau buổi học.'', ''REJECTED'', @TeacherLongId, N''Từ chối do phụ huynh báo sau khi buổi học đã kết thúc không có lý do đột xuất.'', DATEADD(day, -2, GETDATE()), DATEADD(day, -2, GETDATE()));

-- Case 3: Đơn APPROVED -> Ca bù PENDING (Hải Yến - Buổi 11)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuYenId, @L_C3_S11, @ParentLanId, N''Bé Hải Yến về quê giỗ ông cùng gia đình, xin nghỉ Buổi 11 lớp Piano tối.'', ''APPROVED'', @TeacherHaId, N''Cô Hà đồng ý cho bé nghỉ, chuyển Giáo vụ xếp lớp bù phù hợp.'', DATEADD(hour, -2, GETDATE()), DATEADD(hour, -4, GETDATE()));
DECLARE @ArYenPending BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArYenPending, @StuYenId, @L_C3_S11, NULL, ''PENDING'', N''Đơn đã phê duyệt, đang chờ Giáo vụ sắp xếp lịch học bù.'', DATEADD(hour, -2, GETDATE()));

-- Case 4: Đơn APPROVED -> Ca bù NO_SLOT (Ngọc Hân - Buổi 7 Múa Ballet)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuHanId, @L_C5_S7, @ParentHoangId, N''Bé Ngọc Hân bị đau họng nhẹ, xin phép cô Linh nghỉ buổi múa.'', ''APPROVED'', @TeacherLinhId, N''Cô Linh đồng ý cho bé nghỉ và chuyển bộ phận học vụ rà soát lớp.'', DATEADD(day, -1, GETDATE()), DATEADD(day, -2, GETDATE()));
DECLARE @ArHanNoSlot BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArHanNoSlot, @StuHanId, @L_C5_S7, NULL, ''NO_SLOT'', N''CHƯA CÓ LỚP PHÙ HỢP: Hiện trung tâm chưa có lớp Múa Ballet thiếu nhi căn bản song song ở Buổi 7.'', DATEADD(day, -1, GETDATE()));

-- Case 5: Đơn APPROVED -> Ca bù CANCELLED (Trọng Anh - Gia đình không sắp xếp được lịch)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuAnhId, @L_C4_S9, @ParentLanId, N''Bé Trọng Anh bận ôn thi học kỳ trên trường, xin nghỉ buổi Guitar 9.'', ''APPROVED'', @TeacherTuanId, N''Thầy Tuấn đồng ý cho bé nghỉ.'', DATEADD(day, -1, GETDATE()), DATEADD(day, -1, GETDATE()));
DECLARE @ArAnhCancelled BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArAnhCancelled, @StuAnhId, @L_C4_S9, NULL, ''CANCELLED'', N''Gia đình xác nhận bận lịch thi cả tuần nên không tham gia học bù buổi này.'', DATEADD(hour, -10, GETDATE()));

-- Case 6: Đơn APPROVED -> Ca bù SCHEDULED Lần 1/2 hôm nay (Trọng Anh -> Class 7)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuAnhId, @L_C4_S10, @ParentLanId, N''Bé Trọng Anh trùng lịch ngoại khóa buổi chiều, xin chuyển ca học hôm nay.'', ''APPROVED'', @TeacherTuanId, N''Đã duyệt chuyển sang lớp thầy Dũng dạy bù.'', DATEADD(hour, -6, GETDATE()), DATEADD(day, -1, GETDATE()));
DECLARE @ArAnhScheduledToday BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArAnhScheduledToday, @StuAnhId, @L_C4_S10, @L_C7_S10, ''SCHEDULED'', N''[Lần bù: 1/2] Đã xếp lịch học bù tại lớp Guitar Nhập Môn Chiều Thứ 7 (Thầy Dũng).'', DATEADD(hour, -5, GETDATE()));

-- Case 7: Đơn APPROVED -> Ca bù SCHEDULED Lần 1/2 lớp 1-1 (Bảo Nam chuyển buổi 11 sang Class 3)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuNamId, @L_C1_S11, @ParentLanId, N''Xin chuyển buổi Piano 1-1 ngày mai sang học ghép cùng nhóm tối.'', ''APPROVED'', @TeacherHuongId, N''Cô Hương đồng ý chuyển bù.'', DATEADD(hour, -5, GETDATE()), DATEADD(hour, -8, GETDATE()));
DECLARE @ArNamHide11 BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArNamHide11, @StuNamId, @L_C1_S11, @L_C3_S11, ''SCHEDULED'', N''[Lần bù: 1/2] Đã xếp học bù tại lớp Piano Grade 1 - Nhóm Chiều Tối.'', DATEADD(hour, -4, GETDATE()));

-- Case 8: Đơn APPROVED -> Ca bù MISSED Lần 1/2 (Bảo Nam vắng tại C3-S8 ngày @D_M2)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuNamId, @L_C1_S8, @ParentLanId, N''Bé Bảo Nam bị sốt nhẹ nên xin nghỉ Buổi 8 Piano.'', ''APPROVED'', @TeacherHuongId, N''Cô Hương đã duyệt đơn.'', DATEADD(day, -3, GETDATE()), DATEADD(day, -3, GETDATE()));
DECLARE @ArNamMissed BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArNamMissed, @StuNamId, @L_C1_S8, @L_C3_S8, ''MISSED'', N''[Lần bù: 1/2] Bỏ lỡ / Vắng mặt tại buổi học bù (Còn tối đa 1 lần xếp lại cuối cùng).'', DATEADD(day, -2, GETDATE()));

-- Case 9: Đơn APPROVED -> Ca bù SCHEDULED Lần 2/2 hôm nay (Hải Yến -> Class 8)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuYenId, @L_C3_S10, @ParentLanId, N''Gia đình có việc bận đột xuất, xin nghỉ Buổi 10 Piano.'', ''APPROVED'', @TeacherHaId, N''Cô Hà đã duyệt đơn.'', DATEADD(day, -2, GETDATE()), DATEADD(day, -2, GETDATE()));
DECLARE @ArYenRetry2 BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArYenRetry2, @StuYenId, @L_C3_S10, @L_C8_S10, ''SCHEDULED'', N''[Lần bù: 2/2] Xếp lịch học bù lần cuối tại lớp Piano Grade 1 - Cơ Sở Đống Đa.'', DATEADD(day, -1, GETDATE()));

-- Case 10: Đơn APPROVED -> Ca bù CANCELLED Khóa 2/2 (Trọng Anh đã vắng đủ 2/2 lần)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuAnhId, @L_C4_S8, @ParentLanId, N''Bé Trọng Anh tham gia thi Olympic Toán cấp trường, xin nghỉ Buổi 8.'', ''APPROVED'', @TeacherTuanId, N''Thầy Tuấn duyệt đơn.'', DATEADD(day, -3, GETDATE()), DATEADD(day, -3, GETDATE()));
DECLARE @ArAnhLocked2 BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArAnhLocked2, @StuAnhId, @L_C4_S8, @L_C7_S8, ''CANCELLED'', N''[Lần bù: 2/2 - KHÓA] Hủy quyền học bù do học viên đã vắng mặt cả 2/2 lần học bù quy định.'', DATEADD(day, -1, GETDATE()));

-- Case 11: Đơn APPROVED -> Ca bù COMPLETED (Tuấn Kiệt đã hoàn thành tại C6-S8)
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (@StuKietId, @L_C6_S7, @ParentDungId, N''Bé Tuấn Kiệt xin nghỉ Buổi 7 Võ thuật do trùng lịch tiêm chủng.'', ''APPROVED'', @TeacherLongId, N''Thầy Long đồng ý.'', DATEADD(day, -3, GETDATE()), DATEADD(day, -3, GETDATE()));
DECLARE @ArKietCompleted BIGINT = SCOPE_IDENTITY();
INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (@ArKietCompleted, @StuKietId, @L_C6_S7, @L_C6_S8, ''COMPLETED'', N''[Lần bù: 1/2] Tự động hoàn thành sau khi giáo viên điểm danh Có mặt.'', DATEADD(day, -1, GETDATE()));

-- =============================================================================
-- 4. INSERT DỮ LIỆU ĐIỂM DANH LỊCH SỬ (ATTENDANCES)
-- =============================================================================
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at) VALUES
(@L_C3_S8,   @StuNamId,  ''ABSENT'',  N''Học viên vắng mặt tại buổi học bù lần 1/2'', DATEADD(day, -2, GETDATE())),
(@L_C7_S8,   @StuAnhId,  ''ABSENT'',  N''Học viên vắng mặt tại buổi học bù lần 2/2'', DATEADD(day, -1, GETDATE())),
(@L_C4_S5,   @StuAnhId,  ''ABSENT'',  N''Nghỉ có việc gia đình'', DATEADD(day, -6, GETDATE())),
(@L_C4_S6,   @StuAnhId,  ''ABSENT'',  N''Nghỉ ốm'', DATEADD(day, -5, GETDATE())),
(@L_C4_S7,   @StuAnhId,  ''ABSENT'',  N''Nghỉ bận lịch trường'', DATEADD(day, -4, GETDATE())),
(@L_C4_S8,   @StuAnhId,  ''ABSENT'',  N''Nghỉ thi học sinh giỏi'', DATEADD(day, -3, GETDATE())),
(@L_C6_S8,   @StuKietId, ''PRESENT'', N''Tham gia học bù đầy đủ'', DATEADD(day, -1, GETDATE())),
(@L_C6_S4,   @StuKietId, ''ABSENT'',  N''Nghỉ ốm'', DATEADD(day, -6, GETDATE())),
(@L_C6_S5,   @StuKietId, ''ABSENT'',  N''Nghỉ có phép'', DATEADD(day, -5, GETDATE())),
(@L_C6_S6,   @StuKietId, ''ABSENT'',  N''Nghỉ việc gia đình'', DATEADD(day, -4, GETDATE()));

-- =============================================================================
-- 5. KIỂM TRA NHANH DỮ LIỆU SAU KHI NẠP
-- =============================================================================
SELECT ar.id, s.full_name AS student_name, c.class_code, l.lesson_date, l.title, ar.status, ar.reason
FROM absence_requests ar
JOIN students s ON ar.student_id = s.id
JOIN lessons l ON ar.lesson_id = l.id
JOIN classes c ON l.class_id = c.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
ORDER BY ar.id;

SELECT mr.id, s.full_name AS student_name, lo.title AS original_lesson, lt.title AS target_lesson, mr.status, mr.note
FROM makeup_registrations mr
JOIN students s ON mr.student_id = s.id
JOIN lessons lo ON mr.original_lesson_id = lo.id
LEFT JOIN lessons lt ON mr.target_lesson_id = lt.id
WHERE lo.lesson_date BETWEEN @D_M6 AND @D_P6
ORDER BY mr.id;';

-- ================= 4. D26 - TUONG THICH SCHEMA HIEN TAI =================
EXEC sys.sp_executesql N'-- =============================================================================
-- KỊCH BẢN DỮ LIỆU ĐIỀU HÀNH: QUẢN LÝ XIN NGHỈ - HỌC BÙ - ĐIỂM DANH
-- Hệ quản trị cơ sở dữ liệu: Microsoft SQL Server (database: course_operation_management)
--
-- TẬP TRUNG DUY NHẤT 3 TÀI KHOẢN (Mật khẩu: Test@123456):
--   1. d26_staff        : Giáo vụ cơ sở (STAFF)
--   2. d26_parent_new   : Phụ huynh (PARENT) - Phụ huynh bé Nguyễn Bảo An & Nguyễn Bảo Nam
--   3. teacher_huong    : Giáo viên chuyên môn Piano (TEACHER) - Cô Vũ Thu Hương
--
-- ĐẶC TÍNH:
--   - Cửa sổ ngày động xoay quanh ngày hiện tại GETDATE() (-6 ngày đến +6 ngày).
--   - Đầy đủ 10 trường hợp nghiệp vụ toàn diện của quy trình Xin nghỉ & Học bù.
--   - Chuẩn hóa tag điều phối: [Lần bù: 1/2], [Lần bù: 2/2], [Lần bù: 2/2 - KHÓA].
--   - Không chứa bất kỳ tiền tố test thừa nào trong văn bản giao diện.
--   - Idempotent: An toàn chạy lại nhiều lần bất cứ lúc nào.
-- =============================================================================


SET NOCOUNT ON;
SET XACT_ABORT ON;

-- Hash BCrypt chuẩn của mật khẩu Test@123456
DECLARE @BcryptPassword VARCHAR(255) = ''$2a$10$Qq6uoup7qgd2LgZGE.uu9uvZ9upjF2U.il/xf53YdJsXEhKyyDw.K'';

-- Cửa sổ ngày động quanh ngày hiện tại
DECLARE @Today DATE = CAST(GETDATE() AS DATE);
DECLARE @D_M6  DATE = DATEADD(day, -6, @Today);
DECLARE @D_M5  DATE = DATEADD(day, -5, @Today);
DECLARE @D_M4  DATE = DATEADD(day, -4, @Today);
DECLARE @D_M3  DATE = DATEADD(day, -3, @Today);
DECLARE @D_M2  DATE = DATEADD(day, -2, @Today);
DECLARE @D_M1  DATE = DATEADD(day, -1, @Today);
DECLARE @D_0   DATE = @Today;
DECLARE @D_P1  DATE = DATEADD(day,  1, @Today);
DECLARE @D_P2  DATE = DATEADD(day,  2, @Today);
DECLARE @D_P3  DATE = DATEADD(day,  3, @Today);
DECLARE @D_P4  DATE = DATEADD(day,  4, @Today);
DECLARE @D_P5  DATE = DATEADD(day,  5, @Today);
DECLARE @D_P6  DATE = DATEADD(day,  6, @Today);

-- =============================================================================
-- PHẦN 1: KHỞI TẠO / ĐỒNG BỘ 3 TÀI KHOẢN DUY NHẤT (MẬT KHẨU: Test@123456)
-- =============================================================================

-- 1. Tài khoản Giáo vụ: d26_staff
IF NOT EXISTS (SELECT 1 FROM users WHERE username = ''d26_staff'')
    INSERT INTO users (username, password, full_name, email, phone, role, status)
    VALUES (''d26_staff'', @BcryptPassword, N''Nguyễn Thanh Mai (Giáo Vụ Cơ Sở)'', ''staff.d26@talentacademy.edu.vn'', ''0901000002'', ''STAFF'', ''ACTIVE'');
ELSE
    UPDATE users SET password = @BcryptPassword, status = ''ACTIVE'' WHERE username = ''d26_staff'';

-- 2. Tài khoản Phụ huynh: d26_parent_new
IF NOT EXISTS (SELECT 1 FROM users WHERE username = ''d26_parent_new'')
    INSERT INTO users (username, password, full_name, email, phone, role, status)
    VALUES (''d26_parent_new'', @BcryptPassword, N''Phụ Huynh Trần Thị Lan'', ''parent.lan.d26@gmail.com'', ''0901000004'', ''PARENT'', ''ACTIVE'');
ELSE
    UPDATE users SET password = @BcryptPassword, status = ''ACTIVE'' WHERE username = ''d26_parent_new'';

-- 3. Tài khoản Giáo viên: teacher_huong
IF NOT EXISTS (SELECT 1 FROM users WHERE username = ''teacher_huong'')
    INSERT INTO users (username, password, full_name, email, phone, role, status)
    VALUES (''teacher_huong'', @BcryptPassword, N''Cô Vũ Thu Hương (GV Piano)'', ''huong.vu@talentacademy.edu.vn'', ''0912345678'', ''TEACHER'', ''ACTIVE'');
ELSE
    UPDATE users SET password = @BcryptPassword, status = ''ACTIVE'' WHERE username = ''teacher_huong'';

DECLARE @StaffId   BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''d26_staff'');
DECLARE @ParentId  BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''d26_parent_new'');
DECLARE @TeacherId BIGINT = (SELECT TOP 1 id FROM users WHERE username = ''teacher_huong'');

-- =============================================================================
-- PHẦN 2: HỌC VIÊN CỦA PHỤ HUYNH d26_parent_new
-- =============================================================================
-- Bé lớn: Nguyễn Bảo An
IF NOT EXISTS (SELECT 1 FROM students WHERE full_name = N''Nguyễn Bảo An'' AND parent_id = @ParentId)
    INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentId, N''Nguyễn Bảo An'', ''2017-05-12'', N''Nam'', N''Tiểu học Đoàn Thị Điểm'', N''Học viên lớp Piano nền tảng'');
ELSE
    UPDATE students SET parent_id = @ParentId WHERE full_name = N''Nguyễn Bảo An'' AND parent_id = @ParentId;

-- Bé thứ hai: Nguyễn Bảo Nam (giúp phụ huynh kiểm tra đồng thời nhiều trạng thái)
IF NOT EXISTS (SELECT 1 FROM students WHERE full_name = N''Nguyễn Bảo Nam'' AND parent_id = @ParentId)
    INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes)
    VALUES (@ParentId, N''Nguyễn Bảo Nam'', ''2019-10-18'', N''Nam'', N''Tiểu học Đoàn Thị Điểm'', N''Học viên lớp Piano thiếu nhi'');
ELSE
    UPDATE students SET parent_id = @ParentId WHERE full_name = N''Nguyễn Bảo Nam'' AND parent_id = @ParentId;

DECLARE @StuAnId  BIGINT = (SELECT TOP 1 id FROM students WHERE full_name = N''Nguyễn Bảo An'' AND parent_id = @ParentId);
DECLARE @StuNamId BIGINT = (SELECT TOP 1 id FROM students WHERE full_name = N''Nguyễn Bảo Nam'' AND parent_id = @ParentId);

-- =============================================================================
-- PHẦN 3: CƠ SỞ, PHÒNG HỌC, CHƯƠNG TRÌNH & KHÓA HỌC
-- =============================================================================
-- Cơ sở Mỹ Đình
IF NOT EXISTS (SELECT 1 FROM branches WHERE code = ''CS-MD'')
    INSERT INTO branches (code, name, address, phone, email, active)
    VALUES (''CS-MD'', N''Cơ sở Mỹ Đình'', N''Tầng 2, Tòa Mỹ Đình Plaza, Nam Từ Liêm, Hà Nội'', ''02439998811'', ''mydinh@talentacademy.edu.vn'', 1);
DECLARE @BranchMdId BIGINT = (SELECT TOP 1 id FROM branches WHERE code = ''CS-MD'');

-- Cơ sở Cầu Giấy (lựa chọn ca bù khác cơ sở)
IF NOT EXISTS (SELECT 1 FROM branches WHERE code = ''CS01'')
    INSERT INTO branches (code, name, address, phone, email, active)
    VALUES (''CS01'', N''Cơ sở Cầu Giấy'', N''Số 12 Chùa Hà, Cầu Giấy, Hà Nội'', ''02438887711'', ''caugiay@talentacademy.edu.vn'', 1);
DECLARE @BranchCgId BIGINT = (SELECT TOP 1 id FROM branches WHERE code = ''CS01'');

-- Phòng Piano 201 tại Mỹ Đình
IF NOT EXISTS (SELECT 1 FROM rooms WHERE room_code = ''MD-PIA-201'' AND branch_id = @BranchMdId)
    INSERT INTO rooms (branch_id, room_code, room_name, capacity, room_type, status, description)
    VALUES (@BranchMdId, ''MD-PIA-201'', N''Phòng Piano 201'', 8, ''PIANO_GROUP'', ''AVAILABLE'', N''Phòng học Piano 8 đàn điện tiêu chuẩn'');
DECLARE @RoomMd201 BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = ''MD-PIA-201'' AND branch_id = @BranchMdId);

-- Phòng Piano 101 tại Cầu Giấy
IF NOT EXISTS (SELECT 1 FROM rooms WHERE room_code = ''P101'' AND branch_id = @BranchCgId)
    INSERT INTO rooms (branch_id, room_code, room_name, capacity, room_type, status, description)
    VALUES (@BranchCgId, ''P101'', N''Phòng Đàn Piano 101'', 8, ''PIANO_GROUP'', ''AVAILABLE'', N''Phòng thực hành Piano Cầu Giấy'');
DECLARE @RoomCg101 BIGINT = (SELECT TOP 1 id FROM rooms WHERE room_code = ''P101'' AND branch_id = @BranchCgId);

-- Chương trình Âm nhạc
DECLARE @ProgMusicId BIGINT = (SELECT TOP 1 id FROM programs WHERE code = ''PROG-MUSIC'');
IF @ProgMusicId IS NULL
BEGIN
    INSERT INTO programs (code, name, description)
    VALUES (''PROG-MUSIC'', N''Chương Trình Âm Nhạc & Nhạc Cụ'', N''Đào tạo nhạc cụ Piano, Guitar, Thanh nhạc'');
    SET @ProgMusicId = SCOPE_IDENTITY();
END;

-- Khóa học PIA-CB-12 (Piano nền tảng)
DECLARE @CoursePiaId BIGINT = (SELECT TOP 1 id FROM courses WHERE code = ''PIA-CB-12'');
IF @CoursePiaId IS NULL
BEGIN
    INSERT INTO courses (program_id, code, name, level, duration_weeks, total_sessions, tuition_fee, description)
    VALUES (@ProgMusicId, ''PIA-CB-12'', N''Piano nền tảng'', N''Cơ bản'', 12, 12, 3600000, N''Khóa học Piano nền tảng trang bị tư thế tay, nhận diện nốt, đọc bản nhạc và thị tấu.'');
    SET @CoursePiaId = SCOPE_IDENTITY();
END;

-- =============================================================================
-- PHẦN 4: LỚP HỌC (CLASSES) & GHI DANH (ENROLLMENTS) DO Cô Hương GIẢNG DẠY
-- =============================================================================
-- 1. Lớp chính: PIA-MD-2701-01 (Chiều Thứ 7: 17:00-19:00 Mỹ Đình)
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = ''PIA-MD-2701-01'')
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaId, @BranchMdId, @RoomMd201, @TeacherId, ''PIA-MD-2701-01'', N''Piano nền tảng - chiều thứ Bảy Mỹ Đình'', ''GROUP'', 8, 2, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N''Thứ 7 (17:00 - 19:00)'', ''OPEN'');
ELSE
    UPDATE classes SET branch_id = @BranchMdId, teacher_id = @TeacherId, room_id = @RoomMd201, status = ''OPEN'' WHERE class_code = ''PIA-MD-2701-01'';
DECLARE @ClassMainId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''PIA-MD-2701-01'');

-- 2. Lớp song song tiếp nhận ca bù: PIA-MD-2701-02 (Sáng Chủ Nhật: 09:00-11:00 Mỹ Đình)
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = ''PIA-MD-2701-02'')
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaId, @BranchMdId, @RoomMd201, @TeacherId, ''PIA-MD-2701-02'', N''Piano nền tảng - sáng Chủ Nhật Mỹ Đình'', ''GROUP'', 8, 2, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N''Chủ Nhật (09:00 - 11:00)'', ''OPEN'');
ELSE
    UPDATE classes SET branch_id = @BranchMdId, teacher_id = @TeacherId, room_id = @RoomMd201, status = ''OPEN'' WHERE class_code = ''PIA-MD-2701-02'';
DECLARE @ClassParaMdId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''PIA-MD-2701-02'');

-- 3. Lớp ca bù khác cơ sở: PIA-CG-2701-01 (Chiều Thứ 5: 17:30-19:30 Cầu Giấy)
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = ''PIA-CG-2701-01'')
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaId, @BranchCgId, @RoomCg101, @TeacherId, ''PIA-CG-2701-01'', N''Piano nền tảng - chiều thứ Năm Cầu Giấy'', ''GROUP'', 8, 3, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N''Thứ 5 (17:30 - 19:30)'', ''OPEN'');
ELSE
    UPDATE classes SET teacher_id = @TeacherId, room_id = @RoomCg101, status = ''OPEN'' WHERE class_code = ''PIA-CG-2701-01'';
DECLARE @ClassParaCgId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''PIA-CG-2701-01'');

-- 4. Lớp đầy chỗ (1/1) để kiểm tra bộ lọc hệ thống loại trừ lớp không còn slot
IF NOT EXISTS (SELECT 1 FROM classes WHERE class_code = ''PIA-MD-2701-FULL'')
    INSERT INTO classes (course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type, max_students, current_students, start_date, end_date, schedule_description, status)
    VALUES (@CoursePiaId, @BranchMdId, @RoomMd201, @TeacherId, ''PIA-MD-2701-FULL'', N''Piano nền tảng - sáng Thứ Hai (Kín chỗ)'', ''GROUP'', 1, 1, DATEADD(day, -30, @Today), DATEADD(day, 60, @Today), N''Thứ 2 (08:30 - 10:30)'', ''OPEN'');
DECLARE @ClassFullId BIGINT = (SELECT TOP 1 id FROM classes WHERE class_code = ''PIA-MD-2701-FULL'');

-- Ghi danh 2 học viên của d26_parent_new vào lớp chính
IF NOT EXISTS (SELECT 1 FROM enrollments WHERE student_id = @StuAnId AND class_id = @ClassMainId)
    INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes)
    VALUES (@StuAnId, @ClassMainId, @ParentId, ''ENROLLED'', N''Ghi danh lớp chính khóa'');

IF NOT EXISTS (SELECT 1 FROM enrollments WHERE student_id = @StuNamId AND class_id = @ClassMainId)
    INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes)
    VALUES (@StuNamId, @ClassMainId, @ParentId, ''ENROLLED'', N''Ghi danh lớp chính khóa'');

-- =============================================================================
-- PHẦN 5: LÀM SẠCH VÙNG LỊCH XOAY QUANH HÔM NAY (-6 ĐẾN +6 NGÀY)
-- =============================================================================
DELETE a
FROM attendances a
JOIN lessons l ON a.lesson_id = l.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
  AND l.class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId);

DELETE m
FROM makeup_registrations m
LEFT JOIN lessons lo ON m.original_lesson_id = lo.id
LEFT JOIN lessons lt ON m.target_lesson_id = lt.id
WHERE (lo.lesson_date BETWEEN @D_M6 AND @D_P6 AND lo.class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId))
   OR (lt.lesson_date BETWEEN @D_M6 AND @D_P6 AND lt.class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId));

DELETE ar
FROM absence_requests ar
JOIN lessons l ON ar.lesson_id = l.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
  AND l.class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId);

DELETE FROM lessons
WHERE lesson_date BETWEEN @D_M6 AND @D_P6
  AND class_id IN (@ClassMainId, @ClassParaMdId, @ClassParaCgId, @ClassFullId);

-- =============================================================================
-- PHẦN 6: TẠO CÁC BUỔI HỌC (LESSONS) CHO LỚP CÔ HƯƠNG TRONG CỬA SỔ LỊCH
-- =============================================================================
-- A. Lớp chính: PIA-MD-2701-01
-- Buổi 1 (-6 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 1, @D_M6, ''17:00'', ''19:00'', N''Buổi 1: Giới thiệu đàn Piano & Tư thế bấm phím'', N''Làm quen bàn phím, ngồi đúng tư thế tay hình vòm'', ''COMPLETED'');
DECLARE @L_Main_S1 BIGINT = SCOPE_IDENTITY();

-- Buổi 2 (-4 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 2, @D_M4, ''17:00'', ''19:00'', N''Buổi 2: Khóa Sol, Khóa Pha và nốt Đô trung tâm'', N''Luyện tập nhận diện nốt nhạc trên khuông nhạc'', ''COMPLETED'');
DECLARE @L_Main_S2 BIGINT = SCOPE_IDENTITY();

-- Buổi 3 (-3 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 3, @D_M3, ''17:00'', ''19:00'', N''Buổi 3: Luyện ngón tay phải & Tiết tấu nốt đen'', N''Bài thực hành số 1 giáo trình Alfred'', ''COMPLETED'');
DECLARE @L_Main_S3 BIGINT = SCOPE_IDENTITY();

-- Buổi 4 (-2 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 4, @D_M2, ''17:00'', ''19:00'', N''Buổi 4: Luyện ngón tay trái & Nhịp 4/4'', N''Thực hành ghép hai tay chậm từng ô nhịp'', ''COMPLETED'');
DECLARE @L_Main_S4 BIGINT = SCOPE_IDENTITY();

-- Buổi 5 (-1 ngày)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 5, @D_M1, ''17:00'', ''19:00'', N''Buổi 5: Ghép hai tay tiểu phẩm Vui Bước'', N''Tập trung giữ nhịp phách ổn định'', ''COMPLETED'');
DECLARE @L_Main_S5 BIGINT = SCOPE_IDENTITY();

-- Buổi 6 (HÔM NAY @D_0) - Buổi học chính khóa chiều tối nay
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 6, @D_0, ''17:00'', ''19:00'', N''Buổi 6: Kỹ thuật Legato & Hợp âm Đô trưởng'', N''Buổi học chính khóa chiều thứ Bảy của lớp'', ''SCHEDULED'');
DECLARE @L_Main_S6 BIGINT = SCOPE_IDENTITY();

-- Buổi 6B (HÔM NAY @D_0 23:45 - Kiểm thử khóa giờ điểm danh của giáo viên trước giờ bắt đầu)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 6, @D_0, ''23:45'', ''23:59'', N''Buổi 6B: Thực hành biểu diễn cuối ngày'', N''Kiểm tra cơ chế khóa giờ điểm danh trước giờ học'', ''SCHEDULED'');
DECLARE @L_Main_S6B BIGINT = SCOPE_IDENTITY();

-- Buổi 7 (+2 ngày tương lai) - Có đơn xin nghỉ chờ duyệt
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 7, @D_P2, ''17:00'', ''19:00'', N''Buổi 7: Dấu hóa thăng, giáng cơ bản'', N''Phụ huynh đã nộp đơn xin nghỉ trước giờ quy định'', ''SCHEDULED'');
DECLARE @L_Main_S7 BIGINT = SCOPE_IDENTITY();

-- Buổi 8 (+4 ngày tương lai)
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassMainId, @RoomMd201, @TeacherId, 8, @D_P4, ''17:00'', ''19:00'', N''Buổi 8: Luyện ngón Hanon nâng cao số 1'', N''Luyện độ độc lập của các ngón tay'', ''SCHEDULED'');
DECLARE @L_Main_S8 BIGINT = SCOPE_IDENTITY();

-- B. Lớp song song tiếp nhận học bù: PIA-MD-2701-02 (Cô Hương giảng dạy)
-- Buổi S3 (-2 ngày): Buổi bù đã hoàn thành
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaMdId, @RoomMd201, @TeacherId, 3, @D_M2, ''09:00'', ''11:00'', N''Buổi 3: Luyện ngón tay phải & Tiết tấu nốt đen'', N''Lớp ghép học bù Chủ Nhật'', ''COMPLETED'');
DECLARE @L_ParaMd_S3 BIGINT = SCOPE_IDENTITY();

-- Buổi S4 (-1 ngày): Buổi bù bị vắng lần 1
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaMdId, @RoomMd201, @TeacherId, 4, @D_M1, ''09:00'', ''11:00'', N''Buổi 4: Luyện ngón tay trái & Nhịp 4/4'', N''Lớp tiếp nhận học viên học bù'', ''COMPLETED'');
DECLARE @L_ParaMd_S4 BIGINT = SCOPE_IDENTITY();

-- Buổi S5 (HÔM NAY @D_0): Tiếp nhận ca học bù hôm nay!
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaMdId, @RoomMd201, @TeacherId, 5, @D_0, ''09:00'', ''11:00'', N''Buổi 5: Ghép hai tay tiểu phẩm Vui Bước'', N''Buổi học tiếp nhận học viên học bù hôm nay'', ''SCHEDULED'');
DECLARE @L_ParaMd_S5 BIGINT = SCOPE_IDENTITY();

-- Buổi S6 (+3 ngày tương lai): Ứng viên ca bù chờ xếp
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaMdId, @RoomMd201, @TeacherId, 6, @D_P3, ''09:00'', ''11:00'', N''Buổi 6: Kỹ thuật Legato & Hợp âm Đô trưởng'', N''Buổi học sẵn sàng tiếp nhận học viên bù'', ''SCHEDULED'');
DECLARE @L_ParaMd_S6 BIGINT = SCOPE_IDENTITY();

-- C. Lớp khác cơ sở Cầu Giấy (+3 ngày tương lai): Ứng viên ca bù khác cơ sở
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassParaCgId, @RoomCg101, @TeacherId, 6, @D_P3, ''17:30'', ''19:30'', N''Buổi 6: Kỹ thuật Legato & Hợp âm Đô trưởng'', N''Lựa chọn học bù khác cơ sở cho học viên'', ''SCHEDULED'');
DECLARE @L_ParaCg_S6 BIGINT = SCOPE_IDENTITY();

-- D. Lớp đầy chỗ (1/1) để test bộ lọc loại trừ lớp hết chỗ
INSERT INTO lessons (class_id, room_id, teacher_id, session_number, lesson_date, start_time, end_time, title, lesson_note, status)
VALUES (@ClassFullId, @RoomMd201, @TeacherId, 6, @D_P3, ''08:30'', ''10:30'', N''Buổi 6: Kỹ thuật Legato (Đã đầy chỗ)'', N''Lớp đã đủ sĩ số 1/1'', ''SCHEDULED'');
DECLARE @L_Full_S6 BIGINT = SCOPE_IDENTITY();

-- =============================================================================
-- PHẦN 7: TẠO ĐẦY ĐỦ 10 TÌNH HUỐNG TRÊN 3 TÀI KHOẢN DUY NHẤT
-- =============================================================================

-- CASE 1: ĐƠN XIN NGHỈ PENDING (Chờ duyệt)
-- Thao tác kiểm thử:
--   - d26_parent_new đăng nhập: xem đơn của bé Bảo An, có nút "Hủy đơn" (Cancel eligible request).
--   - teacher_huong hoặc d26_staff đăng nhập: xem đơn, có nút "Duyệt" (Approve) và "Từ chối" (Reject).
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, created_at)
VALUES (
    @StuAnId,
    @L_Main_S7,
    @ParentId,
    N''Học viên Nguyễn Bảo An bị sốt virus theo chỉ định của bác sĩ cần nghỉ ngơi 3 ngày, gia đình xin phép cô Hương cho bé nghỉ buổi 7.'',
    ''PENDING'',
    DATEADD(minute, -25, GETDATE())
);

-- CASE 2: ĐƠN XIN NGHỈ REJECTED (Bị từ chối)
-- Nghiệp vụ: Phụ huynh gửi đơn muộn sau giờ học -> Cô Hương từ chối kèm phản hồi -> Không sinh ca bù.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S4,
    @ParentId,
    N''Gia đình có lịch về quê đột xuất nên xin phép cho bé Bảo Nam vắng mặt.'',
    ''REJECTED'',
    @TeacherId,
    N''Từ chối do phụ huynh gửi đơn sau thời gian diễn ra buổi học và không thông báo trước cho giáo vụ.'',
    DATEADD(day, -1, GETDATE()),
    DATEADD(day, -1, GETDATE())
);

-- CASE 3: ĐƠN APPROVED -> CA BÙ PENDING (Chờ giáo vụ xếp lịch)
-- Thao tác kiểm thử:
--   - d26_staff vào mục Quản lý học bù -> Bấm Xếp lịch bù -> Hệ thống lọc gợi ý các buổi cùng trình độ (S6), loại trừ lớp kín chỗ.
--   - d26_parent_new vào xem và chọn ca bù (Select make-up option).
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuAnId,
    @L_Main_S6,
    @ParentId,
    N''Học viên Bảo An trùng lịch thi học kỳ tại trường tiểu học vào chiều Thứ Bảy.'',
    ''APPROVED'',
    @TeacherId,
    N''Cô Hương đồng ý cho bé nghỉ. Chuyển bộ phận giáo vụ sắp xếp ca học bù phù hợp cho học viên.'',
    DATEADD(hour, -2, GETDATE()),
    DATEADD(hour, -5, GETDATE())
);
DECLARE @Ar_Pending_Makeup BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Pending_Makeup,
    @StuAnId,
    @L_Main_S6,
    NULL,
    ''PENDING'',
    N''Đơn xin nghỉ đã duyệt, đang chờ phụ huynh và giáo vụ lựa chọn lịch học bù thích hợp.'',
    DATEADD(hour, -2, GETDATE())
);

-- CASE 4: ĐƠN APPROVED -> CA BÙ NO_SLOT (Chưa có lớp phù hợp)
-- Nghiệp vụ: Giáo vụ rà soát chưa có lớp song song phù hợp -> Đánh dấu NO_SLOT, giữ mở để theo dõi.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S5,
    @ParentId,
    N''Bé Bảo Nam bị đau họng và khàn tiếng, xin nghỉ buổi 5 để đi khám chuyên khoa tai mũi họng.'',
    ''APPROVED'',
    @TeacherId,
    N''Đã xác nhận lý do nghỉ ốm của học viên.'',
    DATEADD(day, -1, GETDATE()),
    DATEADD(day, -2, GETDATE())
);
DECLARE @Ar_NoSlot BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_NoSlot,
    @StuNamId,
    @L_Main_S5,
    NULL,
    ''NO_SLOT'',
    N''Chưa có lớp phù hợp: Các lớp cùng trình độ trong tuần hiện chưa mở hoặc đã kín lịch.'',
    DATEADD(day, -1, GETDATE())
);

-- CASE 5: ĐƠN APPROVED -> CA BÙ CANCELLED (Gia đình từ chối học bù)
-- Nghiệp vụ: Phụ huynh d26_parent_new báo gia đình bận không tham gia học bù -> Đóng ca bù CANCELLED.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S2,
    @ParentId,
    N''Học viên Bảo Nam có việc gia đình đột xuất xin phép nghỉ học.'',
    ''APPROVED'',
    @TeacherId,
    N''Đã duyệt đơn nghỉ phép.'',
    DATEADD(day, -3, GETDATE()),
    DATEADD(day, -4, GETDATE())
);
DECLARE @Ar_Cancelled BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Cancelled,
    @StuNamId,
    @L_Main_S2,
    NULL,
    ''CANCELLED'',
    N''Gia đình xác nhận bận lịch riêng nên xin phép không tham gia học bù buổi này.'',
    DATEADD(day, -3, GETDATE())
);

-- CASE 6: CA BÙ SCHEDULED LẦN 1/2 DIỄN RA HÔM NAY
-- Thao tác kiểm thử:
--   - teacher_huong vào Lịch dạy / Điểm danh hôm nay: thấy bé Bảo An tại lớp S5 có nhãn [Học bù].
--   - Điểm danh Có mặt -> chuyển COMPLETED.
--   - Điểm danh Vắng mặt -> chuyển MISSED (Lần 1).
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuAnId,
    @L_Main_S3,
    @ParentId,
    N''Bé Bảo An cần đi tiêm phòng định kỳ vào chiều Thứ Bảy nên xin nghỉ.'',
    ''APPROVED'',
    @TeacherId,
    N''Cô Hương đồng ý cho học viên học bù ghép vào ca sáng Chủ Nhật.'',
    DATEADD(day, -2, GETDATE()),
    DATEADD(day, -3, GETDATE())
);
DECLARE @Ar_Scheduled_Today BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Scheduled_Today,
    @StuAnId,
    @L_Main_S3,
    @L_ParaMd_S5,
    ''SCHEDULED'',
    N''[Lần bù: 1/2] Đã xếp lịch học bù tại lớp Piano sáng Chủ Nhật Mỹ Đình.'',
    DATEADD(day, -1, GETDATE())
);

-- CASE 7: BỎ LỠ CA BÙ LẦN 1/2 -> TRẠNG THÁI MISSED (CÒN 1 LẦN BÙ CUỐI CÙNG)
-- Nghiệp vụ: Đã xếp buổi bù (-1 ngày) nhưng học viên vắng mặt -> Ghi nhận MISSED, tag [Lần bù: 1/2], còn 1 quyền xếp lại.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S3,
    @ParentId,
    N''Học viên Bảo Nam bị sốt nhẹ, xin phép cô Hương nghỉ buổi 3.'',
    ''APPROVED'',
    @TeacherId,
    N''Cô Hương đã duyệt đơn.'',
    DATEADD(day, -3, GETDATE()),
    DATEADD(day, -4, GETDATE())
);
DECLARE @Ar_Missed1 BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Missed1,
    @StuNamId,
    @L_Main_S3,
    @L_ParaMd_S4,
    ''MISSED'',
    N''[Lần bù: 1/2] Học viên vắng mặt tại buổi học bù lần 1 (Còn 1 lần xếp lại lịch bù cuối cùng).'',
    DATEADD(day, -1, GETDATE())
);

-- Ghi nhận điểm danh vắng mặt tại buổi bù S4
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at)
VALUES (@L_ParaMd_S4, @StuNamId, ''ABSENT'', N''Học viên vắng mặt tại buổi học bù lần 1'', DATEADD(day, -1, GETDATE()));

-- CASE 8: CA BÙ SCHEDULED LẦN 2/2 DIỄN RA HÔM NAY (LẦN CUỐI CÙNG)
-- Thao tác kiểm thử:
--   - Bé Bảo Nam được xếp học bù lần 2 tại buổi S5 hôm nay.
--   - teacher_huong điểm danh vắng mặt hôm nay sẽ tự động khóa hủy quyền học bù.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuNamId,
    @L_Main_S4,
    @ParentId,
    N''Gia đình có việc gấp cần về quê, xin phép cô Hương nghỉ buổi 4.'',
    ''APPROVED'',
    @TeacherId,
    N''Đã đồng ý cho học viên học bù lần 2.'',
    DATEADD(day, -2, GETDATE()),
    DATEADD(day, -3, GETDATE())
);
DECLARE @Ar_Scheduled_Retry2 BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Scheduled_Retry2,
    @StuNamId,
    @L_Main_S4,
    @L_ParaMd_S5,
    ''SCHEDULED'',
    N''[Lần bù: 2/2] Xếp lịch học bù lần cuối cùng tại lớp Piano Mỹ Đình.'',
    DATEADD(day, -1, GETDATE())
);

-- CASE 9: VẮNG MẶT CẢ 2/2 LẦN BÙ -> KHÓA HỦY QUYỀN HỌC BÙ (LOCKED CANCELLED)
-- Nghiệp vụ: Học viên đã vắng mặt cả 2 lần học bù quy định -> Hệ thống tự động khóa vĩnh viễn theo quy chế trung tâm.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuAnId,
    @L_Main_S1,
    @ParentId,
    N''Bé Bảo An bị cảm lạnh ngày khai giảng, xin nghỉ buổi mở đầu.'',
    ''APPROVED'',
    @TeacherId,
    N''Đã duyệt đơn nghỉ học.'',
    DATEADD(day, -5, GETDATE()),
    DATEADD(day, -6, GETDATE())
);
DECLARE @Ar_Locked BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Locked,
    @StuAnId,
    @L_Main_S1,
    @L_ParaMd_S4,
    ''CANCELLED'',
    N''[Lần bù: 2/2 - KHÓA] Tự động khóa quyền học bù do học viên đã vắng mặt cả 2 lần học bù quy định.'',
    DATEADD(day, -1, GETDATE())
);

-- Ghi nhận điểm danh vắng mặt tại buổi bù S4 cho bé An
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at)
VALUES (@L_ParaMd_S4, @StuAnId, ''ABSENT'', N''Học viên vắng mặt tại buổi học bù lần 2 (Hết lượt)'', DATEADD(day, -1, GETDATE()));

-- CASE 10: HOÀN THÀNH HỌC BÙ (COMPLETED)
-- Nghiệp vụ: Học viên Bảo An đã tham gia học bù tại buổi S3 (-2 ngày) và được cô Hương điểm danh Có mặt.
INSERT INTO absence_requests (student_id, lesson_id, requested_by_user_id, reason, status, approved_by_teacher_id, review_note, reviewed_at, created_at)
VALUES (
    @StuAnId,
    @L_Main_S2,
    @ParentId,
    N''Học viên bận thi cờ vua cấp trường, xin nghỉ buổi 2.'',
    ''APPROVED'',
    @TeacherId,
    N''Cô Hương đã duyệt đơn.'',
    DATEADD(day, -4, GETDATE()),
    DATEADD(day, -5, GETDATE())
);
DECLARE @Ar_Completed BIGINT = SCOPE_IDENTITY();

INSERT INTO makeup_registrations (absence_request_id, student_id, original_lesson_id, target_lesson_id, status, note, created_at)
VALUES (
    @Ar_Completed,
    @StuAnId,
    @L_Main_S2,
    @L_ParaMd_S3,
    ''COMPLETED'',
    N''[Lần bù: 1/2] Hoàn thành buổi học bù sau khi giáo viên điểm danh Có mặt.'',
    DATEADD(day, -2, GETDATE())
);

-- Ghi nhận điểm danh Có mặt tại buổi bù S3
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at)
VALUES (@L_ParaMd_S3, @StuAnId, ''PRESENT'', N''Tham gia học bù đầy đủ và hoàn thành bài luyện ngón'', DATEADD(day, -2, GETDATE()));

-- Điểm danh một số buổi học chính khóa lịch sử
INSERT INTO attendances (lesson_id, student_id, status, note, marked_at) VALUES
(@L_Main_S1, @StuNamId, ''PRESENT'', N''Tham gia học đầy đủ'', DATEADD(day, -6, GETDATE())),
(@L_Main_S2, @StuAnId,  ''ABSENT'',  N''Nghỉ thi cờ vua'', DATEADD(day, -4, GETDATE())),
(@L_Main_S2, @StuNamId, ''ABSENT'',  N''Nghỉ có việc gia đình'', DATEADD(day, -4, GETDATE())),
(@L_Main_S3, @StuAnId,  ''ABSENT'',  N''Nghỉ tiêm phòng'', DATEADD(day, -3, GETDATE())),
(@L_Main_S3, @StuNamId, ''ABSENT'',  N''Nghỉ ốm'', DATEADD(day, -3, GETDATE())),
(@L_Main_S4, @StuAnId,  ''PRESENT'', N''Tham gia học đầy đủ'', DATEADD(day, -2, GETDATE())),
(@L_Main_S4, @StuNamId, ''ABSENT'',  N''Vắng mặt nộp đơn muộn'', DATEADD(day, -2, GETDATE())),
(@L_Main_S5, @StuAnId,  ''PRESENT'', N''Tham gia học đầy đủ'', DATEADD(day, -1, GETDATE())),
(@L_Main_S5, @StuNamId, ''ABSENT'',  N''Nghỉ đau họng'', DATEADD(day, -1, GETDATE()));

-- =============================================================================
-- PHẦN 8: TRUY VẤN TỔNG KẾT BẢO ĐẢM DỮ LIỆU ĐÃ SẴN SÀNG
-- =============================================================================
PRINT N''==> TỔNG KẾT ĐƠN XIN NGHỈ (3 TÀI KHOẢN: d26_staff / d26_parent_new / teacher_huong):'';
SELECT
    ar.id AS absence_id,
    s.full_name AS student_name,
    u_req.username AS requested_by,
    c.class_code,
    l.lesson_date,
    l.title AS lesson_title,
    ar.status AS absence_status,
    ar.reason,
    ar.review_note
FROM absence_requests ar
JOIN students s ON ar.student_id = s.id
JOIN users u_req ON ar.requested_by_user_id = u_req.id
JOIN lessons l ON ar.lesson_id = l.id
JOIN classes c ON l.class_id = c.id
WHERE l.lesson_date BETWEEN @D_M6 AND @D_P6
ORDER BY ar.id;

PRINT N''==> TỔNG KẾT CA HỌC BÙ (3 TÀI KHOẢN: d26_staff / d26_parent_new / teacher_huong):'';
SELECT
    mr.id AS makeup_id,
    s.full_name AS student_name,
    lo.title AS original_lesson,
    lo.lesson_date AS original_date,
    ISNULL(lt.title, N''-- Chưa gán buổi bù --'') AS target_lesson,
    lt.lesson_date AS target_date,
    mr.status AS makeup_status,
    mr.note
FROM makeup_registrations mr
JOIN students s ON mr.student_id = s.id
JOIN lessons lo ON mr.original_lesson_id = lo.id
LEFT JOIN lessons lt ON mr.target_lesson_id = lt.id
WHERE lo.lesson_date BETWEEN @D_M6 AND @D_P6
ORDER BY mr.id;

PRINT N''==> HOÀN TẤT NẠP DỮ LIỆU KIỂM THỬ CHO 3 TÀI KHOẢN DUY NHẤT!'';

UPDATE users SET token_version=COALESCE(token_version,0)+1,must_change_password=0 WHERE username IN(''d26_staff'',''d26_parent_new'',''teacher_huong'');
';

-- ================= 5. WEEKLY SCHEDULE VA ATTENDANCE TRACKING =================
EXEC sys.sp_executesql N'-- Seed data for reviewing Weekly Schedule + Attendance Tracking.
-- Run the user''s du_lieu_theo_word.sql first, then run this file in SSMS.
-- Creates an isolated class with 4 students and 12 lessons:
-- 10 historical lessons with varied attendance, today''s editable lesson,
-- and one future locked lesson. Does not modify the source Word demo class.
-- Reruns are idempotent: an installation marker prevents resetting demo data.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF DB_NAME() <> N''course_operation_management''
    THROW 51700, ''Wrong database. Run this file in course_operation_management.'', 1;
IF OBJECT_ID(N''dbo.users'', N''U'') IS NULL
 OR OBJECT_ID(N''dbo.students'', N''U'') IS NULL
 OR OBJECT_ID(N''dbo.courses'', N''U'') IS NULL
 OR OBJECT_ID(N''dbo.branches'', N''U'') IS NULL
 OR OBJECT_ID(N''dbo.classes'', N''U'') IS NULL
 OR OBJECT_ID(N''dbo.lessons'', N''U'') IS NULL
 OR OBJECT_ID(N''dbo.enrollments'', N''U'') IS NULL
 OR OBJECT_ID(N''dbo.attendances'', N''U'') IS NULL
    THROW 51701, ''Required application tables are missing. Install the application schema first.'', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @LockResult INT;
    EXEC @LockResult = sys.sp_getapplock
        @Resource = N''ATTENDANCE_WEEKLY_DEMO_V1'',
        @LockMode = ''Exclusive'', @LockOwner = ''Transaction'', @LockTimeout = 15000;
    IF @LockResult < 0 THROW 51702, ''Another attendance demo installation is running. Retry later.'', 1;

    IF OBJECT_ID(N''dbo.attendance_weekly_demo_installations'', N''U'') IS NULL
        CREATE TABLE dbo.attendance_weekly_demo_installations (
            batch_key VARCHAR(80) NOT NULL PRIMARY KEY,
            installed_at DATETIME2 NOT NULL,
            class_code VARCHAR(50) NOT NULL,
            first_lesson_date DATE NOT NULL,
            today_lesson_date DATE NOT NULL,
            future_lesson_date DATE NOT NULL
        );

    IF EXISTS (SELECT 1 FROM dbo.attendance_weekly_demo_installations WHERE batch_key = ''ATT_WEEKLY_V1'')
    BEGIN
        PRINT N''Dữ liệu attendance demo đã được cài. Giữ nguyên các thay đổi đã demo; không nạp lại.'';
    END
    ELSE
    BEGIN
        DECLARE @Now DATETIME2 = CONVERT(DATETIME2, SYSDATETIMEOFFSET() AT TIME ZONE ''SE Asia Standard Time'');
        DECLARE @Today DATE = CONVERT(DATE, @Now);
        DECLARE @Teacher BIGINT = (SELECT id FROM dbo.users WHERE username = ''d26_teacher_piano'' AND role = ''TEACHER'' AND status = ''ACTIVE'');
        IF EXISTS (SELECT 1 FROM dbo.users WHERE username = ''d26_teacher_other'')
            THROW 51706, ''Reserved username d26_teacher_other already exists. Existing account was preserved; choose another unused username in this demo script.'', 1;
        INSERT dbo.users(username, password, full_name, email, role, subject, teaching_subject,
            status, token_version, must_change_password, created_at)
        VALUES(''d26_teacher_other'', ''$2a$10$U9rS8H8giwtd3tJo7XlXG.tkjsA8opaNfJZqihlijddbmKw2k76Ju'',
            N''Trần Giáo Viên Khác'', ''d26_teacher_other@talent.example'', ''TEACHER'', ''GUITAR'', ''GUITAR'',
            ''ACTIVE'', 0, 0, @Now);
        DECLARE @Parent BIGINT = (SELECT id FROM dbo.users WHERE username = ''d26_parent_new'' AND role = ''PARENT'' AND status = ''ACTIVE'');
        DECLARE @Course BIGINT = (SELECT id FROM dbo.courses WHERE code = ''PIA-CB-12'');
        DECLARE @Branch BIGINT = (SELECT id FROM dbo.branches WHERE code = ''MD01'' AND active = 1);
        DECLARE @ClassCode VARCHAR(50) = ''ATT-MD-DEMO-01'';

        IF @Teacher IS NULL OR @Parent IS NULL OR @Course IS NULL OR @Branch IS NULL
            THROW 51703, ''Run du_lieu_theo_word.sql first; required d26 accounts, Piano course, or branch were not found.'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.courses WHERE id = @Course AND total_sessions = 12)
            THROW 51709, ''The demo requires PIA-CB-12 to have 12 sessions. Existing course was preserved.'', 1;
        IF (SELECT COUNT(*) FROM dbo.students WHERE parent_id = @Parent AND full_name IN
            (N''Nguyễn Bảo An'', N''Nguyễn An Nhiên'', N''Nguyễn Minh Quân'', N''Nguyễn Ngọc Hân'')) <> 4
            THROW 51704, ''Expected four presentation students under d26_parent_new. No attendance data was installed.'', 1;
        IF EXISTS (SELECT 1 FROM dbo.classes WHERE class_code = @ClassCode)
            THROW 51705, ''ATT-MD-DEMO-01 already exists without this install marker. Existing class was preserved.'', 1;

        DECLARE @ClassId BIGINT;
        INSERT dbo.classes(course_id, branch_id, room_id, teacher_id, class_code, class_name, class_type,
            max_students, current_students, start_date, end_date, schedule_description, status)
        VALUES(@Course, @Branch, NULL, @Teacher, @ClassCode, N''Lớp Piano - Demo điểm danh'', ''GROUP'',
            4, 4, DATEADD(DAY, -70, @Today), DATEADD(DAY, 7, @Today),
            N''Lớp dữ liệu test: 10 buổi lịch sử, 1 buổi hôm nay, 1 buổi tương lai.'', ''OPEN'');
        SET @ClassId = SCOPE_IDENTITY();

        INSERT dbo.enrollments(student_id, class_id, registered_by_user_id, enrollment_date, status, notes)
        SELECT s.id, @ClassId, @Parent, @Now, ''ENROLLED'', N''Dữ liệu demo Weekly Schedule / Attendance Tracking''
        FROM dbo.students s
        WHERE s.parent_id = @Parent AND s.full_name IN
            (N''Nguyễn Bảo An'', N''Nguyễn An Nhiên'', N''Nguyễn Minh Quân'', N''Nguyễn Ngọc Hân'');

        DECLARE @Lessons TABLE(session_number INT PRIMARY KEY, lesson_date DATE NOT NULL, lesson_id BIGINT NULL);
        ;WITH N AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM N WHERE n < 10)
        INSERT @Lessons(session_number, lesson_date)
        SELECT n, DATEADD(DAY, -7 * (11 - n), @Today) FROM N
        UNION ALL SELECT 11, @Today
        UNION ALL SELECT 12, DATEADD(DAY, 7, @Today);

        -- Dedicated room: do not occupy or change the Word/D26 demonstration rooms.
        IF EXISTS (SELECT 1 FROM dbo.rooms WHERE room_code = ''ATT-MD-ROOM-01'')
            THROW 51707, ''Reserved attendance room already exists without installation marker.'', 1;
        INSERT dbo.rooms(branch_id, room_code, room_name, capacity, room_type, status, description)
        VALUES(@Branch, ''ATT-MD-ROOM-01'', N''Phòng Piano - Demo chuyên cần'', 4,
            ''PIANO_GROUP'', ''AVAILABLE'', N''Phòng riêng cho lớp ATT-MD-DEMO-01.'');
        DECLARE @RoomId BIGINT = SCOPE_IDENTITY();
        UPDATE dbo.classes SET room_id = @RoomId WHERE id = @ClassId;
        DECLARE @Device INT = 1;
        WHILE @Device <= 4
        BEGIN
            INSERT dbo.equipments(room_id, name, code, category, status, description)
            VALUES(@RoomId, N''Đàn Piano demo số '' + CONVERT(NVARCHAR(2), @Device),
                ''ATT-MD-KEY-'' + CONVERT(VARCHAR(2), @Device), ''KEYBOARD'', ''GOOD'',
                N''Thiết bị mẫu cho lớp điểm danh riêng.'');
            SET @Device += 1;
        END;

        -- Only a lesson date that the teacher/room can accommodate is installed.
        -- The existing Word and absence/makeup lessons remain untouched.
        IF EXISTS (
            SELECT 1 FROM dbo.lessons l JOIN dbo.classes c ON c.id = l.class_id
            JOIN @Lessons d ON d.lesson_date = l.lesson_date
            WHERE l.status <> ''CANCELLED''
              AND COALESCE(l.teacher_id, c.teacher_id) = @Teacher
              AND l.start_time < CONVERT(TIME, ''09:30'')
              AND l.end_time > CONVERT(TIME, ''06:30''))
            THROW 51708, ''Piano teacher conflicts with the demo slot or its 30-minute break. No existing lesson was moved.'', 1;

        INSERT dbo.lessons(class_id, room_id, teacher_id, session_number, lesson_date,
            start_time, end_time, title, lesson_note, status)
        SELECT @ClassId, @RoomId, @Teacher, d.session_number, d.lesson_date,
            ''07:00'', ''09:00'',
            N''Buổi '' + CONVERT(NVARCHAR(2), d.session_number) + N'': Piano thực hành - chuyên cần'',
            CASE WHEN d.session_number <= 10 THEN N''Lịch sử điểm danh mẫu; không phải giao dịch học phí.''
                 WHEN d.session_number = 11 THEN N''Buổi hôm nay: để trống cho giáo viên tự điểm danh.''
                 ELSE N''Buổi tương lai: mọi vai trò chỉ được xem, không được sửa điểm danh.'' END,
            CASE WHEN d.session_number <= 10 THEN ''COMPLETED'' ELSE ''SCHEDULED'' END
        FROM @Lessons d;
        UPDATE d SET lesson_id = l.id
        FROM @Lessons d JOIN dbo.lessons l ON l.class_id = @ClassId AND l.session_number = d.session_number;

        -- Denominator is the existing PIA-CB-12 course (12 sessions).
        -- 0 / 1 / 2 / 3 absences yield NORMAL / NORMAL / WARNING / CRITICAL.
        DECLARE @History TABLE(student_id BIGINT PRIMARY KEY, absent_sessions INT NOT NULL);
        INSERT @History(student_id, absent_sessions)
        SELECT id, CASE full_name WHEN N''Nguyễn Bảo An'' THEN 0 WHEN N''Nguyễn An Nhiên'' THEN 1
                      WHEN N''Nguyễn Minh Quân'' THEN 2 ELSE 3 END
        FROM dbo.students WHERE parent_id = @Parent AND full_name IN
            (N''Nguyễn Bảo An'', N''Nguyễn An Nhiên'', N''Nguyễn Minh Quân'', N''Nguyễn Ngọc Hân'');
        INSERT dbo.attendances(lesson_id, student_id, status, note, marked_at)
        SELECT d.lesson_id, h.student_id,
            CASE WHEN d.session_number <= h.absent_sessions THEN ''ABSENT'' ELSE ''PRESENT'' END,
            CASE WHEN d.session_number <= h.absent_sessions THEN N''Vắng trong dữ liệu chuyên cần mẫu.''
                 ELSE N''Có mặt trong dữ liệu chuyên cần mẫu.'' END,
            DATEADD(HOUR, 9, CONVERT(DATETIME2, d.lesson_date))
        FROM @Lessons d CROSS JOIN @History h WHERE d.session_number <= 10;

        INSERT dbo.attendance_weekly_demo_installations(batch_key, installed_at, class_code,
            first_lesson_date, today_lesson_date, future_lesson_date)
        VALUES(''ATT_WEEKLY_V1'', @Now, @ClassCode, DATEADD(DAY, -70, @Today), @Today, DATEADD(DAY, 7, @Today));
        PRINT N''Đã tạo lớp riêng: 4 học viên, 12 buổi học và 40 bản ghi điểm danh lịch sử.'';
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

SELECT batch_key, class_code, first_lesson_date, today_lesson_date, future_lesson_date
FROM dbo.attendance_weekly_demo_installations WHERE batch_key = ''ATT_WEEKLY_V1'';
SELECT u.username, u.role, N''Test@123456'' AS initial_password
FROM dbo.users u WHERE u.username IN (''d26_staff'',''d26_admin'',''d26_parent_new'',''d26_teacher_piano'',''d26_teacher_other'');
SELECT l.id AS lesson_id, l.session_number, l.lesson_date, l.start_time, l.end_time, l.status,
    (SELECT COUNT(*) FROM dbo.attendances a WHERE a.lesson_id = l.id) AS attendance_records
FROM dbo.lessons l JOIN dbo.classes c ON c.id = l.class_id
WHERE c.class_code = ''ATT-MD-DEMO-01'' ORDER BY l.session_number;
SELECT s.full_name, SUM(CASE WHEN a.status = ''ABSENT'' THEN 1 ELSE 0 END) AS absent_sessions,
    CAST(SUM(CASE WHEN a.status = ''ABSENT'' THEN 1 ELSE 0 END) * 100.0 / 12 AS DECIMAL(5,1)) AS absent_percent
FROM dbo.students s JOIN dbo.enrollments e ON e.student_id = s.id
JOIN dbo.classes c ON c.id = e.class_id
LEFT JOIN dbo.lessons l ON l.class_id = c.id
LEFT JOIN dbo.attendances a ON a.lesson_id = l.id AND a.student_id = s.id
WHERE c.class_code = ''ATT-MD-DEMO-01'' GROUP BY s.id, s.full_name;';
  INSERT dbo.combined_demo_installations(seed_key) VALUES('COMBINED_DEMO_V1');
  PRINT N'Da nap day du du lieu demo tong hop.';
 END
 ELSE PRINT N'Bo du lieu tong hop da duoc nap; giu nguyen du lieu hien tai.';
 -- Additive extension: also runs on databases where COMBINED_DEMO_V1 was installed.
 -- Existing accounts, passwords and payment history are never reset by this extension.
 IF EXISTS(SELECT 1 FROM dbo.users WHERE username='d26_staff_2' AND role<>'STAFF')
  THROW 51802,'d26_staff_2 already exists with a different role; no account was changed.',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.users WHERE username='d26_staff_2')
 BEGIN
  INSERT dbo.users(username,password,full_name,email,phone,role,status,token_version,must_change_password,created_at)
  VALUES('d26_staff_2',
   '$2a$10$U9rS8H8giwtd3tJo7XlXG.tkjsA8opaNfJZqihlijddbmKw2k76Ju',
   N'Nhân viên thu ngân 2','d26.staff2@example.test','0909000022','STAFF','ACTIVE',0,0,SYSDATETIME());
  PRINT N'Da bo sung tai khoan d26_staff_2 de test quyen dieu chinh khoan thu.';
 END;
 SELECT username,full_name,role,status FROM dbo.users WHERE username='d26_staff_2';
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
