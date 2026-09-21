package com.talent.management.features.course_enrollment.dto.request;

import com.talent.management.features.course_enrollment.enums.EnrollmentDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnrollmentDecisionRequest(
        @NotNull(message = "Vui lòng chọn quyết định") EnrollmentDecision decision,
        @Size(max = 500, message = "Ghi chú duyệt không được vượt quá 500 ký tự") String reviewNote
) {
}
