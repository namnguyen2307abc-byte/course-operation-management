package com.talent.management.features.attendance_makeup.dto.response;

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
public class MakeupRegistrationResponse {
    private Long id;
    private Long absenceRequestId;
    private String absenceReason;

    // Học viên
    private Long studentId;
    private String studentName;
    private String parentName;

    // Buổi học gốc đã nghỉ
    private Long originalLessonId;
    private Integer originalSessionNumber;
    private LocalDate originalLessonDate;
    private LocalTime originalStartTime;
    private LocalTime originalEndTime;
    private String originalTimeSlot;
    private String originalClassName;

    // Buổi học bù (target) được xếp
    private Long targetLessonId;
    private Integer targetSessionNumber;
    private LocalDate targetLessonDate;
    private LocalTime targetStartTime;
    private LocalTime targetEndTime;
    private String targetTimeSlot;
    private String targetClassName;
    private String targetRoomName;
    private String targetTeacherName;

    // Trạng thái & ghi chú
    private MakeupStatus status;
    private String note;
    private LocalDateTime createdAt;
}
