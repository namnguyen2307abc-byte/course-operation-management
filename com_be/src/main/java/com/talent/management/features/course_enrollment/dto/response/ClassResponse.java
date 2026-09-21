package com.talent.management.features.course_enrollment.dto.response;

import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.ClassType;

import java.time.LocalDate;

public record ClassResponse(
        Long id,
        Long courseId,
        String courseName,
        String classCode,
        String className,
        ClassType classType,
        Long branchId,
        String branchName,
        Long roomId,
        String roomName,
        String teacherName,
        Integer maxStudents,
        Integer currentStudents,
        Integer availableSeats,
        LocalDate startDate,
        LocalDate endDate,
        String scheduleDescription,
        ClassStatus status
) {
}
