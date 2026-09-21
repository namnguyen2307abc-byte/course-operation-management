package com.talent.management.features.attendance_makeup.dto.response;

import com.talent.management.shared.enums.AbsenceStatus;
import com.talent.management.shared.enums.MakeupStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceRequestResponse {
    private Long id;
    
    // Thông tin học viên
    private Long studentId;
    private String studentName;
    private Long parentId;
    private String parentName;
    private String parentPhone;

    // Thông tin buổi học xin nghỉ
    private Long lessonId;
    private Integer sessionNumber;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String timeSlot;
    private String lessonTitle;
    private String className;
    private String courseName;
    private String roomName;
    private String teacherName;

    // Chi tiết đơn xin nghỉ
    private String reason;
    private AbsenceStatus status;
    private String reviewNote;
    private String approvedByTeacherName;
    private LocalDateTime createdAt;

    // Thông tin học bù liên quan (nếu có)
    private Long makeupRegistrationId;
    private MakeupStatus makeupStatus;
    private String makeupTargetDate;
}
