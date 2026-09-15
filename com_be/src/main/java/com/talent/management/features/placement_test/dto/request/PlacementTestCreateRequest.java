package com.talent.management.features.placement_test.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementTestCreateRequest {
    private Long studentId;
    private Long teacherId;
    private String notes;
}
