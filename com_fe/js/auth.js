/** Shared navigation and account presentation. Roles come from the login response. */
function appPath(path) { return (window.location.pathname.startsWith('/com_fe/') ? '/com_fe' : '') + path; }
function checkAuth() {
    if ((!localStorage.getItem('token') || !getCurrentUser()) && !window.location.pathname.endsWith('login.html')) window.location.replace(appPath('/login.html'));
}
function sanitizeUserData(user) {
    if (!user || typeof user !== 'object' || !user.role) return null;
    return { ...user, role: String(user.role).toUpperCase().replace(/^ROLE_/, '') };
}
function getCurrentUser() {
    try { return sanitizeUserData(JSON.parse(localStorage.getItem('user'))); } catch { return null; }
}
function roleLabel(role) {
    return { ADMIN: 'Quản trị viên', BRANCH_MANAGER: 'Quản lý chi nhánh', STAFF: 'Nhân viên', CASHIER: 'Thu ngân', TEACHER: 'Giáo viên', PARENT: 'Phụ huynh', STUDENT: 'Học viên' }[role] || 'Tài khoản trung tâm';
}
const WORKSPACE_FEATURES = [
    { file: 'course-enrollment', label: 'Đăng ký & xếp lớp', icon: 'person-plus', group: 'Đào tạo & học vụ', roles: ['PARENT', 'STAFF', 'BRANCH_MANAGER', 'ADMIN'], description: 'Đăng ký lớp học, theo dõi và xử lý yêu cầu ghi danh.' },
    { file: 'course-management', label: 'Khóa học', icon: 'journal-bookmark', group: 'Đào tạo & học vụ', roles: ['STAFF', 'BRANCH_MANAGER', 'ADMIN'], description: 'Quản lý khóa học theo bộ môn, trình độ và học phí.' },
    { file: 'placement-test', label: 'Đánh giá đầu vào', icon: 'clipboard-check', group: 'Đào tạo & học vụ', roles: ['ADMIN', 'TEACHER', 'STAFF', 'BRANCH_MANAGER'], description: 'Sắp xếp lịch kiểm tra và ghi nhận kết quả đánh giá năng khiếu.' },
    { file: 'absence-makeup', label: 'Nghỉ học & học bù', icon: 'calendar2-week', group: 'Đào tạo & học vụ', roles: ['PARENT', 'TEACHER', 'STAFF', 'BRANCH_MANAGER', 'ADMIN', 'STUDENT'], description: 'Theo dõi đơn xin nghỉ và sắp xếp buổi học bù phù hợp.' },
    { file: 'branch-facility', label: 'Cơ sở & phòng học', icon: 'buildings', group: 'Vận hành trung tâm', roles: ['ADMIN', 'BRANCH_MANAGER', 'STAFF', 'TEACHER', 'PARENT', 'STUDENT'], description: 'Tra cứu chi nhánh, phòng học và cơ sở vật chất của trung tâm.' },
    { file: 'tuition-payment', label: 'Thu học phí', icon: 'wallet2', group: 'Vận hành trung tâm', roles: ['ADMIN', 'STAFF', 'CASHIER'], description: 'Tra cứu hóa đơn, thu học phí và xem lịch sử thanh toán.' }
];
function visibleFeatures(user) { return WORKSPACE_FEATURES.filter(feature => feature.roles.includes(user?.role)); }
function updateNavbarUser() {
    const nav = document.getElementById('workspaceNavigation');
    if (!nav) return;
    checkAuth();
    const user = getCurrentUser();
    if (!user) return;
    document.body.classList.add('has-workspace-sidebar');
    const main = document.querySelector('main');
    if (main && !main.id) main.id = 'main-content';
    document.getElementById('sidebarUserName').textContent = user.fullName || user.username || 'Tài khoản';
    document.getElementById('sidebarUserRole').textContent = roleLabel(user.role);
    document.getElementById('sidebarAvatar').textContent = (user.fullName || user.username || 'T').trim().split(/\s+/).pop().slice(0, 1).toUpperCase();
    nav.replaceChildren();
    function addLink(label, icon, path) {
        const a = document.createElement('a');
        a.href = appPath(path);
        a.className = 'workspace-nav-link';
        a.innerHTML = `<i class="bi bi-${icon}" aria-hidden="true"></i><span>${label}</span>`;
        if (window.location.pathname === a.pathname || (path === '/index.html' && ['/', '/com_fe/'].includes(window.location.pathname))) {
            a.classList.add('active'); a.setAttribute('aria-current', 'page');
        }
        nav.appendChild(a);
    }
    addLink('Trang chủ', 'grid-1x2', '/index.html');
    let group;
    visibleFeatures(user).forEach(feature => {
        if (group !== feature.group) {
            group = feature.group;
            const title = document.createElement('p'); title.className = 'sidebar-group-label'; title.textContent = group; nav.appendChild(title);
        }
        addLink(feature.label, feature.icon, '/pages/' + feature.file + '.html');
    });
    document.querySelectorAll('[data-app-path]').forEach(link => link.href = appPath(link.dataset.appPath));
}
function toggleMobileSidebar(force) {
    const open = typeof force === 'boolean' ? force : !document.body.classList.contains('sidebar-open');
    document.body.classList.toggle('sidebar-open', open);
    const sidebar = document.getElementById('appSidebar');
    document.getElementById('sidebarBackdrop').hidden = !open;
    const toggle = document.querySelector('.menu-toggle');
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? 'Đóng menu điều hướng' : 'Mở menu điều hướng');
    if (open) sidebar.querySelector('a').focus(); else toggle.focus();
}
document.addEventListener('keydown', event => {
    if (!document.body.classList.contains('sidebar-open')) return;
    if (event.key === 'Escape') toggleMobileSidebar(false);
    if (event.key === 'Tab') {
        const items = [...document.querySelectorAll('#appSidebar a, #appSidebar button')];
        const first = items[0], last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
});
window.addEventListener('resize', () => { if (window.innerWidth > 991 && document.body.classList.contains('sidebar-open')) toggleMobileSidebar(false); });
function logout() {
    localStorage.removeItem('token'); localStorage.removeItem('user'); window.location.href = appPath('/login.html');
}
document.addEventListener('DOMContentLoaded', updateNavbarUser);
