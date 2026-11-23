package com.talent.management.features.placement_test.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "placement_schedule_criteria_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementScheduleCriteriaScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private PlacementSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_id")
    private PlacementCriteria criteria;

    @Column(name = "criteria_name_snapshot", nullable = false, columnDefinition = "NVARCHAR(150)")
    private String criteriaNameSnapshot;

    @Column(name = "score_10", nullable = false)
    private Double score;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 1;
}
