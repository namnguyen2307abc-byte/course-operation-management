[CmdletBinding()]
param([string]$Server = 'localhost', [string]$Database = 'course_operation_management', [string]$ApiBase = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path $PSScriptRoot -Parent
& sqlcmd -S $Server -E -C -d $Database -i (Join-Path $workspacePath 'database/admin_manual_test_fixture.sql') -b -f 65001
if ($LASTEXITCODE -ne 0) { throw 'Khong the tao du lieu test.' }
$installed = & sqlcmd -S $Server -E -C -d $Database -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM admin_audit_logs WHERE action='SEED_TEST_DATA' AND details LIKE N'admin-manual-test-actions-v1:%';" -b
if ($LASTEXITCODE -ne 0) { throw 'Khong kiem tra duoc nhat ky test.' }
if (($installed -join '').Trim() -ne '0') { Write-Host 'Du lieu va nhat ky test da co; giu nguyen cac thay doi ban da test.'; exit 0 }

function Invoke-DemoApi([string]$Path, [string]$Method = 'GET', $Body = $null) {
    $arguments = @{ Uri = $ApiBase + $Path; Method = $Method; ContentType = 'application/json; charset=utf-8' }
    if ($script:demoHeaders) { $arguments.Headers = $script:demoHeaders }
    if ($null -ne $Body) { $arguments.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 5)) }
    Invoke-RestMethod @arguments
}
$login = Invoke-DemoApi '/api/auth/login' 'POST' @{ username = 'qa_admin'; password = 'Test@123456' }
$script:demoHeaders = @{ Authorization = 'Bearer ' + $login.token }
$historyUser = (Invoke-DemoApi '/api/admin/users?keyword=qa_staff_07').items | Where-Object { $_.username -eq 'qa_staff_07' } | Select-Object -First 1
if (!$historyUser) { throw 'Khong tim thay tai khoan test qa_staff_07.' }
$id = $historyUser.id
Invoke-DemoApi "/api/admin/users/$id/status" 'PATCH' @{ status = 'INACTIVE'; reason = '[TEST] Kiem tra khoa tai khoan' } | Out-Null
Invoke-DemoApi "/api/admin/users/$id/status" 'PATCH' @{ status = 'ACTIVE'; reason = '[TEST] Kiem tra mo khoa tai khoan' } | Out-Null
Invoke-DemoApi "/api/admin/users/$id/role" 'PATCH' @{ role = 'BRANCH_MANAGER'; reason = '[TEST] Kiem tra cap quyen quan ly' } | Out-Null
Invoke-DemoApi "/api/admin/users/$id/role" 'PATCH' @{ role = 'STAFF'; reason = '[TEST] Tra ve vai tro nhan vien' } | Out-Null
Invoke-DemoApi "/api/admin/users/$id" 'PUT' @{ fullName = $historyUser.fullName; email = 'qa_staff_07@example.test'; phone = '0908007707'; subject = '' } | Out-Null
$passwordUser = (Invoke-DemoApi '/api/admin/users?keyword=qa_password').items | Where-Object { $_.username -eq 'qa_password' } | Select-Object -First 1
Invoke-DemoApi "/api/admin/users/$($passwordUser.id)/reset-password" 'POST' @{ password = 'Test@123456'; reason = '[TEST] Kiem tra yeu cau doi mat khau khi dang nhap' } | Out-Null
$existing = Invoke-DemoApi '/api/admin/users?keyword=qa_created'
if (!$existing.totalElements) {
    Invoke-DemoApi '/api/admin/users' 'POST' @{ username = 'qa_created'; password = 'Test@123456'; fullName = '[TEST] Tai khoan duoc cap qua API'; role = 'STAFF'; email = 'qa_created@example.test' } | Out-Null
}
& sqlcmd -S $Server -E -C -d $Database -b -Q "SET NOCOUNT ON; INSERT INTO admin_audit_logs (actor_id,actor_username,target_id,target_username,action,details,created_at) SELECT id,username,id,username,'SEED_TEST_DATA',N'admin-manual-test-actions-v1: thuc hien cap tai khoan, doi quyen, khoa/mo khoa, sua thong tin va reset mat khau qua API tren du lieu test.',SYSDATETIME() FROM users WHERE username='qa_admin';"
if ($LASTEXITCODE -ne 0) { throw 'Khong ghi duoc moc hoan tat du lieu test.' }
Write-Host 'Da tao 18 tai khoan qa_ va nhat ky thao tac that. Mat khau test: Test@123456'
