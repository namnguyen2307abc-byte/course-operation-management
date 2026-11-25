package com.talent.management.features.course_management.dto.request;

import com.talent.management.features.placement_test.entity.PlacementLevel;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCourseRequest {

    @NotNull(message = "Bộ môn (program) không được để trống")
    private Long programId;

    @NotBlank(message = "Mã khóa học không được để trống")
    @Size(max = 50, message = "Mã khóa học không vượt quá 50 ký tự")
    private String code;

    @NotBlank(message = "Tên khóa học không được để trống")
    @Size(max = 150, message = "Tên khóa học không vượt quá 150 ký tự")
    private String name;

    @NotNull(message = "Trình độ (level) không được để trống")
    private PlacementLevel level;

    private Integer durationWeeks;

    private BigDecimal totalSessions;

    // Kept for old JSON clients. Total tuition is always calculated by the server.
    private BigDecimal tuitionFee;

    @NotNull(message = "Đơn giá mỗi buổi không được để trống")
    private BigDecimal tuitionPerSession;

    @Size(max = 1000, message = "Mô tả không vượt quá 1000 ký tự")
    private String description;
}
