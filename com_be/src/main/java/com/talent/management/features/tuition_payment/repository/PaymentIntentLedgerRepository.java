package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.*;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@RequiredArgsConstructor
public class PaymentIntentLedgerRepository {
    private final EntityManager em;

    public Optional<TuitionInstallment> firstUnpaid(Long plan) {
        return em.createQuery(
                        "select d from TuitionInstallment d where d.plan.id=:id and d.amountDue >"
                            + " (select coalesce(sum(a.amount),0) from PaymentAllocation a where"
                            + " a.installment=d and"
                            + " a.payment.status=com.talent.management.shared.enums.PaymentStatus.SUCCESS"
                            + " and not exists(select r.id from TuitionReversal r where"
                            + " r.payment=a.payment and r.status='APPROVED')) order by"
                            + " d.sequenceNumber",
                        TuitionInstallment.class)
                .setParameter("id", plan)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public Optional<TuitionInstallment> installment(Long id) {
        return Optional.ofNullable(em.find(TuitionInstallment.class, id));
    }

    public List<TuitionPaymentIntent> boundIntents(Long invoice, Long installment) {
        return em.createQuery(
                        "select i from TuitionPaymentIntent i where i.invoice.id=:invoice and"
                                + " i.installmentId=:due",
                        TuitionPaymentIntent.class)
                .setParameter("invoice", invoice)
                .setParameter("due", installment)
                .getResultList();
    }

    public List<TuitionPaymentIntent> unboundIntents(Long invoice) {
        return em.createQuery(
                        "select i from TuitionPaymentIntent i where i.invoice.id=:invoice and"
                                + " i.installmentId is null and i.status in"
                                + " ('CREATING','PENDING','UNCERTAIN','PARTIALLY_PAID')",
                        TuitionPaymentIntent.class)
                .setParameter("invoice", invoice)
                .getResultList();
    }
}
