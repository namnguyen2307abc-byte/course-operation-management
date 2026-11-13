[CmdletBinding()]
param([string]$Tag='HD261111',[string]$BaseUrl='http://127.0.0.1')
$ErrorActionPreference='Stop'
if($Tag -cnotmatch '^[A-Z0-9]{6,18}$'){throw 'Invalid tag'}
$headersByRole=@{}
foreach($suffix in @('admin','staff','leader','teacher','teacher2','parent','other','student')){
 $body=@{username="$($Tag.ToLower())_$suffix";password='Test@123456'}|ConvertTo-Json
 $login=Invoke-RestMethod "$BaseUrl/api/auth/login" -Method Post -ContentType application/json -Body $body
 if(!$login.token){throw "Login failed: $suffix"}
 $headersByRole[$suffix]=@{Authorization="Bearer $($login.token)"}
}
function Read-DemoApi($path,$role){
 try {$result=Invoke-RestMethod "$BaseUrl$path" -Headers $headersByRole[$role]} catch {throw "Demo API failed: $role $path : $($_.Exception.Message)"}
 Write-Output $result
}
$children=@(Read-DemoApi '/api/course-enrollment/children' 'parent')
if(@($children|Where-Object {$_.fullName.StartsWith("[$Tag-")}).Count -ne 14){throw 'Parent fixture must have 14 children'}
$others=@(Read-DemoApi '/api/course-enrollment/children' 'other')
if($others.Count -ne 1){throw 'Other family isolation failed'}
$receivables=@(Read-DemoApi '/api/tuition-payment/receivables' 'staff'|Where-Object {$_.code.StartsWith($Tag)})
if($receivables.Count -ne 11){throw 'Expected 11 initial invoice fixtures; skip this check after manual demo progresses'}
$overdue=$receivables|Where-Object code -eq "$Tag-INV-6"
if($overdue.paid -ne 3000000 -or $overdue.balance -ne 3000000){throw 'Overdue invoice amounts incorrect'}
$children4=$children|Where-Object {$_.fullName.StartsWith("[$Tag-04]")}
$children5=$children|Where-Object {$_.fullName.StartsWith("[$Tag-05]")}
$courseRows=Read-DemoApi '/api/course-enrollment/courses' 'staff'
$course=$courseRows|Where-Object code -eq "$Tag-P1"
$classes=Read-DemoApi "/api/class-management/classes/page?size=10&keyword=$Tag" 'admin'
$live=$classes.items|Where-Object classCode -eq "$Tag-B"
$progress=Read-DemoApi "/api/schedule/attendance-tracking?studentId=$($children5.id)&classId=$($live.id)" 'parent'
if($progress.absentCount -ne 3 -or $progress.warningLevel -ne 'CRITICAL'){throw 'Expected three absences and critical warning'}
$middleChild=$children|Where-Object {$_.fullName.StartsWith("[$Tag-15]")}
$middleClass=$classes.items|Where-Object classCode -eq "$Tag-W"
$middle=Read-DemoApi "/api/schedule/attendance-tracking?studentId=$($middleChild.id)&classId=$($middleClass.id)" 'parent'
if($middle.warningLevel -ne 'WARNING' -or $middle.absentRate -ne 15){throw 'Middle attendance warning is incorrect'}
$date=[TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow,'SE Asia Standard Time').ToString('yyyy-MM-dd')
$schedule=Read-DemoApi "/api/schedule/weekly?date=$date" 'teacher2'
$target=$schedule.lessons|Where-Object classCode -eq "$Tag-C"|Select-Object -First 1
if(!$target){throw 'No today makeup lesson found; this dataset is date-relative'}
$sheet=Read-DemoApi "/api/schedule/lessons/$($target.lessonId)/attendance" 'teacher2'
if(!@($sheet.students|Where-Object {$_.fullName.StartsWith("[$Tag-08]")}).Count){throw 'Makeup student is absent from roster'}
$absence=Read-DemoApi '/api/attendance-makeup/absence-requests' 'parent'
if(@($absence|Where-Object {$_.studentName.StartsWith("[$Tag-")}).Count -ne 4){throw 'Expected four initial absence fixtures'}
$notice=Read-DemoApi '/api/notifications' 'teacher'
if(!$notice.notifications.Count){throw 'Demo teacher has no deep-link notification'}
Write-Output "PASS: 8 demo logins, 14+1 children with family isolation, 11 invoices, overdue balance, 15%/30% absence warnings, today makeup roster and absence notifications. No demo workflow was consumed."
