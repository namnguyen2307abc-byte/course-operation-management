package com.talent.management.features.class_management.dto;

import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.ClassType;

import java.time.LocalDate;

public record ManagedClassResponse(
        Long id,
        Long courseId,
        String courseCode,
        String courseName,
        Long branchId,
        String branchName,
        String classCode,
        String className,
        ClassType classType,
        Integer maxStudents,
        Integer currentStudents,
        Long pendingPaymentStudents,
        Long availableSeats,
        ClassStatus status,
        LocalDate startDate,
        LocalDate endDate
) {
}
