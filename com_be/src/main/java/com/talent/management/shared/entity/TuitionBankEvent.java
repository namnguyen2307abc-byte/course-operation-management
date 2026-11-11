package com.talent.management.shared.entity;

import jakarta.persistence.*;

import lombok.*;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "tuition_bank_events")
@Getter
@Setter
@NoArgsConstructor
public class TuitionBankEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private TuitionPaymentIntent intent;

    private Long gatewayOrderCode;

    @Column(nullable = false, unique = true, length = 120)
    private String reference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal receivedAmount;

    @Column(nullable = false, length = 25)
    private String status;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();
}
