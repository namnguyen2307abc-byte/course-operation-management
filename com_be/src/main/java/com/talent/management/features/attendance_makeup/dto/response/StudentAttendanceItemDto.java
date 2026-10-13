package com.talent.management.features.attendance_makeup.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAttendanceItemDto {
    private int orderNumber;
    private Long studentId;
    private String studentCode;
    private String fullName;
    private String avatarUrl;
    private String status; // PRESENT, ABSENT_UNEXCUSED, ABSENT_EXCUSED, NOT_MARKED
    private String note;
    private boolean hasApprovedAbsence;
    private String absenceReason;
}
