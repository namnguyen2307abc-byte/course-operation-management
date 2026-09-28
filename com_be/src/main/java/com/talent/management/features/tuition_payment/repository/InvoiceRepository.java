package com.talent.management.features.tuition_payment.repository;

import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceCode(String invoiceCode);

    List<Invoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);

    List<Invoice> findAllByOrderByCreatedAtDesc();

    long countByStatus(InvoiceStatus status);

    @Query("SELECT i FROM Invoice i " +
           "LEFT JOIN FETCH i.student s " +
           "LEFT JOIN FETCH s.parent p " +
           "LEFT JOIN FETCH i.enrollment e " +
           "LEFT JOIN FETCH e.classEntity c " +
           "LEFT JOIN FETCH c.course co " +
           "LEFT JOIN FETCH c.branch b " +
           "LEFT JOIN FETCH c.room r " +
           "WHERE i.status = :status " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           "     OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR (p.phone IS NOT NULL AND p.phone LIKE CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(i.invoiceCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(c.className) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           ") " +
           "ORDER BY i.createdAt DESC")
    List<Invoice> searchInvoicesByStatusAndKeyword(@Param("status") InvoiceStatus status, @Param("keyword") String keyword);

    @Query("SELECT i FROM Invoice i " +
           "LEFT JOIN FETCH i.student s " +
           "LEFT JOIN FETCH s.parent p " +
           "LEFT JOIN FETCH i.enrollment e " +
           "LEFT JOIN FETCH e.classEntity c " +
           "LEFT JOIN FETCH c.course co " +
           "LEFT JOIN FETCH c.branch b " +
           "LEFT JOIN FETCH c.room r " +
           "WHERE i.id = :id")
    Optional<Invoice> findByIdWithDetails(@Param("id") Long id);
}
