package com.talent.management.shared.entity;

import com.talent.management.shared.enums.MakeupStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "makeup_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MakeupRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "absence_request_id")
    private AbsenceRequest absenceRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_lesson_id", nullable = false)
    private Lesson originalLesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_lesson_id", nullable = true)
    private Lesson targetLesson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private MakeupStatus status = MakeupStatus.PENDING;

    @Column(length = 500)
    private String note;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
