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
public class MakeupScheduleRequest {

    /**
     * ID buổi học bù được xếp
     */
    @NotNull(message = "Vui lòng chọn buổi học bù")
    private Long targetLessonId;

    /**
     * Ghi chú xếp lịch (phòng học, dặn dò học viên)
     */
    private String note;
}
