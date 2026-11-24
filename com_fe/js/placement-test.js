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
let cancellingScheduleId = null;
let placementCancelling = false;
let uploadedFileUrl = "";
let attachmentUploadVersion = 0;
let attachmentUploading = false;
let evaluationBlockedByMissingCriteria = false;

function updateEvaluationSubmitButtonState() {
    const save = document.querySelector("#evaluatePlacementTestForm button[type=submit]");
    if (save) {
        save.disabled = Boolean(attachmentUploading || evaluationBlockedByMissingCriteria);
    }
}

function setAttachmentUploading(busy) {
    attachmentUploading = busy;
    updateEvaluationSubmitButtonState();
}

function escapeCourseOption(value) {
    return String(value ?? '').replace(/[&<>"']/g, char => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[char]);
}

document.addEventListener("DOMContentLoaded", () => {
    // 1. Strict Permission Check: Block PARENT role completely
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

    loadPlacementCriteria();
    loadPlacementTests();
    loadBranchesForScheduling();
    setupEventListeners();
    configureAdminCriteriaButton();
});

const WORKING_HOURS_START = "08:00";
const WORKING_HOURS_END = "17:30";

function isWithinWorkingHours(timeStr) {
    if (!timeStr) return false;
    const hm = String(timeStr).slice(0, 5);
    return hm >= WORKING_HOURS_START && hm <= WORKING_HOURS_END;
}

function getTodayDateString() {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
}

function getDateString(dateObj) {
    const yyyy = dateObj.getFullYear();
    const mm = String(dateObj.getMonth() + 1).padStart(2, '0');
    const dd = String(dateObj.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
}

function initDateRestrictions() {
    const testDateInput = document.getElementById("createTestDate");
    const testTimeInput = document.getElementById("createTestTime");
    if (!testDateInput) return;

    const now = new Date();
    const todayStr = getTodayDateString();
    const laterDate = new Date(now.getTime() + 15 * 60000);
    const laterTimeStr = `${String(laterDate.getHours()).padStart(2, '0')}:${String(laterDate.getMinutes()).padStart(2, '0')}`;
    const todayWorkingHoursEnded = getDateString(laterDate) > todayStr || laterTimeStr > WORKING_HOURS_END;

    const minAllowedDateStr = todayWorkingHoursEnded
        ? getDateString(new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1))
        : todayStr;

    // Set strict min date
    testDateInput.min = minAllowedDateStr;
    testDateInput.setAttribute("min", minAllowedDateStr);

    if (!testDateInput.value || testDateInput.value < minAllowedDateStr) {
        testDateInput.value = minAllowedDateStr;
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

    testTimeInput.max = WORKING_HOURS_END;
    testTimeInput.setAttribute("max", WORKING_HOURS_END);

    if (testDateInput.value === todayStr) {
        const laterDate = new Date(now.getTime() + 15 * 60000);
        const laterTimeStr = `${String(laterDate.getHours()).padStart(2, '0')}:${String(laterDate.getMinutes()).padStart(2, '0')}`;

        if (getDateString(laterDate) > todayStr || laterTimeStr > WORKING_HOURS_END) {
            const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
            const tomorrowStr = getDateString(tomorrow);
            testDateInput.min = tomorrowStr;
            testDateInput.setAttribute("min", tomorrowStr);
            testDateInput.value = tomorrowStr;
            testTimeInput.min = WORKING_HOURS_START;
            testTimeInput.setAttribute("min", WORKING_HOURS_START);
            if (!isWithinWorkingHours(testTimeInput.value)) {
                testTimeInput.value = "09:30";
            }
            return;
        }

        const currentHours = String(now.getHours()).padStart(2, '0');
        const currentMinutes = String(now.getMinutes()).padStart(2, '0');
        const currentTimeStr = `${currentHours}:${currentMinutes}`;
        const minTime = currentTimeStr > WORKING_HOURS_START ? currentTimeStr : WORKING_HOURS_START;

        testTimeInput.min = minTime;
        testTimeInput.setAttribute("min", minTime);

        if (!testTimeInput.value || testTimeInput.value < minTime) {
            testTimeInput.value = laterTimeStr >= WORKING_HOURS_START ? laterTimeStr : WORKING_HOURS_START;
        } else if (testTimeInput.value > WORKING_HOURS_END) {
            testTimeInput.value = WORKING_HOURS_END;
        }
    } else if (testDateInput.value < todayStr) {
        testDateInput.value = todayStr;
        updateTimeMinRestriction();
    } else {
        testTimeInput.min = WORKING_HOURS_START;
        testTimeInput.setAttribute("min", WORKING_HOURS_START);
        if (!testTimeInput.value || testTimeInput.value < WORKING_HOURS_START) {
            testTimeInput.value = "09:30";
        } else if (testTimeInput.value > WORKING_HOURS_END) {
            testTimeInput.value = WORKING_HOURS_END;
        }
    }
}

function validateRealtimeDateTime() {
    const testDateInput = document.getElementById("createTestDate");
    const testTimeInput = document.getElementById("createTestTime");
    if (!testDateInput || !testTimeInput) return;

    const testDateVal = testDateInput.value;
    const testTimeVal = testTimeInput.value;
    if (!testDateVal || !testTimeVal) return;

    if (!isWithinWorkingHours(testTimeVal)) {
        showNotification(`Giờ hẹn test chỉ được phép đặt trong giờ hành chính làm việc (${WORKING_HOURS_START} - ${WORKING_HOURS_END})!`, "warning");
        if (testTimeVal.slice(0, 5) < WORKING_HOURS_START) {
            testTimeInput.value = WORKING_HOURS_START;
        } else if (testTimeVal.slice(0, 5) > WORKING_HOURS_END) {
            testTimeInput.value = WORKING_HOURS_END;
        }
    }

    const selectedDateTime = new Date(`${testDateVal}T${testTimeInput.value}:00`);
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

    if (roleUpper === "PARENT") {
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

const SUBJECT_STYLE_META = {
    'DAN': {
        subjectName: 'Bộ Môn Đàn',
        badgeClass: 'bg-primary-subtle text-primary border border-primary-subtle',
        headerIcon: 'bi-music-note-beamed text-primary',
        notePlaceholder: 'Nhận xét chi tiết về cảm âm, nhịp phách, kỹ thuật phím đàn, thị tấu và gợi ý lớp học phù hợp...'
    },
    'MUA': {
        subjectName: 'Bộ Môn Múa',
        badgeClass: 'bg-danger-subtle text-danger border border-danger-subtle',
        headerIcon: 'bi-heart-pulse-fill text-danger',
        notePlaceholder: 'Nhận xét chi tiết về độ dẻo dai cơ thể, khả năng cảm thụ âm nhạc, phom dáng và gợi ý lớp múa phù hợp...'
    },
    'VO': {
        subjectName: 'Bộ Môn Võ Thuật',
        badgeClass: 'bg-warning-subtle text-warning-emphasis border border-warning-subtle',
        headerIcon: 'bi-shield-fill-check text-warning',
        notePlaceholder: 'Nhận xét chi tiết về thể lực, phản xạ, tấn pháp đòn thế, tinh thần kỷ luật võ đạo và gợi ý cấp đai xếp lớp...'
    }
};

const CRITERIA_BADGE_PALETTE = [
    { icon: 'bi-check2-circle text-primary', badgeClass: 'bg-primary text-white' },
    { icon: 'bi-star-fill text-warning', badgeClass: 'bg-warning text-dark' },
    { icon: 'bi-award-fill text-success', badgeClass: 'bg-success text-white' },
    { icon: 'bi-lightning-charge-fill text-info', badgeClass: 'bg-info text-dark' }
];

let placementCriteriaBySubject = { DAN: [], MUA: [], VO: [] };
let adminCriteriaList = [];

function isAdmin() {
    let currentUser = null;
    if (typeof getCurrentUser === 'function') {
        currentUser = getCurrentUser();
    }
    if (!currentUser || !currentUser.role) return false;
    const roleUpper = String(currentUser.role).toUpperCase().replace("ROLE_", "");
    return roleUpper === "ADMIN";
}

function configureAdminCriteriaButton() {
    const btn = document.getElementById("btnManageCriteria");
    if (btn) {
        btn.classList.toggle("d-none", !isAdmin());
    }
}

async function loadPlacementCriteria(subject = null) {
    if (typeof callApi !== 'function') return [];
    try {
        const endpoint = subject
            ? `/api/placement-tests/criteria?subject=${encodeURIComponent(normalizeSubject(subject))}`
            : '/api/placement-tests/criteria';
        const result = await callApi(endpoint, 'GET', null, false, false);
        if (Array.isArray(result)) {
            if (subject) {
                const norm = normalizeSubject(subject);
                placementCriteriaBySubject[norm] = result;
            } else {
                const grouped = { DAN: [], MUA: [], VO: [] };
                for (const item of result) {
                    const s = normalizeSubject(item.subject || 'DAN');
                    if (!grouped[s]) grouped[s] = [];
                    grouped[s].push(item);
                }
                placementCriteriaBySubject = grouped;
            }
            return result;
        }
    } catch (err) {
        console.warn("Không tải được tiêu chí đánh giá từ máy chủ:", err);
    }
    return [];
}

function getCriteriaStyle(index) {
    return CRITERIA_BADGE_PALETTE[index % CRITERIA_BADGE_PALETTE.length];
}

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
    return roleUpper === "TEACHER";
}

function isTeacherAllowedForTest(item) {
    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();
    if (!currentUser || !currentUser.role) return false;

    const roleUpper = String(currentUser.role).toUpperCase().replace("ROLE_", "");
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

    const msg = `Quyền truy cập bị từ chối: Tài khoản của bạn hiện có vai trò là [${currentRoleName}]. Quản trị viên chỉ có quyền theo dõi danh sách; chỉ Giáo Viên (TEACHER) đúng bộ môn mới được phép thực hiện chấm điểm & đánh giá bài thi Placement Test.`;

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
            if (item.cancelReason) {
                statusBadge += `<div class="small text-danger mt-1 text-truncate" style="max-width: 160px;" title="Lý do hủy: ${escapeCourseOption(item.cancelReason)}"><i class="bi bi-info-circle me-1"></i>${escapeCourseOption(item.cancelReason)}</div>`;
            }
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
                    <div class="mb-1">${subjectBadge}${item.programName ? ` <span class="small fw-semibold">${escapeCourseOption(item.programName)}</span>` : ''}</div>
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
                        ${item.status === 'CANCELLED' ? `
                            <button class="btn btn-outline-secondary opacity-50" disabled title="Đơn đã bị hủy">
                                <i class="bi bi-slash-circle me-1"></i>Đã Hủy
                            </button>
                        ` : canGradeThisItem ? `
                            <button class="btn btn-warning text-dark fw-semibold" onclick="openEvaluateModal(${item.id})" title="Chấm Điểm (Giáo Viên Bộ Môn)">
                                <i class="bi bi-pencil-square me-1"></i>${item.status === 'COMPLETED' ? 'Sửa' : 'Chấm'}
                            </button>
                        ` : `
                            <button class="btn btn-outline-secondary opacity-50" onclick="showTeacherOnlyAlert()" title="Chỉ Giáo Viên bộ môn này mới có quyền chấm điểm">
                                <i class="bi bi-lock-fill me-1"></i>Chấm
                            </button>
                        `}
                        ${item.status === 'SCHEDULED' ? `
                            <button class="btn btn-outline-danger" onclick="openCancelPlacementModal(${item.id})" title="Hủy đơn Placement Test">
                                <i class="bi bi-x-circle me-1"></i>Hủy
                            </button>
                        ` : ''}
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

    const branchSelect = document.getElementById("createBranch");
    if (branchSelect) {
        branchSelect.addEventListener("change", onBranchChange);
    }

    const programSelect = document.getElementById("createProgram");
    if (programSelect) {
        programSelect.addEventListener("change", () => renderRoomOptionsForBranch(currentBranchRooms));
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
            attachmentUploadVersion++;
            evaluationBlockedByMissingCriteria = false;
            setAttachmentUploading(false);
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
    document.getElementById('createParentNote').classList.toggle('d-none', !selected);
    document.getElementById('createParentNoteText').textContent = selected?.note || 'Không có ghi chú.';
    const programSelect = document.getElementById('createProgram');
    const programId = selected?.requestedProgramId;
    programSelect.disabled = Boolean(programId);
    programSelect.value = programId ? String(programId) : '';
    document.getElementById('createProgramHint').textContent = programId
        ? (programSelect.value ? 'Môn đã đăng ký trong đơn.' : `${selected.requestedProgramName} chưa có trong danh sách hỗ trợ. Hãy kiểm tra cấu hình hoặc thử mở lại form.`)
        : (selected ? 'Đơn cũ chưa có môn. Hãy xác nhận với phụ huynh trước khi chọn.' : '');
    if (currentBranchRooms.length > 0) {
        renderRoomOptionsForBranch(currentBranchRooms);
    }
}

async function loadProgramsForScheduling() {
    const select = document.getElementById('createProgram');
    select.replaceChildren(new Option('Đang tải môn kiểm tra...', ''));
    try {
        const programs = await callApi('/api/course-enrollment/placement-programs');
        select.options[0].text = programs?.length ? 'Chọn môn kiểm tra' : 'Chưa có môn hỗ trợ Placement Test';
        for (const program of programs || []) select.add(new Option(program.name, program.id));
    } catch {
        select.options[0].text = 'Chưa tải được môn. Hãy mở lại form để thử lại.';
    }
    if (document.getElementById('createEnrollmentRequest').value) onEnrollmentRequestChange();
}

async function loadEnrollmentRequestsForScheduling() {
    const field = document.getElementById('createEnrollmentRequestField');
    const select = document.getElementById('createEnrollmentRequest');
    enrollmentRequestsForScheduling = [];
    field.classList.add('d-none');
    select.innerHTML = '<option value="">Lịch test độc lập</option>';
    onEnrollmentRequestChange();

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
            option.textContent = `#${request.id} - ${request.childName}${request.requestedProgramName ? ' · ' + request.requestedProgramName : ''}`;
            select.appendChild(option);
        }
        if (enrollmentRequestsForScheduling.length) field.classList.remove('d-none');
    } catch (error) {
        console.warn('Cannot load pending enrollment requests:', error);
    }
}

let branchesForScheduling = [];
let currentBranchRooms = [];
let schedulingBranchLoad = 0;
let schedulingRoomLoad = 0;

function getSelectedProgramNameForScheduling() {
    const programSelect = document.getElementById('createProgram');
    if (programSelect && programSelect.value) {
        const opt = programSelect.options[programSelect.selectedIndex];
        if (opt && opt.text) return opt.text.trim();
    }
    const requestId = Number(document.getElementById('createEnrollmentRequest')?.value);
    if (requestId) {
        const selectedReq = enrollmentRequestsForScheduling.find(r => r.id === requestId);
        if (selectedReq?.requestedProgramName) return selectedReq.requestedProgramName.trim();
    }
    return '';
}

function getProgramRoomCategory(programText) {
    const p = String(programText || '').toLowerCase().trim();
    if (!p) return null;
    if (p.includes('guitar')) return 'GUITAR';
    if (p.includes('piano') || p.includes('organ')) return 'PIANO';
    if (p.includes('múa') || p.includes('mua') || p.includes('dance') || p.includes('ballet')) return 'MUA';
    if (p.includes('võ') || p.includes('vo') || p.includes('martial') || p.includes('taekwondo')) return 'VO';
    if (p.includes('đàn') || p.includes('dan')) return 'DAN';
    return null;
}

function doesRoomMatchCategory(room, category) {
    if (!category || !room) return true;
    const rType = String(room.roomType || '').toUpperCase();
    const rText = `${room.roomName || ''} ${room.roomCode || ''}`.toLowerCase();

    if (category === 'GUITAR') {
        return rType === 'GUITAR_ROOM' || rText.includes('guitar') || (rText.includes('đàn') && !rText.includes('piano'));
    }
    if (category === 'PIANO') {
        return rType === 'PIANO_INDIVIDUAL' || rType === 'PIANO_GROUP' || rText.includes('piano') || (rText.includes('đàn') && !rText.includes('guitar'));
    }
    if (category === 'DAN') {
        return rType === 'GUITAR_ROOM' || rType === 'PIANO_INDIVIDUAL' || rType === 'PIANO_GROUP'
            || rText.includes('piano') || rText.includes('guitar') || rText.includes('đàn');
    }
    if (category === 'MUA') {
        return rType === 'DANCE_ROOM' || rText.includes('múa') || rText.includes('mua') || rText.includes('ballet') || rText.includes('dance');
    }
    if (category === 'VO') {
        return rText.includes('võ') || rText.includes('vo') || rText.includes('thể lực')
            || (rType === 'THEORY_ROOM' && !rText.includes('piano') && !rText.includes('guitar') && !rText.includes('múa'));
    }
    return true;
}

function isSubjectSpecificRoom(room) {
    const rType = String(room?.roomType || '').toUpperCase();
    if (['PIANO_INDIVIDUAL', 'PIANO_GROUP', 'GUITAR_ROOM', 'DANCE_ROOM', 'THEORY_ROOM'].includes(rType)) {
        return true;
    }
    const rText = `${room?.roomName || ''} ${room?.roomCode || ''}`.toLowerCase();
    return rText.includes('piano') || rText.includes('guitar') || rText.includes('đàn')
        || rText.includes('múa') || rText.includes('ballet') || rText.includes('võ');
}

function renderRoomOptionsForBranch(roomList) {
    const roomSelect = document.getElementById('createRoomName');
    if (!roomSelect) return;

    if (!Array.isArray(roomList) || roomList.length === 0) {
        roomSelect.replaceChildren(new Option('-- Cơ sở này chưa có phòng học --', ''));
        roomSelect.disabled = true;
        return;
    }

    const availableRooms = roomList.filter(r => !r.status || r.status === 'AVAILABLE');
    const baseList = availableRooms.length > 0 ? availableRooms : roomList;
    const programName = getSelectedProgramNameForScheduling();
    const category = getProgramRoomCategory(programName);

    let displayList = baseList;
    if (category) {
        const matched = baseList.filter(r => doesRoomMatchCategory(r, category));
        if (matched.length > 0) {
            displayList = matched;
        } else if (baseList.some(isSubjectSpecificRoom)) {
            roomSelect.replaceChildren(new Option(`-- Cơ sở này chưa có phòng học môn ${programName} --`, ''));
            roomSelect.disabled = true;
            return;
        }
    }

    roomSelect.replaceChildren(new Option('-- Chọn phòng học --', ''));
    for (const r of displayList) {
        const roomNameText = r.roomName || r.roomCode || 'Phòng học';
        const hasCode = r.roomCode && !roomNameText.includes(r.roomCode);
        const label = hasCode ? `${roomNameText} (${r.roomCode})` : roomNameText;
        const statusNote = (r.status && r.status !== 'AVAILABLE') ? ` [${r.status}]` : '';
        roomSelect.add(new Option(label + statusNote, label));
    }
    roomSelect.selectedIndex = 1;
    roomSelect.disabled = false;
}

async function loadBranchesForScheduling(force = false) {
    const branchSelect = document.getElementById('createBranch');
    const roomSelect = document.getElementById('createRoomName');
    if (!branchSelect) return;
    const load = ++schedulingBranchLoad;
    ++schedulingRoomLoad;

    if (!force && branchesForScheduling.length > 0 && branchSelect.options.length > 1) {
        branchSelect.value = String(branchesForScheduling[0].id);
        await onBranchChange();
        return;
    }

    branchSelect.replaceChildren(new Option('Đang tải cơ sở...', ''));
    if (roomSelect) {
        roomSelect.replaceChildren(new Option('-- Vui lòng chọn cơ sở trước --', ''));
        roomSelect.disabled = true;
    }

    try {
        const branches = await callApi('/api/branches');
        if (load !== schedulingBranchLoad) return;
        branchesForScheduling = (Array.isArray(branches) ? branches : []).filter(b => b.active !== false);
        if (branchesForScheduling.length > 0) {
            branchSelect.replaceChildren(new Option('-- Chọn cơ sở đào tạo --', ''));
            for (const b of branchesForScheduling) {
                const text = b.name + (b.address ? ` - ${b.address}` : '');
                const opt = new Option(text, String(b.id));
                opt.dataset.branchName = b.name;
                branchSelect.add(opt);
            }
            branchSelect.value = String(branchesForScheduling[0].id);
            await onBranchChange();
        } else {
            branchSelect.options[0].text = 'Chưa có cơ sở nào khả dụng';
            if (roomSelect) {
                roomSelect.options[0].text = 'Chưa có phòng học';
                roomSelect.disabled = true;
            }
        }
    } catch (error) {
        if (load !== schedulingBranchLoad) return;
        console.warn('Lỗi tải danh sách cơ sở:', error);
        branchSelect.options[0].text = 'Chưa tải được cơ sở. Vui lòng thử lại.';
        if (roomSelect) {
            roomSelect.options[0].text = 'Chưa có phòng học';
            roomSelect.disabled = true;
        }
    }
}

async function onBranchChange() {
    const branchSelect = document.getElementById('createBranch');
    const roomSelect = document.getElementById('createRoomName');
    if (!branchSelect || !roomSelect) return;

    const load = ++schedulingRoomLoad;
    const branchId = branchSelect.value;
    if (!branchId) {
        roomSelect.replaceChildren(new Option('-- Vui lòng chọn cơ sở trước --', ''));
        roomSelect.disabled = true;
        return;
    }

    roomSelect.disabled = true;
    roomSelect.replaceChildren(new Option('Đang tải phòng học...', ''));

    try {
        const rooms = await callApi(`/api/branches/${encodeURIComponent(branchId)}/rooms`);
        if (load !== schedulingRoomLoad || branchSelect.value !== branchId) return;
        currentBranchRooms = Array.isArray(rooms) ? rooms : [];
        renderRoomOptionsForBranch(currentBranchRooms);
    } catch (error) {
        if (load !== schedulingRoomLoad || branchSelect.value !== branchId) return;
        currentBranchRooms = [];
        console.warn('Lỗi tải danh sách phòng học:', error);
        roomSelect.options[0].text = 'Chưa tải được phòng học';
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
    if (file.size > 50 * 1024 * 1024) {
        if (statusEl) statusEl.textContent = "File vượt quá 50 MB. Vui lòng chọn file nhỏ hơn.";
        e.target.value = "";
        return;
    }
    const version = ++attachmentUploadVersion;
    const assessmentId = currentEditingId;
    setAttachmentUploading(true);
    if (statusEl) { statusEl.className = "small mt-1"; statusEl.textContent = "Đang tải file lên máy chủ..."; }
    try {
        const formData = new FormData();
        formData.append("file", file);
        const res = await callApi('/api/placement-tests/upload', 'POST', formData, true, false);
        if (version !== attachmentUploadVersion || assessmentId !== currentEditingId) return;
        if (!res || typeof res.fileUrl !== 'string' || !res.fileUrl.startsWith('/uploads/')) {
            throw new Error("Máy chủ chưa trả về file đã lưu. Vui lòng thử lại.");
        }
        uploadedFileUrl = res.fileUrl;
        document.getElementById("evalMediaUrl").value = res.fileUrl;
        if (statusEl) {
            statusEl.replaceChildren();
            const text = document.createElement('span');
            text.className = 'text-success';
            text.textContent = 'Đã tải thành công: ';
            const link = document.createElement('a');
            link.href = (typeof BASE_URL === 'string' ? BASE_URL : '') + res.fileUrl;
            link.target = '_blank';
            link.rel = 'noopener';
            link.textContent = file.name;
            text.append(link);
            statusEl.append(text);
        }
    } catch (err) {
        if (version !== attachmentUploadVersion || assessmentId !== currentEditingId) return;
        if (statusEl) {
            statusEl.className = 'small mt-1 text-danger';
            statusEl.textContent = 'Chưa tải được file: ' + (err.message || 'Vui lòng thử lại.')
                + ' Nội dung chấm điểm vẫn được giữ nguyên.';
        }
        e.target.value = "";
    } finally {
        if (version === attachmentUploadVersion) setAttachmentUploading(false);
    }
}

async function openEvaluateModal(id) {
    const item = placementTestsData.find(x => x.id === id);
    if (!item) return;

    if (item.status === 'CANCELLED') {
        showNotification('Không thể chấm điểm cho đơn Placement Test đã bị hủy!', 'warning');
        return;
    }

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

    attachmentUploadVersion++;
    setAttachmentUploading(false);
    currentEditingId = id;
    uploadedFileUrl = item.videoUrl || item.audioUrl || item.recordUrl || item.imageUrl || "";

    const fileInput = document.getElementById("evalAttachmentFile");
    if (fileInput) fileInput.value = "";

    document.getElementById("evalCandidateCode").innerText = `#PT-${item.id}`;
    document.getElementById("evalCandidateName").innerText = item.studentName || "Học viên";

    const normSubject = normalizeSubject(item.subject || inferSubject(item.title));
    const cfg = SUBJECT_STYLE_META[normSubject] || SUBJECT_STYLE_META['DAN'];
    const subjectName = cfg.subjectName;
    const teacherDisplayName = currentUser ? (currentUser.subject ? `GV ${getSubjectDisplayName(currentUser.subject)}` : 'GV') : '';
    const examinerInfo = currentUser ? `${currentUser.fullName || currentUser.username} (${teacherDisplayName})` : (item.evaluatedByName || 'Chưa phân công');
    document.getElementById("evalCandidateMeta").innerText = `Môn: ${item.programName || subjectName} | Tiêu đề: ${item.title || 'Test Năng Khiếu'} | Cơ sở: ${item.branch || 'Cơ sở 1'} | GV chấm: ${examinerInfo}`;

    const baseScore10 = item.score != null ? (item.score / 10) : 7.5;

    // Luôn tải mới danh sách tiêu chí của bộ môn để phát hiện ngay nếu Admin vừa xóa hết tiêu chí
    const activeCriteria = await loadPlacementCriteria(normSubject);
    const savedScores = Array.isArray(item?.criteriaScores) ? item.criteriaScores : [];
    const isCompletedWithSnapshots = item?.status === 'COMPLETED' && savedScores.length > 0;

    if ((!activeCriteria || activeCriteria.length === 0) && !isCompletedWithSnapshots) {
        showNotification(`Không thể chấm điểm: ${subjectName} hiện đã bị xóa hết tiêu chí đánh giá! Vui lòng liên hệ Quản trị viên (ADMIN) thêm tiêu chí trước khi chấm.`, 'danger');
    }

    renderEvaluateCriteriaSliders(normSubject, activeCriteria, item, baseScore10);

    document.getElementById("evalLevelSelect").value = item.recommendedLevel || "BEGINNER";
    const courseSelect = document.getElementById("evalCourseSelect");
    courseSelect.required = item.enrollmentRequestId != null;
    document.getElementById("evalCourseRequired").classList.toggle("d-none", !courseSelect.required);
    courseSelect.innerHTML = '<option value="">Đang tải khóa học...</option>';
    try {
        const courses = await callApi('/api/course-enrollment/courses');
        const eligibleCourses = (courses || []).filter(course => !item.programId || course.programId === item.programId);
        courseSelect.innerHTML = '<option value="">Chọn khóa học phù hợp</option>' +
            eligibleCourses.map(course => `<option value="${course.id}">${escapeCourseOption(course.programName)} — ${escapeCourseOption(course.name)}</option>`).join('');
        courseSelect.value = item.recommendedCourseId == null ? '' : String(item.recommendedCourseId);
    } catch (error) {
        courseSelect.innerHTML = '<option value="">Không tải được khóa học</option>';
        showNotification(`Không tải được khóa học: ${error.message}`, 'danger');
    }
    const notesEl = document.getElementById("evalExaminerNotes");
    if (notesEl) {
        notesEl.value = item.teacherNote || "";
        notesEl.placeholder = cfg.notePlaceholder;
    }
    document.getElementById("evalMediaUrl").value = uploadedFileUrl;

    const statusEl = document.getElementById("uploadStatusMessage");
    const existingFileHref = uploadedFileUrl.startsWith("/uploads/") ? (typeof BASE_URL === "string" ? BASE_URL : "") + uploadedFileUrl : uploadedFileUrl;
    if (statusEl) statusEl.innerHTML = uploadedFileUrl ? `<span class="text-info"><i class="bi bi-link-45deg me-1"></i>Đã có file: <a href="${escapeCourseOption(existingFileHref)}" target="_blank" rel="noopener" class="text-info text-decoration-underline">Xem file đính kèm</a></span>` : "";

    const modal = new bootstrap.Modal(document.getElementById('evaluateModal'));
    modal.show();
}

function renderEvaluateCriteriaSliders(normSubject, criteriaList, item, baseScore10) {
    const evalContainer = document.getElementById("evalCriteriaContainer");
    if (!evalContainer) return;

    const cfg = SUBJECT_STYLE_META[normSubject] || SUBJECT_STYLE_META['DAN'];
    const savedScores = Array.isArray(item?.criteriaScores) ? item.criteriaScores : [];
    const isCompletedWithSnapshots = item?.status === 'COMPLETED' && savedScores.length > 0;

    // Nếu bài thi đã COMPLETED và đã có snapshot lịch sử các tiêu chí lúc chấm,
    // chỉ hiển thị đúng bộ tiêu chí đã chấm của bài thi đó (không tự gắn tiêu chí mới thêm sau này).
    const listToRender = isCompletedWithSnapshots
        ? savedScores.map((s, idx) => ({
            id: s.criteriaId || (idx + 1),
            criteriaName: s.criteriaName || `Tiêu chí ${idx + 1}`,
            savedScore: s.score
        }))
        : ((criteriaList && criteriaList.length > 0)
            ? criteriaList
            : savedScores.map((s, idx) => ({
                id: s.criteriaId || (idx + 1),
                criteriaName: s.criteriaName || `Tiêu chí ${idx + 1}`,
                savedScore: s.score
            })));

    if (!listToRender.length) {
        evaluationBlockedByMissingCriteria = true;
        updateEvaluationSubmitButtonState();
        evalContainer.innerHTML = `
            <div class="alert alert-danger py-3 px-3 small mb-3 border-danger">
                <i class="bi bi-exclamation-octagon-fill me-2"></i><strong>Không thể chấm và lưu điểm:</strong> Chưa có tiêu chí đánh giá nào đang áp dụng cho <strong>${cfg.subjectName}</strong> (Quản trị viên đã xóa hoặc ngưng áp dụng toàn bộ tiêu chí). Vui lòng liên hệ ADMIN thêm tiêu chí đánh giá trước khi chấm bài!
            </div>
        `;
        calculateAverageScore();
        return;
    }

    evaluationBlockedByMissingCriteria = false;
    updateEvaluationSubmitButtonState();

    evalContainer.innerHTML = `
        <div class="d-flex align-items-center justify-content-between mb-3">
            <h6 class="fw-bold text-dark mb-0"><i class="bi ${cfg.headerIcon} me-2"></i>Chấm điểm ${listToRender.length} tiêu chí ${cfg.subjectName} (Thang điểm 0 - 10)</h6>
            <span class="badge ${cfg.badgeClass} rounded-pill px-3 py-1 fw-bold">${cfg.subjectName}</span>
        </div>
        <div class="row g-3 mb-4">
            ${listToRender.map((c, idx) => {
                const style = getCriteriaStyle(idx);
                const critKey = c.id != null ? `crit_${c.id}_${idx}` : `crit_idx_${idx}`;
                const matchedSaved = c.savedScore != null
                    ? { score: c.savedScore }
                    : savedScores.find(s =>
                        (s.criteriaId != null && c.id != null && Number(s.criteriaId) === Number(c.id))
                        || (s.criteriaName && s.criteriaName === c.criteriaName));
                const sliderVal = matchedSaved && matchedSaved.score != null ? Number(matchedSaved.score) : baseScore10;
                const safeLabel = escapeCourseOption(c.criteriaName || `Tiêu chí ${idx + 1}`);
                return `
                    <div class="col-md-6">
                        <div class="score-slider-card">
                            <div class="d-flex justify-content-between align-items-center mb-1">
                                <label class="fw-semibold text-dark small"><i class="bi ${style.icon} me-1"></i>${safeLabel}</label>
                                <span class="badge ${style.badgeClass} px-2" id="val_${critKey}">${sliderVal.toFixed(1)}</span>
                            </div>
                            <input type="range" class="form-range eval-criteria-slider"
                                id="score_${critKey}"
                                data-criteria-id="${c.id != null ? c.id : ''}"
                                data-criteria-name="${safeLabel}"
                                min="0" max="10" step="0.5" value="${sliderVal}"
                                oninput="document.getElementById('val_${critKey}').innerText = parseFloat(this.value).toFixed(1); calculateAverageScore();">
                        </div>
                    </div>
                `;
            }).join('')}
        </div>
    `;
    calculateAverageScore();
}

async function handleSaveEvaluation(e) {
    e.preventDefault();

    if (attachmentUploading) {
        showNotification('Vui lòng đợi tải file xong trước khi lưu đánh giá.', 'warning');
        return;
    }
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
    if (!sliders.length || evaluationBlockedByMissingCriteria) {
        showNotification("Không thể lưu kết quả test đầu vào vì bộ môn này hiện không có tiêu chí đánh giá nào đang áp dụng! Vui lòng liên hệ ADMIN thêm tiêu chí.", "danger");
        return;
    }
    let total = 0;
    let count = 0;
    const criteriaScores = [];
    sliders.forEach(s => {
        const val = parseFloat(s.value || 0);
        total += val;
        count++;
        criteriaScores.push({
            criteriaId: s.dataset.criteriaId ? Number(s.dataset.criteriaId) : null,
            criteriaName: s.dataset.criteriaName || '',
            score: val
        });
    });
    const avg10 = count > 0 ? (total / count) : 0;
    const score100 = Math.round(avg10 * 10);

    const recommendedLevel = document.getElementById("evalLevelSelect").value;
    const recommendedCourseId = document.getElementById("evalCourseSelect").value;
    if (item.enrollmentRequestId != null && !recommendedCourseId) {
        showNotification('Vui lòng chọn khóa học đề xuất trước khi gửi đánh giá.', 'warning');
        return;
    }
    const teacherNote = document.getElementById("evalExaminerNotes").value;
    const mediaUrlInput = document.getElementById("evalMediaUrl").value.trim();
    const mediaUrl = mediaUrlInput || uploadedFileUrl;

    const isAudio = /\.(mp3|wav|ogg|m4a)$/i.test(mediaUrl);
    const isVideo = /\.(mp4|webm|mov|avi|mkv)$/i.test(mediaUrl);
    const isImage = /\.(png|jpg|jpeg|gif|webp|svg)$/i.test(mediaUrl);

    const assessmentPayload = {
        score: score100,
        recommendedLevel: recommendedLevel,
        recommendedCourseId: recommendedCourseId ? Number(recommendedCourseId) : null,
        teacherNote: teacherNote,
        audioUrl: isAudio ? mediaUrl : null,
        videoUrl: isVideo ? mediaUrl : null,
        imageUrl: isImage ? mediaUrl : null,
        recordUrl: mediaUrl || null,
        criteriaScores: criteriaScores
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

    initDateRestrictions();
    const modal = new bootstrap.Modal(document.getElementById('createModal'));
    const enrollmentRequests = loadEnrollmentRequestsForScheduling();
    const programs = loadProgramsForScheduling();
    const branches = loadBranchesForScheduling(true);
    modal.show();
    await Promise.all([enrollmentRequests, programs, branches]);
}

async function handleCreatePlacementTest(e) {
    e.preventDefault();

    const studentName = document.getElementById("createStudentName").value.trim();
    const programId = Number(document.getElementById("createProgram").value);
    if (!programId) {
        showNotification('Vui lòng chọn môn kiểm tra có trong danh mục.', 'danger');
        return;
    }
    const branchSelect = document.getElementById("createBranch");
    const selectedBranchOpt = branchSelect ? branchSelect.options[branchSelect.selectedIndex] : null;
    const branch = selectedBranchOpt ? (selectedBranchOpt.dataset.branchName || selectedBranchOpt.text || '') : '';
    if (!branchSelect || !branchSelect.value || !branch) {
        showNotification('Vui lòng chọn cơ sở đào tạo.', 'danger');
        return;
    }
    const roomSelect = document.getElementById("createRoomName");
    const roomName = roomSelect ? roomSelect.value.trim() : '';
    if (!roomName || roomSelect.disabled) {
        showNotification('Vui lòng chọn phòng học.', 'danger');
        return;
    }
    const title = document.getElementById("createTitle").value.trim();
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

    if (!isWithinWorkingHours(testTimeVal)) {
        showNotification(`Giờ hẹn test phải nằm trong khung giờ hành chính làm việc (${WORKING_HOURS_START} - ${WORKING_HOURS_END})!`, "danger");
        document.getElementById("createTestTime").focus();
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
        programId,
        title: title,
        roomName: roomName,
        branch: branch,
        testDate: isoDateTime,
        note: note,
        enrollmentRequestId
    };

    const submit = e.currentTarget.querySelector('button[type="submit"]');
    submit.disabled = true;
    let savedRecord;
    try {
        savedRecord = await callApi('/api/placement-tests', 'POST', createPayload);
        if (!savedRecord?.id) throw new Error('Chưa nhận được xác nhận lưu lịch từ máy chủ.');
    } catch (error) {
        showNotification(`Không thể tạo lịch kiểm tra: ${error.message}`, 'danger');
        return;
    } finally {
        submit.disabled = false;
    }

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

    const testSubjectDisplayName = savedRecord.programName || getSubjectDisplayName(savedRecord.subject);
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

    const cancelBox = document.getElementById("detailCancelReasonBox");
    const cancelText = document.getElementById("detailCancelReason");
    if (cancelBox && cancelText) {
        if (item.status === 'CANCELLED' && item.cancelReason) {
            cancelText.innerText = item.cancelReason;
            cancelBox.classList.remove("d-none");
        } else {
            cancelText.innerText = "—";
            cancelBox.classList.add("d-none");
        }
    }

    const baseScore = item.score != null ? item.score : 0;
    const normSubject = normalizeSubject(item.subject || inferSubject(item.title));
    const cfg = SUBJECT_STYLE_META[normSubject] || SUBJECT_STYLE_META['DAN'];

    const detailCriteriaContainer = document.getElementById("detailCriteriaContainer");
    if (detailCriteriaContainer) {
        if (item.status !== 'COMPLETED') {
            detailCriteriaContainer.innerHTML = `
                <div class="d-flex align-items-center justify-content-between mb-2">
                    <h6 class="fw-bold text-dark mb-0"><i class="bi ${cfg.headerIcon} me-2"></i>Chi tiết điểm tiêu chí [${cfg.subjectName}]</h6>
                    <span class="badge ${cfg.badgeClass} rounded-pill px-3 py-1 fw-bold">${cfg.subjectName}</span>
                </div>
                <div class="alert alert-light border text-muted small py-2 px-3 mb-4">
                    <i class="bi bi-clock-history me-1"></i>Ca kiểm tra này chưa hoàn thành chấm điểm nên chưa có kết quả chi tiết từng tiêu chí.
                </div>
            `;
        } else {
            const savedScores = Array.isArray(item.criteriaScores) ? item.criteriaScores : [];
            renderDetailCriteriaProgress(detailCriteriaContainer, cfg, savedScores, baseScore);
        }
    }

    const mediaContainer = document.getElementById("detailMediaAttachment");
    const mediaUrl = item.videoUrl || item.audioUrl || item.recordUrl || item.imageUrl;
    if (mediaContainer) {
        if (mediaUrl) {
            const fullMediaUrl = mediaUrl.startsWith("/uploads/")
                ? (typeof BASE_URL === "string" ? BASE_URL : "") + mediaUrl
                : mediaUrl;
            const safeHref = escapeCourseOption(fullMediaUrl);
            const isImg = /\.(png|jpg|jpeg|gif|webp|svg)$/i.test(mediaUrl);
            const isVid = /\.(mp4|webm|mov|avi|mkv)$/i.test(mediaUrl);
            const isAud = /\.(mp3|wav|ogg|m4a)$/i.test(mediaUrl);
            let previewHtml = "";
            if (isImg) {
                previewHtml = `<div class="mt-2 text-center"><a href="${safeHref}" target="_blank" rel="noopener"><img src="${safeHref}" alt="Ảnh đính kèm bài thi" class="img-fluid rounded-3 border shadow-sm" style="max-height: 280px; object-fit: contain;"></a></div>`;
            } else if (isVid) {
                previewHtml = `<div class="mt-2"><video controls class="w-100 rounded-3 border shadow-sm" style="max-height: 320px;" src="${safeHref}"></video></div>`;
            } else if (isAud) {
                previewHtml = `<div class="mt-2"><audio controls class="w-100" src="${safeHref}"></audio></div>`;
            }
            mediaContainer.innerHTML = `
                <div class="alert alert-info py-2 px-3 small d-flex align-items-center justify-content-between mb-0">
                    <span><i class="bi bi-paperclip me-1"></i>File đính kèm bài thi / ghi âm:</span>
                    <a href="${safeHref}" target="_blank" rel="noopener" class="btn btn-sm btn-info text-dark fw-bold rounded-pill">
                        <i class="bi bi-download me-1"></i>Xem / Tải file
                    </a>
                </div>
                ${previewHtml}
            `;
        } else {
            mediaContainer.innerHTML = `<span class="text-muted small">Không có đính kèm media</span>`;
        }
    }

    const modal = new bootstrap.Modal(document.getElementById('detailModal'));
    modal.show();
}

function openCancelPlacementModal(id) {
    if (placementCancelling) return;
    const item = placementTestsData.find(x => x.id === id);
    if (!item) return;

    if (item.status === 'CANCELLED') {
        showNotification('Đơn Placement Test này đã được hủy trước đó.', 'warning');
        return;
    }
    if (item.status === 'COMPLETED') {
        showNotification('Không thể hủy đơn Placement Test đã hoàn thành chấm điểm.', 'warning');
        return;
    }

    cancellingScheduleId = id;
    const codeEl = document.getElementById('cancelScheduleCodeDisplay');
    const nameEl = document.getElementById('cancelStudentNameDisplay');
    const reasonInput = document.getElementById('cancelReasonInput');
    const reasonError = document.getElementById('cancelReasonError');
    if (codeEl) codeEl.innerText = `#PT-${item.id}`;
    if (nameEl) nameEl.innerText = item.studentName || 'Học viên';
    if (reasonInput) {
        reasonInput.value = '';
        reasonInput.classList.remove('is-invalid');
    }
    if (reasonError) reasonError.classList.add('d-none');

    const modalEl = document.getElementById('cancelPlacementModal');
    if (modalEl) {
        const modal = new bootstrap.Modal(modalEl);
        modal.show();
    }
}

async function confirmCancelPlacementTest() {
    if (!cancellingScheduleId || placementCancelling) return;
    const scheduleId = cancellingScheduleId;
    const item = placementTestsData.find(x => x.id === scheduleId);
    if (!item) return;

    const reasonInput = document.getElementById('cancelReasonInput');
    const reasonError = document.getElementById('cancelReasonError');
    const cancelReason = reasonInput ? reasonInput.value.trim() : '';

    if (!cancelReason || cancelReason.length > 500) {
        if (reasonInput) {
            reasonInput.classList.add('is-invalid');
            reasonInput.focus();
        }
        if (reasonError) reasonError.classList.remove('d-none');
        showNotification('Vui lòng nhập lý do hủy từ 1 đến 500 ký tự.', 'warning');
        return;
    }
    if (reasonInput) reasonInput.classList.remove('is-invalid');
    if (reasonError) reasonError.classList.add('d-none');

    const btn = document.getElementById('btnConfirmCancelPlacement');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Đang hủy...';
    }

    placementCancelling = true;
    try {
        if (typeof callApi !== 'function') throw new Error('Chưa kết nối được máy chủ. Vui lòng tải lại trang.');
        const updatedResponse = await callApi(`/api/placement-tests/${scheduleId}/cancel`, 'POST', { cancelReason }, false, false);
        if (!updatedResponse || updatedResponse.id !== scheduleId || updatedResponse.status !== 'CANCELLED') {
            throw new Error('Máy chủ chưa xác nhận hủy đơn. Vui lòng thử lại.');
        }
        const idx = placementTestsData.findIndex(x => x.id === scheduleId);
        if (idx !== -1) placementTestsData[idx] = updatedResponse;

        const modalEl = document.getElementById('cancelPlacementModal');
        const modal = modalEl ? bootstrap.Modal.getInstance(modalEl) : null;
        if (modal) modal.hide();

        cancellingScheduleId = null;
        showNotification(`Đã hủy đơn Placement Test #PT-${scheduleId} thành công!`, 'success');
        renderStats();
        filterPlacementTests();
    } catch (err) {
        console.error('Cancel placement test error:', err);
        showNotification(`Không thể hủy đơn: ${err.message || 'Vui lòng thử lại.'}`, 'danger');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-x-circle me-1"></i>Xác Nhận Hủy Đơn';
        }
        placementCancelling = false;
    }
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

function renderDetailCriteriaProgress(container, cfg, rows, baseScore) {
    if (!container) return;
    if (!rows || !rows.length) {
        container.innerHTML = `
            <div class="d-flex align-items-center justify-content-between mb-2">
                <h6 class="fw-bold text-dark mb-0"><i class="bi ${cfg.headerIcon} me-2"></i>Điểm tiêu chí [${cfg.subjectName}]</h6>
                <span class="badge ${cfg.badgeClass} rounded-pill px-3 py-1 fw-bold">${cfg.subjectName}</span>
            </div>
            <div class="text-muted small mb-3">Đang tải danh sách tiêu chí đánh giá...</div>
        `;
        return;
    }

    container.innerHTML = `
        <div class="d-flex align-items-center justify-content-between mb-3">
            <h6 class="fw-bold text-dark mb-0"><i class="bi ${cfg.headerIcon} me-2"></i>Điểm số ${rows.length} tiêu chí [${cfg.subjectName}]</h6>
            <span class="badge ${cfg.badgeClass} rounded-pill px-3 py-1 fw-bold">${cfg.subjectName}</span>
        </div>
        <div class="row g-3 mb-4">
            ${rows.map((c, idx) => {
                const style = getCriteriaStyle(idx);
                const s10 = c.score != null ? Number(c.score) : (baseScore / 10);
                const percent = Math.min(Math.max(Math.round(s10 * 10), 0), 100);
                const safeLabel = escapeCourseOption(c.criteriaName || `Tiêu chí ${idx + 1}`);
                return `
                    <div class="col-md-6">
                        <small class="fw-semibold text-muted d-flex justify-content-between">
                            <span><i class="bi ${style.icon} me-1"></i>${safeLabel}</span>
                            <strong class="text-dark">${s10.toFixed(1)}/10</strong>
                        </small>
                        <div class="progress mt-1" style="height: 8px;">
                            <div class="progress-bar ${style.badgeClass.split(' ')[0]}" role="progressbar" style="width: ${percent}%"></div>
                        </div>
                    </div>
                `;
            }).join('')}
        </div>
    `;
}

// ─── ADMIN CRITERIA CONFIGURATION MODAL ──────────────────────────────────────

async function openCriteriaConfigModal() {
    if (!isAdmin()) {
        showNotification("Chỉ Quản trị viên (ADMIN) mới có quyền cấu hình tiêu chí đánh giá đầu vào!", "danger");
        return;
    }
    resetCriteriaConfigForm();
    const modalEl = document.getElementById("criteriaConfigModal");
    if (modalEl) {
        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        modal.show();
    }
    await loadCriteriaForAdminModal();
}

function getNextAvailableCriteriaOrder(list) {
    if (!Array.isArray(list) || list.length === 0) return 1;
    const maxOrder = list.reduce((max, item) => {
        const val = Number(item?.displayOrder);
        return Number.isFinite(val) && val > max ? val : max;
    }, 0);
    return maxOrder + 1;
}

async function loadCriteriaForAdminModal() {
    const subjectSelect = document.getElementById("criteriaSubjectSelect");
    const subject = subjectSelect ? subjectSelect.value : "DAN";
    const tbody = document.getElementById("criteriaConfigTableBody");
    const badge = document.getElementById("criteriaCountBadge");
    if (tbody) {
        tbody.innerHTML = `<tr><td colspan="4" class="text-center py-3 text-muted small">Đang tải danh sách tiêu chí...</td></tr>`;
    }
    try {
        const list = await callApi(`/api/placement-tests/criteria?subject=${encodeURIComponent(subject)}&includeInactive=true`, 'GET', null, false, false);
        adminCriteriaList = Array.isArray(list) ? list : [];
        const activeList = adminCriteriaList.filter(c => c.active !== false);
        placementCriteriaBySubject[normalizeSubject(subject)] = activeList;
        if (badge) {
            badge.textContent = `${activeList.length} tiêu chí đang áp dụng / ${adminCriteriaList.length} tổng số`;
        }
        renderCriteriaConfigTable(adminCriteriaList);
        const editId = document.getElementById("criteriaEditId")?.value;
        if (!editId) {
            const orderInput = document.getElementById("criteriaOrderInput");
            if (orderInput) orderInput.value = String(getNextAvailableCriteriaOrder(adminCriteriaList));
        }
    } catch (err) {
        if (tbody) {
            tbody.innerHTML = `<tr><td colspan="4" class="text-center py-3 text-danger small">Không tải được tiêu chí: ${escapeCourseOption(err.message || 'Vui lòng thử lại')}</td></tr>`;
        }
    }
}

function renderCriteriaConfigTable(list) {
    const tbody = document.getElementById("criteriaConfigTableBody");
    if (!tbody) return;
    if (!list || !list.length) {
        tbody.innerHTML = `<tr><td colspan="4" class="text-center py-3 text-muted small">Chưa có tiêu chí nào cho bộ môn này.</td></tr>`;
        return;
    }

    tbody.innerHTML = list.map(c => {
        const isActive = c.active !== false;
        const statusHtml = isActive
            ? `<span class="badge bg-success-subtle text-success border border-success-subtle">Đang áp dụng</span>`
            : `<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle">Ngưng áp dụng (Lưu lịch sử)</span>`;
        return `
            <tr class="${isActive ? '' : 'table-light text-muted'}">
                <td class="fw-semibold text-center">${c.displayOrder ?? 1}</td>
                <td>${escapeCourseOption(c.criteriaName)}</td>
                <td>${statusHtml}</td>
                <td class="text-end text-nowrap">
                    <button type="button" class="btn btn-sm btn-outline-primary py-0 px-2 me-1" onclick="startEditCriteria(${c.id})" title="Chỉnh sửa tiêu chí">
                        <i class="bi bi-pencil"></i> Sửa
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-danger py-0 px-2" onclick="handleDeleteCriteriaConfig(${c.id})" title="${c.usedInAssessments ? 'Ngưng áp dụng (giữ lịch sử các bài đã chấm)' : 'Xóa tiêu chí'}">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

function startEditCriteria(id) {
    const found = adminCriteriaList.find(c => Number(c.id) === Number(id));
    if (!found) return;
    document.getElementById("criteriaEditId").value = String(found.id);
    document.getElementById("criteriaNameInput").value = found.criteriaName || "";
    document.getElementById("criteriaOrderInput").value = String(found.displayOrder ?? 1);
    document.getElementById("criteriaActiveInput").checked = found.active !== false;
    const titleEl = document.getElementById("criteriaFormTitle");
    if (titleEl) {
        titleEl.innerHTML = `<i class="bi bi-pencil-square text-warning me-1"></i>Đang sửa tiêu chí #${found.id}`;
    }
    const cancelBtn = document.getElementById("btnCancelCriteriaEdit");
    if (cancelBtn) cancelBtn.classList.remove("d-none");
    document.getElementById("criteriaNameInput").focus();
}

function resetCriteriaConfigForm() {
    const editIdEl = document.getElementById("criteriaEditId");
    const nameEl = document.getElementById("criteriaNameInput");
    const orderEl = document.getElementById("criteriaOrderInput");
    const activeEl = document.getElementById("criteriaActiveInput");
    const titleEl = document.getElementById("criteriaFormTitle");
    const cancelBtn = document.getElementById("btnCancelCriteriaEdit");

    if (editIdEl) editIdEl.value = "";
    if (nameEl) nameEl.value = "";
    if (orderEl) {
        orderEl.value = String(getNextAvailableCriteriaOrder(adminCriteriaList));
        orderEl.classList.remove("is-invalid");
    }
    if (activeEl) activeEl.checked = true;
    if (titleEl) {
        titleEl.innerHTML = `<i class="bi bi-plus-circle text-primary me-1"></i>Thêm tiêu chí đánh giá mới`;
    }
    if (cancelBtn) cancelBtn.classList.add("d-none");
}

async function handleSaveCriteriaConfig(e) {
    e.preventDefault();
    if (!isAdmin()) {
        showNotification("Chỉ ADMIN mới có quyền thêm hoặc sửa tiêu chí đánh giá!", "danger");
        return;
    }

    const editId = document.getElementById("criteriaEditId").value.trim();
    const subject = document.getElementById("criteriaSubjectSelect").value || "DAN";
    const criteriaName = document.getElementById("criteriaNameInput").value.trim();
    const orderInput = document.getElementById("criteriaOrderInput");
    const rawOrder = orderInput ? orderInput.value.trim() : "";
    const displayOrder = Number(rawOrder);
    const active = document.getElementById("criteriaActiveInput").checked;

    if (!criteriaName) {
        showNotification("Vui lòng nhập tên tiêu chí đánh giá!", "warning");
        return;
    }

    if (!rawOrder || !Number.isInteger(displayOrder) || displayOrder < 1) {
        if (orderInput) {
            orderInput.classList.add("is-invalid");
            orderInput.focus();
        }
        showNotification("Thứ tự (ID) tiêu chí phải là số nguyên dương lớn hơn hoặc bằng 1!", "danger");
        return;
    }

    const duplicatedOrderCriterion = (adminCriteriaList || []).find(c =>
        Number(c.displayOrder) === displayOrder && (!editId || Number(c.id) !== Number(editId))
    );
    if (duplicatedOrderCriterion) {
        if (orderInput) {
            orderInput.classList.add("is-invalid");
            orderInput.focus();
        }
        showNotification(`Thứ tự (ID) số ${displayOrder} đã tồn tại cho tiêu chí "${duplicatedOrderCriterion.criteriaName}"! Vui lòng nhập số thứ tự khác.`, "danger");
        return;
    }
    if (orderInput) orderInput.classList.remove("is-invalid");

    const btn = document.getElementById("btnSaveCriteria");
    if (btn) btn.disabled = true;
    try {
        const payload = { subject, criteriaName, displayOrder, active };
        if (editId) {
            await callApi(`/api/placement-tests/criteria/${encodeURIComponent(editId)}`, 'PUT', payload, false, false);
            showNotification("Đã cập nhật tiêu chí đánh giá thành công!", "success");
        } else {
            await callApi('/api/placement-tests/criteria', 'POST', payload, false, false);
            showNotification("Đã thêm tiêu chí đánh giá mới thành công!", "success");
        }
        resetCriteriaConfigForm();
        await loadCriteriaForAdminModal();
    } catch (err) {
        showNotification(`Không thể lưu tiêu chí: ${err.message || 'Vui lòng thử lại'}`, "danger");
    } finally {
        if (btn) btn.disabled = false;
    }
}

async function handleDeleteCriteriaConfig(id) {
    if (!isAdmin()) {
        showNotification("Chỉ ADMIN mới có quyền xóa tiêu chí đánh giá!", "danger");
        return;
    }
    const found = adminCriteriaList.find(c => Number(c.id) === Number(id));
    if (!found) return;

    try {
        await callApi(`/api/placement-tests/criteria/${encodeURIComponent(id)}`, 'DELETE', null, false, false);
        const msg = found.usedInAssessments
            ? `Tiêu chí "${found.criteriaName}" đã từng dùng chấm thi nên được chuyển sang Ngưng áp dụng để bảo toàn lịch sử điểm cũ.`
            : `Đã xóa tiêu chí "${found.criteriaName}" thành công!`;
        showNotification(msg, "success");
        resetCriteriaConfigForm();
        await loadCriteriaForAdminModal();
    } catch (err) {
        showNotification(`Không thể xóa tiêu chí: ${err.message || 'Vui lòng thử lại'}`, "danger");
    }
}

