package com.talent.management.shared.entity;

import com.talent.management.shared.enums.EnrollmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_user_id")
    private User registeredByUser;

    @Builder.Default
    private LocalDateTime enrollmentDate = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.PENDING_PAYMENT;

    @Column(length = 500)
    private String notes;
}
