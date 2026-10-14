package com.talent.management.features.course_management.dto.response;

import java.math.BigDecimal;

public record CourseDetailResponse(
        Long id,
        Long programId,
        String programCode,
        String programName,
        String code,
        String name,
        String level,
        Integer durationWeeks,
        Integer totalSessions,
        BigDecimal tuitionFee,
        String description,
        Long totalClasses,
        Long totalEnrollments
) {
}
