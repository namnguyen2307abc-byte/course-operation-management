-- =================================================================================
-- SCRIPT THÊM DỮ LIỆU CÁC HÓA ĐƠN CHỜ THU PHÍ (UNPAID) ĐỂ THU NGÂN / ADMIN TEST TAY
-- =================================================================================
USE course_operation_management;
GO

-- 1. Thêm Phụ huynh mới phục vụ test
IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'parent_bich')
BEGIN
    INSERT INTO users (username, password, full_name, email, phone, role, status) VALUES
    ('parent_bich', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Phạm Thị Bích (Phụ Huynh)', 'bich.pham@gmail.com', '0912889911', 'PARENT', 'ACTIVE');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'parent_nam')
BEGIN
    INSERT INTO users (username, password, full_name, email, phone, role, status) VALUES
    ('parent_nam', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Vũ Văn Nam (Phụ Huynh)', 'nam.vu@gmail.com', '0988776655', 'PARENT', 'ACTIVE');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'parent_trang')
BEGIN
    INSERT INTO users (username, password, full_name, email, phone, role, status) VALUES
    ('parent_trang', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.oW.x2FfUePcm81wO8Xm1N6B2W.x.lG', N'Hoàng Thu Trang (Phụ Huynh)', 'trang.hoang@gmail.com', '0936112233', 'PARENT', 'ACTIVE');
END
GO

-- 2. Thêm Học viên mới
DECLARE @ParentBichId BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'parent_bich');
DECLARE @ParentNamId BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'parent_nam');
DECLARE @ParentTrangId BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'parent_trang');
DECLARE @ParentLanId BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'parent_lan');

IF NOT EXISTS (SELECT 1 FROM students WHERE full_name = N'Đỗ Minh Khang (Bé Bon)')
BEGIN
    INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes) VALUES
    (@ParentBichId, N'Đỗ Minh Khang (Bé Bon)', '2019-09-12', N'Nam', N'Mầm non Sasuke Cầu Giấy', N'Bé nhanh nhẹn, thích học đàn Piano mầm non');
END

IF NOT EXISTS (SELECT 1 FROM students WHERE full_name = N'Vũ Quỳnh Anh (Bé Bống)')
BEGIN
    INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes) VALUES
    (@ParentNamId, N'Vũ Quỳnh Anh (Bé Bống)', '2018-04-25', N'Nữ', N'Tiểu học Kim Đồng', N'Đã qua kiểm tra đầu vào năng khiếu Piano Grade 1');
END

IF NOT EXISTS (SELECT 1 FROM students WHERE full_name = N'Lê Tuấn Kiệt (Bé Ken)')
BEGIN
    INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes) VALUES
    (@ParentTrangId, N'Lê Tuấn Kiệt (Bé Ken)', '2017-11-08', N'Nam', N'Tiểu học Dịch Vọng A', N'Học làm quen phím đàn và xướng âm');
END

IF NOT EXISTS (SELECT 1 FROM students WHERE full_name = N'Phạm Gia Huy (Bé Tom)')
BEGIN
    INSERT INTO students (parent_id, full_name, date_of_birth, gender, school_name, notes) VALUES
    (@ParentLanId, N'Phạm Gia Huy (Bé Tom)', '2018-06-18', N'Nam', N'Tiểu học Nghĩa Tân', N'Đăng ký khóa học Piano 1-1 rèn luyện kỹ năng');
END
GO

-- 3. Tạo Enrollments và Invoices chờ thu phí (Trạng thái UNPAID)
DECLARE @StudentBonId BIGINT = (SELECT TOP 1 id FROM students WHERE full_name = N'Đỗ Minh Khang (Bé Bon)');
DECLARE @StudentBongId BIGINT = (SELECT TOP 1 id FROM students WHERE full_name = N'Vũ Quỳnh Anh (Bé Bống)');
DECLARE @StudentKenId BIGINT = (SELECT TOP 1 id FROM students WHERE full_name = N'Lê Tuấn Kiệt (Bé Ken)');
DECLARE @StudentTomId BIGINT = (SELECT TOP 1 id FROM students WHERE full_name = N'Phạm Gia Huy (Bé Tom)');
DECLARE @CashierId BIGINT = (SELECT TOP 1 id FROM users WHERE username = 'cashier_mai');

-- Hóa đơn 1: Bé Bon - Lớp Piano Mầm Non (3.600.000 VNĐ)
IF NOT EXISTS (SELECT 1 FROM invoices WHERE invoice_code = 'INV-2026-011')
BEGIN
    INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes) VALUES
    (@StudentBonId, 2, @CashierId, 'PENDING_PAYMENT', N'Đăng ký học viên mới - Chờ phụ huynh đóng học phí tại quầy');
    DECLARE @Enr11 BIGINT = SCOPE_IDENTITY();

    INSERT INTO invoices (invoice_code, student_id, enrollment_id, original_amount, discount_type, discount_amount, discount_reason, final_amount, status, due_date, notes) VALUES
    ('INV-2026-011', @StudentBonId, @Enr11, 3600000.00, 'NONE', 0.00, NULL, 3600000.00, 'UNPAID', DATEADD(month, 1, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí khóa Piano Mầm Non Nhóm Sáng Thứ 7');
END

-- Hóa đơn 2: Bé Bống - Lớp Piano Sơ Cấp 1-1 (4.800.000 VNĐ)
IF NOT EXISTS (SELECT 1 FROM invoices WHERE invoice_code = 'INV-2026-012')
BEGIN
    INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes) VALUES
    (@StudentBongId, 1, @CashierId, 'PENDING_PAYMENT', N'Đăng ký học viên mới - Chờ quét mã PayOS VietQR tự động');
    DECLARE @Enr12 BIGINT = SCOPE_IDENTITY();

    INSERT INTO invoices (invoice_code, student_id, enrollment_id, original_amount, discount_type, discount_amount, discount_reason, final_amount, status, due_date, notes) VALUES
    ('INV-2026-012', @StudentBongId, @Enr12, 4800000.00, 'NONE', 0.00, NULL, 4800000.00, 'UNPAID', DATEADD(month, 1, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí khóa Piano Sơ Cấp (Grade 1)');
END

-- Hóa đơn 3: Bé Ken - Lớp Piano Mầm Non (3.600.000 VNĐ)
IF NOT EXISTS (SELECT 1 FROM invoices WHERE invoice_code = 'INV-2026-013')
BEGIN
    INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes) VALUES
    (@StudentKenId, 2, @CashierId, 'PENDING_PAYMENT', N'Đăng ký học viên mới - Chờ phụ huynh hoàn tất nộp phí');
    DECLARE @Enr13 BIGINT = SCOPE_IDENTITY();

    INSERT INTO invoices (invoice_code, student_id, enrollment_id, original_amount, discount_type, discount_amount, discount_reason, final_amount, status, due_date, notes) VALUES
    ('INV-2026-013', @StudentKenId, @Enr13, 3600000.00, 'NONE', 0.00, NULL, 3600000.00, 'UNPAID', DATEADD(month, 1, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí lớp Piano Mầm Non Cảm Thụ Âm Nhạc');
END

-- Hóa đơn 4: Bé Tom - Lớp Piano 1-1 (4.800.000 VNĐ)
IF NOT EXISTS (SELECT 1 FROM invoices WHERE invoice_code = 'INV-2026-014')
BEGIN
    INSERT INTO enrollments (student_id, class_id, registered_by_user_id, status, notes) VALUES
    (@StudentTomId, 1, @CashierId, 'PENDING_PAYMENT', N'Đăng ký học viên mới - Chờ nộp tiền mặt tại quầy hoặc PayOS');
    DECLARE @Enr14 BIGINT = SCOPE_IDENTITY();

    INSERT INTO invoices (invoice_code, student_id, enrollment_id, original_amount, discount_type, discount_amount, discount_reason, final_amount, status, due_date, notes) VALUES
    ('INV-2026-014', @StudentTomId, @Enr14, 4800000.00, 'NONE', 0.00, NULL, 4800000.00, 'UNPAID', DATEADD(month, 1, CAST(CURRENT_TIMESTAMP AS DATE)), N'Học phí lớp Piano 1-1 Bé Tom');
END
GO

PRINT N'Thêm thành công các hóa đơn chờ thu phí để kiểm thử!';
