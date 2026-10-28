(async function () {
    if (!await Admin.ready()) return;
    const { $, escape: esc } = Admin;
    function barChart(points) {
        const max = Math.max(1, ...points.map(p => Number(p.amount)));
        $('revenueChart').innerHTML = points.map(p => `<div class="admin-bar-column"><div class="admin-bar" style="height:${Math.max(Number(p.amount) > 0 ? 2 : 0, Number(p.amount) / max * 150)}px" title="${esc(p.date)}: ${esc(Admin.money(p.amount))}"></div><span>${esc(p.date.slice(8) + '/' + p.date.slice(5, 7))}</span></div>`).join('');
        $('revenueTable').innerHTML = points.map(p => `<tr><td>${esc(p.date)}</td><td class="text-end">${esc(Admin.money(p.amount))}</td></tr>`).join('');
    }
    async function load(event) {
        event?.preventDefault(); Admin.message(); $('refreshDashboard').disabled = true; $('dashboardContent').setAttribute('aria-busy', 'true');
        try {
            const data = await Admin.request('/dashboard?' + Admin.query({ from: $('fromDate').value, to: $('toDate').value }));
            $('fromDate').value = data.from; $('toDate').value = data.to;
            $('revenueTotal').textContent = Admin.money(data.revenue);
            $('transactionCount').textContent = `${data.successfulTransactions} giao dịch thành công trong khoảng ngày đã chọn`;
            $('outstandingAmount').textContent = Admin.money(data.outstandingAmount);
            $('overdueAmount').textContent = Admin.money(data.overdueAmount);
            $('overviewCards').innerHTML = [
                ['people', 'Hồ sơ học viên', data.overview.students, 'course-enrollment'],
                ['person-video3', 'Giáo viên hoạt động', data.overview.teachers, 'admin-users'],
                ['collection', 'Lớp đang mở', data.overview.openClasses, 'class-management'],
                ['person-check', 'Lượt ghi danh chính thức', data.overview.enrollments, 'course-enrollment']
            ].map(([icon, label, value, file]) => `<a class="admin-stat" href="${esc(appPath('/pages/' + file + '.html'))}"><i class="bi bi-${icon}" aria-hidden="true"></i><span>${esc(label)}</span><strong>${esc(value)}</strong></a>`).join('');
            barChart(data.revenueByDay);
            $('requestBreakdown').innerHTML = Object.entries(Admin.statuses).map(([status, label]) => Admin.link('course-enrollment', label, data.enrollmentRequests[status] || 0)).join('');
            $('workQueues').innerHTML = Admin.link('absence-makeup', 'Đơn nghỉ chờ duyệt', data.queues.absencePending)
                + Admin.link('absence-makeup', 'Ca bù chờ xếp lịch', data.queues.makeupPending)
                + Admin.link('absence-makeup', 'Ca bù chưa có lớp phù hợp', data.queues.makeupNoSlot)
                + Admin.link('tuition-payment', 'Hóa đơn chưa thanh toán', data.overview.unpaidInvoices);
            $('roleBreakdown').innerHTML = Admin.roles.map(role => `<li><span>${esc(roleLabel(role))}</span><strong>${esc(data.roles[role] || 0)}</strong></li>`).join('');
            $('accountSummary').textContent = `${data.overview.users} tài khoản · ${data.overview.activeUsers} hoạt động · ${data.overview.inactiveUsers} đã khóa`;
            $('facilitySummary').innerHTML = Admin.link('branch-facility', 'Cơ sở', data.overview.branches) + Admin.link('branch-facility', 'Phòng học', data.overview.rooms)
                + Admin.link('branch-facility', 'Thiết bị', data.overview.equipment) + Admin.link('course-management', 'Khóa học', data.overview.courses);
            $('upcomingTests').innerHTML = data.upcomingTests.length ? data.upcomingTests.map(test => `<li><div><strong>${esc(test.studentName || test.title)}</strong><p>${esc(test.title)}</p></div><time>${esc(Admin.date(test.testDate))}</time></li>`).join('') : '<li>Không có lịch kiểm tra sắp tới.</li>';
        } catch (error) { Admin.message(error.message); }
        finally { $('refreshDashboard').disabled = false; $('dashboardContent').removeAttribute('aria-busy'); }
    }
    $('dashboardFilters').addEventListener('submit', load);
    $('dashboardRange').addEventListener('change', () => {
        const range = $('dashboardRange').value;
        if (range === 'CUSTOM') return;
        const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
        let from = today;
        if (range === 'MONTH') from = today.slice(0, 8) + '01';
        if (range === 'WEEK') { const date = new Date(today + 'T12:00:00Z'); date.setUTCDate(date.getUTCDate() - 6); from = date.toISOString().slice(0, 10); }
        $('fromDate').value = from; $('toDate').value = today; load();
    });
    for (const id of ['fromDate', 'toDate']) $(id).addEventListener('change', () => { $('dashboardRange').value = 'CUSTOM'; });
    await load();
})();
