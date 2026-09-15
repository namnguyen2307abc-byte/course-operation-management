package com.talent.management.features.branch_facility_enrollment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBranchRequest {

    @NotBlank(message = "Mã cơ sở không được để trống")
    @Size(max = 20, message = "Mã cơ sở tối đa 20 ký tự")
    private String code;

    @NotBlank(message = "Tên cơ sở không được để trống")
    @Size(max = 150, message = "Tên cơ sở tối đa 150 ký tự")
    private String name;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phone;

    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @Builder.Default
    private Boolean active = true;
}
