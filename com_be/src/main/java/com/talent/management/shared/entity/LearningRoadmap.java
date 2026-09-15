package com.talent.management.shared.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_roadmaps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningRoadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_test_id")
    private PlacementTest placementTest;

    @Column(nullable = false, length = 200)
    private String targetGoal; 

    @Column(length = 100)
    private String currentLevel;

    @Column(length = 100)
    private String targetLevel;

    private Integer estimatedDurationMonths;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String milestones; 

    @Column(length = 1000)
    private String notes;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
