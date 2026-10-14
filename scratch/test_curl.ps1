$resp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"parent_test_01","password":"123456"}'
$t = $resp.token
curl.exe -s -H "Authorization: Bearer $t" "http://localhost:8080/api/course-enrollment/courses" -o scratch/courses_direct.json
$bytes = [System.IO.File]::ReadAllBytes("scratch/courses_direct.json")
Write-Host "File size in bytes: $($bytes.Length)"
$first100Hex = ($bytes[0..99] | ForEach-Object { $_.ToString("X2") }) -join " "
Write-Host "Hex: $first100Hex"
$utf8 = [System.Text.Encoding]::UTF8.GetString($bytes)
Write-Host "Decoded UTF8:"
Write-Host $utf8
