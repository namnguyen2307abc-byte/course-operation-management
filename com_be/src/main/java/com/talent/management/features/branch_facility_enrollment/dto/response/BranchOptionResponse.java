package com.talent.management.features.branch_facility_enrollment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchOptionResponse {
    private Long id;
    private String name;
    private String code;
}
