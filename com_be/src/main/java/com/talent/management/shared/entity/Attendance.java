package com.talent.management.shared.entity;

import com.talent.management.shared.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendances", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"lesson_id", "student_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Column(length = 500)
    private String note;

    @Builder.Default
    private LocalDateTime markedAt = LocalDateTime.now();
}
