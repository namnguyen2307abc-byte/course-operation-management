package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.shared.entity.Payment;

import java.math.BigDecimal;
import java.util.Optional;

/** Application contract for CashCollectionService. */
public interface CashCollectionService {
    PaymentReceiptResponse collectReceipt(
            Long invoiceId, BigDecimal amount, BigDecimal cashGiven, String key);

    Payment cash(Long invoiceId, BigDecimal amount, BigDecimal cashGiven, String key);

    Optional<PaymentReceiptResponse> replayCashReceipt(
            Long invoiceId, BigDecimal amount, BigDecimal cashGiven, String key);
}
