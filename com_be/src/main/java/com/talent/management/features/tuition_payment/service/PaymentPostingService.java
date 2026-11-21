package com.talent.management.features.tuition_payment.service;

import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.Payment;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.PaymentMethod;

import java.math.BigDecimal;

/** Application contract for PaymentPostingService. */
public interface PaymentPostingService {
    Payment post(
            Invoice invoice,
            TuitionPlan plan,
            BigDecimal amount,
            PaymentMethod method,
            User cashier,
            String key,
            String bankReference,
            BigDecimal cashGiven);
}
