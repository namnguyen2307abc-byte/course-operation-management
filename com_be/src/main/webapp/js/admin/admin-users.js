(async function () {
    if (!await Admin.ready()) return;
    const { $, escape: esc } = Admin;
    Admin.options($('roleFilter'), Admin.roles, roleLabel, 'Tất cả vai trò');
    let currentPage = 0, generation = 0;
    async function load(page = 0) {
        const requestId = ++generation;
        currentPage = page; Admin.message(); $('usersTable').setAttribute('aria-busy', 'true');
        $('directoryUpdate').textContent = 'Đang cập nhật…';
        try {
            const data = await Admin.request('/users?' + Admin.query({ keyword: $('keyword').value.trim(), role: $('roleFilter').value, status: $('statusFilter').value, page, size: 5 }));
            if (requestId !== generation) return;
            $('directoryCount').textContent = data.totalElements;
            $('usersBody').innerHTML = data.items.length ? data.items.map(u => {
                const initial = (u.fullName || u.username).trim().split(/\s+/).pop().slice(0, 1).toUpperCase();
                return `<tr><td><div class="admin-person"><span class="admin-avatar role-${esc(u.role.toLowerCase())}">${esc(initial)}</span><div><strong>${esc(u.fullName)} ${u.id === getCurrentUser().userId ? '<span class="admin-self-tag">Bạn</span>' : ''}</strong><small>@${esc(u.username)} ${u.username.startsWith('qa_') ? '<span class="admin-test-tag">Test</span>' : ''}</small></div></div></td><td><span class="admin-role-label role-${esc(u.role.toLowerCase())}">${esc(roleLabel(u.role))}</span></td><td><span class="admin-badge ${u.status === 'ACTIVE' ? 'active' : 'inactive'}"><span class="status-dot ${u.status === 'ACTIVE' ? 'active' : 'inactive'}"></span>${u.status === 'ACTIVE' ? 'Hoạt động' : 'Đã khóa'}</span>${u.mustChangePassword ? '<small class="admin-password-hint"><i class="bi bi-key" aria-hidden="true"></i> Cần đổi mật khẩu</small>' : ''}</td><td>${esc(u.email || 'Chưa có email')}<small>${esc(u.phone || 'Chưa có số điện thoại')}</small></td><td class="admin-created-cell">${esc(Admin.date(u.createdAt))}</td><td><button class="btn btn-sm admin-detail-button" data-user-id="${u.id}" aria-label="Chi tiết tài khoản ${esc(u.username)}">Chi tiết <i class="bi bi-arrow-up-right" aria-hidden="true"></i></button></td></tr>`;
            }).join('') : '<tr><td colspan="6" class="admin-empty"><i class="bi bi-search" aria-hidden="true"></i><strong>Không tìm thấy tài khoản</strong><p>Thử từ khóa khác hoặc xóa bộ lọc để xem toàn bộ danh sách.</p></td></tr>';
            Admin.pagination(data, load);
            $('directoryUpdate').textContent = 'Vừa cập nhật';
        } catch (error) { if (requestId === generation) { Admin.message(error.message); $('directoryUpdate').textContent = 'Chưa cập nhật được'; } }
        finally { if (requestId === generation) $('usersTable').removeAttribute('aria-busy'); }
    }
    AdminUserForm.initialize(() => load(currentPage));
    $('userFilters').addEventListener('submit', event => { event.preventDefault(); load(0); });
    $('createUser').addEventListener('click', () => AdminUserForm.create());
    $('resetUserFilters').addEventListener('click', () => { $('userFilters').reset(); load(0); });
    $('usersBody').addEventListener('click', event => {
        const button = event.target.closest('[data-user-id]');
        if (button) AdminUserForm.detail(Number(button.dataset.userId));
    });
    await load();
})();
