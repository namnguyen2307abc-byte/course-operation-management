package com.talent.management.features.class_management.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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
        String scheduleDescription,
        List<Integer> daysOfWeek,
        @JsonFormat(pattern = "HH:mm[:ss]") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm[:ss]") LocalTime endTime,
        Integer totalSessions
) {
    @JsonCreator
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
        this.courseId = courseId;
        this.branchId = branchId;
        this.teacherId = teacherId;
        this.roomId = roomId;
        this.classCode = classCode;
        this.className = className;
        this.maxStudents = maxStudents;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.scheduleDescription = scheduleDescription;
        this.daysOfWeek = daysOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalSessions = totalSessions;
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


