const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');
let browser, server, origin;
const root = path.resolve(__dirname, '../com_fe');
before(async () => {
    server = http.createServer(async (req, res) => {
        const file = path.resolve(root, '.' + new URL(req.url, 'http://local').pathname);
        if (!file.startsWith(root + path.sep)) { res.writeHead(403); return res.end(); }
        try {
            res.setHeader('Content-Type', {'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css'}[path.extname(file)] || 'text/plain');
            res.end(await fs.readFile(file));
        } catch { res.writeHead(404); res.end(); }
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    origin = 'http://127.0.0.1:' + server.address().port;
    browser = await chromium.launch({ channel: 'chrome', headless: true });
});
after(async () => { await browser?.close(); if (server) await new Promise(resolve => server.close(resolve)); });

test('admin has five role choices and can inspect a retired account without errors', async () => {
    for (const width of [1440, 390]) {
        const page = await browser.newPage({ viewport: { width, height: 1000 } });
        const errors = [];
        page.on('pageerror', e => errors.push(e.message));
        await page.route('https://cdn.jsdelivr.net/**', async route => {
            const name = path.basename(new URL(route.request().url()).pathname);
            if (process.env.CLASS_BOOTSTRAP_CACHE && ['bootstrap.min.css', 'bootstrap.bundle.min.js'].includes(name))
                return route.fulfill({ body: await fs.readFile(path.join(process.env.CLASS_BOOTSTRAP_CACHE, name)), contentType: name.endsWith('.js') ? 'text/javascript' : 'text/css' });
            return route.abort();
        });
        const admin = { userId: 1, username: 'admin', fullName: 'Quản trị viên', role: 'ADMIN', token: 'test' };
        const retired = { id: 2, username: 'retired', fullName: 'Tài khoản lưu trữ', role: null, status: 'INACTIVE' };
        await page.addInitScript(admin => {
            localStorage.setItem('token', 'test'); localStorage.setItem('user', JSON.stringify(admin));
        }, admin);
        await page.route('**/api/**', async route => {
            const url = new URL(route.request().url());
            let body = [];
            if (url.pathname === '/api/auth/me') body = admin;
            if (url.pathname === '/api/admin/users') body = { items: [retired], page: 0, size: 5, totalElements: 1, totalPages: 1 };
            if (url.pathname === '/api/admin/users/2') body = { user: retired, classes: [], children: [] };
            if (url.pathname === '/api/notifications') body = { notifications: [], unreadCount: 0 };
            await route.fulfill({ json: body, headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' } });
        });
        await page.goto(origin + '/pages/admin-users.html');
        await page.locator('[data-user-id="2"]').waitFor();
        await page.locator('#createUser').click();
        assert.deepEqual(await page.locator('#userRole option').evaluateAll(nodes => nodes.map(n => n.value)),
            ['ADMIN', 'BRANCH_MANAGER', 'STAFF', 'TEACHER', 'PARENT']);
        assert.equal(await page.locator('#userStudentFields').count(), 0);
        await page.locator('#userDialog [data-close-dialog]').first().click();
        await page.locator('[data-user-id="2"]').click();
        await page.locator('#detailDialog[open]').waitFor();
        assert.match(await page.locator('#detailProfile').innerText(), /Lưu trữ/);
        for (const id of ['changeRole', 'changeStatus', 'resetPassword'])
            assert.equal(await page.locator('#' + id).isDisabled(), true);
        if (process.env.ACCOUNT_ROLE_SCREENSHOTS) {
            await fs.mkdir(process.env.ACCOUNT_ROLE_SCREENSHOTS, {recursive: true});
            await page.screenshot({path: path.join(process.env.ACCOUNT_ROLE_SCREENSHOTS, 'archived-account-' + width + '.png')});
        }
        await page.locator('#detailDialog [data-close-dialog]').first().click();
        await page.locator('#createUser').click();
        await page.locator('#userRole').selectOption('PARENT');
        await page.locator('#parentChildrenFields').waitFor();
        await page.locator('#userUsername').fill('parent_demo');
        await page.locator('#userPassword').fill('Test@123456');
        await page.locator('#userName').fill('Phụ huynh kiểm thử');
        await page.locator('.child-name').fill('Học viên kiểm thử');
        await page.locator('.child-dob').fill('2018-05-20');
        const saved = page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/users' && request.method() === 'POST');
        await page.locator('#saveUser').click();
        const payload = (await saved).postDataJSON();
        assert.equal(payload.role, 'PARENT');
        assert.equal(payload.children.length, 1);
        assert.equal(payload.children[0].fullName, 'Học viên kiểm thử');
        assert.equal(payload.children[0].dateOfBirth, '2018-05-20');
        assert.equal(Object.hasOwn(payload, 'studentId'), false);
        await page.locator('#userDialog').waitFor({state:'hidden'});
        assert.deepEqual(errors, []);
        await page.close();
    }
});
