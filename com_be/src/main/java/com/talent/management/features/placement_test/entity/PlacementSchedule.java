package com.talent.management.features.placement_test.entity;

import com.talent.management.shared.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "placement_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User parent;

    @Column(name = "student_name", columnDefinition = "NVARCHAR(100)")
    private String studentName;

    @Column(columnDefinition = "NVARCHAR(150)", nullable = false)
    private String title;

    @Column(name = "room_name", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String roomName;

    @Column(columnDefinition = "NVARCHAR(150)")
    private String branch;

    @Column(name = "test_date", nullable = false)
    private LocalDateTime testDate;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PlacementScheduleStatus status = PlacementScheduleStatus.SCHEDULED;

    @Column(name = "score")
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_level", length = 30)
    private PlacementLevel recommendedLevel;

    @Column(name = "teacher_note", columnDefinition = "NVARCHAR(MAX)")
    private String teacherNote;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "record_url", length = 500)
    private String recordUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User evaluatedBy;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
