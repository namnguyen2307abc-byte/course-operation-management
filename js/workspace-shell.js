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
            border: 1px solid rgba(226, 232, 240, 0.9);
            background: #ffffff;
            color: #334155;
            width: 38px;
            height: 38px;
            border-radius: 12px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
            box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
            outline: none;
        }
        .notif-bell-btn:hover {
            background: #f8fafc;
            color: #0f766e;
            border-color: #cbd5e1;
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
        }
        .notif-bell-btn.active {
            background: #f0fdfa;
            color: #0f766e;
            border-color: #99f6e4;
        }
        .notif-bell-btn.has-unread {
            color: #0f766e;
            background: #f0fdfa;
            border-color: #99f6e4;
            box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.15), 0 0 12px rgba(15, 118, 110, 0.25);
            animation: notifBellGlow 2.8s infinite ease-in-out;
        }
        @keyframes notifBellGlow {
            0%, 100% {
                box-shadow: 0 0 0 2px rgba(15, 118, 110, 0.12), 0 0 8px rgba(15, 118, 110, 0.2);
                border-color: #99f6e4;
            }
            50% {
                box-shadow: 0 0 0 4px rgba(15, 118, 110, 0.25), 0 0 16px rgba(15, 118, 110, 0.4);
                border-color: #0f766e;
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
            min-width: 18px;
            height: 18px;
            padding: 0 5px;
            border-radius: 999px;
            background: #ef4444;
            color: #ffffff;
            font-size: 0.68rem;
            font-weight: 700;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            border: 2px solid #ffffff;
            line-height: 1;
            box-shadow: 0 2px 6px rgba(239, 68, 68, 0.4);
        }
        .notif-dropdown-panel {
            position: absolute;
            top: calc(100% + 12px);
            right: 0;
            width: 410px;
            max-width: calc(100vw - 24px);
            background: #ffffff;
            border: 1px solid rgba(226, 232, 240, 0.95);
            border-radius: 16px;
            box-shadow: 0 20px 48px -10px rgba(15, 23, 42, 0.16), 0 4px 16px -2px rgba(15, 23, 42, 0.06);
            z-index: 2050;
            overflow: hidden;
            display: flex;
            flex-direction: column;
            animation: notifFadeIn 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        }
        @keyframes notifFadeIn {
            from { opacity: 0; transform: translateY(-8px) scale(0.98); }
            to { opacity: 1; transform: translateY(0) scale(1); }
        }
        .notif-dropdown-header {
            padding: 14px 18px;
            background: #ffffff;
            border-bottom: 1px solid #f1f5f9;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .notif-header-title {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            font-size: 0.95rem;
            font-weight: 700;
            color: #0f172a;
        }
        .notif-header-unread-pill {
            font-size: 0.72rem;
            font-weight: 700;
            padding: 2px 9px;
            border-radius: 999px;
            background: #fee2e2;
            color: #ef4444;
            display: inline-flex;
            align-items: center;
            letter-spacing: 0.2px;
        }
        .notif-header-unread-pill.all-read {
            background: #f1f5f9;
            color: #64748b;
        }
        .notif-mark-all-btn {
            font-size: 0.82rem;
            font-weight: 600;
            color: #0f766e;
            background: none;
            border: none;
            padding: 4px 8px;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.15s ease;
            display: inline-flex;
            align-items: center;
            gap: 4px;
        }
        .notif-mark-all-btn:hover {
            color: #0d9488;
            background: #f0fdfa;
        }
        .notif-filter-bar {
            padding: 8px 14px;
            background: #f8fafc;
            border-bottom: 1px solid #f1f5f9;
            display: flex;
            gap: 6px;
            align-items: center;
            flex-wrap: wrap;
        }
        .notif-filter-chip {
            padding: 5px 12px;
            border-radius: 999px;
            font-size: 0.74rem;
            font-weight: 600;
            border: 1px solid #e2e8f0;
            background: #ffffff;
            color: #64748b;
            cursor: pointer;
            transition: all 0.15s ease;
            display: inline-flex;
            align-items: center;
            gap: 4px;
            line-height: 1.2;
        }
        .notif-filter-chip:hover {
            background: #f1f5f9;
            color: #334155;
            border-color: #cbd5e1;
        }
        .notif-filter-chip.active {
            background: #0f766e;
            color: #ffffff;
            border-color: #0f766e;
            box-shadow: 0 2px 6px rgba(15, 118, 110, 0.25);
        }
        .notif-filter-chip.chip-alert.active {
            background: #ef4444;
            color: #ffffff;
            border-color: #ef4444;
            box-shadow: 0 2px 6px rgba(239, 68, 68, 0.25);
        }
        .notif-filter-chip.chip-alert:not(.active) {
            color: #ef4444;
        }
        .notif-dropdown-list {
            max-height: 400px;
            overflow-y: auto;
            overscroll-behavior: contain;
            scrollbar-width: thin;
            scrollbar-color: #cbd5e1 transparent;
        }
        .notif-dropdown-list::-webkit-scrollbar {
            width: 5px;
        }
        .notif-dropdown-list::-webkit-scrollbar-track {
            background: transparent;
        }
        .notif-dropdown-list::-webkit-scrollbar-thumb {
            background: #cbd5e1;
            border-radius: 999px;
        }
        .notif-dropdown-list::-webkit-scrollbar-thumb:hover {
            background: #94a3b8;
        }
        .notif-item {
            padding: 13px 16px;
            border-bottom: 1px solid #f1f5f9;
            display: flex;
            gap: 12px;
            cursor: pointer;
            transition: all 0.15s ease;
            position: relative;
            background: #ffffff;
            align-items: flex-start;
            text-align: left;
        }
        .notif-item:last-child {
            border-bottom: none;
        }
        .notif-item:hover {
            background: #f8fafc;
        }
        .notif-item.unread {
            background: #f0fdfa;
        }
        .notif-item.unread::before {
            content: '';
            position: absolute;
            left: 0;
            top: 0;
            bottom: 0;
            width: 3.5px;
            background: #0f766e;
            border-top-right-radius: 2px;
            border-bottom-right-radius: 2px;
        }
        .notif-item.unread:hover {
            background: #e6fffa;
        }
        .notif-item-icon {
            width: 38px;
            height: 38px;
            border-radius: 12px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            font-size: 1.1rem;
            margin-top: 1px;
        }
        .notif-ic-primary { background: #e0f2fe; color: #0284c7; }
        .notif-ic-info { background: #ccfbf1; color: #0f766e; }
        .notif-ic-warning { background: #fef3c7; color: #d97706; }
        .notif-ic-danger { background: #fee2e2; color: #dc2626; }
        .notif-ic-success { background: #dcfce7; color: #16a34a; }

        .notif-item-content {
            flex-grow: 1;
            min-width: 0;
        }
        .notif-item-title-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
            margin-bottom: 3px;
        }
        .notif-item-title {
            font-size: 0.85rem;
            font-weight: 700;
            color: #0f172a;
            line-height: 1.3;
        }
        .notif-tag {
            font-size: 0.65rem;
            font-weight: 700;
            padding: 2px 7px;
            border-radius: 6px;
            line-height: 1.2;
            display: inline-block;
            white-space: nowrap;
            flex-shrink: 0;
            letter-spacing: 0.2px;
        }
        .notif-tag-primary { background: #e0f2fe; color: #0369a1; }
        .notif-tag-info { background: #ccfbf1; color: #0f766e; }
        .notif-tag-warning { background: #fef3c7; color: #b45309; }
        .notif-tag-danger { background: #fee2e2; color: #b91c1c; }
        .notif-tag-success { background: #dcfce7; color: #15803d; }

        .notif-item-msg {
            font-size: 0.79rem;
            color: #475569;
            line-height: 1.45;
            margin-bottom: 6px;
            word-break: break-word;
        }
        .notif-item-meta {
            font-size: 0.71rem;
            color: #94a3b8;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .notif-item-meta-sender {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            font-weight: 500;
        }
        .notif-item-meta-time {
            font-variant-numeric: tabular-nums;
        }
        .notif-pagination-bar {
            padding: 10px 16px;
            background: #f8fafc;
            border-top: 1px solid #f1f5f9;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .notif-page-info {
            font-size: 0.74rem;
            color: #64748b;
            font-weight: 500;
        }
        .notif-page-btn {
            background: #ffffff;
            border: 1px solid #e2e8f0;
            border-radius: 8px;
            padding: 4px 10px;
            font-size: 0.73rem;
            font-weight: 600;
            color: #334155;
            cursor: pointer;
            transition: all 0.15s ease;
            display: inline-flex;
            align-items: center;
            gap: 4px;
        }
        .notif-page-btn:hover:not(:disabled) {
            background: #f1f5f9;
            border-color: #cbd5e1;
            color: #0f172a;
        }
        .notif-page-btn:disabled {
            opacity: 0.4;
            cursor: not-allowed;
            background: #f8fafc;
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
                <div class="notif-header-title">
                    <i class="bi bi-bell-fill" style="color: #0f766e;"></i>
                    <span>Thông báo</span>
                    <span class="notif-header-unread-pill" id="notifHeaderUnreadCount">0 chưa đọc</span>
                </div>
                <button type="button" class="notif-mark-all-btn" id="notifMarkAllBtn">
                    <i class="bi bi-check2-all"></i> Đọc tất cả
                </button>
            </div>
            <div class="notif-filter-bar" id="notifFilterTabs">
                <button type="button" class="notif-filter-chip active" data-filter="ALL" id="notifTabAll">Tất cả (0)</button>
                <button type="button" class="notif-filter-chip" data-filter="UNREAD" id="notifTabUnread">Chưa đọc (0)</button>
                <button type="button" class="notif-filter-chip chip-alert" data-filter="ALERT" id="notifTabAlert"><i class="bi bi-exclamation-triangle-fill"></i>Cảnh báo (0)</button>
            </div>
            <div class="notif-dropdown-list" id="notifDropdownList">
                <div class="p-4 text-center text-muted small">Đang tải thông báo...</div>
            </div>
            <div class="d-none notif-pagination-bar" id="notifPaginationBar">
                <span class="notif-page-info" id="notifPageInfo">Trang 1 / 1</span>
                <div class="d-flex" style="gap: 6px;">
                    <button type="button" class="notif-page-btn" id="notifPrevBtn"><i class="bi bi-chevron-left"></i> Trước</button>
                    <button type="button" class="notif-page-btn" id="notifNextBtn">Sau <i class="bi bi-chevron-right"></i></button>
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
        if (!type) return { cls: 'notif-ic-primary', icon: 'bi-info-circle-fill', tag: '' };
        if (type.includes('ATTENDANCE_CRITICAL') || type.includes('MAKEUP_LIMIT_EXCEEDED')) {
            return { cls: 'notif-ic-danger', icon: 'bi-exclamation-octagon-fill', tag: '<span class="notif-tag notif-tag-danger">Khẩn cấp</span>' };
        }
        if (type.includes('ATTENDANCE_WARNING') || type.includes('MAKEUP_MISSED')) {
            return { cls: 'notif-ic-warning', icon: 'bi-exclamation-triangle-fill', tag: '<span class="notif-tag notif-tag-warning">Cảnh báo</span>' };
        }
        if (type.includes('ABSENCE_CREATED')) {
            return { cls: 'notif-ic-info', icon: 'bi-envelope-plus-fill', tag: '<span class="notif-tag notif-tag-info">Đơn mới</span>' };
        }
        if (type.includes('MAKEUP_SCHEDULED') || type.includes('MAKEUP_COORDINATED') || type.includes('MAKEUP_PENDING')) {
            return { cls: 'notif-ic-primary', icon: 'bi-calendar2-week-fill', tag: '<span class="notif-tag notif-tag-primary">Học bù</span>' };
        }
        if (type.includes('PAYMENT_SUCCESS')) {
            return { cls: 'notif-ic-success', icon: 'bi-cash-coin', tag: '' };
        }
        if (type === 'TUITION_OVERDUE') {
            return { cls: 'notif-ic-danger', icon: 'bi-receipt-cutoff', tag: '<span class="notif-tag notif-tag-danger">Quá hạn</span>' };
        }
        if (type === 'TUITION_DUE_TODAY') {
            return { cls: 'notif-ic-warning', icon: 'bi-wallet2', tag: '<span class="notif-tag notif-tag-warning">Đến hạn</span>' };
        }
        if (type === 'TUITION_UPCOMING' || type === 'TUITION_PLAN_CONFIRMED') {
            return { cls: 'notif-ic-info', icon: 'bi-calendar-check', tag: '' };
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
            return { cls: 'notif-ic-warning', icon: 'bi-receipt-cutoff', tag: '' };
        }
        if (type.includes('ENROLLMENT_APPROVED') || type.includes('ENROLLMENT_COMPLETED')) {
            return { cls: 'notif-ic-success', icon: 'bi-mortarboard-fill', tag: '' };
        }
        if (type.includes('ENROLLMENT_REQUEST')) {
            return { cls: 'notif-ic-info', icon: 'bi-person-plus-fill', tag: '' };
        }
        if (type.includes('PLACEMENT')) {
            return { cls: 'notif-ic-primary', icon: 'bi-clipboard2-check-fill', tag: '' };
        }
        if (type.includes('APPROVED') || type.includes('COMPLETED') || type.includes('PRESENT')) {
            return { cls: 'notif-ic-success', icon: 'bi-check-circle-fill', tag: '' };
        }
        if (type.includes('REJECTED') || type.includes('ABSENT') || type.includes('CANCELLED') || type.includes('NO_SLOT')) {
            return { cls: 'notif-ic-danger', icon: 'bi-exclamation-circle-fill', tag: '' };
        }
        return { cls: 'notif-ic-primary', icon: 'bi-calendar2-event-fill', tag: '' };
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
                ? 'notif-header-unread-pill'
                : 'notif-header-unread-pill all-read';
        }
        if (tabAllBtn) {
            tabAllBtn.textContent = `Tất cả (${total})`;
            tabAllBtn.className = notifFilter === 'ALL'
                ? 'notif-filter-chip active'
                : 'notif-filter-chip';
        }
        if (tabUnreadBtn) {
            tabUnreadBtn.textContent = `Chưa đọc (${unread})`;
            tabUnreadBtn.className = notifFilter === 'UNREAD'
                ? 'notif-filter-chip active'
                : 'notif-filter-chip';
        }
        if (tabAlertBtn) {
            tabAlertBtn.innerHTML = `<i class="bi bi-exclamation-triangle-fill"></i>Cảnh báo (${alertCount})`;
            tabAlertBtn.className = notifFilter === 'ALERT'
                ? 'notif-filter-chip chip-alert active'
                : 'notif-filter-chip chip-alert';
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
            listEl.innerHTML = '<div class="p-4 text-center text-muted small"><i class="bi bi-bell-slash d-block fs-4 mb-2 text-secondary"></i>Không có thông báo nào trong mục này</div>';
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
                    <div class="notif-item-content">
                        <div class="notif-item-title-row">
                            <span class="notif-item-title">${esc(item.title)}</span>
                            ${ic.tag || ''}
                        </div>
                        <div class="notif-item-msg">${esc(item.message)}</div>
                        <div class="notif-item-meta">
                            <span class="notif-item-meta-sender"><i class="bi bi-person me-1"></i>${esc(item.senderName || 'Hệ thống')}</span>
                            <span class="notif-item-meta-time"><i class="bi bi-clock me-1"></i>${esc(item.createdAtText || '')}</span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        if (paginationBar) {
            paginationBar.classList.remove('d-none');
            if (pageInfoEl) pageInfoEl.textContent = `Trang ${notifPage} / ${totalPages} (${filteredList.length} thông báo)`;
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
        bellBtn.classList.toggle('active', isHidden);
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
            bellBtn.classList.remove('active');
        }
    });

    // Tải số lượng thông báo ngay khi vào trang và cập nhật định kỳ mỗi 15s
    if (localStorage.getItem('token')) {
        loadNotifications();
        setInterval(loadNotifications, 15000);
    }
})();
