package com.talent.management.shared.entity;

import jakarta.persistence.*;

import lombok.*;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "tuition_installments")
@Getter
@Setter
@NoArgsConstructor
public class TuitionInstallment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TuitionPlan plan;

    @Column(nullable = false)
    private int sequenceNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountDue;

    @Column(nullable = false)
    private LocalDate dueDate;
}
