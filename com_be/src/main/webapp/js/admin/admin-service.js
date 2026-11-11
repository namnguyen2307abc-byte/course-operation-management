/* Shared transport/presentation helpers. Domain rules are enforced by the backend. */
const Admin = (() => {
    const roles = ['ADMIN', 'BRANCH_MANAGER', 'STAFF', 'TEACHER', 'PARENT', 'STUDENT'];
    const actions = { CREATE_USER: 'Cấp tài khoản', UPDATE_USER: 'Sửa thông tin', CHANGE_ROLE: 'Đổi vai trò', CHANGE_STATUS: 'Khóa / mở khóa', RESET_PASSWORD: 'Đặt lại mật khẩu', SEED_TEST_DATA: 'Tạo dữ liệu test' };
    const statuses = { PENDING: 'Chờ xử lý', WAITING_PLACEMENT: 'Chờ kiểm tra đầu vào', READY_FOR_ASSIGNMENT: 'Chờ xếp lớp', PENDING_PAYMENT: 'Chờ thanh toán', APPROVED: 'Đã ghi danh', REJECTED: 'Từ chối', CANCELLED: 'Đã hủy' };
    const $ = id => document.getElementById(id);
    const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value || 0);
    const date = value => value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—';
    function query(values) { return new URLSearchParams(Object.entries(values).filter(([, v]) => v !== '' && v != null)).toString(); }
    async function request(path, method = 'GET', body = null) {
        return callApi('/api/admin' + path, method, body, false, false);
    }
    function message(text = '', success = false) {
        const node = $('adminMessage');
        node.hidden = !text; node.className = 'alert ' + (success ? 'alert-success' : 'alert-danger'); node.textContent = text;
    }
    function options(select, values, label = value => value, first) {
        select.replaceChildren();
        if (first != null) select.add(new Option(first, ''));
        values.forEach(value => select.add(new Option(label(value), value)));
    }
    function pagination(page, load) {
        $('pageInfo').textContent = `${page.totalElements} kết quả · Trang ${page.totalPages ? page.page + 1 : 0}/${page.totalPages}`;
        $('previousPage').disabled = !page.hasPrevious; $('nextPage').disabled = !page.hasNext;
        $('previousPage').onclick = () => load(page.page - 1); $('nextPage').onclick = () => load(page.page + 1);
    }
    async function ready() {
        if (!checkAuth()) return false;
        try {
            const user = await callApi('/api/auth/me', 'GET', null, false, false);
            localStorage.setItem('token', user.token); localStorage.setItem('user', JSON.stringify(user));
            if (user.mustChangePassword) { window.location.replace(appPath('/pages/change-password.html')); return false; }
            if (user.role !== 'ADMIN') { window.location.replace(appPath('/index.html')); return false; }
            updateNavbarUser();
            return true;
        } catch (error) { message(error.message); return false; }
    }
    function link(file, text, count) {
        return `<a class="admin-queue" href="${escape(appPath('/pages/' + file + '.html'))}"><span>${escape(text)}</span><strong>${escape(count)}</strong><i class="bi bi-arrow-up-right" aria-hidden="true"></i></a>`;
    }
    return { $, roles, actions, statuses, escape, money, date, query, request, message, options, pagination, ready, link };
})();
