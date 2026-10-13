/**
 * weekly-schedule.js
 * Quản lý Thời Khóa Biểu Tuần & Điểm Danh Học Viên Buổi Học
 * Hỗ trợ phân quyền: Học Sinh, Phụ Huynh, Giáo Viên, Admin & Giáo Vụ
 * Ràng buộc Time-Lock: Giáo viên chỉ được điểm danh trong ngày diễn ra ca học.
 */

// =============================================================================
// TRẠNG THÁI TOÀN CỤC (GLOBAL STATE)
// =============================================================================
let currentTargetDate = new Date(); // Ngày đang xem
let currentScheduleData = null;     // Dữ liệu ma trận tuần từ API
let activeLessonAttendance = null;  // Dữ liệu buổi học đang mở trong Modal điểm danh
let isAttendanceEditMode = false;   // Cờ trạng thái đang chỉnh sửa hay chỉ xem
let currentUser = null;

// Bootstrap Modals
let modalAttendanceObj = null;
let modalCreateClassObj = null;

// Danh sách bộ lọc
let filterTeacherId = '';
let filterStudentId = '';

// =============================================================================
// KHỞI TẠO KHI TẢI TRANG
// =============================================================================
document.addEventListener('DOMContentLoaded', async () => {
    // 1. Kiểm tra xác thực & cập nhật Navbar
    currentUser = getCurrentUser();
    initNavbarAndRoleBadge();

    // 2. Khởi tạo Bootstrap Modals
    const attModalEl = document.getElementById('modalAttendance');
    if (attModalEl) modalAttendanceObj = new bootstrap.Modal(attModalEl);

    const createModalEl = document.getElementById('modalCreateClass');
    if (createModalEl) modalCreateClassObj = new bootstrap.Modal(createModalEl);

    // 3. Khởi tạo Datepicker với ngày hiện tại (YYYY-MM-DD)
    const weekPicker = document.getElementById('weekPickerDate');
    if (weekPicker) {
        weekPicker.value = formatDateForInput(currentTargetDate);
    }

    // 4. Nạp bộ lọc theo quyền người dùng
    await loadFilterOptions();

    // 5. Tải thời khóa biểu tuần
    await loadWeeklySchedule();
});

function initNavbarAndRoleBadge() {
    // Nạp navbar từ components/navbar.html
    fetch('../components/navbar.html')
        .then(r => r.text())
        .then(html => {
            const container = document.getElementById('navbar-container');
            if (container) {
                container.innerHTML = html;
                updateNavbarUser();
            }
        })
        .catch(e => console.error("Lỗi nạp navbar:", e));

    // Nạp footer
    fetch('../components/footer.html')
        .then(r => r.text())
        .then(html => {
            const container = document.getElementById('footer-container');
            if (container) container.innerHTML = html;
        })
        .catch(e => console.error("Lỗi nạp footer:", e));

    // Cập nhật huy hiệu vai trò
    const roleBadge = document.getElementById('currentUserRoleBadge');
    const btnCreateClass = document.getElementById('btnOpenCreateClassModal');

    if (!currentUser) {
        if (roleBadge) {
            roleBadge.innerText = 'Chưa đăng nhập';
            roleBadge.className = 'badge bg-secondary px-3 py-2 rounded-pill small';
        }
        return;
    }

    const role = currentUser.role || 'TEACHER';
    if (roleBadge) {
        if (role === 'TEACHER') {
            roleBadge.innerHTML = '<i class="bi bi-mortarboard-fill me-1"></i>Giáo Viên: ' + (currentUser.fullName || currentUser.username);
            roleBadge.className = 'badge bg-warning text-dark px-3 py-2 rounded-pill small';
        } else if (role === 'PARENT') {
            roleBadge.innerHTML = '<i class="bi bi-people-fill me-1"></i>Phụ Huynh: ' + (currentUser.fullName || currentUser.username);
            roleBadge.className = 'badge bg-success px-3 py-2 rounded-pill small';
        } else if (role === 'STUDENT') {
            roleBadge.innerHTML = '<i class="bi bi-person-fill me-1"></i>Học Viên: ' + (currentUser.fullName || currentUser.username);
            roleBadge.className = 'badge bg-info text-dark px-3 py-2 rounded-pill small';
        } else {
            roleBadge.innerHTML = '<i class="bi bi-shield-check me-1"></i>Quản Trị Viên / Giáo Vụ';
            roleBadge.className = 'badge bg-danger px-3 py-2 rounded-pill small';
        }
    }

    // Hiển thị nút Tạo Lớp đối với Quản trị viên / Giáo vụ
    if (btnCreateClass && (role === 'ADMIN' || role === 'STAFF' || role === 'BRANCH_MANAGER')) {
        btnCreateClass.classList.remove('d-none');
    }
}

// =============================================================================
// ĐIỀU HƯỚNG TUẦN (WEEK NAVIGATION)
// =============================================================================
function navigateWeek(offsetWeeks) {
    currentTargetDate.setDate(currentTargetDate.getDate() + (offsetWeeks * 7));
    syncDatepicker();
    loadWeeklySchedule();
}

function navigateCurrentWeek() {
    currentTargetDate = new Date();
    syncDatepicker();
    loadWeeklySchedule();
}

function onDateSelected(dateStr) {
    if (!dateStr) return;
    currentTargetDate = new Date(dateStr);
    loadWeeklySchedule();
}

function syncDatepicker() {
    const weekPicker = document.getElementById('weekPickerDate');
    if (weekPicker) {
        weekPicker.value = formatDateForInput(currentTargetDate);
    }
}

// =============================================================================
// GỌI API & RENDER MA TRẬN THỜI KHÓA BIỂU TUẦN
// =============================================================================
async function loadWeeklySchedule() {
    const tbody = document.getElementById('timetableBody');
    if (tbody) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="text-center py-5 text-muted">
                    <div class="spinner-border text-primary spinner-border-sm me-2" role="status"></div>
                    Đang nạp dữ liệu lịch học...
                </td>
            </tr>
        `;
    }

    const dateStr = formatDateForInput(currentTargetDate);
    let url = `/api/schedule/weekly?date=${dateStr}`;
    if (filterTeacherId) url += `&teacherId=${filterTeacherId}`;
    if (filterStudentId) url += `&studentId=${filterStudentId}`;

    try {
        const data = await callApi(url);
        currentScheduleData = data;
        renderWeeklyMatrix(data);
    } catch (err) {
        console.error("Lỗi khi tải thời khóa biểu tuần:", err);
        if (tbody) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="8" class="text-center py-5 text-danger">
                        <i class="bi bi-exclamation-triangle-fill fs-3 d-block mb-2"></i>
                        Không thể tải thời khóa biểu: ${err.message || 'Lỗi kết nối máy chủ'}
                    </td>
                </tr>
            `;
        }
    }
}

function renderWeeklyMatrix(data) {
    if (!data) return;

    // 1. Cập nhật tiêu đề tuần & khoảng ngày
    const weekTitleEl = document.getElementById('weekDisplayTitle');
    const weekSubtitleEl = document.getElementById('weekDateRangeSubtitle');

    const startFmt = formatDateDM(data.startDate);
    const endFmt = formatDateDMY(data.endDate);

    if (weekTitleEl) {
        weekTitleEl.innerText = `Tuần ${data.weekNumber} (${data.year})`;
    }
    if (weekSubtitleEl) {
        weekSubtitleEl.innerText = `Từ ngày ${startFmt} đến ngày ${endFmt}`;
    }

    // 2. Tính toán ngày từng cột (Thứ 2 đến Chủ nhật) và highlight ngày Hôm Nay
    const mondayDate = parseLocalDate(data.startDate);
    const todayStr = data.currentDate; // YYYY-MM-DD

    for (let dayIndex = 1; dayIndex <= 7; dayIndex++) {
        const colDate = new Date(mondayDate);
        colDate.setDate(colDate.getDate() + (dayIndex - 1));
        const colDateStr = formatDateForInput(colDate);

        const subEl = document.getElementById(`sub-day-${dayIndex}`);
        const thEl = document.getElementById(`th-day-${dayIndex}`);

        if (subEl) {
            subEl.innerText = formatDateDM(colDateStr);
        }

        if (thEl) {
            if (colDateStr === todayStr) {
                thEl.classList.add('today-header');
            } else {
                thEl.classList.remove('today-header');
            }
        }
    }

    // 3. Gom nhóm danh sách Lessons theo (slotNumber, dayOfWeekValue)
    // dayOfWeekValue: 1 = MONDAY, ..., 7 = SUNDAY
    const cellLessonsMap = {};
    if (data.lessons && data.lessons.length > 0) {
        data.lessons.forEach(lesson => {
            const key = `${lesson.slotNumber}_${lesson.dayOfWeekValue}`;
            if (!cellLessonsMap[key]) cellLessonsMap[key] = [];
            cellLessonsMap[key].push(lesson);
        });
    }

    // 4. Vẽ từng hàng Slot (Slot 1 đến Slot 4)
    const tbody = document.getElementById('timetableBody');
    if (!tbody) return;

    const slots = data.slots || [
        { slotNumber: 1, slotName: "Slot 1", timeRange: "07:00 - 09:00" },
        { slotNumber: 2, slotName: "Slot 2", timeRange: "09:30 - 11:30" },
        { slotNumber: 3, slotName: "Slot 3", timeRange: "13:00 - 15:00" },
        { slotNumber: 4, slotName: "Slot 4", timeRange: "15:30 - 17:30" }
    ];

    let html = '';
    slots.forEach(slot => {
        html += `<tr>`;
        // Cột tiêu đề Slot
        html += `
            <td class="slot-cell-header">
                <div class="fw-bold text-dark fs-6">${slot.slotName}</div>
                <div class="small text-muted fw-normal">${slot.timeRange}</div>
            </td>
        `;

        // 7 cột Thứ 2 đến Chủ nhật
        for (let dayIndex = 1; dayIndex <= 7; dayIndex++) {
            const colDate = new Date(mondayDate);
            colDate.setDate(colDate.getDate() + (dayIndex - 1));
            const colDateStr = formatDateForInput(colDate);
            const isToday = (colDateStr === todayStr);

            const key = `${slot.slotNumber}_${dayIndex}`;
            const lessonsInCell = cellLessonsMap[key] || [];

            html += `<td class="${isToday ? 'today-column' : ''}">`;

            if (lessonsInCell.length > 0) {
                lessonsInCell.forEach(l => {
                    html += renderLessonCard(l, data.currentUserRole);
                });
            }

            html += `</td>`;
        }

        html += `</tr>`;
    });

    tbody.innerHTML = html;
}

function renderLessonCard(lesson, userRole) {
    // Trạng thái thẻ ca học
    let statusBadge = '';
    let cardClass = 'lesson-card';

    if (userRole === 'STUDENT' || userRole === 'PARENT') {
        // Hiển thị trạng thái điểm danh của học viên
        if (lesson.attendanceStatus === 'PRESENT') {
            statusBadge = `<span class="badge bg-success-subtle text-success border border-success-subtle lesson-badge"><i class="bi bi-check-circle-fill"></i> Có mặt</span>`;
            cardClass += ' completed';
        } else if (lesson.attendanceStatus === 'ABSENT_EXCUSED') {
            statusBadge = `<span class="badge bg-warning-subtle text-warning border border-warning-subtle lesson-badge"><i class="bi bi-shield-check"></i> Nghỉ phép</span>`;
        } else if (lesson.attendanceStatus === 'ABSENT_UNEXCUSED') {
            statusBadge = `<span class="badge bg-danger-subtle text-danger border border-danger-subtle lesson-badge"><i class="bi bi-x-circle-fill"></i> Vắng mặt</span>`;
        } else {
            statusBadge = `<span class="badge bg-secondary-subtle text-secondary border lesson-badge"><i class="bi bi-calendar"></i> Chưa diễn ra</span>`;
        }
    } else {
        // Dành cho Giáo viên, Admin, Staff
        if (lesson.attendanceCompleted) {
            statusBadge = `<span class="badge bg-success-subtle text-success border border-success-subtle lesson-badge" title="Đã có kết quả điểm danh">
                <i class="bi bi-check2-circle"></i> Đã điểm danh (${lesson.presentCount}/${lesson.totalStudents})
            </span>`;
            cardClass += ' completed';
        } else {
            statusBadge = `<span class="badge bg-warning-subtle text-dark border border-warning-subtle lesson-badge" title="Chưa lưu điểm danh">
                <i class="bi bi-clock-history"></i> Chưa điểm danh (${lesson.totalStudents} HV)
            </span>`;
        }
    }

    if (lesson.isPast) {
        cardClass += ' past-due';
    }

    return `
        <div class="${cardClass}" onclick="openAttendanceModal(${lesson.lessonId})" title="Nhấp để xem chi tiết và điểm danh buổi học">
            <div class="d-flex justify-content-between align-items-start mb-1">
                <span class="badge bg-primary-subtle text-primary border border-primary-subtle small fw-bold" style="font-size: 0.65rem;">
                    ${lesson.classCode || 'LỚP'}
                </span>
                ${statusBadge}
            </div>

            <div class="lesson-title" title="${lesson.className}">
                ${lesson.className}
            </div>

            <div class="lesson-meta">
                <i class="bi bi-clock text-primary"></i>
                <span>${lesson.timeRange}</span>
                ${lesson.sessionNumber ? `<span class="text-secondary">• B${lesson.sessionNumber}</span>` : ''}
            </div>

            <div class="lesson-meta text-truncate">
                <i class="bi bi-geo-alt text-danger"></i>
                <span class="text-truncate">${lesson.roomName}</span>
            </div>

            <div class="lesson-meta text-truncate text-secondary">
                <i class="bi bi-person text-secondary"></i>
                <span class="text-truncate">${lesson.teacherName}</span>
            </div>
        </div>
    `;
}

// =============================================================================
// MODAL ĐIỂM DANH BUỔI HỌC (ATTENDANCE SHEET MODAL)
// =============================================================================
async function openAttendanceModal(lessonId) {
    if (!modalAttendanceObj) return;

    // Reset giao diện Modal
    document.getElementById('attModalTitle').innerText = 'Đang tải thông tin điểm danh...';
    document.getElementById('attModalSubtitle').innerText = '';
    document.getElementById('attStudentTableBody').innerHTML = `
        <tr><td colspan="6" class="text-center py-4 text-muted">
            <div class="spinner-border text-primary spinner-border-sm me-2"></div>Đang tải danh sách học viên...
        </td></tr>
    `;

    modalAttendanceObj.show();

    try {
        const data = await callApi(`/api/schedule/lessons/${lessonId}/attendance`);
        activeLessonAttendance = data;

        renderAttendanceModalData(data);
    } catch (err) {
        console.error("Lỗi khi tải bảng điểm danh:", err);
        showToast('error', 'Lỗi tải điểm danh', err.message || 'Không thể lấy thông tin buổi học');
        modalAttendanceObj.hide();
    }
}

function renderAttendanceModalData(data) {
    if (!data) return;

    // 1. Cập nhật thông tin Header
    document.getElementById('attModalTitle').innerText = `Điểm Danh: ${data.className}`;
    document.getElementById('attModalSubtitle').innerText = `Mã lớp: ${data.classCode} • Buổi số ${data.sessionNumber || 1} • Ngày ${formatDateDMY(data.lessonDate)}`;

    document.getElementById('attInfoClass').innerText = `${data.className} (${data.courseName || ''})`;
    document.getElementById('attInfoTime').innerText = `${data.timeRange} (${formatDateDMY(data.lessonDate)})`;
    document.getElementById('attInfoRoom').innerText = `${data.roomName} - ${data.branchName || ''}`;
    document.getElementById('attInfoTeacher').innerText = data.teacherName || 'Chưa phân công';

    // 2. Thống kê sĩ số
    updateAttendanceCounters();

    // 3. Banner Cảnh Báo Time-Lock / Phân Quyền
    const banner = document.getElementById('attLockBanner');
    const lockMsg = document.getElementById('attLockMessage');
    const lockIcon = document.getElementById('attLockIcon');

    if (!data.canEdit && data.lockReason) {
        banner.className = 'alert alert-danger py-2 px-3 small border mb-3 d-flex align-items-center';
        lockIcon.className = 'bi bi-lock-fill text-danger fs-5 me-2';
        lockMsg.innerHTML = `<strong>ĐÃ KHÓA CHỈNH SỬA:</strong> ${data.lockReason}`;
        banner.classList.remove('d-none');
    } else if (data.canEdit && data.isAlreadySaved) {
        banner.className = 'alert alert-info py-2 px-3 small border mb-3 d-flex align-items-center';
        lockIcon.className = 'bi bi-info-circle-fill text-info fs-5 me-2';
        lockMsg.innerHTML = `<strong>ĐÃ LƯU ĐIỂM DANH:</strong> Buổi học này đã được điểm danh trước đó. Bạn có thể nhấn nút <strong>Chỉnh Sửa</strong> để cập nhật lại nếu cần.`;
        banner.classList.remove('d-none');
    } else {
        banner.className = 'alert alert-success py-2 px-3 small border mb-3 d-flex align-items-center';
        lockIcon.className = 'bi bi-check-circle-fill text-success fs-5 me-2';
        lockMsg.innerHTML = `<strong>SẴN SÀNG ĐIỂM DANH:</strong> Ca học diễn ra hôm nay. Bạn có thể chọn Có mặt / Vắng mặt và nhấn <strong>Lưu Điểm Danh</strong>.`;
        banner.classList.remove('d-none');
    }

    // 4. Thiết lập Chế độ: Chỉ xem (View) hay Soạn thảo (Edit)
    const btnEdit = document.getElementById('btnEditAttendance');
    const btnSave = document.getElementById('btnSaveAttendance');
    const quickActions = document.getElementById('attQuickActionGroup');

    if (data.isAlreadySaved && data.canEdit) {
        // Đã lưu -> Vào chế độ xem trước, hiện nút Chỉnh sửa
        isAttendanceEditMode = false;
        btnEdit.classList.remove('d-none');
        btnSave.classList.add('d-none');
        quickActions.classList.add('d-none');
    } else if (data.canEdit && !data.isAlreadySaved) {
        // Chưa lưu -> Vào trực tiếp chế độ chỉnh sửa
        isAttendanceEditMode = true;
        btnEdit.classList.add('d-none');
        btnSave.classList.remove('d-none');
        quickActions.classList.remove('d-none');
    } else {
        // Bị khóa hoàn toàn (Hết hạn hoặc Role Học sinh)
        isAttendanceEditMode = false;
        btnEdit.classList.add('d-none');
        btnSave.classList.add('d-none');
        quickActions.classList.add('d-none');
    }

    // 5. Render danh sách học viên
    renderStudentsTable(data.students);
}

function renderStudentsTable(students) {
    const tbody = document.getElementById('attStudentTableBody');
    if (!tbody) return;

    if (!students || students.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center py-5 text-muted">
                    <i class="bi bi-people fs-2 d-block mb-2 text-secondary"></i>
                    Không có học viên nào được xếp trong lớp học này.
                </td>
            </tr>
        `;
        return;
    }

    let html = '';
    students.forEach((s, idx) => {
        const isPresent = (s.status === 'PRESENT');
        const isAbsent = (s.status === 'ABSENT_UNEXCUSED' || s.status === 'ABSENT_EXCUSED');
        const initials = getInitials(s.fullName);
        const disabledAttr = isAttendanceEditMode ? '' : 'disabled';

        html += `
            <tr id="row_student_${s.studentId}">
                <td class="text-center fw-bold text-muted">${s.orderNumber || (idx + 1)}</td>
                <td class="text-center">
                    <div class="student-avatar mx-auto">${initials}</div>
                </td>
                <td>
                    <span class="badge bg-secondary-subtle text-secondary border font-monospace">${s.studentCode || 'HV000' + s.studentId}</span>
                </td>
                <td>
                    <div class="fw-bold text-dark">${s.fullName}</div>
                    ${s.hasApprovedAbsence ? `<span class="badge bg-warning-subtle text-warning border border-warning-subtle small mt-1"><i class="bi bi-shield-check me-1"></i>Có đơn xin nghỉ phép</span>` : ''}
                </td>
                <td class="text-center">
                    <div class="btn-group" role="group">
                        <button type="button" 
                                class="btn btn-outline-success btn-attendance-toggle ${isPresent ? 'active-present' : ''}" 
                                id="btn_present_${s.studentId}" 
                                onclick="setStudentStatus(${s.studentId}, 'PRESENT')" 
                                ${disabledAttr}>
                            <i class="bi bi-check-lg me-1"></i> Có Mặt
                        </button>
                        <button type="button" 
                                class="btn btn-outline-danger btn-attendance-toggle ${isAbsent ? 'active-absent' : ''}" 
                                id="btn_absent_${s.studentId}" 
                                onclick="setStudentStatus(${s.studentId}, 'ABSENT_UNEXCUSED')" 
                                ${disabledAttr}>
                            <i class="bi bi-x-lg me-1"></i> Vắng Mặt
                        </button>
                    </div>
                </td>
                <td>
                    <input type="text" 
                           class="form-control form-control-sm form-control-modern" 
                           id="note_student_${s.studentId}" 
                           value="${s.note || (s.absenceReason ? 'Nghỉ phép: ' + s.absenceReason : '')}" 
                           placeholder="Nhập lý do hoặc ghi chú..." 
                           oninput="updateStudentNote(${s.studentId}, this.value)" 
                           ${disabledAttr}>
                </td>
            </tr>
        `;
    });

    tbody.innerHTML = html;
}

// =============================================================================
// THAO TÁC TRÊN BẢNG ĐIỂM DANH (CLIENT-SIDE INTERACTIONS)
// =============================================================================
function setStudentStatus(studentId, newStatus) {
    if (!isAttendanceEditMode || !activeLessonAttendance) return;

    const student = activeLessonAttendance.students.find(s => s.studentId === studentId);
    if (!student) return;

    student.status = newStatus;

    // Cập nhật trạng thái nút UI
    const btnPres = document.getElementById(`btn_present_${studentId}`);
    const btnAbs = document.getElementById(`btn_absent_${studentId}`);

    if (newStatus === 'PRESENT') {
        if (btnPres) btnPres.classList.add('active-present');
        if (btnAbs) btnAbs.classList.remove('active-absent');
    } else {
        if (btnPres) btnPres.classList.remove('active-present');
        if (btnAbs) btnAbs.classList.add('active-absent');
    }

    updateAttendanceCounters();
}

function updateStudentNote(studentId, noteText) {
    if (!activeLessonAttendance) return;
    const student = activeLessonAttendance.students.find(s => s.studentId === studentId);
    if (student) {
        student.note = noteText;
    }
}

function markAllPresent() {
    if (!isAttendanceEditMode || !activeLessonAttendance) return;

    activeLessonAttendance.students.forEach(s => {
        s.status = 'PRESENT';
        const btnPres = document.getElementById(`btn_present_${s.studentId}`);
        const btnAbs = document.getElementById(`btn_absent_${s.studentId}`);
        if (btnPres) btnPres.classList.add('active-present');
        if (btnAbs) btnAbs.classList.remove('active-absent');
    });

    updateAttendanceCounters();
    showToast('info', 'Điểm danh nhanh', 'Đã đặt tất cả học viên thành Có Mặt');
}

function enableAttendanceEditing() {
    isAttendanceEditMode = true;

    // Bật lại các nút và input
    document.querySelectorAll('.btn-attendance-toggle, #attStudentTable input').forEach(el => {
        el.removeAttribute('disabled');
    });

    document.getElementById('btnEditAttendance').classList.add('d-none');
    document.getElementById('btnSaveAttendance').classList.remove('d-none');
    document.getElementById('attQuickActionGroup').classList.remove('d-none');

    showToast('info', 'Chế độ chỉnh sửa', 'Bạn có thể thay đổi trạng thái và bấm Lưu Điểm Danh');
}

function updateAttendanceCounters() {
    if (!activeLessonAttendance || !activeLessonAttendance.students) return;

    const total = activeLessonAttendance.students.length;
    const present = activeLessonAttendance.students.filter(s => s.status === 'PRESENT').length;
    const absent = activeLessonAttendance.students.filter(s => s.status && s.status.startsWith('ABSENT')).length;

    const elTotal = document.getElementById('attTotalStudents');
    const elPres = document.getElementById('attPresentCount');
    const elAbs = document.getElementById('attAbsentCount');

    if (elTotal) elTotal.innerText = total;
    if (elPres) elPres.innerText = present;
    if (elAbs) elAbs.innerText = absent;
}

// =============================================================================
// LƯU ĐIỂM DANH CẢ LỚP (BATCH ATTENDANCE SAVE)
// =============================================================================
async function submitAttendanceBatch() {
    if (!activeLessonAttendance) return;

    const btnSave = document.getElementById('btnSaveAttendance');
    btnSave.disabled = true;
    btnSave.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span> Đang lưu...`;

    // Chuẩn bị payload Batch
    const payload = {
        attendances: activeLessonAttendance.students.map(s => {
            const noteInput = document.getElementById(`note_student_${s.studentId}`);
            const noteVal = noteInput ? noteInput.value.trim() : (s.note || '');
            return {
                studentId: s.studentId,
                status: s.status || 'PRESENT',
                note: noteVal
            };
        })
    };

    try {
        const response = await callApi(
            `/api/schedule/lessons/${activeLessonAttendance.lessonId}/attendance`,
            'POST',
            payload
        );

        showToast('success', 'Thành công', response.message || 'Lưu điểm danh thành công!');

        // Đổi về chế độ đã lưu
        isAttendanceEditMode = false;
        activeLessonAttendance.isAlreadySaved = true;

        // Vô hiệu hóa lại các nút
        document.querySelectorAll('.btn-attendance-toggle, #attStudentTable input').forEach(el => {
            el.setAttribute('disabled', 'true');
        });

        document.getElementById('btnSaveAttendance').classList.add('d-none');
        document.getElementById('btnEditAttendance').classList.remove('d-none');
        document.getElementById('attQuickActionGroup').classList.add('d-none');

        // Cập nhật banner thông báo
        const banner = document.getElementById('attLockBanner');
        const lockMsg = document.getElementById('attLockMessage');
        const lockIcon = document.getElementById('attLockIcon');
        if (banner) {
            banner.className = 'alert alert-info py-2 px-3 small border mb-3 d-flex align-items-center';
            lockIcon.className = 'bi bi-info-circle-fill text-info fs-5 me-2';
            lockMsg.innerHTML = `<strong>ĐÃ LƯU ĐIỂM DANH:</strong> Điểm danh đã được cập nhật thành công!`;
            banner.classList.remove('d-none');
        }

        // Tải lại ma trận tuần ở nền để cập nhật badge ngay lập tức
        loadWeeklySchedule();
    } catch (err) {
        console.error("Lỗi khi lưu điểm danh:", err);
        showToast('error', 'Lưu thất bại', err.message || 'Không thể lưu điểm danh');
    } finally {
        btnSave.disabled = false;
        btnSave.innerHTML = `<i class="bi bi-floppy-fill me-1"></i> Lưu Điểm Danh`;
    }
}

// =============================================================================
// MODAL TẠO LỚP HỌC MỚI & TỰ ĐỘNG SINH DANH SÁCH BUỔI HỌC (LESSON GENERATOR)
// =============================================================================
function openCreateClassModal() {
    if (!modalCreateClassObj) return;

    // Reset Form
    document.getElementById('createClassForm').reset();
    document.getElementById('newStartDate').value = formatDateForInput(new Date());

    // Mặc định chọn Thứ 2 (1) và Thứ 5 (4)
    const chk1 = document.getElementById('dow1');
    const chk4 = document.getElementById('dow4');
    if (chk1) chk1.checked = true;
    if (chk4) chk4.checked = true;

    // Tự sinh mã lớp ngẫu nhiên gợi ý
    const randomNum = Math.floor(10 + Math.random() * 90);
    document.getElementById('newClassCode').value = `CL-PIA-K${randomNum}`;
    document.getElementById('newClassName').value = `Piano Cơ Bản K${randomNum}`;

    loadCreateClassDependencies();
    modalCreateClassObj.show();
}

async function loadCreateClassDependencies() {
    try {
        // Nạp khóa học
        const courses = await callApi('/api/course-enrollment/courses').catch(() => []);
        const courseSelect = document.getElementById('newCourseSelect');
        if (courseSelect && courses) {
            courseSelect.innerHTML = '<option value="">-- Chọn khóa học --</option>' +
                courses.map(c => `<option value="${c.id}">${c.name} (${c.level || 'Cơ bản'})</option>`).join('');
            if (courses.length > 0) courseSelect.value = courses[0].id;
        }

        // Nạp cơ sở
        const branches = await callApi('/api/branches').catch(() => []);
        const branchSelect = document.getElementById('newBranchSelect');
        if (branchSelect && branches) {
            branchSelect.innerHTML = '<option value="">-- Chọn cơ sở đào tạo --</option>' +
                branches.map(b => `<option value="${b.id}">${b.name}</option>`).join('');
            if (branches.length > 0) {
                branchSelect.value = branches[0].id;
                onNewBranchChanged();
            }
        }

        // Nạp giáo viên
        const teachers = await callApi('/api/schedule/teachers').catch(() => []);
        const teacherSelect = document.getElementById('newTeacherSelect');
        if (teacherSelect && teachers) {
            teacherSelect.innerHTML = '<option value="">-- Chọn giáo viên phụ trách --</option>' +
                teachers.map(t => `<option value="${t.id}">${t.fullName} (@${t.username})</option>`).join('');
            if (teachers.length > 0) teacherSelect.value = teachers[0].id;
        }
    } catch (e) {
        console.error("Lỗi tải dependency tạo lớp:", e);
    }
}

async function onNewBranchChanged() {
    const branchId = document.getElementById('newBranchSelect').value;
    const roomSelect = document.getElementById('newRoomSelect');
    if (!roomSelect) return;

    if (!branchId) {
        roomSelect.innerHTML = '<option value="">-- Vui lòng chọn cơ sở trước --</option>';
        return;
    }

    try {
        const rooms = await callApi(`/api/branch-facility/branches/${branchId}/rooms`).catch(() => []);
        if (rooms && rooms.length > 0) {
            roomSelect.innerHTML = '<option value="">-- Chọn phòng học --</option>' +
                rooms.map(r => `<option value="${r.id}">${r.roomName} (${r.roomCode})</option>`).join('');
            roomSelect.value = rooms[0].id;
        } else {
            roomSelect.innerHTML = '<option value="">-- Chưa có phòng nào khả dụng --</option>';
        }
    } catch (e) {
        roomSelect.innerHTML = '<option value="">-- Không thể tải phòng --</option>';
    }
}

async function submitCreateClass() {
    const classCode = document.getElementById('newClassCode').value.trim();
    const className = document.getElementById('newClassName').value.trim();
    const courseId = document.getElementById('newCourseSelect').value;
    const branchId = document.getElementById('newBranchSelect').value;
    const roomId = document.getElementById('newRoomSelect').value;
    const teacherId = document.getElementById('newTeacherSelect').value;
    const maxStudents = parseInt(document.getElementById('newMaxStudents').value) || 8;
    const startDate = document.getElementById('newStartDate').value;
    const totalSessions = parseInt(document.getElementById('newTotalSessions').value) || 12;
    const startTimeStr = document.getElementById('newStartTime').value;
    const endTimeStr = document.getElementById('newEndTime').value;
    const scheduleDesc = document.getElementById('newScheduleDesc').value.trim();

    // Lấy danh sách thứ trong tuần
    const checkedDays = Array.from(document.querySelectorAll('input[name="daysOfWeek"]:checked'))
        .map(el => parseInt(el.value));

    if (checkedDays.length === 0) {
        alert("Vui lòng chọn ít nhất một thứ trong tuần để sinh lịch học!");
        return;
    }

    const payload = {
        courseId: parseInt(courseId),
        branchId: parseInt(branchId),
        roomId: roomId ? parseInt(roomId) : null,
        teacherId: teacherId ? parseInt(teacherId) : null,
        classCode: classCode,
        className: className,
        maxStudents: maxStudents,
        startDate: startDate,
        totalSessions: totalSessions,
        daysOfWeek: checkedDays,
        startTime: startTimeStr.length === 5 ? startTimeStr + ":00" : startTimeStr,
        endTime: endTimeStr.length === 5 ? endTimeStr + ":00" : endTimeStr,
        scheduleDescription: scheduleDesc || `Ca học thứ ${checkedDays.join(', ')}`
    };

    const btnSubmit = document.getElementById('btnSubmitCreateClass');
    btnSubmit.disabled = true;
    btnSubmit.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span> Đang tạo & sinh lịch...`;

    try {
        const response = await callApi('/api/schedule/classes', 'POST', payload);
        showToast('success', 'Thành công', response.message || 'Đã tạo lớp và tự động sinh lịch học!');
        modalCreateClassObj.hide();

        // Chuyển ngày xem đến ngày khai giảng lớp mới để thấy ngay lịch
        currentTargetDate = new Date(startDate);
        syncDatepicker();
        loadWeeklySchedule();
    } catch (err) {
        console.error("Lỗi khi tạo lớp:", err);
        showToast('error', 'Lỗi tạo lớp', err.message || 'Không thể tạo lớp học');
    } finally {
        btnSubmit.disabled = false;
        btnSubmit.innerHTML = `<i class="bi bi-check2-circle me-1"></i> Tạo Lớp & Sinh Lịch`;
    }
}

// =============================================================================
// BỘ LỌC GIÁO VIÊN & HỌC VIÊN
// =============================================================================
async function loadFilterOptions() {
    if (!currentUser) return;
    const role = currentUser.role || 'TEACHER';

    if (role === 'ADMIN' || role === 'STAFF' || role === 'BRANCH_MANAGER') {
        const group = document.getElementById('teacherFilterGroup');
        const select = document.getElementById('teacherFilterSelect');
        if (group && select) {
            group.classList.remove('d-none');
            const teachers = await callApi('/api/schedule/teachers').catch(() => []);
            if (teachers && teachers.length > 0) {
                select.innerHTML = '<option value="">-- Tất cả giáo viên --</option>' +
                    teachers.map(t => `<option value="${t.id}">${t.fullName}</option>`).join('');
            }
        }
    } else if (role === 'PARENT') {
        const group = document.getElementById('childFilterGroup');
        const select = document.getElementById('childFilterSelect');
        if (group && select) {
            group.classList.remove('d-none');
            let children = [];
            try {
                children = await callApi('/api/attendance-makeup/students', 'GET', null, false, false);
            } catch (err) {
                try {
                    children = await callApi('/api/course-enrollment/children', 'GET', null, false, false);
                } catch (err2) {
                    children = [];
                }
            }
            if (children && children.length > 0) {
                select.innerHTML = '<option value="">-- Lịch tất cả các con --</option>' +
                    children.map(c => `<option value="${c.id}">${c.fullName}</option>`).join('');
            }
        }
    }
}

function onTeacherFilterChange() {
    const sel = document.getElementById('teacherFilterSelect');
    filterTeacherId = sel ? sel.value : '';
    loadWeeklySchedule();
}

function onChildFilterChange() {
    const sel = document.getElementById('childFilterSelect');
    filterStudentId = sel ? sel.value : '';
    loadWeeklySchedule();
}

// =============================================================================
// HÀM TIỆN ÍCH HỖ TRỢ (UTILITIES)
// =============================================================================
function formatDateForInput(d) {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function formatDateDM(dateStr) {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3) return `${parts[2]}/${parts[1]}`;
    return dateStr;
}

function formatDateDMY(dateStr) {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
    return dateStr;
}

function parseLocalDate(dateStr) {
    const parts = dateStr.split('-');
    return new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
}

function getInitials(name) {
    if (!name) return 'HV';
    const words = name.trim().split(/\s+/);
    if (words.length === 1) return words[0].substring(0, 2).toUpperCase();
    return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

function showToast(type, title, message) {
    const container = document.getElementById('toastContainer');
    if (!container) {
        alert(`${title}: ${message}`);
        return;
    }

    const toastId = 'toast_' + Date.now();
    const bgClass = (type === 'success') ? 'bg-success text-white' :
                    (type === 'error') ? 'bg-danger text-white' : 'bg-primary text-white';

    const icon = (type === 'success') ? 'bi-check-circle-fill' :
                 (type === 'error') ? 'bi-x-circle-fill' : 'bi-info-circle-fill';

    const toastEl = document.createElement('div');
    toastEl.id = toastId;
    toastEl.className = `toast align-items-center ${bgClass} border-0 shadow-lg mb-2`;
    toastEl.setAttribute('role', 'alert');
    toastEl.setAttribute('aria-live', 'assertive');
    toastEl.setAttribute('aria-atomic', 'true');

    toastEl.innerHTML = `
        <div class="d-flex">
            <div class="toast-body d-flex align-items-center gap-2">
                <i class="bi ${icon} fs-5"></i>
                <div>
                    <strong>${title}</strong>: ${message}
                </div>
            </div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
        </div>
    `;

    container.appendChild(toastEl);
    const bsToast = new bootstrap.Toast(toastEl, { delay: 4000 });
    bsToast.show();

    toastEl.addEventListener('hidden.bs.toast', () => {
        toastEl.remove();
    });
}

function selectSlotPreset(startTime, endTime, desc) {
    const startInput = document.getElementById('newStartTime');
    const endInput = document.getElementById('newEndTime');
    const descInput = document.getElementById('newScheduleDesc');
    if (startInput) startInput.value = startTime;
    if (endInput) endInput.value = endTime;
    if (descInput && !descInput.value) descInput.value = desc;
}

