package com.talent.management.features.course_management.controller;

import com.talent.management.features.course_management.dto.request.CreateCourseRequest;
import com.talent.management.features.course_management.dto.request.UpdateCourseRequest;
import com.talent.management.features.course_management.dto.response.CourseDetailResponse;
import com.talent.management.features.course_management.dto.response.ProgramOptionResponse;
import com.talent.management.features.course_management.service.CourseManagementService;
import com.talent.management.features.placement_test.entity.PlacementLevel;
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
@RequestMapping("/api/course-management")
@RequiredArgsConstructor
@Tag(name = "5. Quản lý Khóa Học", description = "CRUD quản lý danh mục khóa học dành cho Nhân viên & Quản trị viên")
public class CourseManagementController {

    private final CourseManagementService service;

    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_MANAGER', 'ADMIN')")
    @Operation(summary = "Lấy toàn bộ danh sách khóa học (kèm thống kê lớp học và học viên)")
    public ResponseEntity<List<CourseDetailResponse>> getAllCourses() {
        return ResponseEntity.ok(service.getAllCourses());
    }

    @GetMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_MANAGER', 'ADMIN')")
    @Operation(summary = "Xem chi tiết một khóa học")
    public ResponseEntity<CourseDetailResponse> getCourseById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCourseById(id));
    }

    @GetMapping("/programs")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_MANAGER', 'ADMIN')")
    @Operation(summary = "Lấy danh sách bộ môn (dùng cho dropdown khi tạo/sửa khóa học)")
    public ResponseEntity<List<ProgramOptionResponse>> getAllPrograms() {
        return ResponseEntity.ok(service.getAllPrograms());
    }

    @GetMapping("/levels")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_MANAGER', 'ADMIN')")
    @Operation(summary = "Lấy danh sách trình độ dùng chung với đánh giá năng khiếu")
    public ResponseEntity<List<PlacementLevel>> getCourseLevels() {
        return ResponseEntity.ok(List.of(PlacementLevel.values()));
    }

    @PostMapping("/courses")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_MANAGER', 'ADMIN')")
    @Operation(summary = "Tạo mới khóa học")
    public ResponseEntity<CourseDetailResponse> createCourse(
            @Valid @RequestBody CreateCourseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCourse(request));
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_MANAGER', 'ADMIN')")
    @Operation(summary = "Cập nhật thông tin khóa học")
    public ResponseEntity<CourseDetailResponse> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseRequest request
    ) {
        return ResponseEntity.ok(service.updateCourse(id, request));
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Xóa khóa học (chỉ khi chưa có lớp học nào)")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        service.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }
}
