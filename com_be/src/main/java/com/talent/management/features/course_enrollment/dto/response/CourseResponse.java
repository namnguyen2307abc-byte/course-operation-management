package com.talent.management.features.course_enrollment.dto.response;

import java.math.BigDecimal;

public record CourseResponse(
        Long id,
        String programName,
        String code,
        String name,
        String level,
        Integer durationWeeks,
        Integer totalSessions,
        BigDecimal tuitionFee,
        String description
) {
}
