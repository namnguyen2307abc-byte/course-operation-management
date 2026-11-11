package com.talent.management.features.class_management.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherClassStudentItem {
    private Long studentId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String schoolName;
    private String parentName;
    private String parentPhone;
    private String parentEmail;
    private int attendedLessons;
    private int absentLessons;
}
