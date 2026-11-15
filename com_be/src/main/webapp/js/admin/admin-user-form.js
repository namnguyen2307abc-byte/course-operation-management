const AdminUserForm = (() => {
    const { $, escape: esc } = Admin;
    let selected, mode, onSaved, editingId;
    function error(id, message = '') { $(id).textContent = message; $(id).hidden = !message; }
    function close(id) { $(id).close(); }

    function createChildRow(name = '', dob = '', gender = 'Nam', school = '') {
        const row = document.createElement('div');
        row.className = 'child-item-card border p-2 mb-2 rounded bg-white';
        row.innerHTML = `
            <div class="row g-2 align-items-center">
                <div class="col-md-5">
                    <input class="form-control form-control-sm child-name" placeholder="Họ và tên con" value="${esc(name)}">
                </div>
                <div class="col-md-3">
                    <input type="date" class="form-control form-control-sm child-dob" value="${esc(dob)}" title="Ngày sinh">
                </div>
                <div class="col-md-3">
                    <select class="form-select form-select-sm child-gender">
                        <option value="Nam"${gender === 'Nam' ? ' selected' : ''}>Nam</option>
                        <option value="Nữ"${gender === 'Nữ' ? ' selected' : ''}>Nữ</option>
                    </select>
                </div>
                <div class="col-md-1 text-end">
                    <button type="button" class="btn btn-sm btn-outline-danger child-remove" title="Xóa con" aria-label="Xóa"><i class="bi bi-trash" aria-hidden="true"></i></button>
                </div>
                <div class="col-12 mt-1">
                    <input class="form-control form-control-sm child-school" placeholder="Trường học (nếu có)" value="${esc(school)}">
                </div>
            </div>`;
        row.querySelector('.child-remove').addEventListener('click', () => {
            const list = $('childrenList');
            if (list && list.children.length > 1) {
                row.remove();
            } else {
                row.querySelector('.child-name').value = '';
                row.querySelector('.child-dob').value = '';
                row.querySelector('.child-school').value = '';
            }
        });
        return row;
    }

    function resetChildren(initialOne = true) {
        const list = $('childrenList');
        if (!list) return;
        list.innerHTML = '';
        if (initialOne) list.appendChild(createChildRow());
    }

    function showRoleFields(role, prefix = 'user') {
        const needed = role === 'STUDENT';
        if ($(prefix + 'StudentFields')) $(prefix + 'StudentFields').hidden = !needed;
        if ($(prefix + 'StudentId')) $(prefix + 'StudentId').required = needed;
        if (needed) loadStudents(prefix);

        if (prefix === 'user') {
            const hints = {
                ADMIN: 'Quản lý tài khoản và theo dõi hoạt động toàn trung tâm.',
                BRANCH_MANAGER: 'Điều phối học vụ và các nghiệp vụ dành cho quản lý.',
                STAFF: 'Thực hiện nghiệp vụ tuyển sinh, học vụ và thu học phí.',
                TEACHER: 'Theo dõi lịch dạy, điểm danh và đánh giá đầu vào.',
                PARENT: 'Đăng ký lớp và theo dõi việc học của con.',
                STUDENT: 'Xem lịch và thông tin của đúng hồ sơ học viên được liên kết.'
            };
            $('userRoleHint').textContent = hints[role] || '';
            $('userTeachingSubjectFields').hidden = role !== 'TEACHER';
            if ($('parentChildrenFields')) $('parentChildrenFields').hidden = (editingId !== null || role !== 'PARENT');
        }
    }

    async function loadStudents(prefix) {
        const input = $(prefix + 'StudentSearch');
        const keyword = input ? input.value : '';
        try {
            const students = await Admin.request('/users/student-options?' + Admin.query({ keyword }));
            const select = $(prefix + 'StudentId');
            if (select) {
                select.replaceChildren(new Option('Chọn hồ sơ học viên', ''));
                students.forEach(s => select.add(new Option(`#${s.id} · ${s.name}`, s.id)));
            }
        } catch (e) {
            error(prefix === 'user' ? 'userFormError' : 'actionError', e.message);
        }
    }

    function create() {
        editingId = null; $('userForm').reset(); error('userFormError');
        $('userDialogTitle').textContent = 'Cấp tài khoản mới'; $('createOnly').hidden = false;
        $('userUsername').disabled = false; $('userPassword').required = true;
        resetChildren(true);
        $('userRole').value = 'STAFF'; showRoleFields('STAFF', 'user'); $('userDialog').showModal();
    }

    async function detail(id) {
        Admin.message();
        try {
            const result = await Admin.request(`/users/${id}`); selected = result.user;
            $('detailTitle').textContent = selected.fullName;
            $('detailProfile').innerHTML = `<dl class="admin-detail-grid">${[
                ['Tên đăng nhập', selected.username], ['Vai trò', roleLabel(selected.role)],
                ['Trạng thái', selected.status === 'ACTIVE' ? 'Hoạt động' : 'Đã khóa'], ['Email', selected.email],
                ['Điện thoại', selected.phone], ['Bộ môn đánh giá đầu vào', selected.subject],
                ['Chuyên môn giảng dạy', selected.teachingSubject], ['Ngày tạo', Admin.date(selected.createdAt)],
                ['Mật khẩu', selected.mustChangePassword ? 'Cần đổi khi đăng nhập' : 'Đã thiết lập']
            ].map(([label, value]) => `<div><dt>${esc(label)}</dt><dd>${esc(value || '—')}</dd></div>`).join('')}</dl>`;
            function links(title, records) { return `<section class="admin-linked-records"><h3>${title}</h3><ul>${records.length ? records.map(r => `<li>${esc(r.name)} <span class="text-muted">#${r.id}</span></li>`).join('') : '<li>Chưa có liên kết nghiệp vụ. Có thể đổi vai trò nếu đáp ứng các điều kiện khác.</li>'}</ul></section>`; }
            $('detailLinks').innerHTML = (selected.role === 'TEACHER' || result.classes.length ? links('Lớp được phân công', result.classes) : '')
                + (selected.role === 'PARENT' || result.children.length ? links('Hồ sơ con của phụ huynh', result.children) : '')
                + (selected.role === 'STUDENT' || result.studentProfiles.length ? links('Hồ sơ học viên độc lập', result.studentProfiles) : '');
            const self = selected.id === getCurrentUser().userId;
            $('changeRole').disabled = self; $('changeStatus').disabled = self; $('resetPassword').disabled = self;
            $('changeStatus').textContent = selected.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản';
            $('selfAccountNote').hidden = !self;
            $('userAuditLink').href = appPath('/pages/admin-audit.html') + '?targetId=' + id;
            if (!$('detailDialog').open) $('detailDialog').showModal();
        } catch (e) { Admin.message(e.message); }
    }

    function edit() {
        close('detailDialog'); editingId = selected.id; $('userForm').reset(); error('userFormError');
        $('userDialogTitle').textContent = 'Cập nhật thông tin tài khoản'; $('createOnly').hidden = true;
        $('userUsername').disabled = true; $('userPassword').required = false;
        showRoleFields('', 'user');
        $('userTeachingSubjectFields').hidden = selected.role !== 'TEACHER';
        if ($('parentChildrenFields')) $('parentChildrenFields').hidden = true;
        for (const [id, field] of [['userName', 'fullName'], ['userEmail', 'email'], ['userPhone', 'phone'], ['userSubject', 'subject'], ['userTeachingSubject', 'teachingSubject']]) $(id).value = selected[field] || '';
        $('userDialog').showModal();
    }

    function action(type) {
        mode = type; close('detailDialog'); $('actionForm').reset(); error('actionError');
        const titles = { role: 'Thay đổi vai trò', status: selected.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản', password: 'Đặt lại mật khẩu' };
        $('actionTitle').textContent = titles[type]; $('actionAccount').textContent = `${selected.fullName} (${selected.username})`;
        $('actionRoleFields').hidden = type !== 'role'; $('actionPasswordFields').hidden = type !== 'password';
        $('actionPassword').required = type === 'password'; $('actionRole').value = selected.role;
        showRoleFields(type === 'role' ? selected.role : '', 'action');
        $('actionNotice').textContent = type === 'password' ? 'Người dùng phải đổi mật khẩu mới ở lần đăng nhập tiếp theo. Hãy cung cấp mật khẩu tạm qua kênh phù hợp.' : 'Thay đổi sẽ vô hiệu hóa phiên đăng nhập cũ của tài khoản. Các liên kết nghiệp vụ được giữ lại.';
        $('actionDialog').showModal();
    }

    function initialize(callback) {
        onSaved = callback;
        $('userDialog').addEventListener('close', () => {
            $('userPassword').value = ''; $('userPassword').type = 'password';
            $('toggleUserPassword').textContent = 'Hiện'; $('toggleUserPassword').setAttribute('aria-pressed', 'false');
        });
        $('actionDialog').addEventListener('close', () => { $('actionPassword').value = ''; });
        $('toggleUserPassword').addEventListener('click', () => {
            const input = $('userPassword'); const show = input.type === 'password';
            input.type = show ? 'text' : 'password'; $('toggleUserPassword').textContent = show ? 'Ẩn' : 'Hiện';
            $('toggleUserPassword').setAttribute('aria-pressed', String(show));
        });
        Admin.options($('userRole'), Admin.roles, roleLabel); Admin.options($('actionRole'), Admin.roles, roleLabel);
        document.querySelectorAll('[data-close-dialog]').forEach(button => button.addEventListener('click', () => close(button.dataset.closeDialog)));
        $('userRole').addEventListener('change', () => showRoleFields($('userRole').value, 'user'));
        $('actionRole').addEventListener('change', () => showRoleFields($('actionRole').value, 'action'));
        for (const prefix of ['user', 'action']) {
            if ($(prefix + 'StudentFind')) {
                $(prefix + 'StudentFind').addEventListener('click', () => loadStudents(prefix));
            }
        }
        $('addChildBtn')?.addEventListener('click', () => {
            $('childrenList')?.appendChild(createChildRow('', '', 'Nam', ''));
        });
        $('editUser').addEventListener('click', edit);
        $('changeRole').addEventListener('click', () => action('role')); $('changeStatus').addEventListener('click', () => action('status'));
        $('resetPassword').addEventListener('click', () => action('password'));
        $('userForm').addEventListener('submit', async event => {
            event.preventDefault(); error('userFormError'); $('saveUser').disabled = true;
            const payload = { fullName: $('userName').value.trim(), email: $('userEmail').value.trim(), phone: $('userPhone').value.trim(), subject: $('userSubject').value.trim() };
            if ((editingId ? selected.role : $('userRole').value) === 'TEACHER') payload.teachingSubject = $('userTeachingSubject').value.trim();
            if (!editingId) {
                Object.assign(payload, {
                    username: $('userUsername').value.trim(),
                    password: $('userPassword').value,
                    role: $('userRole').value,
                    studentId: $('userRole').value === 'STUDENT' ? Number($('userStudentId').value) : null
                });
                if ($('userRole').value === 'PARENT') {
                    const childCards = document.querySelectorAll('#childrenList .child-item-card');
                    const children = [];
                    childCards.forEach(r => {
                        const name = (r.querySelector('.child-name')?.value || '').trim();
                        const dob = r.querySelector('.child-dob')?.value || null;
                        const gender = r.querySelector('.child-gender')?.value || null;
                        const school = (r.querySelector('.child-school')?.value || '').trim();
                        if (name) {
                            children.push({
                                fullName: name,
                                dateOfBirth: dob || null,
                                gender: gender || null,
                                schoolName: school || null
                            });
                        }
                    });
                    if (children.length > 0) payload.children = children;
                }
            }
            try {
                await Admin.request(editingId ? `/users/${editingId}` : '/users', editingId ? 'PUT' : 'POST', payload);
                close('userDialog'); $('userPassword').value = ''; await onSaved(); Admin.message('Đã lưu tài khoản.', true);
            } catch (e) { error('userFormError', e.message); }
            finally { $('saveUser').disabled = false; }
        });
        $('actionForm').addEventListener('submit', async event => {
            event.preventDefault(); error('actionError'); $('saveAction').disabled = true;
            const payload = { reason: $('actionReason').value.trim() };
            let path, method = 'PATCH';
            if (mode === 'role') { path = 'role'; payload.role = $('actionRole').value; payload.studentId = payload.role === 'STUDENT' ? Number($('actionStudentId').value) : null; }
            if (mode === 'status') { path = 'status'; payload.status = selected.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'; }
            if (mode === 'password') { path = 'reset-password'; method = 'POST'; payload.password = $('actionPassword').value; }
            try {
                await Admin.request(`/users/${selected.id}/${path}`, method, payload); close('actionDialog'); $('actionPassword').value = '';
                await onSaved(); Admin.message('Đã cập nhật tài khoản và ghi nhật ký.', true);
            } catch (e) { error('actionError', e.message); }
            finally { $('saveAction').disabled = false; }
        });
    }
    return { initialize, create, detail };
})();
