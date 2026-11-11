package com.talent.management.features.attendance_makeup.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO báo chưa có lớp học bù phù hợp:
 *  - PATCH /makeup-requests/{id}/no-slot  → báo phụ huynh chưa có lớp bù (NO_SLOT)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MakeupResolveRequest {

    /** Nội dung thông báo gửi tới phụ huynh (bắt buộc) */
    private String note;
}
