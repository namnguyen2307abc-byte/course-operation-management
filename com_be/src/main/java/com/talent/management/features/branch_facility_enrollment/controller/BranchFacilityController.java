package com.talent.management.features.branch_facility_enrollment.controller;

import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.UpdateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.EquipmentResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomUsageResponse;
import com.talent.management.features.branch_facility_enrollment.service.BranchFacilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
@Tag(name = "1. Cơ sở, CSVC & Xếp lớp", description = "Cơ sở, phòng và thiết bị phục vụ trung tâm đa môn")
public class BranchFacilityController {

    private final BranchFacilityService branchFacilityService;

    @GetMapping
    @Operation(summary = "Xem danh sách các cơ sở / chi nhánh")
    public ResponseEntity<List<BranchResponse>> getAllBranches() {
        return ResponseEntity.ok(branchFacilityService.getAllBranches());
    }

    @GetMapping("/options")
    @Operation(summary = "Lấy danh sách tóm tắt cơ sở cho dropdown select")
    public ResponseEntity<List<BranchOptionResponse>> getBranchOptions() {
        return ResponseEntity.ok(branchFacilityService.getBranchOptions());
    }

    @GetMapping("/{branchId}/rooms")
    @Operation(summary = "Xem danh sách phòng học theo cơ sở")
    public ResponseEntity<List<RoomResponse>> getRoomsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(branchFacilityService.getRoomsByBranch(branchId));
    }

    @GetMapping("/rooms/{roomId}/usage")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'BRANCH_MANAGER', 'TEACHER')")
    public ResponseEntity<RoomUsageResponse> getRoomUsage(@PathVariable Long roomId) {
        return ResponseEntity.ok(branchFacilityService.getRoomUsage(roomId));
    }

    @PutMapping("/rooms/{roomId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> updateRoom(@PathVariable Long roomId,
                                                   @Valid @RequestBody UpdateRoomRequest request) {
        return ResponseEntity.ok(branchFacilityService.updateRoom(roomId, request));
    }

    @PutMapping("/equipments/{equipmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EquipmentResponse> updateEquipment(@PathVariable Long equipmentId,
                                                             @Valid @RequestBody UpdateEquipmentRequest request) {
        return ResponseEntity.ok(branchFacilityService.updateEquipment(equipmentId, request));
    }
}
