package com.talent.management.features.attendance_makeup.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceReviewRequest {

    /**
     * Hành động xét duyệt: APPROVE | SPECIAL_APPROVE | REJECT
     */
    private String action;

    /**
     * Ghi chú xét duyệt:
     * - Bắt buộc khi SPECIAL_APPROVE (lý do duyệt đặc biệt có tình người)
     * - Bắt buộc khi REJECT (lý do từ chối đơn)
     * - Tùy chọn khi APPROVE
     */
    private String reviewNote;
}
