function Check-Utf8Text($name, $json) {
    if ($json -match 'á»|Ä|Ã|Æ|â€|ï¿½') {
        Write-Host "[FAIL] $name contains corrupted characters!" -ForegroundColor Red
        Write-Host $json.Substring(0, [Math]::Min(300, $json.Length))
    } else {
        Write-Host "[OK] $name clean UTF-8" -ForegroundColor Green
    }
}

# 1. Login Parent
$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"parent_test_01","password":"123456"}'
$tParent = $loginResp.token

# 2. Login Staff
$loginStaffResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"staff_lan","password":"123456"}'
$tStaff = $loginStaffResp.token

# Check parent endpoints
curl.exe -s -H "Authorization: Bearer $tParent" "http://localhost:8080/api/course-enrollment/children" -o scratch/out_children.json
Check-Utf8Text "children" ([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("scratch/out_children.json")))

curl.exe -s -H "Authorization: Bearer $tParent" "http://localhost:8080/api/course-enrollment/courses" -o scratch/out_courses.json
Check-Utf8Text "courses" ([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("scratch/out_courses.json")))

curl.exe -s -H "Authorization: Bearer $tParent" "http://localhost:8080/api/course-enrollment/requests/my" -o scratch/out_reqs_my.json
Check-Utf8Text "requests/my" ([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("scratch/out_reqs_my.json")))

curl.exe -s -H "Authorization: Bearer $tParent" "http://localhost:8080/api/course-enrollment/children/12/class-recommendations" -o scratch/out_recom.json
Check-Utf8Text "class-recommendations" ([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("scratch/out_recom.json")))

# Check staff endpoints
curl.exe -s -H "Authorization: Bearer $tStaff" "http://localhost:8080/api/course-enrollment/requests/pending" -o scratch/out_pending.json
Check-Utf8Text "requests/pending" ([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("scratch/out_pending.json")))

curl.exe -s -H "Authorization: Bearer $tStaff" "http://localhost:8080/api/course-enrollment/open-classes" -o scratch/out_open_classes.json
Check-Utf8Text "open-classes" ([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("scratch/out_open_classes.json")))

curl.exe -s -H "Authorization: Bearer $tStaff" "http://localhost:8080/api/tuition-payment/search?keyword=" -o scratch/out_tuition.json
Check-Utf8Text "tuition-payment/search" ([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("scratch/out_tuition.json")))

curl.exe -s -H "Authorization: Bearer $tStaff" "http://localhost:8080/api/absence-makeup/absence-requests" -o scratch/out_absence.json
Check-Utf8Text "absence-requests" ([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("scratch/out_absence.json")))

curl.exe -s -H "Authorization: Bearer $tStaff" "http://localhost:8080/api/absence-makeup/makeup-classes?studentId=1" -o scratch/out_makeup.json
Check-Utf8Text "makeup-classes" ([System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes("scratch/out_makeup.json")))
