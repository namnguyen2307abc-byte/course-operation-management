package com.talent.management.features.attendance_makeup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO báo chưa có lớp học bù phù hợp:
 *  - PATCH /makeup-requests/{id}/no-slot → hủy ca bù và báo phụ huynh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MakeupResolveRequest {

    /** Nội dung thông báo gửi tới phụ huynh (bắt buộc) */
    @NotBlank(message = "Vui lòng nhập nội dung thông báo gửi phụ huynh")
    @Size(max = 400, message = "Nội dung tối đa 400 ký tự")
    private String note;
}
