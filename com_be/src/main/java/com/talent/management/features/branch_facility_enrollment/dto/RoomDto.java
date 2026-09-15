package com.talent.management.features.branch_facility_enrollment.dto;

import com.talent.management.shared.enums.RoomStatus;
import com.talent.management.shared.enums.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDto {
    private Long id;
    private Long branchId;
    private String branchName;
    private String roomCode;
    private String roomName;
    private Integer capacity;
    private RoomType roomType;
    private RoomStatus status;
    private String description;
}
