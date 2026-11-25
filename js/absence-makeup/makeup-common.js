/**
 * =============================================================================
 * MODULE DÙNG CHUNG — NGHỈ HỌC & HỌC BÙ (makeup-common.js)
 * =============================================================================
 * Mục lục tra cứu nhanh:
 *   1. Biến trạng thái toàn cục (danh sách đơn, ca bù, học viên, modal, user)
 *   2. Tiện ích định dạng & hiển thị: cleanTitle, formatCourseAndLevel, formatCleanNote,
 *      escapeHtml, formatDateTime, showToast, getAbsenceStatusBadgeHtml, renderMakeupCounterBadge (3/3)
 *   3. Kiểm tra phiên đăng nhập & khởi tạo trang (DOMContentLoaded, handleDeepLinkParams)
 *   4. Phân quyền hiển thị theo vai trò (initRoleDisplay), nạp dữ liệu (reloadData),
 *      thống kê KPI (updateKpiStats) & chuyển tab (switchMainTab)
 */

// -----------------------------------------------------------------------------
// 1. BIẾN TRẠNG THÁI TOÀN CỤC
// -----------------------------------------------------------------------------
let currentAbsenceList = [];
let currentMakeupList = [];
let currentStudents = [];
let currentSelectableLessons = [];
let currentUser = null;
let initialAuthSignature = '';

// Phân trang Tab 1 (Danh sách đơn xin nghỉ)
let absencePage = 1;
let absencePageSize = 5;

// ID đơn đang duyệt / từ chối
let activeReviewId = null;

// Bootstrap Modal instances
let modalApproveObj = null;
let modalRejectObj = null;
let modalDetailObj = null;
let modalResolveObj = null;
let activeResolveConfig = null;

// -----------------------------------------------------------------------------
// 2. TIỆN ÍCH ĐỊNH DẠNG & HUY HIỆU (BADGES)
// -----------------------------------------------------------------------------

function cleanTitle(str) {
    if (!str) return '';
    return String(str).replace(/\[TEST\]\s*/gi, '').trim();
}

function formatCourseAndLevel(courseName, level) {
    const cleanCourse = cleanTitle(courseName || '');
    if (!cleanCourse && !level) return '';
    const levelMap = {
        BEGINNER: 'Cơ bản',
        INTERMEDIATE: 'Trung cấp',
        ADVANCED: 'Nâng cao'
    };
    const rawLevel = String(level || '').trim();
    const displayLevel = levelMap[rawLevel.toUpperCase()] || rawLevel;
    if (!displayLevel) return cleanCourse;
    if (!cleanCourse) return displayLevel;
    const lowerCourse = cleanCourse.toLowerCase();
    if (lowerCourse.includes(rawLevel.toLowerCase()) || lowerCourse.includes(displayLevel.toLowerCase())) {
        return cleanCourse;
    }
    return `${cleanCourse} (${displayLevel})`;
}

function formatCleanNote(text) {
    if (!text) return '';
    let cleaned = String(text)
        .trim()
        .replace(/^\[TEST\]\s*/gi, '')
        .replace(/^\[Lần bù:\s*[^\]]+\]\s*/i, '')
        .replace(/^(?:ĐÃ HỦY\s*\(Chưa có lớp phù hợp\)|CHƯA CÓ LỚP PHÙ HỢP|HỦY|CHUYỂN KỲ SAU):\s*/i, '')
        .replace(/^Xếp lịch bù linh hoạt\s*\([^)]*\):\s*/i, '')
        .trim();
    if (/^Tự động tạo ca học bù/i.test(cleaned)) return '';
    if (/^Xếp lịch học bù tại/i.test(cleaned)) return '';
    return cleaned;
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>"']/g, function (m) {
        return {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        }[m];
    });
}

function formatDateTime(dtStr) {
    if (!dtStr) return 'N/A';
    try {
        const d = new Date(dtStr);
        if (isNaN(d.getTime())) return dtStr;
        const pad = (n) => String(n).padStart(2, '0');
        const hh = pad(d.getHours());
        const mm = pad(d.getMinutes());
        const dd = pad(d.getDate());
        const MM = pad(d.getMonth() + 1);
        const yyyy = d.getFullYear();
        return `${hh}:${mm} - ${dd}/${MM}/${yyyy}`;
    } catch {
        return dtStr;
    }
}

function showToast(type, message) {
    const toastContainer = document.getElementById('toastContainer');
    if (!toastContainer) return;

    const toastId = 'toast_' + Date.now();
    const bgClass = type === 'success' ? 'bg-success' : (type === 'warning' ? 'bg-warning text-dark' : 'bg-danger');
    const iconClass = type === 'success' ? 'bi-check-circle-fill' : (type === 'warning' ? 'bi-exclamation-triangle-fill' : 'bi-x-circle-fill');

    const toastHtml = `
        <div id="${toastId}" class="toast align-items-center text-white ${bgClass} border-0 shadow-lg mb-2" role="alert" aria-live="assertive" aria-atomic="true">
            <div class="d-flex">
                <div class="toast-body d-flex align-items-center gap-2">
                    <i class="bi ${iconClass} fs-5"></i>
                    <div>${message}</div>
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        </div>
    `;
    toastContainer.insertAdjacentHTML('beforeend', toastHtml);
    const toastEl = document.getElementById(toastId);
    const toast = new bootstrap.Toast(toastEl, { delay: 4000 });
    toast.show();
    toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
}

function getAbsenceStatusBadgeHtml(status) {
    if (status === 'PENDING') return '<span class="badge-status-modern status-pending"><i class="bi bi-hourglass-split"></i>Chờ Duyệt</span>';
    if (status === 'APPROVED') return '<span class="badge-status-modern status-approved"><i class="bi bi-check-circle-fill"></i>Đã Duyệt</span>';
    if (status === 'REJECTED') return '<span class="badge-status-modern status-rejected"><i class="bi bi-x-circle-fill"></i>Từ Chối</span>';
    return `<span class="badge bg-secondary">${escapeHtml(status || '')}</span>`;
}

/**
 * Huy hiệu hiển thị hạn mức quyền học bù trong 1 khóa (mặc định 0..3 / 3).
 */
function renderMakeupCounterBadge(count, max = 3) {
    const val = Number(count || 0);
    const tip = `Đã xếp/sử dụng ${val}/${max} buổi học bù trong khóa học`;
    if (val >= max) {
        return `<span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill px-2 py-1" style="font-size:0.72rem; font-weight:600;" title="${tip}"><i class="bi bi-exclamation-octagon-fill me-1"></i>Lượt bù khóa: ${val}/${max}</span>`;
    }
    if (val > 0) {
        return `<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle rounded-pill px-2 py-1" style="font-size:0.72rem; font-weight:600;" title="${tip}"><i class="bi bi-clock-history me-1"></i>Lượt bù khóa: ${val}/${max}</span>`;
    }
    return `<span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill px-2 py-1" style="font-size:0.72rem; font-weight:600;" title="${tip}"><i class="bi bi-check-circle me-1"></i>Lượt bù khóa: 0/${max}</span>`;
}

// -----------------------------------------------------------------------------
// 3. KHỞI TẠO TRANG, ĐỒNG BỘ PHIÊN & ĐIỀU HƯỚNG DEEP-LINK
// -----------------------------------------------------------------------------

function getAuthSignature(userObj = null) {
    const u = userObj || (typeof getCurrentUser === 'function' ? getCurrentUser() : null);
    const tok = localStorage.getItem('token') || '';
    const uid = u ? (u.username || u.userId || u.id || '') : '';
    const urole = u ? (u.role || '') : '';
    return `${uid}|${urole}|${tok}`;
}

function ensureSessionMatchesTab() {
    if (!initialAuthSignature) return true;
    if (getAuthSignature() === initialAuthSignature) return true;
    showToast('warning', 'Phiên đăng nhập ở tab khác đã đổi tài khoản. Đang tải lại...');
    setTimeout(() => window.location.reload(), 650);
    return false;
}

window.addEventListener('DOMContentLoaded', async () => {
    if (typeof updateNavbarUser === 'function') updateNavbarUser();
    fetch('../components/footer.html')
        .then(r => r.text())
        .then(h => {
            const footerEl = document.getElementById('footer-container');
            if (footerEl) footerEl.innerHTML = h;
        });

    currentUser = typeof getCurrentUser === 'function' ? getCurrentUser() : null;
    initialAuthSignature = getAuthSignature(currentUser);
    window.addEventListener('storage', (e) => {
        if (e.key === 'token' || e.key === 'user') ensureSessionMatchesTab();
    });
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') ensureSessionMatchesTab();
    });
    window.addEventListener('focus', () => ensureSessionMatchesTab());
    initRoleDisplay();

    const modalApproveEl = document.getElementById('modalApprove');
    if (modalApproveEl) modalApproveObj = new bootstrap.Modal(modalApproveEl);

    const modalRejectEl = document.getElementById('modalReject');
    if (modalRejectEl) modalRejectObj = new bootstrap.Modal(modalRejectEl);

    const modalDetailEl = document.getElementById('modalDetail');
    if (modalDetailEl) modalDetailObj = new bootstrap.Modal(modalDetailEl);

    const modalResolveEl = document.getElementById('modalResolveNote');
    if (modalResolveEl) modalResolveObj = new bootstrap.Modal(modalResolveEl);

    await reloadData();
    await handleDeepLinkParams();
});

async function handleDeepLinkParams() {
    const params = new URLSearchParams(window.location.search);
    const tab = params.get('tab');
    const absenceId = params.get('absenceId');
    const makeupId = params.get('makeupId');

    if (tab === 'MAKEUP') {
        switchMainTab('MAKEUP');
    } else if (tab === 'CREATE' && currentUser && currentUser.role === 'PARENT') {
        switchMainTab('CREATE');
    } else if (tab === 'ABSENCE') {
        switchMainTab('ABSENCE');
    }

    if (absenceId && typeof viewAbsenceDetail === 'function') {
        await viewAbsenceDetail(Number(absenceId));
    } else if (makeupId && typeof viewMakeupDetail === 'function') {
        await viewMakeupDetail(Number(makeupId));
    }
}

// -----------------------------------------------------------------------------
// 4. PHÂN QUYỀN GIAO DIỆN, NẠP DỮ LIỆU, KPI & CHUYỂN TAB
// -----------------------------------------------------------------------------

function initRoleDisplay() {
    const role = currentUser ? (currentUser.role || 'USER') : 'USER';
    const isParent = role === 'PARENT';
    const isOperator = ['ADMIN', 'BRANCH_MANAGER', 'STAFF'].includes(role);

    const pageTitleEl = document.getElementById('pageTitleText');
    const breadcrumbEl = document.getElementById('pageBreadcrumbText');
    const heroDescEl = document.getElementById('pageHeroDesc');
    const tabItemCreate = document.getElementById('tabItemCreate');
    const createBtnWrapper = document.getElementById('createBtnWrapper');
    const statsRow = document.getElementById('statsRow');
    const makeupDirWrapper = document.getElementById('makeupDirWrapper');
    const absenceBranchWrap = document.getElementById('absenceBranchFilterWrap');
    const makeupBranchWrap = document.getElementById('makeupBranchFilterWrap');

    if (statsRow) statsRow.classList.toggle('d-none', isParent);
    if (makeupDirWrapper) makeupDirWrapper.classList.toggle('d-none', role !== 'TEACHER');
    if (absenceBranchWrap) absenceBranchWrap.classList.toggle('d-none', !isOperator);
    if (makeupBranchWrap) makeupBranchWrap.classList.toggle('d-none', !isOperator);

    if (isParent) {
        document.title = 'Gửi Đơn & Học Bù - Học Viện Talent';
        if (pageTitleEl) pageTitleEl.textContent = 'Gửi Đơn & Học Bù';
        if (breadcrumbEl) breadcrumbEl.textContent = 'Gửi Đơn & Học Bù';
        if (heroDescEl) heroDescEl.textContent = 'Gửi đơn xin nghỉ và theo dõi lịch học bù của con.';
        if (tabItemCreate) tabItemCreate.classList.remove('d-none');
        if (createBtnWrapper) createBtnWrapper.classList.remove('d-none');
    } else {
        document.title = 'Quản Lý Đơn & Học Bù - Học Viện Talent';
        if (pageTitleEl) pageTitleEl.textContent = 'Quản Lý Đơn';
        if (breadcrumbEl) breadcrumbEl.textContent = 'Quản Lý Đơn';
        if (heroDescEl) {
            heroDescEl.textContent = role === 'TEACHER'
                ? 'Duyệt đơn xin nghỉ và theo dõi học bù lớp phụ trách.'
                : role === 'STAFF'
                    ? 'Duyệt đơn xin nghỉ và xếp lịch học bù.'
                    : 'Theo dõi, duyệt đơn và điều phối học bù toàn trung tâm.';
        }
        if (tabItemCreate) tabItemCreate.classList.add('d-none');
        if (createBtnWrapper) createBtnWrapper.classList.add('d-none');
    }

    const badge = document.getElementById('userRoleBadge');
    if (!badge) return;
    if (!currentUser) {
        badge.innerText = 'Chưa Đăng Nhập';
        badge.className = 'badge bg-light text-dark px-3 py-2 rounded-pill fw-bold shadow-sm';
        return;
    }

    let roleName = 'Người Dùng';
    if (role === 'PARENT') roleName = 'Phụ huynh';
    else if (role === 'TEACHER') roleName = 'Giáo viên';
    else if (role === 'ADMIN') roleName = 'Quản trị viên';
    else if (role === 'BRANCH_MANAGER') roleName = 'Quản lý chi nhánh';
    else if (role === 'STAFF') roleName = 'Giáo vụ';

    const displayName = currentUser.fullName || currentUser.username || '';
    badge.innerHTML = `<i class="bi bi-person-fill me-1"></i>${roleName}: ${escapeHtml(displayName)}`;
    badge.className = 'badge px-3 py-2 rounded-pill fw-bold shadow-sm';
    badge.style.background = '#e3efdf';
    badge.style.color = '#143c34';
    badge.style.border = '1px solid #c7dec1';
}

async function reloadData() {
    const tasks = [];
    if (typeof loadAbsenceRequests === 'function') tasks.push(loadAbsenceRequests());
    if (currentUser && currentUser.role === 'PARENT' && typeof loadSelectableStudents === 'function') {
        tasks.push(loadSelectableStudents());
    }
    if (typeof loadMakeupRequests === 'function') tasks.push(loadMakeupRequests());
    await Promise.all(tasks);
    updateKpiStats();
}

function updateKpiStats() {
    const pendingAbsence = (currentAbsenceList || []).filter(a => a.status === 'PENDING').length;
    const approvedAbsence = (currentAbsenceList || []).filter(a => a.status === 'APPROVED').length;
    const pendingMakeup = (currentMakeupList || []).filter(m => m.status === 'PENDING').length;
    const completedMakeup = (currentMakeupList || []).filter(m => m.status === 'COMPLETED').length;

    const elPending = document.getElementById('statPendingCount');
    if (elPending) elPending.innerText = pendingAbsence;

    const elApproved = document.getElementById('statApprovedCount');
    if (elApproved) elApproved.innerText = approvedAbsence;

    const elMakeupPending = document.getElementById('statMakeupPendingCount');
    if (elMakeupPending) elMakeupPending.innerText = pendingMakeup;

    const elMakeupCompleted = document.getElementById('statMakeupCompletedCount');
    if (elMakeupCompleted) elMakeupCompleted.innerText = completedMakeup;
}

function switchMainTab(tab) {
    const secAbsence = document.getElementById('sectionAbsence');
    const secCreate = document.getElementById('sectionCreate');
    const secMakeup = document.getElementById('sectionMakeup');

    const btnAbsence = document.getElementById('tabBtnAbsence');
    const btnCreate = document.getElementById('tabBtnCreate');
    const btnMakeup = document.getElementById('tabBtnMakeup');

    if (btnAbsence) btnAbsence.classList.remove('active');
    if (btnCreate) btnCreate.classList.remove('active');
    if (btnMakeup) btnMakeup.classList.remove('active');

    if (secAbsence) secAbsence.classList.add('d-none');
    if (secCreate) secCreate.classList.add('d-none');
    if (secMakeup) secMakeup.classList.add('d-none');

    if (tab === 'ABSENCE') {
        if (btnAbsence) btnAbsence.classList.add('active');
        if (secAbsence) secAbsence.classList.remove('d-none');
        if (typeof renderAbsenceTable === 'function') renderAbsenceTable();
        updateKpiStats();
    } else if (tab === 'CREATE') {
        if (btnCreate) btnCreate.classList.add('active');
        if (secCreate) secCreate.classList.remove('d-none');
    } else if (tab === 'MAKEUP') {
        if (btnMakeup) btnMakeup.classList.add('active');
        if (secMakeup) secMakeup.classList.remove('d-none');
        if (typeof renderMakeupTable === 'function') renderMakeupTable();
        updateKpiStats();
    }

    document.querySelectorAll('.workspace-subnav-link').forEach(el => {
        el.classList.toggle('active', el.dataset.tab === tab);
    });
}
