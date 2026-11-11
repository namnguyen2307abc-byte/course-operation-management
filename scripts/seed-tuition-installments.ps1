[CmdletBinding()]
param([string]$Server='localhost',[string]$Database='course_operation_management',[string]$ApiBase='http://127.0.0.1')
$ErrorActionPreference='Stop'
$workspacePath=Split-Path $PSScriptRoot -Parent
sqlcmd -S $Server -E -C -d $Database -b -f 65001 -i (Join-Path $workspacePath 'database/seed_tuition_installments.sql')
if($LASTEXITCODE){throw 'Không tạo được bộ demo.'}
$login=Invoke-RestMethod "$ApiBase/api/auth/login" -Method Post -ContentType application/json -Body (@{username='qa_inst_staff';password='Test@123456'}|ConvertTo-Json -Compress)
$headers=@{Authorization="Bearer $($login.token)"}
$today=[TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow,'SE Asia Standard Time').Date
$invoices=Invoke-RestMethod "$ApiBase/api/tuition-payment/receivables" -Headers $headers
foreach($invoice in $invoices){
 if($invoice.code -notmatch '^QA-INST-INV-[1-4]$'){continue}
 if($invoice.paid -eq 0 -and $invoice.mode -eq 'FULL'){
  $plan=@{installments=@(
   @{amount=3000000;date=$today.ToString('yyyy-MM-dd')},
   @{amount=1500000;date=$today.AddDays(30).ToString('yyyy-MM-dd')},
   @{amount=1500000;date=$today.AddDays(60).ToString('yyyy-MM-dd')}
  )}|ConvertTo-Json -Depth 5
  Invoke-RestMethod "$ApiBase/api/tuition-payment/$($invoice.id)/plan" -Headers $headers -Method Post -ContentType application/json -Body $plan|Out-Null
 }
 if($invoice.code -in @('QA-INST-INV-2','QA-INST-INV-3') -and $invoice.paid -eq 0){
  $key="seed-first-"+$invoice.code.Substring($invoice.code.Length-1)
  $body=@{amount=3000000;cashGiven=3000000;idempotencyKey=$key}|ConvertTo-Json
  Invoke-RestMethod "$ApiBase/api/tuition-payment/$($invoice.id)/collections" -Headers $headers -Method Post -ContentType application/json -Body $body|Out-Null
 }
}
# Only the untouched demo's second installment becomes overdue. Reviewed schedules are preserved.
sqlcmd -S $Server -E -C -d $Database -b -Q "SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET ANSI_PADDING ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF; UPDATE d SET due_date=DATEADD(DAY,-9,CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATE)) FROM tuition_installments d JOIN tuition_plans p ON p.id=d.plan_id JOIN invoices i ON i.id=p.invoice_id WHERE i.invoice_code='QA-INST-INV-2' AND d.sequence_number=2 AND d.due_date=DATEADD(DAY,30,CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATE)) AND NOT EXISTS(SELECT 1 FROM tuition_plan_changes c WHERE c.plan_id=p.id);"
if($LASTEXITCODE){throw 'Không chuẩn bị được kỳ demo quá hạn.'}
Write-Host 'Demo sẵn sàng: qa_inst_parent / qa_inst_other / qa_inst_staff / qa_inst_admin, mật khẩu Test@123456.'
