const fs = require('fs');
const file = 'com_fe/pages/absence-makeup.html';
let content = fs.readFileSync(file, 'utf8');

// 1. Thêm dropdown filterMakeupDirection vào bộ lọc học bù
const oldFilterBlock = `<div class="col-md-4">
                        <label class="form-label small fw-semibold text-secondary mb-1">
                            <i class="bi bi-funnel me-1 text-primary"></i>Trạng Thái Ca Bù:
                        </label>
                        <select id="filterMakeupStatus" class="form-select form-control-modern" onchange="renderMakeupTable(true)">`;

const newFilterBlock = `<div class="col-md-3">
                        <label class="form-label small fw-semibold text-secondary mb-1">
                            <i class="bi bi-arrow-left-right me-1 text-success"></i>Điều Phối Học Bù:
                        </label>
                        <select id="filterMakeupDirection" class="form-select form-control-modern" onchange="renderMakeupTable(true)">
                            <option value="" selected>Tất Cả Ca Bù</option>
                            <option value="OUT">↗ Học viên lớp tôi sang lớp khác</option>
                            <option value="IN">↙ Học viên ngoài bù vào lớp tôi</option>
                        </select>
                    </div>
                    <div class="col-md-3">
                        <label class="form-label small fw-semibold text-secondary mb-1">
                            <i class="bi bi-funnel me-1 text-primary"></i>Trạng Thái Ca Bù:
                        </label>
                        <select id="filterMakeupStatus" class="form-select form-control-modern" onchange="renderMakeupTable(true)">`;

content = content.replace(oldFilterBlock, newFilterBlock);
content = content.replace('<div class="col-md-5">', '<div class="col-md-4">');
content = content.replace('<div class="col-md-3 text-md-end pt-md-4">', '<div class="col-md-2 text-md-end pt-md-4">');

// 2. Cập nhật renderMakeupTable
const oldRenderHead = `            const statusFilter = document.getElementById('filterMakeupStatus').value;
            const searchKeyword = (document.getElementById('searchMakeupInput').value || '').trim().toLowerCase();

            let filtered = currentMakeupList;
            if (statusFilter) {
                filtered = filtered.filter(m => m.status === statusFilter);
            }`;

const newRenderHead = `            const statusFilter = document.getElementById('filterMakeupStatus').value;
            const dirFilter = document.getElementById('filterMakeupDirection') ? document.getElementById('filterMakeupDirection').value : '';
            const searchKeyword = (document.getElementById('searchMakeupInput').value || '').trim().toLowerCase();
            const myId = currentUser ? currentUser.id : null;

            let filtered = currentMakeupList;
            if (dirFilter === 'OUT' && myId) {
                filtered = filtered.filter(m => m.originalTeacherId === myId && m.targetTeacherId !== myId);
            } else if (dirFilter === 'IN' && myId) {
                filtered = filtered.filter(m => m.targetTeacherId === myId && m.originalTeacherId !== myId);
            }
            if (statusFilter) {
                filtered = filtered.filter(m => m.status === statusFilter);
            }`;

content = content.replace(oldRenderHead, newRenderHead);

// 3. Cập nhật hiển thị dòng trong renderMakeupTable
const oldRowStudent = `                        <td>
                            <div class="fw-bold text-dark" style="font-size: 0.88rem;">\${item.studentName || 'Học viên'}</div>
                        </td>`;

const newRowStudent = `                        <td>
                            \${(() => {
                                const isOut = myId && item.originalTeacherId === myId && item.targetTeacherId !== myId;
                                const isIn = myId && item.targetTeacherId === myId && item.originalTeacherId !== myId;
                                let tag = '';
                                if (isOut) {
                                    tag = '<span class=\"badge bg-primary-subtle text-primary border border-primary-subtle px-2 py-0.5 rounded-pill mb-1 d-inline-block\" style=\"font-size: 0.68rem;\"><i class=\"bi bi-box-arrow-up-right me-1\"></i>Lớp tôi sang lớp khác</span>';
                                } else if (isIn) {
                                    tag = '<span class=\"badge bg-success-subtle text-success border border-success-subtle px-2 py-0.5 rounded-pill mb-1 d-inline-block\" style=\"font-size: 0.68rem;\"><i class=\"bi bi-box-arrow-in-down-left me-1\"></i>Ngoài bù vào lớp tôi</span>';
                                }
                                return tag;
                            })()}
                            <div class="fw-bold text-dark" style="font-size: 0.88rem;">\${item.studentName || 'Học viên'}</div>
                        </td>`;

content = content.replace(oldRowStudent, newRowStudent);

// 4. Cập nhật hiển thị GV ở ô Buổi học gốc
const oldOrigCell = `                        <td>
                            <div class="fw-semibold text-secondary text-truncate" style="max-width: 250px;">
                                \${escapeHtml(item.originalClassName || 'Lớp học')}
                            </div>
                            <div class="text-muted mt-1 d-flex align-items-center gap-2" style="font-size: 0.76rem;">
                                <span class="badge bg-light text-dark border px-1.5 py-0.5 font-monospace" style="font-size: 0.72rem;">
                                    <i class="bi bi-calendar3 me-1 text-secondary"></i>\${origDateStr}
                                </span>
                                <span class="text-secondary"><i class="bi bi-clock me-1"></i>\${origTimeStr}</span>
                            </div>
                        </td>`;

const newOrigCell = `                        <td>
                            <div class="fw-semibold text-secondary text-truncate" style="max-width: 250px;">
                                \${escapeHtml(item.originalClassName || 'Lớp học')}
                            </div>
                            <div class="text-muted mt-1 d-flex align-items-center gap-2" style="font-size: 0.76rem;">
                                <span class="badge bg-light text-dark border px-1.5 py-0.5 font-monospace" style="font-size: 0.72rem;">
                                    <i class="bi bi-calendar3 me-1 text-secondary"></i>\${origDateStr}
                                </span>
                                <span class="text-secondary"><i class="bi bi-clock me-1"></i>\${origTimeStr}</span>
                            </div>
                            <div class="text-muted mt-0.5 text-truncate" style="font-size: 0.72rem; max-width: 240px;">
                                <i class="bi bi-person me-1 text-secondary"></i>GV: <strong>\${escapeHtml(item.originalTeacherName || 'Chưa phân công')}</strong>
                            </div>
                        </td>`;

content = content.replace(oldOrigCell, newOrigCell);

fs.writeFileSync(file, content, 'utf8');
console.log('Successfully updated absence-makeup.html');
