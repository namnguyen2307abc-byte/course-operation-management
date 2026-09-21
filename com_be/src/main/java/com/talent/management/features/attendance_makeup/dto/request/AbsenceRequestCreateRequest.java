package com.talent.management.features.attendance_makeup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceRequestCreateRequest {

    /**
     * ID học viên (bắt buộc chọn)
     */
    @NotNull(message = "Vui lòng chọn học viên xin nghỉ")
    private Long studentId;

    /**
     * ID buổi học xin nghỉ (bắt buộc chọn)
     */
    @NotNull(message = "Vui lòng chọn buổi học muốn nghỉ")
    private Long lessonId;

    /**
     * Lý do xin nghỉ học (bắt buộc nhập)
     */
    @NotBlank(message = "Vui lòng nhập lý do xin nghỉ học")
    private String reason;

    private Long enrollmentId;
}
