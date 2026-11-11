const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');

let browser, server, origin;
before(async () => {
    const root = path.resolve(__dirname, '../com_fe');
    server = http.createServer((request, response) => {
        const file = path.resolve(root, '.' + new URL(request.url, 'http://localhost').pathname);
        if (!file.startsWith(root + path.sep)) { response.writeHead(403); return response.end(); }
        fs.readFile(file, (error, data) => {
            if (error) { response.writeHead(404); return response.end(); }
            response.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' })[path.extname(file)] || 'application/octet-stream');
            response.end(data);
        });
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    origin = `http://127.0.0.1:${server.address().port}`;
    browser = await chromium.launch({ headless: true, channel: 'chrome' });
});
after(async () => {
    if (browser) await browser.close();
    if (server) await new Promise(resolve => server.close(resolve));
});

async function screen(file, role, width) {
    const page = await browser.newPage({ viewport: { width, height: width === 390 ? 844 : 1000 } });
    const errors = [], calls = [];
    page.on('pageerror', error => errors.push(error.message));
    const room = { id: 1, branchId: 1, roomCode: 'R01', roomName: 'Chess room', roomType: 'CHESS_ROOM', capacity: 20, status: 'AVAILABLE', warnings: [] };
    const cls = { id: 1, courseId: 1, branchId: 1, roomId: 1, classCode: 'C01', className: 'Chess class', courseName: 'Chess', branchName: 'Campus', maxStudents: 10, availableSeats: 10, status: 'PLANNING' };
    const equipment = { id: 1, roomId: 1, code: 'E01', name: 'Chess set', category: 'CHESS_SET', status: 'GOOD' };
    const rows = base => Array.from({ length: 23 }, (_, i) => ({ ...base, id: i + 1 }));
    const cache = process.env.CLASS_BOOTSTRAP_CACHE || path.resolve('outputs/class-scheduling-check');
    await page.route('https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/**', route => {
        const name = path.basename(new URL(route.request().url()).pathname);
        return route.fulfill({ path: path.join(cache, name), contentType: name.endsWith('.js') ? 'text/javascript' : 'text/css' });
    });
    await page.route('https://cdn.jsdelivr.net/npm/bootstrap-icons@**', route => route.abort());
    await page.addInitScript(role => {
        localStorage.setItem('token', 'pagination-test');
        localStorage.setItem('user', JSON.stringify({ role, username: 'pagination-test' }));
    }, role);
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const endpoint = url.pathname;
        calls.push(url);
        let body;
        if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' } });
        if (endpoint.endsWith('/page')) {
            let items = endpoint.includes('/requests/') ? rows({ childName: 'Student', preferredCourseName: 'Chess', status: 'READY_FOR_ASSIGNMENT', requestedAt: '2026-11-01T10:00:00', placementRequested: false, cancellable: false })
                : endpoint.endsWith('/rooms/page') ? rows(room)
                : endpoint.endsWith('/equipments/page') ? rows(equipment)
                : endpoint.endsWith('/lessons/page') ? rows({ classId: 1, classCode: 'C01', className: 'Chess class', lessonDate: '2027-01-01', startTime: '09:00:00', endTime: '10:00:00', teacherName: 'Teacher' })
                : rows(cls);
            if (url.searchParams.get('keyword') === 'missing') items = [];
            const index = Number(url.searchParams.get('page')), size = Number(url.searchParams.get('size'));
            body = { items: items.slice(index * size, (index + 1) * size), page: index, size, totalElements: items.length, totalPages: Math.ceil(items.length / size) };
        } else if (endpoint === '/api/notifications') body = {notifications:[], unreadCount:0};
        else if (endpoint === '/api/parent-tuition/notifications') body = [];
        else if (endpoint === '/api/branches' || endpoint === '/api/branches/options') body = [{ id: 1, code: 'B1', name: 'Campus' }];
        else if (endpoint.endsWith('/counts')) body = { rooms: 23, equipment: 23, goodEquipment: 23 };
        else if (endpoint.endsWith('/overview')) body = { room, warnings: [] };
        else if (endpoint.endsWith('/detail')) body = room;
        else if (endpoint === '/api/course-enrollment/courses') body = [{ id: 1, name: 'Chess', code: 'CHESS' }];
        else if (endpoint === '/api/course-enrollment/children') body = [{ id: 1, fullName: 'Student' }];
        else if (endpoint === '/api/class-management/classes/99') body = { ...cls, id: 99, className: 'Off-page class' };
        else if (endpoint.includes('/courses/1/classes')) body = [cls];
        else if (endpoint.endsWith('/teachers') || endpoint.endsWith('/rooms')) body = [];
        else throw new Error('Unmocked endpoint: ' + endpoint);
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body), headers: { 'access-control-allow-origin': '*' } });
    });
    await page.goto(origin + '/pages/' + file);
    return { page, calls, errors };
}

async function navigation(page, pagerId, bodyId) {
    const pager = page.locator('#' + pagerId);
    await pager.getByRole('status').filter({ hasText: '1–10 / 23' }).waitFor();
    assert.equal(await page.locator('#' + bodyId + ' tbody tr, #' + bodyId + ' > tr').count(), 10);
    await pager.getByLabel('Trang sau', { exact: true }).click();
    await pager.getByRole('status').filter({ hasText: '11–20 / 23' }).waitFor();
    await pager.getByLabel('Trang sau', { exact: true }).click();
    await pager.getByRole('status').filter({ hasText: '21–23 / 23' }).waitFor();
    assert.equal(await page.locator('#' + bodyId + ' tbody tr, #' + bodyId + ' > tr').count(), 3);
    assert.equal(await pager.getByLabel('Trang sau', { exact: true }).isDisabled(), true);
    await pager.getByLabel('Số dòng mỗi trang').selectOption('5');
    await pager.getByRole('status').filter({ hasText: '1–5 / 23' }).waitFor();
}

async function layout(page, name, width) {
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    if (process.env.PAGINATION_SCREENSHOT_DIR) {
        fs.mkdirSync(process.env.PAGINATION_SCREENSHOT_DIR, { recursive: true });
        await page.screenshot({ path: path.join(process.env.PAGINATION_SCREENSHOT_DIR, `${name}-${width}.png`) });
    }
}

for (const width of [1440, 390]) {
    test(`class pages, filters and off-page edit at ${width}px`, async () => {
        const { page, calls, errors } = await screen('class-management.html', 'STAFF', width);
        try {
            await navigation(page, 'classPager', 'classRows');
            assert.equal(await page.locator('#resultCount').innerText(), '23 lớp');
            await page.locator('#keyword').fill('missing');
            await page.locator('#filterBtn').click();
            await page.locator('#classPager [role=status]').filter({ hasText: '0–0 / 0' }).waitFor();
            assert.equal(calls.at(-1).searchParams.get('page'), '0');
            await layout(page, 'classes', width);
            await page.goto(origin + '/pages/class-management.html?classId=99');
            await page.locator('#classModal.show').waitFor();
            assert.equal(await page.locator('#className').inputValue(), 'Off-page class');
            assert.deepEqual(errors, []);
        } finally { await page.close(); }
    });

    test(`rooms, equipment and all room detail tables at ${width}px`, async () => {
        const { page, errors } = await screen('branch-facility.html', 'ADMIN', width);
        try {
            await navigation(page, 'roomPager', 'roomTableBody');
            assert.equal(await page.locator('#statRoomsCount').innerText(), '23');
            await page.locator('[data-room-usage="1"]').click();
            for (const kind of ['equipments', 'classes', 'lessons']) await navigation(page, `usage-${kind}-pager`, `usage-${kind}-body`);
            await page.locator('#roomUsageModal .btn-close').click();
            await page.locator('#roomUsageModal').waitFor({ state: 'hidden' });
            await page.locator('#tabEquipments').click();
            await navigation(page, 'equipmentPager', 'equipmentTableBody');
            await page.locator('#equipmentKeyword').fill('missing');
            await page.locator('#equipmentFilters button').click();
            await page.locator('#equipmentPager [role=status]').filter({ hasText: '0–0 / 0' }).waitFor();
            assert.equal(await page.locator('#statEquipmentCount').innerText(), '23');
            await layout(page, 'facilities', width);
            assert.deepEqual(errors, []);
        } finally { await page.close(); }
    });

    test(`parent requests, staff queue and preview equipment at ${width}px`, async () => {
        for (const role of ['PARENT', 'STAFF']) {
            const { page, calls, errors } = await screen('course-enrollment.html', role, width);
            try {
                const own = role === 'PARENT';
                await navigation(page, own ? 'myRequestPager' : 'staffRequestPager', own ? 'myRequestTableBody' : 'pendingTableBody');
                if (own) {
                    await page.locator('#parentFacilityPreviewBtn').click();
                    await navigation(page, 'previewEquipmentPager', 'previewEquipmentBody');
                    await page.locator('#facilityModal .btn-close').click();
                    await page.locator('#facilityModal').waitFor({ state: 'hidden' });
                }
                await page.locator(own ? '#myRequestKeyword' : '#staffRequestKeyword').fill('missing');
                await page.locator(own ? '#myRequestFilters button' : '#staffRequestFilters button').click();
                await page.locator(`#${own ? 'myRequestPager' : 'staffRequestPager'} [role=status]`).filter({ hasText: '0–0 / 0' }).waitFor();
                assert(calls.every(url => !url.pathname.endsWith('/requests/my') && !url.pathname.endsWith('/staff/requests')));
                await layout(page, 'enrollment-' + role.toLowerCase(), width);
                assert.deepEqual(errors, []);
            } finally { await page.close(); }
        }
    });
}
