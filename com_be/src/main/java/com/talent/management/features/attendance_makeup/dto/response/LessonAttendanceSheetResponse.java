package com.talent.management.features.attendance_makeup.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonAttendanceSheetResponse {
    private Long lessonId;
    private Long classId;
    private String classCode;
    private String className;
    private String courseName;
    private LocalDate lessonDate;
    private String timeRange;
    private String roomName;
    private String branchName;
    private Long teacherId;
    private String teacherName;
    private Integer sessionNumber;
    private boolean canEdit;
    private String lockReason;
    private boolean isAlreadySaved;
    private int totalStudents;
    private int presentCount;
    private int absentCount;
    private List<StudentAttendanceItemDto> students;
}
