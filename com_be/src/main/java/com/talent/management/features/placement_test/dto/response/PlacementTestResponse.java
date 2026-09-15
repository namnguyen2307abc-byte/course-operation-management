package com.talent.management.features.placement_test.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementTestResponse {
    private Long id;
    private Long studentId;
    private Long teacherId;
    private String notes;
}
