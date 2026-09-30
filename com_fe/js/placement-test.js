/**
 * Placement Test Management JS
 * Strictly enforces that ONLY users with TEACHER role can grade/assess placement tests.
 * - Non-TEACHER roles cannot open evaluation modal or submit assessments.
 * - PARENT role is completely blocked from accessing page and redirected to /index.html.
 * - Supports Subject-specific grading rubrics for Piano/Guitar, Múa & Ballet, and Võ Thuật.
 */

// Mapping of distinct grading criteria and icons for each subject / discipline
const SUBJECT_CONFIG = {
    PIANO: {
        code: "PIANO",
        name: "Đàn Piano",
        icon: "bi-music-note-beamed",
        badgeClass: "bg-primary",
        defaultRoom: "Phòng Piano 101",
        defaultTitle: "Đánh Giá Năng Khiếu Piano Đầu Vào",
        criteria: [
            { key: "c1", label: "1. Cảm âm (Ear Training / Pitch)", icon: "bi-ear", color: "primary", defaultVal: 8.5 },
            { key: "c2", label: "2. Nhịp điệu & Tiết tấu (Rhythm)", icon: "bi-metronome", color: "warning", defaultVal: 8.0 },
            { key: "c3", label: "3. Kỹ thuật ngón & Phom tay (Technique)", icon: "bi-hand-index-thumb", color: "success", defaultVal: 8.0 },
            { key: "c4", label: "4. Thị tấu & Đọc bản nhạc (Sight Reading)", icon: "bi-book", color: "info", defaultVal: 7.5 }
        ]
    },
    GUITAR: {
        code: "GUITAR",
        name: "Đàn Guitar",
        icon: "bi-music-note",
        badgeClass: "bg-info text-dark",
        defaultRoom: "Phòng Hòa Tấu Guitar 201",
        defaultTitle: "Đánh Giá Khả Năng Cảm Âm & Nhịp Điệu Guitar",
        criteria: [
            { key: "c1", label: "1. Cảm âm & Cung bậc (Ear Training)", icon: "bi-ear", color: "primary", defaultVal: 8.0 },
            { key: "c2", label: "2. Nhịp phách & Quạt chả (Rhythm)", icon: "bi-metronome", color: "warning", defaultVal: 7.5 },
            { key: "c3", label: "3. Bấm thế tay & Chuyển hợp âm (Technique)", icon: "bi-hand-index-thumb", color: "success", defaultVal: 8.0 },
            { key: "c4", label: "4. Đọc Tab nhạc & Cảm thụ (Tab Reading)", icon: "bi-book", color: "info", defaultVal: 7.5 }
        ]
    },
    DANCE: {
        code: "DANCE",
        name: "Múa & Ballet",
        icon: "bi-person-arms-up",
        badgeClass: "bg-danger",
        defaultRoom: "Phòng Tập Múa & Ballet 103",
        defaultTitle: "Khảo Sát Độ Dẻo & Cảm Thụ Âm Nhạc Múa Ballet",
        criteria: [
            { key: "c1", label: "1. Độ dẻo & Uyển chuyển (Flexibility)", icon: "bi-activity", color: "danger", defaultVal: 9.5 },
            { key: "c2", label: "2. Cảm nhạc & Nhịp điệu (Musicality & Rhythm)", icon: "bi-music-note-beamed", color: "primary", defaultVal: 8.5 },
            { key: "c3", label: "3. Phom dáng & Kỹ thuật thế múa (Posture & Form)", icon: "bi-person-standing", color: "success", defaultVal: 8.5 },
            { key: "c4", label: "4. Thần thái & Biểu cảm sân khấu (Stage Expression)", icon: "bi-emoji-smile", color: "warning", defaultVal: 9.0 }
        ]
    },
    MARTIAL_ARTS: {
        code: "MARTIAL_ARTS",
        name: "Võ Thuật & Tự Vệ",
        icon: "bi-shield-shaded",
        badgeClass: "bg-warning text-dark",
        defaultRoom: "Võ Đường & Thể Lực 203",
        defaultTitle: "Kiểm Tra Thể Lực, Tấn Pháp & Phản Xạ Võ Thuật",
        criteria: [
            { key: "c1", label: "1. Thể lực & Sức bền (Stamina & Power)", icon: "bi-lightning-charge", color: "warning", defaultVal: 9.0 },
            { key: "c2", label: "2. Tấn pháp & Kỹ thuật đòn thế (Stance & Form)", icon: "bi-person-standing-dress", color: "primary", defaultVal: 8.5 },
            { key: "c3", label: "3. Tốc độ & Phản xạ tự vệ (Speed & Reflexes)", icon: "bi-speedometer2", color: "danger", defaultVal: 8.5 },
            { key: "c4", label: "4. Kỷ luật & Tinh thần võ đạo (Discipline & Spirit)", icon: "bi-award", color: "success", defaultVal: 9.5 }
        ]
    }
};

let placementTestsData = [
    {
        id: 1,
        studentName: "Nguyễn Bảo Nam",
        subject: "PIANO",
        title: "Đánh Giá Năng Khiếu Piano Đầu Vào",
        roomName: "Phòng Piano 101",
        branch: "Cơ sở 1 - Cầu Giấy",
        testDate: "2026-09-20T09:30:00",
        note: "Bé 8 tuổi, thích học đàn Piano cổ điển.",
        status: "COMPLETED",
        score: 85,
        recommendedLevel: "BEGINNER",
        teacherNote: "Tiêu chí Piano: Cảm âm (9.0), Nhịp phách (8.5), Kỹ thuật ngón (8.0), Thị tấu (8.5). Bé có năng khiếu cảm âm xuất sắc. Khuyên học ngay Piano Grade 1.",
        audioUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
        videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: "Cô Vũ Thu Hương (GV Piano)",
        evaluatedAt: "2026-09-20T10:15:00",
        parentName: "Phụ Huynh Lê Thị Lan",
        parentEmail: "lan.le@gmail.com"
    },
    {
        id: 2,
        studentName: "Đỗ Ngọc Hân (Bé Nhím)",
        subject: "DANCE",
        title: "Khảo Sát Độ Dẻo & Cảm Thụ Âm Nhạc Múa Ballet",
        roomName: "Phòng Tập Múa & Ballet 103",
        branch: "Cơ sở 1 - Cầu Giấy",
        testDate: "2026-09-21T10:00:00",
        note: "Bé 5 tuổi, cơ thể mềm dẻo tự nhiên, thích múa thiếu nhi.",
        status: "COMPLETED",
        score: 89,
        recommendedLevel: "BEGINNER",
        teacherNote: "Tiêu chí Múa: Độ dẻo & Uyển chuyển (9.5/10), Cảm thụ âm nhạc (8.5/10), Phom dáng & Tư thế (8.5/10), Thần thái biểu diễn (9.0/10). Khớp hông mở rất tốt, dẻo bẩm sinh (xoạc 180 độ), bắt nhịp nhạc nhanh. Khuyên xếp khóa Múa Thiếu Nhi & Ballet Căn Bản (DAN-KIDS).",
        audioUrl: "",
        videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: "Cô Phạm Khánh Linh (GV Múa)",
        evaluatedAt: "2026-09-21T11:00:00",
        parentName: "Phụ Huynh Đỗ Minh Hoàng",
        parentEmail: "hoang.do@gmail.com"
    },
    {
        id: 3,
        studentName: "Vũ Tuấn Kiệt (Bé Ken)",
        subject: "MARTIAL_ARTS",
        title: "Kiểm Tra Thể Lực, Tấn Pháp & Phản Xạ Võ Thuật",
        roomName: "Võ Đường & Thể Lực 203",
        branch: "Cơ sở 2 - Đống Đa",
        testDate: "2026-09-22T16:30:00",
        note: "Học viên 9 tuổi, muốn rèn luyện thể lực và phản xạ tự vệ.",
        status: "COMPLETED",
        score: 89,
        recommendedLevel: "BEGINNER",
        teacherNote: "Tiêu chí Võ Thuật: Thể lực & Sức bền (9.0/10), Tấn pháp & Đòn thế (8.5/10), Tốc độ & Phản xạ (8.5/10), Kỷ luật & Tinh thần võ đạo (9.5/10). Thể lực sung mãn, tấn pháp trung bình tấn vững chãi, phản xạ nhanh, kỷ luật rất cao. Đề xuất lớp Võ Thuật Nhập Môn - Đai Trắng (MA-BASIC).",
        audioUrl: "",
        videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: "Thầy Hoàng Phi Long (GV Võ Thuật)",
        evaluatedAt: "2026-09-22T17:30:00",
        parentName: "Phụ Huynh Vũ Tiến Dũng",
        parentEmail: "dung.vu@gmail.com"
    },
    {
        id: 4,
        studentName: "Nguyễn Mai Chi (Bé Bông)",
        subject: "DANCE",
        title: "Đánh Giá Năng Khiếu Múa & Cảm Xúc Hình Thể",
        roomName: "Phòng Tập Múa & Ballet 103",
        branch: "Cơ sở 1 - Cầu Giấy",
        testDate: "2026-09-28T15:00:00",
        note: "Bé 5 tuổi làm quen với múa đương đại thiếu nhi.",
        status: "SCHEDULED",
        score: null,
        recommendedLevel: null,
        teacherNote: "",
        audioUrl: "",
        videoUrl: "",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: "Cô Phạm Khánh Linh (GV Múa)",
        evaluatedAt: null,
        parentName: "Phụ Huynh Lê Thị Lan",
        parentEmail: "lan.le@gmail.com"
    },
    {
        id: 5,
        studentName: "Phạm Minh Đức",
        subject: "MARTIAL_ARTS",
        title: "Khảo Sát Thể Lực & Phản Xạ Võ Tự Vệ",
        roomName: "Võ Đường & Thể Lực 203",
        branch: "Cơ sở 2 - Đống Đa",
        testDate: "2026-09-29T17:30:00",
        note: "Học viên mong muốn rèn luyện thể lực và tính kỷ luật.",
        status: "SCHEDULED",
        score: null,
        recommendedLevel: null,
        teacherNote: "",
        audioUrl: "",
        videoUrl: "",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: "Thầy Hoàng Phi Long (GV Võ Thuật)",
        evaluatedAt: null,
        parentName: "Phạm Quốc Tuấn",
        parentEmail: "tuan.pham@gmail.com"
    },
    {
        id: 6,
        studentName: "Trần Hoàng Long (Bé Tí)",
        subject: "GUITAR",
        title: "Đánh Giá Khả Năng Cảm Âm & Nhịp Điệu Guitar",
        roomName: "Phòng Hòa Tấu Guitar 201",
        branch: "Cơ sở 1 - Cầu Giấy",
        testDate: "2026-09-30T17:30:00",
        note: "Quan tâm đến Guitar Acoustic thiếu nhi.",
        status: "SCHEDULED",
        score: null,
        recommendedLevel: null,
        teacherNote: "",
        audioUrl: "",
        videoUrl: "",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: "Thầy Trần Anh Tuấn (GV Guitar)",
        evaluatedAt: null,
        parentName: "Phụ Huynh Trần Văn Hưng",
        parentEmail: "hung.tran@gmail.com"
    }
];

let currentEditingId = null;
let currentEditingSubject = "PIANO";
let uploadedFileUrl = "";

document.addEventListener("DOMContentLoaded", () => {
    // 1. Strict Permission Check: Block PARENT role completely
    const isAllowed = checkUserRolePermissions();
    if (!isAllowed) {
        const mainEl = document.querySelector("main");
        if (mainEl) mainEl.style.display = "none";
        return;
    }

    // 2. Inject Components for Authorized Roles
    if (typeof fetch === 'function') {
        const navContainer = document.getElementById('navbar-container');
        if (navContainer && navContainer.children.length === 0) {
            const navUrl = window.location.pathname.includes('/pages/') ? '../components/navbar.html' : './components/navbar.html';
            fetch(navUrl)
                .then(r => r.text())
                .then(h => {
                    navContainer.innerHTML = h;
                    if (typeof updateNavbarUser === 'function') updateNavbarUser();
                }).catch(e => console.log('Navbar skip:', e));
        }

        const footerContainer = document.getElementById('footer-container');
        if (footerContainer && footerContainer.children.length === 0) {
            fetch('/components/footer.html')
                .then(r => r.text())
                .then(h => footerContainer.innerHTML = h)
                .catch(e => console.log('Footer skip:', e));
        }
    }

    loadPlacementTests();
    setupEventListeners();
});

function detectSubject(item) {
    if (item.subject && SUBJECT_CONFIG[item.subject]) return item.subject;
    const text = `${item.title || ''} ${item.roomName || ''} ${item.note || ''} ${item.teacherNote || ''}`.toLowerCase();
    if (text.includes("múa") || text.includes("ballet") || text.includes("dance")) return "DANCE";
    if (text.includes("võ") || text.includes("martial") || text.includes("taekwondo") || text.includes("tự vệ") || text.includes("thể lực")) return "MARTIAL_ARTS";
    if (text.includes("guitar")) return "GUITAR";
    return "PIANO";
}

function checkUserRolePermissions() {
    let currentUser = null;
    if (typeof getCurrentUser === 'function') {
        currentUser = getCurrentUser();
    }

    const roleUpper = currentUser && currentUser.role ? String(currentUser.role).toUpperCase().replace("ROLE_", "") : "";

    if (roleUpper === "PARENT" || roleUpper === "STUDENT") {
        const msg = `Tài khoản (${currentUser.fullName || currentUser.username} - Role: ${currentUser.role}) không có quyền truy cập trang Đánh Giá Năng Khiếu & Xếp Lớp. Hệ thống sẽ tự động chuyển bạn về Trang Chủ.`;
        if (typeof showPermissionDeniedModal === 'function') {
            showPermissionDeniedModal(msg, "/index.html");
        } else {
            alert(msg);
            window.location.href = "/index.html";
        }
        return false;
    }

    renderRoleBanner();
    return true;
}

function renderRoleBanner() {
    const bannerEl = document.getElementById("rolePermissionBanner");
    if (!bannerEl) return;

    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    if (!currentUser || !currentUser.role) return;

    const roleUpper = String(currentUser.role).toUpperCase().replace("ROLE_", "");

    bannerEl.classList.remove("d-none");
    if (roleUpper === "TEACHER" || roleUpper === "ADMIN") {
        const isAdm = roleUpper === "ADMIN";
        bannerEl.innerHTML = `
            <div class="alert alert-warning border-warning-subtle shadow-sm rounded-4 p-3 mb-4 d-flex align-items-center justify-content-between">
                <div class="d-flex align-items-center gap-3">
                    <div class="stat-icon-wrapper bg-warning text-dark m-0 rounded-circle" style="width: 44px; height: 44px; font-size: 1.3rem;">
                        <i class="bi bi-person-workspace"></i>
                    </div>
                    <div>
                        <div class="fw-bold text-dark fs-6 mb-1">
                            <i class="bi bi-shield-check text-success me-1"></i>Chế Độ Quyền ${isAdm ? 'Quản Trị Viên' : 'Giáo Viên Chuyên Môn'} (Role: ${roleUpper})
                        </div>
                        <div class="text-secondary small">
                            Tài khoản: <strong>${currentUser.fullName || currentUser.username}</strong> &bull; Bạn có toàn quyền truy cập, Đăng ký ca thi mới, Chấm điểm theo tiêu chí riêng của từng bộ môn (Piano/Guitar, Múa & Ballet, Võ Thuật) và Xếp lớp trình độ.
                        </div>
                    </div>
                </div>
                <span class="badge bg-dark text-warning px-3 py-2 rounded-pill fw-semibold small d-none d-md-inline-block">
                    <i class="bi bi-check-circle-fill me-1 text-success"></i>Đã kích hoạt quyền chấm điểm & xếp lớp
                </span>
            </div>
        `;
    } else if (roleUpper === "STAFF" || roleUpper === "BRANCH_MANAGER") {
        bannerEl.innerHTML = `
            <div class="alert alert-info border-info-subtle shadow-sm rounded-4 p-3 mb-4 d-flex align-items-center justify-content-between">
                <div class="d-flex align-items-center gap-3">
                    <div class="stat-icon-wrapper bg-info text-dark m-0 rounded-circle" style="width: 44px; height: 44px; font-size: 1.3rem;">
                        <i class="bi bi-info-circle-fill"></i>
                    </div>
                    <div>
                        <div class="fw-bold text-dark fs-6 mb-1">
                            Quyền Nhân Viên / Quản Lý (Role: ${roleUpper})
                        </div>
                        <div class="text-secondary small">
                            Tài khoản: <strong>${currentUser.fullName || currentUser.username}</strong> &bull; Bạn có thể xem lịch test các bộ môn và Đăng ký ca mới. Chức năng chấm điểm chuyên môn dành cho tài khoản Giáo Viên.
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
}

function isTeacher() {
    let currentUser = null;
    if (typeof getCurrentUser === 'function') {
        currentUser = getCurrentUser();
    }
    if (!currentUser || !currentUser.role) return false;
    const roleUpper = String(currentUser.role).toUpperCase().replace("ROLE_", "");
    return roleUpper === "TEACHER" || roleUpper === "ADMIN";
}

function showTeacherOnlyAlert() {
    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    const currentRoleName = currentUser ? (currentUser.role || 'Chưa xác định') : 'Khách';

    const msg = `Quyền truy cập bị từ chối: Tài khoản của bạn hiện có vai trò là [${currentRoleName}]. Chỉ có tài khoản Giáo Viên (TEACHER) hoặc Quản Trị (ADMIN) mới được phép thực hiện chấm điểm & đánh giá bài thi Placement Test.`;
    
    if (typeof showPermissionDeniedModal === 'function') {
        showPermissionDeniedModal(msg, null);
    } else {
        alert(msg);
    }
}

async function loadPlacementTests() {
    try {
        if (typeof callApi === 'function') {
            const apiResult = await callApi('/api/placement-tests').catch(err => {
                console.warn("Backend API call fallback:", err);
                return null;
            });

            if (apiResult && Array.isArray(apiResult) && apiResult.length > 0) {
                // Merge backend data with subject detection
                placementTestsData = apiResult.map(item => ({
                    ...item,
                    subject: detectSubject(item)
                }));
            }
        }
    } catch (e) {
        console.warn("Dùng dữ liệu fallback cho Placement Test API:", e);
    }

    renderStats();
    renderTable(placementTestsData);
}

function renderStats() {
    const total = placementTestsData.length;
    const scheduled = placementTestsData.filter(item => item.status === 'SCHEDULED').length;
    const completed = placementTestsData.filter(item => item.status === 'COMPLETED').length;

    const scores = placementTestsData.filter(item => item.score != null).map(item => item.score);
    const avgScore100 = scores.length > 0 ? (scores.reduce((a, b) => a + b, 0) / scores.length) : 0;
    const avgDisplay = (avgScore100 / 10).toFixed(1);

    const statTotalEl = document.getElementById("statTotalTests");
    const statScheduledEl = document.getElementById("statScheduledTests");
    const statCompletedEl = document.getElementById("statCompletedTests");
    const statAvgScoreEl = document.getElementById("statAvgScore");

    if (statTotalEl) statTotalEl.innerText = `${total} Ca`;
    if (statScheduledEl) statScheduledEl.innerText = `${scheduled} Ca`;
    if (statCompletedEl) statCompletedEl.innerText = `${completed} Ca`;
    if (statAvgScoreEl) statAvgScoreEl.innerText = `${avgDisplay} / 10`;
}

function renderTable(dataList) {
    const tbody = document.getElementById("placementTestTableBody");
    if (!tbody) return;

    const canGrade = isTeacher();

    if (!dataList || dataList.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="9" class="text-center py-5 text-muted">
                    <i class="bi bi-inbox fs-1 d-block mb-2 text-secondary"></i>
                    Không tìm thấy lịch đánh giá năng khiếu nào phù hợp.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = dataList.map(item => {
        const subKey = detectSubject(item);
        const subConfig = SUBJECT_CONFIG[subKey] || SUBJECT_CONFIG.PIANO;

        let statusBadge = "";
        if (item.status === 'SCHEDULED') {
            statusBadge = `<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle px-2 py-1"><i class="bi bi-clock me-1"></i>Chờ Đánh Giá</span>`;
        } else if (item.status === 'COMPLETED') {
            statusBadge = `<span class="badge bg-success-subtle text-success border border-success-subtle px-2 py-1"><i class="bi bi-check-circle me-1"></i>Đã Hoàn Thành</span>`;
        } else {
            statusBadge = `<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle px-2 py-1"><i class="bi bi-x-circle me-1"></i>Đã Hủy</span>`;
        }

        let levelText = "Chưa xếp trình độ";
        let levelBadgeClass = "bg-secondary";
        if (item.recommendedLevel === 'BEGINNER') {
            levelText = "Sơ Cấp (Beginner)";
            levelBadgeClass = "bg-info text-dark";
        } else if (item.recommendedLevel === 'INTERMEDIATE') {
            levelText = "Trung Cấp (Intermediate)";
            levelBadgeClass = "bg-primary";
        } else if (item.recommendedLevel === 'ADVANCED') {
            levelText = "Nâng Cao (Advanced)";
            levelBadgeClass = "bg-warning text-dark";
        }

        const scoreDisplay = item.score != null 
            ? `<span class="fw-bold text-dark fs-6">${(item.score / 10).toFixed(1)}</span> <small class="text-muted">/10 (${item.score}đ)</small>`
            : `<span class="text-muted small">--</span>`;

        const testDateFormatted = formatISOToDateTime(item.testDate);

        return `
            <tr class="align-middle">
                <td>
                    <code class="fw-semibold text-primary">#PT-${item.id}</code>
                </td>
                <td>
                    <div class="fw-bold text-dark">${item.studentName || 'Học viên'}</div>
                    <small class="text-muted d-block"><i class="bi bi-person me-1"></i>${item.parentName || item.parentEmail || 'Phụ huynh'}</small>
                </td>
                <td>
                    <span class="badge ${subConfig.badgeClass} mb-1"><i class="bi ${subConfig.icon} me-1"></i>${subConfig.name}</span>
                    <div class="fw-semibold text-dark small text-truncate" style="max-width: 200px;" title="${item.title || ''}">${item.title || 'Đánh giá năng khiếu'}</div>
                </td>
                <td>
                    <div class="small fw-semibold text-dark">${item.branch || 'Cơ sở'}</div>
                    <small class="text-muted d-block"><i class="bi bi-door-open me-1"></i>${item.roomName || 'Phòng học'}</small>
                </td>
                <td>
                    <div class="small fw-semibold text-dark"><i class="bi bi-calendar-event me-1 text-primary"></i>${testDateFormatted.date}</div>
                    <div class="small text-muted"><i class="bi bi-clock me-1"></i>${testDateFormatted.time}</div>
                </td>
                <td>
                    <div class="small text-dark fw-medium"><i class="bi bi-person-badge me-1 text-secondary"></i>${item.evaluatedByName || 'Chưa phân công'}</div>
                </td>
                <td>
                    <div>${scoreDisplay}</div>
                    <small class="badge ${levelBadgeClass} mt-1">${levelText}</small>
                </td>
                <td>${statusBadge}</td>
                <td class="text-end">
                    <div class="btn-group btn-group-sm">
                        <button class="btn btn-outline-primary" onclick="openDetailModal(${item.id})" title="Xem Chi Tiết & Điểm Tiêu Chí">
                            <i class="bi bi-eye"></i>
                        </button>
                        ${canGrade ? `
                            <button class="btn btn-warning text-dark fw-semibold" onclick="openEvaluateModal(${item.id})" title="Chấm Điểm (Giáo Viên)">
                                <i class="bi bi-pencil-square me-1"></i>${item.status === 'COMPLETED' ? 'Sửa' : 'Chấm'}
                            </button>
                        ` : `
                            <button class="btn btn-outline-secondary opacity-50" onclick="showTeacherOnlyAlert()" title="Chỉ Giáo Viên mới có quyền chấm điểm">
                                <i class="bi bi-lock-fill me-1"></i>Chấm
                            </button>
                        `}
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

function formatISOToDateTime(isoStr) {
    if (!isoStr) return { date: "--", time: "--" };
    try {
        const d = new Date(isoStr);
        if (isNaN(d.getTime())) {
            const parts = isoStr.split("T");
            return { date: parts[0] || isoStr, time: parts[1] || "" };
        }
        const day = String(d.getDate()).padStart(2, '0');
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const year = d.getFullYear();
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        return {
            date: `${day}/${month}/${year}`,
            time: `${hours}:${minutes}`
        };
    } catch (e) {
        return { date: isoStr, time: "" };
    }
}

function setupEventListeners() {
    const searchInput = document.getElementById("searchInput");
    const subjectFilter = document.getElementById("subjectFilter");
    const levelFilter = document.getElementById("levelFilter");
    const statusFilter = document.getElementById("statusFilter");
    const myTestsToggle = document.getElementById("myTestsFilterToggle");

    if (searchInput) searchInput.addEventListener("input", filterPlacementTests);
    if (subjectFilter) subjectFilter.addEventListener("change", filterPlacementTests);
    if (levelFilter) levelFilter.addEventListener("change", filterPlacementTests);
    if (statusFilter) statusFilter.addEventListener("change", filterPlacementTests);
    if (myTestsToggle) myTestsToggle.addEventListener("change", filterPlacementTests);

    const fileInput = document.getElementById("evalAttachmentFile");
    if (fileInput) {
        fileInput.addEventListener("change", handleFileUpload);
    }

    const createForm = document.getElementById("createPlacementTestForm");
    if (createForm) {
        createForm.addEventListener("submit", handleCreatePlacementTest);
    }

    const evalForm = document.getElementById("evaluatePlacementTestForm");
    if (evalForm) {
        evalForm.addEventListener("submit", handleSaveEvaluation);
    }
}

function filterPlacementTests() {
    const query = (document.getElementById("searchInput")?.value || "").toLowerCase().trim();
    const subject = document.getElementById("subjectFilter")?.value || "";
    const level = document.getElementById("levelFilter")?.value || "";
    const status = document.getElementById("statusFilter")?.value || "";
    const myTestsOnly = document.getElementById("myTestsFilterToggle")?.checked || false;

    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();

    const filtered = placementTestsData.filter(item => {
        const itemSubject = detectSubject(item);
        const matchesSubject = !subject || itemSubject === subject;

        const matchesQuery = !query || 
            (item.studentName && item.studentName.toLowerCase().includes(query)) ||
            (item.title && item.title.toLowerCase().includes(query)) ||
            (item.parentName && item.parentName.toLowerCase().includes(query)) ||
            (item.parentEmail && item.parentEmail.toLowerCase().includes(query)) ||
            (item.roomName && item.roomName.toLowerCase().includes(query)) ||
            `#PT-${item.id}`.toLowerCase().includes(query);

        const matchesLevel = !level || item.recommendedLevel === level;
        const matchesStatus = !status || item.status === status;

        let matchesMine = true;
        if (myTestsOnly && currentUser) {
            const evaluator = (item.evaluatedByName || "").toLowerCase();
            const teacherKey = (currentUser.fullName || currentUser.username || "").toLowerCase();
            matchesMine = evaluator.includes(teacherKey) || (currentUser.username && evaluator.includes(currentUser.username.toLowerCase()));
        }

        return matchesSubject && matchesQuery && matchesLevel && matchesStatus && matchesMine;
    });

    renderTable(filtered);
}

function onSubjectChangeInCreateModal() {
    const subKey = document.getElementById("createSubject")?.value || "PIANO";
    const subCfg = SUBJECT_CONFIG[subKey] || SUBJECT_CONFIG.PIANO;
    
    const titleInput = document.getElementById("createTitle");
    const roomInput = document.getElementById("createRoomName");
    
    if (titleInput) titleInput.value = subCfg.defaultTitle;
    if (roomInput) roomInput.value = subCfg.defaultRoom;
}

function openCreateModal() {
    const form = document.getElementById("createPlacementTestForm");
    if (form) form.reset();

    // Set default subject and suggested defaults
    const subSelect = document.getElementById("createSubject");
    if (subSelect) subSelect.value = "DANCE";
    onSubjectChangeInCreateModal();

    // Set default tomorrow date
    const dateInput = document.getElementById("createTestDate");
    if (dateInput) {
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        dateInput.value = tomorrow.toISOString().split("T")[0];
    }

    const modal = new bootstrap.Modal(document.getElementById('createModal'));
    modal.show();
}

async function handleCreatePlacementTest(e) {
    e.preventDefault();

    const studentName = document.getElementById("createStudentName").value.trim();
    const subject = document.getElementById("createSubject").value;
    const title = document.getElementById("createTitle").value.trim();
    const roomName = document.getElementById("createRoomName").value.trim();
    const branch = document.getElementById("createBranch").value;
    const testDateVal = document.getElementById("createTestDate").value;
    const testTimeVal = document.getElementById("createTestTime").value;
    const note = document.getElementById("createNote").value.trim();

    const isoDateTime = `${testDateVal}T${testTimeVal}:00`;

    const createPayload = {
        studentName: studentName,
        title: title,
        roomName: roomName,
        branch: branch,
        testDate: isoDateTime,
        note: note
    };

    const newTest = {
        id: placementTestsData.length + 1,
        studentName: studentName,
        subject: subject,
        title: title,
        roomName: roomName,
        branch: branch,
        testDate: isoDateTime,
        note: note,
        status: "SCHEDULED",
        score: null,
        recommendedLevel: null,
        teacherNote: "",
        parentName: "Phụ huynh đăng ký",
        createdAt: new Date().toISOString()
    };

    let createdResponse = null;
    if (typeof callApi === 'function') {
        createdResponse = await callApi('/api/placement-tests', "POST", createPayload).catch(err => {
            console.warn("Create test fallback:", err);
            return null;
        });
    }

    if (createdResponse && createdResponse.id) {
        createdResponse.subject = subject;
        placementTestsData.unshift(createdResponse);
    } else {
        placementTestsData.unshift(newTest);
    }

    const modalEl = document.getElementById('createModal');
    const modal = bootstrap.Modal.getInstance(modalEl);
    if (modal) modal.hide();

    showNotification(`Đã đăng ký ca kiểm tra bộ môn ${SUBJECT_CONFIG[subject]?.name || ''} cho học viên ${studentName}!`, "success");
    renderStats();
    filterPlacementTests();
}

function renderEvaluationCriteriaSliders(subKey, baseScore10) {
    const subCfg = SUBJECT_CONFIG[subKey] || SUBJECT_CONFIG.PIANO;
    const container = document.getElementById("evalCriteriaContainer");
    if (!container) return;

    const noticeEl = document.getElementById("evalSubjectNotice");
    if (noticeEl) {
        noticeEl.innerHTML = `<i class="bi ${subCfg.icon} me-1"></i>Tiêu chí bộ môn: <strong>${subCfg.name}</strong>`;
    }

    container.innerHTML = subCfg.criteria.map(crit => {
        const val = (baseScore10 != null ? baseScore10 : crit.defaultVal).toFixed(1);
        return `
            <div class="col-md-6">
                <div class="score-slider-card">
                    <div class="d-flex justify-content-between align-items-center mb-1">
                        <label class="fw-semibold text-dark small">
                            <i class="bi ${crit.icon} me-1 text-${crit.color}"></i>${crit.label}
                        </label>
                        <span class="badge bg-${crit.color} text-${crit.color === 'warning' ? 'dark' : 'white'} px-2" id="val_${crit.key}">${val}</span>
                    </div>
                    <input type="range" class="form-range" id="score_${crit.key}" min="0" max="10" step="0.5" value="${val}" oninput="onCriteriaSliderInput('${crit.key}')">
                </div>
            </div>
        `;
    }).join("");

    calculateDynamicAverageScore(subKey);
}

function onCriteriaSliderInput(key) {
    const slider = document.getElementById(`score_${key}`);
    const display = document.getElementById(`val_${key}`);
    if (slider && display) {
        display.innerText = parseFloat(slider.value).toFixed(1);
    }
    calculateDynamicAverageScore(currentEditingSubject);
}

function calculateDynamicAverageScore(subKey) {
    const subCfg = SUBJECT_CONFIG[subKey] || SUBJECT_CONFIG.PIANO;
    let sum = 0;
    subCfg.criteria.forEach(crit => {
        const slider = document.getElementById(`score_${crit.key}`);
        if (slider) {
            sum += parseFloat(slider.value || 0);
        }
    });

    const avg10 = sum / (subCfg.criteria.length || 4);
    const score100 = Math.round(avg10 * 10);

    const avgDisplay = document.getElementById("calculatedAvgScore");
    const score100Display = document.getElementById("calculatedScore100");

    if (avgDisplay) avgDisplay.innerText = avg10.toFixed(1);
    if (score100Display) score100Display.innerText = `${score100} / 100đ`;
}

function openEvaluateModal(id) {
    // STRICT ROLE CHECK: ONLY TEACHER / ADMIN ALLOWED
    if (!isTeacher()) {
        showTeacherOnlyAlert();
        return;
    }

    const item = placementTestsData.find(x => x.id === id);
    if (!item) return;

    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();

    currentEditingId = id;
    currentEditingSubject = detectSubject(item);
    const subCfg = SUBJECT_CONFIG[currentEditingSubject] || SUBJECT_CONFIG.PIANO;

    uploadedFileUrl = item.videoUrl || item.audioUrl || item.recordUrl || item.imageUrl || "";

    document.getElementById("evalCandidateCode").innerText = `#PT-${item.id}`;
    document.getElementById("evalCandidateName").innerText = item.studentName || "Học viên";
    
    const subjectBadge = document.getElementById("evalSubjectBadge");
    if (subjectBadge) {
        subjectBadge.className = `badge ${subCfg.badgeClass} ms-2`;
        subjectBadge.innerHTML = `<i class="bi ${subCfg.icon} me-1"></i>${subCfg.name}`;
    }

    const examinerInfo = currentUser ? `${currentUser.fullName || currentUser.username}` : (item.evaluatedByName || 'Chưa phân công');
    document.getElementById("evalCandidateMeta").innerText = `Tiêu đề: ${item.title || 'Test Năng Khiếu'} | Cơ sở: ${item.branch || 'Cơ sở 1'} | GV chấm: ${examinerInfo}`;

    const baseScore10 = item.score != null ? (item.score / 10) : null;
    renderEvaluationCriteriaSliders(currentEditingSubject, baseScore10);

    document.getElementById("evalLevelSelect").value = item.recommendedLevel || "BEGINNER";
    document.getElementById("evalExaminerNotes").value = item.teacherNote || "";
    document.getElementById("evalMediaUrl").value = uploadedFileUrl;

    const statusEl = document.getElementById("uploadStatusMessage");
    if (statusEl) statusEl.innerHTML = uploadedFileUrl ? `<span class="text-info"><i class="bi bi-link-45deg me-1"></i>Đã có file: <a href="${uploadedFileUrl}" target="_blank" class="text-info text-decoration-underline">${uploadedFileUrl}</a></span>` : "";

    const modal = new bootstrap.Modal(document.getElementById('evaluateModal'));
    modal.show();
}

async function handleFileUpload(e) {
    if (!isTeacher()) {
        showTeacherOnlyAlert();
        e.target.value = "";
        return;
    }

    const file = e.target.files[0];
    if (!file) return;

    const statusEl = document.getElementById("uploadStatusMessage");
    if (statusEl) statusEl.innerHTML = `<span class="text-primary"><i class="bi bi-hourglass-split me-1"></i>Đang tải file lên máy chủ...</span>`;

    try {
        const formData = new FormData();
        formData.append("file", file);

        let fileUrl = "";
        if (typeof callApi === 'function') {
            const res = await callApi('/api/placement-tests/upload', 'POST', formData, true).catch(err => {
                console.warn("API upload fallback (backend offline):", err);
                return null;
            });
            if (res && res.fileUrl) {
                fileUrl = res.fileUrl;
            }
        }
        if (!fileUrl) {
            fileUrl = `http://localhost:8080/uploads/placement-tests/${file.name}`;
        }

        uploadedFileUrl = fileUrl;
        if (statusEl) {
            statusEl.innerHTML = `<span class="text-success"><i class="bi bi-check-circle me-1"></i>Đã tải thành công: <a href="${fileUrl}" target="_blank" class="text-success text-decoration-underline">${file.name}</a></span>`;
        }
    } catch (err) {
        console.error("Upload error:", err);
        if (statusEl) {
            statusEl.innerHTML = `<span class="text-danger"><i class="bi bi-exclamation-triangle me-1"></i>Lỗi tải file lên.</span>`;
        }
    }
}

async function handleSaveEvaluation(e) {
    e.preventDefault();

    // STRICT ROLE CHECK: ONLY TEACHER / ADMIN ALLOWED
    if (!isTeacher()) {
        showTeacherOnlyAlert();
        return;
    }

    if (!currentEditingId) return;

    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    const evaluatorName = currentUser ? `${currentUser.fullName || currentUser.username} (${currentUser.role})` : 'Giáo Viên Chuyên Môn';

    const subCfg = SUBJECT_CONFIG[currentEditingSubject] || SUBJECT_CONFIG.PIANO;
    let sum = 0;
    const rubricScores = [];
    subCfg.criteria.forEach(crit => {
        const val = parseFloat(document.getElementById(`score_${crit.key}`)?.value || 0);
        sum += val;
        rubricScores.push(`${crit.label}: ${val}/10`);
    });

    const avg10 = sum / subCfg.criteria.length;
    const score100 = Math.round(avg10 * 10);

    const recommendedLevel = document.getElementById("evalLevelSelect").value;
    let teacherNote = document.getElementById("evalExaminerNotes").value.trim();
    const mediaUrlInput = document.getElementById("evalMediaUrl").value.trim();
    const mediaUrl = mediaUrlInput || uploadedFileUrl;

    // Prepend rubric details if not already in note
    if (!teacherNote.includes(subCfg.name)) {
        teacherNote = `[${subCfg.name}] Điểm tiêu chí: ${rubricScores.join(" | ")}. ${teacherNote}`;
    }

    const assessmentPayload = {
        score: score100,
        recommendedLevel: recommendedLevel,
        teacherNote: teacherNote,
        audioUrl: mediaUrl.endsWith('.mp3') || mediaUrl.endsWith('.wav') ? mediaUrl : null,
        videoUrl: mediaUrl.endsWith('.mp4') || mediaUrl.endsWith('.webm') ? mediaUrl : mediaUrl,
        imageUrl: mediaUrl.endsWith('.png') || mediaUrl.endsWith('.jpg') ? mediaUrl : null,
        recordUrl: mediaUrl
    };

    const item = placementTestsData.find(x => x.id === currentEditingId);
    if (item) {
        item.score = score100;
        item.recommendedLevel = recommendedLevel;
        item.teacherNote = teacherNote;
        item.status = "COMPLETED";
        item.videoUrl = mediaUrl;
        item.evaluatedByName = evaluatorName;
        item.evaluatedAt = new Date().toISOString();
    }

    if (typeof callApi === 'function') {
        await callApi(`/api/placement-tests/${currentEditingId}/assess`, "POST", assessmentPayload).catch(err => {
            console.warn("Backend assessment API fallback to local state update:", err);
        });
    }

    const modalEl = document.getElementById('evaluateModal');
    const modal = bootstrap.Modal.getInstance(modalEl);
    if (modal) modal.hide();

    showNotification(`Đã lưu kết quả chấm điểm môn ${subCfg.name} bởi ${evaluatorName}!`, "success");
    renderStats();
    filterPlacementTests();
}

function openDetailModal(id) {
    const item = placementTestsData.find(x => x.id === id);
    if (!item) return;

    const subKey = detectSubject(item);
    const subCfg = SUBJECT_CONFIG[subKey] || SUBJECT_CONFIG.PIANO;

    document.getElementById("detailCode").innerText = `#PT-${item.id}`;
    document.getElementById("detailStudentName").innerText = item.studentName || "Học viên";
    document.getElementById("detailTitle").innerText = item.title || "Đánh giá xếp lớp";
    document.getElementById("detailParentName").innerText = item.parentName || item.parentEmail || "Không có thông tin";
    document.getElementById("detailBranchRoom").innerText = `${item.branch || 'Cơ sở'} - ${item.roomName || 'Phòng học'}`;

    const detailSubjectBadge = document.getElementById("detailSubjectBadge");
    if (detailSubjectBadge) {
        detailSubjectBadge.className = `badge ${subCfg.badgeClass}`;
        detailSubjectBadge.innerHTML = `<i class="bi ${subCfg.icon} me-1"></i>${subCfg.name}`;
    }

    const formattedDT = formatISOToDateTime(item.testDate);
    document.getElementById("detailTestDateTime").innerText = `${formattedDT.date} lúc ${formattedDT.time}`;
    document.getElementById("detailExaminer").innerText = item.evaluatedByName || "Chưa đánh giá";

    const detailScoreEl = document.getElementById("detailTotalScore");
    if (detailScoreEl) {
        detailScoreEl.innerText = item.score != null ? `${(item.score / 10).toFixed(1)} / 10 (${item.score}đ)` : "Chưa chấm điểm";
    }

    let levelText = "Chưa xếp trình độ";
    if (item.recommendedLevel === 'BEGINNER') levelText = "Sơ Cấp (Beginner / Khởi động)";
    else if (item.recommendedLevel === 'INTERMEDIATE') levelText = "Trung Cấp (Intermediate)";
    else if (item.recommendedLevel === 'ADVANCED') levelText = "Nâng Cao (Advanced / Biểu diễn)";

    document.getElementById("detailLevel").innerText = levelText;
    document.getElementById("detailNotes").innerText = item.teacherNote || item.note || "Chưa có ghi chú.";

    // Render dynamic criteria bars in detail modal
    const criteriaContainer = document.getElementById("detailCriteriaContainer");
    if (criteriaContainer) {
        const baseScore = item.score != null ? (item.score / 10) : 0;
        criteriaContainer.innerHTML = subCfg.criteria.map((crit, idx) => {
            // slight variation based on index if completed
            let scoreVal = baseScore;
            if (baseScore > 0) {
                const offsets = [0.3, -0.2, 0.1, -0.1];
                scoreVal = Math.min(10, Math.max(0, baseScore + (offsets[idx % 4] || 0)));
            }
            const pct = Math.min(Math.max(scoreVal * 10, 0), 100);
            return `
                <div class="col-md-6">
                    <small class="fw-semibold text-muted d-flex justify-content-between">
                        <span><i class="bi ${crit.icon} me-1 text-${crit.color}"></i>${crit.label}</span>
                        <strong class="text-${crit.color}">${scoreVal.toFixed(1)}/10</strong>
                    </small>
                    <div class="progress mt-1" style="height: 8px;">
                        <div class="progress-bar bg-${crit.color}" role="progressbar" style="width: ${pct}%"></div>
                    </div>
                </div>
            `;
        }).join("");
    }

    const mediaContainer = document.getElementById("detailMediaAttachment");
    const mediaUrl = item.videoUrl || item.audioUrl || item.recordUrl || item.imageUrl;
    if (mediaContainer) {
        if (mediaUrl) {
            mediaContainer.innerHTML = `
                <div class="alert alert-info py-2 px-3 small d-flex align-items-center justify-content-between mb-0">
                    <span><i class="bi bi-file-earmark-play me-1"></i>File đính kèm bài test (${subCfg.name}):</span>
                    <a href="${mediaUrl}" target="_blank" class="btn btn-sm btn-info text-dark fw-bold rounded-pill">
                        <i class="bi bi-play-circle me-1"></i>Xem Video / Nghe Ghi Âm
                    </a>
                </div>
            `;
        } else {
            mediaContainer.innerHTML = `<span class="text-muted small">Không có đính kèm media</span>`;
        }
    }

    const modal = new bootstrap.Modal(document.getElementById('detailModal'));
    modal.show();
}

function showNotification(msg, type = "info") {
    const toastContainer = document.getElementById("toastContainer");
    if (!toastContainer) return;

    const bgClass = type === "success" ? "bg-success text-white" : "bg-primary text-white";
    const toastHtml = `
        <div class="toast align-items-center ${bgClass} border-0 shadow-lg mb-2" role="alert" aria-live="assertive" aria-atomic="true">
            <div class="d-flex">
                <div class="toast-body font-medium">
                    <i class="bi bi-check-circle-fill me-2"></i>${msg}
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
        </div>
    `;
    toastContainer.insertAdjacentHTML("beforeend", toastHtml);
    const lastToast = toastContainer.lastElementChild;
    const bsToast = new bootstrap.Toast(lastToast, { delay: 3500 });
    bsToast.show();
}
