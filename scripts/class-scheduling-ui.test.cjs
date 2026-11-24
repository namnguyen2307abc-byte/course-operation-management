const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const http = require('node:http');
const { chromium } = require('playwright');

test('class form filters assignments, calculates end date and displays a scheduling rejection', async () => {
    const root = path.resolve(__dirname, '../com_fe');
    const server = http.createServer((req, res) => {
        const file = path.resolve(root, '.' + new URL(req.url, 'http://local').pathname);
        if (!file.startsWith(root + path.sep)) { res.writeHead(403); return res.end(); }
        fs.readFile(file, (error, data) => {
            if (error) { res.writeHead(404); return res.end(); }
            res.setHeader('Content-Type', {'.html':'text/html', '.js':'text/javascript', '.css':'text/css'}[path.extname(file)] || 'application/octet-stream');
            res.end(data);
        });
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const origin = process.env.CLASS_TEST_URL || 'http://127.0.0.1:' + server.address().port;
    const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' });
    try {
        for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
            const page = await browser.newPage({ viewport });
            if (process.env.CLASS_BOOTSTRAP_CACHE) {
                await page.route('https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/**', route => {
                    const file = path.basename(new URL(route.request().url()).pathname);
                    return route.fulfill({ path: path.join(process.env.CLASS_BOOTSTRAP_CACHE, file),
                        contentType: file.endsWith('.js') ? 'text/javascript' : 'text/css' });
                });
            }
            const errors = [];
            page.on('pageerror', error => { errors.push(error.message); console.error('Browser error:', error.message); });
            page.on('dialog', dialog => dialog.accept());
            await page.addInitScript(() => {
                localStorage.setItem('token', 'ui-test-only');
                localStorage.setItem('user', JSON.stringify({ role: 'STAFF', username: 'ui-test' }));
            });
            let payload;
            let classRows = [];
            let failTeacherLoading = false;
            let extraRooms = false;
            await page.route('**/api/**', async route => {
                const request = route.request();
                const url = new URL(request.url());
                let body = [];
                let status = 200;
                if (url.pathname === '/api/class-management/classes/page') body = { items: classRows, page: 0, size: 10, totalElements: classRows.length, totalPages: classRows.length ? 1 : 0 };
                if (url.pathname === '/api/course-enrollment/courses') body = [
                    { id: 1, code: 'PIANO', name: 'Piano', totalSessions: 12 },
                    { id: 2, code: 'GUITAR', name: 'Guitar', totalSessions: 24 },
                    { id: 3, code: 'OLD', name: 'Legacy course', totalSessions: 20 }
                ];
                if (url.pathname === '/api/branches') body = [
                    { id: 1, code: 'B1', name: 'Campus', active: true },
                    { id: 2, code: 'B2', name: 'Inactive campus', active: false }
                ];
                if (url.pathname.endsWith('/classes/teachers')) {
                    const piano = url.searchParams.get('courseId') === '1';
                    body = [{ id: piano ? 11 : 12, fullName: piano ? 'Piano teacher' : 'Guitar teacher', subject: piano ? 'PIANO' : 'GUITAR' }];
                    if (failTeacherLoading) {
                        status = 500;
                        body = { message: 'Teacher options unavailable' };
                    }
                }
                if (url.pathname.endsWith('/classes/rooms')) {
                    const piano = url.searchParams.get('courseId') === '1';
                    const roomId = piano ? 21 : 22;
                    const candidates = extraRooms
                        ? [{ id: roomId+200, roomCode:'R16', roomName:'Large room', capacity:16 },
                           { id: roomId+100, roomCode:'R12', roomName:'Medium room', capacity:12 },
                           { id: roomId, roomCode:piano?'P1':'G1', roomName:piano?'Piano room':'Guitar room', capacity:8 }]
                        : [{ id:roomId, roomCode:piano?'P1':'G1', roomName:piano?'Piano room':'Guitar room', capacity:8 }];
                    body = candidates.filter(room => room.capacity >= Number(url.searchParams.get('maxStudents')));
                }
                if (request.method() === 'POST' || request.method() === 'PUT') {
                    payload = request.postDataJSON();
                    status = 409;
                    body = { message: 'Giáo viên bị trùng lịch hoặc chưa đủ 30 phút nghỉ' };
                }
                await route.fulfill({ status: request.method() === 'OPTIONS' ? 204 : status,
                    headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*',
                        'access-control-allow-methods': 'GET,POST,PUT,OPTIONS' },
                    contentType: 'application/json', body: request.method() === 'OPTIONS' ? '' : JSON.stringify(body) });
            });
            await page.goto(origin + '/pages/class-management.html');
            await page.locator('#courseId option[value="1"]').waitFor({ state: 'attached' });
            await page.locator('#newClassBtn').click();
            await page.locator('#courseId').selectOption('3');
            await page.locator('#branchId').selectOption('1');
            await page.locator('#classCode').fill('LEGACY');
            await page.locator('#className').fill('Legacy class');
            await page.locator('#saveBtn').click();
            await page.locator('#formError').filter({hasText:'cập nhật khóa học'}).waitFor();
            assert.equal(payload, undefined);
            await page.locator('#branchId').selectOption('');
            assert.match(await page.locator('#filterBranch option[value="2"]').textContent(), /Ngừng hoạt động/);
            assert.equal(await page.locator('#branchId option[value="2"]').count(), 0);
            await page.locator('#courseId').selectOption('1');
            assert.equal(await page.locator('#totalSessions').inputValue(), '12');
            assert.equal(await page.locator('#totalSessions').evaluate(el => el.readOnly), true);
            await page.locator('#teacherId option[value="11"]').waitFor({ state: 'attached' });
            assert.equal(await page.locator('#teacherId option').count(), 2);
            await page.locator('#teacherId').selectOption('11');
            await page.locator('#branchId').selectOption('1');
            await page.locator('#roomId option[value="21"]').waitFor({ state: 'attached' });
            await page.locator('#roomId').selectOption('21');
            extraRooms = true;
            await page.locator('#maxStudents').fill('6');
            await page.locator('#maxStudents').press('Tab');
            await page.getByRole('button',{name:'Xem thêm 2 phòng phù hợp',exact:true}).waitFor();
            assert.equal(await page.locator('#roomId option').count(),2);
            assert.equal(await page.locator('#roomId').inputValue(),'21');
            await page.locator('#toggleMoreRoomsBtn').click();
            await page.waitForFunction(() => document.querySelectorAll('#roomId option').length === 4);
            assert.equal(await page.locator('#roomId option').count(),4);
            await page.locator('#roomId').selectOption('121');
            await page.locator('#toggleMoreRoomsBtn').click();
            await page.waitForFunction(() => document.querySelectorAll('#roomId option').length === 3);
            assert.equal(await page.locator('#roomId').inputValue(),'121');
            assert.equal(await page.locator('#roomId option').count(),3);
            await page.locator('#maxStudents').fill('13');
            await page.locator('#maxStudents').press('Tab');
            await page.locator('#roomId option[value="221"]').waitFor({state:'attached'});
            assert.equal(await page.locator('#roomId option[value="21"]').count(),0);
            assert.equal(await page.locator('#roomId').inputValue(),'');
            extraRooms = false;
            await page.locator('#maxStudents').fill('6');
            await page.locator('#maxStudents').press('Tab');
            await page.locator('#roomId option[value="21"]').waitFor({state:'attached'});
            await page.locator('#roomId').selectOption('21');
            await page.locator('#courseId').selectOption('2');
            assert.equal(await page.locator('#totalSessions').inputValue(), '24');
            await page.locator('#teacherId option[value="12"]').waitFor({ state: 'attached' });
            await page.locator('#roomId option[value="22"]').waitFor({ state: 'attached' });
            assert.equal(await page.locator('#teacherId').inputValue(), '');
            assert.equal(await page.locator('#teacherId option[value="11"]').count(), 0);
            await page.locator('#teacherId').selectOption('12');
            await page.locator('#roomId').selectOption('22');
            await page.locator('#startDate').fill('2027-01-01');
            await page.locator('#startDate').dispatchEvent('change');
            // A changed DOM value must not override the selected course in the request.
            await page.locator('#totalSessions').evaluate(el => {el.value = '1';});
            assert.equal(await page.locator('#endDate').inputValue(), '2027-02-24');
            assert.equal(await page.locator('#endDate').evaluate(element => element.readOnly), true);
            await page.locator('#classCode').fill('UI-TEST');
            await page.locator('#className').fill('Class scheduling test');
            await page.locator('#status').selectOption('OPEN');
            await page.locator('#saveBtn').click();
            await page.locator('#formError').filter({ hasText: '30 phút' }).waitFor();
            assert.equal(payload.teacherId, 12);
            assert.equal(payload.roomId, 22);
            assert.equal(payload.endDate, '2027-02-24');
            assert.equal(payload.totalSessions, 24);
            assert.deepEqual(errors, []);
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
            if (process.env.CLASS_SCREENSHOT_DIR) {
                fs.mkdirSync(process.env.CLASS_SCREENSHOT_DIR, { recursive: true });
                await page.locator('#classModal').evaluate(element => element.scrollTop = 0);
                await page.screenshot({ path: path.join(process.env.CLASS_SCREENSHOT_DIR, `class-form-${viewport.width}.png`), fullPage: true });
            }
            await page.locator('#classModal .btn-close').click();
            await page.locator('#classModal').waitFor({ state: 'hidden' });
            classRows = [{ id: 9, courseId: 2, courseName: 'Guitar', branchId: 1, branchName: 'Campus',
                teacherId: 12, teacherName: 'Guitar teacher', roomId: 22, roomName: 'Guitar room',
                classCode: 'DRAFT', className: 'Draft', maxStudents: 8, currentStudents: 0, pendingPaymentStudents: 0,
                availableSeats: 8, totalLessons: 0, status: 'PLANNING', scheduleDescription: 'Family preference',
                startDate: '2027-02-01', endDate: '2027-05-01' }];
            await page.locator('#filterForm button[type="submit"]').click();
            await page.locator('[data-edit-id="9"]').click();
            assert.equal(await page.locator('#endDate').evaluate(element => element.readOnly), false);
            assert.equal(await page.locator('#scheduleDescription').evaluate(element => element.readOnly), false);
            assert.equal(await page.locator('#scheduleDescription').inputValue(), 'Family preference');
            await page.locator('#teacherId').selectOption('');
            await page.locator('#roomId').selectOption('');
            const clearing = page.waitForRequest(request => request.method() === 'PUT');
            await page.locator('#saveBtn').click();
            const clearRequest = await clearing;
            assert.equal(clearRequest.postDataJSON().clearTeacher, true);
            assert.equal(clearRequest.postDataJSON().clearRoom, true);
            await page.locator('#classModal .btn-close').click();
            await page.locator('#classModal').waitFor({ state: 'hidden' });
            failTeacherLoading = true;
            await page.locator('[data-edit-id="9"]').click();
            await page.locator('#formError').filter({ hasText: 'Teacher options unavailable' }).waitFor();
            const previousPayload = payload;
            await page.locator('#saveBtn').click();
            await page.locator('#formError').filter({ hasText: 'tải thất bại' }).waitFor();
            assert.equal(payload, previousPayload);
            await page.close();
        }
    } finally {
        await browser.close();
        await new Promise(resolve => server.close(resolve));
    }
});
