package com.talent.management.features.attendance_makeup.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO xét duyệt đơn xin nghỉ học — gộp 3 action cũ (approve/special-approve/reject)
 * thành 1 endpoint duy nhất /review với enum ReviewDecision.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceReviewRequest {

    /**
     * Quyết định xét duyệt (bắt buộc).
     * APPROVED  → Duyệt, cần học bù → auto tạo MakeupRegistration{PENDING}
     * EXCUSED   → Duyệt, miễn học bù (nghỉ ốm có giấy, lý do đặc biệt) → KHÔNG tạo ca bù
     * REJECTED  → Từ chối
     */
    @NotNull(message = "Vui lòng chọn quyết định xét duyệt (APPROVED / EXCUSED / REJECTED)")
    private ReviewDecision decision;

    /**
     * Ghi chú xét duyệt:
     * - Bắt buộc khi EXCUSED  (lý do miễn học bù)
     * - Bắt buộc khi REJECTED (lý do từ chối, thông báo tới phụ huynh)
     * - Tùy chọn  khi APPROVED
     */
    private String reviewNote;

    public enum ReviewDecision {
        APPROVED, EXCUSED, REJECTED
    }
}
