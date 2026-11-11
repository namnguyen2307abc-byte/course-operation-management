package com.talent.management.shared.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.*;

@Entity
@Table(name = "tuition_notices")
@Getter
@Setter
@NoArgsConstructor
public class TuitionNotice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TuitionInstallment installment;

    @ManyToOne(fetch = FetchType.LAZY)
    private TuitionPaymentIntent paymentIntent;

    @Column(nullable = false, unique = true, length = 180)
    private String eventKey;

    @Column(nullable = false, length = 25)
    private String type;

    @Column(nullable = false)
    private long revision;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
