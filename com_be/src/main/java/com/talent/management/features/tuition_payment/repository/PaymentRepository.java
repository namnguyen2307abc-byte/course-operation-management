package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceId(Long invoiceId);

    Optional<Payment> findFirstByInvoiceIdOrderByIdDesc(Long invoiceId);

    List<Payment> findAllByOrderByPaymentDateDesc();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'SUCCESS' AND p.paymentDate >= :since")
    BigDecimal sumAmountSince(LocalDateTime since);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCESS' AND p.paymentDate >= :since")
    long countPaymentsSince(LocalDateTime since);
}
