-- Demo facilities for the subjects currently offered: Piano, Guitar, Chess, Swimming.
-- Safe to run again: branch + room code and equipment code identify seed rows.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @Rooms TABLE (
    branch_code VARCHAR(50), room_code VARCHAR(50), room_name NVARCHAR(150),
    capacity INT, room_type VARCHAR(50), description NVARCHAR(500)
);
INSERT INTO @Rooms VALUES
('CS01', 'DEMO-GUI-01', N'Phòng Guitar Nhóm 1', 10, 'GUITAR_ROOM', N'Phòng luyện guitar tại Cầu Giấy, có đàn và thiết bị âm thanh.'),
('CS02', 'DEMO-GUI-02', N'Phòng Guitar Nhóm 2', 10, 'GUITAR_ROOM', N'Phòng thực hành guitar nhóm, có giá nhạc và âm ly.'),
('CS01', 'DEMO-CHS-02', N'Phòng Cờ Vua Chiến Thuật 2', 12, 'CHESS_ROOM', N'Bàn cờ thi đấu và bảng phân tích thế cờ.'),
('CS02', 'DEMO-CHS-03', N'Phòng Cờ Vua Đống Đa', 12, 'CHESS_ROOM', N'Phòng học cờ vua với bàn cờ và đồng hồ thi đấu.'),
('CS01', 'DEMO-SWM-03', N'Bể Bơi Cơ Bản Cầu Giấy', 12, 'SWIMMING_POOL', N'Khu học bơi cơ bản có phao và dây phân làn.'),
('CS02', 'DEMO-SWM-01', N'Bể Bơi Cơ Bản - Làn 1', 12, 'SWIMMING_POOL', N'Khu học bơi nhập môn có phao và dụng cụ hỗ trợ.'),
('CS02', 'DEMO-SWM-02', N'Bể Bơi Kỹ Thuật - Làn 2', 12, 'SWIMMING_POOL', N'Khu học bơi kỹ thuật với dây phân làn và bảng tập.' );

IF EXISTS (
    SELECT 1 FROM @Rooms s
    WHERE NOT EXISTS (SELECT 1 FROM dbo.branches b WHERE b.code = s.branch_code)
)
    THROW 50001, 'Missing branch for facility demo seed', 1;

INSERT INTO dbo.rooms (branch_id, room_code, room_name, capacity, room_type, status, description)
SELECT b.id, s.room_code, s.room_name, s.capacity, s.room_type, 'AVAILABLE', s.description
FROM @Rooms s
JOIN dbo.branches b ON b.code = s.branch_code
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.rooms r WHERE r.branch_id = b.id AND r.room_code = s.room_code
);

DECLARE @Equipment TABLE (
    branch_code VARCHAR(50), room_code VARCHAR(50), code VARCHAR(50),
    name NVARCHAR(150), category VARCHAR(50), description NVARCHAR(500)
);
INSERT INTO @Equipment VALUES
('CS01', 'P101', 'DEMO-PIA-MET01', N'Máy đếm nhịp điện tử', 'OTHER', N'Luyện giữ nhịp khi học Piano cá nhân.'),
('CS01', 'P102', 'DEMO-PIA-PED01', N'Bàn đạp sustain Piano', 'OTHER', N'Bàn đạp hỗ trợ lớp Piano nhóm.'),
('CS02', 'P202', 'DEMO-PIA-HEAD01', N'Tai nghe luyện đàn', 'AUDIO', N'Tai nghe cho học viên thực hành Piano.'),
('CS01', 'DEMO-GUI-01', 'DEMO-GUI-GTR01', N'Đàn Guitar Acoustic Cầu Giấy', 'GUITAR', N'Đàn thực hành cho lớp Guitar tại Cầu Giấy.'),
('CS01', 'DEMO-GUI-01', 'DEMO-GUI-TUN02', N'Máy lên dây Guitar Cầu Giấy', 'OTHER', N'Máy lên dây chromatic cho lớp Guitar.'),
('CS01', 'DEMO-GUI-01', 'DEMO-GUI-AMP02', N'Âm ly Guitar Cầu Giấy', 'AUDIO', N'Âm ly dùng khi thực hành nhóm.'),
('CS02', 'P201', 'DEMO-GUI-TUN01', N'Máy lên dây Guitar', 'OTHER', N'Máy lên dây chromatic cho lớp Guitar.'),
('CS02', 'P201', 'DEMO-GUI-AMP01', N'Âm ly Guitar', 'AUDIO', N'Âm ly dùng khi luyện tập nhóm.'),
('CS02', 'DEMO-GUI-02', 'DEMO-GUI-GTR02', N'Đàn Guitar Acoustic', 'GUITAR', N'Đàn thực hành cho học viên.'),
('CS02', 'DEMO-GUI-02', 'DEMO-GUI-CAP02', N'Capo Guitar', 'OTHER', N'Capo hỗ trợ chuyển tông khi đệm hát.'),
('CS01', 'TEST-CHESS-ROOM', 'DEMO-CHS-SET01', N'Bộ cờ vua thi đấu 1', 'CHESS_SET', N'Bàn và quân cờ tiêu chuẩn.'),
('CS01', 'TEST-CHESS-ROOM', 'DEMO-CHS-CLK01', N'Đồng hồ cờ vua 1', 'OTHER', N'Đồng hồ bấm giờ cho ván đấu.'),
('CS01', 'DEMO-CHS-02', 'DEMO-CHS-SET02', N'Bộ cờ vua thi đấu 2', 'CHESS_SET', N'Bàn và quân cờ cho lớp chiến thuật.'),
('CS01', 'DEMO-CHS-02', 'DEMO-CHS-BOARD02', N'Bảng phân tích thế cờ', 'OTHER', N'Bảng hướng dẫn khai cuộc và tàn cuộc.'),
('CS02', 'DEMO-CHS-03', 'DEMO-CHS-SET03', N'Bộ cờ vua Đống Đa', 'CHESS_SET', N'Bàn và quân cờ tiêu chuẩn.'),
('CS02', 'DEMO-CHS-03', 'DEMO-CHS-CLK03', N'Đồng hồ cờ vua Đống Đa', 'OTHER', N'Đồng hồ bấm giờ cho ván đấu.'),
('CS02', 'DEMO-CHS-03', 'DEMO-CHS-BOARD03', N'Bảng phân tích cờ Đống Đa', 'OTHER', N'Bảng hướng dẫn chiến thuật.'),
('CS01', 'DEMO-SWM-03', 'DEMO-SWM-BOARD03', N'Bảng tập bơi Cầu Giấy', 'SPORTS_EQUIPMENT', N'Bảng tập nổi cho học viên mới.'),
('CS01', 'DEMO-SWM-03', 'DEMO-SWM-BUOY03', N'Phao cứu hộ Cầu Giấy', 'SPORTS_EQUIPMENT', N'Phao an toàn đặt cạnh bể.'),
('CS01', 'DEMO-SWM-03', 'DEMO-SWM-LANE03', N'Dây phân làn bơi Cầu Giấy', 'SPORTS_EQUIPMENT', N'Dây phân khu tập luyện.'),
('CS02', 'DEMO-SWM-01', 'DEMO-SWM-BOARD01', N'Bảng tập bơi cơ bản', 'SPORTS_EQUIPMENT', N'Bảng tập nổi cho học viên mới.'),
('CS02', 'DEMO-SWM-01', 'DEMO-SWM-BUOY01', N'Phao cứu hộ khu bơi 1', 'SPORTS_EQUIPMENT', N'Phao an toàn đặt cạnh bể.'),
('CS02', 'DEMO-SWM-01', 'DEMO-SWM-LANE01', N'Dây phân làn bơi 1', 'SPORTS_EQUIPMENT', N'Dây phân khu tập luyện.'),
('CS02', 'DEMO-SWM-02', 'DEMO-SWM-BOARD02', N'Bảng tập kỹ thuật bơi', 'SPORTS_EQUIPMENT', N'Bảng tập tay và chân.'),
('CS02', 'DEMO-SWM-02', 'DEMO-SWM-BUOY02', N'Phao cứu hộ khu bơi 2', 'SPORTS_EQUIPMENT', N'Phao an toàn đặt cạnh bể.'),
('CS02', 'DEMO-SWM-02', 'DEMO-SWM-LANE02', N'Dây phân làn bơi 2', 'SPORTS_EQUIPMENT', N'Dây phân khu tập kỹ thuật.');

IF EXISTS (
    SELECT 1 FROM @Equipment s
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.rooms r JOIN dbo.branches b ON b.id = r.branch_id
        WHERE b.code = s.branch_code AND r.room_code = s.room_code
    )
)
    THROW 50002, 'Missing room for facility demo equipment', 1;

INSERT INTO dbo.equipments (room_id, name, code, category, status, description)
SELECT r.id, s.name, s.code, s.category, 'GOOD', s.description
FROM @Equipment s
JOIN dbo.branches b ON b.code = s.branch_code
JOIN dbo.rooms r ON r.branch_id = b.id AND r.room_code = s.room_code
WHERE NOT EXISTS (SELECT 1 FROM dbo.equipments e WHERE e.code = s.code);

COMMIT TRANSACTION;
