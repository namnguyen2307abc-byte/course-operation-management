const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../com_fe');
function read(file) { return fs.readFileSync(path.join(root, file), 'utf8'); }
function context(extra = {}) {
    return vm.createContext({ document: { addEventListener() {} },
        window: { location: { pathname: '/index.html' }, addEventListener() {} },
        localStorage: { getItem() { return null; }, removeItem() {} }, console, ...extra });
}
test('merged sidebar keeps weekly schedule and class management with role restrictions', () => {
    const ctx = context(); vm.runInContext(read('js/auth.js'), ctx);
    for (const role of ['ADMIN', 'STAFF', 'BRANCH_MANAGER', 'TEACHER', 'PARENT', 'STUDENT']) {
        const files = ctx.visibleFeatures({ role }).map(f => f.file);
        assert(files.includes('weekly-schedule'));
        assert.equal(files.includes('class-management'), ['ADMIN', 'STAFF', 'BRANCH_MANAGER'].includes(role));
        for (const file of files) assert(fs.existsSync(path.join(root, 'pages', `${file}.html`)));
    }
    assert.equal(ctx.getCurrentUser(), null);
    assert.equal(ctx.sanitizeUserData({username:'teacher_demo',role:'ROLE_PARENT'}).role, 'PARENT');
});
test('weekly cards distinguish unmarked past lessons and show each child separately', () => {
    const ctx = context(); vm.runInContext(read('js/weekly-schedule.js'), ctx);
    const lesson = {lessonId:1, past:true, attendanceStatus:'NOT_MARKED', studentAttendances:[
        {studentId:1,fullName:'Bé A',status:'PRESENT'}, {studentId:2,fullName:'Bé B',status:'ABSENT'}]};
    const html = ctx.renderLessonCard(lesson, 'PARENT');
    assert.match(html, /Bé A: <strong>Có mặt/);
    assert.match(html, /Bé B: <strong>Vắng mặt/);
    assert.doesNotMatch(html, /lesson-attendance.html/);
    assert.match(ctx.renderLessonCard({...lesson, studentAttendances:[]}, 'PARENT'), /Chưa điểm danh/);
});
test('expired session rejects API promise instead of being treated as a successful save', async () => {
    const ctx = context({alert() {}, fetch: async () => ({status:401}), console:{error() {}}});
    ctx.window.location = {port:'8080', pathname:'/pages/lesson-attendance.html'};
    vm.runInContext(read('js/api.js'), ctx);
    await assert.rejects(ctx.callApi('/api/schedule/lessons/1/attendance', 'POST', {}, false, false));
    assert.equal(ctx.window.location.href, '/login.html');
});
test('readonly attendance cannot enter edit mode or submit a save', async () => {
    const ctx = context({callApi() { assert.fail('readonly save must not call API'); }});
    vm.runInContext(read('js/lesson-attendance.js'), ctx);
    vm.runInContext('sheet = {canEdit:false, students:[]}; editMode = false;', ctx);
    ctx.enableEditing(); await ctx.saveAttendance();
    assert.equal(vm.runInContext('editMode', ctx), false);
});
test('every HTML script parses and all script/stylesheet dependencies exist', () => {
    const files = ['index.html','login.html', ...fs.readdirSync(path.join(root, 'pages')).map(f => `pages/${f}`)];
    for (const file of files) {
        const html = read(file);
        for (const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
            if (!/\bsrc=|type=["']module/.test(match[1])) new vm.Script(match[2], {filename:file});
        }
        for (const match of html.matchAll(/<(?:script|link)\b[^>]*(?:src|href)=["']([^"']+)["']/gi)) {
            const url = match[1].split('?')[0];
            if (/^https?:/.test(url)) continue;
            const target = url.startsWith('/') ? path.join(root, url) : path.resolve(root, path.dirname(file), url);
            assert(fs.existsSync(target), `${file} refers to missing ${url}`);
        }
    }
});
