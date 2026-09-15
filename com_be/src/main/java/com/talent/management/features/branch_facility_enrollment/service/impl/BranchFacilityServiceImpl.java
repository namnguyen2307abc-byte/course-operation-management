package com.talent.management.features.branch_facility_enrollment.service.impl;

import com.talent.management.features.branch_facility_enrollment.dto.request.CreateBranchRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.EquipmentResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomResponse;
import com.talent.management.features.branch_facility_enrollment.mapper.BranchFacilityMapper;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.branch_facility_enrollment.repository.EquipmentRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.branch_facility_enrollment.service.BranchFacilityService;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.Equipment;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.enums.EquipmentCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchFacilityServiceImpl implements BranchFacilityService {

    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;
    private final BranchFacilityMapper branchFacilityMapper;

    @Override
    public List<BranchResponse> getAllBranches() {
        return branchRepository.findAll().stream()
                .map(branchFacilityMapper::toBranchResponse)
                .toList();
    }

    @Override
    public List<BranchOptionResponse> getBranchOptions() {
        return branchRepository.findByActiveTrue().stream()
                .map(branchFacilityMapper::toBranchOptionResponse)
                .toList();
    }

    @Override
    public List<RoomResponse> getRoomsByBranch(Long branchId) {
        return roomRepository.findByBranchId(branchId).stream()
                .map(branchFacilityMapper::toRoomResponse)
                .toList();
    }

    @Override
    public List<RoomOptionResponse> getRoomOptions(Long branchId) {
        return roomRepository.findByBranchId(branchId).stream()
                .map(branchFacilityMapper::toRoomOptionResponse)
                .toList();
    }

    @Override
    public List<EquipmentResponse> getEquipmentsByBranch(Long branchId, EquipmentCategory category) {
        var equipments = (category != null)
                ? equipmentRepository.findEquipmentsByBranchIdAndCategory(branchId, category)
                : equipmentRepository.findEquipmentsByBranchId(branchId);

        return equipments.stream()
                .map(branchFacilityMapper::toEquipmentResponse)
                .toList();
    }

    @Override
    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        Branch branch = branchFacilityMapper.toBranch(request);
        Branch saved = branchRepository.save(branch);
        return branchFacilityMapper.toBranchResponse(saved);
    }

    @Override
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở với id: " + request.getBranchId()));
        Room room = branchFacilityMapper.toRoom(request, branch);
        Room saved = roomRepository.save(room);
        return branchFacilityMapper.toRoomResponse(saved);
    }

    @Override
    @Transactional
    public EquipmentResponse createEquipment(CreateEquipmentRequest request) {
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng học với id: " + request.getRoomId()));
        Equipment equipment = branchFacilityMapper.toEquipment(request, room);
        Equipment saved = equipmentRepository.save(equipment);
        return branchFacilityMapper.toEquipmentResponse(saved);
    }
}
