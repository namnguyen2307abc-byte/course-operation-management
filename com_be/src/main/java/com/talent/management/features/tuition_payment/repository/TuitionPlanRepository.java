package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.TuitionPlan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TuitionPlanRepository extends JpaRepository<TuitionPlan, Long> {
    Optional<TuitionPlan> findByInvoiceId(Long id);
}
