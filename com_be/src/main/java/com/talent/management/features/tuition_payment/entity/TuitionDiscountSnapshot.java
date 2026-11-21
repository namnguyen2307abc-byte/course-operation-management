package com.talent.management.features.tuition_payment.entity;

import jakarta.persistence.*;

import lombok.*;

import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Immutable
@Entity
@Table(name = "tuition_discount_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class TuitionDiscountSnapshot {
    @Id
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(nullable = false, length = 30)
    private String source;

    private Long placementTestId;

    @Column(precision = 5, scale = 2)
    private BigDecimal placementScore;

    @Column(nullable = false)
    private int percentage;

    @Column(nullable = false, length = 30)
    private String method;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, length = 40)
    private String policyVersion;

    @Column(nullable = false)
    private LocalDateTime confirmedAt;
}
