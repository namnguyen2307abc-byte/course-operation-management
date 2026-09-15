package com.talent.management.features.branch_facility_enrollment.service;

import com.talent.management.features.branch_facility_enrollment.dto.BranchDto;
import com.talent.management.features.branch_facility_enrollment.dto.EquipmentDto;
import com.talent.management.features.branch_facility_enrollment.dto.RoomDto;
import com.talent.management.features.branch_facility_enrollment.mapper.BranchFacilityMapper;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.branch_facility_enrollment.repository.EquipmentRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.shared.enums.EquipmentCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchFacilityService {

    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;
    private final BranchFacilityMapper branchFacilityMapper;

    public List<BranchDto> getAllBranches() {
        return branchRepository.findAll().stream()
                .map(branchFacilityMapper::toBranchDto)
                .toList();
    }

    public List<RoomDto> getRoomsByBranch(Long branchId) {
        return roomRepository.findByBranchId(branchId).stream()
                .map(branchFacilityMapper::toRoomDto)
                .toList();
    }

    public List<EquipmentDto> getEquipmentsByBranch(Long branchId, EquipmentCategory category) {
        var equipments = (category != null)
                ? equipmentRepository.findEquipmentsByBranchIdAndCategory(branchId, category)
                : equipmentRepository.findEquipmentsByBranchId(branchId);

        return equipments.stream()
                .map(branchFacilityMapper::toEquipmentDto)
                .toList();
    }
}
