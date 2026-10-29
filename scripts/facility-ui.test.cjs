const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');

let browser;
let server;
let origin = process.env.FACILITY_TEST_URL;
before(async () => {
    if (!origin) {
        const root = path.resolve(__dirname, '../com_fe');
        server = http.createServer((request, response) => {
            const filename = path.resolve(root, '.' + new URL(request.url, 'http://localhost').pathname);
            if (!filename.startsWith(root + path.sep)) { response.writeHead(403); return response.end(); }
            fs.readFile(filename, (error, data) => {
                if (error) { response.writeHead(404); return response.end(); }
                response.setHeader('Content-Type', ({ '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript' })[path.extname(filename)] || 'application/octet-stream');
                response.end(data);
            });
        });
        await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
        origin = `http://127.0.0.1:${server.address().port}`;
    }
    browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' });
});
after(async () => {
    if (browser) await browser.close();
    if (server) await new Promise(resolve => server.close(resolve));
});

function deferred() {
    let resolve;
    const promise = new Promise(done => { resolve = done; });
    return { promise, resolve };
}

async function facilityPage(role = 'ADMIN', viewport = { width: 1440, height: 1000 }, configure = () => {}) {
    const page = await browser.newPage({ viewport });
    const state = {
        requests: [], errors: [], unexpected: [], roomConflict: true, broken: false,
        intercept: async () => null,
        rooms: [
            { id: 11, branchId: 1, roomCode: 'POOL', roomName: 'Bể bơi A', roomType: 'SWIMMING_POOL', capacity: 12, status: 'AVAILABLE', description: 'Khu bơi trong nhà', warnings: ['Cần kiểm tra dụng cụ cứu hộ'], goodEquipmentCount: 1, totalEquipmentCount: 1 },
            { id: 12, branchId: 1, roomCode: 'SPORT', roomName: 'Sân thể thao', roomType: 'SPORTS_AREA', capacity: 16, status: 'AVAILABLE', warnings: [], goodEquipmentCount: 0, totalEquipmentCount: 0 }
        ],
        equipment: { id: 31, code: 'RESCUE-1', name: 'Bộ cứu hộ', roomId: 11, roomName: 'Bể bơi A', category: 'RESCUE_EQUIPMENT', status: 'GOOD', serialNumber: 'SN-31', description: 'Một bộ cứu hộ' }
    };
    page.on('pageerror', error => state.errors.push(error.message));
    page.on('dialog', dialog => { state.errors.push(`Unexpected dialog: ${dialog.message()}`); return dialog.dismiss(); });
    const cache = process.env.FACILITY_BOOTSTRAP_CACHE || process.env.CLASS_BOOTSTRAP_CACHE;
    if (cache) {
        await page.route('https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/**', route => {
            const filename = path.basename(new URL(route.request().url()).pathname);
            return route.fulfill({ path: path.join(cache, filename), contentType: filename.endsWith('.js') ? 'text/javascript' : 'text/css' });
        });
    }
    await page.addInitScript(role => {
        localStorage.setItem('token', 'facility-ui-test');
        localStorage.setItem('user', JSON.stringify({ role, username: 'facility-ui-test' }));
    }, role);
    await page.route('**/api/**', async route => {
        const request = route.request();
        const pathname = new URL(request.url()).pathname;
        const method = request.method();
        const payload = method === 'PUT' ? request.postDataJSON() : null;
        if (method !== 'OPTIONS') state.requests.push({ pathname, method, payload });
        let result = method === 'OPTIONS' ? { status: 204, body: null } : await state.intercept({ pathname, method, payload });
        if (!result && method === 'GET' && pathname === '/api/branches') result = { body: [{ id: 1, name: 'Cơ sở A', address: 'Quận 1' }, { id: 2, name: 'Cơ sở B', address: 'Quận 2' }] };
        if (!result && method === 'GET' && /^\/api\/branches\/\d+\/rooms$/.test(pathname)) {
            result = { body: pathname.includes('/1/') ? state.rooms : [{ ...state.rooms[0], id: 21, branchId: 2, roomName: 'Phòng cơ sở B' }] };
        }
        if (!result && method === 'GET' && /^\/api\/branches\/\d+\/equipments$/.test(pathname)) result = { body: pathname.includes('/1/') ? [state.equipment] : [] };
        if (!result && method === 'GET' && /^\/api\/branches\/rooms\/\d+\/usage$/.test(pathname)) {
            const id = Number(pathname.split('/')[4]);
            result = { body: {
                room: id === 21 ? { ...state.rooms[0], id: 21, branchId: 2, roomName: 'Phòng cơ sở B' } : state.rooms.find(room => room.id === id), equipments: id === 11 ? [state.equipment] : [],
                warnings: id === 11 ? ['Cảnh báo lịch sử dụng <script>bad()</script>'] : [],
                classes: id === 11 ? [{ id: 71, classCode: 'SWIM-01', className: 'Bơi cơ bản', courseName: 'Bơi lội', maxStudents: 10, status: 'IN_PROGRESS' }] : [],
                upcomingLessonCount: id === 11 ? 2 : 0,
                upcomingLessons: id === 11 ? [
                    { id: 92, classId: 71, classCode: 'SWIM-01', className: 'Bơi cơ bản', lessonDate: '2027-02-06', startTime: '10:00:00', endTime: '11:00:00', teacherName: 'Cô Lan' },
                    { id: 91, classId: 71, classCode: 'SWIM-01', className: 'Bơi cơ bản', lessonDate: '2027-02-03', startTime: '08:00:00', endTime: '09:00:00', teacherName: 'Thầy Minh' }
                ] : []
            } };
        }
        if (!result && method === 'PUT' && pathname === '/api/branches/rooms/11') {
            if (state.roomConflict) result = { status: 409, body: { message: 'Không thể bảo trì: lớp SWIM-01 có buổi học ngày 03/02/2027.' } };
            else { Object.assign(state.rooms[0], payload); result = { body: state.rooms[0] }; }
        }
        if (!result && method === 'PUT' && pathname === '/api/branches/equipments/31') {
            Object.assign(state.equipment, payload);
            state.broken = payload.status === 'BROKEN';
            state.rooms[0].goodEquipmentCount = state.broken ? 0 : 1;
            state.rooms[0].warnings = state.broken ? ['Dụng cụ cứu hộ bị hỏng'] : [];
            result = { body: { ...state.equipment, warnings: state.broken ? ['Đã lưu thiết bị hỏng; cần thay thế trước buổi học.'] : [], affectedLessonCount: state.broken ? 2 : 0 } };
        }
        if (!result) { state.unexpected.push(`${method} ${pathname}`); result = { status: 404, body: { message: 'Unexpected mocked endpoint' } }; }
        await route.fulfill({ status: result.status || 200, contentType: 'application/json', body: method === 'OPTIONS' ? '' : JSON.stringify(result.body),
            headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': 'GET,PUT,OPTIONS' } });
    });
    configure(state);
    await page.goto(origin + '/pages/branch-facility.html' + (state.search || ''));
    return { page, state };
}

async function checkLayout(page, modal) {
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'page must not overflow horizontally');
    if (modal) {
        await page.waitForFunction(selector => {
            const element = document.querySelector(selector);
            return getComputedStyle(element).opacity === '1' && getComputedStyle(element.querySelector('.modal-dialog')).transform === 'none';
        }, modal);
        const modalBounds = await page.locator(modal + ' .modal-content').evaluate(element => {
            const bounds = element.getBoundingClientRect();
            const dialog = element.closest('.modal-dialog'), modal = element.closest('.modal');
            return {left:bounds.left,right:bounds.right,width:innerWidth,
                dialogLeft:dialog.getBoundingClientRect().left, modalLeft:modal.getBoundingClientRect().left,
                padding:getComputedStyle(modal).padding, scrollLeft:modal.scrollLeft};
        });
        assert.equal(modalBounds.left >= 0 && modalBounds.right <= modalBounds.width + 1,
            true, 'modal must fit viewport: ' + JSON.stringify(modalBounds));
        assert.equal(await page.locator(modal + ' .modal-footer').evaluate(element => element.getBoundingClientRect().bottom <= innerHeight + 1), true, 'modal footer must remain visible');
    }
}

async function screenshot(page, name) {
    if (!process.env.FACILITY_SCREENSHOT_DIR) return;
    fs.mkdirSync(process.env.FACILITY_SCREENSHOT_DIR, { recursive: true });
    await page.screenshot({ path: path.join(process.env.FACILITY_SCREENSHOT_DIR, name + '.png'), fullPage: false });
}

for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    test(`facility details, conflict draft and saved BROKEN warning at ${viewport.width}px`, async () => {
        const { page, state } = await facilityPage('ROLE_ADMIN', viewport);
        try {
            await page.locator('[data-room-usage="11"]').waitFor();
            assert.match(await page.locator('#roomTableBody').innerText(), /Bể bơi[\s\S]*Khu thể thao/);
            assert.match(await page.locator('#roomTableBody').innerText(), /Thiết bị tốt: 1\/1/);
            assert.match(await page.locator('#roomTableBody').innerText(), /Cần kiểm tra/);
            assert.equal(await page.getByRole('button', { name: /Thêm|Tạo mới/ }).count(), 0);
            await page.locator('[data-room-usage="11"]').click();
            await page.locator('#roomUsageBody').getByText('Thầy Minh').waitFor();
            assert.match(await page.locator('#roomUsageBody').innerText(), /Dụng cụ cứu hộ[\s\S]*Hoạt động tốt/);
            assert.equal(await page.locator('#roomUsageBody script').count(), 0);
            assert.equal(await page.locator('#roomUsageBody a').first().getAttribute('href'), '/pages/class-management.html?classId=71');
            const lessonRows = await page.locator('#roomUsageBody section').last().locator('tbody tr').allTextContents();
            assert.match(lessonRows[0], /03\/02\/2027[\s\S]*08:00[\s\S]*Thầy Minh/);
            assert.match(lessonRows[1], /06\/02\/2027/);
            await checkLayout(page, '#roomUsageModal');
            await screenshot(page, `facility-usage-${viewport.width}`);
            await page.locator('#roomUsageModal .btn-close').click();
            await page.locator('#roomUsageModal').waitFor({ state: 'hidden' });
            await page.locator('[data-edit-room="11"]').click();
            await page.locator('#editFacilityName').fill('Bể bơi đã chỉnh sửa');
            await page.locator('#editRoomCapacity').fill('4');
            await page.locator('#editFacilityStatus').selectOption('MAINTENANCE');
            await page.locator('#editFacilityDescription').fill('Giữ lại bản nháp');
            await page.locator('#saveFacilityBtn').click();
            await page.locator('#editFacilityError').getByText(/03\/02\/2027/).waitFor();
            assert.equal(await page.locator('#editFacilityName').inputValue(), 'Bể bơi đã chỉnh sửa');
            assert.equal(await page.locator('#editRoomCapacity').inputValue(), '4');
            await checkLayout(page, '#editFacilityModal');
            await screenshot(page, `facility-conflict-${viewport.width}`);
            await page.locator('#viewRoomUsageBtn').click();
            await page.locator('#roomUsageBody').getByText('Thầy Minh').waitFor();
            assert.equal(await page.locator('.modal.show').count(), 1);
            await page.locator('#backToFacilityEditBtn').click();
            await page.locator('#editFacilityModal.show').waitFor();
            assert.equal(await page.locator('#editFacilityName').inputValue(), 'Bể bơi đã chỉnh sửa');
            assert.equal(await page.locator('#editFacilityStatus').inputValue(), 'MAINTENANCE');
            assert.equal(await page.locator('#editFacilityDescription').inputValue(), 'Giữ lại bản nháp');
            assert.match(await page.locator('#editFacilityError').innerText(), /SWIM-01/);
            state.roomConflict = false;
            await page.locator('#saveFacilityBtn').click();
            await page.locator('#editFacilityModal').waitFor({ state: 'hidden' });
            await page.locator('#facilityNotice').getByText('Đã lưu thay đổi phòng học.').waitFor();
            await page.locator('#tabEquipments').click();
            await page.locator('[data-edit-equipment="31"]').click();
            assert.equal(await page.locator('input[name="quantity"], #quantity, #editEquipmentQuantity').count(), 0);
            assert.equal(await page.locator('#editFacilityCategory').inputValue(), 'RESCUE_EQUIPMENT');
            await page.locator('#editFacilityStatus').selectOption('BROKEN');
            await page.locator('#saveFacilityBtn').click();
            await page.locator('#editFacilityModal').waitFor({ state: 'hidden' });
            await page.locator('#facilityNotice .alert-success').getByText('Đã lưu thay đổi thiết bị.').waitFor();
            assert.match(await page.locator('#facilityNotice .alert-warning').innerText(), /Đã lưu thiết bị hỏng[\s\S]*2 buổi học/);
            await page.locator('#equipmentTableBody').getByText('Hỏng', { exact: true }).waitFor();
            await page.locator('#tabRooms').click();
            await page.locator('#roomTableBody').getByText('Dụng cụ cứu hộ bị hỏng').waitFor();
            assert.match(await page.locator('#roomTableBody').innerText(), /Thiết bị tốt: 0\/1/);
            assert.equal(state.requests.findLast(request => request.method === 'PUT').payload.status, 'BROKEN');
            assert.equal(state.requests.some(request => ['POST', 'DELETE'].includes(request.method)), false);
            await checkLayout(page);
            assert.deepEqual(state.errors, []);
            assert.deepEqual(state.unexpected, []);
        } finally { await page.close(); }
    });
}

test('usage and class links respect role boundaries; only Admin edits', async () => {
    for (const role of ['ADMIN', 'STAFF', 'BRANCH_MANAGER', 'TEACHER', 'PARENT', 'STUDENT']) {
        const { page, state } = await facilityPage(role);
        try {
            await page.locator('#roomTableBody').getByText('Bể bơi A', { exact: true }).waitFor();
            const hasUsage = ['ADMIN', 'STAFF', 'BRANCH_MANAGER', 'TEACHER'].includes(role);
            assert.equal(await page.locator('[data-room-usage]').count(), hasUsage ? 2 : 0, role);
            assert.equal(await page.locator('[data-edit-room]').count(), role === 'ADMIN' ? 2 : 0, role);
            await page.locator('#tabEquipments').click();
            await page.locator('#equipmentTableBody').getByText('Bộ cứu hộ', { exact: true }).waitFor();
            assert.equal(await page.locator('[data-edit-equipment]').count(), role === 'ADMIN' ? 1 : 0, role);
            await page.locator('#tabRooms').click();
            if (hasUsage) {
                await page.locator('[data-room-usage="11"]').click();
                await page.locator('#roomUsageBody').getByText('Thầy Minh').waitFor();
                assert.equal(await page.locator('#roomUsageBody a').count(), role === 'TEACHER' ? 0 : 3, role);
            } else {
                await page.evaluate(() => { openRoomUsage('11'); openFacilityEditor('room', '11'); });
                assert.equal(await page.locator('.modal.show').count(), 0);
                assert.equal(state.requests.filter(request => request.pathname.endsWith('/usage')).length, 0);
            }
            assert.deepEqual(state.errors, []);
            assert.deepEqual(state.unexpected, []);
        } finally { await page.close(); }
    }
});

test('usage errors retry locally, stale room/usage responses cannot replace current selection', async () => {
    const { page, state } = await facilityPage();
    try {
        await page.locator('[data-room-usage="11"]').waitFor();
        let failure = 503;
        state.intercept = async ({ pathname }) => pathname.endsWith('/11/usage') && failure
            ? { status: failure, body: { message: failure === 403 ? 'Không có quyền xem lịch phòng' : 'Lịch phòng tạm thời không tải được' } } : null;
        await page.locator('[data-room-usage="11"]').click();
        await page.locator('#roomUsageBody').getByText('Lịch phòng tạm thời không tải được').waitFor();
        failure = 403;
        await page.locator('[data-facility-retry="usage"]').click();
        await page.locator('#roomUsageBody').getByText('Không có quyền xem lịch phòng').waitFor();
        assert.equal(page.url(), origin + '/pages/branch-facility.html');
        failure = 0;
        await page.locator('[data-facility-retry="usage"]').click();
        await page.locator('#roomUsageBody').getByText('Thầy Minh').waitFor();
        await page.locator('#roomUsageModal .btn-close').click();
        await page.locator('#roomUsageModal').waitFor({ state: 'hidden' });

        const oldRooms = deferred(), roomsStarted = deferred();
        let holdRooms = true;
        state.intercept = async ({ pathname }) => {
            if (pathname === '/api/branches/1/rooms' && holdRooms) {
                holdRooms = false;
                roomsStarted.resolve();
                await oldRooms.promise;
                return { status: 500, body: { message: 'Stale room failure' } };
            }
            return null;
        };
        await page.getByRole('button', { name: 'Làm mới dữ liệu' }).click();
        await roomsStarted.promise;
        await page.locator('#branchSelect').selectOption('2');
        await page.locator('#roomTableBody').getByText('Phòng cơ sở B').waitFor();
        await page.locator('#branchSelect').selectOption('1');
        await page.locator('[data-room-usage="11"]').waitFor();
        const oldRoomResponse = page.waitForResponse(response => response.url().endsWith('/api/branches/1/rooms') && response.status() === 500);
        oldRooms.resolve();
        await oldRoomResponse;
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
        assert.equal(await page.getByText('Stale room failure').count(), 0);
        assert.equal(await page.locator('[data-room-usage="11"]').count(), 1);

        const oldUsage = deferred(), usageStarted = deferred();
        state.intercept = async ({ pathname }) => {
            if (pathname.endsWith('/11/usage')) {
                usageStarted.resolve();
                await oldUsage.promise;
                return { status: 500, body: { message: 'Stale usage failure' } };
            }
            return null;
        };
        await page.locator('[data-room-usage="11"]').click();
        await usageStarted.promise;
        await page.locator('#roomUsageBody').getByRole('status').waitFor();
        await page.locator('#roomUsageModal .btn-close').click();
        await page.locator('#roomUsageModal').waitFor({ state: 'hidden' });
        await page.locator('[data-room-usage="12"]').click();
        await page.locator('#roomUsageTitle').getByText('SPORT · Sân thể thao').waitFor();
        const oldUsageResponse = page.waitForResponse(response => response.url().endsWith('/11/usage') && response.status() === 500);
        oldUsage.resolve();
        await oldUsageResponse;
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
        assert.match(await page.locator('#roomUsageTitle').innerText(), /Sân thể thao/);
        assert.equal(await page.getByText('Stale usage failure').count(), 0);
        assert.match(await page.locator('#roomUsageBody').innerText(), /Phòng chưa có thiết bị[\s\S]*Chưa có lớp[\s\S]*Chưa có buổi học/);
        assert.deepEqual(state.errors, []);
        assert.deepEqual(state.unexpected, []);
    } finally { await page.close(); }
});

test('roomId links open usage directly and select its branch only for authorized roles', async () => {
    for (const role of ['ADMIN', 'STAFF', 'BRANCH_MANAGER', 'TEACHER', 'PARENT', 'STUDENT']) {
        const { page, state } = await facilityPage(role, { width: 390, height: 844 }, state => { state.search = '?roomId=21'; });
        try {
            const authorized = ['ADMIN', 'STAFF', 'BRANCH_MANAGER', 'TEACHER'].includes(role);
            if (authorized) {
                await page.locator('#roomUsageTitle').getByText('POOL · Phòng cơ sở B').waitFor();
                await page.locator('[data-room-usage="21"]').waitFor({ state: 'attached' });
                assert.equal(await page.locator('#branchSelect').inputValue(), '2', role);
                assert.equal(state.requests.filter(request => request.pathname === '/api/branches/rooms/21/usage').length, 1, role);
                assert.equal(await page.locator('#backToFacilityEditBtn').isVisible(), false);
                await checkLayout(page, '#roomUsageModal');
            } else {
                await page.locator('#facilityNotice').getByText('Bạn không có quyền xem lịch sử dụng phòng.').waitFor();
                assert.equal(await page.locator('#branchSelect').inputValue(), '1', role);
                assert.equal(await page.locator('.modal.show').count(), 0);
                assert.equal(state.requests.some(request => request.pathname.endsWith('/usage')), false, role);
            }
            assert.deepEqual(state.errors, []);
            assert.deepEqual(state.unexpected, []);
        } finally { await page.close(); }
    }
});

test('branch, room and equipment failures expose working retries', async () => {
    const remainingFailures = new Set(['/api/branches', '/api/branches/1/rooms', '/api/branches/1/equipments']);
    const { page, state } = await facilityPage('ADMIN', undefined, state => {
        state.intercept = async ({ pathname }) => {
            if (!remainingFailures.delete(pathname)) return null;
            return { status: 503, body: { message: 'Tạm thời không tải được ' + pathname } };
        };
    });
    try {
        await page.locator('[data-facility-retry="branches"]').click();
        await page.locator('[data-facility-retry="rooms"]').click();
        await page.locator('[data-room-usage="11"]').waitFor();
        await page.locator('#tabEquipments').click();
        await page.locator('[data-facility-retry="equipments"]').click();
        await page.locator('[data-edit-equipment="31"]').waitFor();
        assert.equal(await page.locator('#statGoodCount').innerText(), '1/1');
        assert.equal(await page.locator('#facilityLoadError').isVisible(), false);
        assert.deepEqual(state.errors, []);
        assert.deepEqual(state.unexpected, []);
    } finally { await page.close(); }
});

test('an in-flight save blocks duplicate submission and modal dismissal', async () => {
    const { page, state } = await facilityPage();
    const release = deferred(), started = deferred();
    try {
        await page.locator('[data-edit-room="11"]').click();
        state.intercept = async ({ method, pathname, payload }) => {
            if (method !== 'PUT') return null;
            assert.equal(pathname, '/api/branches/rooms/11');
            started.resolve();
            await release.promise;
            return { body: { ...state.rooms[0], ...payload } };
        };
        await page.locator('#saveFacilityBtn').click();
        await started.promise;
        assert.equal(await page.locator('#saveFacilityBtn').isDisabled(), true);
        assert.equal(await page.locator('#branchSelect').isDisabled(), true);
        await page.evaluate(() => {
            document.getElementById('editFacilityForm').dispatchEvent(new Event('submit', { cancelable: true }));
            bootstrap.Modal.getInstance(document.getElementById('editFacilityModal')).hide();
        });
        assert.equal(await page.locator('#editFacilityModal.show').count(), 1);
        assert.equal(state.requests.filter(request => request.method === 'PUT').length, 1);
        release.resolve();
        await page.locator('#editFacilityModal').waitFor({ state: 'hidden' });
        await page.locator('#facilityNotice .alert-success').waitFor();
        assert.equal(await page.locator('#branchSelect').isDisabled(), false);
        assert.deepEqual(state.errors, []);
        assert.deepEqual(state.unexpected, []);
    } finally { release.resolve(); await page.close(); }
});
