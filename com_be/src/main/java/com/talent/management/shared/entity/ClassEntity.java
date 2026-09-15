package com.talent.management.shared.entity;

import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.ClassType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "classes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @Column(nullable = false, unique = true, length = 50)
    private String classCode;

    @Column(nullable = false, length = 150)
    private String className;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ClassType classType = ClassType.GROUP;

    @Column(nullable = false)
    private Integer maxStudents;

    @Builder.Default
    private Integer currentStudents = 0;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(length = 200)
    private String scheduleDescription; 

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ClassStatus status = ClassStatus.OPEN;
}
