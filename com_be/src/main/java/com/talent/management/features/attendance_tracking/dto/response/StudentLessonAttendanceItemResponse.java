package com.talent.management.features.attendance_tracking.dto.response;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLessonAttendanceItemResponse {
    private Long lessonId;
    private Integer sessionNumber;
    private LocalDate lessonDate;
    private String dayOfWeekText;
    private String slot;
    private String roomName;
    private String teacherName;
    private String className;
    private String classCode;
    private String status;
    private String statusLabel;
    private String note;
    private boolean isMakeup;
    private String makeupInfo;
}
