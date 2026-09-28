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
    BigDecimal sumAmountSince(@org.springframework.data.repository.query.Param("since") LocalDateTime since);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCESS' AND p.paymentDate >= :since")
    long countPaymentsSince(@org.springframework.data.repository.query.Param("since") LocalDateTime since);

    @Query("SELECT p FROM Payment p " +
           "LEFT JOIN FETCH p.invoice i " +
           "LEFT JOIN FETCH i.student s " +
           "LEFT JOIN FETCH s.parent par " +
           "LEFT JOIN FETCH i.enrollment e " +
           "LEFT JOIN FETCH e.classEntity c " +
           "LEFT JOIN FETCH c.course co " +
           "LEFT JOIN FETCH c.branch b " +
           "LEFT JOIN FETCH p.cashier u " +
           "WHERE (:startDate IS NULL OR p.paymentDate >= :startDate) " +
           "AND (:endDate IS NULL OR p.paymentDate <= :endDate) " +
           "AND (:cashierUsername IS NULL OR :cashierUsername = '' OR u.username = :cashierUsername) " +
           "ORDER BY p.paymentDate DESC")
    List<Payment> findPaymentsWithFilters(
            @org.springframework.data.repository.query.Param("startDate") LocalDateTime startDate,
            @org.springframework.data.repository.query.Param("endDate") LocalDateTime endDate,
            @org.springframework.data.repository.query.Param("cashierUsername") String cashierUsername
    );
}

