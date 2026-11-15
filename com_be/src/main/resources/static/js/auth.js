/** Shared navigation and account presentation. Roles come from the login response. */
function appPath(path) { return (window.location.pathname.startsWith('/com_fe/') ? '/com_fe' : '') + path; }
function checkAuth() {
    const authenticated = Boolean(localStorage.getItem('token') && getCurrentUser());
    if (authenticated && getCurrentUser().mustChangePassword && !window.location.pathname.endsWith('change-password.html')) {
        window.location.replace(appPath('/pages/change-password.html'));
        return false;
    }
    if (!authenticated && !window.location.pathname.endsWith('login.html')) window.location.replace(appPath('/login.html'));
    return authenticated;
}
function sanitizeUserData(user) {
    if (!user || typeof user !== 'object' || !user.role) return null;
    const role = String(user.role).toUpperCase().replace(/^ROLE_/, '');
    if (!['ADMIN', 'BRANCH_MANAGER', 'STAFF', 'TEACHER', 'PARENT', 'CASHIER'].includes(role)) return null;
    return { ...user, role };
}
function getCurrentUser() {
    try { return sanitizeUserData(JSON.parse(localStorage.getItem('user'))); } catch { return null; }
}
function roleLabel(role) {
    return { ADMIN: 'Quản trị viên', BRANCH_MANAGER: 'Quản lý chi nhánh', STAFF: 'Nhân viên', CASHIER: 'Thu ngân', TEACHER: 'Giáo viên', PARENT: 'Phụ huynh' }[role] || (role == null ? 'Lưu trữ' : 'Tài khoản trung tâm');
}
const WORKSPACE_FEATURES = [
    { file: 'admin-dashboard', label: 'Tổng quan quản trị', icon: 'bar-chart-line', group: 'Quản trị hệ thống', roles: ['ADMIN'], description: 'Theo dõi số liệu vận hành, học phí và các công việc chờ xử lý.' },
    { file: 'admin-users', label: 'Quản lý tài khoản', icon: 'person-gear', group: 'Quản trị hệ thống', roles: ['ADMIN'], description: 'Cấp tài khoản, phân quyền, khóa và mở quyền truy cập.' },
    { file: 'admin-audit', label: 'Nhật ký quản trị', icon: 'clock-history', group: 'Quản trị hệ thống', roles: ['ADMIN'], description: 'Tra cứu lịch sử thay đổi tài khoản và người thực hiện.' },
    { file: 'weekly-schedule', label: 'Thời khóa biểu', icon: 'calendar3', group: 'Đào tạo & học vụ', roles: ['ADMIN', 'STAFF', 'BRANCH_MANAGER', 'TEACHER', 'PARENT'], description: 'Xem thời khóa biểu tuần các ca học.' },
    { file: 'teacher-classes', label: 'Lớp phụ trách', icon: 'mortarboard', group: 'Đào tạo & học vụ', roles: ['TEACHER'], description: 'Xem danh sách các lớp phụ trách giảng dạy, học viên và tiến độ buổi học.' },
    { file: 'attendance-tracking', label: 'Điểm danh', icon: 'check2-square', group: 'Đào tạo & học vụ', roles: ['ADMIN', 'STAFF', 'BRANCH_MANAGER', 'PARENT'], description: 'Theo dõi tiến độ buổi học và lịch sử điểm danh của học viên theo khóa học.' },
    { file: 'course-enrollment', label: 'Đăng ký & xếp lớp', icon: 'person-plus', group: 'Đào tạo & học vụ', roles: ['PARENT', 'STAFF', 'BRANCH_MANAGER', 'ADMIN'], description: 'Đăng ký lớp học, theo dõi và xử lý yêu cầu ghi danh.' },
    { file: 'class-management', label: 'Quản lý lớp', icon: 'people', group: 'Đào tạo & học vụ', roles: ['ADMIN', 'STAFF', 'BRANCH_MANAGER'], description: 'Quản lý lớp, phân công giáo viên, phòng học và tạo lịch học.' },
    { file: 'course-management', label: 'Khóa học', icon: 'journal-bookmark', group: 'Đào tạo & học vụ', roles: ['STAFF', 'BRANCH_MANAGER', 'ADMIN'], description: 'Quản lý khóa học theo bộ môn, trình độ và học phí.' },
    { file: 'placement-test', label: 'Đánh giá đầu vào', icon: 'clipboard-check', group: 'Đào tạo & học vụ', roles: ['ADMIN', 'TEACHER', 'STAFF', 'BRANCH_MANAGER'], description: 'Sắp xếp lịch kiểm tra và ghi nhận kết quả đánh giá năng khiếu.' },
    { file: 'absence-makeup', label: 'Gửi đơn & Học bù', icon: 'envelope-paper', group: 'Đào tạo & học vụ', roles: ['PARENT', 'TEACHER', 'STAFF', 'BRANCH_MANAGER', 'ADMIN'], description: 'Viết đơn xin nghỉ học, theo dõi phê duyệt và sắp xếp buổi học bù.' },
    { file: 'branch-facility', label: 'Cơ sở & phòng học', icon: 'buildings', group: 'Vận hành trung tâm', roles: ['ADMIN', 'BRANCH_MANAGER', 'STAFF', 'TEACHER', 'PARENT'], description: 'Tra cứu chi nhánh, phòng học và cơ sở vật chất của trung tâm.' },
    { file: 'parent-tuition', label: 'Học phí & thông báo', icon: 'wallet2', group: 'Học tập của con', roles: ['PARENT'], description: 'Xem lịch đóng, thông báo, thanh toán online và biên lai học phí.' },
    { file: 'tuition-receivables', label: 'Quản lý học phí', icon: 'cash-stack', group: 'Vận hành trung tâm', roles: ['ADMIN','STAFF','BRANCH_MANAGER'], description: 'Chốt lịch đóng, thu học phí và quản lý công nợ.' },
];
function visibleFeatures(user) { return WORKSPACE_FEATURES.filter(feature => feature.roles.includes(user?.role)); }
function getFeatureLabel(feature, user) {
    if (feature.file === 'weekly-schedule') {
        return 'Thời khóa biểu';
    }
    if (feature.file === 'absence-makeup') {
        if (user && user.role === 'PARENT') {
            return 'Gửi đơn & Học bù';
        }
        return 'Quản lý đơn';
    }
    return feature.label;
}
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
    const navigationKey = user.username + ':' + user.role;
    if (nav.dataset.navigationKey === navigationKey) return;
    nav.dataset.navigationKey = navigationKey;
    nav.replaceChildren();
    function addLink(label, icon, path) {
        const a = document.createElement('a');
        a.href = appPath(path);
        a.className = 'workspace-nav-link';
        a.innerHTML = `<i class="bi bi-${icon}" aria-hidden="true"></i><span>${label}</span>`;
        if (window.location.pathname === a.pathname || (path.includes('weekly-schedule') && window.location.pathname.endsWith('/lesson-attendance.html')) || (path === '/index.html' && ['/', '/com_fe/'].includes(window.location.pathname))) {
            a.classList.add('active'); a.setAttribute('aria-current', 'page');
        }
        nav.appendChild(a);
    }
    function addCollapsibleNav(feature, user) {
        const wrap = document.createElement('div');
        wrap.className = 'workspace-nav-collapsible';
        const isCurrentPage = window.location.pathname.endsWith('/absence-makeup.html');
        if (isCurrentPage) wrap.classList.add('open');

        const parentBtn = document.createElement('button');
        parentBtn.type = 'button';
        parentBtn.className = 'workspace-nav-link has-submenu';

        const label = getFeatureLabel(feature, user);
        parentBtn.innerHTML = `
            <span class="nav-label-wrap">
                <i class="bi bi-${feature.icon}" aria-hidden="true"></i>
                <span>${label}</span>
            </span>
            <i class="bi bi-chevron-down nav-chevron" aria-hidden="true"></i>
        `;

        parentBtn.addEventListener('click', (e) => {
            e.preventDefault();
            if (!isCurrentPage) {
                window.location.href = appPath('/pages/absence-makeup.html');
                return;
            }
            wrap.classList.toggle('open');
        });

        wrap.appendChild(parentBtn);

        const subNav = document.createElement('div');
        subNav.className = 'workspace-nav-submenu';

        const subItems = [
            { id: 'ABSENCE', label: 'Xem đơn', path: '/pages/absence-makeup.html?tab=ABSENCE' }
        ];

        if (user.role === 'PARENT') {
            subItems.push({ id: 'CREATE', label: 'Gửi đơn', path: '/pages/absence-makeup.html?tab=CREATE' });
        }

        subItems.push({ id: 'MAKEUP', label: 'Lịch học bù', path: '/pages/absence-makeup.html?tab=MAKEUP' });

        const currentTab = isCurrentPage ? (new URLSearchParams(window.location.search).get('tab') || 'ABSENCE') : '';

        subItems.forEach(item => {
            const subLink = document.createElement('a');
            subLink.href = appPath(item.path);
            subLink.className = 'workspace-subnav-link';
            subLink.dataset.tab = item.id;
            subLink.innerHTML = `<span class="subnav-dot">•</span><span>${item.label}</span>`;

            if (isCurrentPage && currentTab === item.id) {
                subLink.classList.add('active');
            }

            subLink.addEventListener('click', (e) => {
                if (isCurrentPage) {
                    e.preventDefault();
                    if (typeof switchMainTab === 'function') {
                        switchMainTab(item.id);
                        const newUrl = new URL(window.location);
                        newUrl.searchParams.set('tab', item.id);
                        history.replaceState(null, '', newUrl.toString());
                    }
                }
            });

            subNav.appendChild(subLink);
        });

        wrap.appendChild(subNav);
        nav.appendChild(wrap);
    }
    addLink('Trang chủ', 'grid-1x2', '/index.html');
    let group;
    visibleFeatures(user).forEach(feature => {
        if (group !== feature.group) {
            group = feature.group;
            const title = document.createElement('p'); title.className = 'sidebar-group-label'; title.textContent = group; nav.appendChild(title);
        }
        if (feature.file === 'absence-makeup') {
            addCollapsibleNav(feature, user);
        } else {
            addLink(getFeatureLabel(feature, user), feature.icon, '/pages/' + feature.file + '.html');
        }
    });
    addLink('Đổi mật khẩu', 'key', '/pages/change-password.html');
    document.querySelectorAll('[data-app-path]').forEach(link => link.href = appPath(link.dataset.appPath));
    try { nav.scrollTop = Number(sessionStorage.getItem('workspace-nav-scroll:' + user.username)) || 0; } catch {}
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

async function updateTuitionNoticeIndicator() {
    if (window.location.pathname.endsWith('change-password.html') || window.location.pathname.endsWith('login.html')) return;
    const user = getCurrentUser();
    if (!user || user.mustChangePassword || user.role !== 'PARENT' || typeof callApi !== 'function') return;
    if (!document.getElementById('workspaceNavigation')) return;
    try {
        const notices = await callApi('/api/parent-tuition/notifications', 'GET', null, false, false);
        const link = [...document.querySelectorAll('#workspaceNavigation a')].find(a => a.pathname.endsWith('/parent-tuition.html'));
        const count = notices.filter(n => !n.read).length;
        if (link) link.querySelector('span').textContent = 'Học phí & thông báo' + (count ? ' (' + count + ')' : '');
    } catch {}
}
document.addEventListener('DOMContentLoaded', updateTuitionNoticeIndicator);
