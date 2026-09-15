package com.talent.management.features.branch_facility_enrollment.dto.request;

import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.shared.enums.EquipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEquipmentRequest {

    @NotNull(message = "Phòng học không được để trống")
    private Long roomId;

    @NotBlank(message = "Tên thiết bị không được để trống")
    @Size(max = 150, message = "Tên thiết bị tối đa 150 ký tự")
    private String name;

    @Size(max = 50, message = "Mã thiết bị tối đa 50 ký tự")
    private String code;

    @NotNull(message = "Loại thiết bị không được để trống")
    private EquipmentCategory category;

    @Builder.Default
    private EquipmentStatus status = EquipmentStatus.GOOD;

    @Size(max = 100, message = "Số sê-ri tối đa 100 ký tự")
    private String serialNumber;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;
}
