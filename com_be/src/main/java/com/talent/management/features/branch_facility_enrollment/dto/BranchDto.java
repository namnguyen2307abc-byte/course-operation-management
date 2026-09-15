package com.talent.management.features.branch_facility_enrollment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchDto {
    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private String email;
    private Boolean active;
}
