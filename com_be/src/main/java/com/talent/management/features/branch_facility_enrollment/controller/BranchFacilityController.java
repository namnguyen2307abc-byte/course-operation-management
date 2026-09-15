package com.talent.management.features.branch_facility_enrollment.controller;

import com.talent.management.features.branch_facility_enrollment.dto.BranchDto;
import com.talent.management.features.branch_facility_enrollment.dto.EquipmentDto;
import com.talent.management.features.branch_facility_enrollment.dto.RoomDto;
import com.talent.management.features.branch_facility_enrollment.service.BranchFacilityService;
import com.talent.management.shared.enums.EquipmentCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
@Tag(name = "1. Cơ sở, CSVC & Xếp lớp", description = "Quản lý hệ thống cơ sở, phòng học âm nhạc và trang thiết bị đàn Piano")
public class BranchFacilityController {

    private final BranchFacilityService branchFacilityService;

    @GetMapping
    @Operation(summary = "Xem danh sách các cơ sở / chi nhánh")
    public ResponseEntity<List<BranchDto>> getAllBranches() {
        return ResponseEntity.ok(branchFacilityService.getAllBranches());
    }

    @GetMapping("/{branchId}/rooms")
    @Operation(summary = "Xem danh sách phòng học theo cơ sở")
    public ResponseEntity<List<RoomDto>> getRoomsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(branchFacilityService.getRoomsByBranch(branchId));
    }

    @GetMapping("/{branchId}/equipments")
    @Operation(summary = "Xem danh sách thiết bị nhạc cụ / đàn Piano theo cơ sở")
    public ResponseEntity<List<EquipmentDto>> getEquipmentsByBranch(
            @PathVariable Long branchId,
            @RequestParam(required = false) EquipmentCategory category
    ) {
        return ResponseEntity.ok(branchFacilityService.getEquipmentsByBranch(branchId, category));
    }
}
