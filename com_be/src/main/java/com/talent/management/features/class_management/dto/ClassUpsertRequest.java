package com.talent.management.features.class_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ClassUpsertRequest(
        @NotNull Long courseId,
        @NotNull Long branchId,
        @NotBlank @Size(max = 50) String classCode,
        @NotBlank @Size(max = 150) String className,
        @NotNull @Positive Integer maxStudents,
        @NotBlank String status,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
