package com.talent.management.features.tuition_payment.service;

import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.Payment;
import com.talent.management.shared.entity.TuitionPlan;

import java.math.BigDecimal;

/** Application contract for TuitionLedgerService. */
public interface TuitionLedgerService {
    Invoice lock(Long id);

    void checkParent(Invoice invoice);

    BigDecimal paid(Invoice invoice);

    BigDecimal balance(Invoice invoice);

    void refreshUnpaidFullPlan(Invoice invoice);

    TuitionPlan ensure(Invoice invoice);

    void requireCollectible(Invoice invoice, TuitionPlan plan, BigDecimal amount);

    void validateMoney(BigDecimal amount);

    void requireCurrentInstallment(Invoice invoice, TuitionPlan plan, BigDecimal amount);

    void allocate(Payment payment, TuitionPlan plan);
}
