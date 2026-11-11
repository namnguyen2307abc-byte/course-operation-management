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
public class TeacherClassSummaryResponse {
    private Long classId;
    private String classCode;
    private String className;
    private Long courseId;
    private String courseName;
    private String courseSubject;
    private Long branchId;
    private String branchName;
    private Long roomId;
    private String roomName;
    private String scheduleDescription;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer currentStudents;
    private Integer maxStudents;
    private Integer totalLessons;
    private Integer completedLessons;
    private Integer progressPercent;
    private LocalDate nextLessonDate;
    private LocalTime nextLessonStartTime;
    private LocalTime nextLessonEndTime;
    private String teacherName;
}
