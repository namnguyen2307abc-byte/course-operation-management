/**
 * lesson-attendance.js
 * Trang điểm danh một buổi học: danh sách học viên + nút tròn bé Present / Absent.
 * - Không tự động chọn mặc định có mặt hay vắng mặt cho buổi mới.
 * - Giáo viên tự chọn từ đầu cho từng học viên rồi nhấn Lưu.
 * - Chặn học viên / phụ huynh truy cập trang này.
 */

let sheet = null;         // Dữ liệu buổi học & danh sách học viên từ API
let editMode = false;     // true: đang cho phép chọn / chỉnh sửa
let saving = false;

const AVATAR_COLORS = [
    ['#6366f1', '#2563eb'], ['#ec4899', '#db2777'], ['#f59e0b', '#ea580c'],
    ['#10b981', '#0d9488'], ['#8b5cf6', '#6d28d9'], ['#0ea5e9', '#0369a1'],
    ['#f43f5e', '#be123c'], ['#14b8a6', '#0f766e']
];

document.addEventListener('DOMContentLoaded', () => {
    loadNavbar();
    checkRoleAccess();

    const lessonId = new URLSearchParams(window.location.search).get('lessonId');
    if (!lessonId) {
        showEmpty('Không tìm thấy mã buổi học.', 'Hãy quay lại thời khóa biểu và chọn một ca học.');
        document.getElementById('attTitle').textContent = 'Thiếu mã buổi học';
        return;
    }

    document.getElementById('searchInput').addEventListener('input', renderStudents);
    loadSheet(lessonId);
});

function checkRoleAccess() {
    try {
        const user = typeof getCurrentUser === 'function' ? getCurrentUser() : null;
        if (user) {
            const role = (user.roleName || user.role || '').toUpperCase();
            if (role === 'STUDENT' || role === 'PARENT') {
                alert('Tài khoản Học viên / Phụ huynh không có quyền thực hiện điểm danh lớp học. Đang chuyển về Thời Khóa Biểu...');
                window.location.replace('weekly-schedule.html');
                return false;
            }
            const roleIndicator = document.getElementById('roleIndicator');
            if (roleIndicator) {
                roleIndicator.textContent = role === 'ADMIN' ? 'Quản Trị Viên' : 'Giáo Viên Phụ Trách';
            }
        }
    } catch (e) {
        console.warn('Lỗi kiểm tra quyền:', e);
    }
    return true;
}

function loadNavbar() {
    fetch('../components/navbar.html')
        .then(r => r.text())
        .then(html => {
            const el = document.getElementById('navbar-container');
            if (el) {
                el.innerHTML = html;
                if (typeof updateNavbarUser === 'function') updateNavbarUser();
            }
        })
        .catch(e => console.error('Lỗi nạp navbar:', e));
}

async function loadSheet(lessonId) {
    try {
        sheet = await callApi(`/api/schedule/lessons/${lessonId}/attendance`, 'GET', null, false, false);
        if (!sheet) return;

        // NẾU BUỔI HỌC CHƯA TỪNG ĐƯỢC LƯU ĐIỂM DANH:
        // Tuyệt đối không auto chọn Present, để tất cả học viên status = null
        // để giáo viên tự tay bấm chọn từng người từ đầu
        const saved = isSaved();
        if (!saved && Array.isArray(sheet.students)) {
            sheet.students.forEach(s => {
                if (!s.hasApprovedAbsence) {
                    s.status = null;
                }
            });
        }

        renderHeader();
        setupMode();
        renderStudents();
    } catch (err) {
        console.error('Lỗi tải điểm danh:', err);
        document.getElementById('attTitle').textContent = 'Không thể tải buổi học';
        showEmpty('Không thể tải danh sách học viên.', err.message || 'Lỗi kết nối máy chủ');
    }
}

/* ------------------------------ HEADER ------------------------------ */
function renderHeader() {
    document.title = `Điểm Danh: ${sheet.className || 'Lớp học'} - Học Viện Talent`;
    document.getElementById('attTitle').textContent = sheet.className || 'Buổi học';
    document.getElementById('attSubtitle').textContent =
        `Mã lớp: ${sheet.classCode || '—'} • Buổi số ${sheet.sessionNumber || 1} • Ngày ${fmtDMY(sheet.lessonDate)}`;
    document.getElementById('attInfoCourse').textContent = sheet.courseName || sheet.className || '—';
    document.getElementById('attInfoTime').textContent = `${sheet.timeRange || ''} · ${fmtDMY(sheet.lessonDate)}`;
    document.getElementById('attInfoRoom').textContent =
        [sheet.roomName, sheet.branchName].filter(Boolean).join(' - ') || '—';
    document.getElementById('attInfoTeacher').textContent = sheet.teacherName || 'Chưa phân công';

    const count = (sheet.students || []).length;
    const badgeEl = document.getElementById('badgeStudentCount');
    if (badgeEl) badgeEl.textContent = `${count} học viên`;

    // Nút quay lại đúng ngày tuần của ca học này
    const backUrl = `weekly-schedule.html?date=${sheet.lessonDate}`;
    const btnBack = document.getElementById('btnBack');
    if (btnBack) btnBack.setAttribute('href', backUrl);
    const btnBottomBack = document.getElementById('btnBottomBack');
    if (btnBottomBack) btnBottomBack.setAttribute('href', backUrl);
}

/* ------------------------------ CHẾ ĐỘ ------------------------------ */
function isSaved() {
    return Boolean(sheet.alreadySaved ?? sheet.isAlreadySaved);
}

function setupMode() {
    const banner = document.getElementById('attBanner');
    const icon = document.getElementById('attBannerIcon');
    const text = document.getElementById('attBannerText');
    const btnEdit = document.getElementById('btnEdit');
    const btnSave = document.getElementById('btnSave');

    banner.classList.remove('d-none', 'lock', 'saved', 'ready', 'future');

    // Xác định thời gian so với ngày hôm nay (YYYY-MM-DD)
    const now = new Date();
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    const lessonDateStr = sheet.lessonDate || '';
    const isFuture = lessonDateStr > todayStr;
    const isPast = lessonDateStr < todayStr;

    const user = typeof getCurrentUser === 'function' ? getCurrentUser() : null;
    const role = (user && (user.roleName || user.role || ''))?.toUpperCase();
    const isAdmin = role === 'ADMIN' || role === 'STAFF' || role === 'BRANCH_MANAGER';

    if (isFuture && !isAdmin) {
        // 1. BUỔI HỌC TRONG TƯƠNG LAI: Giáo viên chỉ xem trước danh sách, KHÔNG có nút lưu hay sửa
        editMode = false;
        banner.classList.add('future');
        icon.className = 'bi bi-calendar-event-fill';
        text.innerHTML = `<strong>CHƯA ĐẾN NGÀY HỌC:</strong> Buổi học sẽ diễn ra vào ngày <b>${fmtDMY(lessonDateStr)}</b>. Hiện tại bạn chỉ có thể xem danh sách học viên và chỉ được phép điểm danh vào đúng ngày diễn ra ca học.`;
        btnEdit.classList.add('d-none');
        btnSave.classList.add('d-none');
    } else if (isPast && !isAdmin) {
        // 2. BUỔI HỌC TRONG QUÁ KHỨ: Đã qua ngày, khóa điểm danh
        editMode = false;
        banner.classList.add('lock');
        icon.className = 'bi bi-lock-fill';
        text.innerHTML = `<strong>ĐÃ KHÓA ĐIỂM DANH:</strong> Buổi học ngày <b>${fmtDMY(lessonDateStr)}</b> đã kết thúc trong quá khứ. Theo quy định, chỉ Admin hoặc Giáo vụ mới có thể can thiệp chỉnh sửa.`;
        btnEdit.classList.add('d-none');
        btnSave.classList.add('d-none');
    } else if (!sheet.canEdit && !isAdmin) {
        editMode = false;
        banner.classList.add('lock');
        icon.className = 'bi bi-lock-fill';
        text.innerHTML = `<strong>ĐÃ KHÓA CHỈNH SỬA:</strong> ${escapeHtml(sheet.lockReason || 'Bạn chỉ có quyền xem danh sách buổi học này.')}`;
        btnEdit.classList.add('d-none');
        btnSave.classList.add('d-none');
    } else if (isSaved()) {
        // 3. BUỔI HỌC HÔM NAY ĐÃ LƯU: Cho phép bấm "Chỉnh sửa điểm danh"
        editMode = false;
        banner.classList.add('saved');
        icon.className = 'bi bi-check-circle-fill';
        text.innerHTML = '<strong>ĐÃ LƯU ĐIỂM DANH:</strong> Buổi học hôm nay đã được lưu điểm danh thành công. Nhấn <b>Chỉnh Sửa Điểm Danh</b> nếu muốn cập nhật lại.';
        btnEdit.classList.remove('d-none');
        btnSave.classList.add('d-none');
    } else {
        // 4. BUỔI HỌC HÔM NAY CHƯA LƯU: Sẵn sàng điểm danh
        editMode = true;
        banner.classList.add('ready');
        icon.className = 'bi bi-pencil-square';
        text.innerHTML = '<strong>SẴN SÀNG ĐIỂM DANH:</strong> Ca học diễn ra hôm nay. Vui lòng tích chọn nút tròn <b>Present</b> hoặc <b>Absent</b> cho từng học viên rồi nhấn <b>Lưu Điểm Danh</b>.';
        btnEdit.classList.add('d-none');
        btnSave.classList.remove('d-none');
    }
}

function enableEditing() {
    editMode = true;
    document.getElementById('btnEdit').classList.add('d-none');
    document.getElementById('btnSave').classList.remove('d-none');

    const banner = document.getElementById('attBanner');
    banner.classList.remove('saved', 'lock');
    banner.classList.add('ready');
    document.getElementById('attBannerIcon').className = 'bi bi-pencil-square';
    document.getElementById('attBannerText').innerHTML = '<strong>ĐANG CHỈNH SỬA:</strong> Bạn có thể thay đổi trạng thái Present / Absent của học viên rồi nhấn <b>Lưu Điểm Danh</b>.';

    renderStudents();
    showToast('info', 'Chế độ chỉnh sửa', 'Bạn có thể thay đổi Present / Absent cho học viên.');
}

/* ------------------------------ DANH SÁCH HỌC VIÊN ------------------------------ */
function renderStudents() {
    if (!sheet) return;
    const list = document.getElementById('studentList');
    const q = document.getElementById('searchInput').value.trim().toLowerCase();

    const students = (sheet.students || []).filter(s =>
        !q || (s.fullName || '').toLowerCase().includes(q) || (s.studentCode || '').toLowerCase().includes(q));

    if (!sheet.students || sheet.students.length === 0) {
        showEmpty('Chưa có học viên nào trong buổi học này.', 'Hãy kiểm tra danh sách ghi danh của lớp học.');
        return;
    }
    if (students.length === 0) {
        showEmpty('Không tìm thấy học viên phù hợp.', 'Thử tìm với từ khóa khác.');
        return;
    }

    const dis = editMode ? '' : 'disabled';
    const disCls = editMode ? '' : 'is-disabled';

    list.innerHTML = students.map((s, i) => {
        const [c1, c2] = AVATAR_COLORS[(s.studentId || i) % AVATAR_COLORS.length];
        const makeup = Boolean(s.makeup ?? s.isMakeup);
        const tags = [
            makeup ? '<span class="att-tag makeup"><i class="bi bi-arrow-repeat"></i>Học bù</span>' : '',
            s.hasApprovedAbsence ? '<span class="att-tag leave"><i class="bi bi-shield-check"></i>Có phép</span>' : ''
        ].join('');
        const makeupOrigin = s.makeupOrigin
            ? `<div class="att-reason" style="color: #2563eb; font-weight: 500;"><i class="bi bi-arrow-return-right me-1"></i>${escapeHtml(s.makeupOrigin)}</div>` : '';
        const reason = s.absenceReason
            ? `<div class="att-reason"><i class="bi bi-info-circle me-1"></i>Lý do nghỉ: ${escapeHtml(s.absenceReason)}</div>` : '';

        // Kiểm tra xem học viên đã được chọn trạng thái nào chưa
        // CHÚ Ý: Nếu status là null (buổi mới chưa điểm danh), cả hai đều false (UNCHECKED)
        const isPresentChecked = s.status === 'PRESENT';
        const isAbsentChecked = Boolean(s.status && String(s.status).startsWith('ABSENT'));

        let rowState = '';
        if (isPresentChecked) rowState = 'is-present';
        else if (isAbsentChecked) rowState = 'is-absent';

        return `
        <div class="att-row ${rowState}" id="row_${s.studentId}">
            <div class="att-idx">${s.orderNumber || i + 1}</div>

            <div class="att-student">
                <div class="att-avatar" style="--c1:${c1};--c2:${c2}">${initials(s.fullName)}</div>
                <div class="min-w-0">
                    <div class="att-name">${escapeHtml(s.fullName)}${tags}</div>
                    <div class="att-code">${escapeHtml(s.studentCode || 'HV' + s.studentId)}</div>
                    ${makeupOrigin}
                    ${reason}
                </div>
            </div>

            <!-- Nút tròn bé Present -->
            <div class="att-radio-cell rc-p">
                <label class="att-radio present ${disCls}">
                    <input type="radio" name="att_${s.studentId}" value="PRESENT"
                           ${isPresentChecked ? 'checked' : ''} ${dis}
                           onchange="setStatus(${s.studentId}, 'PRESENT')">
                    <span class="dot"></span>
                    <span class="txt">Present</span>
                </label>
            </div>

            <!-- Nút tròn bé Absent -->
            <div class="att-radio-cell rc-a">
                <label class="att-radio absent ${disCls}">
                    <input type="radio" name="att_${s.studentId}" value="ABSENT"
                           ${isAbsentChecked ? 'checked' : ''} ${dis}
                           onchange="setStatus(${s.studentId}, 'ABSENT')">
                    <span class="dot"></span>
                    <span class="txt">Absent</span>
                </label>
            </div>

            <!-- Ghi chú -->
            <div class="rc-note">
                <input type="text" class="att-note" id="note_${s.studentId}" value="${escapeAttr(s.note || '')}"
                       placeholder="${editMode ? 'Nhập ghi chú (nếu có)...' : '—'}" ${dis}
                       oninput="setNote(${s.studentId}, this.value)">
            </div>
        </div>`;
    }).join('');
}

function setStatus(studentId, status) {
    if (!editMode || !sheet) return;
    const s = sheet.students.find(x => x.studentId === studentId);
    if (!s) return;

    s.status = status;
    const row = document.getElementById(`row_${studentId}`);
    if (row) {
        row.classList.toggle('is-present', status === 'PRESENT');
        row.classList.toggle('is-absent', status === 'ABSENT');
    }
}

function setNote(studentId, value) {
    const s = sheet && sheet.students.find(x => x.studentId === studentId);
    if (s) s.note = value;
}

/* ------------------------------ LƯU ĐIỂM DANH ------------------------------ */
async function saveAttendance() {
    if (!sheet || saving) return;

    // KIỂM TRA: Giáo viên phải chọn Present hoặc Absent cho toàn bộ học viên từ đầu
    const unselected = (sheet.students || []).filter(s => !s.status);
    if (unselected.length > 0) {
        showToast('error', 'Chưa hoàn tất điểm danh',
            `Còn ${unselected.length} học viên chưa được chọn Present hoặc Absent. Vui lòng điểm danh đủ cho cả lớp trước khi lưu!`);
        return;
    }

    saving = true;
    const btn = document.getElementById('btnSave');
    const original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Đang lưu...';

    const payload = {
        attendances: sheet.students.map(s => ({
            studentId: s.studentId,
            status: String(s.status).startsWith('ABSENT') ? 'ABSENT' : 'PRESENT',
            note: (s.note || '').trim()
        }))
    };

    try {
        const res = await callApi(`/api/schedule/lessons/${sheet.lessonId}/attendance`, 'POST', payload, false, false);
        showToast('success', 'Thành công', (res && res.message) || 'Lưu điểm danh thành công!');

        sheet.alreadySaved = true;
        sheet.isAlreadySaved = true;
        editMode = false;
        setupMode();
        renderStudents();
    } catch (err) {
        console.error('Lỗi lưu điểm danh:', err);
        showToast('error', 'Lưu thất bại', err.message || 'Không thể lưu điểm danh');
    } finally {
        saving = false;
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

/* ------------------------------ TIỆN ÍCH ------------------------------ */
function showEmpty(title, desc) {
    document.getElementById('studentList').innerHTML =
        `<div class="att-empty"><i class="bi bi-people"></i><div class="fw-bold text-dark mb-1">${escapeHtml(title)}</div><div>${escapeHtml(desc || '')}</div></div>`;
}

function fmtDMY(d) {
    if (!d) return '';
    const p = String(d).split('-');
    return p.length === 3 ? `${p[2]}/${p[1]}/${p[0]}` : d;
}

function initials(name) {
    if (!name) return 'HV';
    const w = name.replace(/\(.*?\)/g, '').trim().split(/\s+/);
    if (w.length === 1) return w[0].substring(0, 2).toUpperCase();
    return (w[0][0] + w[w.length - 1][0]).toUpperCase();
}

function escapeHtml(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function escapeAttr(v) {
    return escapeHtml(v);
}

function showToast(type, title, message) {
    const container = document.getElementById('toastContainer');
    if (!container) {
        alert(`${title}: ${message}`);
        return;
    }

    const bg = type === 'success' ? 'bg-success' : type === 'error' ? 'bg-danger' : 'bg-primary';
    const icon = type === 'success' ? 'bi-check-circle-fill' : type === 'error' ? 'bi-x-circle-fill' : 'bi-info-circle-fill';

    const el = document.createElement('div');
    el.className = `toast align-items-center text-white ${bg} border-0 shadow-lg mb-2`;
    el.setAttribute('role', 'alert');
    el.innerHTML = `
        <div class="d-flex">
            <div class="toast-body d-flex align-items-center gap-2">
                <i class="bi ${icon} fs-5"></i>
                <div><strong>${escapeHtml(title)}</strong>: ${escapeHtml(message)}</div>
            </div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
        </div>`;
    container.appendChild(el);
    const t = new bootstrap.Toast(el, { delay: 4000 });
    t.show();
    el.addEventListener('hidden.bs.toast', () => el.remove());
}
