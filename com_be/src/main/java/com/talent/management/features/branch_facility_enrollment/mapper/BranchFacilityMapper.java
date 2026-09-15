package com.talent.management.features.branch_facility_enrollment.mapper;

import com.talent.management.features.branch_facility_enrollment.dto.BranchDto;
import com.talent.management.features.branch_facility_enrollment.dto.EquipmentDto;
import com.talent.management.features.branch_facility_enrollment.dto.RoomDto;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.entity.Room;
import org.springframework.stereotype.Component;

@Component
public class BranchFacilityMapper {

    public BranchDto toBranchDto(Branch branch) {
        if (branch == null) {
            return null;
        }
        return BranchDto.builder()
                .id(branch.getId())
                .code(branch.getCode())
                .name(branch.getName())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .active(branch.getActive())
                .build();
    }

    public RoomDto toRoomDto(Room room) {
        if (room == null) {
            return null;
        }
        return RoomDto.builder()
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

    public EquipmentDto toEquipmentDto(Equipment equipment) {
        if (equipment == null) {
            return null;
        }

        Room room = equipment.getRoom();
        Long roomId = (room != null) ? room.getId() : null;
        String roomName = (room != null) ? room.getRoomName() : null;
        String roomCode = (room != null) ? room.getRoomCode() : null;
        String branchName = (room != null && room.getBranch() != null) ? room.getBranch().getName() : null;

        return EquipmentDto.builder()
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
