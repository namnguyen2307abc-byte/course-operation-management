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
let quickSearchKeyword = '';
let expandAllCells = false;
const MAX_VISIBLE_CARDS_PER_CELL = 2;

// =============================================================================
// KHỞI TẠO KHI TẢI TRANG
// =============================================================================
document.addEventListener('DOMContentLoaded', async () => {
    if (!checkAuth()) return;
    // 1. Kiểm tra xác thực & cập nhật Navbar
    currentUser = getCurrentUser();
    initNavbarAndRoleBadge();

    // 2. Khởi tạo Bootstrap Modals
    const attModalEl = document.getElementById('modalAttendance');
    if (attModalEl) modalAttendanceObj = new bootstrap.Modal(attModalEl);

    const createModalEl = document.getElementById('modalCreateClass');
    if (createModalEl) modalCreateClassObj = new bootstrap.Modal(createModalEl);

    // 3. Khởi tạo Datepicker (ưu tiên ?date=YYYY-MM-DD khi quay lại từ trang điểm danh)
    const dateParam = new URLSearchParams(window.location.search).get('date');
    if (dateParam && /^\d{4}-\d{2}-\d{2}$/.test(dateParam)) {
        currentTargetDate = parseLocalDate(dateParam);
    }
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
    updateNavbarUser();

    // Nạp footer
    fetch('../components/footer.html')
        .then(r => r.text())
        .then(html => {
            const container = document.getElementById('footer-container');
            if (container) container.innerHTML = html;
        })
        .catch(e => console.error("Lỗi nạp footer:", e));

    // Cập nhật huy hiệu vai trò và tiêu đề động theo Role
    const roleBadge = document.getElementById('currentUserRoleBadge');
    const btnCreateClass = document.getElementById('btnOpenCreateClassModal');
    const heroBreadcrumb = document.getElementById('heroBreadcrumbCurrent');
    const heroTitle = document.getElementById('heroTitleText');
    const heroSubtitle = document.getElementById('heroSubtitleText');

    if (!currentUser) {
        if (roleBadge) {
            roleBadge.innerText = 'Chưa đăng nhập';
            roleBadge.className = 'badge bg-secondary px-3 py-2 rounded-pill small';
        }
        return;
    }

    const role = currentUser.role || 'TEACHER';
    if (role === 'TEACHER') {
        document.title = 'Thời Khóa Biểu Giảng Dạy - Học Viện Talent';
        if (heroBreadcrumb) heroBreadcrumb.textContent = 'Thời Khóa Biểu Giảng Dạy';
        if (heroTitle) heroTitle.textContent = 'Thời Khóa Biểu Giảng Dạy';
        if (heroSubtitle) heroSubtitle.textContent = 'Xem lịch dạy các lớp theo ca trong tuần, theo dõi sĩ số và bấm trực tiếp vào ca để thực hiện điểm danh học viên.';
        if (roleBadge) {
            roleBadge.innerHTML = '<i class="bi bi-mortarboard-fill me-1"></i>Giáo Viên: ' + (currentUser.fullName || currentUser.username);
            roleBadge.className = 'badge bg-warning text-dark px-3 py-2 rounded-pill small';
        }
    } else if (role === 'PARENT') {
        document.title = 'Lịch Học Của Con - Học Viện Talent';
        if (heroBreadcrumb) heroBreadcrumb.textContent = 'Lịch Học Của Con';
        if (heroTitle) heroTitle.textContent = 'Lịch Học Của Con';
        if (heroSubtitle) heroSubtitle.textContent = 'Theo dõi thời khóa biểu và chuyên cần của con theo từng ca học và ngày trong tuần.';
        if (roleBadge) {
            roleBadge.innerHTML = '<i class="bi bi-people-fill me-1"></i>Phụ Huynh: ' + (currentUser.fullName || currentUser.username);
            roleBadge.className = 'badge bg-success px-3 py-2 rounded-pill small';
        }
    } else if (role === 'STUDENT') {
        document.title = 'Lịch Học Cá Nhân - Học Viện Talent';
        if (heroBreadcrumb) heroBreadcrumb.textContent = 'Lịch Học';
        if (heroTitle) heroTitle.textContent = 'Lịch Học Cá Nhân';
        if (heroSubtitle) heroSubtitle.textContent = 'Theo dõi thời khóa biểu các ca học và điểm danh chuyên cần của bạn.';
        if (roleBadge) {
            roleBadge.innerHTML = '<i class="bi bi-person-fill me-1"></i>Học Viên: ' + (currentUser.fullName || currentUser.username);
            roleBadge.className = 'badge bg-info text-dark px-3 py-2 rounded-pill small';
        }
    } else {
        document.title = 'Thời Khóa Biểu Trung Tâm - Học Viện Talent';
        if (heroBreadcrumb) heroBreadcrumb.textContent = 'Thời Khóa Biểu Trung Tâm';
        if (heroTitle) heroTitle.textContent = 'Thời Khóa Biểu Trung Tâm';
        if (heroSubtitle) heroSubtitle.textContent = 'Theo dõi toàn bộ lịch học trung tâm theo 4 slot khung giờ, lọc theo giáo viên và quản lý điểm danh ca học.';
        if (roleBadge) {
            roleBadge.innerHTML = '<i class="bi bi-shield-check me-1"></i>' + (role === 'ADMIN' ? 'Quản Trị Viên' : 'Giáo Vụ');
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
    const expandBtn = document.getElementById('btnToggleExpandCells');
    if (expandBtn && data.currentUserRole !== 'STUDENT' && data.currentUserRole !== 'PARENT') {
        expandBtn.classList.remove('d-none');
    }
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
            <td class="slot-cell-header slot-${slot.slotNumber}">
                <div class="slot-name">${slot.slotName}</div>
                <div class="slot-time"><i class="bi bi-clock"></i>${slot.timeRange}</div>
            </td>
        `;

        // 7 cột Thứ 2 đến Chủ nhật
        for (let dayIndex = 1; dayIndex <= 7; dayIndex++) {
            const colDate = new Date(mondayDate);
            colDate.setDate(colDate.getDate() + (dayIndex - 1));
            const colDateStr = formatDateForInput(colDate);
            const isToday = (colDateStr === todayStr);

            const key = `${slot.slotNumber}_${dayIndex}`;
            const rawLessonsInCell = cellLessonsMap[key] || [];
            const lessonsInCell = quickSearchKeyword
                ? rawLessonsInCell.filter(l => {
                    const kw = quickSearchKeyword.toLowerCase();
                    return (l.className && l.className.toLowerCase().includes(kw))
                        || (l.classCode && l.classCode.toLowerCase().includes(kw))
                        || (l.roomName && l.roomName.toLowerCase().includes(kw))
                        || (l.teacherName && l.teacherName.toLowerCase().includes(kw));
                })
                : rawLessonsInCell;

            html += `<td class="${isToday ? 'today-column' : ''}">`;

            const isParentOrStudent = (data.currentUserRole === 'STUDENT' || data.currentUserRole === 'PARENT');
            if (lessonsInCell.length > 0) {
                if (isParentOrStudent || expandAllCells || lessonsInCell.length <= MAX_VISIBLE_CARDS_PER_CELL) {
                    lessonsInCell.forEach(l => {
                        html += renderLessonCard(l, data.currentUserRole);
                    });
                } else {
                    const visibleLessons = lessonsInCell.slice(0, MAX_VISIBLE_CARDS_PER_CELL);
                    const hiddenLessons = lessonsInCell.slice(MAX_VISIBLE_CARDS_PER_CELL);
                    visibleLessons.forEach(l => {
                        html += renderLessonCard(l, data.currentUserRole);
                    });
                    html += `<div id="cellMore_${key}" class="d-none">`;
                    hiddenLessons.forEach(l => {
                        html += renderLessonCard(l, data.currentUserRole);
                    });
                    html += `</div>`;
                    html += `
                        <button type="button" class="btn-cell-more" id="btnCellMore_${key}" onclick="event.stopPropagation(); toggleCellMore('${key}', ${hiddenLessons.length})">
                            <i class="bi bi-layers-fill me-1"></i>+${hiddenLessons.length} lớp khác &#9662;
                        </button>
                    `;
                }
            }

            html += `</td>`;
        }

        html += `</tr>`;
    });

    tbody.innerHTML = html;
}

function renderLessonCard(lesson, userRole) {
    // Lombok sinh getter isToday()/isPast() nên JSON key là "today"/"past"; đọc cả hai tên.
    const isToday = Boolean(lesson.today ?? lesson.isToday);
    const isPast = Boolean(lesson.past ?? lesson.isPast);

    const slotNum = lesson.slotNumber || 1;
    const slotClass = `slot-${slotNum}`;
    let stateClass = isToday ? 'state-today' : '';

    const slotBorder = lesson.slotBorderColor || (slotNum === 1 ? '#0284c7' : slotNum === 2 ? '#059669' : slotNum === 3 ? '#ea580c' : '#7c3aed');
    const slotBg = lesson.slotBgColor || (slotNum === 1 ? '#f0f9ff' : slotNum === 2 ? '#f0fdf4' : slotNum === 3 ? '#ffedd5' : '#f3e8ff');
    const slotInlineStyle = `style="--accent: ${slotBorder}; --accent-soft: ${slotBg}; border-left: 4.5px solid ${slotBorder} !important;"`;

    // 1. DÀNH CHO HỌC VIÊN HOẶC PHỤ HUYNH
    // Không cho phép bấm link vào trang điểm danh, cập nhật ngay trạng thái điểm danh
    if (userRole === 'STUDENT' || userRole === 'PARENT') {
        const personalMarks = lesson.studentAttendances || [];
        let effectiveStatus = lesson.attendanceStatus;
        if (isPast && (!effectiveStatus || effectiveStatus === 'NOT_MARKED')) {
            effectiveStatus = 'ABSENT';
        }
        let stuBadge = '';
        if (effectiveStatus === 'PRESENT') {
            stateClass = 'state-done';
            stuBadge = '<span class="stu-badge present"><i class="bi bi-check-circle-fill"></i> Có mặt</span>';
        } else if (effectiveStatus && String(effectiveStatus).includes('ABSENT')) {
            stateClass = 'state-absent';
            stuBadge = '<span class="stu-badge absent"><i class="bi bi-x-circle-fill"></i> Vắng mặt</span>';
        } else {
            stuBadge = `<span class="stu-badge pending"><i class="bi bi-clock"></i> ${isToday ? 'Chưa điểm danh' : 'Sắp diễn ra'}</span>`;
        }
        let personalRows = '';
        if (personalMarks.length > 1) {
            personalRows = personalMarks.map(mark => {
                const status = mark.status === 'PRESENT'
                    ? 'Có mặt'
                    : (mark.status === 'ABSENT' || isPast ? 'Vắng mặt' : (isToday ? 'Chưa điểm danh' : 'Sắp diễn ra'));
                return `<div class="lesson-meta"><i class="bi bi-person-check"></i><span>${escapeHtml(mark.fullName)}: <strong>${status}</strong></span></div>`;
            }).join('');
        } else if (personalMarks.length === 1 && userRole === 'PARENT') {
            personalRows = `<div class="lesson-meta"><i class="bi bi-person"></i><span>${escapeHtml(personalMarks[0].fullName)}</span></div>`;
        }

        const makeupTag = lesson.makeupSession
            ? '<span class="stu-badge makeup-tag" title="Buổi học bù"><i class="bi bi-arrow-repeat"></i>Học bù</span>'
            : '';
        const sessionText = lesson.sessionNumber ? ` • B${lesson.sessionNumber}` : '';

        return `
            <div class="lesson-card ${slotClass} ${stateClass} student-card" ${slotInlineStyle} title="${escapeAttr(lesson.className)}">
                <div class="lesson-card-top">
                    <span class="lesson-code" title="${escapeAttr(lesson.classCode || 'LỚP')}">${escapeHtml(lesson.classCode || 'LỚP')}</span>
                    <div class="d-flex flex-wrap align-items-center gap-1">
                        ${makeupTag}
                        ${personalMarks.length > 1 ? '' : stuBadge}
                    </div>
                </div>

                <div class="lesson-title" title="${escapeAttr(lesson.className)}">${escapeHtml(lesson.className)}</div>
                ${personalRows}

                <div class="lesson-meta">
                    <i class="bi bi-clock"></i>
                    <span>${escapeHtml(lesson.timeRange)}${escapeHtml(sessionText)}</span>
                </div>

                <div class="lesson-meta">
                    <i class="bi bi-geo-alt"></i>
                    <span>${escapeHtml(lesson.roomName || '—')}</span>
                </div>

                <div class="lesson-meta mb-0">
                    <i class="bi bi-person"></i>
                    <span>${escapeHtml(lesson.teacherName || '—')}</span>
                </div>
            </div>
        `;
    }

    // 2. DÀNH CHO GIÁO VIÊN / ADMIN / STAFF (GIAO DIỆN GỌN GÀNG, CHỐNG TRÀN Ô)
    const now = new Date();
    const currentTimeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    let slotStartTime = '';
    if (lesson.startTime) {
        slotStartTime = String(lesson.startTime).substring(0, 5);
    } else if (lesson.timeRange && lesson.timeRange.includes('-')) {
        slotStartTime = lesson.timeRange.split('-')[0].trim();
    }
    const isBeforeSlot = Boolean(isToday && slotStartTime && currentTimeStr < slotStartTime);

    const makeupInCount = lesson.makeupInCount || 0;
    const makeupOutCount = lesson.makeupOutCount || 0;
    const isTransferredEmpty = (lesson.totalStudents || 0) === 0 && makeupOutCount > 0;

    let badgeHtml = '';
    if (isTransferredEmpty) {
        stateClass = '';
        badgeHtml = '<span class="stu-badge pending" title="Học viên đã chuyển sang buổi học bù khác"><i class="bi bi-arrow-left-right"></i>Trống (Bù)</span>';
    } else if (lesson.attendanceCompleted) {
        stateClass = 'state-done';
        badgeHtml = '<span class="stu-badge present" title="Đã hoàn thành điểm danh"><i class="bi bi-check2-circle"></i>Đã ĐD</span>';
    } else if (isToday) {
        if (isBeforeSlot && userRole === 'TEACHER') {
            badgeHtml = `<span class="stu-badge" style="background:#f1f5f9; color:#475569; font-weight:600;" title="Chưa tới giờ vào ca (${escapeAttr(slotStartTime)})"><i class="bi bi-clock"></i>${escapeHtml(slotStartTime)}</span>`;
        } else {
            badgeHtml = '<span class="stu-badge" style="background:#fef3c7; color:#92400e; font-weight:700;" title="Ca học hôm nay cần điểm danh"><i class="bi bi-pencil-square"></i>Cần ĐD</span>';
        }
    } else if (isPast) {
        stateClass = 'state-missed';
        badgeHtml = '<span class="stu-badge" style="background:#fee2e2; color:#991b1b; font-weight:700;" title="Buổi học đã qua chưa điểm danh (học viên mặc định tính vắng mặt)"><i class="bi bi-exclamation-circle"></i>Chưa ĐD</span>';
    } else {
        badgeHtml = '<span class="stu-badge pending" title="Buổi học sắp diễn ra"><i class="bi bi-clock"></i>Sắp tới</span>';
    }

    const href = `lesson-attendance.html?lessonId=${lesson.lessonId}`;
    const actionText = isTransferredEmpty ? 'Chi tiết' : (lesson.attendanceCompleted ? 'Đã lưu' : (isBeforeSlot && userRole === 'TEACHER' ? 'Chi tiết' : 'Điểm danh'));
    const actionPillClass = isTransferredEmpty ? 'pill-view' : (lesson.attendanceCompleted ? 'pill-done' : (isBeforeSlot && userRole === 'TEACHER' ? 'pill-view' : 'pill-go'));
    const sessionBadge = lesson.sessionNumber ? ` • B${lesson.sessionNumber}` : '';

    const makeupInTip = (lesson.makeupInDetails || []).join(', ');
    const makeupOutTip = (lesson.makeupOutDetails || []).join(', ');
    const makeupBadgeLine = (makeupInCount > 0 || makeupOutCount > 0) ? `
        <div class="d-flex flex-wrap gap-1 mt-1" style="font-size: 0.68rem;">
            ${makeupInCount > 0 ? `<span class="badge bg-info-subtle text-info-emphasis border border-info-subtle px-1.5 py-0.5 rounded-pill" title="Học viên học bù vào lớp: ${escapeAttr(makeupInTip)}">+${makeupInCount} bù vào</span>` : ''}
            ${makeupOutCount > 0 ? `<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle px-1.5 py-0.5 rounded-pill" title="Học viên chuyển sang học bù lớp khác: ${escapeAttr(makeupOutTip)}">-${makeupOutCount} chuyển bù</span>` : ''}
        </div>` : '';

    // Hiển thị danh sách học viên thực tế của buổi học cho GV / Admin / Staff
    const sessionRoster = lesson.studentAttendances || [];
    const rosterTitleTip = sessionRoster.map(s => s.fullName).join(', ');
    let sessionRosterHtml = '';
    if (sessionRoster.length > 0) {
        const visibleRoster = sessionRoster.slice(0, 2).map(s => {
            const isMk = String(s.fullName || '').includes('(Học bù)');
            const shortName = String(s.fullName || '').replace(' (Học bù)', '');
            return `<span class="d-inline-flex align-items-center gap-1 text-truncate" style="max-width:100%;" title="${escapeAttr(s.fullName)}">
                <i class="bi ${isMk ? 'bi-person-plus-fill text-info' : 'bi-person-fill text-secondary'}"></i>
                <span class="text-truncate">${escapeHtml(shortName)}</span>${isMk ? '<span class="badge bg-info-subtle text-info-emphasis px-1" style="font-size:0.62rem;">Bù</span>' : ''}
            </span>`;
        }).join('');
        const extraCount = sessionRoster.length - 2;
        const extraHtml = extraCount > 0 ? `<span class="text-muted" style="font-size:0.68rem;">+${extraCount} HV khác</span>` : '';
        sessionRosterHtml = `<div class="lesson-meta flex-column align-items-start gap-0 mt-1" style="font-size:0.72rem;" title="${escapeAttr(rosterTitleTip)}">${visibleRoster}${extraHtml}</div>`;
    }

    return `
        <div class="lesson-card ${slotClass} ${stateClass} teacher-class-card" 
             onclick="window.location.href='${href}'" 
             ${slotInlineStyle} 
             title="${escapeAttr(lesson.className || '')} (${escapeAttr(lesson.classCode || '')}) - Phòng: ${escapeAttr(lesson.roomName || 'Chưa xếp')} - GV: ${escapeAttr(lesson.teacherName || '—')}${rosterTitleTip ? ' - HV: ' + escapeAttr(rosterTitleTip) : ''}">
            <div class="lesson-card-top">
                <span class="lesson-code" title="${escapeAttr(lesson.classCode || '')}">${escapeHtml(lesson.classCode || 'LỚP')}</span>
                ${badgeHtml}
            </div>

            <div class="lesson-title fw-bold text-dark" title="${escapeAttr(lesson.className)}">
                ${escapeHtml(lesson.className)}
            </div>

            <div class="lesson-meta">
                <i class="bi bi-clock"></i>
                <span>${escapeHtml(lesson.timeRange)}${escapeHtml(sessionBadge)}</span>
            </div>

            <div class="lesson-meta">
                <i class="bi bi-geo-alt text-primary"></i>
                <span>${escapeHtml(lesson.roomName || 'Chưa xếp phòng')}</span>
            </div>

            ${userRole !== 'TEACHER' ? `
            <div class="lesson-meta">
                <i class="bi bi-person"></i>
                <span>${escapeHtml(lesson.teacherName || '—')}</span>
            </div>
            ` : ''}

            ${sessionRosterHtml}
            ${makeupBadgeLine}

            <div class="tc-card-footer">
                <span class="tc-student-count" title="${rosterTitleTip ? 'Học viên buổi này: ' + escapeAttr(rosterTitleTip) : 'Sĩ số lớp'}">
                    <i class="bi bi-people-fill"></i>${lesson.totalStudents || 0} HV
                </span>
                <span class="tc-action-pill ${actionPillClass}">
                    ${actionText} <i class="bi bi-arrow-right-short"></i>
                </span>
            </div>
        </div>
    `;
}

function toggleCellMore(key, hiddenCount) {
    const moreEl = document.getElementById(`cellMore_${key}`);
    const btnEl = document.getElementById(`btnCellMore_${key}`);
    if (!moreEl || !btnEl) return;
    const isHidden = moreEl.classList.contains('d-none');
    moreEl.classList.toggle('d-none', !isHidden);
    if (isHidden) {
        btnEl.innerHTML = '<i class="bi bi-chevron-up me-1"></i>Thu gọn &#9652;';
    } else {
        btnEl.innerHTML = `<i class="bi bi-layers-fill me-1"></i>+${hiddenCount} lớp khác &#9662;`;
    }
}

function onQuickSearchScheduleChange() {
    const input = document.getElementById('scheduleQuickSearchInput');
    quickSearchKeyword = input ? input.value.trim() : '';
    if (currentScheduleData) {
        renderWeeklyMatrix(currentScheduleData);
    }
}

function toggleExpandAllCells() {
    expandAllCells = !expandAllCells;
    const btn = document.getElementById('btnToggleExpandCells');
    if (btn) {
        btn.innerHTML = expandAllCells
            ? '<i class="bi bi-arrows-collapse me-1"></i>Thu gọn ô'
            : '<i class="bi bi-arrows-expand me-1"></i>Mở rộng ô';
        btn.className = expandAllCells
            ? 'btn btn-primary btn-sm rounded-pill px-2.5'
            : 'btn btn-outline-secondary btn-sm rounded-pill px-2.5';
    }
    if (currentScheduleData) {
        renderWeeklyMatrix(currentScheduleData);
    }
}

// =============================================================================
// MODAL TẠO LỚP HỌC MỚI MODAL TẠO LỚP HỌC MỚI & TỰ ĐỘNG SINH DANH SÁCH BUỔI HỌC (LESSON GENERATOR)
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
                courses.map(c => `<option value="${c.id}" data-sessions="${c.totalSessions || 12}">${c.name} (${c.level || 'Cơ bản'} - ${c.totalSessions || 12} buổi)</option>`).join('');
            if (courses.length > 0) {
                courseSelect.value = courses[0].id;
                const totalInput = document.getElementById('newTotalSessions');
                if (totalInput && courses[0].totalSessions) totalInput.value = courses[0].totalSessions;
            }
            courseSelect.onchange = function() {
                const opt = courseSelect.options[courseSelect.selectedIndex];
                const totalInput = document.getElementById('newTotalSessions');
                if (opt && opt.dataset.sessions && totalInput) {
                    totalInput.value = opt.dataset.sessions;
                }
            };
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
        const rooms = await callApi(`/api/branches/${branchId}/rooms`).catch(() => []);
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
    const maxStudents = parseInt(document.getElementById('newMaxStudents').value, 10);
    const startDate = document.getElementById('newStartDate').value;
    const totalSessions = parseInt(document.getElementById('newTotalSessions').value, 10);
    const startTimeStr = document.getElementById('newStartTime').value;
    const endTimeStr = document.getElementById('newEndTime').value;
    const scheduleDesc = document.getElementById('newScheduleDesc').value.trim();

    if (!courseId) {
        showToast('error', 'Thiếu thông tin', 'Vui lòng chọn khóa học!');
        return;
    }
    if (!branchId) {
        showToast('error', 'Thiếu thông tin', 'Vui lòng chọn cơ sở đào tạo!');
        return;
    }
    if (!classCode || classCode.length > 50) {
        showToast('error', 'Mã lớp không hợp lệ', 'Vui lòng nhập mã lớp (tối đa 50 ký tự)!');
        return;
    }
    if (!className || className.length > 150) {
        showToast('error', 'Tên lớp không hợp lệ', 'Vui lòng nhập tên lớp học (tối đa 150 ký tự)!');
        return;
    }
    if (!startDate) {
        showToast('error', 'Thiếu ngày bắt đầu', 'Vui lòng chọn ngày bắt đầu khai giảng lớp!');
        return;
    }
    if (isNaN(maxStudents) || maxStudents < 1 || maxStudents > 100) {
        showToast('error', 'Sĩ số không hợp lệ', 'Sĩ số tối đa phải nằm trong khoảng từ 1 đến 100 học viên!');
        return;
    }
    if (isNaN(totalSessions) || totalSessions < 1 || totalSessions > 60) {
        showToast('error', 'Số buổi không hợp lệ', 'Tổng số buổi học phải nằm trong khoảng từ 1 đến 60 buổi!');
        return;
    }
    if (!startTimeStr || !endTimeStr || endTimeStr <= startTimeStr) {
        showToast('error', 'Giờ học không hợp lệ', 'Giờ kết thúc phải lớn hơn giờ bắt đầu!');
        return;
    }

    // Lấy danh sách thứ trong tuần
    const checkedDays = Array.from(document.querySelectorAll('input[name="daysOfWeek"]:checked'))
        .map(el => parseInt(el.value, 10))
        .filter(d => d >= 1 && d <= 7);

    if (checkedDays.length === 0) {
        showToast('error', 'Chưa chọn thứ học', 'Vui lòng chọn ít nhất một thứ trong tuần để sinh lịch học!');
        return;
    }

    const payload = {
        courseId: parseInt(courseId, 10),
        branchId: parseInt(branchId, 10),
        roomId: roomId ? parseInt(roomId, 10) : null,
        teacherId: teacherId ? parseInt(teacherId, 10) : null,
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
                children = await callApi('/api/schedule/students', 'GET', null, false, false);
            } catch (err) {
                try {
                    children = await callApi('/api/attendance-makeup/students', 'GET', null, false, false);
                } catch (err2) {
                    try {
                        children = await callApi('/api/course-enrollment/children', 'GET', null, false, false);
                    } catch (err3) {
                        children = [];
                    }
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

function escapeHtml(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function escapeAttr(v) {
    return escapeHtml(v);
}


