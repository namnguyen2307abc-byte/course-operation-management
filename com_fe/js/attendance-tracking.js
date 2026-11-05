/**
 * attendance-tracking.js
 * Quản lý giao diện Theo Dõi Điểm Danh & Tiến Độ Khóa Học của Học Viên (Có Phân Trang)
 */

let currentUser = null;
let currentStudents = [];
let currentEnrolledClasses = [];
let selectedStudentId = null;
let selectedClassId = null;

// Biến lưu trữ dữ liệu & trạng thái phân trang
let currentTrackingData = null;
let currentPage = 1;
let pageSize = 10;

// =============================================================================
// KHỞI TẠO TRANG
// =============================================================================
document.addEventListener('DOMContentLoaded', async () => {
    currentUser = typeof getCurrentUser === 'function' ? getCurrentUser() : null;
    if (!currentUser) {
        if (typeof checkAuth === 'function') checkAuth();
        return;
    }

    if (typeof updateNavbarUser === 'function') {
        updateNavbarUser();
    }

    initUserRoleBadge();
    await loadStudents();
});

function initUserRoleBadge() {
    const badge = document.getElementById('currentUserRoleBadge');
    if (!badge || !currentUser) return;

    const roleName = typeof roleLabel === 'function' ? roleLabel(currentUser.role) : currentUser.role;
    badge.textContent = `${roleName}: ${currentUser.fullName || currentUser.username}`;

    if (currentUser.role === 'ADMIN') {
        badge.className = 'badge bg-danger-subtle text-danger border border-danger-subtle px-3 py-2 rounded-pill fw-bold';
    } else if (currentUser.role === 'PARENT') {
        badge.className = 'badge bg-success-subtle text-success border border-success-subtle px-3 py-2 rounded-pill fw-bold';
    } else if (currentUser.role === 'STUDENT') {
        badge.className = 'badge bg-primary-subtle text-primary border border-primary-subtle px-3 py-2 rounded-pill fw-bold';
    } else {
        badge.className = 'badge bg-info-subtle text-info-emphasis border border-info-subtle px-3 py-2 rounded-pill fw-bold';
    }
}

// =============================================================================
// TẢI DANH SÁCH HỌC VIÊN
// =============================================================================
async function loadStudents() {
    const studentSelect = document.getElementById('studentSelect');

    try {
        const res = await callApi('/api/attendance-makeup/students', 'GET');
        currentStudents = res || [];

        if (currentStudents.length === 0) {
            studentSelect.innerHTML = '<option value="">Không tìm thấy học viên nào</option>';
            showEmptyTrackingState('Không có dữ liệu học viên được phân công hoặc liên kết với tài khoản này.');
            return;
        }

        if (currentUser.role === 'STUDENT') {
            studentSelect.innerHTML = currentStudents.map(s => 
                `<option value="${s.id}" selected>${escapeHtml(s.fullName || s.name)} (${s.studentCode || 'HV'})</option>`
            ).join('');
            selectedStudentId = currentStudents[0].id;
            studentSelect.disabled = true;
            await loadEnrolledClasses(selectedStudentId);
            return;
        }

        let optionsHtml = '';
        if (currentUser.role === 'PARENT') {
            optionsHtml = currentStudents.map(s => 
                `<option value="${s.id}">${escapeHtml(s.fullName || s.name)} (${s.studentCode || 'Con'})</option>`
            ).join('');
        } else {
            optionsHtml = '<option value="">-- Chọn học viên cần tra cứu --</option>' +
                currentStudents.map(s => 
                    `<option value="${s.id}">${escapeHtml(s.fullName || s.name)} (${s.studentCode || 'HV'})</option>`
                ).join('');
        }

        studentSelect.innerHTML = optionsHtml;
        studentSelect.disabled = false;

        if (currentStudents.length > 0) {
            studentSelect.value = currentStudents[0].id;
            selectedStudentId = currentStudents[0].id;
            await loadEnrolledClasses(selectedStudentId);
        }

    } catch (err) {
        console.error('Lỗi tải danh sách học viên:', err);
        showToast('danger', 'Không thể nạp danh sách học viên: ' + (err.message || 'Lỗi kết nối'));
        studentSelect.innerHTML = '<option value="">Lỗi tải dữ liệu</option>';
    }
}

// =============================================================================
// TẢI DANH SÁCH LỚP / KHÓA HỌC MÀ HỌC VIÊN ĐÃ GHI DANH
// =============================================================================
async function loadEnrolledClasses(studentId) {
    const classSelect = document.getElementById('classSelect');
    classSelect.innerHTML = '<option value="">Đang nạp các khóa học của học viên...</option>';
    classSelect.disabled = true;

    try {
        const res = await callApi(`/api/schedule/students/${studentId}/enrolled-classes`, 'GET');
        currentEnrolledClasses = res || [];

        if (currentEnrolledClasses.length === 0) {
            classSelect.innerHTML = '<option value="">Học viên chưa ghi danh lớp/khóa học nào</option>';
            classSelect.disabled = true;
            showEmptyTrackingState('Học viên chưa đăng ký hoặc chưa được xếp vào khóa học nào.');
            return;
        }

        classSelect.innerHTML = currentEnrolledClasses.map(c => {
            const courseTitle = c.courseName ? `${c.courseName}` : 'Khóa học';
            const sessions = c.totalSessions ? `${c.totalSessions} buổi` : 'Chưa rõ số buổi';
            const classTitle = c.classCode || c.className || 'Lớp';
            return `<option value="${c.classId}">${classTitle} &bull; ${courseTitle} (${sessions})</option>`;
        }).join('');

        classSelect.disabled = false;
        selectedClassId = currentEnrolledClasses[0].classId;
        classSelect.value = selectedClassId;

        await loadAttendanceTracking();

    } catch (err) {
        console.error('Lỗi nạp khóa học của học viên:', err);
        showToast('danger', 'Lỗi nạp khóa học: ' + (err.message || 'Lỗi kết nối'));
        classSelect.innerHTML = '<option value="">Lỗi tải khóa học</option>';
        classSelect.disabled = true;
    }
}

// =============================================================================
// SỰ KIỆN THAY ĐỔI LỰA CHỌN
// =============================================================================
async function onStudentChange() {
    const studentSelect = document.getElementById('studentSelect');
    selectedStudentId = studentSelect.value ? Number(studentSelect.value) : null;
    if (selectedStudentId) {
        await loadEnrolledClasses(selectedStudentId);
    } else {
        document.getElementById('classSelect').innerHTML = '<option value="">-- Vui lòng chọn học viên trước --</option>';
        document.getElementById('classSelect').disabled = true;
        showEmptyTrackingState('Vui lòng chọn học viên để tra cứu tiến độ điểm danh.');
    }
}

async function onClassChange() {
    const classSelect = document.getElementById('classSelect');
    selectedClassId = classSelect.value ? Number(classSelect.value) : null;
    if (selectedClassId) {
        await loadAttendanceTracking();
    }
}

// =============================================================================
// NẠP DỮ LIỆU ĐIỂM DANH & RENDER BẢNG (ATTENDANCE TRACKING)
// =============================================================================
async function loadAttendanceTracking() {
    const studentSelect = document.getElementById('studentSelect');
    const classSelect = document.getElementById('classSelect');

    const sId = selectedStudentId || (studentSelect.value ? Number(studentSelect.value) : null);
    const cId = selectedClassId || (classSelect.value ? Number(classSelect.value) : null);

    if (!sId) {
        showToast('warning', 'Vui lòng chọn học viên!');
        return;
    }

    const tbody = document.getElementById('attendanceTableBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="8" class="text-center py-5 text-muted">
                <div class="spinner-border text-primary spinner-border-sm me-2" role="status"></div>
                Đang nạp thông tin điểm danh khóa học...
            </td>
        </tr>
    `;

    try {
        let url = `/api/schedule/attendance-tracking?studentId=${sId}`;
        if (cId) {
            url += `&classId=${cId}`;
        }

        const data = await callApi(url, 'GET');
        currentTrackingData = data;
        currentPage = 1;
        renderAttendanceTrackingView(data);

    } catch (err) {
        console.error('Lỗi nạp điểm danh:', err);
        showToast('danger', 'Không thể nạp dữ liệu điểm danh: ' + (err.message || 'Lỗi kết nối'));
        document.getElementById('attendancePaginationWrapper')?.classList.add('d-none');
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="text-center py-4 text-danger">
                    <i class="bi bi-exclamation-triangle me-1"></i> Có lỗi xảy ra khi tải dữ liệu: ${escapeHtml(err.message || 'Lỗi không xác định')}
                </td>
            </tr>
        `;
    }
}

// =============================================================================
// RENDER GIAO DIỆN CHI TIẾT & PHÂN TRANG
// =============================================================================
function renderAttendanceTrackingView(data) {
    if (!data) {
        showEmptyTrackingState('Không tìm thấy dữ liệu điểm danh.');
        return;
    }

    const classHeaderLabel = document.getElementById('classHeaderLabel');
    const courseName = data.courseName || 'Khóa học';
    const className = data.classCode || data.className || 'Lớp học';
    const totalSessions = data.totalSessions || 0;
    classHeaderLabel.innerHTML = `<strong>${escapeHtml(className)}</strong> &bull; ${escapeHtml(courseName)} (${totalSessions} buổi)`;

    const kpiContainer = document.getElementById('kpiContainer');
    kpiContainer.classList.remove('d-none');

    document.getElementById('kpiTotalSessions').textContent = `${totalSessions} buổi`;
    document.getElementById('kpiCourseInfo').textContent = `${escapeHtml(className)} - ${escapeHtml(courseName)}`;
    document.getElementById('kpiAttended').textContent = `${data.attendedCount || 0} buổi`;
    document.getElementById('kpiAbsent').textContent = `${data.absentCount || 0} buổi`;
    document.getElementById('kpiUnmarked').textContent = `${data.unmarkedCount || 0} buổi`;

    const absentRate = data.absentRate != null ? data.absentRate : 0;
    const kpiAbsentRate = document.getElementById('kpiAbsentRate');
    kpiAbsentRate.textContent = `Tỷ lệ vắng: ${absentRate}%`;
    if (absentRate >= 20) {
        kpiAbsentRate.className = 'small text-danger fw-bold mt-1';
    } else {
        kpiAbsentRate.className = 'small text-muted mt-1';
    }

    const alertBanner = document.getElementById('attendanceAlertBanner');
    const alertText = document.getElementById('attendanceAlertText');
    alertBanner.classList.remove('d-none');

    if (data.summaryAlert) {
        alertText.textContent = data.summaryAlert;
    } else {
        alertText.textContent = `Vắng ${absentRate}% (${data.absentCount || 0}/${totalSessions} buổi), còn ${data.unmarkedCount || 0} buổi chưa điểm danh.`;
    }

    if (absentRate >= 20) {
        alertBanner.style.backgroundColor = '#fff1f2';
        alertBanner.style.borderColor = '#fecdd3';
        alertBanner.style.color = '#9f1239';
        alertBanner.querySelector('i').className = 'bi bi-exclamation-octagon-fill text-danger fs-5';
    } else {
        alertBanner.style.backgroundColor = '#f8fafc';
        alertBanner.style.borderColor = '#e2e8f0';
        alertBanner.style.color = '#334155';
        alertBanner.querySelector('i').className = 'bi bi-info-circle text-primary fs-5';
    }

    renderAttendanceTablePage();
}

function renderAttendanceTablePage() {
    const tbody = document.getElementById('attendanceTableBody');
    const pagWrapper = document.getElementById('attendancePaginationWrapper');
    const pagInfo = document.getElementById('attendancePageInfo');
    const pagList = document.getElementById('attendancePaginationList');

    const lessons = (currentTrackingData && currentTrackingData.lessons) ? currentTrackingData.lessons : [];
    const defaultClassName = currentTrackingData ? (currentTrackingData.classCode || currentTrackingData.className || 'Lớp học') : '';

    if (lessons.length === 0) {
        if (pagWrapper) pagWrapper.classList.add('d-none');
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="text-center py-5 text-muted">
                    <i class="bi bi-calendar-x fs-2 d-block mb-2 text-secondary"></i>
                    Lớp học này chưa có buổi học nào được sinh ra trong lịch.
                </td>
            </tr>
        `;
        return;
    }

    const totalItems = lessons.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    if (currentPage > totalPages) currentPage = totalPages;
    if (currentPage < 1) currentPage = 1;

    const startIdx = (currentPage - 1) * pageSize;
    const endIdx = Math.min(startIdx + pageSize, totalItems);
    const pageItems = lessons.slice(startIdx, endIdx);

    tbody.innerHTML = pageItems.map((item, idx) => {
        const orderNum = item.sessionNumber || (startIdx + idx + 1);
        const dayOfWeekText = item.dayOfWeekText || item.lessonDate || '-';
        const slotText = item.slot || '07:30 - 09:50';
        const roomText = item.roomName || 'Chưa xếp phòng';
        const teacherText = item.teacherName || 'Chưa phân công';
        const classText = item.className || defaultClassName;

        let statusHtml = '';
        if (item.status === 'PRESENT') {
            statusHtml = '<span class="badge-status-present">Có mặt</span>';
        } else if (item.status === 'ABSENT') {
            statusHtml = '<span class="badge-status-absent">Vắng mặt</span>';
        } else {
            statusHtml = '<span class="badge-status-unmarked">Chưa điểm danh</span>';
        }

        let noteHtml = '-';
        if (item.isMakeup) {
            noteHtml = `<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle rounded-pill px-2 py-1 small">
                <i class="bi bi-arrow-repeat me-1"></i>Học bù
            </span>`;
            if (item.makeupInfo) {
                noteHtml += ` <small class="text-muted">${escapeHtml(item.makeupInfo)}</small>`;
            }
        } else if (item.note) {
            noteHtml = `<span class="small text-secondary">${escapeHtml(item.note)}</span>`;
        }

        return `
            <tr>
                <td class="text-muted fw-semibold">${orderNum}</td>
                <td>
                    <span class="badge-date-pill">
                        <i class="bi bi-calendar3"></i>
                        <span>${escapeHtml(dayOfWeekText)}</span>
                    </span>
                </td>
                <td>
                    <span class="badge-slot-pill">
                        <i class="bi bi-clock"></i>
                        <span>${escapeHtml(slotText)}</span>
                    </span>
                </td>
                <td class="fw-semibold text-dark">${escapeHtml(roomText)}</td>
                <td class="text-secondary">${escapeHtml(teacherText)}</td>
                <td class="fw-bold text-primary">${escapeHtml(classText)}</td>
                <td>${statusHtml}</td>
                <td>${noteHtml}</td>
            </tr>
        `;
    }).join('');

    // Cập nhật thanh phân trang
    if (pagWrapper && pagInfo && pagList) {
        pagWrapper.classList.remove('d-none');
        pagInfo.innerHTML = `Hiển thị <strong>${startIdx + 1} - ${endIdx}</strong> trong tổng số <strong>${totalItems}</strong> buổi học`;

        let pagesHtml = `
            <li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
                <button class="page-link rounded-pill px-3" onclick="goToAttendancePage(${currentPage - 1})">&laquo; Trước</button>
            </li>
        `;

        for (let p = 1; p <= totalPages; p++) {
            pagesHtml += `
                <li class="page-item ${p === currentPage ? 'active' : ''}">
                    <button class="page-link rounded-circle mx-1" style="width:32px;height:32px;display:inline-flex;align-items:center;justify-content:center;" onclick="goToAttendancePage(${p})">${p}</button>
                </li>
            `;
        }

        pagesHtml += `
            <li class="page-item ${currentPage === totalPages ? 'disabled' : ''}">
                <button class="page-link rounded-pill px-3" onclick="goToAttendancePage(${currentPage + 1})">Sau &raquo;</button>
            </li>
        `;

        pagList.innerHTML = pagesHtml;
    }
}

function goToAttendancePage(page) {
    if (!currentTrackingData || !currentTrackingData.lessons) return;
    const totalPages = Math.max(1, Math.ceil(currentTrackingData.lessons.length / pageSize));
    if (page < 1 || page > totalPages) return;
    currentPage = page;
    renderAttendanceTablePage();
}

function onAttendancePageSizeChange() {
    const sel = document.getElementById('attendancePageSizeSelect');
    pageSize = sel ? ( parseInt(sel.value, 10) || 10 ) : 10;
    currentPage = 1;
    renderAttendanceTablePage();
}

// =============================================================================
// TRẠNG THÁI RỖNG & TIỆN ÍCH
// =============================================================================
function showEmptyTrackingState(message) {
    document.getElementById('kpiContainer').classList.add('d-none');
    document.getElementById('attendanceAlertBanner').classList.add('d-none');
    document.getElementById('attendancePaginationWrapper')?.classList.add('d-none');
    document.getElementById('classHeaderLabel').textContent = '--';

    const tbody = document.getElementById('attendanceTableBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="8" class="text-center py-5 text-muted">
                <i class="bi bi-inbox fs-2 d-block mb-2 text-secondary"></i>
                ${escapeHtml(message)}
            </td>
        </tr>
    `;
}

function showToast(type, message) {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toastId = 'toast_' + Date.now();
    const bgClass = type === 'success' ? 'bg-success text-white' : (type === 'warning' ? 'bg-warning text-dark' : 'bg-danger text-white');
    const icon = type === 'success' ? 'bi-check-circle-fill' : (type === 'warning' ? 'bi-exclamation-triangle-fill' : 'bi-x-circle-fill');

    const html = `
        <div id="${toastId}" class="toast align-items-center ${bgClass} border-0 shadow-lg rounded-3 mb-2" role="alert" aria-live="assertive" aria-atomic="true">
            <div class="d-flex">
                <div class="toast-body d-flex align-items-center gap-2">
                    <i class="bi ${icon}"></i>
                    <span>${escapeHtml(message)}</span>
                </div>
                <button type="button" class="btn-close ${type === 'warning' ? '' : 'btn-close-white'} me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        </div>
    `;

    container.insertAdjacentHTML('beforeend', html);
    const toastEl = document.getElementById(toastId);
    const bsToast = new bootstrap.Toast(toastEl, { delay: 3500 });
    bsToast.show();
    toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
}

function escapeHtml(text) {
    if (text === null || text === undefined) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
