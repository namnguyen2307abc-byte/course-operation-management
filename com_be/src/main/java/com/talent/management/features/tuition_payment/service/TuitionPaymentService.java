package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.request.ApplyDiscountRequest;
import com.talent.management.features.tuition_payment.dto.request.CreateReservationRequest;
import com.talent.management.features.tuition_payment.dto.request.ProcessPaymentRequest;
import com.talent.management.features.tuition_payment.dto.response.CashierDashboardStatsResponse;
import com.talent.management.features.tuition_payment.dto.response.InvoicePaymentStatusResponse;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.dto.response.PendingInvoiceResponse;
import com.talent.management.features.tuition_payment.dto.response.VietQrResponse;

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

    CashierDashboardStatsResponse getDashboardStats();

    void fixVietnameseFontData();
}
