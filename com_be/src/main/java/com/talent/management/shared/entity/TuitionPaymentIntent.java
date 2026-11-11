package com.talent.management.shared.entity;

import jakarta.persistence.*;

import lombok.*;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "tuition_payment_intents")
@Getter
@Setter
@NoArgsConstructor
public class TuitionPaymentIntent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Invoice invoice;

    @Column(nullable = false, unique = true)
    private long orderCode;

    @Column(nullable = false)
    private long planRevision;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User initiatedBy;

    @Column(nullable = false, length = 25)
    private String status = "CREATING";

    @Column(length = 2000)
    private String checkoutUrl;

    @Column(length = 3000)
    private String qrCode;

    private boolean simulated;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
