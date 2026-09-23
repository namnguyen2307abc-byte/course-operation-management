/**
 * Main Entry Point for Tuition Payment & Cashier POS Module
 * Talent Academy - Clean Architecture Refactoring
 */

import { POS_TABS } from './constants.js';
import { getDashboardStats, searchPendingInvoices, getPaymentHistory } from './service.js';
import { renderDashboardStats, renderPendingTable, renderHistoryTable, renderReceiptModal, switchPosTab } from './ui.js';
import { initPaymentModal, openPaymentModal } from './modal-payment.js';

// Application State
let activeTab = POS_TABS.PENDING;
let pendingList = [];
let historyList = [];
let pendingPage = 1;
let historyPage = 1;
const PAGE_SIZE = 5;

/**
 * Tải navbar và cập nhật quyền hiển thị
 */
async function loadNavbar() {
    try {
        const resp = await fetch('/components/navbar.html');
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
        input.placeholder = "Tra cứu phiếu chờ thu: Gõ SĐT phụ huynh, tên học sinh hoặc mã hóa đơn (INV-...)...";
    } else {
        input.placeholder = "Tra cứu lịch sử đã thu: Gõ SĐT, tên học sinh, mã phiếu (PAY-...) hoặc mã HĐ...";
    }
}

/**
 * Tải dữ liệu danh sách phiếu giữ chỗ chờ thu
 */
async function loadPendingData(keyword = '') {
    try {
        pendingList = await searchPendingInvoices(keyword) || [];
        renderPendingCurrentPage();
    } catch (err) {
        console.error("Lỗi khi tải phiếu chờ thu:", err);
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
 * Tải dữ liệu danh sách lịch sử đã thu tiền
 */
async function loadHistoryData(keyword = '') {
    try {
        historyList = await getPaymentHistory(keyword) || [];
        renderHistoryCurrentPage();
    } catch (err) {
        console.error("Lỗi khi tải lịch sử thu tiền:", err);
    }
}

function renderHistoryCurrentPage() {
    renderHistoryTable(
        historyList,
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
        // 1. Tải thống kê POS
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

    updateSearchPlaceholder();

    // Tải dữ liệu ban đầu
    await refreshData();
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
