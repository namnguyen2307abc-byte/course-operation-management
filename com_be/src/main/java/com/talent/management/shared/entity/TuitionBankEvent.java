package com.talent.management.shared.entity;

import com.talent.management.features.tuition_payment.enums.BankTransferReason;

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

    @Column(length = 500)
    private String transferContent;

    @Column(length = 150)
    private String senderName;

    @Column(length = 100)
    private String senderAccount;

    @Column(length = 100)
    private String senderBank;

    private LocalDateTime transferredAt;

    @Column(length = 30)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private BankTransferReason reason;

    @ManyToOne(fetch = FetchType.LAZY)
    private Invoice resolvedInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    private User recordedBy;

    @Column(precision = 12, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal refundedAmount;

    @Column(length = 120)
    private String refundReference;

    @Column(length = 500)
    private String resolutionNote;

    private Long resolvedInstallmentId;

    @Column(precision = 12, scale = 2)
    private BigDecimal expectedAmount;
}
