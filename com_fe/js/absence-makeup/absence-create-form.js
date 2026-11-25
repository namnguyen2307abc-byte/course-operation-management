/**
 * =============================================================================
 * MODULE TAB 2: PHỤ HUYNH TẠO ĐƠN XIN NGHỈ HỌC (absence-create-form.js)
 * =============================================================================
 * Mục lục tra cứu nhanh:
 *   1. Chọn học viên & tải danh sách con: loadSelectableStudents, selectStudentCard
 *   2. Tải & chọn buổi học xin nghỉ: onStudentSelectChanged, selectLessonCard
 *   3. Kiểm tra & khóa quyền học bù (3/3): updateLeaveModeForLesson, selectLeaveMode
 *   4. Lý do nghỉ & gửi đơn lên API: selectReasonChip, onReasonInputChanged, submitAbsenceRequest
 */

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
    document.getElementById('createStudentError')?.classList.add('d-none');
    document.querySelectorAll('.student-pick-card').forEach(btn => {
        btn.classList.toggle('active', Number(btn.dataset.studentId) === Number(studentId));
    });
    onStudentSelectChanged();
}

function selectLessonCard(lessonId) {
    const lessonSelect = document.getElementById('createLessonSelect');
    if (lessonSelect) lessonSelect.value = String(lessonId || '');
    document.getElementById('createLessonError')?.classList.add('d-none');
    document.querySelectorAll('#lessonPickerList .lesson-pick-card').forEach(btn => {
        btn.classList.toggle('active', Number(btn.dataset.lessonId) === Number(lessonId));
    });
    const chosen = (currentSelectableLessons || []).find(l => Number(l.id) === Number(lessonId));
    updateLeaveModeForLesson(chosen || null);
}

function selectLeaveMode(needMakeup) {
    const radioMakeup = document.getElementById('leaveModeRadioMakeup');
    const radioOnly = document.getElementById('leaveModeRadioOnly');
    const cardMakeup = document.getElementById('leaveModeCardMakeup');
    const cardOnly = document.getElementById('leaveModeCardOnly');
    if (needMakeup && radioMakeup && radioMakeup.disabled) return;
    if (radioMakeup) radioMakeup.checked = Boolean(needMakeup);
    if (radioOnly) radioOnly.checked = !needMakeup;
    if (cardMakeup) cardMakeup.classList.toggle('active', Boolean(needMakeup));
    if (cardOnly) cardOnly.classList.toggle('active', !needMakeup);
}

/**
 * Kiểm tra hạn mức quyền học bù (tối đa 3 lượt/khóa) của buổi học đang chọn:
 * nếu đã dùng đủ 3/3 thì khóa chọn "Nghỉ & học bù" và tự chuyển sang "Chỉ xin nghỉ".
 */
function updateLeaveModeForLesson(lesson) {
    const radioMakeup = document.getElementById('leaveModeRadioMakeup');
    const cardMakeup = document.getElementById('leaveModeCardMakeup');
    const hintEl = document.getElementById('leaveModeMakeupHint');
    const warningBox = document.getElementById('studentMakeupWarningBox');

    if (!lesson) {
        if (radioMakeup) radioMakeup.disabled = false;
        if (cardMakeup) cardMakeup.classList.remove('disabled');
        if (hintEl) {
            hintEl.textContent = '';
            hintEl.classList.add('d-none');
        }
        if (warningBox) {
            warningBox.className = 'd-none';
            warningBox.innerHTML = '';
        }
        selectLeaveMode(true);
        return;
    }

    const used = Number(lesson.courseMakeupUsed ?? 0);
    const limit = Number(lesson.makeupLimit ?? 3);
    const available = lesson.makeupAvailable !== false && used < limit;
    const courseLabel = lesson.courseName ? `Khóa ${lesson.courseName}` : 'Khóa hiện tại';

    if (!available) {
        if (radioMakeup) radioMakeup.disabled = true;
        if (cardMakeup) cardMakeup.classList.add('disabled');
        if (hintEl) {
            hintEl.textContent = `Đã hết ${used}/${limit} lượt bù`;
            hintEl.classList.remove('d-none');
        }
        selectLeaveMode(false);
        if (warningBox) {
            warningBox.className = 'd-block';
            warningBox.innerHTML = `
                <div class="makeup-limit-alert">
                    <i class="bi bi-info-circle-fill"></i>
                    <div>
                        <div class="makeup-limit-alert-title">${escapeHtml(courseLabel)} đã hết lượt học bù (${used}/${limit})</div>
                        <p class="makeup-limit-alert-text">Chỉ có thể gửi đơn ở chế độ Chỉ xin nghỉ.</p>
                    </div>
                </div>
            `;
        }
    } else {
        if (radioMakeup) radioMakeup.disabled = false;
        if (cardMakeup) cardMakeup.classList.remove('disabled');
        if (hintEl) {
            hintEl.textContent = '';
            hintEl.classList.add('d-none');
        }
        if (warningBox) {
            warningBox.className = 'd-none';
            warningBox.innerHTML = '';
        }
        selectLeaveMode(true);
    }
}

function selectReasonChip(btn, text) {
    document.querySelectorAll('.reason-chip').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    const input = document.getElementById('createReasonInput');
    if (!input) return;
    if (text) {
        input.value = text;
        input.dataset.chipPreset = text;
    } else if (input.dataset.chipPreset && input.value === input.dataset.chipPreset) {
        input.value = '';
        delete input.dataset.chipPreset;
    }
    document.getElementById('createReasonError')?.classList.add('d-none');
    updateReasonCharCount();
    input.focus();
}

function onReasonInputChanged() {
    updateReasonCharCount();
    const input = document.getElementById('createReasonInput');
    if (!input) return;
    if (input.value.trim()) {
        document.getElementById('createReasonError')?.classList.add('d-none');
    }
    if (input.dataset.chipPreset && input.value !== input.dataset.chipPreset) {
        document.querySelectorAll('.reason-chip').forEach(b => b.classList.remove('active'));
        delete input.dataset.chipPreset;
    }
}

function updateReasonCharCount() {
    const input = document.getElementById('createReasonInput');
    const counter = document.getElementById('reasonCharCount');
    if (input && counter) {
        counter.textContent = `${input.value.length} / 1000`;
    }
}

async function loadSelectableStudents() {
    try {
        const select = document.getElementById('createStudentSelect');
        const prevSelectedId = select ? select.value : '';
        const data = await callApi('/api/attendance-makeup/students', 'GET', null, false, false);
        currentStudents = data || [];

        const grid = document.getElementById('studentPickerGrid');
        if (!select) return;

        if (currentStudents.length === 0) {
            select.innerHTML = '<option value="">Không có học viên nào</option>';
            if (grid) grid.innerHTML = '<div class="lesson-empty-box w-100">Chưa có học viên liên kết</div>';
            return;
        }

        select.innerHTML = '<option value="">-- Chọn học viên --</option>' +
            currentStudents.map(s => `
                <option value="${s.id}">
                    ${escapeHtml(s.fullName)} (${escapeHtml(s.currentClassName || 'Chưa xếp lớp')})
                </option>
            `).join('');

        if (grid) {
            if (currentStudents.length > 6) {
                grid.classList.add('d-none');
                select.classList.remove('d-none');
            } else {
                grid.classList.remove('d-none');
                select.classList.add('d-none');
                grid.innerHTML = currentStudents.map(s => {
                    const extraInfo = [s.currentClassName || 'Chưa xếp lớp', s.schoolName].filter(Boolean).join(' • ');
                    return `
                        <button type="button" class="student-pick-card" data-student-id="${s.id}" onclick="selectStudentCard(${s.id})">
                            <div class="student-pick-avatar">${escapeHtml(getInitials(s.fullName))}</div>
                            <div class="min-w-0 flex-grow-1">
                                <div class="student-pick-name">${escapeHtml(s.fullName)}</div>
                                <div class="student-pick-class">${escapeHtml(extraInfo)}</div>
                            </div>
                        </button>
                    `;
                }).join('');
            }
        }

        const hasPrev = prevSelectedId && currentStudents.some(s => String(s.id) === String(prevSelectedId));
        if (hasPrev) {
            selectStudentCard(prevSelectedId);
        } else if (currentUser && currentUser.role === 'PARENT' && currentStudents.length >= 1) {
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
    currentSelectableLessons = [];
    updateLeaveModeForLesson(null);

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
        const rawLessons = await callApi(`/api/attendance-makeup/students/${studentId}/lessons`, 'GET', null, false, false);
        const now = new Date();
        const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
        const cutoffMs = now.getTime() + 2 * 60 * 60 * 1000;

        const lessons = (rawLessons || []).filter(l => {
            if (!l.lessonDate) return false;
            const startTimeStr = (l.startTime || (l.timeSlot ? l.timeSlot.split('-')[0].trim() : '')).substring(0, 5);
            if (!startTimeStr || !/^\d{2}:\d{2}$/.test(startTimeStr)) {
                return l.lessonDate > todayStr;
            }
            const startDt = new Date(`${l.lessonDate}T${startTimeStr}:00`);
            return !isNaN(startDt.getTime()) && startDt.getTime() >= cutoffMs;
        });
        currentSelectableLessons = lessons;

        if (!lessons || lessons.length === 0) {
            if (lessonSelect) {
                lessonSelect.innerHTML = '<option value="">Chưa có buổi học nào</option>';
                lessonSelect.disabled = true;
            }
            if (lessonCountEl) lessonCountEl.textContent = '0 buổi';
            if (lessonList) lessonList.innerHTML = '<div class="lesson-empty-box">Chưa có buổi học phù hợp (phải gửi đơn trước giờ học tối thiểu 2 tiếng)</div>';
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
                const courseStr = l.courseName ? cleanTitle(l.courseName) : '';
                const branchStr = l.branchName ? cleanTitle(l.branchName) : '';
                const roomStr = cleanTitle(l.roomName || 'Phòng học');
                const teacherStr = l.teacherName || '';
                const used = l.courseMakeupUsed ?? 0;
                const limit = l.makeupLimit ?? 3;
                const quotaPill = l.makeupAvailable === false
                    ? `<span class="badge bg-danger-subtle text-danger border border-danger-subtle ms-2" style="font-size:0.68rem;">Hết bù ${used}/${limit}</span>`
                    : `<span class="badge bg-light text-secondary border ms-2" style="font-size:0.68rem;">Bù: ${used}/${limit}</span>`;
                return `
                    <button type="button" class="lesson-pick-card" data-lesson-id="${l.id}" onclick="selectLessonCard(${l.id})">
                        <div class="lesson-pick-datebox">
                            <div class="lesson-pick-dow">${escapeHtml(dt.dow)}</div>
                            <div class="lesson-pick-day">${escapeHtml(dt.day)}</div>
                        </div>
                        <div class="lesson-pick-info">
                            <div class="d-flex align-items-center justify-content-between gap-2">
                                <div class="lesson-pick-title">${escapeHtml(titleStr)}</div>
                                ${quotaPill}
                            </div>
                            <div class="lesson-pick-meta">
                                <span><i class="bi bi-clock me-1"></i>${escapeHtml(timeStr)}</span>
                                <span><i class="bi bi-easel me-1"></i>${escapeHtml(classStr)}${courseStr ? ` (${escapeHtml(courseStr)})` : ''}</span>
                                <span><i class="bi bi-door-open me-1"></i>${escapeHtml(roomStr)}${branchStr ? ` • ${escapeHtml(branchStr)}` : ''}</span>
                                ${teacherStr ? `<span><i class="bi bi-person me-1"></i>${escapeHtml(teacherStr)}</span>` : ''}
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
    if (!ensureSessionMatchesTab()) return;
    const studentId = document.getElementById('createStudentSelect')?.value;
    const lessonId = document.getElementById('createLessonSelect')?.value;
    const reasonEl = document.getElementById('createReasonInput');
    const reason = (reasonEl?.value || '').trim();
    const needMakeup = document.querySelector('input[name="leaveNeedMakeup"]:checked')?.value !== 'false';

    document.getElementById('createStudentError')?.classList.toggle('d-none', Boolean(studentId));
    document.getElementById('createLessonError')?.classList.toggle('d-none', Boolean(lessonId));
    document.getElementById('createReasonError')?.classList.toggle('d-none', Boolean(reason));

    if (!studentId || !lessonId || !reason) {
        showToast('danger', 'Vui lòng chọn học viên, buổi học và nhập lý do!');
        if (studentId && lessonId && !reason && reasonEl) reasonEl.focus();
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
            reason: reason,
            needMakeup: needMakeup
        }, false, false);

        showToast('success', needMakeup ? 'Đã gửi đơn xin nghỉ kèm học bù.' : 'Đã gửi đơn xin nghỉ.');
        const reasonInput = document.getElementById('createReasonInput');
        if (reasonInput) {
            reasonInput.value = '';
            delete reasonInput.dataset.chipPreset;
        }
        document.querySelectorAll('.reason-chip, .lesson-pick-card').forEach(b => b.classList.remove('active'));
        const lessonSelect = document.getElementById('createLessonSelect');
        if (lessonSelect) lessonSelect.value = '';
        updateLeaveModeForLesson(null);
        updateReasonCharCount();

        await reloadData();
        if (window.refreshNotifications) window.refreshNotifications();
        switchMainTab('ABSENCE');
    } catch (err) {
        showToast('danger', 'Không thể gửi đơn xin nghỉ: ' + err.message);
    } finally {
        if (btnSubmit) {
            btnSubmit.disabled = false;
            btnSubmit.innerHTML = '<i class="bi bi-send-fill me-1"></i>Gửi Đơn Xin Nghỉ';
        }
    }
}
