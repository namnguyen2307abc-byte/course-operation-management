const facilityElement = id => document.getElementById(id);
const roomTypes = { PIANO_INDIVIDUAL: 'Piano cá nhân', PIANO_GROUP: 'Piano nhóm', DANCE_ROOM: 'Phòng múa', THEORY_ROOM: 'Phòng lý thuyết', GUITAR_ROOM: 'Phòng guitar', MULTIPURPOSE_ROOM: 'Phòng đa năng', CHESS_ROOM: 'Phòng cờ vua', SPORTS_AREA: 'Khu thể thao', SWIMMING_POOL: 'Bể bơi' };
const equipmentTypes = { GRAND_PIANO: 'Đại dương cầm', UPRIGHT_PIANO: 'Piano cơ', KEYBOARD: 'Đàn phím', GUITAR: 'Đàn guitar', AUDIO: 'Âm thanh', MIRROR: 'Gương', CHESS_SET: 'Bộ cờ vua', SPORTS_EQUIPMENT: 'Dụng cụ thể thao', RESCUE_EQUIPMENT: 'Dụng cụ cứu hộ', OTHER: 'Khác' };
const roomStatuses = { AVAILABLE: 'Sẵn sàng', OCCUPIED: 'Đang sử dụng', MAINTENANCE: 'Bảo trì' };
const equipmentStatuses = { GOOD: 'Hoạt động tốt', MAINTENANCE: 'Bảo trì', BROKEN: 'Hỏng' };
const classStatuses = { PLANNING: 'Đang lên kế hoạch', OPEN: 'Đang tuyển sinh', IN_PROGRESS: 'Đang học', FINISHED: 'Đã kết thúc' };
let branches = [];
let rooms = [];
let equipments = [];
let branchRequest = 0;
let roomRequest = 0;
let equipmentRequest = 0;
let usageRequest = 0;
let editing = null;
let usageRoomId = null;
let usageSelectsBranch = false;
let initialRoomId = new URLSearchParams(window.location.search).get('roomId');
let returnToEditor = false;
let savingFacility = false;
const editElement = facilityElement('editFacilityModal');
const usageElement = facilityElement('roomUsageModal');
const editModal = new bootstrap.Modal(editElement);
const usageModal = new bootstrap.Modal(usageElement);

function canEditFacility() { return getCurrentUser()?.role === 'ADMIN'; }
function canViewRoomUsage() { return ['ADMIN', 'STAFF', 'BRANCH_MANAGER', 'TEACHER'].includes(getCurrentUser()?.role); }
function canManageClasses() { return ['ADMIN', 'STAFF', 'BRANCH_MANAGER'].includes(getCurrentUser()?.role); }
function selectedBranchId() { return facilityElement('branchSelect').value; }
function escapeHtml(value) {
    return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#039;');
}

// Keep errors local: the shared helper redirects on 403 and drops the HTTP status.
async function facilityRequest(endpoint, method = 'GET', payload = null) {
    const token = localStorage.getItem('token');
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    let response;
    try {
        response = await fetch(BASE_URL + endpoint, { method, headers, body: payload === null ? null : JSON.stringify(payload) });
    } catch {
        throw new Error('Không kết nối được máy chủ. Vui lòng thử lại.');
    }
    const data = await response.json().catch(() => null);
    if (!response.ok) {
        const fallback = response.status === 403 ? 'Bạn không có quyền xem hoặc sửa dữ liệu này.'
            : response.status === 401 ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' : 'Không tải hoặc lưu được dữ liệu.';
        const error = new Error(data?.message || fallback);
        error.status = response.status;
        throw error;
    }
    return data;
}

function statusBadge(status, labels) {
    const color = ['AVAILABLE', 'GOOD', 'FINISHED'].includes(status) ? 'success'
        : status === 'MAINTENANCE' ? 'warning' : ['BROKEN', 'CANCELLED'].includes(status) ? 'danger' : 'secondary';
    return `<span class="badge bg-${color}-subtle text-${color} border">${escapeHtml(labels[status] || status || 'Chưa rõ')}</span>`;
}

function warningList(warnings) {
    return Array.isArray(warnings) && warnings.length
        ? `<ul class="facility-warnings">${warnings.map(warning => `<li>${escapeHtml(warning)}</li>`).join('')}</ul>` : '';
}

function showFacilityNotice(message, warnings = []) {
    facilityElement('facilityNotice').innerHTML = `<div class="alert alert-success" role="status">${escapeHtml(message)}</div>`
        + (warnings.length ? `<div class="alert alert-warning" role="alert">${warningList(warnings)}</div>` : '');
}

function tableMessage(bodyId, columns, message, retry) {
    facilityElement(bodyId).innerHTML = `<tr><td colspan="${columns}" class="text-center py-4 text-muted">${escapeHtml(message)}
        ${retry ? `<button type="button" class="btn btn-sm btn-outline-secondary ms-2" data-facility-retry="${retry}"><i class="bi bi-arrow-clockwise me-1" aria-hidden="true"></i>Thử lại</button>` : ''}</td></tr>`;
}

async function loadBranches() {
    const request = ++branchRequest;
    const select = facilityElement('branchSelect');
    select.disabled = true;
    select.innerHTML = '<option value="">Đang tải danh sách cơ sở...</option>';
    facilityElement('facilityLoadError').classList.add('d-none');
    try {
        const result = await facilityRequest('/api/branches');
        if (request !== branchRequest) return;
        if (!Array.isArray(result)) throw new Error('Danh sách cơ sở không hợp lệ.');
        branches = result;
        select.innerHTML = branches.length ? branches.map(branch => `<option value="${escapeHtml(branch.id)}">${escapeHtml(branch.name)} (${escapeHtml(branch.address || '')})</option>`).join('')
            : '<option value="">Không có chi nhánh</option>';
        facilityElement('statBranchesCount').textContent = String(branches.length);
        loadCurrentTabData();
        if (initialRoomId) {
            const id = initialRoomId;
            initialRoomId = null;
            openRoomUsage(id, false, true);
        }
    } catch (error) {
        if (request !== branchRequest) return;
        select.innerHTML = '<option value="">Không tải được cơ sở</option>';
        const alert = facilityElement('facilityLoadError');
        alert.innerHTML = `${escapeHtml(error.message)} <button type="button" class="btn btn-sm btn-outline-danger" data-facility-retry="branches">Thử lại</button>`;
        alert.classList.remove('d-none');
        tableMessage('roomTableBody', 7, 'Không tải được danh sách cơ sở.');
        tableMessage('equipmentTableBody', 8, 'Không tải được danh sách cơ sở.');
    } finally {
        if (request === branchRequest) select.disabled = !branches.length;
    }
}

function switchTab(tab) {
    const showRooms = tab === 'ROOMS';
    facilityElement('tabRooms').classList.toggle('active', showRooms);
    facilityElement('tabEquipments').classList.toggle('active', !showRooms);
    facilityElement('tabRooms').setAttribute('aria-pressed', String(showRooms));
    facilityElement('tabEquipments').setAttribute('aria-pressed', String(!showRooms));
    facilityElement('roomsView').classList.toggle('d-none', !showRooms);
    facilityElement('equipmentsView').classList.toggle('d-none', showRooms);
}

function onBranchChange() {
    returnToEditor = false;
    usageRoomId = null;
    usageSelectsBranch = false;
    ++usageRequest;
    editing = null;
    editModal.hide();
    usageModal.hide();
    facilityElement('facilityNotice').replaceChildren();
    loadCurrentTabData();
}

function reloadData() {
    if (selectedBranchId()) loadCurrentTabData();
    else loadBranches();
}

function loadCurrentTabData() {
    const branchId = selectedBranchId();
    if (!branchId) {
        ++roomRequest;
        ++equipmentRequest;
        rooms = [];
        equipments = [];
        tableMessage('roomTableBody', 7, 'Không có phòng học.');
        tableMessage('equipmentTableBody', 8, 'Không có thiết bị.');
        for (const id of ['statRoomsCount', 'statEquipmentCount', 'statGoodCount']) facilityElement(id).textContent = '0';
        return;
    }
    loadRooms(branchId);
    loadEquipments(branchId);
}

function roomActions(room) {
    const view = canViewRoomUsage() ? `<button type="button" class="btn btn-sm btn-outline-secondary" data-room-usage="${escapeHtml(room.id)}" title="Xem chi tiết & lịch sử dụng" aria-label="Xem chi tiết phòng ${escapeHtml(room.roomName)}"><i class="bi bi-eye" aria-hidden="true"></i></button>` : '';
    const edit = canEditFacility() ? `<button type="button" class="btn btn-sm btn-outline-primary" data-edit-room="${escapeHtml(room.id)}" title="Sửa phòng" aria-label="Sửa phòng ${escapeHtml(room.roomName)}"><i class="bi bi-pencil" aria-hidden="true"></i></button>` : '';
    return `<div class="facility-actions">${view}${edit}</div>`;
}

async function loadRooms(branchId) {
    const request = ++roomRequest;
    rooms = [];
    facilityElement('roomCountBadge').textContent = 'Đang tải...';
    facilityElement('statRoomsCount').textContent = '--';
    tableMessage('roomTableBody', 7, 'Đang tải danh sách phòng học...');
    try {
        const result = await facilityRequest(`/api/branches/${encodeURIComponent(branchId)}/rooms`);
        if (request !== roomRequest || selectedBranchId() !== branchId) return;
        if (!Array.isArray(result)) throw new Error('Danh sách phòng học không hợp lệ.');
        rooms = result;
        facilityElement('roomCountBadge').textContent = `${rooms.length} Phòng học`;
        facilityElement('statRoomsCount').textContent = String(rooms.length);
        const branch = branches.find(item => String(item.id) === branchId);
        facilityElement('roomsSubtitle').textContent = `Danh sách phòng học tại ${branch?.name || ''}`;
        if (!rooms.length) return tableMessage('roomTableBody', 7, 'Chưa có phòng học nào tại cơ sở này');
        facilityElement('roomTableBody').innerHTML = rooms.map(room => `<tr>
            <td><code class="fw-bold">${escapeHtml(room.roomCode)}</code></td>
            <td class="fw-bold text-dark">${escapeHtml(room.roomName)}</td>
            <td>${escapeHtml(roomTypes[room.roomType] || room.roomType)}</td>
            <td><strong>${Number(room.capacity) || 0}</strong> học viên</td>
            <td>${statusBadge(room.status, roomStatuses)}</td>
            <td class="small"><div class="text-muted">${escapeHtml(room.description || '—')}</div>
                ${Number.isInteger(room.totalEquipmentCount) ? `<div class="mt-1">Thiết bị tốt: ${Number(room.goodEquipmentCount) || 0}/${room.totalEquipmentCount}</div>` : ''}
                <div class="text-warning-emphasis mt-1">${warningList(room.warnings)}</div></td>
            <td>${roomActions(room)}</td></tr>`).join('');
    } catch (error) {
        if (request !== roomRequest || selectedBranchId() !== branchId) return;
        facilityElement('roomCountBadge').textContent = 'Chưa tải được';
        tableMessage('roomTableBody', 7, error.message, 'rooms');
    }
}

async function loadEquipments(branchId) {
    const request = ++equipmentRequest;
    equipments = [];
    facilityElement('equipmentCountBadge').textContent = 'Đang tải...';
    facilityElement('statEquipmentCount').textContent = '--';
    facilityElement('statGoodCount').textContent = '--';
    tableMessage('equipmentTableBody', 8, 'Đang tải danh sách thiết bị...');
    try {
        const result = await facilityRequest(`/api/branches/${encodeURIComponent(branchId)}/equipments`);
        if (request !== equipmentRequest || selectedBranchId() !== branchId) return;
        if (!Array.isArray(result)) throw new Error('Danh sách thiết bị không hợp lệ.');
        equipments = result;
        facilityElement('equipmentCountBadge').textContent = `${equipments.length} Thiết bị`;
        facilityElement('statEquipmentCount').textContent = String(equipments.length);
        facilityElement('statGoodCount').textContent = `${equipments.filter(item => item.status === 'GOOD').length}/${equipments.length}`;
        const branch = branches.find(item => String(item.id) === branchId);
        facilityElement('equipmentsSubtitle').textContent = `Thiết bị tại ${branch?.name || ''}`;
        if (!equipments.length) return tableMessage('equipmentTableBody', 8, 'Chưa có thiết bị nào tại cơ sở này');
        facilityElement('equipmentTableBody').innerHTML = equipments.map(item => `<tr>
            <td><code>${escapeHtml(item.code || '—')}</code></td><td class="fw-bold text-dark">${escapeHtml(item.name)}</td>
            <td>${escapeHtml(equipmentTypes[item.category] || item.category)}</td><td>${escapeHtml(item.roomName)}</td>
            <td><code class="text-muted">${escapeHtml(item.serialNumber || '—')}</code></td><td>${statusBadge(item.status, equipmentStatuses)}</td>
            <td class="text-muted small">${escapeHtml(item.description || '—')}</td>
            <td><div class="facility-actions">${canEditFacility() ? `<button type="button" class="btn btn-sm btn-outline-primary" data-edit-equipment="${escapeHtml(item.id)}" title="Sửa thiết bị" aria-label="Sửa thiết bị ${escapeHtml(item.name)}"><i class="bi bi-pencil" aria-hidden="true"></i></button>` : ''}</div></td>
            </tr>`).join('');
    } catch (error) {
        if (request !== equipmentRequest || selectedBranchId() !== branchId) return;
        facilityElement('equipmentCountBadge').textContent = 'Chưa tải được';
        tableMessage('equipmentTableBody', 8, error.message, 'equipments');
    }
}

function usageTable(headers, rows, emptyMessage) {
    if (!rows.length) return `<p class="text-muted mb-0">${emptyMessage}</p>`;
    return `<div class="table-responsive"><table class="table table-sm align-middle mb-0"><thead class="table-light"><tr>${headers.map(label => `<th scope="col">${label}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`;
}

function classReference(item) {
    const label = `${item.classCode || ''}${item.classCode && item.className ? ' · ' : ''}${item.className || ''}` || 'Lớp học';
    const id = item.classId ?? item.id;
    return canManageClasses() && id != null
        ? `<a href="${appPath('/pages/class-management.html')}?classId=${encodeURIComponent(id)}">${escapeHtml(label)}</a>` : escapeHtml(label);
}

function lessonDateLabel(value) {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value || '');
    return match ? `${match[3]}/${match[2]}/${match[1]}` : (value || '—');
}

function renderRoomUsage(data) {
    const room = data.room;
    const equipmentRows = (data.equipments || []).map(item => `<tr><td>${escapeHtml(item.code || '—')}</td><td>${escapeHtml(item.name)}</td>
        <td>${escapeHtml(equipmentTypes[item.category] || item.category)}</td><td>${statusBadge(item.status, equipmentStatuses)}</td>
        <td>${escapeHtml(item.serialNumber || '—')}</td><td>${escapeHtml(item.description || '—')}</td></tr>`);
    const classRows = (data.classes || []).map(item => `<tr><td>${classReference(item)}</td><td>${escapeHtml(item.courseName || '—')}</td>
        <td>${Number(item.maxStudents) || 0}</td><td>${statusBadge(item.status, classStatuses)}</td></tr>`);
    const lessons = [...(data.upcomingLessons || [])].sort((a, b) => `${a.lessonDate} ${a.startTime}`.localeCompare(`${b.lessonDate} ${b.startTime}`));
    const lessonRows = lessons.map(item => `<tr><td>${escapeHtml(lessonDateLabel(item.lessonDate))}</td>
        <td class="text-nowrap">${escapeHtml(item.startTime?.slice(0, 5) || '—')} – ${escapeHtml(item.endTime?.slice(0, 5) || '—')}</td>
        <td>${classReference({ ...item, id: item.classId })}</td><td>${escapeHtml(item.teacherName || 'Chưa phân công')}</td></tr>`);
    const total = Number.isInteger(data.upcomingLessonCount) ? data.upcomingLessonCount : lessons.length;
    facilityElement('roomUsageTitle').textContent = `${room.roomCode || ''} · ${room.roomName || 'Chi tiết phòng'}`;
    facilityElement('roomUsageBody').innerHTML = `<section aria-label="Thông tin phòng">
        <div class="d-flex flex-wrap gap-2 align-items-center mb-2">${statusBadge(room.status, roomStatuses)}<span>${escapeHtml(roomTypes[room.roomType] || room.roomType)}</span><span>· ${Number(room.capacity) || 0} học viên</span></div>
        <p class="text-muted mb-0">${escapeHtml(room.description || 'Chưa có mô tả.')}</p></section>
        ${warningList(data.warnings) ? `<div class="alert alert-warning mt-3" role="alert"><strong>Cảnh báo</strong>${warningList(data.warnings)}</div>` : ''}
        <section><h6>Thiết bị trong phòng (${equipmentRows.length})</h6>${usageTable(['Mã', 'Thiết bị', 'Phân loại', 'Tình trạng', 'Số sê-ri', 'Mô tả'], equipmentRows, 'Phòng chưa có thiết bị.')}</section>
        <section><h6>Lớp đang sử dụng phòng (${classRows.length})</h6>${usageTable(['Lớp', 'Khóa học', 'Sĩ số tối đa', 'Trạng thái'], classRows, 'Chưa có lớp đang sử dụng phòng.')}</section>
        <section><h6>Buổi học sắp tới (${total})</h6>${usageTable(['Ngày', 'Giờ học', 'Lớp', 'Giáo viên'], lessonRows, 'Chưa có buổi học sắp tới.')}
        ${total > lessons.length ? `<p class="small text-muted mt-2 mb-0">Hiển thị ${lessons.length}/${total} buổi học.</p>` : ''}</section>`;
}

async function loadRoomUsage(id) {
    const request = ++usageRequest;
    let branchId = selectedBranchId();
    const selectRoomBranch = usageSelectsBranch;
    facilityElement('roomUsageBody').innerHTML = '<p class="text-muted" role="status">Đang tải chi tiết phòng & lịch sử dụng...</p>';
    try {
        if (!canViewRoomUsage()) throw new Error('Bạn không có quyền xem lịch sử dụng phòng.');
        const data = await facilityRequest(`/api/branches/rooms/${encodeURIComponent(id)}/usage`);
        if (request !== usageRequest || usageRoomId !== id || branchId !== selectedBranchId()) return;
        if (!data?.room) throw new Error('Không tải được thông tin phòng.');
        if (selectRoomBranch) {
            const roomBranchId = String(data.room.branchId ?? '');
            if (!branches.some(branch => String(branch.id) === roomBranchId)) {
                throw new Error('Cơ sở của phòng không nằm trong danh sách được phép truy cập.');
            }
            if (branchId !== roomBranchId) {
                facilityElement('branchSelect').value = roomBranchId;
                branchId = roomBranchId;
                loadCurrentTabData();
            }
        }
        renderRoomUsage(data);
    } catch (error) {
        if (request !== usageRequest || usageRoomId !== id || branchId !== selectedBranchId()) return;
        facilityElement('roomUsageBody').innerHTML = `<div class="alert alert-danger" role="alert">${escapeHtml(error.message)}</div>
            <button type="button" class="btn btn-outline-secondary" data-facility-retry="usage"><i class="bi bi-arrow-clockwise me-1" aria-hidden="true"></i>Thử lại</button>`;
    }
}

function openRoomUsage(id, fromEditor = false, selectRoomBranch = false) {
    if (!canViewRoomUsage()) {
        facilityElement('facilityNotice').innerHTML = '<div class="alert alert-warning" role="alert">Bạn không có quyền xem lịch sử dụng phòng.</div>';
        return;
    }
    if (!fromEditor && !selectRoomBranch && !rooms.some(room => String(room.id) === String(id))) return;
    returnToEditor = fromEditor;
    usageSelectsBranch = selectRoomBranch;
    usageRoomId = String(id);
    facilityElement('backToFacilityEditBtn').classList.toggle('d-none', !fromEditor);
    facilityElement('roomUsageTitle').textContent = 'Chi tiết phòng & lịch sử dụng';
    loadRoomUsage(usageRoomId);
    usageModal.show();
}

function showEditError(message) {
    facilityElement('editFacilityError').textContent = message;
    facilityElement('editFacilityError').classList.toggle('d-none', !message);
}

function editOptions(labels, selected) {
    const entries = Object.entries(labels);
    if (selected && !labels[selected]) entries.push([selected, selected]);
    return entries.map(([value, label]) => `<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`).join('');
}

function openFacilityEditor(kind, id) {
    if (savingFacility) return;
    if (!canEditFacility()) {
        facilityElement('facilityNotice').innerHTML = '<div class="alert alert-warning" role="alert">Quyền chỉnh sửa thuộc quản trị viên.</div>';
        return;
    }
    const item = (kind === 'room' ? rooms : equipments).find(entry => String(entry.id) === String(id));
    if (!item) return;
    editing = { kind, id: String(id), branchId: selectedBranchId() };
    const isRoom = kind === 'room';
    facilityElement('editFacilityTitle').textContent = isRoom ? `Sửa phòng ${item.roomCode}` : `Sửa thiết bị ${item.code || item.name}`;
    facilityElement('editFacilityName').value = isRoom ? item.roomName : item.name;
    facilityElement('roomCapacityField').classList.toggle('d-none', !isRoom);
    facilityElement('editRoomCapacity').required = isRoom;
    facilityElement('editRoomCapacity').value = isRoom ? item.capacity : '';
    facilityElement('equipmentSerialField').classList.toggle('d-none', isRoom);
    facilityElement('editEquipmentSerial').value = isRoom ? '' : (item.serialNumber || '');
    const category = facilityElement('editFacilityCategory');
    category.innerHTML = editOptions(isRoom ? roomTypes : equipmentTypes, isRoom ? item.roomType : item.category);
    category.value = isRoom ? item.roomType : item.category;
    const status = facilityElement('editFacilityStatus');
    status.innerHTML = editOptions(isRoom ? roomStatuses : equipmentStatuses, item.status);
    status.value = item.status;
    facilityElement('editFacilityDescription').value = item.description || '';
    facilityElement('viewRoomUsageBtn').classList.toggle('d-none', !isRoom);
    showEditError('');
    editModal.show();
}

function setFacilitySaving(saving) {
    savingFacility = saving;
    editElement.querySelectorAll('input, select, textarea, button').forEach(element => { element.disabled = saving; });
    facilityElement('branchSelect').disabled = saving || !branches.length;
    facilityElement('saveFacilityBtn').textContent = saving ? 'Đang lưu...' : 'Lưu thay đổi';
}

async function saveFacility(event) {
    event.preventDefault();
    if (!editing || savingFacility || !editElement.classList.contains('show')) return;
    if (!canEditFacility()) return showEditError('Quyền chỉnh sửa thuộc quản trị viên.');
    const context = editing;
    if (context.branchId !== selectedBranchId()) return showEditError('Cơ sở đã thay đổi. Vui lòng mở lại thông tin.');
    const isRoom = context.kind === 'room';
    const name = facilityElement('editFacilityName').value.trim();
    const category = facilityElement('editFacilityCategory').value;
    const status = facilityElement('editFacilityStatus').value;
    const description = facilityElement('editFacilityDescription').value.trim();
    const capacity = Number(facilityElement('editRoomCapacity').value);
    if (!name || (isRoom && (!Number.isInteger(capacity) || capacity < 1))) return showEditError('Kiểm tra tên và sức chứa phòng.');
    const payload = isRoom ? { roomName: name, capacity, roomType: category, status, description }
        : { name, category, status, serialNumber: facilityElement('editEquipmentSerial').value.trim(), description };
    setFacilitySaving(true);
    showEditError('');
    try {
        const result = await facilityRequest(`/api/branches/${isRoom ? 'rooms' : 'equipments'}/${encodeURIComponent(context.id)}`, 'PUT', payload);
        if (editing !== context || selectedBranchId() !== context.branchId) return;
        const warnings = Array.isArray(result?.warnings) ? [...result.warnings] : [];
        if (!isRoom && Number(result?.affectedLessonCount) > 0) warnings.push(`${Number(result.affectedLessonCount)} buổi học sắp tới bị ảnh hưởng.`);
        setFacilitySaving(false);
        editing = null;
        editModal.hide();
        showFacilityNotice(isRoom ? 'Đã lưu thay đổi phòng học.' : 'Đã lưu thay đổi thiết bị.', warnings);
        loadCurrentTabData();
    } catch (error) {
        if (editing === context && selectedBranchId() === context.branchId) showEditError(error.message || 'Không lưu được thay đổi.');
    } finally {
        setFacilitySaving(false);
    }
}

facilityElement('roomTableBody').addEventListener('click', event => {
    const view = event.target.closest('[data-room-usage]');
    const edit = event.target.closest('[data-edit-room]');
    if (view) openRoomUsage(view.dataset.roomUsage);
    if (edit) openFacilityEditor('room', edit.dataset.editRoom);
});
facilityElement('equipmentTableBody').addEventListener('click', event => {
    const button = event.target.closest('[data-edit-equipment]');
    if (button) openFacilityEditor('equipment', button.dataset.editEquipment);
});
document.addEventListener('click', event => {
    const retry = event.target.closest('[data-facility-retry]')?.dataset.facilityRetry;
    if (retry === 'branches') loadBranches();
    if (retry === 'rooms' && selectedBranchId()) loadRooms(selectedBranchId());
    if (retry === 'equipments' && selectedBranchId()) loadEquipments(selectedBranchId());
    if (retry === 'usage' && usageRoomId) loadRoomUsage(usageRoomId);
});
facilityElement('viewRoomUsageBtn').addEventListener('click', () => {
    if (!editing || editing.kind !== 'room' || savingFacility || !canViewRoomUsage()) return;
    const context = editing;
    // Bootstrap supports one modal at a time; leave the editor DOM intact as the draft.
    editElement.addEventListener('hidden.bs.modal', () => {
        if (editing === context && context.branchId === selectedBranchId()) openRoomUsage(context.id, true);
    }, { once: true });
    editModal.hide();
});
editElement.addEventListener('hide.bs.modal', event => { if (savingFacility) event.preventDefault(); });
usageElement.addEventListener('hide.bs.modal', () => { ++usageRequest; });
usageElement.addEventListener('hidden.bs.modal', () => {
    usageRoomId = null;
    const restore = returnToEditor && editing?.branchId === selectedBranchId() && canEditFacility();
    returnToEditor = false;
    if (restore) {
        editModal.show();
        editElement.addEventListener('shown.bs.modal', () => facilityElement('viewRoomUsageBtn').focus(), { once: true });
    }
});
facilityElement('editFacilityForm').addEventListener('submit', saveFacility);

async function initializeFacilities() {
    if (!checkAuth()) return;
    updateNavbarUser();
    facilityElement('facilityAccess').classList.toggle('d-none', canEditFacility());
    switchTab('ROOMS');
    loadBranches();
    for (const name of ['footer']) {
        try {
            const response = await fetch(`../components/${name}.html`);
            if (!response.ok) continue;
            facilityElement(`${name}-container`).innerHTML = await response.text();
        } catch (error) { console.warn(`Không tải được ${name}:`, error); }
    }
}
initializeFacilities();
