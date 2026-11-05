package com.talent.management.features.attendance_tracking.dto.response;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrolledClassOptionResponse {
    private Long classId;
    private String classCode;
    private String className;
    private Long courseId;
    private String courseName;
    private String courseLevel;
    private Integer totalSessions;
    private LocalDate startDate;
    private LocalDate endDate;
    private String teacherName;
    private String roomName;
    private String branchName;
}
