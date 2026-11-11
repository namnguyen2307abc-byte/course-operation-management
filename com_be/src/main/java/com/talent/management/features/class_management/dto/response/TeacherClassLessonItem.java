package com.talent.management.features.class_management.dto.response;

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
public class TeacherClassLessonItem {
    private Long lessonId;
    private Integer sessionNumber;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String timeRange;
    private String status;
    private String roomName;
    private boolean isPast;
    private boolean isToday;
    private boolean canTakeAttendance;
    private boolean attendanceCompleted;
    private int presentCount;
    private int absentCount;
    private int totalStudents;
}
