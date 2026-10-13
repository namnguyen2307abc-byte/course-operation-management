/**
 * API Service for Tuition Payment & POS Module
 * Talent Academy
 */

// Helper gọi API an toàn thông qua hàm callApi toàn cục
async function apiRequest(endpoint, method = 'GET', body = null) {
    if (typeof window.callApi === 'function') {
        return await window.callApi(endpoint, method, body);
    }
    // Fallback nếu window.callApi chưa sẵn sàng
    const token = localStorage.getItem('token');
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;

    const res = await fetch(`http://localhost:8080${endpoint}`, {
        method,
        headers,
        body: body ? JSON.stringify(body) : null
    });
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || `Lỗi API: ${res.status}`);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

/**
 * Lấy dữ liệu thống kê nhanh trên dashboard quầy POS
 */
export async function getDashboardStats() {
    return await apiRequest('/api/tuition-payment/stats');
}

/**
 * Tra cứu danh sách phiếu giữ chỗ chờ nộp tiền
 */
export async function searchPendingInvoices(keyword = '') {
    const trimmed = keyword ? keyword.trim() : '';
    const url = trimmed
        ? `/api/tuition-payment/search?keyword=${encodeURIComponent(trimmed)}`
        : '/api/tuition-payment/search';
    return await apiRequest(url);
}

/**
 * Lấy thông tin chi tiết một hóa đơn phiếu giữ chỗ
 */
export async function getInvoiceDetail(invoiceId) {
    return await apiRequest(`/api/tuition-payment/${invoiceId}`);
}

/**
 * Xử lý xác nhận thu học phí và ghi danh học viên
 */
export async function executeProcessPayment(payload) {
    return await apiRequest('/api/tuition-payment/process', 'POST', payload);
}

/**
 * Lấy lịch sử các giao dịch thu tiền đã hoàn tất
 */
export async function getPaymentHistory(keyword = '') {
    const trimmed = keyword ? keyword.trim() : '';
    const url = trimmed
        ? `/api/tuition-payment/history?keyword=${encodeURIComponent(trimmed)}`
        : '/api/tuition-payment/history';
    return await apiRequest(url);
}

/**
 * Gọi backend sinh thông tin VietQR (đồng bộ Agribank)
 */
export async function getVietQr(invoiceId, amount) {
    return await apiRequest(`/api/tuition-payment/${invoiceId}/vietqr?amount=${amount}`);
}

/**
 * Gọi backend tạo liên kết thanh toán trực tiếp qua Cổng PayOS thật
 */
export async function createPayOSLink(invoiceId, amount, discountType = '', discountAmount = 0, discountReason = '') {
    const params = new URLSearchParams();
    if (amount) params.append('amount', amount);
    if (discountType) params.append('discountType', discountType);
    if (discountAmount) params.append('discountAmount', discountAmount);
    if (discountReason) params.append('discountReason', discountReason);

    const query = params.toString();
    const url = query 
        ? `/api/tuition-payment/${invoiceId}/payos-link?${query}`
        : `/api/tuition-payment/${invoiceId}/payos-link`;
    return await apiRequest(url, 'POST');
}

/**
 * Lấy báo cáo lịch sử thu ngân kèm 3 thẻ KPI tổng hợp (Admin Dashboard)
 */
export async function getPaymentHistoryReport({ timeRange = 'TODAY', startDate = '', endDate = '', cashier = '', keyword = '' } = {}) {
    const params = new URLSearchParams();
    if (timeRange) params.append('timeRange', timeRange);
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);
    if (cashier) params.append('cashier', cashier);
    if (keyword) params.append('keyword', keyword);

    return await apiRequest(`/api/tuition-payment/history-report?${params.toString()}`);
}

/**
 * Lấy danh sách nhân viên thu ngân / admin phục vụ bộ lọc
 */
export async function getCashiers() {
    return await apiRequest('/api/tuition-payment/cashiers');
}

/**
 * Kiểm tra trạng thái thanh toán hóa đơn phục vụ tự động nhận diện thanh toán Auto-Detect
 */
export async function checkInvoicePaymentStatus(invoiceId) {
    return await apiRequest(`/api/tuition-payment/${invoiceId}/status`);
}

/**
 * Gọi backend mô phỏng Webhook Ngân Hàng báo có (Ting-Ting tự động kích hoạt)
 */
export async function triggerSimulateBankWebhook(invoiceId, amount) {
    const url = amount 
        ? `/api/tuition-payment/${invoiceId}/bank-webhook-simulate?amount=${amount}`
        : `/api/tuition-payment/${invoiceId}/bank-webhook-simulate`;
    return await apiRequest(url, 'POST');
}
