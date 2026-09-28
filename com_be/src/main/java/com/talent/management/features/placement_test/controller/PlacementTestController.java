package com.talent.management.features.placement_test.controller;

import com.talent.management.features.placement_test.dto.request.CreatePlacementScheduleRequest;
import com.talent.management.features.placement_test.dto.request.PlacementAssessmentRequest;
import com.talent.management.features.placement_test.dto.response.PlacementScheduleResponse;
import com.talent.management.features.placement_test.service.PlacementTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.talent.management.shared.service.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/placement-tests")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STAFF', 'BRANCH_MANAGER')")
@RequiredArgsConstructor
@Tag(name = "3. Test đầu vào & Lộ trình", description = "Quản lý đánh giá năng khiếu đầu vào, ghi âm/video đàn và lộ trình phát triển")
public class PlacementTestController {

    private final PlacementTestService placementTestService;
    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên file đính kèm (video, ghi âm, ảnh, record) cho bài thi xếp lớp")
    public ResponseEntity<Map<String, String>> uploadAttachment(
            @RequestParam("file") MultipartFile file) {
        String fileUrl = fileStorageService.storeFile(file, "placement-tests");
        return ResponseEntity.ok(Map.of("fileUrl", fileUrl));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các lịch thi xếp lớp / đánh giá năng khiếu")
    public ResponseEntity<List<PlacementScheduleResponse>> getAllSchedules(
            @RequestParam(required = false) String parentEmail) {
        if (parentEmail != null && !parentEmail.isBlank()) {
            return ResponseEntity.ok(placementTestService.getSchedulesForParent(parentEmail));
        }
        return ResponseEntity.ok(placementTestService.getAllSchedules());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một lịch thi xếp lớp theo ID")
    public ResponseEntity<PlacementScheduleResponse> getScheduleById(@PathVariable Long id) {
        return ResponseEntity.ok(placementTestService.getScheduleById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo mới một lịch đăng ký thi xếp lớp / kiểm tra đầu vào")
    public ResponseEntity<PlacementScheduleResponse> createSchedule(
            @Valid @RequestBody CreatePlacementScheduleRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(placementTestService.createSchedule(request, username));
    }

    @PostMapping("/{id}/assess")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "Giáo viên / Chuyên môn chấm điểm đánh giá và xếp trình độ cho bài thi (Chỉ dành cho Giáo viên)")
    public ResponseEntity<PlacementScheduleResponse> saveAssessment(
            @PathVariable Long id,
            @Valid @RequestBody PlacementAssessmentRequest request,
            Authentication authentication) {
        String teacherUsername = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(placementTestService.saveAssessment(id, request, teacherUsername));
    }
}
