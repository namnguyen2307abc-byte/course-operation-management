[CmdletBinding()]
param([switch]$Restart, [switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path $PSScriptRoot -Parent
$runtimePath = Join-Path $workspacePath '.local-run'
$jarPath = Join-Path $runtimePath 'talent-management.jar'
$pidPath = Join-Path $runtimePath 'backend.pid'
$stdoutPath = Join-Path $runtimePath 'backend.log'
$stderrPath = Join-Path $runtimePath 'backend-error.log'
New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null

function Test-BackendReady {
    try {
        $response = Invoke-WebRequest 'http://127.0.0.1:8080/api/branches/options' -UseBasicParsing -TimeoutSec 3
        return $response.StatusCode -eq 200 -and $response.Headers['Content-Type'] -match 'application/json'
    } catch { return $false }
}

if ($Restart -and (Test-Path -LiteralPath $pidPath)) {
    $backendProcessId = [int](Get-Content -LiteralPath $pidPath -Raw).Trim()
    $backendProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$backendProcessId" -ErrorAction SilentlyContinue
    if ($backendProcess -and $backendProcess.Name -match '^java(w)?\.exe$' -and
        $backendProcess.CommandLine.Contains($jarPath)) {
        Stop-Process -Id $backendProcessId
        Wait-Process -Id $backendProcessId -Timeout 15 -ErrorAction SilentlyContinue
    }
}
if (Test-BackendReady) {
    Write-Host 'Backend da san sang: http://127.0.0.1:8080'
    return
}
if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) {
    throw 'Cong 8080 dang duoc tien trinh khac su dung hoac backend chua san sang. Kiem tra tien trinh do truoc khi thu lai.'
}

$jdkCandidates = @($env:JAVA_HOME, 'C:\Program Files\Java\jdk-17', 'C:\Program Files\Java\jdk-21') |
    Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin/javac.exe')) }
$selectedJdk = $null
foreach ($candidate in $jdkCandidates) {
    $releasePath = Join-Path $candidate 'release'
    if ((Test-Path -LiteralPath $releasePath) -and
        (Get-Content -LiteralPath $releasePath -Raw) -match 'JAVA_VERSION="(17|21)\.') {
        $selectedJdk = $candidate
        break
    }
}
if (!$selectedJdk) { throw 'Can cai JDK 17 hoac 21 va dat JAVA_HOME tro den JDK do.' }

if (!$SkipBuild) {
    $oldJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $selectedJdk
        $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        $mavenPath = if ($mavenCommand) { $mavenCommand.Source } else { Join-Path $workspacePath 'com_be/mvnw.cmd' }
        $buildPath = Join-Path $runtimePath 'build'
        Write-Host 'Dang build backend bang JDK 17/21 trong thu muc rieng...'
        & $mavenPath -f (Join-Path $workspacePath 'com_be/pom.xml') "-Dtalent.build.directory=$buildPath" '-DskipTests' package
        if ($LASTEXITCODE -ne 0) { throw 'Build backend that bai. Xem loi Maven phia tren.' }
        Copy-Item -LiteralPath (Join-Path $buildPath 'talent-management-0.0.1-SNAPSHOT.jar') -Destination $jarPath -Force
    } finally { $env:JAVA_HOME = $oldJavaHome }
}
if (!(Test-Path -LiteralPath $jarPath)) { throw 'Chua co backend jar. Hay chay lai khong kem -SkipBuild.' }

# A packaged process survives the terminal turn and is isolated from IDE output.
# Existing database only: no startup seed and no automatic schema changes.
$backendProcess = Start-Process -FilePath (Join-Path $selectedJdk 'bin/java.exe') `
    -ArgumentList @('-Dspring.devtools.restart.enabled=false', '-jar', "`"$jarPath`"", '--server.port=8080',
        '--app.seed.enabled=false', '--spring.jpa.hibernate.ddl-auto=validate') `
    -WorkingDirectory (Join-Path $workspacePath 'com_be') -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
Set-Content -LiteralPath $pidPath -Value $backendProcess.Id -Encoding ascii

$ready = $false
for ($attempt = 0; $attempt -lt 45; $attempt++) {
    $backendProcess.Refresh()
    if ($backendProcess.HasExited) { break }
    if (Test-BackendReady) { $ready = $true; break }
    Start-Sleep -Seconds 1
}
if (!$ready) {
    if (Test-Path -LiteralPath $stdoutPath) { Get-Content -LiteralPath $stdoutPath -Tail 18 }
    if (Test-Path -LiteralPath $stderrPath) { Get-Content -LiteralPath $stderrPath -Tail 8 }
    throw "Backend chua san sang. Xem $stdoutPath va $stderrPath."
}
Write-Host "Backend da ket noi database va san sang (PID $($backendProcess.Id)): http://127.0.0.1:8080"
Write-Host "Log: $stdoutPath"
