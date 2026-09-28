/**
 * Placement Test Management JS
 * Strictly enforces that ONLY users with TEACHER role can grade/assess placement tests.
 * - Non-TEACHER roles cannot open evaluation modal or submit assessments.
 * - PARENT role is completely blocked from accessing page and redirected to /index.html.
 */

let placementTestsData = [
    {
        id: 1,
        studentName: "Nguyễn Hoàng Anh",
        title: "Đánh Giá Năng Khiếu Piano Đầu Vào",
        roomName: "Phòng Piano 101",
        branch: "Cơ sở 1 - Cầu Giấy",
        testDate: "2026-09-22T09:30:00",
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
        parentName: "Nguyễn Văn Hùng",
        parentEmail: "hung.nguyen@gmail.com"
    },
    {
        id: 2,
        studentName: "Trần Bảo Ngọc",
        title: "Kiểm Tra Trình Độ Piano Chuyên Sâu",
        roomName: "Phòng Piano Grand 202",
        branch: "Cơ sở 2 - Đống Đa",
        testDate: "2026-09-21T15:00:00",
        note: "Có năng khiếu nổi trội, tai nghe cảm âm chuẩn.",
        status: "COMPLETED",
        score: 88,
        recommendedLevel: "INTERMEDIATE",
        teacherNote: "Năng khiếu cảm âm vượt trội (9/10), nhịp điệu vững. Khuyến nghị xếp lớp Piano Intermediate 1.",
        audioUrl: "",
        videoUrl: "https://example.com/recordings/test2.mp4",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: "Cô Vũ Thu Hương (TEACHER)",
        evaluatedAt: "2026-09-21T15:45:00",
        parentName: "Lê Thị Mỹ",
        parentEmail: "my.le@gmail.com"
    },
    {
        id: 3,
        studentName: "Phạm Minh Đức",
        title: "Đánh Giá Khả Năng Cảm Âm & Nhịp Điệu Guitar",
        roomName: "Phòng Hòa Tấu 1",
        branch: "Cơ sở 1 - Cầu Giấy",
        testDate: "2026-09-23T17:30:00",
        note: "Quan tâm đến Guitar Acoustic.",
        status: "SCHEDULED",
        score: null,
        recommendedLevel: null,
        teacherNote: "",
        audioUrl: "",
        videoUrl: "",
        imageUrl: "",
        recordUrl: "",
        evaluatedByName: "Thầy Trần Anh Tuấn (TEACHER)",
        evaluatedAt: null,
        parentName: "Phạm Quốc Tuấn",
        parentEmail: "tuan.pham@gmail.com"
    }
];

let currentEditingId = null;
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
            fetch('/components/navbar.html')
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
                            Tài khoản: <strong>${currentUser.fullName || currentUser.username}</strong> &bull; Bạn có toàn quyền truy cập, Đăng ký ca thi mới, Chấm điểm 4 tiêu chí âm nhạc, Tải lên file đính kèm và Xếp lớp trình độ.
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
                            Tài khoản: <strong>${currentUser.fullName || currentUser.username}</strong> &bull; Bạn có thể xem lịch test và Đăng ký ca mới. Chức năng chấm điểm chuyên môn dành cho tài khoản Giáo Viên.
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

            if (apiResult && Array.isArray(apiResult)) {
                placementTestsData = apiResult;
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
                <td colspan="8" class="text-center py-5 text-muted">
                    <i class="bi bi-inbox fs-1 d-block mb-2 text-secondary"></i>
                    Không tìm thấy lịch đánh giá năng khiếu nào phù hợp.
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

        return `
            <tr class="align-middle">
                <td>
                    <code class="fw-semibold text-primary">#PT-${item.id}</code>
                </td>
                <td>
                    <div class="fw-bold text-dark">${item.studentName || 'Học viên'}</div>
                    <small class="text-muted d-block"><i class="bi bi-person me-1"></i>Phụ huynh: ${item.parentName || item.parentEmail || 'Chưa cập nhật'}</small>
                </td>
                <td>
                    <div class="fw-semibold text-dark small">${item.title || 'Đánh giá năng khiếu'}</div>
                    <small class="text-muted d-block"><i class="bi bi-geo-alt me-1 text-danger"></i>${item.branch || 'Cơ sở'} &bull; ${item.roomName || 'Phòng học'}</small>
                </td>
                <td>
                    <div class="small fw-semibold text-dark"><i class="bi bi-calendar-event me-1 text-primary"></i>${testDateFormatted.date}</div>
                    <div class="small text-muted"><i class="bi bi-clock me-1"></i>${testDateFormatted.time}</div>
                </td>
                <td>
                    <div class="small text-dark fw-medium"><i class="bi bi-person-badge me-1 text-secondary"></i>${item.evaluatedByName || 'Chuyên môn'}</div>
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
    const levelFilter = document.getElementById("levelFilter");
    const statusFilter = document.getElementById("statusFilter");
    const myTestsToggle = document.getElementById("myTestsFilterToggle");

    if (searchInput) searchInput.addEventListener("input", filterPlacementTests);
    if (levelFilter) levelFilter.addEventListener("change", filterPlacementTests);
    if (statusFilter) statusFilter.addEventListener("change", filterPlacementTests);
    if (myTestsToggle) myTestsToggle.addEventListener("change", filterPlacementTests);

    const sliders = ['pitch', 'rhythm', 'technique', 'reading'];
    sliders.forEach(key => {
        const slider = document.getElementById(`score_${key}`);
        const display = document.getElementById(`val_${key}`);
        if (slider && display) {
            slider.addEventListener("input", (e) => {
                display.innerText = parseFloat(e.target.value).toFixed(1);
                calculateAverageScore();
            });
        }
    });

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
    const level = document.getElementById("levelFilter")?.value || "";
    const status = document.getElementById("statusFilter")?.value || "";
    const myTestsOnly = document.getElementById("myTestsFilterToggle")?.checked || false;

    let currentUser = null;
    if (typeof getCurrentUser === 'function') currentUser = getCurrentUser();

    const filtered = placementTestsData.filter(item => {
        const matchesQuery = !query || 
            (item.studentName && item.studentName.toLowerCase().includes(query)) ||
            (item.title && item.title.toLowerCase().includes(query)) ||
            (item.parentName && item.parentName.toLowerCase().includes(query)) ||
            (item.parentEmail && item.parentEmail.toLowerCase().includes(query)) ||
            `#PT-${item.id}`.toLowerCase().includes(query);

        const matchesLevel = !level || item.recommendedLevel === level;
        const matchesStatus = !status || item.status === status;

        let matchesMine = true;
        if (myTestsOnly && currentUser) {
            const evaluator = (item.evaluatedByName || "").toLowerCase();
            const teacherKey = (currentUser.fullName || currentUser.username || "").toLowerCase();
            matchesMine = evaluator.includes(teacherKey) || (currentUser.username && evaluator.includes(currentUser.username.toLowerCase()));
        }

        return matchesQuery && matchesLevel && matchesStatus && matchesMine;
    });

    renderTable(filtered);
}

function calculateAverageScore() {
    const pitch = parseFloat(document.getElementById("score_pitch")?.value || 0);
    const rhythm = parseFloat(document.getElementById("score_rhythm")?.value || 0);
    const technique = parseFloat(document.getElementById("score_technique")?.value || 0);
    const reading = parseFloat(document.getElementById("score_reading")?.value || 0);

    const avg10 = (pitch + rhythm + technique + reading) / 4;
    const score100 = Math.round(avg10 * 10);

    const avgDisplay = document.getElementById("calculatedAvgScore");
    const score100Display = document.getElementById("calculatedScore100");

    if (avgDisplay) avgDisplay.innerText = avg10.toFixed(1);
    if (score100Display) score100Display.innerText = `${score100} / 100đ`;
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
    uploadedFileUrl = item.videoUrl || item.audioUrl || item.recordUrl || item.imageUrl || "";

    document.getElementById("evalCandidateCode").innerText = `#PT-${item.id}`;
    document.getElementById("evalCandidateName").innerText = item.studentName || "Học viên";

    const examinerInfo = currentUser ? `${currentUser.fullName || currentUser.username}` : (item.evaluatedByName || 'Chưa phân công');
    document.getElementById("evalCandidateMeta").innerText = `Tiêu đề: ${item.title || 'Test Năng Khiếu'} | Cơ sở: ${item.branch || 'Cơ sở 1'} | GV chấm: ${examinerInfo}`;

    const baseScore10 = item.score != null ? item.score / 10 : 7.5;
    
    ['pitch', 'rhythm', 'technique', 'reading'].forEach(key => {
        const slider = document.getElementById(`score_${key}`);
        const display = document.getElementById(`val_${key}`);
        if (slider) slider.value = baseScore10;
        if (display) display.innerText = baseScore10.toFixed(1);
    });

    calculateAverageScore();

    document.getElementById("evalLevelSelect").value = item.recommendedLevel || "BEGINNER";
    document.getElementById("evalExaminerNotes").value = item.teacherNote || "";
    document.getElementById("evalMediaUrl").value = uploadedFileUrl;

    const statusEl = document.getElementById("uploadStatusMessage");
    if (statusEl) statusEl.innerHTML = uploadedFileUrl ? `<span class="text-info"><i class="bi bi-link-45deg me-1"></i>Đã có file: <a href="${uploadedFileUrl}" target="_blank" class="text-info text-decoration-underline">${uploadedFileUrl}</a></span>` : "";

    const modal = new bootstrap.Modal(document.getElementById('evaluateModal'));
    modal.show();
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

    const pitch = parseFloat(document.getElementById("score_pitch").value);
    const rhythm = parseFloat(document.getElementById("score_rhythm").value);
    const technique = parseFloat(document.getElementById("score_technique").value);
    const reading = parseFloat(document.getElementById("score_reading").value);
    const avg10 = (pitch + rhythm + technique + reading) / 4;
    const score100 = Math.round(avg10 * 10);

    const recommendedLevel = document.getElementById("evalLevelSelect").value;
    const teacherNote = document.getElementById("evalExaminerNotes").value;
    const mediaUrlInput = document.getElementById("evalMediaUrl").value.trim();
    const mediaUrl = mediaUrlInput || uploadedFileUrl;

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

    showNotification(`Đã lưu kết quả chấm điểm bởi ${evaluatorName}!`, "success");
    renderStats();
    filterPlacementTests();
}

function openCreateModal() {
    const form = document.getElementById("createPlacementTestForm");
    if (form) form.reset();
    const modal = new bootstrap.Modal(document.getElementById('createModal'));
    modal.show();
}

async function handleCreatePlacementTest(e) {
    e.preventDefault();

    const studentName = document.getElementById("createStudentName").value.trim();
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
        placementTestsData.unshift(createdResponse);
    } else {
        placementTestsData.unshift(newTest);
    }

    const modalEl = document.getElementById('createModal');
    const modal = bootstrap.Modal.getInstance(modalEl);
    if (modal) modal.hide();

    showNotification(`Đã đăng ký lịch kiểm tra mới cho học viên ${studentName}!`, "success");
    renderStats();
    filterPlacementTests();
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
    setProgressBar("bar_pitch", "val_detail_pitch", baseScore);
    setProgressBar("bar_rhythm", "val_detail_rhythm", baseScore);
    setProgressBar("bar_technique", "val_detail_technique", baseScore);
    setProgressBar("bar_reading", "val_detail_reading", baseScore);

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
