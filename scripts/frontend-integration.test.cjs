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

test('admin teacher editor keeps placement department separate from the exact class specialism', async () => {
    const nodes = new Map();
    const element = id => {
        if (!nodes.has(id)) nodes.set(id, {value:'', hidden:false, open:false, handlers:{},
            addEventListener(type, handler) {this.handlers[type]=handler;}, setAttribute() {}, reset() {},
            showModal() {this.open=true;}, close() {this.open=false;}});
        return nodes.get(id);
    };
    const requests=[];
    const teacher={id:2, fullName:'Teacher', username:'teacher', role:'TEACHER', subject:'DAN', teachingSubject:'GUITAR', status:'ACTIVE'};
    const ctx=context({roleLabel:value=>value,getCurrentUser:()=>({userId:1}),appPath:value=>value,
        document:{querySelectorAll:()=>[]},Admin:{$:element,escape:String,date:String,options(){},roles:[],query:()=>'',message(){},
            request:async(path,method,body)=>{
                requests.push({path,method,body});
                return method?{}:{user:teacher,classes:[],children:[],studentProfiles:[]};
            }}});
    const form=vm.runInContext(read('js/admin/admin-user-form.js')+'\nAdminUserForm;',ctx);
    form.initialize(async()=>{});
    await form.detail(2);
    element('editUser').handlers.click();
    assert.equal(element('userSubject').value,'DAN');
    assert.equal(element('userTeachingSubject').value,'GUITAR');
    assert.equal(element('userTeachingSubjectFields').hidden,false);
    element('userTeachingSubject').value='PIANO';
    await element('userForm').handlers.submit({preventDefault(){}});
    const saved=requests.find(request=>request.method==='PUT');
    assert.equal(saved.body.subject,'DAN');
    assert.equal(saved.body.teachingSubject,'PIANO');
});
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
test('editing class schedule parses legacy Vietnamese labels and saved times', () => {
    const ctx = context(); vm.runInContext(read('js/class-management-form.js'), ctx);
    assert.equal(JSON.stringify(ctx.parseClassSchedule('Thứ 2 & Thứ 5 (18:00 - 19:00)')),
        JSON.stringify({days:[1,4],startTime:'18:00',endTime:'19:00'}));
    assert.equal(JSON.stringify(ctx.parseClassSchedule('T3-T6-CN (09:30 - 11:30)')),
        JSON.stringify({days:[2,5,7],startTime:'09:30',endTime:'11:30'}));
    assert.equal(JSON.stringify(ctx.parseClassSchedule('Chủ Nhật (9:00 - 10:30)')),
        JSON.stringify({days:[7],startTime:'09:00',endTime:'10:30'}));
    assert.equal(JSON.stringify(ctx.parseClassSchedule(null)), JSON.stringify({days:[],startTime:'',endTime:''}));
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

test('workspace reserves the sidebar before first paint and mounts navigation before content', () => {
    const files = ['index.html', ...fs.readdirSync(path.join(root, 'pages')).map(file => `pages/${file}`)];
    for (const file of files) {
        const html = read(file);
        if (!html.includes('id="navbar-container"')) continue;
        assert.match(html, /<body\b[^>]*class="[^"]*has-workspace-sidebar/, `${file} must reserve sidebar width`);
        assert(html.indexOf('id="appSidebar"') < html.indexOf('<main'), `${file} must include the static shell before content`);
        assert(html.indexOf('js/workspace-shell.js') < html.indexOf('<main'), `${file} must personalize navigation before content`);
        const auth = [...html.matchAll(/<script\b[^>]*src="[^"]*\/auth\.js"[^>]*>/g)];
        assert.equal(auth.length, 1, `${file} must load shared auth once`);
        assert(html.indexOf(auth[0][0]) < html.indexOf('</head>'), `${file} must have navigation functions ready before body`);
        assert.doesNotMatch(html, /<\/[a-z]+\s+[^>]*=/i, `${file} has a malformed closing tag`);
    }
});

test('refreshing the authenticated user preserves the existing sidebar nodes and scroll position', () => {
    const nodes = {};
    let clears = 0;
    function element(tag = 'div') {
        return { tagName: tag, dataset: {}, children: [], classList: { add() {} }, setAttribute() {},
            appendChild(child) { this.children.push(child); }, replaceChildren() { this.children = []; clears++; } };
    }
    for (const id of ['workspaceNavigation', 'sidebarUserName', 'sidebarUserRole', 'sidebarAvatar']) nodes[id] = element();
    const user = { userId: 1, username: 'qa_admin', fullName: 'Admin test', role: 'ADMIN' };
    const ctx = context({
        document: { addEventListener() {}, getElementById: id => nodes[id], querySelector() { return null; },
            querySelectorAll() { return []; }, createElement: element, body: { classList: { add() {} } } },
        sessionStorage: { getItem() { return '180'; } },
        localStorage: { getItem: key => key === 'token' ? 'valid-token' : JSON.stringify(user) }
    });
    vm.runInContext(read('js/auth.js'), ctx);
    ctx.updateNavbarUser();
    const firstLink = nodes.workspaceNavigation.children[0];
    assert.equal(nodes.workspaceNavigation.scrollTop, 180);
    nodes.workspaceNavigation.scrollTop = 215;
    ctx.updateNavbarUser();
    assert.equal(clears, 1);
    assert.equal(nodes.workspaceNavigation.children[0], firstLink);
    assert.equal(nodes.workspaceNavigation.scrollTop, 215);
});
