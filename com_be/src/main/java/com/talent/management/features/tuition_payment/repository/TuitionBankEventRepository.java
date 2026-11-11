package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.TuitionBankEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface TuitionBankEventRepository extends JpaRepository<TuitionBankEvent, Long> {
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select e from TuitionBankEvent e where e.id=:id")
    Optional<TuitionBankEvent> lockById(
            @org.springframework.data.repository.query.Param("id") Long id);

    Optional<TuitionBankEvent> findByReference(String reference);

    List<TuitionBankEvent> findByStatusOrderByIdDesc(String status);
}
