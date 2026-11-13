/* Runs synchronously after the static shell, before page content is parsed. */
updateNavbarUser();
(() => {
    const navigation = document.getElementById('workspaceNavigation');
    if (!navigation) return;
    function rememberScroll() {
        const user = getCurrentUser();
        if (user) {
            try { sessionStorage.setItem('workspace-nav-scroll:' + user.username, String(navigation.scrollTop)); } catch {}
        }
    }
    navigation.addEventListener('scroll', rememberScroll, { passive: true });
    navigation.addEventListener('click', event => {
        const link = event.target.closest('a');
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        rememberScroll();
        if (link.pathname === window.location.pathname && !link.hash && !link.search) event.preventDefault();
    });
    window.addEventListener('pagehide', rememberScroll);
})();

/* ==========================================================================
   HỆ THỐNG CHUÔNG THÔNG BÁO TOÀN CỤC (GLOBAL NOTIFICATION BELL)
   ========================================================================== */
(() => {
    const topbar = document.querySelector('.workspace-topbar');
    if (!topbar || document.getElementById('workspaceNotifContainer')) return;

    const isDev = window.location.port !== '8080' && window.location.port !== '';
    const API_BASE = isDev ? `http://${window.location.hostname || 'localhost'}:8080` : '';

    // 1. Inject CSS cho chuông thông báo
    const style = document.createElement('style');
    style.textContent = `
        .workspace-notif-wrap {
            position: relative;
            margin-left: auto;
            display: inline-flex;
            align-items: center;
            gap: 12px;
        }
        .workspace-topbar .workspace-context {
            margin-left: 0 !important;
        }
        .notif-bell-btn {
            position: relative;
            border: 1px solid #e2e8f0;
            background: #ffffff;
            color: #334155;
            width: 38px;
            height: 38px;
            border-radius: 999px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            transition: all 0.18s ease;
            box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
        }
        .notif-bell-btn:hover {
            background: #f1f5f9;
            color: #2563eb;
            border-color: #cbd5e1;
        }
        .notif-bell-btn.has-unread {
            color: #2563eb;
            background: #eff6ff;
            border-color: #60a5fa;
            box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.2), 0 0 16px rgba(37, 99, 235, 0.45);
            animation: notifBellGlow 2.5s infinite ease-in-out;
        }
        @keyframes notifBellGlow {
            0%, 100% {
                box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15), 0 0 8px rgba(37, 99, 235, 0.35);
                border-color: #93c5fd;
            }
            50% {
                box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.3), 0 0 20px rgba(37, 99, 235, 0.65);
                border-color: #2563eb;
            }
        }
        .notif-bell-btn.has-unread > i {
            display: inline-block;
            transform-origin: top center;
            animation: notifBellSwing 3.5s infinite ease-in-out;
        }
        @keyframes notifBellSwing {
            0%, 75%, 100% { transform: rotate(0); }
            78% { transform: rotate(14deg); }
            82% { transform: rotate(-12deg); }
            86% { transform: rotate(8deg); }
            90% { transform: rotate(-4deg); }
        }
        .notif-unread-badge {
            position: absolute;
            top: -4px;
            right: -4px;
            min-width: 19px;
            height: 19px;
            padding: 0 5px;
            border-radius: 999px;
            background: #ef4444;
            color: #ffffff;
            font-size: 0.7rem;
            font-weight: 800;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            border: 2px solid #ffffff;
            line-height: 1;
            box-shadow: 0 0 8px rgba(239, 68, 68, 0.6);
        }
        .notif-dropdown-panel {
            position: absolute;
            top: calc(100% + 10px);
            right: 0;
            width: 370px;
            max-width: calc(100vw - 24px);
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 16px;
            box-shadow: 0 20px 40px -15px rgba(15, 23, 42, 0.18);
            z-index: 2050;
            overflow: hidden;
        }
        .notif-dropdown-header {
            padding: 12px 16px;
            background: #f8fafc;
            border-bottom: 1px solid #e2e8f0;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .notif-dropdown-list {
            max-height: 380px;
            overflow-y: auto;
        }
        .notif-item {
            padding: 12px 16px;
            border-bottom: 1px solid #f1f5f9;
            display: flex;
            gap: 12px;
            cursor: pointer;
            transition: background 0.15s ease;
            text-align: left;
        }
        .notif-item:hover {
            background: #f8fafc;
        }
        .notif-item.unread {
            background: #eff6ff;
        }
        .notif-item.unread:hover {
            background: #dbeafe;
        }
        .notif-item-icon {
            width: 36px;
            height: 36px;
            border-radius: 10px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            font-size: 1rem;
        }
        .notif-item-title {
            font-size: 0.86rem;
            font-weight: 700;
            color: #0f172a;
            margin-bottom: 2px;
        }
        .notif-item-msg {
            font-size: 0.8rem;
            color: #475569;
            line-height: 1.35;
            margin-bottom: 4px;
        }
        .notif-item-meta {
            font-size: 0.72rem;
            color: #94a3b8;
            display: flex;
            justify-content: space-between;
        }
    `;
    document.head.appendChild(style);

    // 2. Tạo phần tử Chuông & Dropdown trên Topbar
    const contextSpan = topbar.querySelector('.workspace-context');
    const wrap = document.createElement('div');
    wrap.className = 'workspace-notif-wrap';
    wrap.id = 'workspaceNotifContainer';

    wrap.innerHTML = `
        <button type="button" class="notif-bell-btn" id="notifBellBtn" title="Thông báo hệ thống" aria-label="Thông báo">
            <i class="bi bi-bell-fill"></i>
            <span class="notif-unread-badge d-none" id="notifUnreadBadge">0</span>
        </button>
        <div class="notif-dropdown-panel d-none" id="notifDropdownPanel">
            <div class="notif-dropdown-header">
                <strong class="small text-dark"><i class="bi bi-bell me-1 text-primary"></i>Thông báo <span class="badge bg-danger-subtle text-danger rounded-pill ms-1" id="notifHeaderUnreadCount">0 chưa đọc</span></strong>
                <button type="button" class="btn btn-link btn-sm p-0 text-decoration-none small fw-semibold" id="notifMarkAllBtn">
                    Đọc tất cả
                </button>
            </div>
            <div class="px-3 py-1.5 border-bottom bg-white d-flex gap-1 flex-wrap" id="notifFilterTabs" style="font-size: 0.74rem;">
                <button type="button" class="btn btn-sm rounded-pill px-2.5 py-0.5 btn-primary" data-filter="ALL" id="notifTabAll" style="font-size: 0.72rem;">Tất cả (0)</button>
                <button type="button" class="btn btn-sm rounded-pill px-2.5 py-0.5 btn-light border" data-filter="UNREAD" id="notifTabUnread" style="font-size: 0.72rem;">Chưa đọc (0)</button>
                <button type="button" class="btn btn-sm rounded-pill px-2.5 py-0.5 btn-light border text-danger" data-filter="ALERT" id="notifTabAlert" style="font-size: 0.72rem;"><i class="bi bi-exclamation-triangle-fill me-1"></i>Cảnh báo (0)</button>
            </div>
            <div class="notif-dropdown-list" id="notifDropdownList">
                <div class="p-4 text-center text-muted small">Đang tải thông báo...</div>
            </div>
            <div class="d-none border-top px-3 py-2 bg-light d-flex justify-content-between align-items-center" id="notifPaginationBar">
                <span class="small text-muted" id="notifPageInfo">Trang 1 / 1</span>
                <div class="btn-group btn-group-sm">
                    <button type="button" class="btn btn-outline-secondary py-0 px-2" id="notifPrevBtn">&laquo; Trước</button>
                    <button type="button" class="btn btn-outline-secondary py-0 px-2" id="notifNextBtn">Sau &raquo;</button>
                </div>
            </div>
        </div>
    `;

    if (contextSpan) {
        topbar.insertBefore(wrap, contextSpan);
        wrap.appendChild(contextSpan);
    } else {
        topbar.appendChild(wrap);
    }

    const bellBtn = document.getElementById('notifBellBtn');
    const panel = document.getElementById('notifDropdownPanel');
    const badge = document.getElementById('notifUnreadBadge');
    const listEl = document.getElementById('notifDropdownList');
    const markAllBtn = document.getElementById('notifMarkAllBtn');
    const paginationBar = document.getElementById('notifPaginationBar');
    const pageInfoEl = document.getElementById('notifPageInfo');
    const prevBtn = document.getElementById('notifPrevBtn');
    const nextBtn = document.getElementById('notifNextBtn');

    let allNotifications = [];
    let notifFilter = 'ALL';
    let notifPage = 1;
    const notifPageSize = 4;

    const headerUnreadCountEl = document.getElementById('notifHeaderUnreadCount');
    const tabAllBtn = document.getElementById('notifTabAll');
    const tabUnreadBtn = document.getElementById('notifTabUnread');
    const tabAlertBtn = document.getElementById('notifTabAlert');

    function isAlertType(type) {
        if (!type) return false;
        return type.includes('CRITICAL') || type.includes('WARNING') || type.includes('MISSED')
            || type.includes('LIMIT_EXCEEDED') || type.includes('ABSENT') || type.includes('REJECTED')
            || type.includes('NO_SLOT') || type === 'TUITION_OVERDUE' || type === 'TUITION_DUE_TODAY';
    }

    async function fetchNotifApi(endpoint, method = 'GET') {
        const token = localStorage.getItem('token');
        if (!token) return null;
        const res = await fetch(API_BASE + endpoint, {
            method,
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        });
        if (!res.ok) return null;
        const text = await res.text();
        return text ? JSON.parse(text) : null;
    }

    function iconForType(type) {
        if (!type) return { cls: 'bg-primary-subtle text-primary', icon: 'bi-info-circle-fill', tag: '' };
        if (type.includes('ATTENDANCE_CRITICAL') || type.includes('MAKEUP_LIMIT_EXCEEDED')) {
            return { cls: 'bg-danger text-white', icon: 'bi-exclamation-octagon-fill', tag: '<span class="badge bg-danger text-white ms-1" style="font-size:0.62rem;">Khẩn cấp</span>' };
        }
        if (type.includes('ATTENDANCE_WARNING') || type.includes('MAKEUP_MISSED')) {
            return { cls: 'bg-warning-subtle text-warning-emphasis', icon: 'bi-exclamation-triangle-fill', tag: '<span class="badge bg-warning text-dark ms-1" style="font-size:0.62rem;">Cảnh báo</span>' };
        }
        if (type.includes('ABSENCE_CREATED')) {
            return { cls: 'bg-info-subtle text-info-emphasis', icon: 'bi-envelope-plus-fill', tag: '<span class="badge bg-info-subtle text-info-emphasis ms-1" style="font-size:0.62rem;">Đơn mới</span>' };
        }
        if (type.includes('MAKEUP_SCHEDULED') || type.includes('MAKEUP_COORDINATED') || type.includes('MAKEUP_PENDING')) {
            return { cls: 'bg-primary-subtle text-primary', icon: 'bi-calendar2-week-fill', tag: '<span class="badge bg-primary-subtle text-primary ms-1" style="font-size:0.62rem;">Học bù</span>' };
        }
        if (type.includes('PAYMENT_SUCCESS')) {
            return { cls: 'bg-success-subtle text-success', icon: 'bi-cash-coin', tag: '' };
        }
        if (type === 'TUITION_OVERDUE') {
            return { cls: 'bg-danger-subtle text-danger', icon: 'bi-receipt-cutoff', tag: '<span class="badge bg-danger text-white ms-1" style="font-size:0.62rem;">Quá hạn</span>' };
        }
        if (type === 'TUITION_DUE_TODAY') {
            return { cls: 'bg-warning-subtle text-warning-emphasis', icon: 'bi-wallet2', tag: '<span class="badge bg-warning text-dark ms-1" style="font-size:0.62rem;">Đến hạn</span>' };
        }
        if (type === 'TUITION_UPCOMING' || type === 'TUITION_PLAN_CONFIRMED') {
            return { cls: 'bg-info-subtle text-info-emphasis', icon: 'bi-calendar-check', tag: '' };
        }
        if (type.includes('INVOICE') || type.includes('DISCOUNT')) {
            return { cls: 'bg-warning-subtle text-warning-emphasis', icon: 'bi-receipt-cutoff', tag: '' };
        }
        if (type.includes('ENROLLMENT_APPROVED') || type.includes('ENROLLMENT_COMPLETED')) {
            return { cls: 'bg-success-subtle text-success', icon: 'bi-mortarboard-fill', tag: '' };
        }
        if (type.includes('ENROLLMENT_REQUEST')) {
            return { cls: 'bg-info-subtle text-info-emphasis', icon: 'bi-person-plus-fill', tag: '' };
        }
        if (type.includes('PLACEMENT')) {
            return { cls: 'bg-primary-subtle text-primary', icon: 'bi-clipboard2-check-fill', tag: '' };
        }
        if (type.includes('APPROVED') || type.includes('COMPLETED') || type.includes('PRESENT')) {
            return { cls: 'bg-success-subtle text-success', icon: 'bi-check-circle-fill', tag: '' };
        }
        if (type.includes('REJECTED') || type.includes('ABSENT') || type.includes('CANCELLED') || type.includes('NO_SLOT')) {
            return { cls: 'bg-danger-subtle text-danger', icon: 'bi-exclamation-circle-fill', tag: '' };
        }
        return { cls: 'bg-primary-subtle text-primary', icon: 'bi-calendar2-event-fill', tag: '' };
    }

    function esc(str) {
        if (!str) return '';
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function getFilteredNotifications() {
        if (notifFilter === 'UNREAD') {
            return allNotifications.filter(n => !n.isRead);
        }
        if (notifFilter === 'ALERT') {
            return allNotifications.filter(n => isAlertType(n.type));
        }
        return allNotifications;
    }

    function updateFilterCounters() {
        const total = allNotifications.length;
        const unread = allNotifications.filter(n => !n.isRead).length;
        const alertCount = allNotifications.filter(n => isAlertType(n.type)).length;

        if (headerUnreadCountEl) {
            headerUnreadCountEl.textContent = `${unread} chưa đọc`;
            headerUnreadCountEl.className = unread > 0
                ? 'badge bg-danger text-white rounded-pill ms-1'
                : 'badge bg-secondary-subtle text-secondary rounded-pill ms-1';
        }
        if (tabAllBtn) {
            tabAllBtn.textContent = `Tất cả (${total})`;
            tabAllBtn.className = notifFilter === 'ALL'
                ? 'btn btn-sm rounded-pill px-2.5 py-0.5 btn-primary'
                : 'btn btn-sm rounded-pill px-2.5 py-0.5 btn-light border';
        }
        if (tabUnreadBtn) {
            tabUnreadBtn.textContent = `Chưa đọc (${unread})`;
            tabUnreadBtn.className = notifFilter === 'UNREAD'
                ? 'btn btn-sm rounded-pill px-2.5 py-0.5 btn-primary'
                : 'btn btn-sm rounded-pill px-2.5 py-0.5 btn-light border';
        }
        if (tabAlertBtn) {
            tabAlertBtn.innerHTML = `<i class="bi bi-exclamation-triangle-fill me-1"></i>Cảnh báo (${alertCount})`;
            tabAlertBtn.className = notifFilter === 'ALERT'
                ? 'btn btn-sm rounded-pill px-2.5 py-0.5 btn-danger text-white'
                : 'btn btn-sm rounded-pill px-2.5 py-0.5 btn-light border text-danger';
        }
    }

    [tabAllBtn, tabUnreadBtn, tabAlertBtn].forEach(btn => {
        if (!btn) return;
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            notifFilter = btn.getAttribute('data-filter') || 'ALL';
            notifPage = 1;
            updateFilterCounters();
            renderNotificationPage();
        });
    });

    function renderNotificationPage() {
        updateFilterCounters();
        const filteredList = getFilteredNotifications();

        if (filteredList.length === 0) {
            listEl.innerHTML = '<div class="p-4 text-center text-muted small"><i class="bi bi-bell-slash d-block fs-4 mb-1"></i>Không có thông báo nào trong mục này</div>';
            if (paginationBar) paginationBar.classList.add('d-none');
            return;
        }

        const totalPages = Math.max(1, Math.ceil(filteredList.length / notifPageSize));
        if (notifPage > totalPages) notifPage = totalPages;
        if (notifPage < 1) notifPage = 1;

        const startIdx = (notifPage - 1) * notifPageSize;
        const pageItems = filteredList.slice(startIdx, startIdx + notifPageSize);

        listEl.innerHTML = pageItems.map(item => {
            const ic = iconForType(item.type);
            const unreadCls = item.isRead ? '' : 'unread';
            return `
                <div class="notif-item ${unreadCls}" data-id="${item.id}" data-url="${esc(item.targetUrl || '')}">
                    <div class="notif-item-icon ${ic.cls}">
                        <i class="bi ${ic.icon}"></i>
                    </div>
                    <div class="flex-grow-1">
                        <div class="notif-item-title">${esc(item.title)}${ic.tag || ''}</div>
                        <div class="notif-item-msg">${esc(item.message)}</div>
                        <div class="notif-item-meta">
                            <span><i class="bi bi-person me-1"></i>${esc(item.senderName || 'Hệ thống')}</span>
                            <span>${esc(item.createdAtText || '')}</span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        if (paginationBar) {
            paginationBar.classList.remove('d-none');
            if (pageInfoEl) pageInfoEl.textContent = `Trang ${notifPage} / ${totalPages} (${filteredList.length} TB)`;
            if (prevBtn) prevBtn.disabled = notifPage <= 1;
            if (nextBtn) nextBtn.disabled = notifPage >= totalPages;
        }

        listEl.querySelectorAll('.notif-item').forEach(el => {
            el.addEventListener('click', async () => {
                const id = el.getAttribute('data-id');
                const url = el.getAttribute('data-url');
                if (id) {
                    await fetchNotifApi(`/api/notifications/${id}/read`, 'PATCH');
                }
                if (url) {
                    const target = typeof appPath === 'function' ? appPath(url) : url;
                    window.location.href = target;
                } else {
                    await loadNotifications();
                }
            });
        });
    }

    if (prevBtn) {
        prevBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            if (notifPage > 1) {
                notifPage--;
                renderNotificationPage();
            }
        });
    }

    if (nextBtn) {
        nextBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            const filteredList = getFilteredNotifications();
            const totalPages = Math.max(1, Math.ceil(filteredList.length / notifPageSize));
            if (notifPage < totalPages) {
                notifPage++;
                renderNotificationPage();
            }
        });
    }

    async function loadNotifications() {
        try {
            const data = await fetchNotifApi('/api/notifications', 'GET');
            if (!data) return;

            const unreadCount = data.unreadCount || 0;
            allNotifications = data.notifications || [];

            if (unreadCount > 0) {
                badge.textContent = unreadCount > 99 ? '99+' : String(unreadCount);
                badge.classList.remove('d-none');
                bellBtn.classList.add('has-unread');
            } else {
                badge.classList.add('d-none');
                bellBtn.classList.remove('has-unread');
            }

            renderNotificationPage();
        } catch (err) {
            console.warn('Không thể tải thông báo:', err);
        }
    }

    window.refreshNotifications = loadNotifications;

    bellBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const isHidden = panel.classList.contains('d-none');
        panel.classList.toggle('d-none', !isHidden);
        if (isHidden) {
            await loadNotifications();
        }
    });

    markAllBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        await fetchNotifApi('/api/notifications/read-all', 'PATCH');
        await loadNotifications();
    });

    document.addEventListener('click', (e) => {
        if (!wrap.contains(e.target)) {
            panel.classList.add('d-none');
        }
    });

    // Tải số lượng thông báo ngay khi vào trang và cập nhật định kỳ mỗi 15s
    if (localStorage.getItem('token')) {
        loadNotifications();
        setInterval(loadNotifications, 15000);
    }
})();
