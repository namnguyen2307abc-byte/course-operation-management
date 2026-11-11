package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.TuitionInstallment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TuitionInstallmentRepository extends JpaRepository<TuitionInstallment, Long> {
    List<TuitionInstallment> findByPlanIdOrderBySequenceNumber(Long planId);
}
