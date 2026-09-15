package com.talent.management.features.branch_facility_enrollment.dto.request;

import com.talent.management.shared.enums.RoomStatus;
import com.talent.management.shared.enums.RoomType;
import jakarta.validation.constraints.Min;
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
public class CreateRoomRequest {

    @NotNull(message = "Cơ sở không được để trống")
    private Long branchId;

    @NotBlank(message = "Mã phòng không được để trống")
    @Size(max = 20, message = "Mã phòng tối đa 20 ký tự")
    private String roomCode;

    @NotBlank(message = "Tên phòng không được để trống")
    @Size(max = 100, message = "Tên phòng tối đa 100 ký tự")
    private String roomName;

    @NotNull(message = "Sức chứa không được để trống")
    @Min(value = 1, message = "Sức chứa phòng học phải ít nhất là 1")
    private Integer capacity;

    private RoomType roomType;

    @Builder.Default
    private RoomStatus status = RoomStatus.AVAILABLE;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;
}
