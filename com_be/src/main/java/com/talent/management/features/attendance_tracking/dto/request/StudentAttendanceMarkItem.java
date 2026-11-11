package com.talent.management.features.attendance_tracking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAttendanceMarkItem {

    @NotNull(message = "studentId không được để trống")
    private Long studentId;

    @NotBlank(message = "Trạng thái điểm danh không được để trống")
    @Pattern(regexp = "^(PRESENT|ABSENT)$", message = "Trạng thái điểm danh chỉ được là PRESENT hoặc ABSENT")
    private String status; // PRESENT, ABSENT

    @Size(max = 500, message = "Ghi chú điểm danh không được vượt quá 500 ký tự")
    private String note;
}
