package com.talent.management.features.branch_facility_enrollment.service;

import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.EquipmentResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomUsageResponse;

import java.util.List;

public interface BranchFacilityService {

    List<BranchResponse> getAllBranches();

    List<BranchOptionResponse> getBranchOptions();

    List<RoomResponse> getRoomsByBranch(Long branchId);

    RoomUsageResponse getRoomUsage(Long roomId);

    RoomResponse updateRoom(Long roomId, UpdateRoomRequest request);

    EquipmentResponse updateEquipment(Long equipmentId, UpdateEquipmentRequest request);
}
