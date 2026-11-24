package com.talent.management.features.placement_test.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementCriteriaScoreItemRequest {

    private Long criteriaId;

    private String criteriaName;

    @NotNull(message = "Điểm tiêu chí không được để trống")
    @DecimalMin(value = "0.0", message = "Điểm tiêu chí không được nhỏ hơn 0")
    @DecimalMax(value = "10.0", message = "Điểm tiêu chí không được vượt quá 10")
    private Double score;
}
