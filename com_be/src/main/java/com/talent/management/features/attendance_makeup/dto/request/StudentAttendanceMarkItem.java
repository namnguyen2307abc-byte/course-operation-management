package com.talent.management.features.attendance_makeup.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAttendanceMarkItem {

    @NotNull(message = "studentId không được để trống")
    private Long studentId;

    @NotNull(message = "Trạng thái điểm danh không được để trống")
    private String status; // PRESENT, ABSENT_UNEXCUSED, ABSENT_EXCUSED, LATE

    private String note;
}
