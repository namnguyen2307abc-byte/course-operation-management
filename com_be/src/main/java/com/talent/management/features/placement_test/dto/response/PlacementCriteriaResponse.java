package com.talent.management.features.placement_test.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementCriteriaResponse {

    private Long id;
    private String subject;
    private String criteriaName;
    private Integer displayOrder;
    private Boolean active;
    private Boolean usedInAssessments;
}
