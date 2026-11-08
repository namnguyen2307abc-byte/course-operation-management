package com.talent.management.features.attendance_tracking.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAttendanceTrackingResponse {
    private Long studentId;
    private String studentName;
    private String studentCode;
    private Long classId;
    private String classCode;
    private String className;
    private Long courseId;
    private String courseName;
    private Integer totalSessions;
    private int scheduledLessonsCount;
    private int attendedCount;
    private int absentCount;
    private int unmarkedCount;
    private double absentRate;
    private int maxAllowedAbsences;
    private int remainingAllowedAbsences;
    private int makeupCompletedCount;
    private int makeupMissedCount;
    private String warningLevel;
    private String warningTitle;
    private String summaryAlert;
    private List<StudentLessonAttendanceItemResponse> lessons;
}
