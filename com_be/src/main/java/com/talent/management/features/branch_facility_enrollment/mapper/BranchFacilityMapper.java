package com.talent.management.features.branch_facility_enrollment.mapper;

import com.talent.management.features.branch_facility_enrollment.dto.request.CreateBranchRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.EquipmentResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomOptionResponse;
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

    public RoomOptionResponse toRoomOptionResponse(Room room) {
        if (room == null) {
            return null;
        }
        return RoomOptionResponse.builder()
                .id(room.getId())
                .roomName(room.getRoomName())
                .roomCode(room.getRoomCode())
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

    public Branch toBranch(CreateBranchRequest request) {
        if (request == null) {
            return null;
        }
        return Branch.builder()
                .code(request.getCode())
                .name(request.getName())
                .address(request.getAddress())
                .phone(request.getPhone())
                .email(request.getEmail())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
    }

    public Room toRoom(CreateRoomRequest request, Branch branch) {
        if (request == null) {
            return null;
        }
        return Room.builder()
                .branch(branch)
                .roomCode(request.getRoomCode())
                .roomName(request.getRoomName())
                .capacity(request.getCapacity())
                .roomType(request.getRoomType())
                .status(request.getStatus())
                .description(request.getDescription())
                .build();
    }

    public Equipment toEquipment(CreateEquipmentRequest request, Room room) {
        if (request == null) {
            return null;
        }
        return Equipment.builder()
                .room(room)
                .name(request.getName())
                .code(request.getCode())
                .category(request.getCategory())
                .status(request.getStatus())
                .serialNumber(request.getSerialNumber())
                .description(request.getDescription())
                .build();
    }
}
