/**
 * Constants & Configuration for Tuition Payment & POS Module
 * Talent Academy
 */

// Cố định thông tin tài khoản Agribank để test theo yêu cầu đặc tả
export const AGRIBANK_CONFIG = Object.freeze({
    bankName: 'Agribank (Ngân hàng Nông nghiệp & PTNT)',
    bankBin: '970405',
    bankCode: 'AGRIBANK',
    accountNo: '3511205288130',
    accountName: 'HOANG DUC THUAN',
    accountNameEncoded: 'HOANG%20DUC%20THUAN'
});

// Các hình thức thu học phí (chỉ giữ Tiền mặt và Quét VietQR Ngân hàng, loại bỏ hoàn toàn MoMo)
export const PAYMENT_METHODS = Object.freeze({
    CASH_AT_DESK: 'CASH_AT_DESK',
    VIET_QR: 'VIET_QR'
});

// 3 Diện thu học phí
export const DISCOUNT_TYPES = Object.freeze({
    NONE: 'NONE',                       // 1. Thu đủ 100%
    PARTIAL_DISCOUNT: 'PARTIAL_DISCOUNT', // 2. Giảm học phí
    FULL_FREE: 'FULL_FREE'              // 3. Miễn phí 100% (Học bổng)
});

// Chính sách giảm phí tạo sẵn
export const DISCOUNT_PRESETS = Object.freeze({
    EARLY_BIRD: {
        value: 20,
        method: 'PERCENTAGE',
        reason: 'Ưu đãi đăng ký sớm giảm 20%'
    },
    SIBLINGS: {
        value: 10,
        method: 'PERCENTAGE',
        reason: 'Anh chị em cùng học giảm 10%'
    },
    VOUCHER_500K: {
        value: 500000,
        method: 'FIXED_AMOUNT',
        reason: 'Áp dụng Voucher TALENT2026 (-500.000đ)'
    }
});

// Tab hiển thị POS
export const POS_TABS = Object.freeze({
    PENDING: 'PENDING',
    HISTORY: 'HISTORY'
});
