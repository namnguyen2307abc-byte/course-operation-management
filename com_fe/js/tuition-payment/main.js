import { POS_TABS } from './constants.js?v=20260927_v3';
import { getDashboardStats, searchPendingInvoices, getPaymentHistoryReport, getCashiers } from './service.js?v=20260927_v3';
import { renderDashboardStats, renderPendingTable, renderHistoryDashboard, renderReceiptModal, switchPosTab, populateCashierDropdown } from './ui.js?v=20260927_v3';
import { initPaymentModal, openPaymentModal } from './modal-payment.js?v=20260927_v3';

// Application State
let activeTab = POS_TABS.PENDING;
let pendingList = [];
let historyReport = { summary: {}, payments: [] };
let pendingPage = 1;
let historyPage = 1;
const PAGE_SIZE = 5;

// Filter State cho Admin History Dashboard
let currentTimeRange = 'TODAY';
let currentStartDate = '';
let currentEndDate = '';
let currentCashier = 'ALL';
let cashiersList = [];

/**
 * Tải navbar và cập nhật quyền hiển thị
 */
async function loadNavbar() {
    try {
        const navUrl = window.location.pathname.includes('/pages/') ? '../components/navbar.html' : './components/navbar.html';
        const resp = await fetch(navUrl);
        const html = await resp.text();
        const navContainer = document.getElementById('navbar-container');
        if (navContainer) {
            navContainer.innerHTML = html;
        }
        if (typeof window.updateNavbarUser === 'function') {
            window.updateNavbarUser();
        }
    } catch (err) {
        console.error("Không thể load navbar:", err);
    }
}

/**
 * Cập nhật gợi ý placeholder trên thanh tìm kiếm theo đúng tab đang mở
 */
function updateSearchPlaceholder() {
    const input = document.getElementById('searchInput');
    if (!input) return;
    if (activeTab === POS_TABS.PENDING) {
        input.placeholder = "Tra cứu hóa đơn chờ thu: Gõ SĐT phụ huynh, tên học sinh hoặc mã hóa đơn (INV-...)...";
    } else {
        input.placeholder = "Tra cứu lịch sử đã thu: Gõ SĐT, tên học sinh, tên thu ngân, mã phiếu (PAY-...) hoặc mã HĐ...";
    }
}

/**
 * Tải dữ liệu danh sách hóa đơn chờ thu
 */
async function loadPendingData(keyword = '') {
    try {
        pendingList = await searchPendingInvoices(keyword) || [];
        renderPendingCurrentPage();
    } catch (err) {
        console.error("Lỗi khi tải hóa đơn chờ thu:", err);
    }
}

function renderPendingCurrentPage() {
    renderPendingTable(
        pendingList,
        pendingPage,
        PAGE_SIZE,
        openPaymentModal,
        (newPage) => {
            pendingPage = newPage;
            renderPendingCurrentPage();
        }
    );
}

/**
 * Tải danh sách thu ngân vào dropdown bộ lọc của Admin
 */
async function loadCashiersData() {
    try {
        cashiersList = await getCashiers() || [];
        populateCashierDropdown(cashiersList);
    } catch (err) {
        console.error("Lỗi khi tải danh sách thu ngân:", err);
    }
}

/**
 * Tải dữ liệu báo cáo lịch sử và doanh thu Admin theo bộ lọc thời gian & thu ngân
 */
async function loadHistoryData(keyword = '') {
    try {
        historyReport = await getPaymentHistoryReport({
            timeRange: currentTimeRange,
            startDate: currentStartDate,
            endDate: currentEndDate,
            cashier: currentCashier,
            keyword
        }) || { summary: {}, payments: [] };
        renderHistoryCurrentPage();
    } catch (err) {
        console.error("Lỗi khi tải báo cáo lịch sử thu tiền:", err);
    }
}

function renderHistoryCurrentPage() {
    renderHistoryDashboard(
        historyReport,
        historyPage,
        PAGE_SIZE,
        renderReceiptModal,
        (newPage) => {
            historyPage = newPage;
            renderHistoryCurrentPage();
        }
    );
}

/**
 * Tải lại toàn bộ dữ liệu thống kê và bảng
 */
export async function refreshData() {
    try {
        // 1. Tải thống kê tổng quan ca trực
        const stats = await getDashboardStats();
        renderDashboardStats(stats);

        // 2. Tải cả 2 danh sách để cập nhật bảng và badges số lượng
        const currentKeyword = document.getElementById('searchInput')?.value.trim() || '';
        await Promise.all([
            loadPendingData(activeTab === POS_TABS.PENDING ? currentKeyword : ''),
            loadHistoryData(activeTab === POS_TABS.HISTORY ? currentKeyword : '')
        ]);
    } catch (err) {
        console.error("Lỗi khi tải dữ liệu POS:", err);
    }
}

/**
 * Xử lý tìm kiếm thông minh: "Tra bên nào thì ra bên đó", không tự ý chuyển tab!
 */
async function handleSearch() {
    const keyword = document.getElementById('searchInput')?.value.trim() || '';

    if (activeTab === POS_TABS.PENDING) {
        pendingPage = 1;
        await loadPendingData(keyword);
    } else {
        historyPage = 1;
        await loadHistoryData(keyword);
    }
}

/**
 * Xóa tìm kiếm và tải lại toàn bộ danh sách của tab hiện tại
 */
async function clearSearch() {
    const input = document.getElementById('searchInput');
    if (input) input.value = '';

    if (activeTab === POS_TABS.PENDING) {
        pendingPage = 1;
        await loadPendingData('');
    } else {
        historyPage = 1;
        await loadHistoryData('');
    }
}

/**
 * Đảm bảo tài khoản thu ngân đăng nhập để test thuận tiện
 */
async function ensureCashierAuth() {
    if (!localStorage.getItem('token') || !localStorage.getItem('user')) {
        try {
            const loginRes = await fetch("http://localhost:8080/api/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username: "cashier_mai", password: "123456" })
            });
            if (loginRes.ok) {
                const data = await loginRes.json();
                localStorage.setItem("token", data.token);
                localStorage.setItem("user", JSON.stringify(data));
            }
        } catch (e) {
            console.warn("Auto-login cashier error:", e);
        }
    }
}

/**
 * Khởi động ứng dụng
 */
async function bootstrapApp() {
    await ensureCashierAuth();
    await loadNavbar();

    // Khởi tạo controller modal thu tiền
    initPaymentModal(() => {
        refreshData();
    });

    // Gắn sự kiện thanh tìm kiếm
    document.getElementById('btnSearch')?.addEventListener('click', handleSearch);
    document.getElementById('btnClearSearch')?.addEventListener('click', clearSearch);
    document.getElementById('searchInput')?.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') handleSearch();
    });

    // Gắn sự kiện nút Làm mới
    document.getElementById('btnReloadData')?.addEventListener('click', () => {
        refreshData();
    });

    // Gắn sự kiện nút In biên lai
    document.getElementById('btnPrintReceipt')?.addEventListener('click', () => {
        window.print();
    });

    // Gắn sự kiện nút "Xem Trong Lịch Sử Thu Tiền" từ Modal Xác Nhận Thành Công
    document.getElementById('btnGoToHistory')?.addEventListener('click', () => {
        const receiptModalEl = document.getElementById('receiptModal');
        if (window.bootstrap && receiptModalEl) {
            const instance = window.bootstrap.Modal.getInstance(receiptModalEl);
            if (instance) instance.hide();
        }
        switchPosTab(POS_TABS.HISTORY, (tab) => {
            activeTab = tab;
            updateSearchPlaceholder();
        });
    });

    // Gắn sự kiện các nút lọc kỳ báo cáo doanh thu (Hôm nay / Tuần này / Tháng này / Tùy chọn)
    const periodBtns = [
        document.getElementById('filterTodayBtn'),
        document.getElementById('filterWeekBtn'),
        document.getElementById('filterMonthBtn'),
        document.getElementById('filterCustomBtn')
    ];

    periodBtns.forEach(btn => {
        btn?.addEventListener('click', async () => {
            periodBtns.forEach(b => b?.classList.remove('active'));
            btn.classList.add('active');
            const range = btn.getAttribute('data-range');
            currentTimeRange = range;

            const customRow = document.getElementById('customDateRangeRow');
            if (range === 'CUSTOM') {
                customRow?.classList.remove('d-none');
            } else {
                customRow?.classList.add('d-none');
                currentStartDate = '';
                currentEndDate = '';
                historyPage = 1;
                await loadHistoryData(document.getElementById('searchInput')?.value.trim() || '');
            }
        });
    });

    // Gắn sự kiện dropdown chọn Thu Ngân
    document.getElementById('filterCashierSelect')?.addEventListener('change', async (e) => {
        currentCashier = e.target.value;
        historyPage = 1;
        await loadHistoryData(document.getElementById('searchInput')?.value.trim() || '');
    });

    // Gắn sự kiện nút Áp dụng ngày tùy chọn
    document.getElementById('btnApplyCustomDate')?.addEventListener('click', async () => {
        currentStartDate = document.getElementById('filterStartDate')?.value || '';
        currentEndDate = document.getElementById('filterEndDate')?.value || '';
        historyPage = 1;
        await loadHistoryData(document.getElementById('searchInput')?.value.trim() || '');
    });

    // Gắn sự kiện chuyển tab (Không tự ý reset từ khóa nếu đang tìm kiếm)
    document.getElementById('tabPendingBtn')?.addEventListener('click', () => {
        switchPosTab(POS_TABS.PENDING, (tab) => {
            activeTab = tab;
            updateSearchPlaceholder();
        });
    });

    document.getElementById('tabHistoryBtn')?.addEventListener('click', () => {
        switchPosTab(POS_TABS.HISTORY, (tab) => {
            activeTab = tab;
            updateSearchPlaceholder();
        });
    });

    // Tải danh sách thu ngân & dữ liệu ban đầu
    await loadCashiersData();
    await refreshData();

    // Tự động mở modal thu tiền nếu được điều hướng từ bước xếp lớp (ảnh 2)
    const urlParams = new URLSearchParams(window.location.search);
    const invoiceIdParam = urlParams.get('invoiceId') || sessionStorage.getItem('autoOpenInvoiceId');
    const invoiceCodeParam = urlParams.get('invoiceCode') || sessionStorage.getItem('autoOpenInvoiceCode');

    // Dọn dẹp sessionStorage ngay sau khi đọc
    sessionStorage.removeItem('autoOpenInvoiceId');
    sessionStorage.removeItem('autoOpenInvoiceCode');

    if (invoiceIdParam || invoiceCodeParam) {
        let targetId = invoiceIdParam ? Number(invoiceIdParam) : null;
        if (!targetId && invoiceCodeParam) {
            const found = pendingList.find(inv => inv.invoiceCode === invoiceCodeParam);
            if (found) {
                targetId = found.id;
            } else {
                try {
                    const searchRes = await searchPendingInvoices(invoiceCodeParam);
                    if (searchRes && searchRes.length > 0) {
                        targetId = searchRes[0].id;
                    }
                } catch (e) {}
            }
        }

        if (targetId) {
            try {
                window.history.replaceState({}, document.title, window.location.pathname);
            } catch (e) {}

            setTimeout(async () => {
                try {
                    await openPaymentModal(targetId);
                } catch (err) {
                    console.error("Lỗi khi tự động mở modal thu tiền:", err);
                }
            }, 100);
        }
    }
}

// Chạy ứng dụng khi DOM sẵn sàng (hỗ trợ cả DOMContentLoaded và readyState complete)
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bootstrapApp);
} else {
    bootstrapApp();
}

// Export ra window để hỗ trợ tương thích nếu cần
window.TuitionPos = {
    refreshData,
    openPaymentModal,
    handleSearch,
    clearSearch
};
