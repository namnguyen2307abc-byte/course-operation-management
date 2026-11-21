package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.request.ApplyDiscountRequest;
import com.talent.management.features.tuition_payment.dto.request.CreateReservationRequest;
import com.talent.management.features.tuition_payment.dto.request.ProcessPaymentRequest;
import com.talent.management.features.tuition_payment.dto.response.CashierDashboardStatsResponse;
import com.talent.management.features.tuition_payment.dto.response.CashierOptionResponse;
import com.talent.management.features.tuition_payment.dto.response.InvoicePaymentStatusResponse;
import com.talent.management.features.tuition_payment.dto.response.PayOSPaymentLinkResponse;
import com.talent.management.features.tuition_payment.dto.response.PaymentHistoryReportResponse;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.dto.response.PendingInvoiceResponse;
import com.talent.management.features.tuition_payment.dto.response.VietQrResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Compatibility API for earlier clients. Current installment flows use the focused service
 * contracts.
 */
public interface TuitionPaymentService {

    List<PendingInvoiceResponse> searchPendingInvoices(String keyword);

    PendingInvoiceResponse getInvoiceDetail(Long invoiceId);

    PendingInvoiceResponse calculateDiscount(Long invoiceId, ApplyDiscountRequest request);

    VietQrResponse generateVietQr(Long invoiceId, BigDecimal customAmount);

    PaymentReceiptResponse processPayment(ProcessPaymentRequest request, String cashierUsername);

    InvoicePaymentStatusResponse checkPaymentStatus(Long invoiceId);

    PaymentReceiptResponse simulateBankWebhook(Long invoiceId, BigDecimal customAmount);

    PendingInvoiceResponse createDemoReservation(
            CreateReservationRequest request, String registeredByUsername);

    List<PaymentReceiptResponse> getPaymentHistory(String keyword);

    PaymentHistoryReportResponse getPaymentHistoryReport(
            String timeRange,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            String cashierUsername,
            String keyword);

    List<CashierOptionResponse> getCashierList();

    PayOSPaymentLinkResponse createPayOSPaymentLink(Long invoiceId, BigDecimal customAmount);

    PayOSPaymentLinkResponse createPayOSPaymentLink(
            Long invoiceId,
            BigDecimal customAmount,
            String discountType,
            BigDecimal discountAmount,
            String discountReason);

    PaymentReceiptResponse processPayOSWebhook(java.util.Map<String, Object> payload);

    CashierDashboardStatsResponse getDashboardStats();

    void fixVietnameseFontData();
}
