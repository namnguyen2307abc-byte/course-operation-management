/**
 * Utilities for Tuition Payment & POS Module
 * Talent Academy
 */

import { AGRIBANK_CONFIG } from './constants.js';

/**
 * Định dạng tiền tệ VND chuẩn Việt Nam
 */
export function formatCurrency(val) {
    if (val === null || val === undefined || isNaN(val)) return '0 đ';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val);
}

/**
 * Định dạng ngày giờ hiển thị
 */
export function formatDateTime(dtStr) {
    if (!dtStr) return '--/--/----';
    const d = new Date(dtStr);
    if (isNaN(d.getTime())) return dtStr;
    return d.toLocaleDateString('vi-VN') + ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

/**
 * Loại bỏ dấu tiếng Việt chuẩn quốc tế (an toàn cho nội dung chuyển khoản ngân hàng)
 */
export function removeVietnameseAccents(str) {
    if (!str) return '';
    return str.normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[đĐ]/g, m => m === 'đ' ? 'd' : 'D');
}

/**
 * Sinh cú pháp nội dung chuyển khoản ngân hàng tự động:
 * [Mã HĐ] [Tên học viên] dang ky lop [Tên lớp] (Tối đa 50 ký tự, không dấu)
 */
export function buildTransferContent(invoiceCode, studentName, className) {
    const inv = invoiceCode || 'INV';
    const studentClean = removeVietnameseAccents(studentName || '')
        .replace(/[^a-zA-Z0-9 ]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    const classClean = removeVietnameseAccents(className || '')
        .replace(/[^a-zA-Z0-9 ]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();

    let content = `${inv} ${studentClean} dang ky lop ${classClean}`.replace(/\s+/g, ' ').trim();
    if (content.length > 50) {
        content = content.substring(0, 50).trim();
    }
    return content;
}

/**
 * Sinh URL mã QR Agribank động theo chuẩn VietQR
 * https://img.vietqr.io/image/970405-3511205288130-compact2.png?amount={finalAmount}&addInfo={transferContent}&accountName=HOANG%20DUC%20THUAN
 */
export function buildAgribankVietQrUrl(finalAmount, transferContent) {
    const amount = Math.max(0, Math.round(finalAmount || 0));
    const encodedContent = encodeURIComponent(transferContent || '');
    return `https://img.vietqr.io/image/${AGRIBANK_CONFIG.bankBin}-${AGRIBANK_CONFIG.accountNo}-compact2.png?amount=${amount}&addInfo=${encodedContent}&accountName=${AGRIBANK_CONFIG.accountNameEncoded}`;
}

/**
 * Phát âm thanh Ting-Ting mô phỏng thông báo chuyển khoản thành công của quầy POS
 */
export function playPaymentSuccessChime() {
    try {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextClass) return;
        const ctx = new AudioContextClass();

        // Nốt 1: 587.33 Hz (D5)
        const osc1 = ctx.createOscillator();
        const gain1 = ctx.createGain();
        osc1.type = 'sine';
        osc1.frequency.setValueAtTime(587.33, ctx.currentTime);
        gain1.gain.setValueAtTime(0.2, ctx.currentTime);
        gain1.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.35);
        osc1.connect(gain1);
        gain1.connect(ctx.destination);
        osc1.start();
        osc1.stop(ctx.currentTime + 0.35);

        // Nốt 2: 880 Hz (A5 - Ting cao vui tai)
        const osc2 = ctx.createOscillator();
        const gain2 = ctx.createGain();
        osc2.type = 'sine';
        osc2.frequency.setValueAtTime(880, ctx.currentTime + 0.12);
        gain2.gain.setValueAtTime(0.3, ctx.currentTime + 0.12);
        gain2.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.7);
        osc2.connect(gain2);
        gain2.connect(ctx.destination);
        osc2.start(ctx.currentTime + 0.12);
        osc2.stop(ctx.currentTime + 0.7);
    } catch (e) {
        console.warn("Chime blocked or unsupported:", e);
    }
}
