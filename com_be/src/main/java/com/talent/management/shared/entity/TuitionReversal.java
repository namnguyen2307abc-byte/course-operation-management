package com.talent.management.shared.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tuition_reversals")
@Getter
@Setter
@NoArgsConstructor
public class TuitionReversal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true)
    private Payment payment;

    @Column(nullable = false, length = 25)
    private String status = "PENDING";

    @Column(nullable = false, length = 500)
    private String reason;

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
