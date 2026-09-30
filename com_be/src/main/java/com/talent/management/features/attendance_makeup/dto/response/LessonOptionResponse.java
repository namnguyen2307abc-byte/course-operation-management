package com.talent.management.features.attendance_makeup.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonOptionResponse {
    private Long id;
    private Integer sessionNumber;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String timeSlot;
    private String title;
    private Long classId;
    private String className;
    private String courseName;
    private String roomName;
    private String teacherName;
}
