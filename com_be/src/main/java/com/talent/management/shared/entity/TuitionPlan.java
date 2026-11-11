package com.talent.management.shared.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.*;

@Entity
@Table(name = "tuition_plans")
@Getter
@Setter
@NoArgsConstructor
public class TuitionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true)
    private Invoice invoice;

    @Column(nullable = false)
    private long revision = 1;

    @Column(nullable = false)
    private String mode = "FULL";

    @Column(nullable = false)
    private boolean reviewRequired;

    @Column(length = 500)
    private String reviewReason;

    @ManyToOne(fetch = FetchType.LAZY)
    private User createdBy;
}
