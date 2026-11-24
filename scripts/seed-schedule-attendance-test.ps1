<#
.SYNOPSIS
    Tu dong nap bo du lieu test toan dien cho cac tinh nang cua phan he:
    - Thoi khoa bieu tuan (Weekly Schedule)
    - Diem danh buoi hoc (Lesson Attendance, Time-Lock, Roster Guard, Admin Override)
    - Chuyen can 3 cap do (Normal, Warning, Critical)
    - Hoc bu & Quy tac khoa sau lan vang bu dau (Pending, Scheduled, Completed, Cancelled/Locked)
    - Chuong thong bao (Bell Notifications)

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
$sqlFile = Join-Path $rootPath 'database\seed_full_attendance_schedule_test_cases.sql'

if (-not (Test-Path $sqlFile)) {
    throw "Khong tim thay file SQL: $sqlFile"
}

Write-Host ">>> Dang nap du lieu test toan dien vao SQL Server [$Server / $Database]..." -ForegroundColor Cyan

& sqlcmd -S $Server -E -C -d $Database -b -f 65001 -i $sqlFile

if ($LASTEXITCODE -ne 0) {
    throw "Loi thuc thi nap du lieu SQL; transaction da rollback."
}

Write-Host "`n>>> NAP DU LIEU TEST TOAN DIEN THANH CONG!" -ForegroundColor Green
Write-Host "Cac tai khoan mau de dang nhap kiem thu (Mat khau mac dinh: 123456):" -ForegroundColor Yellow
Write-Host "  1. Quan tri vien: admin" -ForegroundColor White
Write-Host "  2. Nhan vien giao vu: staff_minh / cashier_mai" -ForegroundColor White
Write-Host "  3. Giao vien Piano: teacher_huong" -ForegroundColor White
Write-Host "  4. Giao vien Guitar: teacher_tuan" -ForegroundColor White
Write-Host "  5. Phu huynh: parent_lan" -ForegroundColor White
