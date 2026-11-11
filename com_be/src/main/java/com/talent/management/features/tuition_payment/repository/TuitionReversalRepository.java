package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.TuitionReversal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface TuitionReversalRepository extends JpaRepository<TuitionReversal, Long> {
    boolean existsByPaymentIdAndStatus(Long id, String status);

    boolean existsByPaymentId(Long id);

    List<TuitionReversal> findAllByOrderByIdDesc();
}
