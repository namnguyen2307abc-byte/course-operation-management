package com.talent.management.features.branch_facility_enrollment.controller;

import com.talent.management.features.branch_facility_enrollment.dto.response.*;
import com.talent.management.features.branch_facility_enrollment.service.FacilityBrowsingService;
import com.talent.management.shared.dto.PageResponse;
import com.talent.management.shared.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class FacilityBrowsingController {
    private final FacilityBrowsingService service;

    @GetMapping("/{branchId}/counts")
    public FacilityBrowsingService.BranchCounts counts(@PathVariable Long branchId) {
        return service.counts(branchId);
    }

    @GetMapping("/{branchId}/rooms/page")
    public PageResponse<RoomResponse> rooms(@PathVariable Long branchId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) RoomStatus status,
            @RequestParam(required = false) RoomType type, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "code") String order) {
        return service.rooms(branchId, keyword, status, type, page, size, order);
    }

    @GetMapping("/{branchId}/equipments/page")
    public PageResponse<EquipmentResponse> equipments(@PathVariable Long branchId,
            @RequestParam(required = false) Long roomId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EquipmentStatus status, @RequestParam(required = false) EquipmentCategory category,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "code") String order) {
        return service.equipments(branchId, roomId, keyword, status, category, page, size, order);
    }

    @GetMapping("/rooms/{roomId}/overview")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','BRANCH_MANAGER','TEACHER')")
    public FacilityBrowsingService.RoomOverview overview(@PathVariable Long roomId) {
        return service.roomOverview(roomId);
    }

    @GetMapping("/rooms/{roomId}/detail")
    public RoomResponse roomDetail(@PathVariable Long roomId) {
        return service.roomDetail(roomId);
    }

    @GetMapping("/rooms/{roomId}/equipments/page")
    public PageResponse<EquipmentResponse> roomEquipment(@PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.roomEquipment(roomId, page, size);
    }

    @GetMapping("/rooms/{roomId}/classes/page")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','BRANCH_MANAGER','TEACHER')")
    public PageResponse<RoomUsageResponse.ClassUsage> roomClasses(@PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.roomClasses(roomId, page, size);
    }

    @GetMapping("/rooms/{roomId}/lessons/page")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','BRANCH_MANAGER','TEACHER')")
    public PageResponse<RoomUsageResponse.LessonUsage> roomLessons(@PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.roomLessons(roomId, page, size);
    }
}
