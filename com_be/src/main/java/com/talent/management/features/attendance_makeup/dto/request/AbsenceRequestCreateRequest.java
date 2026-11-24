package com.talent.management.features.attendance_makeup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO tạo đơn xin nghỉ học. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceRequestCreateRequest {

    /** ID học viên (bắt buộc) */
    @NotNull(message = "Vui lòng chọn học viên xin nghỉ")
    @Positive(message = "ID học viên không hợp lệ")
    private Long studentId;

    /** ID buổi học xin nghỉ (bắt buộc) */
    @NotNull(message = "Vui lòng chọn buổi học muốn nghỉ")
    @Positive(message = "ID buổi học không hợp lệ")
    private Long lessonId;

    /** Lý do xin nghỉ học (bắt buộc) */
    @NotBlank(message = "Vui lòng nhập lý do xin nghỉ học")
    @Size(max = 1000, message = "Lý do tối đa 1000 ký tự")
    private String reason;

    /** true hoặc bỏ trống: nghỉ kèm học bù · false: chỉ xin nghỉ */
    private Boolean needMakeup;

    public AbsenceRequestCreateRequest(Long studentId, Long lessonId, String reason) {
        this(studentId, lessonId, reason, null);
    }
}
