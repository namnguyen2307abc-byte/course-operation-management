package com.talent.management.features.branch_facility_enrollment.service;

import com.talent.management.features.branch_facility_enrollment.dto.request.CreateBranchRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.EquipmentResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomResponse;
import com.talent.management.shared.enums.EquipmentCategory;

import java.util.List;

public interface BranchFacilityService {

    List<BranchResponse> getAllBranches();

    List<BranchOptionResponse> getBranchOptions();

    List<RoomResponse> getRoomsByBranch(Long branchId);

    List<RoomOptionResponse> getRoomOptions(Long branchId);

    List<EquipmentResponse> getEquipmentsByBranch(Long branchId, EquipmentCategory category);

    BranchResponse createBranch(CreateBranchRequest request);

    RoomResponse createRoom(CreateRoomRequest request);

    EquipmentResponse createEquipment(CreateEquipmentRequest request);
}
