package com.talent.management.features.attendance_makeup.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO xét duyệt đơn xin nghỉ học của giáo viên / nhân viên / admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceReviewRequest {

    /**
     * Quyết định xét duyệt (bắt buộc).
     * APPROVED  → Đồng ý duyệt đơn nghỉ → tự động tạo ca học bù MakeupRegistration{PENDING}
     * REJECTED  → Từ chối đơn nghỉ
     */
    @NotNull(message = "Vui lòng chọn quyết định xét duyệt (APPROVED / REJECTED)")
    private ReviewDecision decision;

    /**
     * Ghi chú xét duyệt:
     * - Bắt buộc khi REJECTED (lý do từ chối, thông báo tới phụ huynh)
     * - Tùy chọn khi APPROVED
     */
    private String reviewNote;

    public enum ReviewDecision {
        APPROVED, REJECTED
    }
}
