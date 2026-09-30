package com.talent.management.features.placement_test.dto.response;

import java.time.LocalDateTime;

public record PlacementRecommendationResponse(
        Long studentId,
        String placementStatus,
        boolean recommendationAvailable,
        Double score,
        String recommendedLevel,
        Long recommendedCourseId,
        String recommendedCourseName,
        String teacherNote,
        LocalDateTime evaluatedAt
) {
    public static PlacementRecommendationResponse notAvailable(Long studentId) {
        return new PlacementRecommendationResponse(
                studentId,
                "NOT_AVAILABLE",
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
