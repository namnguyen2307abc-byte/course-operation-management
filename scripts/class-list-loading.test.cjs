const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../com_fe');
const html = fs.readFileSync(path.join(root, 'pages/class-management.html'), 'utf8');
const pageScript = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].map(match => match[1]).join('\n');

// Run the page and shared API helper together, with controllable network responses.
async function openPage({ failClasses = false, failOptions = false } = {}) {
    const nodes = new Map();
    for (const tag of html.matchAll(/<[^>]+\bid="([^"]+)"[^>]*>/g)) {
        const node = { value: '', innerHTML: '', textContent: '', disabled: false,
            className: tag[0].match(/\bclass="([^"]*)"/)?.[1] || '', handlers: {}, attributes: {},
            addEventListener(type, handler) { this.handlers[type] = handler; },
            setAttribute(name, value) { this.attributes[name] = value; } };
        node.classList = {
            contains(name) { return node.className.split(/\s+/).includes(name); },
            toggle(name, force) {
                const names = new Set(node.className.split(/\s+/).filter(Boolean));
                const enabled = force ?? !names.has(name);
                if (enabled) names.add(name); else names.delete(name);
                node.className = [...names].join(' ');
            },
            add(name) { this.toggle(name, true); },
            remove(name) { this.toggle(name, false); }
        };
        nodes.set(tag[1], node);
    }
    const state = { failClasses, failOptions, rows: [], requests: [], alerts: [], errors: [] };
    const ctx = vm.createContext({ URLSearchParams, TypeError, console: { error: (...args) => state.errors.push(args) },
        document: { getElementById: id => nodes.get(id), querySelectorAll: () => [], querySelector: () => null },
        window: { location: { port: '5500', hostname: 'localhost', pathname: '/pages/class-management.html', search: '' } },
        bootstrap: { Modal: class {} }, checkAuth() {}, getCurrentUser: () => ({ role: 'STAFF' }), updateNavbarUser() {},
        localStorage: { getItem: () => 'test-token' }, alert: message => state.alerts.push(message),
        fetch: async (url, options) => {
            if (url.includes('footer.html')) return { text: async () => '' };
            const parsed = new URL(url);
            state.requests.push({ url: parsed, options });
            const classes = parsed.pathname === '/api/class-management/classes';
            if (classes && state.failClasses) throw new TypeError('Failed to fetch');
            const failed = !classes && state.failOptions;
            const body = classes ? state.rows : failed ? { message: 'Internal database details' } : [{ id: 1, code: 'ONE', name: 'Option' }];
            return { status: failed ? 500 : 200, ok: !failed,
                json: async () => body, text: async () => JSON.stringify(body) };
        }
    });
    vm.runInContext(fs.readFileSync(path.join(root, 'js/api.js'), 'utf8'), ctx);
    vm.runInContext(pageScript, ctx);
    await new Promise(resolve => setImmediate(resolve));
    return { state, nodes, ctx, visible: id => !nodes.get(id).classList.contains('d-none') };
}

test('initial class load failure is friendly, has one retry and hides unknown result count', async () => {
    const page = await openPage({ failClasses: true });
    const { nodes, state } = page;
    assert.equal(page.visible('classLoadError'), true);
    assert.equal(page.visible('classTable'), false);
    assert.equal(page.visible('resultCount'), false);
    assert.equal(page.visible('notice'), false);
    assert.equal(nodes.get('classLoadErrorTitle').textContent, 'Không thể tải danh sách lớp');
    assert.doesNotMatch(nodes.get('classLoadErrorMessage').textContent, /backend|8080|localhost/);
    assert.equal(nodes.get('retryClassesBtn').disabled, false);
    assert.equal(nodes.get('classList').attributes['aria-busy'], 'false');
    assert.deepEqual(state.alerts, []);
    assert.equal(state.errors.length, 1);

    state.failClasses = false;
    nodes.get('keyword').value = '  PIANO  ';
    nodes.get('filterCourse').value = '1';
    nodes.get('filterBranch').value = '1';
    nodes.get('filterStatus').value = 'FINISHED';
    await nodes.get('retryClassesBtn').handlers.click();
    const params = state.requests.at(-1).url.searchParams;
    assert.equal(params.get('keyword'), 'PIANO');
    assert.equal(params.get('courseId'), '1');
    assert.equal(params.get('branchId'), '1');
    assert.equal(params.get('status'), 'FINISHED');
    assert.equal(nodes.get('filterStatus').value, 'FINISHED');
    assert.equal(page.visible('classLoadError'), false);
    assert.equal(page.visible('classTable'), true);
    assert.equal(page.visible('resultCount'), true);
    assert.equal(nodes.get('resultCount').textContent, '0 lớp');
    assert.match(nodes.get('classRows').innerHTML, /Chưa có lớp phù hợp với bộ lọc/);
});

test('failed refresh keeps previous rows and retry replaces them with fresh results', async () => {
    const { nodes, state, ctx, visible } = await openPage();
    state.rows = [{ id: 9, classCode: 'PIANO-01', className: 'Piano', status: 'PLANNING' }];
    await ctx.loadClasses();
    const previousRows = nodes.get('classRows').innerHTML;
    state.failClasses = true;
    await nodes.get('filterForm').handlers.submit({ preventDefault() {} });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(nodes.get('classRows').innerHTML, previousRows);
    assert.equal(visible('classTable'), true);
    assert.equal(visible('resultCount'), false);
    assert.match(nodes.get('classLoadErrorMessage').textContent, /dữ liệu đã tải trước đó/);
    state.failClasses = false;
    state.rows = [];
    await nodes.get('retryClassesBtn').handlers.click();
    assert.equal(visible('classLoadError'), false);
    assert.doesNotMatch(nodes.get('classRows').innerHTML, /PIANO-01/);
    assert.equal(nodes.get('filterBtn').disabled, false);
    assert.deepEqual(state.alerts, []);
});

test('options failure uses friendly feedback and retry recovers options and classes', async () => {
    const { nodes, state, visible } = await openPage({ failClasses: true, failOptions: true });
    assert.equal(visible('filterOptionsWarning'), false);
    assert.equal(visible('classLoadError'), true);
    assert.deepEqual(state.alerts, []);
    state.failClasses = false;
    await nodes.get('retryClassesBtn').handlers.click();
    assert.equal(visible('filterOptionsWarning'), true);
    assert.equal(visible('classLoadError'), false);
    state.failOptions = false;
    await nodes.get('filterForm').handlers.submit({ preventDefault() {} });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(visible('filterOptionsWarning'), false);
    assert.match(nodes.get('filterCourse').innerHTML, /Option/);
    assert.deepEqual(state.alerts, []);
});
