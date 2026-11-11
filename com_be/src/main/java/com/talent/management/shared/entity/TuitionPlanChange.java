package com.talent.management.shared.entity;

import jakarta.persistence.*;

import lombok.*;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "tuition_plan_changes")
@Getter
@Setter
@NoArgsConstructor
public class TuitionPlanChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TuitionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TuitionInstallment installment;

    @Column(nullable = false)
    private long expectedRevision;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedPaid;

    @Column(nullable = false)
    private LocalDate oldDate;

    @Column(nullable = false)
    private LocalDate proposedDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 25)
    private String status = "PENDING";

    @Column(length = 500)
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    private User reviewedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;
}
