package com.talent.management.features.course_enrollment.controller;

import com.talent.management.features.course_enrollment.dto.request.EnrollmentDecisionRequest;
import com.talent.management.features.course_enrollment.dto.request.EnrollmentRequestCreateRequest;
import com.talent.management.features.course_enrollment.dto.response.ChildResponse;
import com.talent.management.features.course_enrollment.dto.response.ClassResponse;
import com.talent.management.features.course_enrollment.dto.response.CourseResponse;
import com.talent.management.features.course_enrollment.dto.response.EnrollmentRequestResponse;
import com.talent.management.features.course_enrollment.service.CourseEnrollmentService;
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
@RequestMapping("/api/course-enrollment")
@RequiredArgsConstructor
@Tag(name = "Course Enrollment", description = "Đăng ký lớp học và duyệt yêu cầu đăng ký")
public class CourseEnrollmentController {

    private final CourseEnrollmentService service;

    @GetMapping("/children")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "Lấy danh sách học viên của phụ huynh")
    public ResponseEntity<List<ChildResponse>> getMyChildren() {
        return ResponseEntity.ok(service.getMyChildren());
    }

    @GetMapping("/courses")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "Lấy danh sách khóa học")
    public ResponseEntity<List<CourseResponse>> getCourses() {
        return ResponseEntity.ok(service.getCourses());
    }

    @GetMapping("/courses/{courseId}/classes")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "Lấy các lớp còn chỗ của khóa học")
    public ResponseEntity<List<ClassResponse>> getOpenClasses(@PathVariable Long courseId) {
        return ResponseEntity.ok(service.getOpenClasses(courseId));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "Gửi yêu cầu đăng ký lớp")
    public ResponseEntity<EnrollmentRequestResponse> submitRequest(
            @Valid @RequestBody EnrollmentRequestCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submitRequest(request));
    }

    @GetMapping("/requests/my")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "Xem trạng thái các yêu cầu đã gửi")
    public ResponseEntity<List<EnrollmentRequestResponse>> getMyRequests() {
        return ResponseEntity.ok(service.getMyRequests());
    }

    @GetMapping("/staff/requests")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_MANAGER', 'ADMIN')")
    @Operation(summary = "Lấy các yêu cầu đang chờ duyệt")
    public ResponseEntity<List<EnrollmentRequestResponse>> getPendingRequests() {
        return ResponseEntity.ok(service.getPendingRequests());
    }

    @PatchMapping("/staff/requests/{requestId}/decision")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_MANAGER', 'ADMIN')")
    @Operation(summary = "Duyệt hoặc từ chối yêu cầu đăng ký")
    public ResponseEntity<EnrollmentRequestResponse> decide(
            @PathVariable Long requestId,
            @Valid @RequestBody EnrollmentDecisionRequest request
    ) {
        return ResponseEntity.ok(service.decide(requestId, request));
    }
}
