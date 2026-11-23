package com.talent.management.features.attendance_makeup.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentOptionResponse {
    private Long id;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String schoolName;
    private Long parentId;
    private String parentName;
    private String parentPhone;
    private String currentClassName;
    private Integer makeupCount;
    private Integer makeupLimit;
    private Boolean makeupLimitReached;
}
