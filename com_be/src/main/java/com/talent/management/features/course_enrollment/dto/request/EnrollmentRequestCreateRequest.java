package com.talent.management.features.course_enrollment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnrollmentRequestCreateRequest(
        @NotNull(message = "Vui lòng chọn học viên") Long childId,
        @NotNull(message = "Vui lòng chọn lớp học") Long classId,
        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự") String note
) {
}
