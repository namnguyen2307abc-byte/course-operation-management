[CmdletBinding()]
param([string]$Server='localhost',[string]$Database='course_operation_management',[string]$ApiBase='http://127.0.0.1')
$ErrorActionPreference='Stop'
$workspacePath=Split-Path $PSScriptRoot -Parent
foreach($file in @('tuition_reservations.sql','seed_tuition_reservations.sql')){
 sqlcmd -S $Server -E -C -d $Database -b -f 65001 -i (Join-Path $workspacePath "database/$file")
 if($LASTEXITCODE){throw "Seed failed: $file"}
}
$login=Invoke-RestMethod "$ApiBase/api/auth/login" -Method Post -ContentType application/json -Body '{"username":"qa_hold_staff","password":"Test@123456"}'
$headers=@{Authorization="Bearer $($login.token)"}
$invoices=Invoke-RestMethod "$ApiBase/api/tuition-payment/receivables" -Headers $headers
$demo=$invoices|Where-Object code -eq 'QA-HOLD-05'
$pending=(sqlcmd -S $Server -E -C -d $Database -b -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM invoices i WHERE i.invoice_code='QA-HOLD-05' AND i.notes NOT LIKE '%QR-BLOCK-READY%' AND NOT EXISTS(SELECT 1 FROM tuition_reservation_actions a WHERE a.invoice_id=i.id);")|Out-String
if($pending.Trim() -eq '1'){
 # Create a real unpaid QR, then expire only this dedicated fixture. No receipt is posted.
 try {
  Invoke-RestMethod "$ApiBase/api/tuition-payment/$($demo.id)/payment-intents" -Headers $headers -Method Post -ContentType application/json -Body '{"amount":4000}'|Out-Null
 } catch { Write-Warning 'QR gateway unavailable; an uncertain request may remain for this scenario.' }
 sqlcmd -S $Server -E -C -d $Database -b -Q "SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET ANSI_PADDING ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF; UPDATE i SET reservation_deadline=DATEADD(HOUR,-3,CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATETIME2)), notes=CONCAT(notes,' [QR-BLOCK-READY]') FROM invoices i WHERE invoice_code='QA-HOLD-05' AND notes NOT LIKE '%QR-BLOCK-READY%';"
 if($LASTEXITCODE){throw 'Failed to prepare expired QR fixture.'}
}
Write-Host 'Ready: QA-HOLD-02..09; student [GIU CHO 01] waiting for class assignment.'
Write-Host 'Accounts: qa_hold_parent, qa_hold_other, qa_hold_staff, qa_hold_admin, qa_hold_leader. Password: Test@123456'
