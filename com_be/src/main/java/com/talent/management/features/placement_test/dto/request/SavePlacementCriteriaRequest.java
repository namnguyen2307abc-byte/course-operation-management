package com.talent.management.features.placement_test.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavePlacementCriteriaRequest {

    @NotBlank(message = "Bộ môn (subject) không được để trống")
    @Size(max = 30, message = "Mã bộ môn không vượt quá 30 ký tự")
    private String subject;

    @NotBlank(message = "Tên tiêu chí không được để trống")
    @Size(max = 150, message = "Tên tiêu chí không vượt quá 150 ký tự")
    private String criteriaName;

    @Min(value = 1, message = "Thứ tự hiển thị phải từ 1 trở lên")
    private Integer displayOrder;

    private Boolean active;
}
