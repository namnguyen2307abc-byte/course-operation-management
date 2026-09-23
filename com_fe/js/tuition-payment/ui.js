/**
 * UI Rendering & DOM Management for Tuition Payment & POS Module
 * Talent Academy
 */

import { formatCurrency, formatDateTime } from './utils.js';
import { POS_TABS } from './constants.js';

/**
 * Cập nhật các thẻ thống kê tổng quan của ca trực POS
 */
export function renderDashboardStats(stats) {
    if (!stats) return;
    const pendingEl = document.getElementById('statPendingCount');
    const paidEl = document.getElementById('statPaidTodayCount');
    const revenueEl = document.getElementById('statRevenueToday');
    const enrolledEl = document.getElementById('statTotalStudentsEnrolled');

    if (pendingEl) pendingEl.innerText = `${stats.pendingInvoicesCount || 0} Phiếu`;
    if (paidEl) paidEl.innerText = `${stats.paidTodayCount || 0} Lượt`;
    if (revenueEl) revenueEl.innerText = formatCurrency(stats.revenueToday || 0);
    if (enrolledEl) enrolledEl.innerText = `${stats.totalStudentsEnrolled || 0} Học viên`;
}

/**
 * Render thanh điều hướng phân trang chung
 */
export function renderPaginationControls(listId, infoId, totalItems, currentPage, pageSize, unitName, onPageChange) {
    const listEl = document.getElementById(listId);
    const infoEl = document.getElementById(infoId);
    if (!listEl || !infoEl) return;

    if (!totalItems || totalItems === 0) {
        infoEl.innerText = `Hiển thị 0 trong 0 ${unitName}`;
        listEl.innerHTML = '';
        return;
    }

    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const safePage = Math.min(Math.max(1, currentPage), totalPages);

    const start = (safePage - 1) * pageSize + 1;
    const end = Math.min(totalItems, safePage * pageSize);

    infoEl.innerText = `Hiển thị ${start} - ${end} trong tổng số ${totalItems} ${unitName}`;

    if (totalPages <= 1) {
        listEl.innerHTML = '';
        return;
    }

    let html = '';
    // Nút Trước
    html += `
        <li class="page-item ${safePage === 1 ? 'disabled' : ''}">
            <button class="page-link" data-page="${safePage - 1}" ${safePage === 1 ? 'disabled' : ''}>
                <i class="bi bi-chevron-left me-1"></i> Trước
            </button>
        </li>
    `;

    // Danh sách số trang
    for (let p = 1; p <= totalPages; p++) {
        html += `
            <li class="page-item ${p === safePage ? 'active' : ''}">
                <button class="page-link" data-page="${p}">${p}</button>
            </li>
        `;
    }

    // Nút Sau
    html += `
        <li class="page-item ${safePage === totalPages ? 'disabled' : ''}">
            <button class="page-link" data-page="${safePage + 1}" ${safePage === totalPages ? 'disabled' : ''}>
                Sau <i class="bi bi-chevron-right ms-1"></i>
            </button>
        </li>
    `;

    listEl.innerHTML = html;

    listEl.querySelectorAll('.page-link').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            const targetPage = parseInt(btn.getAttribute('data-page'));
            if (!isNaN(targetPage) && targetPage >= 1 && targetPage <= totalPages && targetPage !== safePage) {
                if (onPageChange) onPageChange(targetPage);
            }
        });
    });
}

/**
 * Render bảng danh sách phiếu giữ chỗ chờ nộp học phí (kèm phân trang)
 */
export function renderPendingTable(invoices, currentPage = 1, pageSize = 5, onPayClick, onPageChange) {
    const tbody = document.getElementById('pendingTableBody');
    const badgeCount = document.getElementById('tabPendingBadge');
    const resultBadge = document.getElementById('resultCountBadge');

    const total = invoices ? invoices.length : 0;
    if (badgeCount) badgeCount.innerText = total;
    if (resultBadge) resultBadge.innerText = `${total} Phiếu`;

    if (!tbody) return;

    if (!invoices || invoices.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="text-center py-5">
                    <div class="text-muted">
                        <i class="bi bi-inbox fs-1 d-block mb-2 text-secondary"></i>
                        Không tìm thấy phiếu giữ chỗ nào phù hợp.
                    </div>
                </td>
            </tr>
        `;
        renderPaginationControls('pendingPaginationList', 'pendingPaginationInfo', 0, 1, pageSize, 'phiếu', onPageChange);
        return;
    }

    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const safePage = Math.min(Math.max(1, currentPage), totalPages);
    const startIdx = (safePage - 1) * pageSize;
    const pagedInvoices = invoices.slice(startIdx, startIdx + pageSize);

    tbody.innerHTML = pagedInvoices.map(inv => {
        const hoursLeft = inv.hoursLeft || 0;
        const isExp = inv.expired;
        let badgeClass = 'badge-reservation';
        let badgeText = `Còn ${hoursLeft} giờ`;
        if (isExp) {
            badgeClass = 'badge-reservation danger';
            badgeText = 'Hết hạn giữ chỗ';
        } else if (hoursLeft <= 3) {
            badgeClass = 'badge-reservation danger';
            badgeText = `Gấp: Còn ${hoursLeft} giờ`;
        }

        return `
            <tr>
                <td class="ps-4">
                    <span class="badge bg-light text-primary border border-primary-subtle fw-bold">${inv.invoiceCode || ''}</span>
                </td>
                <td>
                    <strong class="text-dark fs-6">${inv.studentName || ''}</strong>
                    <div class="text-muted small">${inv.studentDob ? 'NS: ' + inv.studentDob : ''}</div>
                </td>
                <td>
                    <div class="fw-semibold text-dark">${inv.parentName || 'Chưa cập nhật'}</div>
                    <div class="small"><i class="bi bi-telephone text-primary me-1"></i><code>${inv.parentPhone || 'N/A'}</code></div>
                </td>
                <td>
                    <span class="fw-bold text-primary">${inv.className || ''}</span>
                    <div class="text-muted small">${inv.branchName || ''} • ${inv.roomName || ''}</div>
                </td>
                <td>
                    <strong class="text-dark">${formatCurrency(inv.originalAmount)}</strong>
                </td>
                <td>
                    <span class="${badgeClass}"><i class="bi bi-stopwatch me-1"></i>${badgeText}</span>
                </td>
                <td>
                    <span class="badge bg-warning-subtle text-warning border border-warning-subtle">Chờ nộp tiền</span>
                </td>
                <td class="text-end pe-4">
                    <button class="btn-pay-action btn-sm" data-invoice-id="${inv.invoiceId}">
                        <i class="bi bi-cash-coin me-1"></i> Thu Tiền
                    </button>
                </td>
            </tr>
        `;
    }).join('');

    // Đăng ký event click nút Thu Tiền
    tbody.querySelectorAll('.btn-pay-action').forEach(btn => {
        btn.addEventListener('click', () => {
            const invId = btn.getAttribute('data-invoice-id');
            if (onPayClick) onPayClick(invId);
        });
    });

    // Cập nhật điều hướng phân trang
    renderPaginationControls('pendingPaginationList', 'pendingPaginationInfo', total, safePage, pageSize, 'phiếu', onPageChange);
}

/**
 * Render bảng lịch sử các giao dịch đã thu học phí (kèm phân trang)
 */
export function renderHistoryTable(history, currentPage = 1, pageSize = 5, onViewReceipt, onPageChange) {
    const tbody = document.getElementById('historyTableBody');
    const badgeCount = document.getElementById('tabHistoryBadge');
    const countBadge = document.getElementById('historyCountBadge');

    const total = history ? history.length : 0;
    if (badgeCount) badgeCount.innerText = total;
    if (countBadge) countBadge.innerText = `${total} Giao dịch`;

    if (!tbody) return;

    if (!history || history.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">Không tìm thấy giao dịch nào phù hợp trong lịch sử.</td></tr>';
        renderPaginationControls('historyPaginationList', 'historyPaginationInfo', 0, 1, pageSize, 'giao dịch', onPageChange);
        return;
    }

    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const safePage = Math.min(Math.max(1, currentPage), totalPages);
    const startIdx = (safePage - 1) * pageSize;
    const pagedHistory = history.slice(startIdx, startIdx + pageSize);

    tbody.innerHTML = pagedHistory.map((p, idx) => {
        let methodBadge = 'bg-secondary';
        let methodText = p.paymentMethod;
        if (p.paymentMethod === 'CASH_AT_DESK') {
            methodBadge = 'bg-success';
            methodText = 'Tiền Mặt';
        } else if (p.paymentMethod === 'VIET_QR' || p.paymentMethod === 'BANK_TRANSFER') {
            methodBadge = 'bg-primary';
            methodText = 'VietQR Agribank';
        }

        return `
            <tr>
                <td class="ps-4">
                    <code class="fw-bold">${p.paymentCode || ''}</code>
                    <div class="text-muted small">${p.invoiceCode || ''}</div>
                </td>
                <td><strong class="text-dark">${p.studentName || ''}</strong></td>
                <td>
                    <div class="small">${p.parentName || ''}</div>
                    <code class="small text-muted">${p.parentPhone || ''}</code>
                </td>
                <td><span class="text-primary small fw-bold">${p.className || ''}</span></td>
                <td><strong class="text-success">${formatCurrency(p.finalAmount)}</strong></td>
                <td><span class="badge ${methodBadge}">${methodText}</span></td>
                <td class="small text-muted">${p.cashierName || 'Thu Ngân'}</td>
                <td class="small text-muted">${formatDateTime(p.paymentDate)}</td>
                <td class="text-end pe-4">
                    <button class="btn btn-outline-secondary btn-sm py-1 px-2 rounded-pill btn-view-receipt" data-index="${idx}">
                        <i class="bi bi-eye"></i> Xem
                    </button>
                </td>
            </tr>
        `;
    }).join('');

    tbody.querySelectorAll('.btn-view-receipt').forEach(btn => {
        btn.addEventListener('click', () => {
            const index = parseInt(btn.getAttribute('data-index'));
            if (onViewReceipt && pagedHistory[index]) {
                onViewReceipt(pagedHistory[index]);
            }
        });
    });

    // Cập nhật điều hướng phân trang
    renderPaginationControls('historyPaginationList', 'historyPaginationInfo', total, safePage, pageSize, 'giao dịch', onPageChange);
}

/**
 * Hiển thị thông báo Toast lướt qua góc trên bên phải
 */
export function showToast(title, message, isSuccess = true) {
    const headerBox = document.getElementById('toastHeaderBox');
    const icon = document.getElementById('toastIcon');
    const titleEl = document.getElementById('toastTitle');
    const msgEl = document.getElementById('toastMessage');

    if (headerBox) {
        headerBox.className = isSuccess
            ? 'toast-header bg-success text-white py-2 px-3 border-0'
            : 'toast-header bg-danger text-white py-2 px-3 border-0';
    }
    if (icon) {
        icon.className = isSuccess
            ? 'bi bi-check-circle-fill me-2 fs-5'
            : 'bi bi-exclamation-triangle-fill me-2 fs-5';
    }
    if (titleEl) titleEl.innerHTML = title;
    if (msgEl) msgEl.innerHTML = message;

    const toastEl = document.getElementById('actionToast');
    if (toastEl && window.bootstrap) {
        const toast = window.bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 4500 });
        toast.show();
    }
}

/**
 * Hiển thị Modal Biên lai thu học phí
 */
export function renderReceiptModal(receipt) {
    if (!receipt) return;

    document.getElementById('rcPaymentCode').innerText = receipt.paymentCode || 'PAY-...';
    document.getElementById('rcInvoiceCode').innerText = receipt.invoiceCode || 'INV-...';
    document.getElementById('rcPaymentDate').innerText = formatDateTime(receipt.paymentDate);
    document.getElementById('rcCashierName').innerText = receipt.cashierName || 'Thu Ngân Quầy';
    document.getElementById('rcStudentName').innerText = receipt.studentName || 'Học Sinh';
    document.getElementById('rcParentInfo').innerText = `${receipt.parentName || 'Phụ huynh'} (${receipt.parentPhone || 'Chưa có SĐT'})`;
    document.getElementById('rcClassName').innerText = `${receipt.className || ''} (${receipt.branchName || ''})`;
    document.getElementById('rcOriginalAmount').innerText = formatCurrency(receipt.originalAmount);

    const discountRow = document.getElementById('rcDiscountRow');
    if (receipt.discountAmount && receipt.discountAmount > 0) {
        discountRow.style.display = 'flex';
        document.getElementById('rcDiscountAmount').innerText = `- ${formatCurrency(receipt.discountAmount)}`;
    } else {
        discountRow.style.display = 'none';
    }

    document.getElementById('rcFinalAmount').innerText = formatCurrency(receipt.finalAmount);

    const isCash = receipt.paymentMethod === 'CASH_AT_DESK';
    const cashGivenRow = document.getElementById('rcCashGivenRow');
    const changeRow = document.getElementById('rcChangeRow');
    if (isCash && receipt.cashGiven != null && receipt.cashGiven > 0) {
        cashGivenRow.style.display = 'flex';
        changeRow.style.display = 'flex';
        document.getElementById('rcCashGiven').innerText = formatCurrency(receipt.cashGiven);
        document.getElementById('rcChangeAmount').innerText = formatCurrency(receipt.changeAmount || 0);
    } else {
        cashGivenRow.style.display = 'none';
        changeRow.style.display = 'none';
        document.getElementById('rcCashGiven').innerText = '0 đ';
        document.getElementById('rcChangeAmount').innerText = '0 đ';
    }

    document.getElementById('rcPaymentMethod').innerText = receipt.paymentMethod === 'CASH_AT_DESK' ? 'TIỀN MẶT' : 'QUÉT VIETQR AGRIBANK';

    if (window.bootstrap) {
        const modalEl = document.getElementById('receiptModal');
        const receiptModal = window.bootstrap.Modal.getOrCreateInstance(modalEl);
        receiptModal.show();
    }
}

/**
 * Chuyển đổi tab POS (PENDING vs HISTORY)
 */
export function switchPosTab(tab, onTabChanged) {
    const tabPendingBtn = document.getElementById('tabPendingBtn');
    const tabHistoryBtn = document.getElementById('tabHistoryBtn');
    const pendingView = document.getElementById('pendingTableView');
    const historyView = document.getElementById('historyTableView');

    if (tab === POS_TABS.PENDING) {
        if (tabPendingBtn) tabPendingBtn.classList.add('active');
        if (tabHistoryBtn) tabHistoryBtn.classList.remove('active');
        if (pendingView) pendingView.classList.remove('d-none');
        if (historyView) historyView.classList.add('d-none');
    } else {
        if (tabHistoryBtn) tabHistoryBtn.classList.add('active');
        if (tabPendingBtn) tabPendingBtn.classList.remove('active');
        if (historyView) historyView.classList.remove('d-none');
        if (pendingView) pendingView.classList.add('d-none');
    }

    if (onTabChanged) onTabChanged(tab);
}
