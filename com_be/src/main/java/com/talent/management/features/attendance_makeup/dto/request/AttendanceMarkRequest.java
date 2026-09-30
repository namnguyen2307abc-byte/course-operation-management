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
public class AttendanceMarkRequest {

    /**
     * ID buổi học
     */
    @NotNull(message = "Vui lòng chỉ định buổi học")
    private Long lessonId;

    /**
     * ID học viên
     */
    @NotNull(message = "Vui lòng chỉ định học viên")
    private Long studentId;

    /**
     * Trạng thái điểm danh: PRESENT | ABSENT_EXCUSED | ABSENT_UNEXCUSED | LATE
     */
    private String status;

    /**
     * Ghi chú điểm danh
     */
    private String note;
}
