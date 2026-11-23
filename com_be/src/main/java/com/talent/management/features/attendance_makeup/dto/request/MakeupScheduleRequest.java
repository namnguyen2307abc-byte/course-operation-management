package com.talent.management.features.attendance_makeup.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @Positive(message = "ID buổi học bù không hợp lệ")
    private Long targetLessonId;

    /**
     * Ghi chú xếp lịch (phòng học, dặn dò học viên)
     */
    @Size(max = 400, message = "Ghi chú tối đa 400 ký tự")
    private String note;
}
