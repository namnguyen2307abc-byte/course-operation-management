package com.talent.management.features.branch_facility_enrollment.dto.request;

import com.talent.management.shared.enums.RoomStatus;
import com.talent.management.shared.enums.RoomType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRoomRequest(
        @NotBlank @Size(max = 150) String roomName,
        @NotNull @Min(1) Integer capacity,
        @NotNull RoomType roomType,
        @NotNull RoomStatus status,
        @Size(max = 500) String description
) {}
