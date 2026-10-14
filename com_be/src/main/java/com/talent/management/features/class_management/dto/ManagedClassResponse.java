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
        Long roomId,
        String roomName,
        Long teacherId,
        String teacherName,
        String scheduleDescription,
        Integer totalLessons,
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
    public ManagedClassResponse(
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
        this(id, courseId, courseCode, courseName, branchId, branchName, null, null, null, null, null, 0,
                classCode, className, classType, maxStudents, currentStudents, pendingPaymentStudents, availableSeats,
                status, startDate, endDate);
    }
}

