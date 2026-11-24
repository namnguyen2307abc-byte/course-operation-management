USE [course_operation_management];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.placement_criteria', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.placement_criteria (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        subject VARCHAR(30) NOT NULL,
        criteria_name NVARCHAR(150) NOT NULL,
        display_order INT NOT NULL DEFAULT 1,
        is_active BIT NOT NULL DEFAULT 1,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
    );
END;

IF OBJECT_ID(N'dbo.placement_schedule_criteria_scores', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.placement_schedule_criteria_scores (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        schedule_id BIGINT NOT NULL,
        criteria_id BIGINT NULL,
        criteria_name_snapshot NVARCHAR(150) NOT NULL,
        score_10 FLOAT NOT NULL,
        display_order INT NOT NULL DEFAULT 1,
        CONSTRAINT FK_placement_schedule_criteria_scores_schedule FOREIGN KEY (schedule_id) REFERENCES dbo.placement_schedules(id),
        CONSTRAINT FK_placement_schedule_criteria_scores_criteria FOREIGN KEY (criteria_id) REFERENCES dbo.placement_criteria(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM dbo.placement_criteria WHERE subject = 'DAN')
BEGIN
    INSERT INTO dbo.placement_criteria (subject, criteria_name, display_order, is_active, created_at) VALUES
    ('DAN', N'1. Cảm âm & Nhạc cảm (Pitch)', 1, 1, SYSDATETIME()),
    ('DAN', N'2. Nhịp phách & Tiết tấu (Rhythm)', 2, 1, SYSDATETIME()),
    ('DAN', N'3. Kỹ thuật ngón & Tư thế tay (Technique)', 3, 1, SYSDATETIME()),
    ('DAN', N'4. Đọc bản nhạc & Thị tấu (Sight Reading)', 4, 1, SYSDATETIME());
END;

IF NOT EXISTS (SELECT 1 FROM dbo.placement_criteria WHERE subject = 'MUA')
BEGIN
    INSERT INTO dbo.placement_criteria (subject, criteria_name, display_order, is_active, created_at) VALUES
    ('MUA', N'1. Độ dẻo & Uyển chuyển (Flexibility)', 1, 1, SYSDATETIME()),
    ('MUA', N'2. Cảm thụ âm nhạc & Nhịp điệu (Musicality)', 2, 1, SYSDATETIME()),
    ('MUA', N'3. Phom dáng & Kỹ thuật động tác (Posture)', 3, 1, SYSDATETIME()),
    ('MUA', N'4. Thần thái & Biểu cảm biểu diễn (Stage Presence)', 4, 1, SYSDATETIME());
END;

IF NOT EXISTS (SELECT 1 FROM dbo.placement_criteria WHERE subject = 'VO')
BEGIN
    INSERT INTO dbo.placement_criteria (subject, criteria_name, display_order, is_active, created_at) VALUES
    ('VO', N'1. Thể lực & Sức bền (Stamina & Strength)', 1, 1, SYSDATETIME()),
    ('VO', N'2. Tốc độ & Phản xạ tự vệ (Reflexes & Speed)', 2, 1, SYSDATETIME()),
    ('VO', N'3. Tấn pháp & Kỹ thuật đòn (Stances & Technique)', 3, 1, SYSDATETIME()),
    ('VO', N'4. Tác phong & Kỷ luật võ đạo (Martial Discipline)', 4, 1, SYSDATETIME());
END;

-- Backfill historical criteria snapshots for existing COMPLETED schedules that were graded before per-criteria tracking
;WITH BaseCriteria AS (
    SELECT id, subject, criteria_name, display_order,
           ROW_NUMBER() OVER (PARTITION BY subject ORDER BY display_order ASC, id ASC) AS rn
    FROM dbo.placement_criteria
)
INSERT INTO dbo.placement_schedule_criteria_scores (schedule_id, criteria_id, criteria_name_snapshot, score_10, display_order)
SELECT
    s.id AS schedule_id,
    c.id AS criteria_id,
    c.criteria_name AS criteria_name_snapshot,
    ROUND(COALESCE(s.score, 0) / 10.0, 1) AS score_10,
    c.display_order
FROM dbo.placement_schedules s
INNER JOIN BaseCriteria c
    ON c.subject = CASE
        WHEN UPPER(LTRIM(RTRIM(COALESCE(s.subject, '')))) IN ('MUA', 'DANCE') THEN 'MUA'
        WHEN UPPER(LTRIM(RTRIM(COALESCE(s.subject, '')))) IN ('VO', 'MARTIAL_ARTS') THEN 'VO'
        ELSE 'DAN'
    END
   AND c.rn <= 4
WHERE s.status = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.placement_schedule_criteria_scores sc
      WHERE sc.schedule_id = s.id
  );

COMMIT TRANSACTION;
GO
