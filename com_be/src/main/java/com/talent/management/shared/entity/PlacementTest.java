package com.talent.management.shared.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "placement_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program; 

    @Column(nullable = false)
    private LocalDate testDate;

    
    private Double rhythmScore;       
    private Double techniqueScore;    
    private Double earTrainingScore;  
    private Double expressionScore;   
    private Double totalScore;        

    @Column(length = 2000)
    private String teacherNotes;      

    @Column(length = 100)
    private String recommendedLevel;  

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_course_id")
    private Course recommendedCourse;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
