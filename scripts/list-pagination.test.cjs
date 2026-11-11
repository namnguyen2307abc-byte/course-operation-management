const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function pager() {
    const host = { handlers: {}, setAttribute() {}, querySelectorAll: () => [],
        addEventListener(type, handler) { this.handlers[type] = handler; } };
    let reloads = 0;
    const ctx = vm.createContext({ document: { getElementById: () => host }, URLSearchParams });
    vm.runInContext(fs.readFileSync('com_fe/js/list-pagination.js', 'utf8'), ctx);
    const control = ctx.createListPager('pager', () => { reloads++; });
    return { control, host, reloads: () => reloads };
}

test('pager defaults, filters and size changes use bounded requests', () => {
    const { control, host, reloads } = pager();
    assert.equal(control.params({ keyword: '' }).toString(), 'page=0&size=10');
    control.accept({ items: [], page: 2, size: 10, totalPages: 4, totalElements: 35 });
    assert.match(host.innerHTML, /21–30 \/ 35/);
    assert.equal(control.params({ keyword: 'piano' }).get('page'), '0');
    host.handlers.change({ target: { id: 'pager-size', value: '5' } });
    assert.equal(control.state.size, 5);
    assert.equal(control.state.page, 0);
    assert.equal(reloads(), 1);
});

test('empty and last pages have deterministic controls and recover after removal', async () => {
    const { control, host, reloads } = pager();
    control.state.page = 2;
    assert.equal(control.accept({ items: [], page: 2, size: 10, totalPages: 2, totalElements: 20 }), false);
    await Promise.resolve();
    assert.equal(reloads(), 1);
    assert.equal(control.state.page, 1);
    control.accept({ items: [], page: 0, size: 10, totalPages: 0, totalElements: 0 });
    assert.match(host.innerHTML, /0–0 \/ 0/);
    assert.match(host.innerHTML, /data-page-step="1"[^>]+disabled/);
});

test('in-flight requests block repeated navigation and failed pages restore accepted position', () => {
    const { control, host, reloads } = pager();
    control.accept({ items: [], page: 1, size: 10, totalPages: 3, totalElements: 23 });
    const event = { target: { closest: () => ({ dataset: { pageStep: '1' }, disabled: false }) } };
    control.busy(true);
    host.handlers.click(event);
    assert.equal(reloads(), 0);
    control.busy(false);
    host.handlers.click(event);
    assert.equal(control.state.page, 2);
    control.fail();
    assert.equal(control.state.page, 1);
    assert.match(host.innerHTML, /11–20 \/ 23/);
    assert.throws(() => control.accept([]), /không hợp lệ/);
});
