/**
 * =============================================================================
 * MODULE TAB 3: ĐIỀU PHỐI & QUẢN LÝ LỊCH HỌC BÙ (makeup-schedule.js)
 * =============================================================================
 * Phụ thuộc: makeup-common.js
 * Mục lục tra cứu nhanh:
 *   0. Huy hiệu trạng thái ca bù: getMakeupStatusBadgeHtml
 *   1. Tải dữ liệu, bộ lọc, phân trang & render bảng Tab 3:
 *      populateMakeupFilterOptions, applyMakeupFilter, loadMakeupRequests, renderMakeupTable
 *   2. Giai đoạn 3 - Modal xếp lịch bù thông minh: selectScheduleTargetCard, openScheduleModal, executeSchedule
 *   3. Thao tác kết thúc / hủy / báo chưa có lớp:
 *      confirmCompleteMakeup, confirmCancelMakeup, familyDeclines, markNoSlot
 *   4. Popup chi tiết ca học bù: viewMakeupDetail
 */

// Pagination state for Makeup Requests (Tab 3)
let makeupPage = 1;
let makeupPageSize = 5;

// Currently selected makeup request ID for scheduling
let activeMakeupId = null;

// Bootstrap modal for scheduling
let modalScheduleObj = null;

// Initialize modal when DOM is ready
window.addEventListener('DOMContentLoaded', () => {
    const modalScheduleEl = document.getElementById('modalSchedule');
    if (modalScheduleEl) {
        modalScheduleObj = new bootstrap.Modal(modalScheduleEl);
    }
});

/**
 * Returns badge HTML corresponding to makeup registration status and retry count.
 */
function getMakeupStatusBadgeHtml(status, note = '') {
    const rawNote = String(note || '');
    if (status === 'PENDING') return '<span class="badge-status-modern status-pending"><i class="bi bi-hourglass-split"></i>Chờ Xếp Lớp</span>';
    if (status === 'SCHEDULED') return '<span class="badge-status-modern status-scheduled"><i class="bi bi-calendar2-check"></i>Đã Xếp Lớp</span>';
    if (status === 'NO_SLOT') return '<span class="badge-status-modern status-noslot"><i class="bi bi-exclamation-circle"></i>Chưa Có Lớp</span>';
    if (status === 'MISSED') return '<span class="badge-status-modern status-missed"><i class="bi bi-person-x-fill"></i>Bỏ Lỡ Bù</span>';
    if (status === 'COMPLETED') return '<span class="badge-status-modern status-completed"><i class="bi bi-check2-all"></i>Hoàn Thành</span>';
    if (status === 'CANCELLED') {
        if (rawNote.includes('- KHÓA]')) {
            return '<span class="badge-status-modern status-missed"><i class="bi bi-lock-fill"></i>Hết Quyền Bù</span>';
        }
        if (rawNote.includes('Chưa có lớp phù hợp')) {
            return '<span class="badge-status-modern status-noslot"><i class="bi bi-exclamation-circle"></i>Hủy (Chưa có lớp)</span>';
        }
        return '<span class="badge-status-modern status-cancelled"><i class="bi bi-x-circle"></i>Đã Hủy</span>';
    }
    return `<span class="badge bg-light text-secondary border">${escapeHtml(status || 'Chưa có')}</span>`;
}

// -----------------------------------------------------------------------------
// 1. DATA LOADING & FILTERS (TAB 3)
// -----------------------------------------------------------------------------

function sortMakeupList(list) {
    const priorityMap = { PENDING: 0, NO_SLOT: 1, MISSED: 2, SCHEDULED: 3, COMPLETED: 4, CANCELLED: 5 };
    return [...(list || [])].sort((a, b) => {
        const pa = priorityMap[a.status] ?? 99;
        const pb = priorityMap[b.status] ?? 99;
        if (pa !== pb) return pa - pb;
        const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        if (ta !== tb) return tb - ta;
        return (b.id || 0) - (a.id || 0);
    });
}

function isFutureMakeup(item) {
    if (!item || !item.targetLessonDate) return false;
    const now = new Date();
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    if (item.targetLessonDate > todayStr) return true;
    if (item.targetLessonDate === todayStr) {
        const startStr = String(item.targetStartTime || (item.targetTimeSlot ? item.targetTimeSlot.split('-')[0].trim() : '')).substring(0, 5);
        const nowStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
        if (startStr && startStr > nowStr) return true;
    }
    return false;
}

function populateMakeupFilterOptions() {
    const stuSel = document.getElementById('filterMakeupStudent');
    const clsSel = document.getElementById('filterMakeupClass');
    const branchSel = document.getElementById('filterMakeupBranch');
    if (stuSel) {
        const prev = stuSel.value;
        const names = Array.from(new Set((currentMakeupList || []).map(m => m.studentName).filter(Boolean)))
            .sort((a, b) => a.localeCompare(b, 'vi'));
        stuSel.innerHTML = '<option value="">Tất cả</option>'
            + names.map(n => `<option value="${escapeHtml(n)}">${escapeHtml(n)}</option>`).join('');
        if (prev && names.includes(prev)) stuSel.value = prev;
    }
    if (clsSel) {
        const prev = clsSel.value;
        const classSet = new Set();
        (currentMakeupList || []).forEach(m => {
            if (m.originalClassName) classSet.add(m.originalClassName);
            if (m.targetClassName) classSet.add(m.targetClassName);
        });
        const classes = Array.from(classSet).sort((a, b) => a.localeCompare(b, 'vi'));
        clsSel.innerHTML = '<option value="">Tất cả</option>'
            + classes.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
        if (prev && classes.includes(prev)) clsSel.value = prev;
    }
    if (branchSel) {
        const prev = branchSel.value;
        const branchSet = new Set();
        (currentMakeupList || []).forEach(m => {
            if (m.branchName) branchSet.add(m.branchName);
            if (m.targetBranchName) branchSet.add(m.targetBranchName);
        });
        const branches = Array.from(branchSet).sort((a, b) => a.localeCompare(b, 'vi'));
        branchSel.innerHTML = '<option value="">Tất cả</option>'
            + branches.map(b => `<option value="${escapeHtml(b)}">${escapeHtml(b)}</option>`).join('');
        if (prev && branches.includes(prev)) branchSel.value = prev;
    }
}

function applyMakeupFilter() {
    renderMakeupTable(true);
}

function resetMakeupFilter() {
    ['filterMakeupStatus', 'filterMakeupStudent', 'filterMakeupClass', 'filterMakeupBranch',
     'filterMakeupDirection', 'filterMakeupFromDate', 'filterMakeupToDate', 'searchMakeupInput'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    renderMakeupTable(true);
}

async function loadMakeupRequests() {
    try {
        const data = await callApi('/api/attendance-makeup/makeup-requests', 'GET', null, false, false);
        currentMakeupList = sortMakeupList(data || []);
        populateMakeupFilterOptions();
        renderMakeupTable();
    } catch (err) {
        console.error("Failed to load makeup requests:", err);
        const tbody = document.getElementById('makeupTableBody');
        if (tbody) {
            tbody.innerHTML = `
                <tr><td colspan="7" class="text-center text-danger py-4">Lỗi tải dữ liệu: ${err.message}</td></tr>
            `;
        }
    }
}

function onMakeupPageSizeChange() {
    const sel = document.getElementById('makeupPageSizeSelect');
    makeupPageSize = Number(sel ? sel.value : 5) || 5;
    makeupPage = 1;
    renderMakeupTable(false);
}

function goToMakeupPage(page) {
    makeupPage = page;
    renderMakeupTable(false);
}

function renderMakeupTable(resetPage = false) {
    if (resetPage) makeupPage = 1;
    const tbody = document.getElementById('makeupTableBody');
    if (!tbody) return;

    const paginationWrapper = document.getElementById('makeupPaginationWrapper');
    const paginationInfo = document.getElementById('makeupPaginationInfo');
    const paginationList = document.getElementById('makeupPaginationList');

    const statusFilter = document.getElementById('filterMakeupStatus')?.value || '';
    const studentFilter = document.getElementById('filterMakeupStudent')?.value || '';
    const classFilter = document.getElementById('filterMakeupClass')?.value || '';
    const branchFilter = document.getElementById('filterMakeupBranch')?.value || '';
    const dirFilter = document.getElementById('filterMakeupDirection') ? document.getElementById('filterMakeupDirection').value : '';
    const fromDate = document.getElementById('filterMakeupFromDate')?.value || '';
    const toDate = document.getElementById('filterMakeupToDate')?.value || '';
    const searchKeyword = (document.getElementById('searchMakeupInput')?.value || '').trim().toLowerCase();
    const myId = currentUser ? (currentUser.userId ?? currentUser.id) : null;

    let filtered = sortMakeupList(currentMakeupList);
    if (dirFilter === 'OUT' && myId) {
        filtered = filtered.filter(m => Number(m.originalTeacherId) === Number(myId) && Number(m.targetTeacherId) !== Number(myId));
    } else if (dirFilter === 'IN' && myId) {
        filtered = filtered.filter(m => Number(m.targetTeacherId) === Number(myId) && Number(m.originalTeacherId) !== Number(myId));
    }
    if (statusFilter === 'NO_SLOT') {
        filtered = filtered.filter(m => m.status === 'NO_SLOT'
            || (m.status === 'CANCELLED' && m.note && m.note.includes('Chưa có lớp phù hợp')));
    } else if (statusFilter === 'MISSED') {
        filtered = filtered.filter(m => m.status === 'MISSED'
            || (m.status === 'CANCELLED' && m.note && m.note.includes('- KHÓA]')));
    } else if (statusFilter === 'CANCELLED') {
        filtered = filtered.filter(m => m.status === 'CANCELLED'
            && !(m.note && (m.note.includes('Chưa có lớp phù hợp') || m.note.includes('- KHÓA]'))));
    } else if (statusFilter) {
        filtered = filtered.filter(m => m.status === statusFilter);
    }
    if (studentFilter) {
        filtered = filtered.filter(m => m.studentName === studentFilter);
    }
    if (classFilter) {
        filtered = filtered.filter(m => m.originalClassName === classFilter || m.targetClassName === classFilter);
    }
    if (branchFilter) {
        filtered = filtered.filter(m => m.branchName === branchFilter || m.targetBranchName === branchFilter);
    }
    if (fromDate) {
        filtered = filtered.filter(m =>
            (m.originalLessonDate && m.originalLessonDate >= fromDate) ||
            (m.targetLessonDate && m.targetLessonDate >= fromDate)
        );
    }
    if (toDate) {
        filtered = filtered.filter(m =>
            (m.originalLessonDate && m.originalLessonDate <= toDate) ||
            (m.targetLessonDate && m.targetLessonDate <= toDate)
        );
    }
    if (searchKeyword) {
        filtered = filtered.filter(m =>
            (m.studentName && m.studentName.toLowerCase().includes(searchKeyword)) ||
            (m.targetClassName && m.targetClassName.toLowerCase().includes(searchKeyword)) ||
            (m.originalClassName && m.originalClassName.toLowerCase().includes(searchKeyword)) ||
            (m.targetRoomName && m.targetRoomName.toLowerCase().includes(searchKeyword)) ||
            (m.note && m.note.toLowerCase().includes(searchKeyword)) ||
            (m.id && m.id.toString().includes(searchKeyword))
        );
    }

    const countBadge = document.getElementById('makeupCountBadge');
    if (countBadge) countBadge.innerText = `${filtered.length} ca bù`;

    if (filtered.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center text-muted py-5">
                    <i class="bi bi-inbox fs-2 d-block mb-2 text-secondary"></i>
                    Không có yêu cầu học bù nào phù hợp
                </td>
            </tr>
        `;
        if (paginationWrapper) paginationWrapper.classList.add('d-none');
        return;
    }

    const totalItems = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / makeupPageSize));
    if (makeupPage > totalPages) makeupPage = totalPages;
    if (makeupPage < 1) makeupPage = 1;

    const startIdx = (makeupPage - 1) * makeupPageSize;
    const endIdx = Math.min(startIdx + makeupPageSize, totalItems);
    const pagedItems = filtered.slice(startIdx, endIdx);

    const isStaffOrAdmin = currentUser && (currentUser.role === 'ADMIN' || currentUser.role === 'BRANCH_MANAGER' || currentUser.role === 'STAFF');
    const isTeacher = currentUser && currentUser.role === 'TEACHER';
    const isParent = currentUser && currentUser.role === 'PARENT';

    tbody.innerHTML = pagedItems.map(item => {
        const statusBadge = getMakeupStatusBadgeHtml(item.status, item.note);

        const origDateStr = item.originalLessonDate || '';
        const origTimeStr = item.originalTimeSlot || '';

        const isOrigTeacher = isTeacher && myId != null && Number(item.originalTeacherId) === Number(myId);
        const isTargetTeacher = isTeacher && myId != null && Number(item.targetTeacherId) === Number(myId);

        let targetCell = '<span class="text-muted small">Chưa xếp</span>';
        if (item.targetLessonId) {
            const sessionCompareBadge = (item.originalSessionNumber && item.targetSessionNumber && item.originalSessionNumber !== item.targetSessionNumber)
                ? `<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle ms-1" style="font-size: 0.65rem;">Linh hoạt B${item.targetSessionNumber}</span>`
                : (item.targetSessionNumber ? `<span class="badge bg-success-subtle text-success border border-success-subtle ms-1" style="font-size: 0.65rem;">Buổi ${item.targetSessionNumber}</span>` : '');
            const directionBadge = isTeacher
                ? (isTargetTeacher && !isOrigTeacher
                    ? '<span class="badge bg-info-subtle text-info-emphasis border border-info-subtle ms-1" style="font-size: 0.65rem;">Bù vào lớp</span>'
                    : (isOrigTeacher && !isTargetTeacher
                        ? '<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle ms-1" style="font-size: 0.65rem;">Chuyển đi</span>'
                        : (isOrigTeacher && isTargetTeacher
                            ? '<span class="badge bg-primary-subtle text-primary border border-primary-subtle ms-1" style="font-size: 0.65rem;">Cùng GV</span>'
                            : '')))
                : '';
            targetCell = `
                <div class="tbl-cell-title">
                    ${escapeHtml(item.targetClassName || 'Lớp bù')}${sessionCompareBadge}${directionBadge}
                </div>
                <div class="tbl-cell-sub mt-1 d-flex align-items-center flex-wrap gap-1">
                    <span class="tbl-date-pill">${item.targetLessonDate || ''}</span>
                    <span>${item.targetTimeSlot || ''}</span>
                </div>
                <div class="tbl-cell-sub mt-1">
                    ${escapeHtml(item.targetTeacherName || 'Chưa phân công')} &bull; ${escapeHtml(item.targetRoomName || 'Phòng học')}${item.targetBranchName ? ` &bull; ${escapeHtml(item.targetBranchName)}` : ''}
                </div>
            `;
        }

        let actionButtons = `
            <button class="btn-action-icon" title="Chi tiết" onclick="viewMakeupDetail(${item.id})">
                <i class="bi bi-eye"></i>
            </button>
        `;

        const canReschedule = (item.retryCount || 0) < (item.maxRetries || 1);
        const canComplete = !isFutureMakeup(item);

        if (item.status === 'PENDING') {
            if (isStaffOrAdmin) {
                actionButtons += `
                    <button class="btn-action-pill btn-action-pill-primary" title="Xếp lớp học bù" onclick="openScheduleModal(${item.id})">
                        <i class="bi bi-calendar-plus"></i>Xếp lớp
                    </button>
                    <button class="btn-action-icon btn-action-reject" title="Báo chưa có lớp" onclick="markNoSlot(${item.id})">
                        <i class="bi bi-exclamation-triangle"></i>
                    </button>
                    <button class="btn-action-icon" title="Hủy ca bù (gia đình không học bù)" onclick="familyDeclines(${item.id})">
                        <i class="bi bi-x-circle"></i>
                    </button>
                `;
            }
        } else if (item.status === 'SCHEDULED' || item.status === 'MISSED') {
            if ((isStaffOrAdmin || isTargetTeacher) && canComplete) {
                actionButtons += `
                    <button class="btn-action-pill btn-action-pill-primary" style="background:#16a34a;" title="Điểm danh có mặt" onclick="confirmCompleteMakeup(${item.id})">
                        <i class="bi bi-check2-all"></i>Có mặt
                    </button>
                `;
            }
            if (isStaffOrAdmin) {
                if (canReschedule) {
                    actionButtons += `
                        <button class="btn-action-icon btn-action-primary" title="Đổi ca học bù" onclick="openScheduleModal(${item.id})">
                            <i class="bi bi-arrow-repeat"></i>
                        </button>
                    `;
                }
                if (item.status === 'SCHEDULED' && isFutureMakeup(item)) {
                    actionButtons += `
                        <button class="btn-action-icon btn-action-reject" title="Hủy ca bù" onclick="confirmCancelMakeup(${item.id})">
                            <i class="bi bi-x-circle"></i>
                        </button>
                    `;
                }
            }
        }

        const cleanNote = formatCleanNote(item.note);
        const parentMeta = !isParent && item.parentName
            ? `<div class="tbl-cell-sub mt-1"><span>PH: ${escapeHtml(item.parentName)}${item.parentPhone ? ` &bull; ${escapeHtml(item.parentPhone)}` : ''}</span></div>`
            : '';
        const formattedCourse = typeof formatCourseAndLevel === 'function'
            ? formatCourseAndLevel(item.courseName, item.level)
            : (item.courseName || '');
        const courseMeta = formattedCourse
            ? `<div class="tbl-cell-sub mt-1"><span>${escapeHtml(formattedCourse)}</span></div>`
            : '';
        const quotaBadge = (item.courseMakeupCount != null && typeof renderMakeupCounterBadge === 'function')
            ? `<div class="mt-1">${renderMakeupCounterBadge(item.courseMakeupCount, item.courseMakeupLimit ?? 3)}</div>`
            : '';

        return `
            <tr>
                <td class="text-center"><span class="id-badge">#${item.id}</span></td>
                <td>
                    <div class="tbl-cell-title">${escapeHtml(item.studentName || 'Học viên')}</div>
                    ${parentMeta}
                    ${courseMeta}
                    ${quotaBadge}
                </td>
                <td>
                    <div class="tbl-cell-title">
                        ${escapeHtml(item.originalClassName || 'Lớp học')}
                    </div>
                    <div class="tbl-cell-sub mt-1 d-flex align-items-center flex-wrap gap-1">
                        <span class="tbl-date-pill">${origDateStr}</span>
                        <span>${origTimeStr}</span>
                    </div>
                    <div class="tbl-cell-sub mt-1">
                        ${escapeHtml(item.originalTeacherName || 'Chưa phân công')}${item.originalRoomName ? ` &bull; ${escapeHtml(item.originalRoomName)}` : ''}${item.branchName ? ` &bull; ${escapeHtml(item.branchName)}` : ''}
                    </div>
                </td>
                <td>${targetCell}</td>
                <td class="text-center">${statusBadge}</td>
                <td>
                    <div class="reason-text-clamp" title="${escapeHtml(cleanNote)}">
                        ${escapeHtml(cleanNote || '—')}
                    </div>
                </td>
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
            paginationInfo.textContent = `Hiển thị ${startIdx + 1} - ${endIdx} / ${totalItems} ca bù`;
        }
        if (paginationList) {
            let html = `
                <li class="page-item ${makeupPage <= 1 ? 'disabled' : ''}">
                    <button class="page-link rounded-2" type="button" onclick="goToMakeupPage(${makeupPage - 1})">&laquo; Trước</button>
                </li>
            `;
            for (let p = 1; p <= totalPages; p++) {
                html += `
                    <li class="page-item ${p === makeupPage ? 'active' : ''}">
                        <button class="page-link rounded-2" type="button" onclick="goToMakeupPage(${p})">${p}</button>
                    </li>
                `;
            }
            html += `
                <li class="page-item ${makeupPage >= totalPages ? 'disabled' : ''}">
                    <button class="page-link rounded-2" type="button" onclick="goToMakeupPage(${makeupPage + 1})">Sau &raquo;</button>
                </li>
            `;
            paginationList.innerHTML = html;
        }
    }
}

// -----------------------------------------------------------------------------
// 2. STAGE 3: SCHEDULING MAKEUP MODAL & SELECTION
// -----------------------------------------------------------------------------

function selectScheduleTargetCard(lessonId) {
    const select = document.getElementById('scheduleTargetLessonSelect');
    if (select) select.value = String(lessonId);
    document.querySelectorAll('#scheduleTargetPickerList .lesson-pick-card').forEach(btn => {
        btn.classList.toggle('active', Number(btn.dataset.lessonId) === Number(lessonId));
    });
}

async function openScheduleModal(makeupId) {
    if (typeof ensureSessionMatchesTab === 'function' && !ensureSessionMatchesTab()) return;
    activeMakeupId = makeupId;
    let item = currentMakeupList.find(m => Number(m.id) === Number(makeupId));
    if (!item) {
        try {
            item = await callApi(`/api/attendance-makeup/makeup-requests/${makeupId}`, 'GET', null, false, false);
        } catch (err) {
            showToast('danger', 'Không tải được ca học bù: ' + err.message);
            return;
        }
    }
    if (!item) return;

    const currentRetry = item.retryCount || 0;
    const maxRetries = item.maxRetries || 1;
    if (item.limitExceeded || currentRetry >= maxRetries) {
        showToast('danger', `Đã hết ${maxRetries}/${maxRetries} lần xếp học bù.`);
        return;
    }

    const nextAttempt = currentRetry + 1;
    const origSessionText = item.originalSessionNumber ? `#Buổi ${item.originalSessionNumber} • ` : '';
    const nameEl = document.getElementById('scheduleStudentName');
    const origLessonEl = document.getElementById('scheduleOrigLesson');
    const noteEl = document.getElementById('scheduleNoteInput');

    if (nameEl) nameEl.innerText = item.studentName || '';
    if (origLessonEl) origLessonEl.innerText = `${origSessionText}${item.originalClassName || ''} (${item.originalLessonDate || ''})`;
    const rawCleanNote = formatCleanNote((item.note || '').replace(/^\[Lần bù:\s*\d+\/\d+(?:\s*-\s*KHÓA)?\]\s*/, ''));
    if (noteEl) noteEl.value = item.status === 'PENDING' ? '' : rawCleanNote;

    const quotaBadge = document.getElementById('scheduleQuotaBadge');
    if (quotaBadge) {
        const mkCount = item.courseMakeupCount ?? 0;
        const mkLimit = item.courseMakeupLimit ?? 3;
        quotaBadge.className = mkCount >= mkLimit
            ? 'badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill px-3 py-1 fw-semibold'
            : 'badge rounded-pill px-3 py-1 fw-semibold';
        if (mkCount < mkLimit) {
            quotaBadge.style.background = '#eef6ff';
            quotaBadge.style.color = '#1d4ed8';
        }
        quotaBadge.innerHTML = `<i class="bi bi-calendar2-check me-1"></i>Quyền bù khóa: ${mkCount}/${mkLimit}`;
    }

    const retryBadge = document.getElementById('scheduleRetryBadge');
    if (retryBadge) {
        retryBadge.className = 'badge rounded-pill px-3 py-1 fw-semibold';
        retryBadge.style.background = '#e6f0ec';
        retryBadge.style.color = '#143c34';
        retryBadge.innerHTML = `<i class="bi bi-repeat me-1"></i>Xếp lịch ${nextAttempt}/${maxRetries}`;
    }

    const coordBanner = document.getElementById('scheduleSmartCoordinationBanner');
    if (coordBanner) {
        coordBanner.className = 'd-none mb-3';
        coordBanner.innerHTML = '';
    }

    const select = document.getElementById('scheduleTargetLessonSelect');
    const pickerList = document.getElementById('scheduleTargetPickerList');
    const summaryEl = document.getElementById('scheduleOptionSummary');

    if (select) select.innerHTML = '<option value="">-- Đang tải --</option>';
    if (summaryEl) summaryEl.textContent = '';
    if (pickerList) pickerList.innerHTML = '<div class="lesson-empty-box"><span class="spinner-border spinner-border-sm me-2 text-success"></span>Đang tải ca phù hợp...</div>';

    if (modalScheduleObj) modalScheduleObj.show();

    try {
        const availableLessons = await callApi(`/api/attendance-makeup/makeup-requests/${makeupId}/available-lessons`, 'GET', null, false, false);
        if (!availableLessons || availableLessons.length === 0) {
            if (select) select.innerHTML = '<option value="">-- Không có lớp phù hợp --</option>';
            if (pickerList) pickerList.innerHTML = '<div class="lesson-empty-box">Hiện chưa có lớp phù hợp sắp tới</div>';
            if (coordBanner) {
                coordBanner.className = 'alert alert-warning border-0 rounded-3 p-3 mb-3 small d-flex flex-wrap justify-content-between align-items-center gap-2';
                coordBanner.innerHTML = `
                    <span class="fw-semibold"><i class="bi bi-exclamation-triangle me-1"></i>Không tìm thấy ca trống cùng trình độ.</span>
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-sm btn-outline-danger rounded-pill px-3" onclick="modalScheduleObj.hide(); markNoSlot(${makeupId});">Báo chưa có lớp</button>
                    </div>
                `;
            }
            return;
        }

        const exactLessons = availableLessons.filter(l => l.exactSessionMatch !== false);
        const flexLessons = availableLessons.filter(l => l.exactSessionMatch === false);

        if (summaryEl) {
            const parts = [];
            if (exactLessons.length > 0) parts.push(`${exactLessons.length} đúng bài`);
            if (flexLessons.length > 0) parts.push(`${flexLessons.length} linh hoạt`);
            summaryEl.textContent = parts.join(' • ');
        }

        if (coordBanner && exactLessons.length === 0 && flexLessons.length > 0) {
            coordBanner.className = 'alert alert-warning border-0 rounded-3 py-2 px-3 mb-3 small d-flex flex-wrap justify-content-between align-items-center gap-2';
            coordBanner.innerHTML = `
                <span><i class="bi bi-lightbulb-fill me-1"></i>Đã qua Buổi #${item.originalSessionNumber || ''} — gợi ý ca ghép linh hoạt:</span>
                <div class="d-flex gap-2">
                    <button type="button" class="btn btn-sm btn-outline-dark rounded-pill px-3" onclick="modalScheduleObj.hide(); markNoSlot(${makeupId});">Chưa có lớp</button>
                </div>
            `;
        }

        if (select) {
            select.innerHTML = '<option value="">-- Chọn ca học bù --</option>' +
                availableLessons.map(l => `<option value="${l.id}">${l.lessonDate || ''} - ${l.className || ''}</option>`).join('');
        }

        const renderCard = (l, isExact) => {
            const dt = formatPickDate(l.lessonDate);
            const timeStr = l.timeSlot || `${l.startTime || ''} - ${l.endTime || ''}`;
            const titleStr = l.title || `Buổi ${l.sessionNumber || ''}`;
            const seatBadge = (l.availableSeats != null && l.maxStudents != null)
                ? `<span class="seat-pill">Còn ${l.availableSeats}/${l.maxStudents}</span>` : '';
            const matchBadge = isExact
                ? `<span class="schedule-match-pill schedule-match-exact">Đúng bài #${l.sessionNumber || ''}</span>`
                : `<span class="schedule-match-pill schedule-match-flex">Linh hoạt #${l.sessionNumber || ''}</span>`;
            const isActive = item.targetLessonId && Number(item.targetLessonId) === Number(l.id) ? ' active' : '';

            return `
                <button type="button" class="lesson-pick-card${isActive}" data-lesson-id="${l.id}" onclick="selectScheduleTargetCard(${l.id})">
                    <div class="lesson-pick-datebox">
                        <div class="lesson-pick-dow">${escapeHtml(dt.dow)}</div>
                        <div class="lesson-pick-day">${escapeHtml(dt.day)}</div>
                    </div>
                    <div class="lesson-pick-info">
                        <div class="d-flex align-items-center justify-content-between gap-2 mb-1">
                            <div class="lesson-pick-title">${escapeHtml(l.className || 'Lớp học')}</div>
                            ${matchBadge}
                        </div>
                        <div class="small text-secondary text-truncate">${escapeHtml(titleStr)}</div>
                        <div class="lesson-pick-meta">
                            <span><i class="bi bi-clock me-1"></i>${escapeHtml(timeStr)}</span>
                            ${l.teacherName ? `<span><i class="bi bi-person me-1"></i>${escapeHtml(l.teacherName)}</span>` : ''}
                            ${l.roomName ? `<span><i class="bi bi-door-open me-1"></i>${escapeHtml(l.roomName)}</span>` : ''}
                            ${l.branchName ? `<span><i class="bi bi-building me-1"></i>${escapeHtml(l.branchName)}</span>` : ''}
                            ${seatBadge}
                        </div>
                    </div>
                    <div class="lesson-pick-check"><i class="bi bi-check-lg"></i></div>
                </button>
            `;
        };

        let cardsHtml = '';
        if (exactLessons.length > 0) {
            cardsHtml += `<div class="schedule-picker-group-label"><span>Đúng bài gốc (Ưu tiên)</span><span>${exactLessons.length} ca</span></div>`;
            cardsHtml += exactLessons.map(l => renderCard(l, true)).join('');
        }
        if (flexLessons.length > 0) {
            cardsHtml += `<div class="schedule-picker-group-label mt-1"><span>Ghép bù linh hoạt cùng trình độ</span><span>${flexLessons.length} ca</span></div>`;
            cardsHtml += flexLessons.map(l => renderCard(l, false)).join('');
        }
        if (pickerList) pickerList.innerHTML = cardsHtml;

        if (item.targetLessonId) {
            selectScheduleTargetCard(item.targetLessonId);
        }
    } catch (err) {
        if (select) select.innerHTML = `<option value="">Lỗi: ${err.message}</option>`;
        if (pickerList) pickerList.innerHTML = `<div class="lesson-empty-box text-danger">Lỗi tải ca học: ${escapeHtml(err.message)}</div>`;
    }
}

async function executeSchedule() {
    if (typeof ensureSessionMatchesTab === 'function' && !ensureSessionMatchesTab()) return;
    if (!activeMakeupId) return;
    const targetLessonId = document.getElementById('scheduleTargetLessonSelect')?.value;
    const note = (document.getElementById('scheduleNoteInput')?.value || '').trim();

    if (!targetLessonId) {
        showToast('danger', 'Vui lòng chọn ca học bù.');
        return;
    }

    try {
        await callApi(`/api/attendance-makeup/makeup-requests/${activeMakeupId}/schedule`, 'PUT', {
            targetLessonId: Number(targetLessonId),
            note: note
        }, false, false);
        if (modalScheduleObj) modalScheduleObj.hide();
        showToast('success', 'Đã xếp lớp học bù thành công.');
        await reloadData();
        if (window.refreshNotifications) window.refreshNotifications();
    } catch (err) {
        showToast('danger', 'Lỗi xếp lớp: ' + err.message);
    }
}

// -----------------------------------------------------------------------------
// 3. STAGE 4: COMPLETE (ATTENDANCE) & CANCEL MAKEUP ACTIONS
// -----------------------------------------------------------------------------

function confirmCompleteMakeup(makeupId) {
    openResolveModal({
        headerClass: 'modal-header-emerald',
        titleHtml: '<i class="bi bi-check2-all me-2"></i>Điểm Danh Có Mặt',
        summaryHtml: `Xác nhận học viên đã hoàn thành ca học bù <strong>#${makeupId}</strong>?`,
        hideNote: true,
        btnClass: 'btn-primary',
        btnText: '<i class="bi bi-check-lg me-1"></i>Xác Nhận Có Mặt',
        toastType: 'success',
        toastMessage: 'Đã ghi nhận hoàn thành ca học bù.',
        onConfirm: async () => callApi(`/api/attendance-makeup/makeup-requests/${makeupId}/complete`, 'PUT', null, false, false)
    });
}

function confirmCancelMakeup(makeupId) {
    openResolveModal({
        headerClass: 'modal-header-rose',
        titleHtml: '<i class="bi bi-x-circle-fill me-2"></i>Hủy Ca Học Bù',
        summaryHtml: `Hủy ca học bù <strong>#${makeupId}</strong> và thông báo các bên liên quan.`,
        noteLabel: 'Lý do hủy',
        noteRequired: true,
        placeholder: 'Nhập lý do hủy ca học bù...',
        btnClass: 'btn-rose-solid',
        btnText: '<i class="bi bi-x-circle me-1"></i>Hủy Ca Bù',
        toastType: 'warning',
        toastMessage: 'Đã hủy ca học bù.',
        onConfirm: async (reason) => callApi(`/api/attendance-makeup/makeup-requests/${makeupId}/cancel`, 'PATCH', { reason }, false, false)
    });
}

function familyDeclines(makeupId) {
    openResolveModal({
        headerClass: 'modal-header-slate',
        titleHtml: '<i class="bi bi-x-circle me-2"></i>Gia Đình Không Học Bù',
        summaryHtml: `Xác nhận gia đình báo <strong>không cần học bù</strong> cho ca #${makeupId}?`,
        hideNote: true,
        btnClass: 'btn-secondary',
        btnText: 'Xác Nhận Hủy Bù',
        toastType: 'warning',
        toastMessage: 'Đã hủy yêu cầu học bù.',
        onConfirm: async () => callApi(`/api/attendance-makeup/makeup-requests/${makeupId}/cancel`, 'PATCH', {
            reason: 'Gia đình báo không cần học bù'
        }, false, false)
    });
}

function markNoSlot(makeupId) {
    const mId = Number(makeupId);
    let item = Array.isArray(currentMakeupList) ? currentMakeupList.find(m => m.id === mId) : null;
    if (!item && Array.isArray(currentAbsenceList)) {
        const abs = currentAbsenceList.find(a => a.makeupRegistrationId === mId);
        if (abs) {
            item = {
                id: mId,
                studentName: abs.studentName,
                originalClassName: abs.className,
                parentName: abs.parentName
            };
        }
    }

    let summaryHtml = `Ca học bù <strong>#${mId}</strong>`;
    if (item) {
        summaryHtml = `
            <div class="d-flex align-items-center justify-content-between mb-2 pb-2 border-bottom">
                <span class="text-secondary small">Học viên</span>
                <strong class="text-dark">${escapeHtml(item.studentName || 'Học viên')}</strong>
            </div>
            <div class="d-flex align-items-center justify-content-between mb-2 pb-2 border-bottom">
                <span class="text-secondary small">Lớp học</span>
                <span class="text-dark small fw-semibold">${escapeHtml(item.originalClassName || 'Lớp gốc')}</span>
            </div>
            <div class="d-flex align-items-center justify-content-between">
                <span class="text-secondary small">Mã ca</span>
                <span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle px-2 py-1 rounded-pill fw-semibold">#${mId}</span>
            </div>
        `;
    }

    openResolveModal({
        headerClass: 'modal-header-amber',
        lightHeader: false,
        titleHtml: '<i class="bi bi-exclamation-triangle-fill me-2"></i>Báo Chưa Có Lớp Phù Hợp',
        summaryHtml,
        noteLabel: 'Nội dung thông báo gửi phụ huynh',
        noteRequired: true,
        defaultNote: '',
        placeholder: 'Nhập nội dung thông báo gửi phụ huynh...',
        btnClass: 'btn-amber-solid',
        btnText: '<i class="bi bi-send-fill me-2"></i>Gửi Thông Báo',
        toastType: 'success',
        toastMessage: 'Đã gửi thông báo chưa có lớp tới phụ huynh.',
        onConfirm: async (note) => callApi(`/api/attendance-makeup/makeup-requests/${mId}/no-slot`, 'PATCH', { note }, false, false)
    });
}

// -----------------------------------------------------------------------------
// 4. MAKEUP DETAIL POPUP
// -----------------------------------------------------------------------------

async function viewMakeupDetail(id) {
    try {
        const item = await callApi(`/api/attendance-makeup/makeup-requests/${id}`, 'GET', null, false, false);
        if (!item) return;

        const cleanNote = formatCleanNote(item.note);
        const titleEl = document.getElementById('modalDetailTitle');
        if (titleEl) titleEl.innerHTML = `<i class="bi bi-calendar2-check-fill text-warning me-2"></i>Ca Học Bù #${item.id}`;

        const body = document.getElementById('detailModalBody');
        if (body) {
            const initials = typeof getInitials === 'function' ? getInitials(item.studentName) : 'HV';
            const fmtDate = typeof formatDateTime === 'function' ? formatDateTime : (val) => val || 'N/A';
            const formattedCourse = typeof formatCourseAndLevel === 'function'
                ? formatCourseAndLevel(item.courseName, item.level)
                : (item.courseName || '');
            const submitTime = item.absenceCreatedAt || item.createdAt;
            const reviewTime = item.reviewedAt || item.createdAt;

            body.innerHTML = `
                <!-- 1. THẺ HỌC VIÊN & TRẠNG THÁI CA BÙ -->
                <div class="detail-info-card mb-3">
                    <div class="d-flex justify-content-between align-items-start gap-2">
                        <div class="d-flex align-items-center gap-2">
                            <div class="rounded-circle text-white d-flex align-items-center justify-content-center fw-bold shadow-sm" style="width: 42px; height: 42px; font-size: 0.95rem; background: #1e5346; flex-shrink: 0;">
                                ${escapeHtml(initials)}
                            </div>
                            <div>
                                <div class="fw-bold text-dark fs-6 lh-sm">${escapeHtml(item.studentName || 'Học viên')}</div>
                                <div class="small text-secondary mt-1">
                                    <i class="bi bi-person-heart text-muted me-1"></i>PH: ${escapeHtml(item.parentName || 'N/A')}${item.parentPhone ? ` &bull; ${escapeHtml(item.parentPhone)}` : ''}
                                </div>
                                ${formattedCourse ? `<div class="small text-muted mt-1">${escapeHtml(formattedCourse)}</div>` : ''}
                                ${(item.courseMakeupCount != null && typeof renderMakeupCounterBadge === 'function')
                                    ? `<div class="mt-1">${renderMakeupCounterBadge(item.courseMakeupCount, item.courseMakeupLimit ?? 3)}</div>` : ''}
                            </div>
                        </div>
                        ${getMakeupStatusBadgeHtml(item.status, item.note)}
                    </div>

                    <!-- GỬI ĐƠN & DUYỆT ĐƠN -->
                    <div class="detail-meta-row mt-3 pt-2 border-top">
                        <span><i class="bi bi-send text-primary me-2"></i>Gửi đơn:</span>
                        <strong>${fmtDate(submitTime)}</strong>
                    </div>

                    <div class="detail-meta-row">
                        <span><i class="bi bi-check2-circle text-success me-2"></i>Duyệt đơn:</span>
                        <strong class="text-success">${fmtDate(reviewTime)}</strong>
                    </div>

                    ${item.reviewerName ? `
                    <div class="detail-meta-row">
                        <span><i class="bi bi-person-check text-secondary me-2"></i>Người duyệt:</span>
                        <strong>${escapeHtml(item.reviewerName)}</strong>
                    </div>
                    ` : ''}
                </div>

                <!-- 2. BUỔI NGHỈ -->
                <div class="mb-3">
                    <div class="detail-section-label">
                        <i class="bi bi-calendar-x text-secondary"></i> Buổi nghỉ
                    </div>
                    <div class="detail-info-card bg-subtle">
                        <div class="fw-bold text-dark fs-6">${escapeHtml(cleanTitle(item.originalClassName || ''))}</div>
                        <div class="d-flex flex-wrap gap-2 mt-2 align-items-center">
                            <span class="detail-meta-badge">
                                <i class="bi bi-calendar3 text-primary"></i> ${item.originalLessonDate || ''}
                            </span>
                            <span class="detail-meta-badge">
                                <i class="bi bi-clock text-secondary"></i> ${item.originalTimeSlot || ''}
                            </span>
                        </div>
                        <div class="small text-muted mt-2 pt-2 border-top d-flex flex-wrap gap-3">
                            <span><i class="bi bi-person-video3 text-success me-1"></i>GV gốc: <strong>${escapeHtml(item.originalTeacherName || 'N/A')}</strong></span>
                            ${item.originalRoomName ? `<span><i class="bi bi-geo-alt text-danger me-1"></i>${escapeHtml(item.originalRoomName)}</span>` : ''}
                            ${item.branchName ? `<span><i class="bi bi-building text-primary me-1"></i>${escapeHtml(item.branchName)}</span>` : ''}
                        </div>
                        ${item.absenceReason ? `
                        <div class="small text-secondary mt-2 pt-2 border-top">
                            <span class="text-muted"><i class="bi bi-chat-left-dots me-1"></i>Lý do nghỉ:</span> ${escapeHtml(item.absenceReason)}
                        </div>
                        ` : ''}
                    </div>
                </div>

                <!-- 3. CA HỌC BÙ -->
                <div class="mb-3">
                    <div class="detail-section-label">
                        <i class="bi bi-calendar-check text-success"></i> Ca học bù
                    </div>
                    ${item.targetLessonId ? `
                        <div class="detail-info-card card-accent-emerald">
                            <div class="d-flex flex-wrap gap-2 align-items-center">
                                <span class="badge bg-success text-white px-3 py-1 rounded-pill">
                                    <i class="bi bi-check-circle-fill me-1"></i>ĐÃ XẾP LỊCH
                                </span>
                                <span class="fw-bold text-success fs-6">
                                    ${item.targetLessonDate || ''} (${item.targetTimeSlot || ''})
                                </span>
                            </div>
                            <div class="fw-semibold text-dark mt-2">
                                <i class="bi bi-collection me-1 text-primary"></i>${escapeHtml(cleanTitle(item.targetClassName || ''))}
                                &bull; <i class="bi bi-geo-alt me-1 text-danger"></i>${escapeHtml(cleanTitle(item.targetRoomName || 'Phòng học'))}
                                ${item.targetBranchName ? ` &bull; ${escapeHtml(item.targetBranchName)}` : ''}
                            </div>
                            <div class="small text-muted mt-2 pt-2 border-top border-opacity-25 border-success">
                                <i class="bi bi-person-badge text-success me-1"></i>GV giảng dạy: <strong>${escapeHtml(item.targetTeacherName || 'N/A')}</strong>
                            </div>
                        </div>
                    ` : `
                        <div class="p-3 bg-light rounded-3 border d-flex align-items-center gap-2 text-warning-emphasis">
                            <i class="bi bi-hourglass-split fs-4 text-warning"></i>
                            <div>
                                <div class="fw-bold text-dark small">Chưa xếp ca học bù</div>
                                <div class="small text-muted" style="font-size: 0.78rem;">Đang chờ giáo vụ sắp xếp ca học có phòng và trình độ tương đương.</div>
                            </div>
                        </div>
                    `}
                </div>

                <!-- 4. GHI CHÚ (NẾU CÓ) -->
                ${cleanNote ? `
                    <div class="mb-2">
                        <div class="detail-section-label">
                            <i class="bi bi-sticky text-secondary"></i> Ghi chú
                        </div>
                        <div class="detail-reason-box">
                            <i class="bi bi-info-circle me-1 text-primary"></i>${escapeHtml(cleanNote)}
                        </div>
                    </div>
                ` : ''}
            `;
        }

        const footer = document.getElementById('detailModalFooter');
        if (footer) {
            const role = currentUser ? currentUser.role : '';
            const isCoordinator = ['STAFF', 'ADMIN', 'BRANCH_MANAGER'].includes(role);
            const isTeacher = role === 'TEACHER';
            const myId = currentUser ? (currentUser.userId ?? currentUser.id) : null;
            const isTargetTeacher = isTeacher && myId != null && Number(item.targetTeacherId) === Number(myId);

            let footerActions = '';
            if (isCoordinator && item.status === 'PENDING') {
                footerActions += `
                    <button type="button" class="btn btn-sm btn-primary rounded-pill px-3" onclick="modalDetailObj.hide(); openScheduleModal(${item.id})">
                        <i class="bi bi-calendar-plus me-1"></i>Xếp lớp
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-warning text-dark rounded-pill px-3" onclick="modalDetailObj.hide(); markNoSlot(${item.id})">
                        <i class="bi bi-exclamation-triangle me-1"></i>Chưa có lớp
                    </button>
                `;
            }
            if ((isCoordinator || isTargetTeacher) && (item.status === 'SCHEDULED' || item.status === 'MISSED') && !isFutureMakeup(item)) {
                footerActions += `
                    <button type="button" class="btn btn-sm btn-success rounded-pill px-3" onclick="modalDetailObj.hide(); confirmCompleteMakeup(${item.id})">
                        <i class="bi bi-check2-all me-1"></i>Có mặt
                    </button>
                `;
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
