/* Shared server-side list navigation. Page numbers in the API are zero-based. */
function createListPager(id, reload) {
    const host = document.getElementById(id);
    const state = { page: 0, size: 10, totalElements: 0, totalPages: 0, loading: false };
    let filterKey = '';
    let acceptedKey = '';
    let accepted = { page: 0, size: 10, totalElements: 0, totalPages: 0 };
    function draw() {
        const start = state.totalElements ? state.page * state.size + 1 : 0;
        const end = Math.min((state.page + 1) * state.size, state.totalElements);
        host.className = 'list-pagination';
        host.innerHTML = `<span class="small text-muted" role="status">${start}–${end} / ${state.totalElements} kết quả</span>
            <div class="d-flex flex-wrap align-items-center gap-2">
                <label class="small mb-0" for="${id}-size">Số dòng</label>
                <select id="${id}-size" class="form-select form-select-sm list-page-size" aria-label="Số dòng mỗi trang">
                    ${[5, 10, 20, 50].map(size => `<option value="${size}" ${size === state.size ? 'selected' : ''}>${size}</option>`).join('')}
                </select>
                <button type="button" class="btn btn-sm btn-outline-secondary" data-page-step="-1" title="Trang trước" aria-label="Trang trước" ${state.page === 0 ? 'disabled' : ''}><i class="bi bi-chevron-left" aria-hidden="true"></i></button>
                <span class="small list-page-number">${state.totalPages ? state.page + 1 : 0} / ${state.totalPages}</span>
                <button type="button" class="btn btn-sm btn-outline-secondary" data-page-step="1" title="Trang sau" aria-label="Trang sau" ${state.page + 1 >= state.totalPages ? 'disabled' : ''}><i class="bi bi-chevron-right" aria-hidden="true"></i></button>
            </div>`;
        busy(state.loading);
    }
    function busy(loading) {
        state.loading = loading;
        host.setAttribute('aria-busy', String(loading));
        for (const control of host.querySelectorAll('button, select')) {
            if (loading) control.disabled = true;
            else if (control.matches('select')) control.disabled = false;
            else control.disabled = Number(control.dataset.pageStep) < 0 ? state.page === 0 : state.page + 1 >= state.totalPages;
        }
    }
    host.addEventListener('click', event => {
        const button = event.target.closest('[data-page-step]');
        if (!button || button.disabled || state.loading) return;
        state.page += Number(button.dataset.pageStep);
        reload();
    });
    host.addEventListener('change', event => {
        if (event.target.id !== `${id}-size` || state.loading) return;
        state.size = Number(event.target.value);
        state.page = 0;
        reload();
    });
    draw();
    return {
        state, busy,
        reset() { state.page = 0; },
        fail() { Object.assign(state, accepted); filterKey = acceptedKey; draw(); },
        params(values = {}) {
            const nextKey = JSON.stringify(values);
            if (filterKey !== nextKey) state.page = 0;
            filterKey = nextKey;
            const params = new URLSearchParams({ page: state.page, size: state.size });
            for (const [key, value] of Object.entries(values)) if (value !== '' && value !== null && value !== undefined) params.set(key, value);
            return params;
        },
        accept(result) {
            if (!result || !Array.isArray(result.items) || !Number.isInteger(result.totalPages)) throw new Error('Dữ liệu phân trang không hợp lệ.');
            if (state.page > 0 && state.page >= result.totalPages) {
                state.page = Math.max(0, result.totalPages - 1);
                Promise.resolve().then(reload);
                return false;
            }
            Object.assign(state, { page: result.page, size: result.size, totalElements: result.totalElements, totalPages: result.totalPages });
            accepted = { page: state.page, size: state.size, totalElements: state.totalElements, totalPages: state.totalPages };
            acceptedKey = filterKey;
            draw();
            return true;
        }
    };
}
