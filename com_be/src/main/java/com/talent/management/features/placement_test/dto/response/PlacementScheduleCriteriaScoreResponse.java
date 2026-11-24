package com.talent.management.features.placement_test.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementScheduleCriteriaScoreResponse {

    private Long criteriaId;
    private String criteriaName;
    private Double score;
    private Integer displayOrder;
}
