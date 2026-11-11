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

    private PlacementLevel level;

    @Min(value = 1, message = "Số tuần học phải lớn hơn 0")
    private Integer durationWeeks;

    @Min(value = 1, message = "Tổng số buổi học phải lớn hơn 0")
    private Integer totalSessions;

    @NotNull(message = "Học phí không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Học phí phải lớn hơn 0")
    private BigDecimal tuitionFee;

    @Size(max = 1000, message = "Mô tả không vượt quá 1000 ký tự")
    private String description;
}
