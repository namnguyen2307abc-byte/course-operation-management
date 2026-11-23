package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.TuitionPaymentIntent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface TuitionIntentRepository extends JpaRepository<TuitionPaymentIntent, Long> {
    boolean existsByInvoiceIdAndStatusIn(Long invoiceId, java.util.Collection<String> statuses);

    Optional<TuitionPaymentIntent> findByOrderCode(long orderCode);

    @Query("select i.invoice.id from TuitionPaymentIntent i where i.id=:id")
    Optional<Long> findInvoiceId(@Param("id") Long id);

    List<TuitionPaymentIntent> findByInvoiceIdAndStatus(Long invoiceId, String status);

    @Query(
            "select i.id from TuitionPaymentIntent i where i.status in"
                    + " ('CREATING','PENDING','UNCERTAIN') and i.createdAt <= :before")
    List<Long> findExpiredIds(@Param("before") java.time.LocalDateTime before);

    @Query(
            "select i.id from TuitionPaymentIntent i where i.simulated=false and i.status in"
                + " ('PENDING','PARTIALLY_PAID','UNCERTAIN') and i.createdAt>=:since order by i.id"
                + " desc")
    List<Long> findBankSyncIds(
            @Param("since") java.time.LocalDateTime since,
            org.springframework.data.domain.Pageable pageable);
}
