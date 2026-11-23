/**
 * =============================================================================
 * MODULE TAB 1: DANH SÁCH & XỬ LÝ ĐƠN XIN NGHỈ HỌC (absence-requests.js)
 * =============================================================================
 * Phụ thuộc: makeup-common.js
 * Mục lục tra cứu nhanh:
 *   1. Sắp xếp, bộ lọc, phân trang & render bảng đơn: sortAbsenceList,
 *      populateAbsenceFilterOptions, applyAbsenceFilter, loadAbsenceRequests, renderAbsenceTable
 *   2. Giai đoạn 2 - Duyệt / Từ chối đơn: openApproveModal, executeApprove, openRejectModal, executeReject
 *   3. Hủy đơn & Modal xác nhận dùng chung: confirmCancelAbsence, openResolveModal, executeResolveNote
 *   4. Popup chi tiết đơn xin nghỉ: viewAbsenceDetail
 */

// -----------------------------------------------------------------------------
// 1. TAB 1: SẮP XẾP, LỌC, PHÂN TRANG & HIỂN THỊ BẢNG ĐƠN XIN NGHỈ
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
    const branchSel = document.getElementById('filterAbsenceBranch');
    if (stuSel) {
        const prev = stuSel.value;
        const names = Array.from(new Set((currentAbsenceList || []).map(a => a.studentName).filter(Boolean)))
            .sort((a, b) => a.localeCompare(b, 'vi'));
        stuSel.innerHTML = '<option value="">Tất cả</option>'
            + names.map(n => `<option value="${escapeHtml(n)}">${escapeHtml(n)}</option>`).join('');
        if (prev && names.includes(prev)) stuSel.value = prev;
    }
    if (clsSel) {
        const prev = clsSel.value;
        const classes = Array.from(new Set((currentAbsenceList || []).map(a => a.className).filter(Boolean)))
            .sort((a, b) => a.localeCompare(b, 'vi'));
        clsSel.innerHTML = '<option value="">Tất cả</option>'
            + classes.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
        if (prev && classes.includes(prev)) clsSel.value = prev;
    }
    if (branchSel) {
        const prev = branchSel.value;
        const branches = Array.from(new Set((currentAbsenceList || []).map(a => a.branchName).filter(Boolean)))
            .sort((a, b) => a.localeCompare(b, 'vi'));
        branchSel.innerHTML = '<option value="">Tất cả</option>'
            + branches.map(b => `<option value="${escapeHtml(b)}">${escapeHtml(b)}</option>`).join('');
        if (prev && branches.includes(prev)) branchSel.value = prev;
    }
}

function applyAbsenceFilter() {
    renderAbsenceTable(true);
}

function resetAbsenceFilter() {
    ['filterAbsenceStatus', 'filterAbsenceLeaveMode', 'filterAbsenceStudent', 'filterAbsenceClass',
     'filterAbsenceBranch', 'filterAbsenceFromDate', 'filterAbsenceToDate', 'searchAbsenceInput'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    renderAbsenceTable(true);
}

async function loadAbsenceRequests() {
    try {
        const data = await callApi('/api/attendance-makeup/absence-requests', 'GET', null, false, false);
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

function renderAbsenceTable(resetPage = false) {
    if (resetPage) absencePage = 1;
    const tbody = document.getElementById('absenceTableBody');
    if (!tbody) return;

    const paginationWrapper = document.getElementById('absencePaginationWrapper');
    const paginationInfo = document.getElementById('absencePaginationInfo');
    const paginationList = document.getElementById('absencePaginationList');

    const statusFilter = document.getElementById('filterAbsenceStatus')?.value || '';
    const leaveModeFilter = document.getElementById('filterAbsenceLeaveMode')?.value || '';
    const studentFilter = document.getElementById('filterAbsenceStudent')?.value || '';
    const classFilter = document.getElementById('filterAbsenceClass')?.value || '';
    const branchFilter = document.getElementById('filterAbsenceBranch')?.value || '';
    const fromDate = document.getElementById('filterAbsenceFromDate')?.value || '';
    const toDate = document.getElementById('filterAbsenceToDate')?.value || '';
    const searchKeyword = (document.getElementById('searchAbsenceInput')?.value || '').trim().toLowerCase();

    let filtered = sortAbsenceList(currentAbsenceList);
    if (statusFilter) {
        filtered = filtered.filter(a => a.status === statusFilter);
    }
    if (leaveModeFilter === 'MAKEUP') {
        filtered = filtered.filter(a => a.needMakeup !== false);
    } else if (leaveModeFilter === 'LEAVE_ONLY') {
        filtered = filtered.filter(a => a.needMakeup === false);
    }
    if (studentFilter) {
        filtered = filtered.filter(a => a.studentName === studentFilter);
    }
    if (classFilter) {
        filtered = filtered.filter(a => a.className === classFilter);
    }
    if (branchFilter) {
        filtered = filtered.filter(a => a.branchName === branchFilter);
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
            return item.teacherId == null || (myId != null && Number(myId) === Number(item.teacherId));
        }
        return ['ADMIN', 'BRANCH_MANAGER', 'STAFF'].includes(currentUser.role);
    };

    const isCoordinator = currentUser && ['STAFF', 'ADMIN', 'BRANCH_MANAGER'].includes(currentUser.role);
    const isCreator = currentUser && currentUser.role === 'PARENT';

    tbody.innerHTML = pagedItems.map(item => {
        const statusBadge = getAbsenceStatusBadgeHtml(item.status);
        const cleanMakeupNote = formatCleanNote(item.makeupNote);
        const needMakeup = item.needMakeup !== false;

        let makeupBadge = '<span class="text-muted small">—</span>';
        if (!needMakeup) {
            makeupBadge = '<span class="badge-status-modern badge-leave-only"><i class="bi bi-calendar-x"></i>Không học bù</span>';
        } else if (item.makeupStatus === 'PENDING') {
            makeupBadge = `<span class="badge-status-modern status-pending" title="${escapeHtml(cleanMakeupNote || 'Chờ xếp lớp')}"><i class="bi bi-clock-history"></i>Chờ xếp lớp</span>`;
        } else if (item.makeupStatus === 'SCHEDULED') {
            makeupBadge = `<span class="badge-status-modern status-scheduled" title="${escapeHtml(cleanMakeupNote || ('Đã xếp: ' + (item.makeupTargetDate || '')))}"><i class="bi bi-calendar-check"></i>${item.makeupTargetDate || 'Đã xếp'}</span>`;
        } else if (item.makeupStatus === 'COMPLETED') {
            makeupBadge = `<span class="badge-status-modern status-completed" title="${escapeHtml(cleanMakeupNote || 'Hoàn thành')}"><i class="bi bi-check2-all"></i>Đã học bù</span>`;
        } else if (item.makeupStatus === 'NO_SLOT') {
            makeupBadge = `<span class="badge-status-modern status-noslot" title="${escapeHtml(cleanMakeupNote || 'Chưa có lớp phù hợp')}"><i class="bi bi-exclamation-circle"></i>Chưa có lớp</span>`;
        } else if (item.makeupStatus === 'MISSED') {
            const retryStr = item.makeupRetryCount ? ` (${item.makeupRetryCount}/${item.makeupMaxRetries || 1})` : '';
            makeupBadge = `<span class="badge-status-modern status-missed" title="${escapeHtml(cleanMakeupNote || 'Vắng mặt ca bù')}"><i class="bi bi-person-x-fill"></i>Vắng bù${retryStr}</span>`;
        } else if (item.makeupStatus === 'CANCELLED') {
            const isLocked = Boolean(item.makeupNote && item.makeupNote.includes('- KHÓA]'));
            const isNoSlot = Boolean(item.makeupNote && item.makeupNote.includes('Chưa có lớp phù hợp'));
            if (isLocked) {
                makeupBadge = `<span class="badge-status-modern status-missed" title="${escapeHtml(cleanMakeupNote || 'Hết quyền bù')}"><i class="bi bi-lock-fill"></i>Khóa bù</span>`;
            } else if (isNoSlot) {
                makeupBadge = `<span class="badge-status-modern status-noslot" title="${escapeHtml(cleanMakeupNote || 'Chưa có lớp phù hợp')}"><i class="bi bi-exclamation-circle"></i>Chưa có lớp</span>`;
            } else {
                makeupBadge = `<span class="badge-status-modern status-cancelled" title="${escapeHtml(cleanMakeupNote || 'Đã hủy')}"><i class="bi bi-x-circle"></i>Đã hủy</span>`;
            }
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

        const parentInfo = !isCreator && item.parentName
            ? `<div class="tbl-cell-sub mt-1"><span>PH: ${escapeHtml(item.parentName)}${item.parentPhone ? ` &bull; ${escapeHtml(item.parentPhone)}` : ''}</span></div>`
            : '';

        const courseText = formatCourseAndLevel(item.courseName, item.level);
        return `
            <tr>
                <td class="text-center"><span class="id-badge">#${item.id}</span></td>
                <td>
                    <div class="tbl-cell-title">${escapeHtml(item.studentName || 'Học viên')}</div>
                    ${parentInfo}
                    <div class="mt-1">${renderMakeupCounterBadge(item.courseMakeupCount ?? 0, item.courseMakeupLimit ?? 3)}</div>
                </td>
                <td>
                    <div class="tbl-cell-title" title="${escapeHtml(item.lessonTitle || (`Buổi ${item.sessionNumber || ''}`))}">
                        ${escapeHtml(item.lessonTitle || (`Buổi ${item.sessionNumber || ''}`))}
                    </div>
                    <div class="tbl-cell-sub mt-1 d-flex align-items-center flex-wrap gap-1">
                        <span class="tbl-date-pill">${lessonDateStr}</span>
                        <span>${timeSlotStr}</span>
                        ${item.className ? `<span>&bull; ${escapeHtml(item.className)}</span>` : ''}
                    </div>
                    <div class="tbl-cell-sub mt-1">
                        ${item.roomName ? `<span>${escapeHtml(item.roomName)}</span>` : ''}
                        ${item.teacherName ? `<span>&bull; GV: ${escapeHtml(item.teacherName)}</span>` : ''}
                        ${courseText ? `<span>&bull; ${escapeHtml(courseText)}</span>` : ''}
                        ${item.branchName ? `<span>&bull; ${escapeHtml(item.branchName)}</span>` : ''}
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
// 2. GIAI ĐOẠN 2: XÉT DUYỆT / TỪ CHỐI ĐƠN XIN NGHỈ
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
        if (classRow) classRow.classList.remove('d-none');
    } else if (classRow) {
        classRow.classList.add('d-none');
    }

    const mkCount = item ? (item.courseMakeupCount ?? 0) : 0;
    const mkLimit = item ? (item.courseMakeupLimit ?? 3) : 3;
    const needMakeup = !item || item.needMakeup !== false;
    const isLimit = item ? (item.courseMakeupLimitReached || mkCount >= mkLimit) : false;
    const leaveModeBadge = document.getElementById('approveLeaveModeBadge');
    if (leaveModeBadge) {
        leaveModeBadge.className = needMakeup
            ? 'badge bg-success-subtle text-success border border-success-subtle rounded-pill px-2 py-1 small'
            : 'badge badge-leave-only rounded-pill px-2 py-1 small';
        leaveModeBadge.textContent = needMakeup ? 'Nghỉ & học bù (tạo ca bù)' : 'Chỉ xin nghỉ (không bù)';
    }
    if (countBadgeEl) {
        countBadgeEl.className = `badge ${isLimit ? 'bg-danger-subtle text-danger border-danger-subtle' : (mkCount > 0 ? 'bg-warning-subtle text-warning-emphasis border-warning-subtle' : 'bg-success-subtle text-success border-success-subtle')} border rounded-pill px-2 py-1 mt-1`;
        countBadgeEl.innerHTML = `<i class="bi ${isLimit ? 'bi-exclamation-octagon-fill' : (mkCount > 0 ? 'bi-clock-history' : 'bi-check-circle')} me-1"></i>Lượt bù khóa: ${mkCount}/${mkLimit}`;
    }
    const limitTextEl = document.getElementById('approveMakeupLimitText');
    if (limitTextEl) {
        limitTextEl.textContent = `${mkLimit}/${mkLimit}`;
    }
    if (warningEl) {
        warningEl.classList.toggle('d-none', !needMakeup || !isLimit);
    }

    if (noteEl) noteEl.value = '';
    if (modalApproveObj) modalApproveObj.show();
}

async function executeApprove() {
    if (!ensureSessionMatchesTab()) return;
    if (!activeReviewId) return;
    const note = document.getElementById('approveNoteInput')?.value || '';
    try {
        await callApi(`/api/attendance-makeup/absence-requests/${activeReviewId}/review`, 'PATCH', {
            decision: 'APPROVED',
            reviewNote: note
        }, false, false);
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
        if (classRow) classRow.classList.remove('d-none');
    } else if (classRow) {
        classRow.classList.add('d-none');
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
    if (!ensureSessionMatchesTab()) return;
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
        }, false, false);
        if (modalRejectObj) modalRejectObj.hide();
        showToast('warning', 'Đã từ chối đơn xin nghỉ.');
        await reloadData();
        if (window.refreshNotifications) window.refreshNotifications();
    } catch (err) {
        showToast('danger', 'Lỗi từ chối: ' + err.message);
    }
}

// -----------------------------------------------------------------------------
// 3. HỦY ĐƠN XIN NGHỈ & MODAL XÁC NHẬN DÙNG CHUNG
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
        onConfirm: async () => callApi(`/api/attendance-makeup/absence-requests/${absenceId}`, 'DELETE', null, false, false)
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
    if (!ensureSessionMatchesTab()) return;
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

// -----------------------------------------------------------------------------
// 4. POPUP CHI TIẾT ĐƠN XIN NGHỈ
// -----------------------------------------------------------------------------

async function viewAbsenceDetail(id) {
    try {
        const item = await callApi(`/api/attendance-makeup/absence-requests/${id}`, 'GET', null, false, false);
        if (!item) return;

        const cleanReviewNote = formatCleanNote(item.reviewNote);
        const cleanMakeupNote = formatCleanNote(item.makeupNote);

        const titleEl = document.getElementById('modalDetailTitle');
        if (titleEl) titleEl.innerHTML = `<i class="bi bi-file-earmark-text-fill me-2 text-warning"></i>Đơn Xin Nghỉ #${item.id}`;

        const body = document.getElementById('detailModalBody');
        if (body) {
            const makeupStatusHtml = typeof getMakeupStatusBadgeHtml === 'function'
                ? getMakeupStatusBadgeHtml(item.makeupStatus, item.makeupNote)
                : '';

            const isApproved = item.status === 'APPROVED';
            const isRejected = item.status === 'REJECTED';
            const needMakeup = item.needMakeup !== false;
            const courseLabel = formatCourseAndLevel(item.courseName, item.level);
            const isDangerNote = item.makeupStatus === 'MISSED' || item.makeupStatus === 'CANCELLED';

            body.innerHTML = `
                <div class="detail-info-card mb-3">
                    <div class="d-flex justify-content-between align-items-start gap-2">
                        <div class="d-flex align-items-center gap-2">
                            <div class="rounded-circle text-white d-flex align-items-center justify-content-center fw-bold shadow-sm" style="width: 40px; height: 40px; font-size: 0.95rem; background: #143c34; flex-shrink: 0;">
                                ${escapeHtml(getInitials(item.studentName || 'HV'))}
                            </div>
                            <div>
                                <div class="fw-bold text-dark fs-6 lh-sm">${escapeHtml(item.studentName || 'Học viên')}</div>
                                <div class="small text-secondary mt-1">
                                    PH: ${escapeHtml(item.parentName || 'N/A')}${item.parentPhone ? ` &bull; ${escapeHtml(item.parentPhone)}` : ''}
                                </div>
                                <div class="mt-2 d-flex flex-wrap gap-1 align-items-center">
                                    ${renderMakeupCounterBadge(item.courseMakeupCount ?? 0, item.courseMakeupLimit ?? 3)}
                                    ${needMakeup
                                        ? '<span class="badge bg-info-subtle text-info-emphasis border border-info-subtle rounded-pill px-2 py-1" style="font-size:0.72rem;">Nghỉ &amp; học bù</span>'
                                        : '<span class="badge badge-leave-only rounded-pill px-2 py-1" style="font-size:0.72rem;">Chỉ xin nghỉ</span>'}
                                </div>
                            </div>
                        </div>
                        ${getAbsenceStatusBadgeHtml(item.status)}
                    </div>

                    <div class="detail-meta-row mt-3 pt-2 border-top">
                        <span><i class="bi bi-send text-primary me-2"></i>Gửi đơn:</span>
                        <strong>${formatDateTime(item.createdAt)}${item.requestedByName ? ` (${escapeHtml(item.requestedByName)})` : ''}</strong>
                    </div>

                    ${item.reviewedAt ? `
                    <div class="detail-meta-row">
                        <span><i class="bi bi-check2-circle text-success me-2"></i>Phản hồi:</span>
                        <strong class="text-success">${formatDateTime(item.reviewedAt)}</strong>
                    </div>
                    ` : ''}
                </div>

                <div class="mb-3">
                    <div class="detail-section-label">
                        <i class="bi bi-calendar-x text-danger"></i> Buổi nghỉ
                    </div>
                    <div class="detail-info-card bg-subtle">
                        <div class="fw-bold text-dark fs-6">${escapeHtml(cleanTitle(item.lessonTitle || `Buổi ${item.sessionNumber || ''}`))}</div>
                        <div class="d-flex flex-wrap gap-2 mt-2 align-items-center">
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
                        ${(courseLabel || item.branchName) ? `
                        <div class="small text-secondary mt-2">
                            ${courseLabel ? `Khóa: <strong>${escapeHtml(courseLabel)}</strong>` : ''}
                            ${item.branchName ? `${courseLabel ? ' &bull; ' : ''}${escapeHtml(item.branchName)}` : ''}
                        </div>
                        ` : ''}
                        <div class="small text-muted mt-2 pt-2 border-top d-flex flex-wrap gap-3">
                            <span><i class="bi bi-geo-alt text-danger me-1"></i>${escapeHtml(cleanTitle(item.roomName || 'Phòng học'))}</span>
                            <span><i class="bi bi-person-video3 text-success me-1"></i>GV: ${escapeHtml(item.teacherName || 'N/A')}</span>
                        </div>
                    </div>
                </div>

                <div class="mb-3">
                    <div class="detail-section-label">
                        <i class="bi bi-chat-left-text text-secondary"></i> Lý do
                    </div>
                    <div class="detail-reason-box">
                        <i class="bi bi-quote fs-5 text-secondary opacity-50 align-middle me-1"></i>${escapeHtml(item.reason || 'Không có ghi chú lý do.')}
                    </div>
                </div>

                ${cleanReviewNote || item.reviewedAt ? `
                    <div class="mb-3">
                        <div class="detail-section-label">
                            <i class="bi bi-chat-square-quote ${isRejected ? 'text-danger' : 'text-success'}"></i> Phản hồi
                        </div>
                        <div class="detail-response-box ${isRejected ? 'rejected' : ''}">
                            <div class="fw-semibold mb-1">${escapeHtml(cleanReviewNote || (isApproved ? 'Đã duyệt yêu cầu.' : 'Đã phản hồi.'))}</div>
                            <div class="small text-muted pt-2 mt-2 border-top border-opacity-25 border-secondary">
                                Người duyệt: <strong>${escapeHtml(item.approvedByTeacherName || 'Giáo viên phụ trách')}</strong>
                            </div>
                        </div>
                    </div>
                ` : ''}

                ${!needMakeup ? `
                    <div class="p-3 bg-light rounded-3 border small text-secondary">
                        <i class="bi bi-calendar-x me-1"></i>Đơn chỉ xin nghỉ, không đăng ký học bù.
                    </div>
                ` : (item.makeupRegistrationId ? `
                    <div class="detail-info-card card-accent-emerald">
                        <div class="d-flex justify-content-between align-items-center mb-2">
                            <span class="detail-section-label mb-0 text-success">
                                <i class="bi bi-calendar2-check-fill me-1"></i>Ca học bù #${item.makeupRegistrationId}
                            </span>
                            ${makeupStatusHtml}
                        </div>
                        ${item.makeupTargetDate ? `<div class="small text-dark mt-1">Thời gian: <strong>${item.makeupTargetDate}${item.makeupTargetTimeSlot ? ` (${item.makeupTargetTimeSlot})` : ''}</strong></div>` : ''}
                        ${item.makeupTargetClassName ? `<div class="small text-secondary mt-1">Lớp bù: <strong>${escapeHtml(cleanTitle(item.makeupTargetClassName))}</strong>${item.makeupTargetRoomName ? ` &bull; ${escapeHtml(item.makeupTargetRoomName)}` : ''}${item.makeupTargetTeacherName ? ` &bull; GV: ${escapeHtml(item.makeupTargetTeacherName)}` : ''}</div>` : ''}
                        ${cleanMakeupNote ? `<div class="small ${isDangerNote ? 'text-danger' : 'text-secondary'} mt-2 p-2 bg-white rounded-3 border"><i class="bi bi-info-circle-fill me-1"></i>${escapeHtml(cleanMakeupNote)}</div>` : ''}
                    </div>
                ` : '')}
            `;
        }

        const footer = document.getElementById('detailModalFooter');
        if (footer) {
            const role = currentUser ? currentUser.role : '';
            const isCoordinator = ['STAFF', 'ADMIN', 'BRANCH_MANAGER'].includes(role);
            const isCreator = role === 'PARENT';
            const myId = currentUser ? (currentUser.userId ?? currentUser.id) : null;
            const canReview = isCoordinator || (role === 'TEACHER' && (item.teacherId == null || (myId != null && Number(myId) === Number(item.teacherId))));

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
