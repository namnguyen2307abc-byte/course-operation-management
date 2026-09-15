package com.talent.management.shared.entity;

import com.talent.management.shared.enums.AbsenceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "absence_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbsenceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedByUser;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AbsenceStatus status = AbsenceStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_teacher_id")
    private User approvedByTeacher;

    @Column(length = 500)
    private String reviewNote;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
