package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.TuitionBankEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface TuitionBankEventRepository extends JpaRepository<TuitionBankEvent, Long> {
    Optional<TuitionBankEvent> findByReference(String reference);

    List<TuitionBankEvent> findByStatusOrderByIdDesc(String status);

    boolean existsByIntentIdAndStatus(Long intentId, String status);

    @org.springframework.data.jpa.repository.Query(
            "select count(e) from TuitionBankEvent e where e.intent.invoice.id=:invoice and"
                    + " e.status='RECONCILE' and e.id<>:event")
    long pendingInvoiceHolds(
            @org.springframework.data.repository.query.Param("invoice") Long invoiceId,
            @org.springframework.data.repository.query.Param("event") Long excludedEventId);
}
