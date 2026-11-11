package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.TuitionPlanChange;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface TuitionPlanChangeRepository extends JpaRepository<TuitionPlanChange, Long> {
    List<TuitionPlanChange> findAllByOrderByIdDesc();

    boolean existsByInstallmentIdAndStatus(Long id, String status);
}
