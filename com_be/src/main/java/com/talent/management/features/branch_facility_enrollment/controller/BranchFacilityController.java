package com.talent.management.features.branch_facility_enrollment.controller;

import com.talent.management.features.branch_facility_enrollment.dto.request.CreateBranchRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateEquipmentRequest;
import com.talent.management.features.branch_facility_enrollment.dto.request.CreateRoomRequest;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.BranchResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.EquipmentResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomOptionResponse;
import com.talent.management.features.branch_facility_enrollment.dto.response.RoomResponse;
import com.talent.management.features.branch_facility_enrollment.service.BranchFacilityService;
import com.talent.management.shared.enums.EquipmentCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/{branchId}/rooms/options")
    @Operation(summary = "Lấy danh sách tóm tắt phòng học theo cơ sở cho dropdown select")
    public ResponseEntity<List<RoomOptionResponse>> getRoomOptions(@PathVariable Long branchId) {
        return ResponseEntity.ok(branchFacilityService.getRoomOptions(branchId));
    }

    @GetMapping("/{branchId}/equipments")
    @Operation(summary = "Xem danh sách thiết bị nhạc cụ / đàn Piano theo cơ sở")
    public ResponseEntity<List<EquipmentResponse>> getEquipmentsByBranch(
            @PathVariable Long branchId,
            @RequestParam(required = false) EquipmentCategory category
    ) {
        return ResponseEntity.ok(branchFacilityService.getEquipmentsByBranch(branchId, category));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo mới cơ sở đào tạo")
    public ResponseEntity<BranchResponse> createBranch(@Valid @RequestBody CreateBranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchFacilityService.createBranch(request));
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo mới phòng học theo cơ sở")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchFacilityService.createRoom(request));
    }

    @PostMapping("/equipments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo mới thiết bị nhạc cụ vào phòng học")
    public ResponseEntity<EquipmentResponse> createEquipment(@Valid @RequestBody CreateEquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchFacilityService.createEquipment(request));
    }
}
