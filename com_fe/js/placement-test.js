/**
 * Placement Test Management JS
 * Strictly enforces:
 * 1. Role and Subject-based permissions:
 *    - Only TEACHER role can grade/assess placement tests.
 *    - Teacher of a subject (e.g. PIANO) can ONLY view and grade tests of that subject.
 *    - Non-teachers / other subject tests cannot be viewed or graded.
 * 2. Schedule Creation constraints:
 *    - Test date cannot be in the past (e.g., cannot select yesterday).
 *    - Test time cannot be in the past (validated in real-time from the moment of selection).
 *    - Mandatory subject selection.
 *    - If created test does not belong to current teacher's subject, it is IMMEDIATELY HIDDEN from their grading list without needing to refresh.
 */

let placementTestsData = [
    {
        id: 1,
        studentName: "Nguyễn Hoàng Anh",
        subject: "DAN",
        title: "Đánh Giá Năng Khiếu Đàn Đầu Vào",
        roomName: "Phòng Đàn 101",
        branch: "Cơ sở 1 - Cầu Giấy",
        testDate: "2026-10-05T09:30:00",
        note: "Học viên 10 tuổi, đã tự tập organ 6 tháng ở nhà.",
        status: "SCHEDULED",
        score: null,
        recommendedLevel: null,
        teacherNote: "",
        audioUrl: "",
        videoUrl: "",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: null,
        evaluatedAt: null,
        parentName: "Phụ huynh Lê Thị Lan",
        parentEmail: "lan.le@gmail.com"
    },
    {
        id: 2,
        studentName: "Trần Bảo Ngọc",
        subject: "MUA",
        title: "Khảo Sát Thể Lực & Năng Khiếu Múa Nghệ Thuật",
        roomName: "Phòng Múa Nghệ Thuật 202",
        branch: "Cơ sở 2 - Đống Đa",
        testDate: "2026-10-02T15:00:00",
        note: "Độ dẻo dai cơ thể tốt, cảm thụ âm nhạc nhịp điệu nhanh.",
        status: "COMPLETED",
        score: 88,
        recommendedLevel: "INTERMEDIATE",
        teacherNote: "Độ mở khớp dẻo tốt (9/10), giữ thăng bằng vững. Khuyến nghị xếp lớp Múa Trung Cấp 1.",
        audioUrl: "",
        videoUrl: "https://example.com/recordings/test2.mp4",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: "Cô Nguyễn Mai Phương (GV Múa)",
        evaluatedAt: "2026-10-02T15:45:00",
        parentName: "Phụ huynh Lê Thị Lan",
        parentEmail: "lan.le@gmail.com"
    },
    {
        id: 3,
        studentName: "Phạm Minh Đức",
        subject: "VO",
        title: "Khảo Sát Thể Lực & Phản Xạ Võ Thuật",
        roomName: "Võ Đường & Thể Lực 203",
        branch: "Cơ sở 1 - Cầu Giấy",
        testDate: "2026-10-06T17:30:00",
        note: "Quan tâm đến lớp Võ tự vệ và rèn luyện thể lực kỷ luật.",
        status: "SCHEDULED",
        score: null,
        recommendedLevel: null,
        teacherNote: "",
        audioUrl: "",
        videoUrl: "",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: null,
        evaluatedAt: null,
        parentName: "Phạm Quốc Tuấn",
        parentEmail: "tuan.pham@gmail.com"
    },
    {
        id: 4,
        studentName: "Lê Hoàng Yến",
        subject: "DAN",
        title: "Kiểm Tra Trình Độ Phím Đàn Chuyên Sâu",
        roomName: "Phòng Hòa Tấu & Phím Đàn 301",
        branch: "Cơ sở 2 - Đống Đa",
        testDate: "2026-10-01T10:00:00",
        note: "Học viên 12 tuổi, có nhạc cảm tốt và đọc bản nhạc nhanh.",
        status: "COMPLETED",
        score: 92,
        recommendedLevel: "ADVANCED",
        teacherNote: "Kỹ thuật phím đàn vững vàng, thị tấu tốt. Khuyến nghị xếp lớp Đàn Nâng Cao (Advanced).",
        evaluatedByName: "Cô Vũ Thu Hương (GV Đàn)",
        evaluatedAt: "2026-10-01T11:00:00",
        parentName: "Phụ huynh Lê Thị Lan",
        parentEmail: "lan.le@gmail.com"
    }
];

let currentEditingId = null;
let uploadedFileUrl = "";

document.addEventListener("DOMContentLoaded", () => {
    // 1. Strict Permission Check: Block PARENT & STUDENT roles completely
    const isAllowed = checkUserRolePermissions();
    if (!isAllowed) {
        const mainEl = document.querySelector("main");
        if (mainEl) mainEl.style.display = "none";
        return;
    }

    // 2. Set min date and time for test creation (Prevent past dates & times in real-time)
    initDateRestrictions();

    // 3. Inject Navbar / Footer
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

function getTodayDateString() {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
}

function initDateRestrictions() {
    const testDateInput = document.getElementById("createTestDate");
    const testTimeInput = document.getElementById("createTestTime");
    if (!testDateInput) return;

    const todayStr = getTodayDateString();
    
    // Set strict min date
    testDateInput.min = todayStr;
    testDateInput.setAttribute("min", todayStr);

    if (!testDateInput.value || testDateInput.value < todayStr) {
        testDateInput.value = todayStr;
    }

    updateTimeMinRestriction();

    // Listeners for real-time past date/time validation
    testDateInput.oninput = handleDateInputChange;
    testDateInput.onchange = handleDateInputChange;

    if (testTimeInput) {
        testTimeInput.oninput = validateRealtimeDateTime;
        testTimeInput.onchange = validateRealtimeDateTime;
    }
}

function handleDateInputChange() {
    const testDateInput = document.getElementById("createTestDate");
    if (!testDateInput) return;

    const todayStr = getTodayDateString();

    // If user tries to pick yesterday or a date in the past
    if (testDateInput.value && testDateInput.value < todayStr) {
        showNotification(`Không được phép chọn ngày trong quá khứ! Hệ thống đã tự động đặt lại ngày hôm nay (${todayStr}).`, "warning");
        testDateInput.value = todayStr;
    }

    updateTimeMinRestriction();
}

function updateTimeMinRestriction() {
    const testDateInput = document.getElementById("createTestDate");
    const testTimeInput = document.getElementById("createTestTime");
    if (!testDateInput || !testTimeInput) return;

    const now = new Date();
    const todayStr = getTodayDateString();

    if (testDateInput.value === todayStr) {
        const currentHours = String(now.getHours()).padStart(2, '0');
        const currentMinutes = String(now.getMinutes()).padStart(2, '0');
        const currentTimeStr = `${currentHours}:${currentMinutes}`;
        
        testTimeInput.min = currentTimeStr;
        testTimeInput.setAttribute("min", currentTimeStr);

        // If currently chosen time is in the past, adjust it to 15 mins later
        if (!testTimeInput.value || testTimeInput.value < currentTimeStr) {
            const laterDate = new Date(now.getTime() + 15 * 60000);
            testTimeInput.value = `${String(laterDate.getHours()).padStart(2, '0')}:${String(laterDate.getMinutes()).padStart(2, '0')}`;
        }
    } else if (testDateInput.value < todayStr) {
        testDateInput.value = todayStr;
        updateTimeMinRestriction();
    } else {
        testTimeInput.removeAttribute("min");
    }
}

function validateRealtimeDateTime() {
    const testDateInput = document.getElementById("createTestDate");
    const testTimeInput = document.getElementById("createTestTime");
    if (!testDateInput || !testTimeInput) return;

    const testDateVal = testDateInput.value;
    const testTimeVal = testTimeInput.value;
    if (!testDateVal || !testTimeVal) return;

    const selectedDateTime = new Date(`${testDateVal}T${testTimeVal}:00`);
    const now = new Date();

    if (selectedDateTime <= now) {
        showNotification("Thời gian bạn vừa chọn đã ở trong quá khứ! Hệ thống đã tự động điều chỉnh lại thời gian hợp lệ.", "warning");
        updateTimeMinRestriction();
    }
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

function normalizeSubject(subject) {
    if (!subject) return "DAN";
    const s = String(subject).toUpperCase().trim();
    if (s === "DAN" || s === "PIANO" || s === "GUITAR" || s === "NHAC" || s === "MUSIC" || s === "VIOLIN" || s === "DRUMS" || s === "VOCAL" || s.includes("ĐÀN") || s.includes("NHẠC") || s.includes("PIANO") || s.includes("GUITAR")) return "DAN";
    if (s === "MUA" || s === "DANCE" || s.includes("MÚA") || s.includes("BALLET") || s.includes("NHẢY")) return "MUA";
    if (s === "VO" || s === "MARTIAL_ARTS" || s.includes("VÕ") || s.includes("TAEKWONDO") || s.includes("MARTIAL")) return "VO";
    return s;
}

function getSubjectDisplayName(code) {
    const norm = normalizeSubject(code);
    if (norm === "DAN") return "Đàn";
    if (norm === "MUA") return "Múa";
    if (norm === "VO") return "Võ";
    return code || "Năng khiếu";
}

const SUBJECT_EVALUATION_CONFIG = {
    'DAN': {
        subjectName: 'Bộ Môn Đàn',
        badgeClass: 'bg-primary-subtle text-primary border border-primary-subtle',
        headerIcon: 'bi-music-note-beamed text-primary',
        headerTitle: 'Chấm điểm 4 tiêu chí Bộ Môn Đàn',
        notePlaceholder: 'Nhận xét chi tiết về cảm âm, nhịp phách, kỹ thuật phím đàn, thị tấu và gợi ý lớp học phù hợp...',
        criteria: [
            { id: 'crit_1', label: '1. Cảm âm & Nhạc cảm (Pitch)', icon: 'bi-ear text-primary', badgeClass: 'bg-primary text-white' },
            { id: 'crit_2', label: '2. Nhịp phách & Tiết tấu (Rhythm)', icon: 'bi-metronome text-warning', badgeClass: 'bg-warning text-dark' },
            { id: 'crit_3', label: '3. Kỹ thuật ngón & Tư thế tay (Technique)', icon: 'bi-hand-index-thumb text-success', badgeClass: 'bg-success text-white' },
            { id: 'crit_4', label: '4. Đọc bản nhạc & Thị tấu (Sight Reading)', icon: 'bi-book text-info', badgeClass: 'bg-info text-dark' }
        ]
    },
    'MUA': {
        subjectName: 'Bộ Môn Múa',
        badgeClass: 'bg-danger-subtle text-danger border border-danger-subtle',
        headerIcon: 'bi-heart-pulse-fill text-danger',
        headerTitle: 'Chấm điểm 4 tiêu chí Bộ Môn Múa',
        notePlaceholder: 'Nhận xét chi tiết về độ dẻo dai cơ thể, khả năng cảm thụ âm nhạc, phom dáng và gợi ý lớp múa phù hợp...',
        criteria: [
            { id: 'crit_1', label: '1. Độ dẻo & Uyển chuyển (Flexibility)', icon: 'bi-universal-access text-danger', badgeClass: 'bg-danger text-white' },
            { id: 'crit_2', label: '2. Cảm thụ âm nhạc & Nhịp điệu (Musicality)', icon: 'bi-soundwave text-warning', badgeClass: 'bg-warning text-dark' },
            { id: 'crit_3', label: '3. Phom dáng & Kỹ thuật động tác (Posture)', icon: 'bi-person-standing text-success', badgeClass: 'bg-success text-white' },
            { id: 'crit_4', label: '4. Thần thái & Biểu cảm biểu diễn (Stage Presence)', icon: 'bi-stars text-info', badgeClass: 'bg-info text-dark' }
        ]
    },
    'VO': {
        subjectName: 'Bộ Môn Võ Thuật',
        badgeClass: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
        headerIcon: 'bi-shield-fill-check text-warning',
        headerTitle: 'Chấm điểm 4 tiêu chí Bộ Môn Võ Thuật',
        notePlaceholder: 'Nhận xét chi tiết về thể lực, phản xạ, tấn pháp đòn thế, tinh thần kỷ luật võ đạo và gợi ý cấp đai xếp lớp...',
        criteria: [
            { id: 'crit_1', label: '1. Thể lực & Sức bền (Stamina & Strength)', icon: 'bi-lightning-charge-fill text-danger', badgeClass: 'bg-danger text-white' },
            { id: 'crit_2', label: '2. Tốc độ & Phản xạ tự vệ (Reflexes & Speed)', icon: 'bi-speedometer2 text-warning', badgeClass: 'bg-warning text-dark' },
            { id: 'crit_3', label: '3. Tấn pháp & Kỹ thuật đòn (Stances & Technique)', icon: 'bi-shield-check text-primary', badgeClass: 'bg-primary text-white' },
            { id: 'crit_4', label: '4. Tác phong & Kỷ luật võ đạo (Martial Discipline)', icon: 'bi-award-fill text-success', badgeClass: 'bg-success text-white' }
        ]
    }
};

function getSubjectBadge(subject) {
    const s = normalizeSubject(subject);
    if (s === "DAN") {
        return `<span class="badge bg-primary-subtle text-primary border border-primary-subtle px-2 py-1"><i class="bi bi-music-note me-1"></i>🎹 Đàn</span>`;
    } else if (s === "MUA") {
        return `<span class="badge bg-danger-subtle text-danger border border-danger-subtle px-2 py-1"><i class="bi bi-heart-pulse-fill me-1"></i>🩰 Múa</span>`;
    } else if (s === "VO") {
        return `<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle px-2 py-1"><i class="bi bi-shield-fill-check me-1"></i>🥋 Võ</span>`;
    }
    return `<span class="badge bg-light text-dark border px-2 py-1">${subject || 'Năng khiếu'}</span>`;
}

function inferSubject(title) {
    if (!title) return "DAN";
    const t = title.toLowerCase();
    if (t.includes("võ") || t.includes("vo") || t.includes("taekwondo") || t.includes("martial") || t.includes("thể lực")) return "VO";
    if (t.includes("múa") || t.includes("mua") || t.includes("dance") || t.includes("ballet") || t.includes("nhảy")) return "MUA";
    if (t.includes("đàn") || t.includes("dan") || t.includes("piano") || t.includes("guitar") || t.includes("phím") || t.includes("nhạc") || t.includes("vocal") || t.includes("organ")) return "DAN";
    return "DAN";
}

function renderRoleBanner() {
    // Role banner is disabled per requirements
    const bannerEl = document.getElementById("rolePermissionBanner");
    if (bannerEl) {
        bannerEl.innerHTML = "";
        bannerEl.classList.add("d-none");
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

function isTeacherAllowedForTest(item) {
    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    if (!currentUser || !currentUser.role) return false;

    const roleUpper = String(currentUser.role).toUpperCase().replace("ROLE_", "");
    if (roleUpper === "ADMIN") return true;
    if (roleUpper !== "TEACHER") return false;

    const teacherSubject = normalizeSubject(currentUser.subject || "DAN");
    const itemSubject = normalizeSubject(item.subject || inferSubject(item.title));

    return teacherSubject === itemSubject;
}

function showTeacherSubjectMismatchAlert(teacherSub, testSub) {
    const teacherDisplayName = getSubjectDisplayName(teacherSub);
    const testDisplayName = getSubjectDisplayName(testSub);
    const msg = `Quyền truy cập bị từ chối: Bạn là Giáo Viên bộ môn [${teacherDisplayName}], không được phép chấm bài kiểm tra đầu vào thuộc bộ môn [${testDisplayName}]! Theo quy định, mỗi giáo viên chỉ được chấm bài thuộc chuyên môn bộ môn của mình.`;
    if (typeof showPermissionDeniedModal === 'function') {
        showPermissionDeniedModal(msg, null);
    } else {
        alert(msg);
    }
}

function showTeacherOnlyAlert() {
    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    const currentRoleName = currentUser ? (currentUser.role || 'Chưa xác định') : 'Khách';

    const msg = `Quyền truy cập bị từ chối: Tài khoản của bạn hiện có vai trò là [${currentRoleName}]. Chỉ có tài khoản Giáo Viên (TEACHER) đúng bộ môn hoặc Quản Trị (ADMIN) mới được phép thực hiện chấm điểm & đánh giá bài thi Placement Test.`;
    
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

            if (apiResult && Array.isArray(apiResult)) {
                placementTestsData = apiResult;
            }
        }
    } catch (e) {
        console.warn("Dùng dữ liệu fallback cho Placement Test API:", e);
    }

    renderStats();
    filterPlacementTests();
}

function renderStats() {
    let visibleData = getTeacherFilteredData();

    const total = visibleData.length;
    const scheduled = visibleData.filter(item => item.status === 'SCHEDULED').length;
    const completed = visibleData.filter(item => item.status === 'COMPLETED').length;

    const scores = visibleData.filter(item => item.score != null).map(item => item.score);
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

function getTeacherFilteredData() {
    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();

    if (!currentUser || !currentUser.role) return placementTestsData;
    const roleUpper = String(currentUser.role).toUpperCase().replace("ROLE_", "");

    // STRICT RULE: If TEACHER, only tests of their subject are visible
    if (roleUpper === "TEACHER") {
        const teacherSubject = normalizeSubject(currentUser.subject || "DAN");
        return placementTestsData.filter(item => {
            const itemSubject = normalizeSubject(item.subject || inferSubject(item.title));
            return itemSubject === teacherSubject;
        });
    }

    return placementTestsData;
}

function renderTable(dataList) {
    const tbody = document.getElementById("placementTestTableBody");
    if (!tbody) return;

    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    const roleUpper = currentUser && currentUser.role ? String(currentUser.role).toUpperCase().replace("ROLE_", "") : "";

    if (!dataList || dataList.length === 0) {
        const teacherSubjectName = getSubjectDisplayName(currentUser ? currentUser.subject : "DAN");
        const teacherMsg = roleUpper === "TEACHER" 
            ? `Không có ca thi đầu vào nào thuộc <strong>Bộ Môn ${teacherSubjectName}</strong> của bạn.`
            : "Không tìm thấy lịch đánh giá năng khiếu nào phù hợp.";

        tbody.innerHTML = `
            <tr>
                <td colspan="9" class="text-center py-5 text-muted">
                    <i class="bi bi-inbox fs-1 d-block mb-2 text-secondary"></i>
                    ${teacherMsg}
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = dataList.map(item => {
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
        const subjectBadge = getSubjectBadge(item.subject || inferSubject(item.title));
        const canGradeThisItem = isTeacherAllowedForTest(item);

        return `
            <tr class="align-middle">
                <td>
                    <code class="fw-semibold text-primary">#PT-${item.id}</code>
                </td>
                <td>
                    <div class="fw-bold text-dark">${item.studentName || 'Học viên'}</div>
                    <small class="text-muted d-block"><i class="bi bi-person me-1"></i>${item.parentName || item.parentEmail || 'Chưa cập nhật'}</small>
                </td>
                <td>
                    <div class="mb-1">${subjectBadge}</div>
                    <div class="fw-semibold text-dark small text-truncate" style="max-width: 220px;" title="${item.title || ''}">${item.title || 'Đánh giá năng khiếu'}</div>
                </td>
                <td>
                    <div class="small fw-semibold text-dark">${item.roomName || 'Phòng học'}</div>
                    <small class="text-muted d-block"><i class="bi bi-geo-alt me-1 text-danger"></i>${item.branch || 'Cơ sở'}</small>
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
                        <button class="btn btn-outline-primary" onclick="openDetailModal(${item.id})" title="Xem Chi Tiết">
                            <i class="bi bi-eye"></i>
                        </button>
                        ${canGradeThisItem ? `
                            <button class="btn btn-warning text-dark fw-semibold" onclick="openEvaluateModal(${item.id})" title="Chấm Điểm (Giáo Viên Bộ Môn)">
                                <i class="bi bi-pencil-square me-1"></i>${item.status === 'COMPLETED' ? 'Sửa' : 'Chấm'}
                            </button>
                        ` : `
                            <button class="btn btn-outline-secondary opacity-50" onclick="showTeacherOnlyAlert()" title="Chỉ Giáo Viên bộ môn này mới có quyền chấm điểm">
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

    const createModalEl = document.getElementById('createModal');
    if (createModalEl) {
        createModalEl.addEventListener('show.bs.modal', () => {
            initDateRestrictions();
        });
    }

    const evalModalEl = document.getElementById('evaluateModal');
    if (evalModalEl) {
        evalModalEl.addEventListener('hidden.bs.modal', () => {
            uploadedFileUrl = "";
            currentEditingId = null;
            const fileInput = document.getElementById("evalAttachmentFile");
            if (fileInput) fileInput.value = "";
            const mediaInput = document.getElementById("evalMediaUrl");
            if (mediaInput) mediaInput.value = "";
            const statusEl = document.getElementById("uploadStatusMessage");
            if (statusEl) statusEl.innerHTML = "";
        });
    }

    // Set default subject filter for teacher if element exists
    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    if (currentUser && String(currentUser.role).toUpperCase().includes("TEACHER") && currentUser.subject) {
        if (subjectFilter) {
            subjectFilter.value = normalizeSubject(currentUser.subject);
            subjectFilter.disabled = true; // Lock filter to teacher's subject
        }
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

    // 1. Get base data with teacher subject isolation
    let baseData = getTeacherFilteredData();

    // 2. Apply additional filters
    const filtered = baseData.filter(item => {
        const itemSubject = normalizeSubject(item.subject || inferSubject(item.title));

        const matchesQuery = !query || 
            (item.studentName && item.studentName.toLowerCase().includes(query)) ||
            (item.title && item.title.toLowerCase().includes(query)) ||
            (item.parentName && item.parentName.toLowerCase().includes(query)) ||
            (item.parentEmail && item.parentEmail.toLowerCase().includes(query)) ||
            `#PT-${item.id}`.toLowerCase().includes(query);

        const matchesSubject = !subject || itemSubject === normalizeSubject(subject);
        const matchesLevel = !level || item.recommendedLevel === level;
        const matchesStatus = !status || item.status === status;

        let matchesMine = true;
        if (myTestsOnly && currentUser) {
            const evaluator = (item.evaluatedByName || "").toLowerCase();
            const teacherKey = (currentUser.fullName || currentUser.username || "").toLowerCase();
            matchesMine = evaluator.includes(teacherKey) || (currentUser.username && evaluator.includes(currentUser.username.toLowerCase()));
        }

        return matchesQuery && matchesSubject && matchesLevel && matchesStatus && matchesMine;
    });

    renderTable(filtered);
}

function calculateAverageScore() {
    const sliders = document.querySelectorAll(".eval-criteria-slider");
    let total = 0;
    let count = 0;
    sliders.forEach(s => {
        total += parseFloat(s.value || 0);
        count++;
    });

    const avg10 = count > 0 ? (total / count) : 0;
    const score100 = Math.round(avg10 * 10);

    const avgDisplay = document.getElementById("calculatedAvgScore");
    const score100Display = document.getElementById("calculatedScore100");

    if (avgDisplay) avgDisplay.innerText = avg10.toFixed(1);
    if (score100Display) score100Display.innerText = `${score100} / 100đ`;
}

let enrollmentRequestsForScheduling = [];

function onEnrollmentRequestChange() {
    const requestId = Number(document.getElementById('createEnrollmentRequest')?.value);
    const selected = enrollmentRequestsForScheduling.find(request => request.id === requestId);
    const nameInput = document.getElementById('createStudentName');
    nameInput.value = selected ? selected.childName : '';
    nameInput.readOnly = Boolean(selected);
}

async function loadEnrollmentRequestsForScheduling() {
    const field = document.getElementById('createEnrollmentRequestField');
    const select = document.getElementById('createEnrollmentRequest');
    enrollmentRequestsForScheduling = [];
    field.classList.add('d-none');
    select.innerHTML = '<option value="">Lịch test độc lập</option>';
    document.getElementById('createStudentName').readOnly = false;

    const currentUser = typeof getCurrentUser === 'function' ? getCurrentUser() : null;
    const role = String(currentUser?.role || '').toUpperCase().replace('ROLE_', '');
    if (!['STAFF', 'BRANCH_MANAGER', 'ADMIN'].includes(role)) return;

    try {
        const requests = await callApi('/api/course-enrollment/staff/requests');
        enrollmentRequestsForScheduling = (requests || []).filter(request =>
            request.placementRequested && request.status === 'WAITING_PLACEMENT');
        for (const request of enrollmentRequestsForScheduling) {
            const option = document.createElement('option');
            option.value = String(request.id);
            option.textContent = `#${request.id} - ${request.childName}`;
            select.appendChild(option);
        }
        if (enrollmentRequestsForScheduling.length) field.classList.remove('d-none');
    } catch (error) {
        console.warn('Cannot load pending enrollment requests:', error);
    }
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
        const evalMediaInput = document.getElementById("evalMediaUrl");
        if (evalMediaInput) evalMediaInput.value = fileUrl;

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

function openEvaluateModal(id) {
    const item = placementTestsData.find(x => x.id === id);
    if (!item) return;

    // 1. STRICT ROLE & SUBJECT PERMISSION CHECK
    if (!isTeacher()) {
        showTeacherOnlyAlert();
        return;
    }

    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    const roleUpper = currentUser && currentUser.role ? String(currentUser.role).toUpperCase().replace("ROLE_", "") : "";

    if (roleUpper === "TEACHER") {
        const teacherSubject = normalizeSubject(currentUser.subject || "DAN");
        const itemSubject = normalizeSubject(item.subject || inferSubject(item.title));

        if (teacherSubject !== itemSubject) {
            showTeacherSubjectMismatchAlert(teacherSubject, itemSubject);
            return;
        }
    }

    currentEditingId = id;
    uploadedFileUrl = item.videoUrl || item.audioUrl || item.recordUrl || item.imageUrl || "";

    const fileInput = document.getElementById("evalAttachmentFile");
    if (fileInput) fileInput.value = "";

    document.getElementById("evalCandidateCode").innerText = `#PT-${item.id}`;
    document.getElementById("evalCandidateName").innerText = item.studentName || "Học viên";

    const normSubject = normalizeSubject(item.subject || inferSubject(item.title));
    const cfg = SUBJECT_EVALUATION_CONFIG[normSubject] || SUBJECT_EVALUATION_CONFIG['DAN'];
    const subjectName = cfg.subjectName;
    const teacherDisplayName = currentUser ? (currentUser.subject ? `GV ${getSubjectDisplayName(currentUser.subject)}` : 'GV') : '';
    const examinerInfo = currentUser ? `${currentUser.fullName || currentUser.username} (${teacherDisplayName})` : (item.evaluatedByName || 'Chưa phân công');
    document.getElementById("evalCandidateMeta").innerText = `Bộ môn: ${subjectName} | Tiêu đề: ${item.title || 'Test Năng Khiếu'} | Cơ sở: ${item.branch || 'Cơ sở 1'} | GV chấm: ${examinerInfo}`;

    const baseScore10 = item.score != null ? (item.score / 10) : 7.5;

    // Render dynamic subject-specific criteria sliders
    const evalContainer = document.getElementById("evalCriteriaContainer");
    if (evalContainer) {
        evalContainer.innerHTML = `
            <div class="d-flex align-items-center justify-content-between mb-3">
                <h6 class="fw-bold text-dark mb-0"><i class="bi ${cfg.headerIcon} me-2"></i>${cfg.headerTitle} (Thang điểm 0 - 10)</h6>
                <span class="badge ${cfg.badgeClass} rounded-pill px-3 py-1 fw-bold">${cfg.subjectName}</span>
            </div>
            <div class="row g-3 mb-4">
                ${cfg.criteria.map(c => `
                    <div class="col-md-6">
                        <div class="score-slider-card">
                            <div class="d-flex justify-content-between align-items-center mb-1">
                                <label class="fw-semibold text-dark small"><i class="bi ${c.icon} me-1"></i>${c.label}</label>
                                <span class="badge ${c.badgeClass} px-2" id="val_${c.id}">${baseScore10.toFixed(1)}</span>
                            </div>
                            <input type="range" class="form-range eval-criteria-slider" id="score_${c.id}" min="0" max="10" step="0.5" value="${baseScore10}" oninput="document.getElementById('val_${c.id}').innerText = parseFloat(this.value).toFixed(1); calculateAverageScore();">
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    calculateAverageScore();

    document.getElementById("evalLevelSelect").value = item.recommendedLevel || "BEGINNER";
    const notesEl = document.getElementById("evalExaminerNotes");
    if (notesEl) {
        notesEl.value = item.teacherNote || "";
        notesEl.placeholder = cfg.notePlaceholder;
    }
    document.getElementById("evalMediaUrl").value = uploadedFileUrl;

    const statusEl = document.getElementById("uploadStatusMessage");
    if (statusEl) statusEl.innerHTML = uploadedFileUrl ? `<span class="text-info"><i class="bi bi-link-45deg me-1"></i>Đã có file: <a href="${uploadedFileUrl}" target="_blank" class="text-info text-decoration-underline">${uploadedFileUrl}</a></span>` : "";

    const modal = new bootstrap.Modal(document.getElementById('evaluateModal'));
    modal.show();
}

async function handleSaveEvaluation(e) {
    e.preventDefault();

    if (!currentEditingId) return;
    const item = placementTestsData.find(x => x.id === currentEditingId);
    if (!item) return;

    // STRICT ROLE & SUBJECT CHECK
    if (!isTeacher()) {
        showTeacherOnlyAlert();
        return;
    }

    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    const roleUpper = currentUser && currentUser.role ? String(currentUser.role).toUpperCase().replace("ROLE_", "") : "";

    if (roleUpper === "TEACHER") {
        const teacherSubject = normalizeSubject(currentUser.subject || "DAN");
        const itemSubject = normalizeSubject(item.subject || inferSubject(item.title));
        if (teacherSubject !== itemSubject) {
            showTeacherSubjectMismatchAlert(teacherSubject, itemSubject);
            return;
        }
    }

    const teacherDisplayName = currentUser ? (currentUser.subject ? `GV ${getSubjectDisplayName(currentUser.subject)}` : 'GV') : '';
    const evaluatorName = currentUser ? `${currentUser.fullName || currentUser.username} (${teacherDisplayName})` : 'Giáo Viên Chuyên Môn';

    const sliders = document.querySelectorAll(".eval-criteria-slider");
    let total = 0;
    let count = 0;
    sliders.forEach(s => {
        total += parseFloat(s.value || 0);
        count++;
    });
    const avg10 = count > 0 ? (total / count) : 0;
    const score100 = Math.round(avg10 * 10);

    const recommendedLevel = document.getElementById("evalLevelSelect").value;
    const teacherNote = document.getElementById("evalExaminerNotes").value;
    const mediaUrlInput = document.getElementById("evalMediaUrl").value.trim();
    const mediaUrl = mediaUrlInput || uploadedFileUrl;

    const isAudio = /\.(mp3|wav|ogg|m4a)$/i.test(mediaUrl);
    const isVideo = /\.(mp4|webm|mov|avi|mkv)$/i.test(mediaUrl);
    const isImage = /\.(png|jpg|jpeg|gif|webp|svg)$/i.test(mediaUrl);

    const assessmentPayload = {
        score: score100,
        recommendedLevel: recommendedLevel,
        teacherNote: teacherNote,
        audioUrl: isAudio ? mediaUrl : null,
        videoUrl: isVideo ? mediaUrl : null,
        imageUrl: isImage ? mediaUrl : null,
        recordUrl: mediaUrl || null
    };

    let apiSavedResponse = null;
    if (typeof callApi === 'function') {
        try {
            apiSavedResponse = await callApi(`/api/placement-tests/${currentEditingId}/assess`, "POST", assessmentPayload);
        } catch (err) {
            console.error("Lỗi khi lưu kết quả vào Database:", err);
            showNotification(`Không thể lưu: ${err.message || "Lỗi phân quyền hoặc kết nối!"}`, "danger");
            return;
        }
    }

    if (apiSavedResponse) {
        const itemIndex = placementTestsData.findIndex(x => x.id === currentEditingId);
        if (itemIndex !== -1) {
            placementTestsData[itemIndex] = apiSavedResponse;
        }
    } else {
        item.score = score100;
        item.recommendedLevel = recommendedLevel;
        item.teacherNote = teacherNote;
        item.status = "COMPLETED";
        item.audioUrl = isAudio ? mediaUrl : "";
        item.videoUrl = isVideo ? mediaUrl : "";
        item.imageUrl = isImage ? mediaUrl : "";
        item.recordUrl = mediaUrl || "";
        item.evaluatedByName = evaluatorName;
        item.evaluatedAt = new Date().toISOString();
    }

    const modalEl = document.getElementById('evaluateModal');
    const modal = bootstrap.Modal.getInstance(modalEl);
    if (modal) modal.hide();

    showNotification(`Đã lưu thành công kết quả chấm điểm cho bài thi #PT-${currentEditingId}!`, "success");
    renderStats();
    filterPlacementTests();
}

async function openCreateModal() {
    const form = document.getElementById("createPlacementTestForm");
    if (form) form.reset();

    // Auto select teacher's subject if logged in as teacher
    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    const subjectSelect = document.getElementById("createSubject");
    if (subjectSelect && currentUser && currentUser.subject) {
        subjectSelect.value = normalizeSubject(currentUser.subject);
    }

    initDateRestrictions();
    const modal = new bootstrap.Modal(document.getElementById('createModal'));
    modal.show();
    await loadEnrollmentRequestsForScheduling();
}

async function handleCreatePlacementTest(e) {
    e.preventDefault();

    const studentName = document.getElementById("createStudentName").value.trim();
    const subject = normalizeSubject(document.getElementById("createSubject")?.value || "DAN");
    const title = document.getElementById("createTitle").value.trim();
    const roomName = document.getElementById("createRoomName").value.trim();
    const branch = document.getElementById("createBranch").value;
    const testDateVal = document.getElementById("createTestDate").value;
    const testTimeVal = document.getElementById("createTestTime").value;
    const note = document.getElementById("createNote").value.trim();
    const enrollmentRequestId = Number(document.getElementById('createEnrollmentRequest')?.value) || null;

    // STRICT VALIDATION: Time cannot be in the past calculated at this exact moment
    const todayStr = getTodayDateString();
    if (testDateVal < todayStr) {
        showNotification("Ngày hẹn test không được chọn trong quá khứ! Vui lòng chọn từ ngày hôm nay trở đi.", "danger");
        document.getElementById("createTestDate").focus();
        initDateRestrictions();
        return;
    }

    const selectedDateTime = new Date(`${testDateVal}T${testTimeVal}:00`);
    const now = new Date();

    if (isNaN(selectedDateTime.getTime()) || selectedDateTime <= now) {
        showNotification("Thời gian hẹn test không được ở trong quá khứ tính từ lúc đang chọn! Vui lòng chọn ngày và giờ từ hiện tại trở đi.", "danger");
        document.getElementById("createTestDate").focus();
        updateTimeMinRestriction();
        return;
    }

    const isoDateTime = `${testDateVal}T${testTimeVal}:00`;

    const createPayload = {
        studentName: studentName,
        subject: subject,
        title: title,
        roomName: roomName,
        branch: branch,
        testDate: isoDateTime,
        note: note,
        enrollmentRequestId
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
    if (enrollmentRequestId && typeof callApi !== 'function') {
        showNotification('Không thể kết nối để xếp lịch cho yêu cầu này.', 'danger');
        return;
    }
    if (typeof callApi === 'function') {
        try {
            createdResponse = await callApi('/api/placement-tests', "POST", createPayload);
        } catch (err) {
            console.error("Create test error:", err);
            showNotification(`Lỗi tạo đơn đăng ký: ${err.message || "Thời gian không hợp lệ"}`, "danger");
            return;
        }
    }

    if (enrollmentRequestId && !createdResponse) {
        showNotification('Không thể xếp lịch cho yêu cầu này. Hãy làm mới danh sách đơn.', 'danger');
        return;
    }

    const savedRecord = (createdResponse && createdResponse.id) ? createdResponse : newTest;
    savedRecord.subject = normalizeSubject(savedRecord.subject || subject || inferSubject(savedRecord.title));

    // Add to dataset
    placementTestsData.unshift(savedRecord);

    const modalEl = document.getElementById('createModal');
    const modal = bootstrap.Modal.getInstance(modalEl);
    if (modal) modal.hide();

    // IMMEDIATELY re-filter and render UI without page refresh
    renderStats();
    filterPlacementTests();

    // Check if the created test belongs to current teacher's subject
    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    const roleUpper = currentUser && currentUser.role ? String(currentUser.role).toUpperCase().replace("ROLE_", "") : "";

    if (roleUpper === "TEACHER") {
        const teacherSubject = normalizeSubject(currentUser.subject || "DAN");
        const testSubject = normalizeSubject(savedRecord.subject);

        if (teacherSubject !== testSubject) {
            const testDisplayName = getSubjectDisplayName(testSubject);
            const teacherDisplayName = getSubjectDisplayName(teacherSubject);
            showNotification(`Đã tạo đơn đăng ký ca test #PT-${savedRecord.id} (Bộ Môn: ${testDisplayName}) thành công! ⚠️ Vì ca test này thuộc Bộ môn [${testDisplayName}] khác bộ môn [${teacherDisplayName}] của bạn, ca thi đã TỰ ĐỘNG ẨN khỏi danh sách chấm của bạn.`, "warning");
            return;
        }
    }

    const testSubjectDisplayName = getSubjectDisplayName(savedRecord.subject);
    showNotification(`Đã đăng ký lịch kiểm tra mới cho học viên ${studentName} (Bộ môn ${testSubjectDisplayName}) thành công!`, "success");
}

function openDetailModal(id) {
    const item = placementTestsData.find(x => x.id === id);
    if (!item) return;

    document.getElementById("detailCode").innerText = `#PT-${item.id}`;
    document.getElementById("detailStudentName").innerText = item.studentName || "Học viên";
    document.getElementById("detailTitle").innerText = item.title || "Đánh giá xếp lớp";
    document.getElementById("detailParentName").innerText = item.parentName || item.parentEmail || "Không có thông tin";
    document.getElementById("detailBranchRoom").innerText = `${item.branch || 'Cơ sở'} - ${item.roomName || 'Phòng học'}`;

    const formattedDT = formatISOToDateTime(item.testDate);
    document.getElementById("detailTestDateTime").innerText = `${formattedDT.date} lúc ${formattedDT.time}`;
    document.getElementById("detailExaminer").innerText = item.evaluatedByName || "Chưa đánh giá";

    const detailScoreEl = document.getElementById("detailTotalScore");
    if (detailScoreEl) {
        detailScoreEl.innerText = item.score != null ? `${(item.score / 10).toFixed(1)} / 10 (${item.score}đ)` : "Chưa chấm điểm";
    }

    let levelText = "Chưa xếp trình độ";
    if (item.recommendedLevel === 'BEGINNER') levelText = "Sơ Cấp (Beginner)";
    else if (item.recommendedLevel === 'INTERMEDIATE') levelText = "Trung Cấp (Intermediate)";
    else if (item.recommendedLevel === 'ADVANCED') levelText = "Nâng Cao (Advanced)";

    document.getElementById("detailLevel").innerText = levelText;
    document.getElementById("detailNotes").innerText = item.teacherNote || item.note || "Chưa có ghi chú.";

    const baseScore = item.score != null ? item.score : 0;
    const normSubject = normalizeSubject(item.subject || inferSubject(item.title));
    const cfg = SUBJECT_EVALUATION_CONFIG[normSubject] || SUBJECT_EVALUATION_CONFIG['DAN'];

    const detailCriteriaContainer = document.getElementById("detailCriteriaContainer");
    if (detailCriteriaContainer) {
        detailCriteriaContainer.innerHTML = `
            <div class="d-flex align-items-center justify-content-between mb-3">
                <h6 class="fw-bold text-dark mb-0"><i class="bi ${cfg.headerIcon} me-2"></i>Điểm số 4 tiêu chí [${cfg.subjectName}]</h6>
                <span class="badge ${cfg.badgeClass} rounded-pill px-3 py-1 fw-bold">${cfg.subjectName}</span>
            </div>
            <div class="row g-3 mb-4">
                ${cfg.criteria.map(c => `
                    <div class="col-md-6">
                        <small class="fw-semibold text-muted d-flex justify-content-between">
                            <span><i class="bi ${c.icon} me-1"></i>${c.label}</span>
                            <strong class="text-dark">${(baseScore / 10).toFixed(1)}/10</strong>
                        </small>
                        <div class="progress mt-1" style="height: 8px;">
                            <div class="progress-bar ${c.badgeClass.split(' ')[0]}" role="progressbar" style="width: ${baseScore}%"></div>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    const mediaContainer = document.getElementById("detailMediaAttachment");
    const mediaUrl = item.videoUrl || item.audioUrl || item.recordUrl || item.imageUrl;
    if (mediaContainer) {
        if (mediaUrl) {
            mediaContainer.innerHTML = `
                <div class="alert alert-info py-2 px-3 small d-flex align-items-center justify-content-between mb-0">
                    <span><i class="bi bi-paperclip me-1"></i>File đính kèm bài thi / ghi âm:</span>
                    <a href="${mediaUrl}" target="_blank" class="btn btn-sm btn-info text-dark fw-bold rounded-pill">
                        <i class="bi bi-download me-1"></i>Xem / Tải file
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

function setProgressBar(barId, valId, score100) {
    const bar = document.getElementById(barId);
    const val = document.getElementById(valId);
    const percent = Math.min(Math.max(score100, 0), 100);
    if (bar) bar.style.width = `${percent}%`;
    if (val) val.innerText = `${(score100 / 10).toFixed(1)}/10`;
}

function showNotification(msg, type = "info") {
    const toastContainer = document.getElementById("toastContainer");
    if (!toastContainer) return;

    let bgClass = "bg-primary text-white";
    let iconClass = "bi-info-circle-fill";
    if (type === "success") {
        bgClass = "bg-success text-white";
        iconClass = "bi-check-circle-fill";
    } else if (type === "danger") {
        bgClass = "bg-danger text-white";
        iconClass = "bi-exclamation-triangle-fill";
    } else if (type === "warning") {
        bgClass = "bg-warning text-dark";
        iconClass = "bi-exclamation-diamond-fill";
    }

    const toastHtml = `
        <div class="toast align-items-center ${bgClass} border-0 shadow-lg mb-2" role="alert" aria-live="assertive" aria-atomic="true">
            <div class="d-flex">
                <div class="toast-body font-medium">
                    <i class="bi ${iconClass} me-2"></i>${msg}
                </div>
                <button type="button" class="btn-close ${type === 'warning' ? '' : 'btn-close-white'} me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
        </div>
    `;
    toastContainer.insertAdjacentHTML("beforeend", toastHtml);
    const lastToast = toastContainer.lastElementChild;
    const bsToast = new bootstrap.Toast(lastToast, { delay: 5000 });
    bsToast.show();
}
