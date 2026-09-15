package com.talent.management.shared.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String level; 

    private Integer durationWeeks;

    private Integer totalSessions;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tuitionFee;

    @Column(length = 1000)
    private String description;
}
