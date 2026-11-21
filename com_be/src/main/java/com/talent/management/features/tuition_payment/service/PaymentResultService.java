package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.request.ConfirmBankPaymentRequest;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;

import java.math.BigDecimal;
import java.util.Map;

/** Application contract for PaymentResultService. */
public interface PaymentResultService {
    PaymentReceiptResponse webhook(Map<String, Object> body);

    PaymentReceiptResponse confirmBank(Long id, ConfirmBankPaymentRequest input);

    /**
     * Internal entry point: caller must have verified bank evidence; never expose as a user API.
     */
    PaymentReceiptResponse applyVerifiedTransfer(
            long code, BigDecimal amount, String reference, boolean simulated);
}
