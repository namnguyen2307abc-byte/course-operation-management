$path = "c:\Users\PC\Desktop\SWP391\course-operation-management\com_fe\js\weekly-schedule.js"
$enc = New-Object System.Text.UTF8Encoding($false)
$lines = [System.IO.File]::ReadAllLines($path, $enc)

$startMarker = -1
$endMarker = -1
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($startMarker -lt 0 -and $lines[$i] -like '*ATTENDANCE SHEET MODAL*') { $startMarker = $i }
    if ($lines[$i] -like '*MODAL T*O L*P H*C M*I*LESSON GENERATOR*') { $endMarker = $i }
}
Write-Output "start marker line: $($startMarker + 1), end marker line: $($endMarker + 1)"

if ($startMarker -lt 1 -or $endMarker -le $startMarker) {
    Write-Output "ABORT: markers not found"
    exit 1
}

# Mỗi marker nằm giữa 2 dòng "// ====": cắt từ dòng "// ====" trước marker đầu tới dòng liền trước "// ====" của marker cuối.
$cutFrom = $startMarker - 1
$cutTo = $endMarker - 2
$kept = @()
$kept += $lines[0..($cutFrom - 1)]
$kept += $lines[($cutTo + 1)..($lines.Length - 1)]
[System.IO.File]::WriteAllLines($path, $kept, $enc)
Write-Output "Removed lines $($cutFrom + 1)-$($cutTo + 1). New total: $($kept.Length)"
