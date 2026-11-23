package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.IntentView;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;

import java.math.BigDecimal;

/** Application contract for PaymentIntentService. */
public interface PaymentIntentService {
    IntentView create(Long invoiceId, BigDecimal amount);

    IntentView createForReminder(Long invoiceId);

    IntentView status(Long id);

    void prepareCashPayment(Long invoiceId);

    /** Close verified unpaid payment attempts before changing the tuition schedule. */
    void closeUnpaidRequests(Long invoiceId);

    void prepareCashPayment(Long invoiceId, BigDecimal amount, BigDecimal given);

    void cancel(Long id);

    void expireUnpaidRequests();

    PaymentReceiptResponse simulate(Long id);

    /** Background gateway synchronization only; not exposed as a user API. */
    void refreshFromBank(Long id);
}
