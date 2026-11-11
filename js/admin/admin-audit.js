(async function () {
    if (!await Admin.ready()) return;
    const { $, escape: esc } = Admin;
    Admin.options($('actionFilter'), Object.keys(Admin.actions), value => Admin.actions[value], 'Tất cả thao tác');
    const targetId = new URLSearchParams(window.location.search).get('targetId');
    if (targetId && /^\d+$/.test(targetId)) $('targetId').value = targetId;
    let generation = 0;
    async function load(page = 0) {
        const requestId = ++generation; Admin.message(); $('auditTable').setAttribute('aria-busy', 'true');
        try {
            const data = await Admin.request('/audit-logs?' + Admin.query({ page, size: 5, keyword: $('keyword').value.trim(), action: $('actionFilter').value, targetId: $('targetId').value, from: $('fromDate').value, to: $('toDate').value }));
            if (requestId !== generation) return;
            $('auditBody').innerHTML = data.items.length ? data.items.map(log => `<tr><td>${esc(Admin.date(log.createdAt))}</td><td>${esc(log.actorUsername)}</td><td>${esc(log.targetUsername)}<small>#${log.targetId}</small></td><td><span class="admin-badge">${esc(Admin.actions[log.action] || log.action)}</span></td><td class="admin-audit-details">${esc(log.details)}</td></tr>`).join('') : '<tr><td colspan="5" class="admin-empty">Chưa có thao tác quản trị trong bộ lọc này.</td></tr>';
            Admin.pagination(data, load);
        } catch (e) { if (requestId === generation) Admin.message(e.message); }
        finally { if (requestId === generation) $('auditTable').removeAttribute('aria-busy'); }
    }
    $('auditFilters').addEventListener('submit', event => { event.preventDefault(); load(0); }); await load();
})();
