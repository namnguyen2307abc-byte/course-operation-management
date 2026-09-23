/**
 * Modal Payment POS Controller
 * Handles 3 Fee Tiers (Full, Discount, Free) and 2 Methods (Cash, VietQR Agribank)
 * Talent Academy
 */

import { AGRIBANK_CONFIG, DISCOUNT_TYPES, DISCOUNT_PRESETS, PAYMENT_METHODS } from './constants.js';
import { formatCurrency, buildTransferContent, buildAgribankVietQrUrl, playPaymentSuccessChime } from './utils.js';
import { getInvoiceDetail, executeProcessPayment } from './service.js';
import { showToast, renderReceiptModal } from './ui.js';

// Module State
let currentInvoice = null;
let currentDiscountType = DISCOUNT_TYPES.NONE;
let currentDiscountAmount = 0;
let currentDiscountReason = '';
let currentFinalAmount = 0;
let currentPaymentMethod = PAYMENT_METHODS.CASH_AT_DESK;
let onPaymentCompletedCallback = null;

/**
 * Khởi tạo lắng nghe sự kiện trên Modal Thu Tiền
 */
export function initPaymentModal(onCompleted) {
    onPaymentCompletedCallback = onCompleted;

    // Gắn sự kiện các radio / card discount
    document.getElementById('discountOptNone')?.addEventListener('click', () => setDiscountType(DISCOUNT_TYPES.NONE));
    document.getElementById('discountOptPartial')?.addEventListener('click', () => setDiscountType(DISCOUNT_TYPES.PARTIAL_DISCOUNT));
    document.getElementById('discountOptFree')?.addEventListener('click', () => setDiscountType(DISCOUNT_TYPES.FULL_FREE));

    // Nút áp dụng nhanh chiết khấu
    document.getElementById('btnPresetEarlyBird')?.addEventListener('click', () => applyPreset('EARLY_BIRD'));
    document.getElementById('btnPresetSiblings')?.addEventListener('click', () => applyPreset('SIBLINGS'));
    document.getElementById('btnPresetVoucher')?.addEventListener('click', () => applyPreset('VOUCHER_500K'));

    // Input chiết khấu tùy chỉnh
    document.getElementById('customDiscountValue')?.addEventListener('input', onCustomDiscountInput);
    document.getElementById('customDiscountMethod')?.addEventListener('change', onCustomDiscountInput);
    document.getElementById('customDiscountReason')?.addEventListener('input', onCustomDiscountInput);
    document.getElementById('freeDiscountReason')?.addEventListener('input', onFreeDiscountInput);

    // Chuyển phương thức Tiền Mặt vs VietQR
    document.getElementById('payMethodCashBtn')?.addEventListener('click', () => setPaymentMethod(PAYMENT_METHODS.CASH_AT_DESK));
    document.getElementById('payMethodQrBtn')?.addEventListener('click', () => setPaymentMethod(PAYMENT_METHODS.VIET_QR));

    // Tiền mặt tại quầy
    document.getElementById('cashGivenInput')?.addEventListener('input', calculateChange);
    document.getElementById('btnCashExact')?.addEventListener('click', () => quickFillCash('EXACT'));
    document.getElementById('btnCash4M')?.addEventListener('click', () => quickFillCash(4000000));
    document.getElementById('btnCash5M')?.addEventListener('click', () => quickFillCash(5000000));

    // Sao chép nội dung chuyển khoản
    document.getElementById('btnCopyQrContent')?.addEventListener('click', copyTransferContent);

    // Nút xác nhận thanh toán & ghi danh
    document.getElementById('btnConfirmPayment')?.addEventListener('click', handleConfirmPayment);
}

/**
 * Mở modal thu tiền cho một hóa đơn
 */
export async function openPaymentModal(invoiceId) {
    try {
        const invoice = await getInvoiceDetail(invoiceId);
        if (!invoice) return;

        currentInvoice = invoice;
        currentDiscountType = DISCOUNT_TYPES.NONE;
        currentDiscountAmount = 0;
        currentDiscountReason = '';
        currentFinalAmount = invoice.originalAmount || 0;
        currentPaymentMethod = PAYMENT_METHODS.CASH_AT_DESK;

        // Điền thông tin học sinh & lớp học
        document.getElementById('modalStudentName').innerText = invoice.studentName || 'Học Sinh';
        document.getElementById('modalParentInfo').innerText = `Phụ huynh: ${invoice.parentName || 'N/A'} • SĐT: ${invoice.parentPhone || 'N/A'}`;
        document.getElementById('modalInvoiceCode').innerText = invoice.invoiceCode || 'INV-...';
        document.getElementById('modalClassName').innerText = `${invoice.className || ''} (${invoice.courseName || ''})`;
        document.getElementById('modalBranchRoom').innerText = `${invoice.branchName || ''} • ${invoice.roomName || ''}`;
        document.getElementById('modalSchedule').innerText = invoice.scheduleDescription || 'Lịch học cập nhật';

        // Đặt mặc định tài khoản nhận tiền Agribank trên giao diện QR
        document.getElementById('qrAccountNameDisplay').innerText = AGRIBANK_CONFIG.accountName;
        document.getElementById('qrAccountNoDisplay').innerHTML = `${AGRIBANK_CONFIG.bankName} - <code>${AGRIBANK_CONFIG.accountNo}</code>`;

        // Reset các ô nhập tiền mặt & ghi chú
        const cashGivenInput = document.getElementById('cashGivenInput');
        if (cashGivenInput) cashGivenInput.value = '';
        const cashNoteInput = document.getElementById('cashNoteInput');
        if (cashNoteInput) cashNoteInput.value = '';
        const bankTransIdInput = document.getElementById('bankTransIdInput');
        if (bankTransIdInput) bankTransIdInput.value = '';

        // Reset về diện 1: Thu đủ 100%
        setDiscountType(DISCOUNT_TYPES.NONE);
        // Reset về phương thức 1: Tiền mặt
        setPaymentMethod(PAYMENT_METHODS.CASH_AT_DESK);

        // Hiển thị modal
        if (window.bootstrap) {
            const modalEl = document.getElementById('paymentModal');
            const modalInstance = window.bootstrap.Modal.getOrCreateInstance(modalEl);
            modalInstance.show();
        }
    } catch (err) {
        alert("Lỗi khi mở hóa đơn thu tiền: " + err.message);
    }
}

/**
 * Xử lý chọn 1 trong 3 diện thu phí
 */
export function setDiscountType(type) {
    currentDiscountType = type;
    const optNone = document.getElementById('discountOptNone');
    const optPartial = document.getElementById('discountOptPartial');
    const optFree = document.getElementById('discountOptFree');
    const partialControls = document.getElementById('partialDiscountControls');
    const freeControls = document.getElementById('freeDiscountControls');

    optNone?.classList.remove('selected');
    optPartial?.classList.remove('selected');
    optFree?.classList.remove('selected');
    partialControls?.classList.add('d-none');
    freeControls?.classList.add('d-none');

    document.getElementById('radioNone').checked = false;
    document.getElementById('radioPartial').checked = false;
    document.getElementById('radioFree').checked = false;

    const originalAmount = currentInvoice ? currentInvoice.originalAmount : 0;

    if (type === DISCOUNT_TYPES.NONE) {
        optNone?.classList.add('selected');
        document.getElementById('radioNone').checked = true;
        currentDiscountAmount = 0;
        currentDiscountReason = '';
        currentFinalAmount = originalAmount;
    } else if (type === DISCOUNT_TYPES.PARTIAL_DISCOUNT) {
        optPartial?.classList.add('selected');
        document.getElementById('radioPartial').checked = true;
        partialControls?.classList.remove('d-none');
        onCustomDiscountInput();
        return;
    } else if (type === DISCOUNT_TYPES.FULL_FREE) {
        optFree?.classList.add('selected');
        document.getElementById('radioFree').checked = true;
        freeControls?.classList.remove('d-none');
        currentDiscountAmount = originalAmount;
        currentDiscountReason = document.getElementById('freeDiscountReason')?.value.trim() || 'Học bổng tài năng âm nhạc 100%';
        currentFinalAmount = 0;
    }

    updatePriceDisplay();
}

/**
 * Áp dụng chính sách ưu đãi tạo sẵn
 */
export function applyPreset(presetKey) {
    const preset = DISCOUNT_PRESETS[presetKey];
    if (!preset) return;

    const valInput = document.getElementById('customDiscountValue');
    const methodSelect = document.getElementById('customDiscountMethod');
    const reasonInput = document.getElementById('customDiscountReason');

    if (valInput) valInput.value = preset.value;
    if (methodSelect) methodSelect.value = preset.method;
    if (reasonInput) reasonInput.value = preset.reason;

    onCustomDiscountInput();
}

function onCustomDiscountInput() {
    if (currentDiscountType !== DISCOUNT_TYPES.PARTIAL_DISCOUNT) return;
    const val = parseFloat(document.getElementById('customDiscountValue')?.value) || 0;
    const method = document.getElementById('customDiscountMethod')?.value || 'PERCENTAGE';
    const reason = document.getElementById('customDiscountReason')?.value.trim() || '';
    const orig = currentInvoice ? currentInvoice.originalAmount : 0;

    if (method === 'PERCENTAGE') {
        currentDiscountAmount = Math.round(orig * (val / 100));
        currentDiscountReason = reason || `Giảm ${val}% học phí`;
    } else {
        currentDiscountAmount = val;
        currentDiscountReason = reason || `Giảm tiền mặt ${formatCurrency(val)}`;
    }

    currentDiscountAmount = Math.min(orig, Math.max(0, currentDiscountAmount));
    currentFinalAmount = Math.max(0, orig - currentDiscountAmount);
    updatePriceDisplay();
}

function onFreeDiscountInput() {
    if (currentDiscountType === DISCOUNT_TYPES.FULL_FREE) {
        currentDiscountReason = document.getElementById('freeDiscountReason')?.value.trim() || 'Học bổng tài năng 100%';
        updatePriceDisplay();
    }
}

/**
 * Cập nhật bảng tổng kết số tiền cần thu
 */
function updatePriceDisplay() {
    const orig = currentInvoice ? currentInvoice.originalAmount : 0;
    document.getElementById('calcOriginalAmount').innerText = formatCurrency(orig);
    document.getElementById('calcDiscountAmount').innerText = `- ${formatCurrency(currentDiscountAmount)}`;
    document.getElementById('calcFinalAmount').innerText = formatCurrency(currentFinalAmount);

    const reasonRow = document.getElementById('calcReasonRow');
    const reasonText = document.getElementById('calcReasonText');
    if (currentDiscountReason) {
        if (reasonRow) reasonRow.style.display = 'flex';
        if (reasonText) reasonText.innerText = currentDiscountReason;
    } else {
        if (reasonRow) reasonRow.style.display = 'none';
    }

    // Đồng bộ sang bên phải
    if (currentPaymentMethod === PAYMENT_METHODS.CASH_AT_DESK) {
        calculateChange();
    } else {
        renderAgribankVietQr();
    }
}

/**
 * Chọn hình thức thanh toán (Tiền mặt hoặc Quét VietQR Agribank)
 */
export function setPaymentMethod(method) {
    currentPaymentMethod = method;
    const cashBtn = document.getElementById('payMethodCashBtn');
    const qrBtn = document.getElementById('payMethodQrBtn');
    const cashView = document.getElementById('cashPaymentView');
    const qrView = document.getElementById('qrPaymentView');

    if (method === PAYMENT_METHODS.CASH_AT_DESK) {
        cashBtn?.classList.add('active');
        qrBtn?.classList.remove('active');
        cashView?.classList.remove('d-none');
        qrView?.classList.add('d-none');
        quickFillCash('EXACT');
    } else {
        qrBtn?.classList.add('active');
        cashBtn?.classList.remove('active');
        qrView?.classList.remove('d-none');
        cashView?.classList.add('d-none');
        renderAgribankVietQr();
    }
}

/**
 * Tính tiền thối lại cho phụ huynh
 */
function calculateChange() {
    const cashGiven = parseFloat(document.getElementById('cashGivenInput')?.value) || 0;
    const change = Math.max(0, cashGiven - currentFinalAmount);
    const display = document.getElementById('cashChangeDisplay');
    if (display) display.innerText = formatCurrency(change);
}

function quickFillCash(mode) {
    const input = document.getElementById('cashGivenInput');
    if (!input) return;
    if (mode === 'EXACT') {
        input.value = currentFinalAmount;
    } else {
        input.value = mode;
    }
    calculateChange();
}

/**
 * Sinh mã VietQR Agribank cố định kèm nội dung và số tiền thực thu
 */
function renderAgribankVietQr() {
    if (!currentInvoice) return;

    const transferContent = buildTransferContent(
        currentInvoice.invoiceCode,
        currentInvoice.studentName,
        currentInvoice.className
    );

    const qrUrl = buildAgribankVietQrUrl(currentFinalAmount, transferContent);

    const qrImg = document.getElementById('vietQrImage');
    const amountEl = document.getElementById('qrAmountDisplay');
    const contentEl = document.getElementById('qrTransferContent');

    if (qrImg) qrImg.src = qrUrl;
    if (amountEl) amountEl.innerText = formatCurrency(currentFinalAmount);
    if (contentEl) contentEl.innerText = transferContent;
}

function copyTransferContent() {
    const content = document.getElementById('qrTransferContent')?.innerText || '';
    if (!content) return;
    navigator.clipboard.writeText(content).then(() => {
        showToast("Đã sao chép!", "Nội dung chuyển khoản: " + content, true);
    });
}

/**
 * Thực thi xác nhận thu học phí và kích hoạt ghi danh
 */
async function handleConfirmPayment() {
    if (!currentInvoice) return;

    const isCash = currentPaymentMethod === PAYMENT_METHODS.CASH_AT_DESK;
    let cashGiven = null;
    if (isCash) {
        const parsed = parseFloat(document.getElementById('cashGivenInput')?.value);
        if (isNaN(parsed) || parsed < currentFinalAmount) {
            cashGiven = currentFinalAmount;
            const input = document.getElementById('cashGivenInput');
            if (input) input.value = cashGiven;
            calculateChange();
        } else {
            cashGiven = parsed;
        }
    }

    const btnConfirm = document.getElementById('btnConfirmPayment');
    const originalBtnHtml = btnConfirm ? btnConfirm.innerHTML : '';
    if (btnConfirm) {
        btnConfirm.disabled = true;
        btnConfirm.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status"></span> Đang xác nhận ghi danh...';
    }

    const payload = {
        invoiceId: currentInvoice.invoiceId,
        paymentMethod: currentPaymentMethod,
        discountType: currentDiscountType,
        discountAmount: currentDiscountAmount,
        discountReason: currentDiscountReason,
        finalAmount: currentFinalAmount,
        cashGiven: isCash ? cashGiven : null,
        bankTransactionId: !isCash ? (document.getElementById('bankTransIdInput')?.value.trim() || 'AGRI-' + Date.now()) : null,
        note: isCash ? (document.getElementById('cashNoteInput')?.value.trim() || 'Thu tiền mặt tại quầy') : 'Quét VietQR Agribank chuyển khoản'
    };

    try {
        const receipt = await executeProcessPayment(payload);
        if (receipt) {
            // Khôi phục nút
            if (btnConfirm) {
                btnConfirm.disabled = false;
                btnConfirm.innerHTML = originalBtnHtml;
            }

            // Phát âm thanh Ting-Ting hoàn thành
            playPaymentSuccessChime();

            // Đóng modal thu tiền an toàn và đợi transition kết thúc mới mở receipt modal
            const paymentModalEl = document.getElementById('paymentModal');
            if (window.bootstrap && paymentModalEl) {
                const modalInstance = window.bootstrap.Modal.getInstance(paymentModalEl);
                if (modalInstance) {
                    paymentModalEl.addEventListener('hidden.bs.modal', () => {
                        setTimeout(() => {
                            renderReceiptModal(receipt);
                        }, 50);
                    }, { once: true });
                    modalInstance.hide();
                } else {
                    renderReceiptModal(receipt);
                }
            } else {
                renderReceiptModal(receipt);
            }

            // Hiển thị thông báo Toast lướt qua
            showToast(
                "🎉 THU HỌC PHÍ & GHI DANH THÀNH CÔNG!",
                `Đã thu <strong>${formatCurrency(receipt.finalAmount)}</strong> từ học viên <strong>${receipt.studentName}</strong>.<br><i class="bi bi-mortarboard-fill text-success me-1"></i>Học sinh đã chính thức được xếp vào lớp <strong>${receipt.className}</strong>.`,
                true
            );

            // Kích hoạt callback tải lại dữ liệu bảng & thống kê
            if (onPaymentCompletedCallback) {
                onPaymentCompletedCallback(receipt);
            }
        }
    } catch (err) {
        if (btnConfirm) {
            btnConfirm.disabled = false;
            btnConfirm.innerHTML = originalBtnHtml;
        }
        showToast("Lỗi xử lý giao dịch", err.message || "Không thể xử lý thanh toán!", false);
    }
}
