/**
 * =============================================================================
 * ABSENCE REQUESTS MODULE (absence-requests.js)
 * =============================================================================
 * Handles:
 * 1. Global state & page initialization (tabs, KPI stats, role display).
 * 2. Tab 1: Viewing, filtering, paginating, approving, and rejecting absence requests.
 * 3. Tab 2: Creating a new absence request (selecting student, lesson, reason).
 * 4. Helper modals: Detail view, review popup, common notifications (toast).
 */

// -----------------------------------------------------------------------------
// 1. GLOBAL STATE VARIABLES
// -----------------------------------------------------------------------------
let currentAbsenceList = [];
let currentMakeupList = [];
let currentStudents = [];
let currentUser = null;

// Pagination state for Absence Requests (Tab 1)
let absencePage = 1;
let absencePageSize = 5;

// Review ID when Teacher/Staff clicks Approve or Reject
let activeReviewId = null;

// Bootstrap modal instances
let modalApproveObj = null;
let modalRejectObj = null;
let modalDetailObj = null;
let modalResolveObj = null;
let activeResolveConfig = null;

// -----------------------------------------------------------------------------
// 2. HELPER UTILITIES
// -----------------------------------------------------------------------------

/**
 * Strips [TEST] and technical tags from titles/names.
 */
function cleanTitle(str) {
    if (!str) return '';
    return String(str).replace(/\[TEST\]\s*/gi, '').trim();
}

/**
 * Strips technical prefix tags from notes to show clean text to users.
 */
function formatCleanNote(text) {
    if (!text) return '';
    let cleaned = String(text).trim();
    if (/^Tự động tạo ca học bù/i.test(cleaned)) return '';
    if (/^Xếp lịch học bù tại/i.test(cleaned)) return '';
    cleaned = cleaned
        .replace(/^\[TEST\]\s*/gi, '')
        .replace(/^\[Lần bù:\s*[^\]]+\]\s*/i, '')
        .replace(/^(?:CHƯA CÓ LỚP PHÙ HỢP|HỦY|CHUYỂN KỲ SAU):\s*/i, '')
        .replace(/^Xếp lịch bù linh hoạt\s*\([^)]*\):\s*/i, '')
        .trim();
    return cleaned;
}

/**
 * Escapes HTML characters to prevent XSS.
 */
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

/**
 * Formats ISO date time string to readable VN format: HH:mm - dd/MM/yyyy
 */
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

/**
 * Displays modern floating toast alert.
 * @param {'success'|'warning'|'danger'} type
 * @param {string} message
 */
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

/**
 * Returns badge HTML corresponding to an absence status.
 */
function getAbsenceStatusBadgeHtml(status) {
    if (status === 'PENDING') return '<span class="badge-status-modern status-pending"><i class="bi bi-hourglass-split"></i>Chờ Duyệt</span>';
    if (status === 'APPROVED') return '<span class="badge-status-modern status-approved"><i class="bi bi-check-circle-fill"></i>Đã Duyệt</span>';
    if (status === 'REJECTED') return '<span class="badge-status-modern status-rejected"><i class="bi bi-x-circle-fill"></i>Từ Chối</span>';
    return `<span class="badge bg-secondary">${escapeHtml(status || '')}</span>`;
}

// -----------------------------------------------------------------------------
// 3. PAGE INITIALIZATION & TAB SWITCHING
// -----------------------------------------------------------------------------

window.addEventListener('DOMContentLoaded', async () => {
    // Load shared shell (navbar, footer)
    if (typeof updateNavbarUser === 'function') updateNavbarUser();
    fetch('../components/footer.html')
        .then(r => r.text())
        .then(h => {
            const footerEl = document.getElementById('footer-container');
            if (footerEl) footerEl.innerHTML = h;
        });

    currentUser = typeof getCurrentUser === 'function' ? getCurrentUser() : null;
    initRoleDisplay();

    // Initialize Bootstrap Modals
    const modalApproveEl = document.getElementById('modalApprove');
    if (modalApproveEl) modalApproveObj = new bootstrap.Modal(modalApproveEl);

    const modalRejectEl = document.getElementById('modalReject');
    if (modalRejectEl) modalRejectObj = new bootstrap.Modal(modalRejectEl);

    const modalDetailEl = document.getElementById('modalDetail');
    if (modalDetailEl) modalDetailObj = new bootstrap.Modal(modalDetailEl);

    const modalResolveEl = document.getElementById('modalResolveNote');
    if (modalResolveEl) modalResolveObj = new bootstrap.Modal(modalResolveEl);

    // Initial data load
    await reloadData();
    await handleDeepLinkParams();
});

/**
 * Checks URL query parameters to open appropriate tab or detail popup directly.
 */
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

    if (absenceId) {
        await viewAbsenceDetail(Number(absenceId));
    } else if (makeupId && typeof viewMakeupDetail === 'function') {
        await viewMakeupDetail(Number(makeupId));
    }
}

/**
 * Configures UI based on current user role (Parent, Teacher, Staff, Admin).
 */
function initRoleDisplay() {
    const role = currentUser ? (currentUser.role || 'USER') : 'USER';
    const isParent = role === 'PARENT';

    const pageTitleEl = document.getElementById('pageTitleText');
    const breadcrumbEl = document.getElementById('pageBreadcrumbText');
    const heroDescEl = document.getElementById('pageHeroDesc');
    const tabItemCreate = document.getElementById('tabItemCreate');
    const createBtnWrapper = document.getElementById('createBtnWrapper');
    const statsRow = document.getElementById('statsRow');
    const makeupDirWrapper = document.getElementById('makeupDirWrapper');

    if (statsRow) {
        statsRow.classList.toggle('d-none', role === 'PARENT');
    }
    if (makeupDirWrapper) {
        makeupDirWrapper.classList.toggle('d-none', role !== 'TEACHER');
    }

    if (isParent) {
        if (pageTitleEl) pageTitleEl.textContent = 'Gửi Đơn & Học Bù';
        if (breadcrumbEl) breadcrumbEl.textContent = 'Gửi Đơn & Học Bù';
        if (heroDescEl) heroDescEl.textContent = 'Gửi đơn xin nghỉ và theo dõi lịch học bù.';
        if (tabItemCreate) tabItemCreate.classList.remove('d-none');
        if (createBtnWrapper) createBtnWrapper.classList.remove('d-none');
    } else {
        if (pageTitleEl) pageTitleEl.textContent = 'Quản Lý Đơn';
        if (breadcrumbEl) breadcrumbEl.textContent = 'Quản Lý Đơn';
        if (heroDescEl) heroDescEl.textContent = 'Quản lý đơn xin nghỉ và theo dõi lịch học bù.';
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
    else if (role === 'ADMIN' || role === 'BRANCH_MANAGER') roleName = 'Quản trị';
    else if (role === 'STAFF') roleName = 'Giáo vụ';

    const displayName = currentUser.fullName || currentUser.username || '';
    badge.innerHTML = `<i class="bi bi-person-fill me-1"></i>${roleName}: ${escapeHtml(displayName)}`;
    badge.className = 'badge px-3 py-2 rounded-pill fw-bold shadow-sm';
    badge.style.background = '#e3efdf';
    badge.style.color = '#143c34';
    badge.style.border = '1px solid #c7dec1';
}

/**
 * Reloads all datasets across the entire feature.
 */
async function reloadData() {
    const tasks = [
        loadAbsenceRequests(),
        loadSelectableStudents()
    ];
    if (typeof loadMakeupRequests === 'function') {
        tasks.push(loadMakeupRequests());
    }
    await Promise.all(tasks);
    updateKpiStats();
}

/**
 * Recalculates KPI statistics shown on top cards.
 */
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

/**
 * Switches between Tab 1 (ABSENCE), Tab 2 (CREATE), and Tab 3 (MAKEUP).
 */
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
        renderAbsenceTable();
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

    // Đồng bộ trạng thái active trên thanh nav-bar bên trái
    document.querySelectorAll('.workspace-subnav-link').forEach(el => {
        if (el.dataset.tab === tab) {
            el.classList.add('active');
        } else {
            el.classList.remove('active');
        }
    });
}

// -----------------------------------------------------------------------------
// 4. TAB 1: ABSENCE REQUESTS LIST, FILTER & ACTIONS
// -----------------------------------------------------------------------------

function sortAbsenceList(list) {
    return [...(list || [])].sort((a, b) => {
        const pa = a.status === 'PENDING' ? 0 : 1;
        const pb = b.status === 'PENDING' ? 0 : 1;
        if (pa !== pb) return pa - pb;
        const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        if (ta !== tb) return tb - ta;
        return (b.id || 0) - (a.id || 0);
    });
}

function populateAbsenceFilterOptions() {
    const stuSel = document.getElementById('filterAbsenceStudent');
    const clsSel = document.getElementById('filterAbsenceClass');
    if (stuSel) {
        const prev = stuSel.value;
        const names = Array.from(new Set((currentAbsenceList || []).map(a => a.studentName).filter(Boolean)))
            .sort((a, b) => a.localeCompare(b, 'vi'));
        stuSel.innerHTML = '<option value="">Tất Cả Học Viên</option>'
            + names.map(n => `<option value="${escapeHtml(n)}">${escapeHtml(n)}</option>`).join('');
        if (prev && names.includes(prev)) stuSel.value = prev;
    }
    if (clsSel) {
        const prev = clsSel.value;
        const classes = Array.from(new Set((currentAbsenceList || []).map(a => a.className).filter(Boolean)))
            .sort((a, b) => a.localeCompare(b, 'vi'));
        clsSel.innerHTML = '<option value="">Tất Cả Lớp</option>'
            + classes.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
        if (prev && classes.includes(prev)) clsSel.value = prev;
    }
}

function applyAbsenceFilter() {
    renderAbsenceTable(true);
}

function resetAbsenceFilter() {
    ['filterAbsenceStatus', 'filterAbsenceStudent', 'filterAbsenceClass',
     'filterAbsenceFromDate', 'filterAbsenceToDate', 'searchAbsenceInput'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    renderAbsenceTable(true);
}

async function loadAbsenceRequests() {
    try {
        const data = await callApi('/api/attendance-makeup/absence-requests');
        currentAbsenceList = sortAbsenceList(data || []);
        populateAbsenceFilterOptions();
        renderAbsenceTable();
    } catch (err) {
        console.error("Failed to load absence requests:", err);
        const tbody = document.getElementById('absenceTableBody');
        if (tbody) {
            tbody.innerHTML = `
                <tr><td colspan="7" class="text-center text-danger py-4">Lỗi tải dữ liệu: ${err.message}</td></tr>
            `;
        }
    }
}

function onAbsencePageSizeChange() {
    const sel = document.getElementById('absencePageSizeSelect');
    absencePageSize = Number(sel ? sel.value : 5) || 5;
    absencePage = 1;
    renderAbsenceTable(false);
}

function goToAbsencePage(page) {
    absencePage = page;
    renderAbsenceTable(false);
}

function renderMakeupCounterBadge(count, max = 2) {
    const val = Number(count || 0);
    if (val >= max) {
        return `<span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill px-2 py-0.5" style="font-size:0.72rem; font-weight:600;"><i class="bi bi-exclamation-octagon-fill me-1"></i>Đã học bù: ${val}/${max} lần</span>`;
    }
    if (val > 0) {
        return `<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle rounded-pill px-2 py-0.5" style="font-size:0.72rem; font-weight:600;"><i class="bi bi-clock-history me-1"></i>Đã học bù: ${val}/${max} lần</span>`;
    }
    return `<span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill px-2 py-0.5" style="font-size:0.72rem; font-weight:600;"><i class="bi bi-check-circle me-1"></i>Đã học bù: 0/${max} lần</span>`;
}

function renderAbsenceTable(resetPage = false) {
    if (resetPage) absencePage = 1;
    const tbody = document.getElementById('absenceTableBody');
    if (!tbody) return;

    const paginationWrapper = document.getElementById('absencePaginationWrapper');
    const paginationInfo = document.getElementById('absencePaginationInfo');
    const paginationList = document.getElementById('absencePaginationList');

    const statusFilter = document.getElementById('filterAbsenceStatus')?.value || '';
    const studentFilter = document.getElementById('filterAbsenceStudent')?.value || '';
    const classFilter = document.getElementById('filterAbsenceClass')?.value || '';
    const fromDate = document.getElementById('filterAbsenceFromDate')?.value || '';
    const toDate = document.getElementById('filterAbsenceToDate')?.value || '';
    const searchKeyword = (document.getElementById('searchAbsenceInput')?.value || '').trim().toLowerCase();

    let filtered = sortAbsenceList(currentAbsenceList);
    if (statusFilter) {
        filtered = filtered.filter(a => a.status === statusFilter);
    }
    if (studentFilter) {
        filtered = filtered.filter(a => a.studentName === studentFilter);
    }
    if (classFilter) {
        filtered = filtered.filter(a => a.className === classFilter);
    }
    if (fromDate) {
        filtered = filtered.filter(a => a.lessonDate && a.lessonDate >= fromDate);
    }
    if (toDate) {
        filtered = filtered.filter(a => a.lessonDate && a.lessonDate <= toDate);
    }
    if (searchKeyword) {
        filtered = filtered.filter(a =>
            (a.studentName && a.studentName.toLowerCase().includes(searchKeyword)) ||
            (a.className && a.className.toLowerCase().includes(searchKeyword)) ||
            (a.lessonTitle && a.lessonTitle.toLowerCase().includes(searchKeyword)) ||
            (a.reason && a.reason.toLowerCase().includes(searchKeyword)) ||
            (a.id && a.id.toString().includes(searchKeyword))
        );
    }

    const countBadge = document.getElementById('absenceCountBadge');
    if (countBadge) countBadge.innerText = `${filtered.length} đơn`;

    if (filtered.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center text-muted py-5">
                    <i class="bi bi-inbox fs-2 d-block mb-2 text-secondary"></i>
                    Không có đơn xin nghỉ học nào phù hợp với bộ lọc
                </td>
            </tr>
        `;
        if (paginationWrapper) paginationWrapper.classList.add('d-none');
        return;
    }

    const totalItems = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / absencePageSize));
    if (absencePage > totalPages) absencePage = totalPages;
    if (absencePage < 1) absencePage = 1;

    const startIdx = (absencePage - 1) * absencePageSize;
    const endIdx = Math.min(startIdx + absencePageSize, totalItems);
    const pagedItems = filtered.slice(startIdx, endIdx);

    const canReviewItem = (item) => {
        if (!currentUser) return false;
        if (currentUser.role === 'TEACHER') {
            const myId = currentUser.userId ?? currentUser.id;
            return myId == null || item.teacherId == null || Number(myId) === Number(item.teacherId);
        }
        return ['ADMIN', 'BRANCH_MANAGER', 'STAFF'].includes(currentUser.role);
    };

    const isCoordinator = currentUser && ['STAFF', 'ADMIN', 'BRANCH_MANAGER'].includes(currentUser.role);
    const isCreator = currentUser && currentUser.role === 'PARENT';

    tbody.innerHTML = pagedItems.map(item => {
        const statusBadge = getAbsenceStatusBadgeHtml(item.status);
        const cleanMakeupNote = formatCleanNote(item.makeupNote);

        let makeupBadge = '<span class="text-muted small">—</span>';
        if (item.makeupStatus === 'PENDING') {
            makeupBadge = `<span class="badge-status-modern status-pending" title="${escapeHtml(cleanMakeupNote || 'Chờ xếp lớp')}"><i class="bi bi-clock-history"></i>Chờ xếp lớp</span>`;
        } else if (item.makeupStatus === 'SCHEDULED') {
            makeupBadge = `<span class="badge-status-modern status-scheduled" title="${escapeHtml(cleanMakeupNote || ('Đã xếp: ' + (item.makeupTargetDate || '')))}"><i class="bi bi-calendar-check"></i>${item.makeupTargetDate || 'Đã xếp'}</span>`;
        } else if (item.makeupStatus === 'COMPLETED') {
            makeupBadge = `<span class="badge-status-modern status-completed" title="${escapeHtml(cleanMakeupNote || 'Hoàn thành')}"><i class="bi bi-check2-all"></i>Đã học bù</span>`;
        } else if (item.makeupStatus === 'NO_SLOT') {
            makeupBadge = `<span class="badge-status-modern status-cancelled" title="${escapeHtml(cleanMakeupNote || 'Chưa có lớp phù hợp')}"><i class="bi bi-x-circle"></i>Đã Hủy (Chưa có lớp)</span>`;
        } else if (item.makeupStatus === 'MISSED') {
            const retryStr = item.makeupRetryCount ? ` (${item.makeupRetryCount}/${item.makeupMaxRetries || 2})` : '';
            makeupBadge = `<span class="badge-status-modern status-missed" title="${escapeHtml(cleanMakeupNote || 'Vắng mặt ca bù')}"><i class="bi bi-person-x-fill"></i>Vắng bù${retryStr}</span>`;
        } else if (item.makeupStatus === 'CANCELLED') {
            const isLocked = (item.makeupRetryCount >= 2) || (cleanMakeupNote && cleanMakeupNote.includes('KHÓA'));
            makeupBadge = isLocked
                ? `<span class="badge-status-modern status-cancelled" title="${escapeHtml(cleanMakeupNote || 'Quá 2 lần học bù')}"><i class="bi bi-lock-fill"></i>Khóa bù</span>`
                : `<span class="badge-status-modern status-cancelled" title="${escapeHtml(cleanMakeupNote || 'Đã hủy')}"><i class="bi bi-x-circle"></i>Đã hủy</span>`;
        }

        const lessonDateStr = item.lessonDate || '';
        const timeSlotStr = item.timeSlot || '';

        let actionButtons = `
            <button class="btn-action-icon" title="Chi tiết" onclick="viewAbsenceDetail(${item.id})">
                <i class="bi bi-eye"></i>
            </button>
        `;

        if (canReviewItem(item) && item.status === 'PENDING') {
            actionButtons += `
                <button class="btn-action-icon btn-action-approve" title="Duyệt" onclick="openApproveModal(${item.id}, '${escapeHtml(item.studentName || '')}')">
                    <i class="bi bi-check-lg"></i>
                </button>
                <button class="btn-action-icon btn-action-reject" title="Từ chối" onclick="openRejectModal(${item.id}, '${escapeHtml(item.studentName || '')}')">
                    <i class="bi bi-x-lg"></i>
                </button>
            `;
        }

        if ((isCreator || isCoordinator) && item.status === 'PENDING') {
            actionButtons += `
                <button class="btn-action-pill btn-action-pill-danger" title="Hủy đơn xin nghỉ" onclick="confirmCancelAbsence(${item.id})">
                    <i class="bi bi-trash3"></i>Hủy
                </button>
            `;
        }

        return `
            <tr>
                <td class="text-center"><span class="id-badge">#${item.id}</span></td>
                <td>
                    <div class="tbl-cell-title">${escapeHtml(item.studentName || 'Học viên')}</div>
                    <div class="mt-1">${renderMakeupCounterBadge(item.courseMakeupCount ?? 0, 2)}</div>
                </td>
                <td>
                    <div class="tbl-cell-title text-truncate" style="max-width: 290px;" title="${escapeHtml(item.lessonTitle || (`Buổi ${item.sessionNumber || ''}`))}">
                        ${escapeHtml(item.lessonTitle || (`Buổi ${item.sessionNumber || ''}`))}
                    </div>
                    <div class="tbl-cell-sub mt-1 d-flex align-items-center flex-wrap gap-1">
                        <span class="tbl-date-pill">${lessonDateStr}</span>
                        <span>${timeSlotStr}</span>
                        ${item.className ? `<span>&bull; ${escapeHtml(item.className)}</span>` : ''}
                    </div>
                </td>
                <td>
                    <div class="reason-text-clamp" title="${escapeHtml(item.reason || '')}">
                        ${escapeHtml(item.reason || '—')}
                    </div>
                </td>
                <td class="text-center">${statusBadge}</td>
                <td class="text-center">${makeupBadge}</td>
                <td class="text-end text-nowrap">
                    <div class="action-group-compact">
                        ${actionButtons}
                    </div>
                </td>
            </tr>
        `;
    }).join('');

    if (paginationWrapper) {
        paginationWrapper.classList.remove('d-none');
        if (paginationInfo) {
            paginationInfo.textContent = `Hiển thị ${startIdx + 1} - ${endIdx} / ${totalItems} đơn`;
        }
        if (paginationList) {
            let html = `
                <li class="page-item ${absencePage <= 1 ? 'disabled' : ''}">
                    <button class="page-link rounded-2" type="button" onclick="goToAbsencePage(${absencePage - 1})">&laquo; Trước</button>
                </li>
            `;
            for (let p = 1; p <= totalPages; p++) {
                html += `
                    <li class="page-item ${p === absencePage ? 'active' : ''}">
                        <button class="page-link rounded-2" type="button" onclick="goToAbsencePage(${p})">${p}</button>
                    </li>
                `;
            }
            html += `
                <li class="page-item ${absencePage >= totalPages ? 'disabled' : ''}">
                    <button class="page-link rounded-2" type="button" onclick="goToAbsencePage(${absencePage + 1})">Sau &raquo;</button>
                </li>
            `;
            paginationList.innerHTML = html;
        }
    }
}

// -----------------------------------------------------------------------------
// 5. APPROVE & REJECT ACTIONS (STAGE 2)
// -----------------------------------------------------------------------------

function openApproveModal(id, studentName) {
    activeReviewId = id;
    const item = Array.isArray(currentAbsenceList) ? currentAbsenceList.find(a => a.id === Number(id)) : null;

    const nameEl = document.getElementById('approveStudentName');
    const classRow = document.getElementById('approveClassRow');
    const classNameEl = document.getElementById('approveClassName');
    const noteEl = document.getElementById('approveNoteInput');
    const countBadgeEl = document.getElementById('approveMakeupCountBadge');
    const warningEl = document.getElementById('approveMakeupWarning');

    if (nameEl) nameEl.innerText = studentName || (item ? item.studentName : '...');

    if (item && item.className) {
        if (classNameEl) classNameEl.innerText = item.className;
        if (classRow) classRow.style.display = 'flex';
    } else if (classRow) {
        classRow.style.display = 'none';
    }

    const mkCount = item ? (item.courseMakeupCount ?? 0) : 0;
    const isLimit = item ? (item.courseMakeupLimitReached || mkCount >= 2) : false;
    if (countBadgeEl) {
        countBadgeEl.className = `badge ${isLimit ? 'bg-danger-subtle text-danger border-danger-subtle' : (mkCount > 0 ? 'bg-warning-subtle text-warning-emphasis border-warning-subtle' : 'bg-success-subtle text-success border-success-subtle')} border rounded-pill px-2 py-0.5 mt-1`;
        countBadgeEl.innerHTML = `<i class="bi ${isLimit ? 'bi-exclamation-octagon-fill' : (mkCount > 0 ? 'bi-clock-history' : 'bi-check-circle')} me-1"></i>Đã học bù: ${mkCount}/2 lần`;
    }
    if (warningEl) {
        warningEl.classList.toggle('d-none', !isLimit);
    }

    if (noteEl) noteEl.value = '';
    if (modalApproveObj) modalApproveObj.show();
}

async function executeApprove() {
    if (!activeReviewId) return;
    const note = document.getElementById('approveNoteInput')?.value || '';
    try {
        await callApi(`/api/attendance-makeup/absence-requests/${activeReviewId}/review`, 'PATCH', {
            decision: 'APPROVED',
            reviewNote: note
        });
        if (modalApproveObj) modalApproveObj.hide();
        showToast('success', 'Đã phê duyệt đơn nghỉ học.');
        await reloadData();
        if (window.refreshNotifications) window.refreshNotifications();
    } catch (err) {
        showToast('danger', 'Lỗi phê duyệt: ' + err.message);
    }
}

function openRejectModal(id, studentName) {
    activeReviewId = id;
    const item = Array.isArray(currentAbsenceList) ? currentAbsenceList.find(a => a.id === Number(id)) : null;

    const nameEl = document.getElementById('rejectStudentName');
    const classRow = document.getElementById('rejectClassRow');
    const classNameEl = document.getElementById('rejectClassName');
    const noteEl = document.getElementById('rejectNoteInput');
    const errEl = document.getElementById('rejectNoteError');

    if (nameEl) nameEl.innerText = studentName || (item ? item.studentName : '...');

    if (item && item.className) {
        if (classNameEl) classNameEl.innerText = item.className;
        if (classRow) classRow.style.display = 'flex';
    } else if (classRow) {
        classRow.style.display = 'none';
    }

    if (noteEl) {
        noteEl.value = '';
        noteEl.oninput = () => {
            if (errEl) errEl.style.display = 'none';
        };
    }
    if (errEl) errEl.style.display = 'none';
    if (modalRejectObj) modalRejectObj.show();
}

async function executeReject() {
    if (!activeReviewId) return;
    const note = (document.getElementById('rejectNoteInput')?.value || '').trim();
    const errEl = document.getElementById('rejectNoteError');
    if (!note) {
        if (errEl) errEl.style.display = 'block';
        return;
    }

    try {
        await callApi(`/api/attendance-makeup/absence-requests/${activeReviewId}/review`, 'PATCH', {
            decision: 'REJECTED',
            reviewNote: note
        });
        if (modalRejectObj) modalRejectObj.hide();
        showToast('warning', 'Đã từ chối đơn xin nghỉ.');
        await reloadData();
        if (window.refreshNotifications) window.refreshNotifications();
    } catch (err) {
        showToast('danger', 'Lỗi từ chối: ' + err.message);
    }
}

// -----------------------------------------------------------------------------
// 6. TAB 2: CREATE ABSENCE REQUEST FORM
// -----------------------------------------------------------------------------

function formatPickDate(dateStr) {
    if (!dateStr) return { dow: '--', day: '--' };
    const d = new Date(dateStr + 'T00:00:00');
    if (isNaN(d.getTime())) return { dow: '', day: dateStr };
    const dowNames = ['CN', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
    const dd = String(d.getDate()).padStart(2, '0');
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    return { dow: dowNames[d.getDay()], day: `${dd}/${mm}` };
}

function getInitials(name) {
    if (!name) return 'HV';
    const parts = String(name).trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function selectStudentCard(studentId) {
    const select = document.getElementById('createStudentSelect');
    if (select) select.value = String(studentId);
    document.querySelectorAll('.student-pick-card').forEach(btn => {
        btn.classList.toggle('active', Number(btn.dataset.studentId) === Number(studentId));
    });
    onStudentSelectChanged();
}

function selectLessonCard(lessonId) {
    const lessonSelect = document.getElementById('createLessonSelect');
    if (lessonSelect) lessonSelect.value = String(lessonId);
    document.querySelectorAll('.lesson-pick-card').forEach(btn => {
        btn.classList.toggle('active', Number(btn.dataset.lessonId) === Number(lessonId));
    });
}

function selectReasonChip(btn, text) {
    document.querySelectorAll('.reason-chip').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    const input = document.getElementById('createReasonInput');
    if (!input) return;
    input.value = text || '';
    updateReasonCharCount();
    input.focus();
}

function updateReasonCharCount() {
    const input = document.getElementById('createReasonInput');
    const counter = document.getElementById('reasonCharCount');
    if (input && counter) {
        counter.textContent = `${input.value.length} / 300`;
    }
}

async function loadSelectableStudents() {
    try {
        const data = await callApi('/api/attendance-makeup/students');
        currentStudents = data || [];

        const select = document.getElementById('createStudentSelect');
        const grid = document.getElementById('studentPickerGrid');
        if (!select) return;

        if (currentStudents.length === 0) {
            select.innerHTML = '<option value="">Không có học viên nào</option>';
            if (grid) grid.innerHTML = '<div class="lesson-empty-box w-100">Chưa có học viên liên kết</div>';
            return;
        }

        select.innerHTML = '<option value="">-- Chọn học viên --</option>' +
            currentStudents.map(s => {
                const mkCount = s.makeupCount || 0;
                const mkTag = (s.makeupLimitReached || mkCount >= 2)
                    ? ` [Đã học bù ${mkCount}/2 lần]`
                    : (mkCount > 0 ? ` [Đã học bù ${mkCount}/2 lần]` : ' [Đã học bù 0/2 lần]');
                return `
                    <option value="${s.id}">
                        ${escapeHtml(s.fullName)} (${escapeHtml(s.currentClassName || 'Đào tạo năng khiếu')})${mkTag}
                    </option>
                `;
            }).join('');

        if (grid) {
            if (currentStudents.length > 6) {
                grid.classList.add('d-none');
                select.classList.remove('d-none');
            } else {
                grid.classList.remove('d-none');
                select.classList.add('d-none');
                grid.innerHTML = currentStudents.map(s => {
                    const mkCount = s.makeupCount || 0;
                    const isLimit = s.makeupLimitReached || mkCount >= 2;
                    const badgeHtml = isLimit
                        ? `<span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill px-2 py-0.5" style="font-size:0.7rem;"><i class="bi bi-exclamation-octagon-fill me-1"></i>Đã học bù ${mkCount}/2 lần</span>`
                        : (mkCount > 0
                            ? `<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle rounded-pill px-2 py-0.5" style="font-size:0.7rem;"><i class="bi bi-clock-history me-1"></i>Đã học bù ${mkCount}/2 lần</span>`
                            : `<span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill px-2 py-0.5" style="font-size:0.7rem;"><i class="bi bi-check-circle me-1"></i>Đã học bù 0/2 lần</span>`);
                    return `
                        <button type="button" class="student-pick-card ${isLimit ? 'border-danger-subtle' : ''}" data-student-id="${s.id}" onclick="selectStudentCard(${s.id})">
                            <div class="student-pick-avatar ${isLimit ? 'bg-danger-subtle text-danger' : ''}">${escapeHtml(getInitials(s.fullName))}</div>
                            <div class="min-w-0 flex-grow-1">
                                <div class="student-pick-name">${escapeHtml(s.fullName)}</div>
                                <div class="student-pick-class">${escapeHtml(s.currentClassName || 'Học viên')}</div>
                                <div class="mt-1">${badgeHtml}</div>
                            </div>
                        </button>
                    `;
                }).join('');
            }
        }

        // Auto select first child for Parent
        if (currentUser && currentUser.role === 'PARENT' && currentStudents.length >= 1) {
            selectStudentCard(currentStudents[0].id);
        }
    } catch (err) {
        console.error("Failed to load selectable students:", err);
    }
}

async function onStudentSelectChanged() {
    const studentId = document.getElementById('createStudentSelect')?.value;
    const lessonSelect = document.getElementById('createLessonSelect');
    const lessonList = document.getElementById('lessonPickerList');
    const lessonCountEl = document.getElementById('lessonPickerCount');
    const warningBox = document.getElementById('studentMakeupWarningBox');

    const selStudent = (currentStudents || []).find(s => Number(s.id) === Number(studentId));
    if (warningBox) {
        if (selStudent && (selStudent.makeupLimitReached || (selStudent.makeupCount || 0) >= 2)) {
            const count = selStudent.makeupCount || 2;
            warningBox.className = 'd-block';
            warningBox.innerHTML = `
                <div class="makeup-limit-alert">
                    <i class="bi bi-exclamation-octagon-fill"></i>
                    <div>
                        <div class="makeup-limit-alert-title">Đã đạt giới hạn học bù (${count}/2 lần)</div>
                        <p class="makeup-limit-alert-text">Học viên đã sử dụng tối đa số lần học bù quy định trong khóa học nên không thể gửi thêm đơn xin nghỉ.</p>
                    </div>
                </div>
            `;
        } else {
            warningBox.className = 'd-none';
            warningBox.innerHTML = '';
        }
    }

    if (!studentId) {
        if (lessonSelect) {
            lessonSelect.innerHTML = '<option value="">-- Chọn buổi học --</option>';
            lessonSelect.disabled = true;
        }
        if (lessonCountEl) lessonCountEl.textContent = '';
        if (lessonList) lessonList.innerHTML = '<div class="lesson-empty-box">Chọn học viên ở bước 1 để hiển thị lịch học</div>';
        return;
    }

    if (lessonSelect) {
        lessonSelect.innerHTML = '<option value="">Đang tải...</option>';
        lessonSelect.disabled = true;
    }
    if (lessonList) lessonList.innerHTML = '<div class="lesson-empty-box"><span class="spinner-border spinner-border-sm me-2 text-success"></span>Đang tải các buổi học...</div>';

    try {
        const rawLessons = await callApi(`/api/attendance-makeup/students/${studentId}/lessons`);
        const now = new Date();
        const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
        const currentTimeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;

        const lessons = (rawLessons || []).filter(l => {
            if (!l.lessonDate) return false;
            if (l.lessonDate < todayStr) return false;
            if (l.lessonDate === todayStr) {
                const startTimeStr = (l.startTime || (l.timeSlot ? l.timeSlot.split('-')[0].trim() : '')).substring(0, 5);
                if (startTimeStr && startTimeStr <= currentTimeStr) return false;
            }
            return true;
        });

        if (!lessons || lessons.length === 0) {
            if (lessonSelect) {
                lessonSelect.innerHTML = '<option value="">Chưa có buổi học nào</option>';
                lessonSelect.disabled = true;
            }
            if (lessonCountEl) lessonCountEl.textContent = '0 buổi';
            if (lessonList) lessonList.innerHTML = '<div class="lesson-empty-box">Học viên chưa có buổi học sắp tới</div>';
            return;
        }

        if (lessonSelect) {
            lessonSelect.innerHTML = '<option value="">-- Chọn buổi học --</option>' +
                lessons.map(l => {
                    const dateStr = l.lessonDate || '';
                    const timeStr = l.timeSlot || `${l.startTime} - ${l.endTime}`;
                    const titleStr = cleanTitle(l.title || `Buổi ${l.sessionNumber || ''}`);
                    const classStr = cleanTitle(l.className || '');
                    const roomStr = l.roomName ? ` • ${cleanTitle(l.roomName)}` : '';
                    return `<option value="${l.id}">${dateStr} (${timeStr}) • ${titleStr} - ${classStr}${roomStr}</option>`;
                }).join('');
            lessonSelect.disabled = false;
        }

        if (lessonCountEl) lessonCountEl.textContent = `${lessons.length} buổi`;
        if (lessonList) {
            lessonList.innerHTML = lessons.map(l => {
                const dt = formatPickDate(l.lessonDate);
                const timeStr = l.timeSlot || `${l.startTime || ''} - ${l.endTime || ''}`;
                const titleStr = cleanTitle(l.title || `Buổi ${l.sessionNumber || ''}`);
                const classStr = cleanTitle(l.className || '');
                const roomStr = cleanTitle(l.roomName || 'Phòng học');
                return `
                    <button type="button" class="lesson-pick-card" data-lesson-id="${l.id}" onclick="selectLessonCard(${l.id})">
                        <div class="lesson-pick-datebox">
                            <div class="lesson-pick-dow">${escapeHtml(dt.dow)}</div>
                            <div class="lesson-pick-day">${escapeHtml(dt.day)}</div>
                        </div>
                        <div class="lesson-pick-info">
                            <div class="lesson-pick-title">${escapeHtml(titleStr)}</div>
                            <div class="lesson-pick-meta">
                                <span><i class="bi bi-clock me-1"></i>${escapeHtml(timeStr)}</span>
                                <span><i class="bi bi-easel me-1"></i>${escapeHtml(classStr)}</span>
                                <span><i class="bi bi-door-open me-1"></i>${escapeHtml(roomStr)}</span>
                            </div>
                        </div>
                        <div class="lesson-pick-check"><i class="bi bi-check-lg"></i></div>
                    </button>
                `;
            }).join('');
        }
    } catch (err) {
        if (lessonSelect) {
            lessonSelect.innerHTML = `<option value="">Lỗi: ${err.message}</option>`;
            lessonSelect.disabled = true;
        }
        if (lessonList) lessonList.innerHTML = `<div class="lesson-empty-box text-danger">Lỗi tải lịch học: ${escapeHtml(err.message)}</div>`;
    }
}

async function submitAbsenceRequest() {
    const studentId = document.getElementById('createStudentSelect')?.value;
    const lessonId = document.getElementById('createLessonSelect')?.value;
    const reason = (document.getElementById('createReasonInput')?.value || '').trim();

    if (!studentId || !lessonId || !reason) {
        showToast('danger', 'Vui lòng chọn học viên, buổi học và nhập lý do!');
        return;
    }

    // Chặn học sinh đã đạt giới hạn học bù khi nhấn nút gửi đơn
    const selStudent = (currentStudents || []).find(s => Number(s.id) === Number(studentId));
    if (selStudent && (selStudent.makeupLimitReached || (selStudent.makeupCount || 0) >= 2)) {
        const count = selStudent.makeupCount || 2;
        const msg = `Học viên ${selStudent.fullName} đã đạt giới hạn học bù (${count}/2 lần). Không thể gửi thêm đơn xin nghỉ.`;
        showToast('danger', msg);
        if (typeof openResolveModal === 'function') {
            openResolveModal({
                headerClass: 'modal-header-rose',
                titleHtml: '<i class="bi bi-exclamation-octagon-fill me-2"></i>Đã Đạt Giới Hạn Học Bù',
                summaryHtml: `
                    <div class="p-2 mb-2 text-danger fw-semibold">
                        <i class="bi bi-x-circle-fill me-1.5"></i>${escapeHtml(msg)}
                    </div>
                    <div class="small text-secondary">
                        Theo quy định của trung tâm, mỗi học viên chỉ được xếp học bù tối đa <strong>2 lần</strong> trong một khóa học.
                    </div>
                `,
                hideNote: true,
                confirmBtnText: 'Đã Hiểu',
                confirmBtnClass: 'btn btn-secondary rounded-pill px-4',
                onConfirm: () => {}
            });
        }
        return;
    }

    const btnSubmit = document.getElementById('btnSubmitAbsence');
    if (btnSubmit) {
        btnSubmit.disabled = true;
        btnSubmit.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Đang gửi...';
    }

    try {
        await callApi('/api/attendance-makeup/absence-requests', 'POST', {
            studentId: Number(studentId),
            lessonId: Number(lessonId),
            reason: reason
        });

        showToast('success', 'Đã gửi đơn xin nghỉ.');
        const reasonInput = document.getElementById('createReasonInput');
        if (reasonInput) reasonInput.value = '';
        document.querySelectorAll('.reason-chip, .lesson-pick-card').forEach(b => b.classList.remove('active'));
        const lessonSelect = document.getElementById('createLessonSelect');
        if (lessonSelect) lessonSelect.value = '';
        updateReasonCharCount();

        await reloadData();
        if (window.refreshNotifications) window.refreshNotifications();
        switchMainTab('ABSENCE');
    } catch (err) {
        showToast('danger', 'Không thể gửi đơn xin nghỉ: ' + err.message);
        if (typeof openResolveModal === 'function' && err.message && (err.message.includes('học bù') || err.message.includes('2 lần'))) {
            openResolveModal({
                headerClass: 'modal-header-rose',
                titleHtml: '<i class="bi bi-exclamation-octagon-fill me-2"></i>Không Thể Gửi Đơn Xin Nghỉ',
                summaryHtml: `<div class="p-2 text-danger fw-semibold">${escapeHtml(err.message)}</div>`,
                hideNote: true,
                confirmBtnText: 'Đã Hiểu',
                confirmBtnClass: 'btn btn-secondary rounded-pill px-4',
                onConfirm: () => {}
            });
        }
    } finally {
        if (btnSubmit) {
            btnSubmit.disabled = false;
            btnSubmit.innerHTML = '<i class="bi bi-send-fill me-1"></i>Gửi Đơn Xin Nghỉ';
        }
    }
}

// -----------------------------------------------------------------------------
// 7. CONFIRM / CANCEL ABSENCE REQUEST & DETAIL POPUP
// -----------------------------------------------------------------------------

function confirmCancelAbsence(absenceId) {
    const found = (currentAbsenceList || []).find(a => Number(a.id) === Number(absenceId));
    const label = found ? `${escapeHtml(found.studentName || 'Học viên')} • ${escapeHtml(found.className || '')} (${found.lessonDate || ''})` : `Đơn #${absenceId}`;
    openResolveModal({
        headerClass: 'modal-header-rose',
        titleHtml: '<i class="bi bi-trash3-fill me-2"></i>Hủy Đơn Xin Nghỉ',
        summaryHtml: `Hủy đơn xin nghỉ của <strong>${label}</strong>?`,
        hideNote: true,
        btnClass: 'btn-rose-solid',
        btnText: '<i class="bi bi-trash3 me-1"></i>Hủy Đơn',
        toastType: 'warning',
        toastMessage: 'Đã hủy đơn xin nghỉ.',
        onConfirm: async () => callApi(`/api/attendance-makeup/absence-requests/${absenceId}`, 'DELETE')
    });
}

function openResolveModal(config) {
    activeResolveConfig = config;
    const header = document.getElementById('resolveModalHeader');
    const closeBtn = document.getElementById('resolveModalCloseBtn');
    const title = document.getElementById('resolveModalTitle');
    const summary = document.getElementById('resolveModalSummary');
    const noteGroup = document.getElementById('resolveNoteGroup');
    const noteLabel = document.getElementById('resolveNoteLabel');
    const noteInput = document.getElementById('resolveNoteInput');
    const noteError = document.getElementById('resolveNoteError');
    const btnConfirm = document.getElementById('btnConfirmResolve');
    const quickSuggestionsEl = document.getElementById('resolveQuickSuggestions');

    if (header) header.className = `modal-header ${config.headerClass || 'modal-header-emerald'}`;
    if (closeBtn) closeBtn.className = config.lightHeader ? 'btn-close' : 'btn-close btn-close-white';
    if (title) title.innerHTML = config.titleHtml;
    if (summary) summary.innerHTML = config.summaryHtml;
    if (noteGroup) noteGroup.style.display = config.hideNote ? 'none' : 'block';
    if (noteLabel) noteLabel.innerHTML = `${config.noteLabel || 'Ghi chú'}${config.noteRequired ? ' <span class="text-danger">*</span>' : ''}`;
    if (noteInput) {
        noteInput.value = config.defaultNote || '';
        noteInput.placeholder = config.placeholder || 'Nhập nội dung...';
        noteInput.oninput = () => {
            if (noteError) noteError.style.display = 'none';
        };
    }
    if (noteError) noteError.style.display = 'none';

    if (btnConfirm) {
        btnConfirm.className = `btn rounded-pill px-4 fw-semibold ${config.btnClass || 'btn-primary'}`;
        btnConfirm.innerHTML = config.btnText || 'Xác Nhận';
    }

    if (modalResolveObj) modalResolveObj.show();
}

async function executeResolveNote() {
    if (!activeResolveConfig) return;
    const cfg = activeResolveConfig;
    const noteVal = (document.getElementById('resolveNoteInput')?.value || '').trim();
    const noteError = document.getElementById('resolveNoteError');

    if (cfg.noteRequired && !noteVal) {
        if (noteError) noteError.style.display = 'block';
        return;
    }

    try {
        await cfg.onConfirm(noteVal);
        if (modalResolveObj) modalResolveObj.hide();
        showToast(cfg.toastType || 'success', cfg.toastMessage || 'Đã cập nhật.');
        await reloadData();
        if (window.refreshNotifications) window.refreshNotifications();
    } catch (err) {
        showToast('danger', 'Lỗi: ' + err.message);
    }
}

async function viewAbsenceDetail(id) {
    try {
        const item = await callApi(`/api/attendance-makeup/absence-requests/${id}`);
        if (!item) return;

        const cleanReviewNote = formatCleanNote(item.reviewNote);
        const cleanMakeupNote = formatCleanNote(item.makeupNote);

        const titleEl = document.getElementById('modalDetailTitle');
        if (titleEl) titleEl.innerHTML = `<i class="bi bi-file-earmark-text-fill me-2 text-warning"></i>Đơn Xin Nghỉ #${item.id}`;

        const body = document.getElementById('detailModalBody');
        if (body) {
            const makeupStatusHtml = typeof getMakeupStatusBadgeHtml === 'function'
                ? getMakeupStatusBadgeHtml(item.makeupStatus, item.makeupRetryCount, item.makeupMaxRetries)
                : '';

            const isApproved = item.status === 'APPROVED';
            const isRejected = item.status === 'REJECTED';

            body.innerHTML = `
                <!-- 1. THẺ THÔNG TIN HỌC VIÊN & TRẠNG THÁI -->
                <div class="detail-info-card mb-3">
                    <div class="d-flex justify-content-between align-items-start gap-2">
                        <div class="d-flex align-items-center gap-2.5">
                            <div class="rounded-circle text-white d-flex align-items-center justify-content-center fw-bold shadow-sm" style="width: 40px; height: 40px; font-size: 0.95rem; background: #143c34; flex-shrink: 0;">
                                ${escapeHtml(getInitials(item.studentName || 'HV'))}
                            </div>
                            <div>
                                <div class="fw-bold text-dark fs-6 lh-sm">${escapeHtml(item.studentName || 'Học viên')}</div>
                                <div class="small text-secondary mt-1">
                                    PH: ${escapeHtml(item.parentName || 'N/A')}${item.parentPhone ? ` &bull; ${escapeHtml(item.parentPhone)}` : ''}
                                </div>
                                <div class="mt-1.5">${renderMakeupCounterBadge(item.courseMakeupCount ?? 0, 2)}</div>
                            </div>
                        </div>
                        ${getAbsenceStatusBadgeHtml(item.status)}
                    </div>

                    <!-- THỜI GIAN GỬI ĐƠN & PHẢN HỒI -->
                    <div class="detail-meta-row mt-2.5 pt-2 border-top">
                        <span><i class="bi bi-send text-primary me-1.5"></i>Gửi đơn:</span>
                        <strong>${formatDateTime(item.createdAt)}</strong>
                    </div>

                    ${item.reviewedAt ? `
                    <div class="detail-meta-row">
                        <span><i class="bi bi-check2-circle text-success me-1.5"></i>Phản hồi:</span>
                        <strong class="text-success">${formatDateTime(item.reviewedAt)}</strong>
                    </div>
                    ` : ''}
                </div>

                <!-- 2. BUỔI HỌC XIN NGHỈ -->
                <div class="mb-3">
                    <div class="detail-section-label">
                        <i class="bi bi-calendar-x text-danger"></i> Buổi nghỉ
                    </div>
                    <div class="detail-info-card bg-subtle">
                        <div class="fw-bold text-dark fs-6">${escapeHtml(cleanTitle(item.lessonTitle || `Buổi ${item.sessionNumber || ''}`))}</div>
                        <div class="d-flex flex-wrap gap-2 mt-1.5 align-items-center">
                            <span class="detail-meta-badge">
                                <i class="bi bi-calendar3 text-primary"></i> ${item.lessonDate || ''}
                            </span>
                            <span class="detail-meta-badge">
                                <i class="bi bi-clock text-secondary"></i> ${item.timeSlot || ''}
                            </span>
                            <span class="fw-semibold text-secondary small">
                                &bull; ${escapeHtml(cleanTitle(item.className || ''))}
                            </span>
                        </div>
                        <div class="small text-muted mt-2 pt-1.5 border-top d-flex flex-wrap gap-3">
                            <span><i class="bi bi-geo-alt text-danger me-1"></i>${escapeHtml(cleanTitle(item.roomName || 'Phòng học'))}</span>
                            <span><i class="bi bi-person-video3 text-success me-1"></i>GV: ${escapeHtml(item.teacherName || 'N/A')}</span>
                        </div>
                    </div>
                </div>

                <!-- 3. LÝ DO XIN NGHỈ -->
                <div class="mb-3">
                    <div class="detail-section-label">
                        <i class="bi bi-chat-left-text text-secondary"></i> Lý do
                    </div>
                    <div class="detail-reason-box">
                        <i class="bi bi-quote fs-5 text-secondary opacity-50 align-middle me-1"></i>${escapeHtml(item.reason || 'Không có ghi chú lý do.')}
                    </div>
                </div>

                <!-- 4. PHẢN HỒI DUYỆT (NẾU CÓ) -->
                ${cleanReviewNote || item.reviewedAt ? `
                    <div class="mb-3">
                        <div class="detail-section-label">
                            <i class="bi bi-chat-square-quote ${isRejected ? 'text-danger' : 'text-success'}"></i> Phản hồi
                        </div>
                        <div class="detail-response-box ${isRejected ? 'rejected' : ''}">
                            <div class="fw-semibold mb-1">${escapeHtml(cleanReviewNote || (isApproved ? 'Đã duyệt yêu cầu.' : 'Đã phản hồi.'))}</div>
                            <div class="small text-muted pt-1.5 mt-1.5 border-top border-opacity-25 border-secondary">
                                Người duyệt: <strong>${escapeHtml(item.approvedByTeacherName || 'Giáo viên phụ trách')}</strong>
                            </div>
                        </div>
                    </div>
                ` : ''}

                <!-- 5. CA HỌC BÙ LIÊN KẾT (NẾU CÓ) -->
                ${item.makeupRegistrationId ? `
                    <div class="detail-info-card card-accent-emerald">
                        <div class="d-flex justify-content-between align-items-center mb-1.5">
                            <span class="detail-section-label mb-0 text-success">
                                <i class="bi bi-calendar2-check-fill me-1"></i>Ca học bù #${item.makeupRegistrationId}
                            </span>
                            ${makeupStatusHtml}
                        </div>
                        ${item.makeupTargetDate ? `<div class="small text-dark mt-1">Ngày bù: <strong>${item.makeupTargetDate}</strong></div>` : ''}
                        ${item.makeupTargetClassName ? `<div class="small text-secondary mt-0.5">Lớp bù: <strong>${escapeHtml(cleanTitle(item.makeupTargetClassName))}</strong></div>` : ''}
                        ${cleanMakeupNote ? `<div class="small text-danger mt-2 p-2 bg-white rounded-3 border"><i class="bi bi-info-circle-fill me-1"></i>${escapeHtml(cleanMakeupNote)}</div>` : ''}
                    </div>
                ` : ''}
            `;
        }

        const footer = document.getElementById('detailModalFooter');
        if (footer) {
            const role = currentUser ? currentUser.role : '';
            const isCoordinator = ['STAFF', 'ADMIN', 'BRANCH_MANAGER'].includes(role);
            const isCreator = role === 'PARENT';
            const myId = currentUser ? (currentUser.userId ?? currentUser.id) : null;
            const canReview = isCoordinator || (role === 'TEACHER' && (myId == null || item.teacherId == null || Number(myId) === Number(item.teacherId)));

            let footerActions = '';
            if (canReview && item.status === 'PENDING') {
                footerActions += `
                    <button type="button" class="btn btn-sm btn-success rounded-pill px-3" onclick="modalDetailObj.hide(); openApproveModal(${item.id}, '${escapeHtml(item.studentName || '')}')">
                        <i class="bi bi-check-lg me-1"></i>Duyệt
                    </button>
                    <button type="button" class="btn btn-sm btn-danger rounded-pill px-3" onclick="modalDetailObj.hide(); openRejectModal(${item.id}, '${escapeHtml(item.studentName || '')}')">
                        <i class="bi bi-x-lg me-1"></i>Từ chối
                    </button>
                `;
            }
            if ((isCreator || isCoordinator) && item.status === 'PENDING') {
                footerActions += `
                    <button type="button" class="btn btn-sm btn-outline-danger rounded-pill px-3" onclick="modalDetailObj.hide(); confirmCancelAbsence(${item.id})">
                        <i class="bi bi-trash3 me-1"></i>Hủy đơn
                    </button>
                `;
            }
            if (isCoordinator && item.status === 'APPROVED' && item.makeupRegistrationId) {
                const mStatus = item.makeupStatus;
                if (mStatus === 'PENDING' && typeof openScheduleModal === 'function') {
                    footerActions += `
                        <button type="button" class="btn btn-sm btn-primary rounded-pill px-3" onclick="modalDetailObj.hide(); openScheduleModal(${item.makeupRegistrationId})">
                            <i class="bi bi-calendar-plus me-1"></i>Xếp lớp
                        </button>
                    `;
                }
                if (mStatus === 'PENDING' && typeof markNoSlot === 'function') {
                    footerActions += `
                        <button type="button" class="btn btn-sm btn-outline-warning text-dark rounded-pill px-3" onclick="modalDetailObj.hide(); markNoSlot(${item.makeupRegistrationId})">
                            <i class="bi bi-exclamation-triangle me-1"></i>Chưa có lớp
                        </button>
                    `;
                }
            }

            footer.innerHTML = `
                <div class="d-flex flex-wrap gap-2">${footerActions}</div>
                <button type="button" class="btn btn-secondary rounded-pill px-4" data-bs-dismiss="modal">Đóng</button>
            `;
        }

        if (modalDetailObj) modalDetailObj.show();
    } catch (err) {
        showToast('danger', 'Lỗi xem chi tiết: ' + err.message);
    }
}
