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
                <strong class="small text-dark"><i class="bi bi-bell me-1 text-primary"></i>Thông báo của bạn</strong>
                <button type="button" class="btn btn-link btn-sm p-0 text-decoration-none small fw-semibold" id="notifMarkAllBtn">
                    Đánh dấu đã đọc tất cả
                </button>
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
    let notifPage = 1;
    const notifPageSize = 4;

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
        if (!type) return { cls: 'bg-primary-subtle text-primary', icon: 'bi-info-circle-fill' };
        if (type.includes('PAYMENT_SUCCESS')) {
            return { cls: 'bg-success-subtle text-success', icon: 'bi-cash-coin' };
        }
        if (type.includes('INVOICE') || type.includes('DISCOUNT')) {
            return { cls: 'bg-warning-subtle text-warning-emphasis', icon: 'bi-receipt-cutoff' };
        }
        if (type.includes('ENROLLMENT_APPROVED') || type.includes('ENROLLMENT_COMPLETED')) {
            return { cls: 'bg-success-subtle text-success', icon: 'bi-mortarboard-fill' };
        }
        if (type.includes('ENROLLMENT_REQUEST')) {
            return { cls: 'bg-info-subtle text-info-emphasis', icon: 'bi-person-plus-fill' };
        }
        if (type.includes('PLACEMENT')) {
            return { cls: 'bg-primary-subtle text-primary', icon: 'bi-clipboard2-check-fill' };
        }
        if (type.includes('APPROVED') || type.includes('COMPLETED') || type.includes('PRESENT')) {
            return { cls: 'bg-success-subtle text-success', icon: 'bi-check-circle-fill' };
        }
        if (type.includes('REJECTED') || type.includes('ABSENT') || type.includes('CANCELLED') || type.includes('NO_SLOT')) {
            return { cls: 'bg-danger-subtle text-danger', icon: 'bi-exclamation-circle-fill' };
        }
        return { cls: 'bg-primary-subtle text-primary', icon: 'bi-calendar2-event-fill' };
    }

    function esc(str) {
        if (!str) return '';
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function renderNotificationPage() {
        if (allNotifications.length === 0) {
            listEl.innerHTML = '<div class="p-4 text-center text-muted small"><i class="bi bi-bell-slash d-block fs-4 mb-1"></i>Chưa có thông báo nào</div>';
            if (paginationBar) paginationBar.classList.add('d-none');
            return;
        }

        const totalPages = Math.max(1, Math.ceil(allNotifications.length / notifPageSize));
        if (notifPage > totalPages) notifPage = totalPages;
        if (notifPage < 1) notifPage = 1;

        const startIdx = (notifPage - 1) * notifPageSize;
        const pageItems = allNotifications.slice(startIdx, startIdx + notifPageSize);

        listEl.innerHTML = pageItems.map(item => {
            const ic = iconForType(item.type);
            const unreadCls = item.isRead ? '' : 'unread';
            return `
                <div class="notif-item ${unreadCls}" data-id="${item.id}" data-url="${esc(item.targetUrl || '')}">
                    <div class="notif-item-icon ${ic.cls}">
                        <i class="bi ${ic.icon}"></i>
                    </div>
                    <div class="flex-grow-1">
                        <div class="notif-item-title">${esc(item.title)}</div>
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
            if (pageInfoEl) pageInfoEl.textContent = `Trang ${notifPage} / ${totalPages} (${allNotifications.length} TB)`;
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
            const totalPages = Math.max(1, Math.ceil(allNotifications.length / notifPageSize));
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

    // Tải số lượng thông báo ngay khi vào trang và cập nhật định kỳ mỗi 20s
    if (localStorage.getItem('token')) {
        loadNotifications();
        setInterval(loadNotifications, 20000);
    }
})();
