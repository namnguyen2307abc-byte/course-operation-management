[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$loginPayload = '{"username":"parent_test_01","password":"123456"}'
$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json; charset=utf-8" -Body $loginPayload
$token = $loginResp.token
$headers = @{ "Authorization" = "Bearer $token" }

Write-Host "=== TEST PARENT_TEST_01 CHILDREN ==="
$children = Invoke-RestMethod -Uri "http://localhost:8080/api/course-enrollment/children" -Headers $headers
$children | Format-Table id, fullName, dateOfBirth, gender

Write-Host "=== TEST AVAILABLE COURSES ==="
$courses = Invoke-RestMethod -Uri "http://localhost:8080/api/course-enrollment/courses" -Headers $headers
$courses | Format-Table id, code, name, level, tuitionFee

Write-Host "=== TEST PARENT_LAN REQUESTS ==="
$loginLan = '{"username":"parent_lan","password":"123456"}'
$tokenLan = (Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body $loginLan).token
$reqs = Invoke-RestMethod -Uri "http://localhost:8080/api/course-enrollment/requests/my" -Headers @{ "Authorization" = "Bearer $tokenLan" }
$reqs | Format-Table id, childName, preferredCourseName, className, status
