/**
 * teacher-classes.js
 * Quản lý danh sách lớp phụ trách dành cho Giáo viên
 */

let allClasses = [];
let detailModalInstance = null;

const AVATAR_PALETTES = [
    ['#059669', '#10b981'], ['#0284c7', '#38bdf8'], ['#7c3aed', '#a855f7'],
    ['#d97706', '#f59e0b'], ['#dc2626', '#f87171'], ['#0f766e', '#14b8a6']
];

document.addEventListener('DOMContentLoaded', () => {
    if (!checkAuth()) return;
    updateNavbarUser();

    const modalEl = document.getElementById('classDetailModal');
    if (modalEl) {
        detailModalInstance = new bootstrap.Modal(modalEl);
    }

    loadClasses();
});

async function loadClasses() {
    const container = document.getElementById('classesContainer');
    if (!container) return;

    container.innerHTML = `
        <div class="text-center py-5 text-muted">
            <div class="spinner-border text-success spinner-border-sm mb-2" role="status"></div>
            <div>Đang tải danh sách lớp phụ trách...</div>
        </div>
    `;

    try {
        const data = await callApi('/api/teacher/classes', 'GET');
        allClasses = Array.isArray(data) ? data : [];
        updateKpis(allClasses);
        renderClasses(allClasses);
    } catch (err) {
        console.error('Lỗi tải danh sách lớp:', err);
        container.innerHTML = `
            <div class="text-center py-5 text-danger bg-white rounded-4 border p-4 my-2">
                <i class="bi bi-exclamation-triangle fs-2 d-block mb-2"></i>
                <div class="fw-bold fs-5 mb-1">Không thể tải danh sách lớp học</div>
                <div class="small text-muted mb-3">${escapeHtml(err.message || 'Lỗi kết nối máy chủ')}</div>
                <button class="btn btn-outline-success btn-sm rounded-pill px-4" onclick="loadClasses()">
                    <i class="bi bi-arrow-clockwise me-1"></i> Tải lại
                </button>
            </div>
        `;
    }
}

function updateKpis(classes) {
    const elClasses = document.getElementById('kpiTotalClasses');
    const elStudents = document.getElementById('kpiTotalStudents');
    const elNextLesson = document.getElementById('kpiNextLesson');
    const elNextLessonSub = document.getElementById('kpiNextLessonSub');

    if (elClasses) elClasses.textContent = classes.length;

    const totalStudents = classes.reduce((sum, c) => sum + (c.currentStudents || 0), 0);
    if (elStudents) elStudents.textContent = totalStudents;

    // Tìm ca dạy tiếp theo gần nhất
    const now = new Date();
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

    const upcomingLessons = classes
        .filter(c => c.nextLessonDate && c.nextLessonDate >= todayStr)
        .sort((a, b) => a.nextLessonDate.localeCompare(b.nextLessonDate));

    if (upcomingLessons.length > 0) {
        const next = upcomingLessons[0];
        const timeStr = (next.nextLessonStartTime ? String(next.nextLessonStartTime).substring(0, 5) : '')
            + (next.nextLessonEndTime ? ' - ' + String(next.nextLessonEndTime).substring(0, 5) : '');
        if (elNextLesson) elNextLesson.textContent = `${fmtDMY(next.nextLessonDate)} (${timeStr})`;
        if (elNextLessonSub) elNextLessonSub.textContent = `${next.className} · ${next.roomName || 'Phòng học'}`;
    } else {
        if (elNextLesson) elNextLesson.textContent = 'Không có ca dạy sắp tới';
        if (elNextLessonSub) elNextLessonSub.textContent = 'Tất cả các lớp đã kết thúc hoặc chưa có lịch';
    }
}

function filterClasses() {
    const q = (document.getElementById('searchInput')?.value || '').trim().toLowerCase();
    const status = document.getElementById('statusFilter')?.value || '';

    const filtered = allClasses.filter(c => {
        const matchText = !q || (c.className && c.className.toLowerCase().includes(q))
            || (c.classCode && c.classCode.toLowerCase().includes(q))
            || (c.courseName && c.courseName.toLowerCase().includes(q))
            || (c.courseSubject && c.courseSubject.toLowerCase().includes(q));

        const matchStatus = !status || c.status === status;
        return matchText && matchStatus;
    });

    renderClasses(filtered);
}

function renderClasses(classes) {
    const container = document.getElementById('classesContainer');
    const countEl = document.getElementById('classCountText');
    if (countEl) countEl.textContent = `Hiển thị ${classes.length} lớp`;

    if (!classes || classes.length === 0) {
        container.innerHTML = `
            <div class="text-center py-5 bg-white rounded-4 border p-4 my-2">
                <i class="bi bi-folder2-open text-muted fs-1 d-block mb-2"></i>
                <h5 class="fw-bold text-dark">Chưa có lớp học nào</h5>
                <p class="text-muted small mb-0">Không tìm thấy lớp học phù hợp với điều kiện tìm kiếm hoặc bạn chưa được phân công lớp.</p>
            </div>
        `;
        return;
    }

    let html = '<div class="row g-4">';
    classes.forEach(c => {
        const statusBadge = getStatusBadge(c.status);
        const percent = Math.min(100, Math.max(0, c.progressPercent || 0));

        html += `
            <div class="col-12 col-md-6 col-xl-4">
                <div class="class-card">
                    <div>
                        <!-- Header thẻ: Mã lớp & Trạng thái -->
                        <div class="class-card-header">
                            <span class="class-code-pill">
                                <i class="bi bi-hash"></i>${escapeHtml(c.classCode || 'CL')}
                            </span>
                            ${statusBadge}
                        </div>

                        <!-- Tên lớp học -->
                        <h3 class="class-title">${escapeHtml(c.className || 'Lớp học')}</h3>

                        <!-- Khóa học & Bộ môn -->
                        <div class="course-tag">
                            <i class="bi bi-mortarboard-fill"></i>
                            <span>${escapeHtml(c.courseName || '')}</span>
                            ${c.courseSubject ? `<span class="subject-badge">${escapeHtml(c.courseSubject)}</span>` : ''}
                        </div>

                        <!-- Bảng tóm tắt thông số -->
                        <div class="class-specs-box">
                            <div class="class-spec-row">
                                <span class="spec-label"><i class="bi bi-geo-alt text-danger"></i>Phòng & Cơ sở:</span>
                                <span class="spec-value">${escapeHtml(c.roomName || 'Phòng học')} · ${escapeHtml(c.branchName || 'Cơ sở')}</span>
                            </div>
                            <div class="class-spec-row">
                                <span class="spec-label"><i class="bi bi-clock text-primary"></i>Lịch học:</span>
                                <span class="spec-value">${escapeHtml(c.scheduleDescription || 'Chưa xếp lịch')}</span>
                            </div>
                            <div class="class-spec-row">
                                <span class="spec-label"><i class="bi bi-people text-success"></i>Sĩ số:</span>
                                <span class="spec-value">${c.currentStudents || 0} / ${c.maxStudents || 10} học viên</span>
                            </div>
                        </div>

                        <!-- Thanh tiến độ buổi học -->
                        <div class="progress-capsule">
                            <div class="progress-capsule-head">
                                <span class="fw-semibold text-secondary">Tiến độ khóa học:</span>
                                <span class="fw-bold text-success">${c.completedLessons || 0} / ${c.totalLessons || 0} buổi (${percent}%)</span>
                            </div>
                            <div class="progress-bar-bg">
                                <div class="progress-bar-val" style="width: ${percent}%;"></div>
                            </div>
                        </div>
                    </div>

                    <!-- Nút xem chi tiết -->
                    <div>
                        <button type="button" class="btn-view-class" onclick="viewClassDetail(${c.classId})">
                            <i class="bi bi-arrow-right-circle-fill"></i> Xem chi tiết & Học viên
                        </button>
                    </div>
                </div>
            </div>
        `;
    });
    html += '</div>';
    container.innerHTML = html;
}

async function viewClassDetail(classId) {
    if (!detailModalInstance) return;

    // Reset giao diện Modal
    document.getElementById('modalClassName').textContent = 'Đang tải thông tin lớp...';
    document.getElementById('modalClassCode').textContent = '—';
    document.getElementById('modalStudentsTableBody').innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4"><div class="spinner-border text-success spinner-border-sm me-2"></div>Đang tải danh sách học viên...</td></tr>';
    document.getElementById('modalLessonsTableBody').innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4"><div class="spinner-border text-success spinner-border-sm me-2"></div>Đang tải lịch trình buổi học...</td></tr>';

    detailModalInstance.show();

    try {
        const detail = await callApi(`/api/teacher/classes/${classId}`, 'GET');
        if (!detail || !detail.classOverview) throw new Error('Không thể lấy chi tiết lớp');

        const ov = detail.classOverview;
        const completedCount = (detail.lessons || []).filter(l => Boolean(l.attendanceCompleted || (l.presentCount + l.absentCount > 0))).length;
        const totalCount = (detail.lessons || []).length;
        const percent = totalCount > 0 ? Math.round((completedCount * 100) / totalCount) : 0;

        document.getElementById('modalClassName').textContent = ov.className;
        document.getElementById('modalClassCode').textContent = ov.classCode;
        document.getElementById('modalClassStatus').textContent = ov.status === 'OPEN' ? 'Đang học' : ov.status;
        document.getElementById('modalStudentCount').textContent = (detail.students || []).length;
        document.getElementById('modalLessonCount').textContent = `${completedCount}/${totalCount} buổi (${percent}%)`;

        // Render Danh sách Học viên & Phụ huynh
        renderModalStudents(detail.students || []);

        // Render Lịch trình Buổi học
        renderModalLessons(detail.lessons || [], (detail.students || []).length);

    } catch (err) {
        console.error('Lỗi nạp chi tiết lớp:', err);
        showToast('error', 'Lỗi', err.message || 'Không thể tải thông tin chi tiết lớp học');
    }
}

function renderModalStudents(students) {
    const tbody = document.getElementById('modalStudentsTableBody');
    if (!students || students.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-5"><i class="bi bi-person-x fs-2 d-block mb-2 text-muted"></i>Chưa có học viên nào ghi danh vào lớp này.</td></tr>';
        return;
    }

    tbody.innerHTML = students.map((s, idx) => {
        const dobStr = s.dateOfBirth ? fmtDMY(s.dateOfBirth) : '—';
        const genderBadge = s.gender === 'FEMALE' ? '<span class="badge bg-danger-subtle text-danger px-2 py-0.5 rounded-pill">Nữ</span>'
            : (s.gender === 'MALE' ? '<span class="badge bg-primary-subtle text-primary px-2 py-0.5 rounded-pill">Nam</span>' : '');

        const [c1, c2] = AVATAR_PALETTES[(s.studentId || idx) % AVATAR_PALETTES.length];
        const initials = getInitials(s.fullName || 'HV');

        return `
            <tr>
                <td class="text-center text-muted fw-bold">${idx + 1}</td>
                <td>
                    <div class="d-flex align-items-center gap-2.5">
                        <div class="student-avatar" style="background: linear-gradient(135deg, ${c1}, ${c2});">
                            ${initials}
                        </div>
                        <div>
                            <div class="fw-bold text-dark fs-6">${escapeHtml(s.fullName || 'Học viên')}</div>
                            <div class="small text-muted font-monospace">${s.schoolName ? escapeHtml(s.schoolName) : 'Mã: HV-' + s.studentId}</div>
                        </div>
                    </div>
                </td>
                <td>
                    <div class="fw-medium">${dobStr}</div>
                    <div class="mt-0.5">${genderBadge}</div>
                </td>
                <td>
                    <div class="fw-semibold text-dark">${escapeHtml(s.parentName || '—')}</div>
                    ${s.parentEmail ? `<div class="small text-muted">${escapeHtml(s.parentEmail)}</div>` : ''}
                </td>
                <td>
                    <!-- CHỈ HIỂN THỊ SĐT DẠNG TEXT RÕ RÀNG (KHÔNG CÓ NÚT GỌI) -->
                    <div class="phone-display-pill">
                        <i class="bi bi-telephone-fill"></i>
                        <span>${escapeHtml(s.parentPhone || 'Chưa cập nhật')}</span>
                    </div>
                </td>
                <td class="text-center">
                    <div class="d-inline-flex flex-wrap gap-1 justify-content-center">
                        <span class="att-stat-pill present" title="Số buổi có mặt">
                            <i class="bi bi-check-circle-fill"></i>${s.attendedLessons || 0} có mặt
                        </span>
                        ${(s.absentLessons > 0) ? `
                            <span class="att-stat-pill absent" title="Số buổi vắng">
                                <i class="bi bi-x-circle-fill"></i>${s.absentLessons} vắng
                            </span>
                        ` : ''}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

function renderModalLessons(lessons, totalClassStudents = 1) {
    const tbody = document.getElementById('modalLessonsTableBody');
    if (!lessons || lessons.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-5"><i class="bi bi-calendar-x fs-2 d-block mb-2 text-muted"></i>Chưa có lịch buổi học nào cho lớp này.</td></tr>';
        return;
    }

    const now = new Date();
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    const currentTimeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;

    tbody.innerHTML = lessons.map(l => {
        const lessonDateStr = l.lessonDate ? String(l.lessonDate) : '';
        const isToday = lessonDateStr === todayStr;
        const isPast = Boolean(lessonDateStr && lessonDateStr < todayStr);
        const isFuture = Boolean(lessonDateStr && lessonDateStr > todayStr);

        // Kiểm tra xem ca học hôm nay đã đến giờ bắt đầu chưa
        let slotStartTime = '';
        if (l.startTime) {
            slotStartTime = String(l.startTime).substring(0, 5);
        } else if (l.timeRange && l.timeRange.includes('-')) {
            slotStartTime = l.timeRange.split('-')[0].trim();
        }
        const isBeforeSlot = Boolean(isToday && slotStartTime && currentTimeStr < slotStartTime);

        // 1. CỘT TRẠNG THÁI CA HỌC (TÍNH CHUẨN XÁC THEO NGÀY HIỆN TẠI VÀ KHUNG GIỜ)
        let statusBadge = '<span class="badge bg-light text-muted border px-2.5 py-1 rounded-pill">Sắp diễn ra</span>';
        if (isToday) {
            if (isBeforeSlot) {
                statusBadge = `<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle px-2.5 py-1 rounded-pill fw-semibold" title="Ca học diễn ra lúc ${escapeHtml(l.timeRange || slotStartTime)}"><i class="bi bi-clock me-1"></i>Chưa vào ca (${escapeHtml(slotStartTime)})</span>`;
            } else {
                statusBadge = '<span class="badge bg-primary text-white px-2.5 py-1 rounded-pill fw-bold"><i class="bi bi-dot"></i>Hôm nay</span>';
            }
        } else if (isPast) {
            statusBadge = '<span class="badge bg-secondary-subtle text-secondary border px-2.5 py-1 rounded-pill">Đã kết thúc</span>';
        }

        // 2. CỘT ĐIỂM DANH: RÕ RÀNG (HIỆN BUỔI NÀO ĐIỂM DANH / BUỔI NÀO VẮNG, KHÔNG PHẢI "0 VẮNG, 1 CÓ MẶT")
        const hasAttendance = Boolean(l.attendanceCompleted || (l.presentCount + l.absentCount > 0));
        let attBadge = '<span class="badge bg-light text-secondary border px-2.5 py-1 rounded-pill">Chưa điểm danh</span>';

        if (hasAttendance) {
            if (l.absentCount > 0) {
                // Buổi có học viên vắng
                const absentLabel = (totalClassStudents <= 1) ? 'Vắng mặt' : `Vắng ${l.absentCount} học viên`;
                attBadge = `<span class="badge bg-danger-subtle text-danger border border-danger-subtle px-2.5 py-1 rounded-pill fw-bold"><i class="bi bi-x-circle-fill me-1"></i>${absentLabel}</span>`;
            } else {
                // Buổi đã điểm danh đầy đủ
                const presentLabel = (totalClassStudents <= 1) ? 'Có mặt' : 'Đã điểm danh';
                attBadge = `<span class="badge bg-success-subtle text-success border border-success-subtle px-2.5 py-1 rounded-pill fw-bold"><i class="bi bi-check-circle-fill me-1"></i>${presentLabel}</span>`;
            }
        } else if (isToday) {
            if (isBeforeSlot) {
                attBadge = `<span class="badge bg-light text-muted border px-2.5 py-1 rounded-pill" title="Chưa đến giờ ca học (${escapeHtml(l.timeRange || '')})"><i class="bi bi-clock-history me-1"></i>Chưa vào ca</span>`;
            } else {
                attBadge = '<span class="badge bg-warning-subtle text-warning-emphasis border border-warning px-2.5 py-1 rounded-pill fw-bold"><i class="bi bi-exclamation-circle me-1"></i>Chưa điểm danh</span>';
            }
        } else if (isPast) {
            attBadge = '<span class="badge bg-secondary-subtle text-secondary border px-2.5 py-1 rounded-pill">Chưa điểm danh</span>';
        } else {
            attBadge = '<span class="badge bg-light text-muted border px-2.5 py-1 rounded-pill">Chưa diễn ra</span>';
        }

        // 3. CỘT THAO TÁC: BUỔI ĐÃ ĐIỂM DANH HOẶC CHƯA VÀO CA/QUÁ KHỨ/TƯƠNG LAI THÌ CHỈ "XEM"
        // CHỈ BUỔI HÔM NAY ĐÃ ĐẾN GIỜ CA HỌC VÀ CHƯA ĐIỂM DANH MỚI CÓ NÚT "ĐIỂM DANH"
        let actionBtn = '';
        if (isToday && !hasAttendance) {
            if (isBeforeSlot) {
                // Ca học hôm nay nhưng CHƯA TỚI GIỜ (ví dụ đang 9h sáng mà ca học 13h) -> Nút Xem với chú thích
                actionBtn = `
                    <a href="lesson-attendance.html?lessonId=${l.lessonId}" class="btn btn-sm btn-outline-secondary rounded-pill px-3 py-1 fw-semibold" title="Chưa vào ca học (Ca học bắt đầu lúc ${escapeHtml(slotStartTime)}). Bấm để xem thông tin buổi học">
                        <i class="bi bi-eye me-1"></i> Xem
                    </a>
                `;
            } else {
                // Ca học hôm nay đã vào ca -> Nút xanh "Điểm danh"
                actionBtn = `
                    <a href="lesson-attendance.html?lessonId=${l.lessonId}" class="btn btn-sm btn-success rounded-pill px-3 py-1 fw-bold text-white shadow-sm" style="background: linear-gradient(135deg, #143c34, #1e5346);">
                        <i class="bi bi-check2-square me-1"></i> Điểm danh
                    </a>
                `;
            }
        } else if (hasAttendance) {
            // Buổi đã điểm danh rồi: Nút Xem
            actionBtn = `
                <a href="lesson-attendance.html?lessonId=${l.lessonId}" class="btn btn-sm btn-outline-secondary rounded-pill px-3 py-1 fw-semibold">
                    <i class="bi bi-eye me-1"></i> Xem
                </a>
            `;
        } else if (isPast) {
            // Buổi quá khứ chưa điểm danh: Bị khóa, chỉ xem
            actionBtn = `
                <a href="lesson-attendance.html?lessonId=${l.lessonId}" class="btn btn-sm btn-outline-secondary rounded-pill px-3 py-1 fw-semibold">
                    <i class="bi bi-eye me-1"></i> Xem
                </a>
            `;
        } else {
            // Buổi tương lai: Chỉ xem trước
            actionBtn = `
                <a href="lesson-attendance.html?lessonId=${l.lessonId}" class="btn btn-sm btn-light text-muted border rounded-pill px-3 py-1">
                    <i class="bi bi-eye me-1"></i> Xem
                </a>
            `;
        }

        return `
            <tr>
                <td class="text-center fw-bold text-secondary">
                    <span class="badge bg-light text-dark border">#${l.sessionNumber || '—'}</span>
                </td>
                <td>
                    <div class="fw-semibold text-dark"><i class="bi bi-calendar3 me-1.5 text-muted"></i>${fmtDMY(l.lessonDate)}</div>
                </td>
                <td>
                    <span class="small font-monospace fw-semibold text-primary"><i class="bi bi-clock me-1 text-muted"></i>${escapeHtml(l.timeRange || '—')}</span>
                </td>
                <td>
                    <span class="small text-muted"><i class="bi bi-geo-alt me-1 text-muted"></i>${escapeHtml(l.roomName || '—')}</span>
                </td>
                <td class="text-center">${statusBadge}</td>
                <td class="text-center">${attBadge}</td>
                <td class="text-end">${actionBtn}</td>
            </tr>
        `;
    }).join('');
}

function getStatusBadge(status) {
    if (status === 'OPEN') {
        return '<span class="status-pill open">Đang học</span>';
    } else if (status === 'PLANNING') {
        return '<span class="status-pill planning">Dự kiến</span>';
    } else if (status === 'COMPLETED') {
        return '<span class="status-pill completed">Đã xong</span>';
    }
    return `<span class="status-pill completed">${escapeHtml(status || '')}</span>`;
}

function getInitials(name) {
    if (!name) return 'HV';
    const words = name.replace(/\(.*?\)/g, '').trim().split(/\s+/);
    if (words.length === 1) return words[0].substring(0, 2).toUpperCase();
    return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

function fmtDMY(d) {
    if (!d) return '—';
    const p = String(d).split('-');
    return p.length === 3 ? `${p[2]}/${p[1]}/${p[0]}` : d;
}

function escapeHtml(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function showToast(type, title, message) {
    const container = document.getElementById('toastContainer');
    if (!container) {
        alert(`${title}: ${message}`);
        return;
    }
    const bg = type === 'success' ? 'bg-success' : type === 'error' ? 'bg-danger' : 'bg-primary';
    const el = document.createElement('div');
    el.className = `toast align-items-center text-white ${bg} border-0 shadow mb-2`;
    el.setAttribute('role', 'alert');
    el.innerHTML = `
        <div class="d-flex">
            <div class="toast-body"><strong>${escapeHtml(title)}:</strong> ${escapeHtml(message)}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
        </div>
    `;
    container.appendChild(el);
    const t = new bootstrap.Toast(el, { delay: 4000 });
    t.show();
    el.addEventListener('hidden.bs.toast', () => el.remove());
}
