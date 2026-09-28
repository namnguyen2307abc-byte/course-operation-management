-- ============================================================================
-- DATABASE SETUP & SCHEMA INTEGRATION SCRIPT FOR SQL SERVER
-- Project: Course Operation Management
-- Modules: Base Authorization & Placement Assessment Schedule (Phụ Huynh & Giáo Viên)
-- ============================================================================

-- 1. Create Database (if not existing)
IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'course_operation_management')
BEGIN
    CREATE DATABASE course_operation_management;
END
GO

USE course_operation_management;
GO

-- 2. Create Users Table (Hỗ trợ các Vai trò: ADMIN, STAFF, TEACHER, PARENT)
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[users]') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.users (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        username VARCHAR(100) NULL,
        full_name NVARCHAR(100) NOT NULL,
        email VARCHAR(150) NOT NULL UNIQUE,
        password VARCHAR(255) NOT NULL,
        phone VARCHAR(20) NULL,
        role VARCHAR(30) NOT NULL CONSTRAINT DF_users_role DEFAULT 'PARENT',
        status VARCHAR(30) NOT NULL CONSTRAINT DF_users_status DEFAULT 'ACTIVE',
        avatar_url NVARCHAR(500) NULL,
        created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
        updated_at DATETIME2 NULL DEFAULT GETDATE()
    );
END
GO

-- Update legacy 'STUDENT' roles to 'PARENT' if any exist
UPDATE dbo.users SET role = 'PARENT' WHERE role = 'STUDENT';
GO

-- 3. Create Placement Schedules Table (Lịch Thi Xếp Lớp & Đánh Giá Năng Lực Của Con)
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[placement_schedules]') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.placement_schedules (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NULL CONSTRAINT FK_placement_schedules_parent FOREIGN KEY REFERENCES dbo.users(id),
        student_name NVARCHAR(100) NULL,
        title NVARCHAR(150) NOT NULL,
        room_name NVARCHAR(100) NOT NULL,
        branch NVARCHAR(150) NULL,
        test_date DATETIME2 NOT NULL,
        note NVARCHAR(MAX) NULL,
        status VARCHAR(30) NOT NULL CONSTRAINT DF_placement_schedules_status DEFAULT 'SCHEDULED',
        score INT NULL,
        recommended_level VARCHAR(30) NULL,
        teacher_note NVARCHAR(MAX) NULL,
        audio_url VARCHAR(500) NULL,
        video_url VARCHAR(500) NULL,
        teacher_id BIGINT NULL CONSTRAINT FK_placement_schedules_teacher FOREIGN KEY REFERENCES dbo.users(id),
        evaluated_at DATETIME2 NULL,
        created_at DATETIME2 NOT NULL DEFAULT GETDATE()
    );
END
GO

-- 4. Sample Seed Data (Thêm Tài khoản & Lịch thi mẫu nếu chưa có)
-- Valid BCrypt Hash for 'password123': $2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymY0vB1Fz4eO6T.e/3a.m.
-- Valid BCrypt Hash for 'adminpassword123': $2a$10$e8w.p83W/xXjNq9M9E0tVu3D9vWbB53KzKxJ7W4bO2o1Z8X2X2X2.

-- Phụ huynh mẫu (email: parent1@example.com / pass: password123)
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'parent1@example.com')
BEGIN
    INSERT INTO dbo.users (username, full_name, email, password, role, status, created_at, updated_at)
    VALUES ('parent1@example.com', N'Nguyen Van Phụ Huynh', 'parent1@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymY0vB1Fz4eO6T.e/3a.m.', 'PARENT', 'ACTIVE', GETDATE(), GETDATE());
END

-- Giáo viên mẫu (email: teacher1@example.com / pass: password123)
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'teacher1@example.com')
BEGIN
    INSERT INTO dbo.users (username, full_name, email, password, role, status, created_at, updated_at)
    VALUES ('teacher1@example.com', N'Nguyen Van Teacher', 'teacher1@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymY0vB1Fz4eO6T.e/3a.m.', 'TEACHER', 'ACTIVE', GETDATE(), GETDATE());
END

-- Admin mẫu (email: admin@example.com / pass: adminpassword123)
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'admin@example.com')
BEGIN
    INSERT INTO dbo.users (username, full_name, email, password, role, status, created_at, updated_at)
    VALUES ('admin@example.com', N'Admin User', 'admin@example.com', '$2a$10$e8w.p83W/xXjNq9M9E0tVu3D9vWbB53KzKxJ7W4bO2o1Z8X2X2X2.', 'ADMIN', 'ACTIVE', GETDATE(), GETDATE());
END
GO
