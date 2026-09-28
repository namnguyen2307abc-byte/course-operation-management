/**
 * UI Rendering & DOM Management for Tuition Payment & POS Module
 * Talent Academy
 */

import { formatCurrency, formatDateTime } from './utils.js?v=20260927_v3';
import { POS_TABS } from './constants.js?v=20260927_v3';

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
 * Render bảng danh sách hóa đơn chờ nộp học phí (kèm phân trang, loại bỏ giới hạn 24h)
 */
export function renderPendingTable(invoices, currentPage = 1, pageSize = 5, onPayClick, onPageChange) {
    const tbody = document.getElementById('pendingTableBody');
    const badgeCount = document.getElementById('tabPendingBadge');
    const resultBadge = document.getElementById('resultCountBadge');

    const total = invoices ? invoices.length : 0;
    if (badgeCount) badgeCount.innerText = total;
    if (resultBadge) resultBadge.innerText = `${total} Hóa đơn`;

    if (!tbody) return;

    if (!invoices || invoices.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center py-5">
                    <div class="text-muted">
                        <i class="bi bi-inbox fs-1 d-block mb-2 text-secondary"></i>
                        Không tìm thấy hóa đơn học phí nào cần thu.
                    </div>
                </td>
            </tr>
        `;
        renderPaginationControls('pendingPaginationList', 'pendingPaginationInfo', 0, 1, pageSize, 'hóa đơn', onPageChange);
        return;
    }

    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const safePage = Math.min(Math.max(1, currentPage), totalPages);
    const startIdx = (safePage - 1) * pageSize;
    const pagedInvoices = invoices.slice(startIdx, startIdx + pageSize);

    tbody.innerHTML = pagedInvoices.map(inv => {
        const amountDisplay = inv.finalAmount != null ? inv.finalAmount : inv.originalAmount;

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
                    <div class="fw-semibold text-dark">${inv.parentName || 'Khách vãng lai'}</div>
                    <div class="small"><i class="bi bi-telephone text-primary me-1"></i><code>${inv.parentPhone || 'Chưa có SĐT'}</code></div>
                </td>
                <td>
                    <span class="fw-bold text-primary">${inv.className || 'Chưa xếp lớp'}</span>
                    <div class="text-muted small">${inv.branchName || ''} • ${inv.roomName || ''}</div>
                </td>
                <td>
                    <strong class="text-dark fs-6">${formatCurrency(amountDisplay)}</strong>
                    ${inv.discountAmount && inv.discountAmount > 0 ? `<div class="text-success small"><i class="bi bi-tag-fill me-1"></i>Đã giảm ${formatCurrency(inv.discountAmount)}</div>` : ''}
                </td>
                <td>
                    <span class="badge bg-warning-subtle text-warning border border-warning-subtle px-2 py-1">
                        <i class="bi bi-clock me-1"></i>Chờ thu phí
                    </span>
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
    renderPaginationControls('pendingPaginationList', 'pendingPaginationInfo', total, safePage, pageSize, 'hóa đơn', onPageChange);
}

/**
 * Đổ danh sách thu ngân vào dropdown bộ lọc của Admin
 */
export function populateCashierDropdown(cashiers) {
    const select = document.getElementById('filterCashierSelect');
    if (!select) return;

    const currentVal = select.value;
    let html = '<option value="ALL">👤 Tất cả thu ngân</option>';

    if (Array.isArray(cashiers)) {
        cashiers.forEach(c => {
            html += `<option value="${c.username}">${c.fullName} (${c.username})</option>`;
        });
    }

    select.innerHTML = html;
    if (currentVal) select.value = currentVal;
}

/**
 * Render Dashboard Báo Cáo Lịch Sử Cho Admin:
 * Cập nhật 3 Thẻ Tổng Doanh Thu (Tiền mặt, PayOS VietQR, Tổng) và Bảng Giao Dịch
 */
export function renderHistoryDashboard(report, currentPage = 1, pageSize = 5, onViewReceipt, onPageChange) {
    if (!report) return;

    const summary = report.summary || {};
    const payments = report.payments || [];

    // 1. Cập nhật 3 Thẻ Tổng KPI
    const cashEl = document.getElementById('kpiCashAmount');
    const cashCountEl = document.getElementById('kpiCashCount');
    const bankEl = document.getElementById('kpiBankAmount');
    const bankCountEl = document.getElementById('kpiBankCount');
    const totalEl = document.getElementById('kpiTotalRevenue');
    const totalCountEl = document.getElementById('kpiTotalTransactions');

    if (cashEl) cashEl.innerText = formatCurrency(summary.totalCashAmount || 0);
    if (cashCountEl) cashCountEl.innerText = `${summary.totalCashCount || 0} giao dịch tiền mặt`;

    if (bankEl) bankEl.innerText = formatCurrency(summary.totalBankAmount || 0);
    if (bankCountEl) bankCountEl.innerText = `${summary.totalBankCount || 0} giao dịch PayOS / VietQR`;

    if (totalEl) totalEl.innerText = formatCurrency(summary.totalRevenue || 0);
    if (totalCountEl) totalCountEl.innerText = `Tổng ${summary.totalTransactions || 0} giao dịch`;

    // 2. Cập nhật Badges
    const badgeCount = document.getElementById('tabHistoryBadge');
    const countBadge = document.getElementById('historyCountBadge');
    const total = payments.length;
    if (badgeCount) badgeCount.innerText = total;
    if (countBadge) countBadge.innerText = `${total} Giao dịch`;

    // 3. Render Bảng Lịch Sử
    const tbody = document.getElementById('historyTableBody');
    if (!tbody) return;

    if (total === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-5"><i class="bi bi-inbox fs-2 d-block mb-2 text-secondary"></i>Không có giao dịch nào phù hợp với bộ lọc thời gian & thu ngân này.</td></tr>';
        renderPaginationControls('historyPaginationList', 'historyPaginationInfo', 0, 1, pageSize, 'giao dịch', onPageChange);
        return;
    }

    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const safePage = Math.min(Math.max(1, currentPage), totalPages);
    const startIdx = (safePage - 1) * pageSize;
    const pagedPayments = payments.slice(startIdx, startIdx + pageSize);

    tbody.innerHTML = pagedPayments.map((p, idx) => {
        let methodBadge = 'bg-secondary';
        let methodText = p.paymentMethod;
        if (p.paymentMethod === 'CASH_AT_DESK') {
            methodBadge = 'bg-success';
            methodText = '💵 Tiền Mặt';
        } else if (p.paymentMethod === 'VIET_QR' || p.paymentMethod === 'BANK_TRANSFER') {
            methodBadge = 'bg-primary';
            methodText = '📱 PayOS VietQR';
        }

        const isFree = (p.discountType === 'FULL_FREE') || (p.originalAmount > 0 && p.finalAmount === 0);
        const hasDiscount = (p.discountAmount && p.discountAmount > 0) || isFree;

        let discountBadgeHtml = '';
        if (isFree) {
            discountBadgeHtml = `
                <span class="badge bg-danger text-white fw-bold mb-1 shadow-sm">
                    <i class="bi bi-gift-fill me-1"></i>Miễn phí 100% (-${formatCurrency(p.discountAmount || p.originalAmount)})
                </span>
                <div class="small fw-semibold text-dark mt-1" style="max-width: 250px; line-height: 1.3;">
                    <i class="bi bi-ticket-perforated text-danger me-1"></i>${p.discountReason || 'Học bổng tài năng 100%'}
                </div>
            `;
        } else if (hasDiscount) {
            discountBadgeHtml = `
                <span class="badge bg-danger-subtle text-danger border border-danger-subtle fw-bold mb-1">
                    <i class="bi bi-tag-fill me-1"></i>Giảm -${formatCurrency(p.discountAmount)}
                </span>
                <div class="small fw-semibold text-dark mt-1" style="max-width: 250px; line-height: 1.3;">
                    <i class="bi bi-ticket-perforated text-danger me-1"></i>${p.discountReason || 'Ưu đãi học phí'}
                </div>
            `;
        } else {
            discountBadgeHtml = `<span class="badge bg-light text-muted border">Không giảm giá</span>`;
        }

        return `
            <tr>
                <td class="ps-4">
                    <code class="fw-bold">${p.paymentCode || ''}</code>
                    <div class="text-muted small">${p.invoiceCode || ''}</div>
                </td>
                <td>
                    <strong class="text-dark d-block">${p.studentName || ''}</strong>
                    <div class="small text-muted">${p.parentName || ''} • <code>${p.parentPhone || ''}</code></div>
                </td>
                <td><span class="text-primary small fw-bold">${p.className || ''}</span></td>
                <td><span class="text-muted fw-semibold">${formatCurrency(p.originalAmount || p.finalAmount)}</span></td>
                <td>${discountBadgeHtml}</td>
                <td>
                    <strong class="text-success fs-6">${formatCurrency(p.finalAmount)}</strong>
                    ${p.paymentMethod === 'CASH_AT_DESK' ? `
                        <div class="small text-muted" style="font-size:0.75rem">Đưa: ${formatCurrency(p.cashGiven || p.finalAmount)} | Thối: ${formatCurrency(p.changeAmount || 0)}</div>
                    ` : `
                        <div class="small text-muted" style="font-size:0.75rem">Thối: 0 đ (QR)</div>
                    `}
                </td>
                <td><span class="badge ${methodBadge} px-2 py-1">${methodText}</span></td>
                <td><span class="badge bg-light text-dark border">${p.cashierName || 'Thu Ngân'}</span></td>
                <td class="small text-muted">${formatDateTime(p.paymentDate)}</td>
                <td class="text-end pe-4">
                    <button class="btn btn-outline-secondary btn-sm py-1 px-3 rounded-pill btn-view-receipt" data-index="${idx}">
                        <i class="bi bi-eye me-1"></i> Xem
                    </button>
                </td>
            </tr>
        `;
    }).join('');

    tbody.querySelectorAll('.btn-view-receipt').forEach(btn => {
        btn.addEventListener('click', () => {
            const index = parseInt(btn.getAttribute('data-index'));
            if (onViewReceipt && pagedPayments[index]) {
                onViewReceipt(pagedPayments[index]);
            }
        });
    });

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
    const discountReasonText = document.getElementById('rcDiscountReasonText');
    const isFree = (receipt.discountType === 'FULL_FREE') || (receipt.originalAmount > 0 && receipt.finalAmount === 0);
    const discountAmount = receipt.discountAmount || (isFree ? receipt.originalAmount : 0);

    if (discountAmount > 0 || isFree) {
        discountRow?.classList.remove('d-none');
        discountRow?.classList.add('d-flex');
        if (document.getElementById('rcDiscountAmount')) {
            if (isFree) {
                document.getElementById('rcDiscountAmount').innerText = `Miễn 100% (-${formatCurrency(discountAmount)})`;
            } else {
                document.getElementById('rcDiscountAmount').innerText = `- ${formatCurrency(discountAmount)}`;
            }
        }
        if (discountReasonText) {
            discountReasonText.innerText = receipt.discountReason || (isFree ? 'Học bổng tài năng âm nhạc 100%' : 'Ưu đãi học phí');
        }
    } else {
        discountRow?.classList.add('d-none');
        discountRow?.classList.remove('d-flex');
    }

    document.getElementById('rcFinalAmount').innerText = formatCurrency(receipt.finalAmount);

    const isCash = receipt.paymentMethod === 'CASH_AT_DESK';
    const cashGivenRow = document.getElementById('rcCashGivenRow');
    const changeRow = document.getElementById('rcChangeRow');
    const cashGivenLabel = document.getElementById('rcCashGivenLabel');
    const cashGivenEl = document.getElementById('rcCashGiven');
    const changeAmountEl = document.getElementById('rcChangeAmount');

    cashGivenRow?.classList.remove('d-none');
    cashGivenRow?.classList.add('d-flex');
    changeRow?.classList.remove('d-none');
    changeRow?.classList.add('d-flex');

    if (isCash) {
        if (cashGivenLabel) cashGivenLabel.innerText = 'Tiền khách đưa:';
        if (cashGivenEl) cashGivenEl.innerText = formatCurrency(receipt.cashGiven || receipt.finalAmount);
        if (changeAmountEl) changeAmountEl.innerText = formatCurrency(receipt.changeAmount || 0);
    } else {
        if (cashGivenLabel) cashGivenLabel.innerText = 'Tiền chuyển khoản:';
        if (cashGivenEl) cashGivenEl.innerText = formatCurrency(receipt.finalAmount);
        if (changeAmountEl) changeAmountEl.innerText = '0 đ (Chuyển khoản chính xác)';
    }

    const methodEl = document.getElementById('rcPaymentMethod');
    if (methodEl) {
        if (isCash) {
            methodEl.className = 'badge bg-success px-2 py-1';
            methodEl.innerHTML = '<i class="bi bi-cash me-1"></i> TIỀN MẶT TẠI QUẦY';
        } else {
            methodEl.className = 'badge bg-primary px-2 py-1';
            methodEl.innerHTML = '<i class="bi bi-qr-code me-1"></i> QUÉT MÃ VIETQR (PAYOS)';
        }
    }

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
