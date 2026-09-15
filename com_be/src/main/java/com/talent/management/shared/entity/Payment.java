package com.talent.management.shared.entity;

import com.talent.management.shared.enums.PaymentMethod;
import com.talent.management.shared.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, unique = true, length = 50)
    private String paymentCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod; 

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    private LocalDateTime paymentDate = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private User cashier; 

    @Column(length = 100)
    private String bankTransactionId; 

    @Column(length = 1000)
    private String proofImageUrl; 

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.SUCCESS;
}
