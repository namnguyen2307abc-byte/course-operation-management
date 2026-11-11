[CmdletBinding()]
param([switch]$RestartBackend, [switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'start-backend.ps1') -Restart:$RestartBackend -SkipBuild:$SkipBuild

$frontendReady = $false
try {
    $page = Invoke-WebRequest 'http://127.0.0.1:5500/login.html' -UseBasicParsing -TimeoutSec 3
    $frontendReady = $page.StatusCode -eq 200 -and $page.Content -match 'loginForm'
} catch {}
if (!$frontendReady) {
    if (Get-NetTCPConnection -State Listen -LocalPort 5500 -ErrorAction SilentlyContinue) {
        throw 'Cong 5500 dang duoc ung dung khac su dung. Kiem tra ung dung do truoc khi thu lai.'
    }
    $nodeCommand = Get-Command node.exe -ErrorAction Stop
    $runtimePath = Join-Path $workspacePath '.local-run'
    $frontendProcess = Start-Process -FilePath $nodeCommand.Source `
        -ArgumentList @("`"$(Join-Path $PSScriptRoot 'serve-frontend.cjs')`"") `
        -WorkingDirectory $workspacePath -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $runtimePath 'frontend.log') `
        -RedirectStandardError (Join-Path $runtimePath 'frontend-error.log') -PassThru
    Set-Content -LiteralPath (Join-Path $runtimePath 'frontend.pid') -Value $frontendProcess.Id -Encoding ascii
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        Start-Sleep -Milliseconds 500
        try {
            $page = Invoke-WebRequest 'http://127.0.0.1:5500/login.html' -UseBasicParsing -TimeoutSec 2
            if ($page.StatusCode -eq 200) { $frontendReady = $true; break }
        } catch {}
    }
    if (!$frontendReady) { throw 'Frontend chua san sang. Xem .local-run/frontend-error.log.' }
}
Write-Host 'Frontend da san sang: http://127.0.0.1:5500/login.html'
Write-Host 'Backend da san sang: http://127.0.0.1:8080'
Write-Host 'Cac tien trinh chay nen; dong cua so nay se khong dung ung dung.'
