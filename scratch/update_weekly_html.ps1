$path = "c:\Users\PC\Desktop\SWP391\course-operation-management\com_fe\pages\weekly-schedule.html"
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# 1. Thay thế block style inline bằng schedule.css
$styleRegex = '(?s)<link rel="stylesheet" href="\.\./css/style\.css">.*?<style>.*?</style>'
$newHeadCss = '<link rel="stylesheet" href="../css/style.css">' + "`r`n" + '    <link rel="stylesheet" href="../css/schedule.css">'
$content = [System.Text.RegularExpressions.Regex]::Replace($content, $styleRegex, $newHeadCss)

# 2. Thay thế breadcrumb & tiêu đề cũ bằng HERO & Legend mới
$headerRegex = '(?s)<!-- Breadcrumb & Tiêu đề -->.*?<!-- Thanh điều khiển tuần & Bộ lọc -->'
$newHero = @"
<!-- HERO: Tiêu đề & Thông tin vai trò -->
        <header class="schedule-hero mb-4">
            <div class="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3">
                <div>
                    <nav class="hero-breadcrumb" aria-label="breadcrumb">
                        <ol class="breadcrumb small mb-1">
                            <li class="breadcrumb-item"><a href="../index.html">Trang Chủ</a></li>
                            <li class="breadcrumb-item active" aria-current="page">Thời Khóa Biểu Tuần</li>
                        </ol>
                    </nav>
                    <h1 class="hero-title">
                        <i class="bi bi-calendar3"></i>
                        <span>Thời Khóa Biểu Tuần</span>
                    </h1>
                    <p class="hero-sub mb-0">
                        Theo dõi lịch dạy, ca học theo 4 Slot khung giờ và xem / thực hiện điểm danh học viên trực tiếp.
                    </p>
                </div>
                <div class="d-flex gap-2 align-items-center flex-wrap">
                    <span id="currentUserRoleBadge" class="badge bg-light text-dark px-3 py-2 rounded-pill fw-bold shadow-sm">Đang xác thực...</span>
                    <button class="btn btn-warning btn-sm px-3 rounded-pill fw-bold d-none shadow-sm" id="btnOpenCreateClassModal" onclick="openCreateClassModal()">
                        <i class="bi bi-plus-circle me-1"></i> + Tạo Lớp & Sinh Lịch
                    </button>
                    <button class="btn btn-outline-light btn-sm px-3 rounded-pill fw-semibold" onclick="loadWeeklySchedule()">
                        <i class="bi bi-arrow-clockwise me-1"></i> Làm mới
                    </button>
                </div>
            </div>
        </header>

        <!-- Chú giải trạng thái -->
        <div class="schedule-legend">
            <span class="lg-item"><span class="lg-dot" style="background:#2563eb;"></span> Hôm nay</span>
            <span class="lg-item"><span class="lg-dot" style="background:#10b981;"></span> Đã điểm danh (Present)</span>
            <span class="lg-item"><span class="lg-dot" style="background:#ef4444;"></span> Vắng mặt (Absent)</span>
            <span class="lg-item"><span class="lg-dot" style="background:#f59e0b;"></span> Buổi qua chưa điểm danh</span>
            <span class="lg-item"><span class="lg-dot" style="background:#94a3b8;"></span> Sắp diễn ra</span>
        </div>

        <!-- Thanh điều khiển tuần & Bộ lọc -->
"@
$content = [System.Text.RegularExpressions.Regex]::Replace($content, $headerRegex, $newHero)

# 3. Xóa bỏ modal điểm danh cũ (#modalAttendance)
$modalRegex = '(?s)<!-- =+ -->\s*<!-- MODAL 1: ĐIỂM DANH HỌC VIÊN BUỔI HỌC.*?</div>\s*</div>\s*</div>\s*(?=\s*<!-- =+ -->\s*<!-- MODAL 2: TẠO LỚP HỌC MỚI)'
$content = [System.Text.RegularExpressions.Regex]::Replace($content, $modalRegex, '')

[System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "DONE UPDATE weekly-schedule.html"
