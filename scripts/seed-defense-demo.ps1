[CmdletBinding()]
param([string]$Tag = ('HD' + (Get-Date -Format 'yyMMdd')),
      [string]$Server = 'localhost', [string]$Database = 'course_operation_management')
$ErrorActionPreference='Stop'
if($Tag -cnotmatch '^[A-Z0-9]{6,18}$'){throw 'Tag must contain 6-18 uppercase ASCII letters/digits.'}
$rootPath=Split-Path $PSScriptRoot -Parent
& sqlcmd -S $Server -E -C -d $Database -b -f 65001 -i (Join-Path $rootPath 'database/seed_defense_demo.sql') -v "DemoTag=$Tag"
if($LASTEXITCODE -ne 0){throw 'Demo seed failed; transaction rolled back.'}
& sqlcmd -S $Server -E -C -d $Database -b -f 65001 -i (Join-Path $rootPath 'database/seed_defense_warning.sql') -v "DemoTag=$Tag"
if($LASTEXITCODE -ne 0){throw 'Warning fixture failed; transaction rolled back.'}
Write-Host "Demo: $Tag. Synthetic accounts: $($Tag.ToLower())_parent / _staff / _teacher / _teacher2 / _admin / _leader / _other / _student. Password: Test@123456"
