package com.talent.management.features.attendance_makeup.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO xử lý ca bù không tìm được lớp phù hợp:
 *  - PATCH /makeup-requests/{id}/no-slot  → báo phụ huynh chưa có lớp bù (NO_SLOT)
 *  - PATCH /makeup-requests/{id}/defer    → chuyển sang khóa học kỳ sau (DEFERRED)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MakeupResolveRequest {

    /** Nội dung thông báo gửi tới phụ huynh (bắt buộc) */
    private String note;
}
