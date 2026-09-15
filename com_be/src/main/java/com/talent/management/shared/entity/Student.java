package com.talent.management.shared.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private User parent;

    @Column(nullable = false, length = 150)
    private String fullName;

    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(length = 200)
    private String schoolName;

    @Column(length = 500)
    private String notes;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
