package com.talent.management.shared.entity;

import jakarta.persistence.*;

import lombok.*;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "tuition_allocations")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TuitionInstallment installment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}
