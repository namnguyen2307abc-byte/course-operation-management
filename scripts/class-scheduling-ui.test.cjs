const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { chromium } = require('playwright');

test('class form filters assignments, calculates end date and displays a scheduling rejection', async () => {
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
            await page.route('**/api/**', async route => {
                const request = route.request();
                const url = new URL(request.url());
                let body = [];
                let status = 200;
                if (url.pathname === '/api/class-management/classes') body = classRows;
                if (url.pathname === '/api/course-enrollment/courses') body = [
                    { id: 1, code: 'PIANO', name: 'Piano', totalSessions: 4 },
                    { id: 2, code: 'GUITAR', name: 'Guitar', totalSessions: 4 }
                ];
                if (url.pathname === '/api/branches/options') body = [{ id: 1, code: 'B1', name: 'Campus' }];
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
                    body = Number(url.searchParams.get('maxStudents')) > 8 ? []
                        : [{ id: piano ? 21 : 22, roomCode: piano ? 'P1' : 'G1', roomName: piano ? 'Piano room' : 'Guitar room', capacity: 8 }];
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
            await page.goto((process.env.CLASS_TEST_URL || 'http://127.0.0.1:5500') + '/pages/class-management.html');
            await page.locator('#courseId option[value="1"]').waitFor({ state: 'attached' });
            await page.locator('#newClassBtn').click();
            await page.locator('#courseId').selectOption('1');
            await page.locator('#teacherId option[value="11"]').waitFor({ state: 'attached' });
            assert.equal(await page.locator('#teacherId option').count(), 2);
            await page.locator('#teacherId').selectOption('11');
            await page.locator('#branchId').selectOption('1');
            await page.locator('#roomId option[value="21"]').waitFor({ state: 'attached' });
            await page.locator('#roomId').selectOption('21');
            await page.locator('#courseId').selectOption('2');
            await page.locator('#teacherId option[value="12"]').waitFor({ state: 'attached' });
            await page.locator('#roomId option[value="22"]').waitFor({ state: 'attached' });
            assert.equal(await page.locator('#teacherId').inputValue(), '');
            assert.equal(await page.locator('#teacherId option[value="11"]').count(), 0);
            await page.locator('#teacherId').selectOption('12');
            await page.locator('#roomId').selectOption('22');
            await page.locator('#startDate').fill('2027-01-01');
            await page.locator('#startDate').dispatchEvent('change');
            assert.equal(await page.locator('#endDate').inputValue(), '2027-01-08');
            assert.equal(await page.locator('#endDate').evaluate(element => element.readOnly), true);
            await page.locator('#classCode').fill('UI-TEST');
            await page.locator('#className').fill('Class scheduling test');
            await page.locator('#status').selectOption('OPEN');
            await page.locator('#saveBtn').click();
            await page.locator('#formError').filter({ hasText: '30 phút' }).waitFor();
            assert.equal(payload.teacherId, 12);
            assert.equal(payload.roomId, 22);
            assert.equal(payload.endDate, '2027-01-08');
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
    }
});
