package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.request.ApplyDiscountRequest;
import com.talent.management.features.tuition_payment.dto.request.CreateReservationRequest;
import com.talent.management.features.tuition_payment.dto.request.ProcessPaymentRequest;
import com.talent.management.features.tuition_payment.dto.response.*;

import java.math.BigDecimal;
import java.util.List;

public interface TuitionPaymentService {

    List<PendingInvoiceResponse> searchPendingInvoices(String keyword);

    PendingInvoiceResponse getInvoiceDetail(Long invoiceId);

    PendingInvoiceResponse calculateDiscount(Long invoiceId, ApplyDiscountRequest request);

    VietQrResponse generateVietQr(Long invoiceId, BigDecimal customAmount);

    PaymentReceiptResponse processPayment(ProcessPaymentRequest request, String cashierUsername);

    InvoicePaymentStatusResponse checkPaymentStatus(Long invoiceId);

    PaymentReceiptResponse simulateBankWebhook(Long invoiceId, BigDecimal customAmount);

    PendingInvoiceResponse createDemoReservation(CreateReservationRequest request, String registeredByUsername);

    List<PaymentReceiptResponse> getPaymentHistory(String keyword);

    PaymentHistoryReportResponse getPaymentHistoryReport(String timeRange, java.time.LocalDate startDate, java.time.LocalDate endDate, String cashierUsername, String keyword);

    List<com.talent.management.features.tuition_payment.dto.response.CashierOptionResponse> getCashierList();

    com.talent.management.features.tuition_payment.dto.response.PayOSPaymentLinkResponse createPayOSPaymentLink(Long invoiceId, BigDecimal customAmount);

    com.talent.management.features.tuition_payment.dto.response.PayOSPaymentLinkResponse createPayOSPaymentLink(Long invoiceId, BigDecimal customAmount, String discountType, BigDecimal discountAmount, String discountReason);

    PaymentReceiptResponse processPayOSWebhook(java.util.Map<String, Object> payload);

    CashierDashboardStatsResponse getDashboardStats();

    void fixVietnameseFontData();
}

