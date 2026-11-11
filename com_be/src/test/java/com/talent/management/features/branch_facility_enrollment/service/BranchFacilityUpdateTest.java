package com.talent.management.features.branch_facility_enrollment.service;

import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.mapper.BranchFacilityMapper;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.branch_facility_enrollment.repository.EquipmentRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.branch_facility_enrollment.service.impl.BranchFacilityServiceImpl;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.enums.EquipmentCategory;
import com.talent.management.shared.enums.EquipmentStatus;
import com.talent.management.shared.enums.RoomStatus;
import com.talent.management.shared.enums.RoomType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchFacilityUpdateTest {
    @Mock BranchRepository branchRepository;
    @Mock RoomRepository roomRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock BranchFacilityMapper mapper;
    @InjectMocks BranchFacilityServiceImpl service;

    @Test
    void updatesRoomWithoutChangingCodeOrBranch() {
        Branch branch = Branch.builder().id(1L).build();
        Room room = Room.builder().id(2L).branch(branch).roomCode("R-01")
                .roomName("Phòng cũ").capacity(10).roomType(RoomType.THEORY_ROOM).build();
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(roomRepository.maxAssignedClassSize(2L)).thenReturn(8);
        when(roomRepository.save(room)).thenReturn(room);

        service.updateRoom(2L, new UpdateRoomRequest(" Phòng mới ", 8,
                RoomType.THEORY_ROOM, RoomStatus.MAINTENANCE, "Sửa chữa"));

        assertThat(room.getRoomName()).isEqualTo("Phòng mới");
        assertThat(room.getCapacity()).isEqualTo(8);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.MAINTENANCE);
        assertThat(room.getRoomCode()).isEqualTo("R-01");
        assertThat(room.getBranch()).isSameAs(branch);
    }

    @Test
    void rejectsCapacityBelowAssignedClassSize() {
        Room room = Room.builder().id(2L).roomType(RoomType.THEORY_ROOM).build();
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(roomRepository.maxAssignedClassSize(2L)).thenReturn(10);

        assertThatThrownBy(() -> service.updateRoom(2L,
                new UpdateRoomRequest("Phòng", 8, RoomType.THEORY_ROOM, RoomStatus.AVAILABLE, "")))
                .isInstanceOf(ResponseStatusException.class);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void rejectsChangingTypeOfRoomUsedByClass() {
        Room room = Room.builder().id(2L).roomType(RoomType.THEORY_ROOM).build();
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(roomRepository.hasAssignedClass(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateRoom(2L,
                new UpdateRoomRequest("Phòng", 10, RoomType.CHESS_ROOM, RoomStatus.AVAILABLE, "")))
                .isInstanceOf(ResponseStatusException.class);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void updatesEquipmentWithoutMovingIt() {
        Room room = Room.builder().id(2L).build();
        Equipment equipment = Equipment.builder().id(3L).room(room).code("E-01")
                .name("Thiết bị cũ").category(EquipmentCategory.AUDIO).build();
        when(equipmentRepository.findById(3L)).thenReturn(Optional.of(equipment));
        when(equipmentRepository.save(equipment)).thenReturn(equipment);

        service.updateEquipment(3L, new UpdateEquipmentRequest(" Bộ cờ ", EquipmentCategory.CHESS_SET,
                EquipmentStatus.GOOD, "SER-1", "Đủ quân"));

        assertThat(equipment.getName()).isEqualTo("Bộ cờ");
        assertThat(equipment.getCategory()).isEqualTo(EquipmentCategory.CHESS_SET);
        assertThat(equipment.getCode()).isEqualTo("E-01");
        assertThat(equipment.getRoom()).isSameAs(room);
    }
}
