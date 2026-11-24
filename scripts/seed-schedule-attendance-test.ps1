<#
.SYNOPSIS
    Tự động nạp bộ dữ liệu test toàn diện cho các tính năng của phân hệ:
    - Thời khóa biểu tuần (Weekly Schedule)
    - Điểm danh buổi học (Lesson Attendance, Time-Lock, Roster Guard, Admin Override)
    - Chuyên cần 3 cấp độ (Normal, Warning, Critical)
    - Học bù & Quy tắc khóa sau lần vắng bù đầu (Pending, Scheduled, Completed, Cancelled/Locked)
    - Chuông thông báo (Bell Notifications tiếng Việt chuẩn)

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/seed-schedule-attendance-test.ps1
#>

[CmdletBinding()]
param(
    [string]$Server = 'localhost',
    [string]$Database = 'course_operation_management'
)

$ErrorActionPreference = 'Stop'
$rootPath = Split-Path $PSScriptRoot -Parent
$sqlFile = Join-Path $rootPath 'database/seed_full_attendance_schedule_test_cases.sql'

if (-not (Test-Path $sqlFile)) {
    throw "Không tìm thấy file SQL: $sqlFile"
}

Write-Host ">>> Đang nạp dữ liệu test toàn diện vào SQL Server [$Server / $Database]..." -ForegroundColor Cyan

& sqlcmd -S $Server -E -C -d $Database -b -f 65001 -i $sqlFile

if ($LASTEXITCODE -ne 0) {
    throw "Lỗi thực thi nạp dữ liệu SQL; transaction đã rollback."
}

Write-Host "`n>>> NẠP DỮ LIỆU TEST TOÀN DIỆN THÀNH CÔNG!" -ForegroundColor Green
Write-Host "Các tài khoản mẫu để đăng nhập kiểm thử (Mật khẩu mặc định: 123456):" -ForegroundColor Yellow
Write-Host "  1. Quản trị viên: admin" -ForegroundColor White
Write-Host "  2. Nhân viên giáo vụ: staff_minh / cashier_mai" -ForegroundColor White
Write-Host "  3. Giáo viên Piano: teacher_huong" -ForegroundColor White
Write-Host "  4. Giáo viên Guitar: teacher_tuan" -ForegroundColor White
Write-Host "  5. Phụ huynh (có 4 con): parent_lan" -ForegroundColor White
