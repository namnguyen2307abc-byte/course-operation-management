package com.talent.management.features.course_enrollment.dto.response;

import java.time.LocalDate;

public record ChildResponse(
        Long id,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String schoolName
) {
}
