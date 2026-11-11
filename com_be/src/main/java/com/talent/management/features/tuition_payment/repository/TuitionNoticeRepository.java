package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.TuitionNotice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface TuitionNoticeRepository extends JpaRepository<TuitionNotice, Long> {
    boolean existsByEventKey(String key);

    List<TuitionNotice> findByRecipientIdOrderByIdDesc(Long id);

    List<TuitionNotice> findByInstallmentPlanInvoiceIdOrderByIdDesc(Long invoiceId);
}
