package com.talent.management.features.branch_facility_enrollment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {
    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private String email;
    private Boolean active;
}
