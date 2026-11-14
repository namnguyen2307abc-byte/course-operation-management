package com.talent.management.features.branch_facility_enrollment.mapper;

import com.talent.management.features.branch_facility_enrollment.dto.response.BranchOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.EquipmentResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomResponse;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.entity.Room;
import org.springframework.stereotype.Component;

@Component
public class BranchFacilityMapper {

    public BranchResponse toBranchResponse(Branch branch) {
        if (branch == null) {
            return null;
        }
        return BranchResponse.builder()
                .id(branch.getId())
                .code(branch.getCode())
                .name(branch.getName())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .active(branch.getActive())
                .build();
    }

    public BranchOptionResponse toBranchOptionResponse(Branch branch) {
        if (branch == null) {
            return null;
        }
        return BranchOptionResponse.builder()
                .id(branch.getId())
                .name(branch.getName())
                .code(branch.getCode())
                .build();
    }

    public RoomResponse toRoomResponse(Room room) {
        if (room == null) {
            return null;
        }
        return RoomResponse.builder()
                .id(room.getId())
                .branchId(room.getBranch() != null ? room.getBranch().getId() : null)
                .branchName(room.getBranch() != null ? room.getBranch().getName() : null)
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .capacity(room.getCapacity())
                .roomType(room.getRoomType())
                .status(room.getStatus())
                .description(room.getDescription())
                .build();
    }

    public EquipmentResponse toEquipmentResponse(Equipment equipment) {
        if (equipment == null) {
            return null;
        }

        Room room = equipment.getRoom();
        Long roomId = (room != null) ? room.getId() : null;
        String roomName = (room != null) ? room.getRoomName() : null;
        String roomCode = (room != null) ? room.getRoomCode() : null;
        String branchName = (room != null && room.getBranch() != null) ? room.getBranch().getName() : null;

        return EquipmentResponse.builder()
                .id(equipment.getId())
                .roomId(roomId)
                .roomName(roomName)
                .roomCode(roomCode)
                .branchName(branchName)
                .name(equipment.getName())
                .code(equipment.getCode())
                .category(equipment.getCategory())
                .status(equipment.getStatus())
                .serialNumber(equipment.getSerialNumber())
                .description(equipment.getDescription())
                .build();
    }

}
