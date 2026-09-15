package com.talent.management.shared.entity;

import com.talent.management.shared.enums.DiscountType;
import com.talent.management.shared.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String invoiceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id")
    private Enrollment enrollment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalAmount; 

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DiscountType discountType = DiscountType.NONE; 

    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(length = 255)
    private String discountReason; 

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount; 

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    private LocalDate dueDate;

    @Column(length = 500)
    private String notes;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
