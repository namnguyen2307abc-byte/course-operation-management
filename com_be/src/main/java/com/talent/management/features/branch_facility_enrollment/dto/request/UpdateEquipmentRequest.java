package com.talent.management.features.branch_facility_enrollment.dto.request;

import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.shared.enums.EquipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateEquipmentRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull EquipmentCategory category,
        @NotNull EquipmentStatus status,
        @Size(max = 100) String serialNumber,
        @Size(max = 500) String description
) {}
