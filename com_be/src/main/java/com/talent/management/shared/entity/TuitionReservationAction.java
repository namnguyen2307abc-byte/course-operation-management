package com.talent.management.shared.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tuition_reservation_actions")
@Getter
@Setter
@NoArgsConstructor
public class TuitionReservationAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User actor;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 500)
    private String reason;

    private LocalDateTime oldDeadline;
    private LocalDateTime newDeadline;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
