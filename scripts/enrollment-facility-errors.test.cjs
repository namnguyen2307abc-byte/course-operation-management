const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../com_fe');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const technical = 'SQL connection failed at http://127.0.0.1:8080 backend';
const event = { preventDefault() {} };

function screen(kind) {
    const nodes = new Map();
    const element = id => {
        if (!nodes.has(id)) {
            const node = { value: '', innerHTML: '', textContent: '', innerText: '', disabled: false,
                className: '', options: [], addEventListener() {}, setAttribute() {},
                querySelectorAll: () => [], replaceChildren() { this.innerHTML = ''; } };
            node.classList = {
                contains: name => node.className.split(/\s+/).includes(name),
                toggle(name, enabled) {
                    const names = new Set(node.className.split(/\s+/).filter(Boolean));
                    if (enabled ?? !names.has(name)) names.add(name); else names.delete(name);
                    node.className = [...names].join(' ');
                },
                add(name) { this.toggle(name, true); }, remove(name) { this.toggle(name, false); }
            };
            nodes.set(id, node);
        }
        return nodes.get(id);
    };
    const state = { failures: new Map(), alerts: [], calls: [], logs: [] };
    const room = { id: 7, branchId: 1, roomName: 'Demo room', roomCode: 'R7', roomType: 'PIANO_GROUP', capacity: 8, status: 'AVAILABLE' };
    const equipment = { id: 8, roomId: 7, name: 'Demo piano', code: 'EQ8', category: 'UPRIGHT_PIANO', status: 'GOOD' };
    const ctx = vm.createContext({ URLSearchParams, TypeError,
        document: { getElementById: element, addEventListener() {} },
        window: { location: { port: '5500', hostname: 'localhost', pathname: '/pages/course-enrollment.html', search: '' }, scrollTo() {} },
        localStorage: { getItem: () => 'test-token' }, sessionStorage: { setItem() {} },
        console: { error: (...args) => state.logs.push(args), warn() {} },
        alert: message => state.alerts.push(message), checkAuth: () => false,
        getCurrentUser: () => ({ role: 'ADMIN' }), updateNavbarUser() {},
        appPath: value => value,
        bootstrap: { Modal: class {
            constructor(node) { this.node = node; }
            show() { this.node.classList.add('show'); }
            hide() { this.node.classList.remove('show'); }
        } },
        fetch: async (url, options = {}) => {
            if (url.includes('footer.html')) return { text: async () => '' };
            const pathname = new URL(url).pathname;
            state.calls.push({ pathname, ...options });
            const failure = state.failures.get(pathname);
            if (failure === 'network') throw new TypeError('Failed to fetch');
            let body = pathname.endsWith('/classes') ? [{ id: 3, classCode: 'C3', className: 'Demo class', branchId: 1, roomId: 7, availableSeats: 8 }]
                : pathname === '/api/branches' ? [{ id: 1, name: 'Demo branch' }]
                : pathname.endsWith('/rooms') ? [room]
                : pathname.endsWith('/equipments') ? [equipment]
                : pathname.endsWith('/overview') ? { room, warnings: [] }
                : pathname.endsWith('/detail') ? room
                : pathname.endsWith('/decision') ? { invoiceId: 99, invoiceCode: 'INV-DEMO' }
                : pathname.endsWith('/staff/requests') ? [] : { warnings: [] };
            if (pathname.endsWith('/page')) {
                const items = pathname.endsWith('/rooms/page') ? [room] : pathname.endsWith('/equipments/page') ? [equipment] : [];
                body = { items, page: 0, size: 10, totalElements: items.length, totalPages: items.length ? 1 : 0 };
            }
            if (failure) body = { message: failure.message || technical, details: failure.details };
            const status = failure?.status || 200;
            return { status, ok: status < 400,
                json: async () => { if (failure?.invalidJson) throw new SyntaxError('Invalid JSON'); return body; },
                text: async () => JSON.stringify(body) };
        }
    });
    vm.runInContext(read('js/api.js'), ctx);
    vm.runInContext(read('js/request-feedback.js'), ctx);
    vm.runInContext(read('js/list-pagination.js'), ctx);
    if (kind === 'enrollment') {
        const script = [...read('pages/course-enrollment.html').matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].map(match => match[1]).join('\n');
        vm.runInContext(script, ctx);
        vm.runInContext("decisionModal = new bootstrap.Modal(document.getElementById('decisionModal')); facilityModal = new bootstrap.Modal(document.getElementById('facilityModal')); pendingRequestMap.set(1, {id:1,childName:'Demo child',preferredCourseId:2});", ctx);
    } else {
        vm.runInContext(read('js/branch-facility.js'), ctx);
        element('branchSelect').value = '1';
    }
    return { ctx, state, element };
}

test('assignment list failure stays inside the form and reopening recovers', async () => {
    const { ctx, state, element } = screen('enrollment');
    state.failures.set('/api/course-enrollment/courses/2/classes', 'network');
    await ctx.openDecision(1, 'APPROVE');
    assert.match(element('decisionError').textContent, /Chưa tải được danh sách lớp còn chỗ/);
    assert.equal(element('confirmDecisionBtn').disabled, true);
    assert.equal(element('decisionModal').classList.contains('show'), true);
    assert.equal(element('staffFacilityPreviewBtn').disabled, true);
    state.failures.clear();
    await ctx.openDecision(1, 'APPROVE');
    assert.equal(element('decisionError').classList.contains('d-none'), true);
    assert.equal(element('confirmDecisionBtn').disabled, false);
    assert.match(element('assignedClassSelect').innerHTML, /Demo class/);
    assert.deepEqual(state.alerts, []);
});

test('assign/reject failures hide technical errors, retain drafts and preserve business conflicts', async () => {
    for (const decision of ['APPROVE', 'REJECT']) {
        for (const failure of ['network', { status: 500 }, { status: 409, message: 'Lớp được chọn đã hết chỗ' }]) {
            const { ctx, state, element } = screen('enrollment');
            await ctx.openDecision(1, decision);
            element('reviewNote').value = 'Draft review';
            element('assignedClassSelect').value = '3';
            state.failures.set('/api/course-enrollment/staff/requests/1/decision', failure);
            await ctx.confirmDecision();
            assert.equal(element('decisionModal').classList.contains('show'), true);
            assert.equal(element('reviewNote').value, 'Draft review');
            assert.equal(element('confirmDecisionBtn').disabled, false);
            assert.doesNotMatch(element('decisionError').textContent, /SQL|127\.0\.0\.1|8080|backend/);
            assert.match(element('decisionError').textContent, failure.status === 409 ? /đã hết chỗ/ : /Chưa xác nhận được kết quả/);
            assert.deepEqual(state.alerts, []);
        }
    }
});

test('successful assignment still opens its invoice; rejection still reloads queue', async () => {
    for (const decision of ['APPROVE', 'REJECT']) {
        const { ctx, state, element } = screen('enrollment');
        await ctx.openDecision(1, decision);
        element('assignedClassSelect').value = '3';
        await ctx.confirmDecision();
        assert.equal(element('decisionModal').classList.contains('show'), false);
        if (decision === 'APPROVE') assert.match(ctx.window.location.href, /tuition-receivables\.html\?invoiceId=99/);
        else assert.match(element('pageNotice').textContent, /Đã từ chối yêu cầu/);
        assert.deepEqual(state.alerts, []);
    }
});

test('facility preview hides server error details in its own modal', async () => {
    const { ctx, state, element } = screen('enrollment');
    await ctx.openDecision(1, 'APPROVE');
    element('decisionModal').classList.remove('show');
    state.failures.set('/api/branches', { status: 500 });
    await ctx.openFacilityPreview(3);
    assert.match(element('facilityModalBody').innerHTML, /Chưa tải được thông tin cơ sở vật chất/);
    assert.doesNotMatch(element('facilityModalBody').innerHTML, /SQL|127\.0\.0\.1|backend/);
    assert.deepEqual(state.alerts, []);
});

test('facility branches, rooms, equipment and usage failures have friendly retries', async () => {
    const { ctx, state, element } = screen('facility');
    state.failures.set('/api/branches', { status: 500 });
    await ctx.loadBranches();
    assert.match(element('facilityLoadError').innerHTML, /Chưa tải được danh sách cơ sở/);
    assert.equal(element('statBranchesCount').textContent, '—');
    assert.equal(element('branchSelect').disabled, true);
    const cases = [
        ['/api/branches/1/rooms/page', () => ctx.loadRooms('1'), 'roomTableBody', /Chưa tải được danh sách phòng học/],
        ['/api/branches/1/equipments/page', () => ctx.loadEquipments('1'), 'equipmentTableBody', /Chưa tải được danh sách thiết bị/],
        ['/api/branches/rooms/7/overview', () => ctx.loadRoomUsage('7'), 'roomUsageBody', /Chưa tải được chi tiết phòng/]
    ];
    vm.runInContext("usageRoomId = '7';", ctx);
    for (const [endpoint, load, target, message] of cases) {
        for (const failure of ['network', { status: 500 }, { invalidJson: true }]) {
            state.failures.set(endpoint, failure);
            await load();
            assert.match(element(target).innerHTML, message);
            assert.match(element(target).innerHTML, /data-facility-retry/);
            assert.doesNotMatch(element(target).innerHTML, /SQL|127\.0\.0\.1|backend/);
        }
        state.failures.delete(endpoint);
        await load();
        assert.doesNotMatch(element(target).innerHTML, /Chưa tải được/);
    }
    assert.deepEqual(state.alerts, []);
});

test('facility room/equipment save failures keep drafts, business constraints and local permissions', async () => {
    for (const kind of ['room', 'equipment']) {
        const { ctx, state, element } = screen('facility');
        await ctx.loadRooms('1');
        await ctx.loadEquipments('1');
        ctx.openFacilityEditor(kind, kind === 'room' ? 7 : 8);
        element('editFacilityName').value = 'Unsaved draft';
        element('editFacilityDescription').value = 'Keep this draft';
        const endpoint = `/api/branches/${kind === 'room' ? 'rooms/7' : 'equipments/8'}`;
        for (const failure of ['network', { status: 500 }, { status: 409, message: 'Sức chứa không được nhỏ hơn sĩ số tối đa của lớp C3' }, { status: 403 }, { status: 401 }]) {
            state.failures.set(endpoint, failure);
            await ctx.saveFacility(event);
            assert.equal(element('editFacilityModal').classList.contains('show'), true);
            assert.equal(element('editFacilityName').value, 'Unsaved draft');
            assert.equal(element('editFacilityDescription').value, 'Keep this draft');
            const message = element('editFacilityError').textContent;
            assert.doesNotMatch(message, /SQL|127\.0\.0\.1|backend/);
            assert.match(message, failure.status === 409 ? /Sức chứa/ : failure.status === 403 ? /không có quyền/ : failure.status === 401 ? /đăng nhập đã hết hạn/ : /Chưa xác nhận được việc lưu/);
            assert.equal(vm.runInContext('savingFacility', ctx), false);
        }
        state.failures.clear();
        await ctx.saveFacility(event);
        assert.equal(element('editFacilityModal').classList.contains('show'), false);
        assert.match(element('facilityNotice').innerHTML, /Đã lưu thay đổi/);
        assert.deepEqual(state.alerts, []);
    }
});
