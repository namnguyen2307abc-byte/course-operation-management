package com.talent.management.features.course_enrollment.dto.response;

import com.talent.management.features.course_enrollment.enums.EnrollmentRequestStatus;

import java.time.LocalDateTime;

public record EnrollmentRequestResponse(
        Long id,
        Long childId,
        String childName,
        Long classId,
        String classCode,
        String className,
        String courseName,
        EnrollmentRequestStatus status,
        String note,
        String reviewNote,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        Long enrollmentId,
        String requestedBy,
        String reviewedBy
) {
}
