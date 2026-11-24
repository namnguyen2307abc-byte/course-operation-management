package com.talent.management.features.attendance_makeup.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO hủy ca học bù — endpoint PATCH /makeup-requests/{id}/cancel
 * Nhân viên/Admin cung cấp lý do hủy để lưu vết.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MakeupCancelRequest {

    /** Lý do hủy ca học bù (tùy chọn) */
    @Size(max = 400, message = "Lý do tối đa 400 ký tự")
    private String reason;
}
