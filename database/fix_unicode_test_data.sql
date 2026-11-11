USE course_operation_management;
GO

SET NOCOUNT ON;

-- 1. CẬP NHẬT TIẾNG VIỆT CHUẨN CHO BẢNG LESSONS
UPDATE lessons 
SET title = N'Buổi 3: Gam Đô trưởng & Hợp âm căn bản',
    lesson_note = N'Thực hành đàn 2 tay kết hợp'
WHERE id = 19;

UPDATE lessons 
SET title = N'Buổi 4: Luyện ngón & Thị tấu tác phẩm Grade 1',
    lesson_note = N'Chuẩn bị sách Czerny Op.599'
WHERE id = 20;

UPDATE lessons 
SET title = N'Buổi 4: Kỹ thuật Legato và Staccato',
    lesson_note = N'Học viên mang tai nghe cá nhân'
WHERE id = 21;

UPDATE lessons 
SET title = N'Buổi 4: Tổ hợp vũ đạo cơ bản & Uốn dẻo',
    lesson_note = N'Trang phục múa ballet đầy đủ'
WHERE id = 22;

-- 2. CẬP NHẬT TIẾNG VIỆT CHUẨN CHO BẢNG ABSENCE_REQUESTS
UPDATE absence_requests
SET reason = N'Bé Nam bị sốt siêu vi, bác sĩ chỉ định nghỉ ngơi 2 ngày. Kính nhờ cô Hương duyệt đơn xin phép nghỉ buổi 4 ngày mai giúp gia đình ạ.'
WHERE id = 8;

UPDATE absence_requests
SET reason = N'Bé Mai Chi có lịch thi đấu đàn cờ vua tại trường ngày 15/10, xin phép cô cho bé nghỉ buổi 4 để tham gia thi đấu.',
    review_note = N'Cô Hương đã nhận được thông tin. Chúc con thi đấu đạt kết quả cao nhé!'
WHERE id = 9;

UPDATE absence_requests
SET reason = N'Bé Hân bị cảm cúm sốt nhẹ, gia đình xin phép cho bé nghỉ buổi 4 ngày 16/10.',
    review_note = N'Cô Linh đã duyệt đơn, chúc con mau khỏe nhé!'
WHERE id = 10;

UPDATE absence_requests
SET reason = N'Gia đình có việc hiếu ở quê nên xin phép nghỉ buổi 3 ngày 12/10.',
    review_note = N'Đã tiếp nhận và duyệt đơn nghỉ.'
WHERE id = 11;

-- 3. CẬP NHẬT TIẾNG VIỆT CHUẨN CHO BẢNG MAKEUP_REGISTRATIONS
UPDATE makeup_registrations
SET note = N'Tự động tạo ca học bù sau khi giáo viên duyệt đơn nghỉ của bé Mai Chi. Chờ giáo vụ xếp lớp.'
WHERE id = 4;

UPDATE makeup_registrations
SET note = N'CHƯA CÓ LỚP PHÙ HỢP: Buổi 4 là buổi uốn dẻo và kiểm tra tổ hợp quan trọng. Hiện chưa có lớp cùng cấp độ. Kính mong phụ huynh đưa bé tham gia lớp gốc nếu có thể, hoặc liên hệ giáo vụ để bảo lưu chuyển khóa sau.'
WHERE id = 5;

UPDATE makeup_registrations
SET note = N'Đã xếp học bù vào ca tối nay của Cô Hà tại phòng P102.'
WHERE id = 6;

PRINT N'--> Đã cập nhật toàn bộ tiếng Việt có dấu chuẩn Unicode cho dữ liệu test!';
GO
