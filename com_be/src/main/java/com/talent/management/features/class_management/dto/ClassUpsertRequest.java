package com.talent.management.features.class_management.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ClassUpsertRequest(
        @NotNull Long courseId,
        @NotNull Long branchId,
        Long teacherId,
        Long roomId,
        @NotBlank @Size(max = 50) String classCode,
        @NotBlank @Size(max = 150) String className,
        @NotNull @Positive Integer maxStudents,
        @NotBlank String status,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = 200) String scheduleDescription,
        @Size(max = 7) List<Integer> daysOfWeek,
        @JsonFormat(pattern = "HH:mm[:ss]") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm[:ss]") LocalTime endTime,
        @Min(1) @Max(60) Integer totalSessions,
        boolean clearTeacher,
        boolean clearRoom
) {
    public ClassUpsertRequest(
            Long courseId,
            Long branchId,
            Long teacherId,
            Long roomId,
            String classCode,
            String className,
            Integer maxStudents,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String scheduleDescription,
            List<Integer> daysOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Integer totalSessions
    ) {
        this(courseId, branchId, teacherId, roomId, classCode, className, maxStudents, status,
                startDate, endDate, scheduleDescription, daysOfWeek, startTime, endTime, totalSessions, false, false);
    }

    public ClassUpsertRequest(
            Long courseId,
            Long branchId,
            String classCode,
            String className,
            Integer maxStudents,
            String status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this(courseId, branchId, null, null, classCode, className, maxStudents, status, startDate, endDate, null, null, null, null, null);
    }
}


