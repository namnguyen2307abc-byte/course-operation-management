package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.PaymentAllocation;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    @Query(
            "select coalesce(sum(a.amount),0) from PaymentAllocation a where a.installment.id=:id"
                + " and a.payment.status=com.talent.management.shared.enums.PaymentStatus.SUCCESS"
                + " and not exists (select r.id from TuitionReversal r where r.payment=a.payment"
                + " and r.status='APPROVED')")
    BigDecimal paid(@Param("id") Long id);
}
