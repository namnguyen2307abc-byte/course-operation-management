package com.talent.management.features.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class StudentDto {
    private Long id;
    private Long parentId;
    private String parentName;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String schoolName;
    private String notes;
}
