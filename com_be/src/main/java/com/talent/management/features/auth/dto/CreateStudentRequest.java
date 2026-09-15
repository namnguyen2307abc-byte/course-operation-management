package com.talent.management.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateStudentRequest {
    private Long parentId;

    @NotBlank(message = "Họ tên học viên không được để trống")
    private String fullName;

    private LocalDate dateOfBirth;
    private String gender;
    private String schoolName;
    private String notes;
}
