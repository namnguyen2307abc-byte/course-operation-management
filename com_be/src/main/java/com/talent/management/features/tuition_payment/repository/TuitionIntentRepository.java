package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.TuitionPaymentIntent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface TuitionIntentRepository extends JpaRepository<TuitionPaymentIntent, Long> {
    Optional<TuitionPaymentIntent> findByOrderCode(long orderCode);

    @Query("select i.invoice.id from TuitionPaymentIntent i where i.id=:id")
    Optional<Long> findInvoiceId(@Param("id") Long id);

    List<TuitionPaymentIntent> findByInvoiceIdAndStatus(Long invoiceId, String status);

    @Query("select i.id from TuitionPaymentIntent i where i.status in ('CREATING','PENDING','UNCERTAIN') and i.createdAt <= :before")
    List<Long> findExpiredIds(@Param("before") java.time.LocalDateTime before);
}
