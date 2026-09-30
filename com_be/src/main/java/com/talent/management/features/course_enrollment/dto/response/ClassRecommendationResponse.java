package com.talent.management.features.course_enrollment.dto.response;

import java.util.List;

public record ClassRecommendationResponse(
        Long studentId,
        String studentName,
        String placementStatus,
        boolean recommendationAvailable,
        Double placementScore,
        String recommendedLevel,
        Long recommendedCourseId,
        String recommendedCourseName,
        String teacherNote,
        List<ClassResponse> recommendedClasses,
        List<ClassResponse> openClasses
) {
}
