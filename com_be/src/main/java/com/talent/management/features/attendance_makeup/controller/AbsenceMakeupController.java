package com.talent.management.features.attendance_makeup.controller;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceRequestCreateRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest;
import com.talent.management.features.attendance_makeup.dto.request.AttendanceMarkRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupCancelRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupScheduleRequest;
import com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse;
import com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse;
import com.talent.management.features.attendance_makeup.dto.response.MakeupRegistrationResponse;
import com.talent.management.features.attendance_makeup.dto.response.StudentOptionResponse;
import com.talent.management.features.attendance_makeup.service.AbsenceMakeupService;
import com.talent.management.shared.enums.AbsenceStatus;
import com.talent.management.shared.enums.MakeupStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller RESTful APIs cho Phân hệ Nghỉ học & Học bù — Luồng Mới (4 giai đoạn).
 *
 * Phân quyền theo vai trò thực tế:
 *  PARENT  → Tạo đơn, xem đơn con mình
 *  TEACHER → Xét duyệt đơn (/review), điểm danh
 *  STAFF   → Xếp/hủy lịch bù (/schedule, /cancel), điểm danh
 *  ADMIN   → Toàn quyền
 */
@RestController
@RequestMapping("/api/attendance-makeup")
@RequiredArgsConstructor
@Tag(name = "2. Nghỉ học & Học bù",
     description = "Quản lý đơn xin nghỉ học, phê duyệt theo vai trò, điều phối lịch học bù và điểm danh hoàn thành")
public class AbsenceMakeupController {

    private final AbsenceMakeupService absenceMakeupService;

    // =========================================================================
    // GIAI ĐOẠN 1: PHỤ HUYNH GỬI ĐƠN XIN NGHỈ
    // =========================================================================

    @Operation(summary = "Giai đoạn 1: Tạo yêu cầu nghỉ học (PARENT / TEACHER / STAFF / ADMIN)")
    @PostMapping("/absence-requests")
    public ResponseEntity<AbsenceRequestResponse> createAbsenceRequest(
            @Valid @RequestBody AbsenceRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(absenceMakeupService.createAbsenceRequest(request));
    }

    @Operation(summary = "Danh sách yêu cầu nghỉ học — lọc theo status, studentId, classId")
    @GetMapping("/absence-requests")
    public ResponseEntity<List<AbsenceRequestResponse>> getAbsenceRequests(
            @RequestParam(required = false) AbsenceStatus status,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long classId) {
        return ResponseEntity.ok(absenceMakeupService.getAbsenceRequests(status, studentId, classId));
    }

    @Operation(summary = "Chi tiết yêu cầu nghỉ học theo ID")
    @GetMapping("/absence-requests/{id}")
    public ResponseEntity<AbsenceRequestResponse> getAbsenceRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(absenceMakeupService.getAbsenceRequestById(id));
    }

    // =========================================================================
    // GIAI ĐOẠN 2: GIÁO VIÊN XÉT DUYỆT
    // =========================================================================

    /**
     * Endpoint duy nhất xét duyệt đơn nghỉ — thay thế 3 endpoint cũ
     * (PUT /approve, PUT /special-approve, PUT /reject).
     *
     * Body: { "decision": "APPROVED" | "EXCUSED" | "REJECTED", "reviewNote": "..." }
     *
     * Phân quyền: TEACHER, ADMIN
     */
    @Operation(summary = "Giai đoạn 2: Giáo viên xét duyệt đơn nghỉ (TEACHER / ADMIN) — " +
               "APPROVED: cần bù | EXCUSED: miễn bù | REJECTED: từ chối")
    @PatchMapping("/absence-requests/{id}/review")
    public ResponseEntity<AbsenceRequestResponse> reviewAbsenceRequest(
            @PathVariable Long id,
            @Valid @RequestBody AbsenceReviewRequest request) {
        return ResponseEntity.ok(absenceMakeupService.reviewAbsenceRequest(id, request));
    }

    // =========================================================================
    // GIAI ĐOẠN 3: NHÂN VIÊN/ADMIN XẾP LỊCH VÀ HỦY CA BÙ
    // =========================================================================

    @Operation(summary = "Danh sách yêu cầu học bù — lọc theo status, studentId")
    @GetMapping("/makeup-requests")
    public ResponseEntity<List<MakeupRegistrationResponse>> getMakeupRegistrations(
            @RequestParam(required = false) MakeupStatus status,
            @RequestParam(required = false) Long studentId) {
        return ResponseEntity.ok(absenceMakeupService.getMakeupRegistrations(status, studentId));
    }

    @Operation(summary = "Chi tiết yêu cầu học bù theo ID")
    @GetMapping("/makeup-requests/{id}")
    public ResponseEntity<MakeupRegistrationResponse> getMakeupRegistrationById(@PathVariable Long id) {
        return ResponseEntity.ok(absenceMakeupService.getMakeupRegistrationById(id));
    }

    @Operation(summary = "Giai đoạn 3: Nhân viên/Admin xếp lịch học bù (STAFF / ADMIN) → SCHEDULED")
    @PutMapping("/makeup-requests/{id}/schedule")
    public ResponseEntity<MakeupRegistrationResponse> scheduleMakeup(
            @PathVariable Long id,
            @Valid @RequestBody MakeupScheduleRequest request) {
        return ResponseEntity.ok(absenceMakeupService.scheduleMakeup(id, request));
    }

    @Operation(summary = "Giai đoạn 3: Nhân viên/Admin hủy ca học bù (STAFF / ADMIN) → CANCELLED")
    @PatchMapping("/makeup-requests/{id}/cancel")
    public ResponseEntity<MakeupRegistrationResponse> cancelMakeup(
            @PathVariable Long id,
            @RequestBody(required = false) MakeupCancelRequest request) {
        return ResponseEntity.ok(absenceMakeupService.cancelMakeup(id, request));
    }

    // =========================================================================
    // GIAI ĐOẠN 4: GIÁO VIÊN ĐIỂM DANH & HOÀN THÀNH
    // =========================================================================

    @Operation(summary = "Giai đoạn 4: Điểm danh buổi học bù (TEACHER / STAFF / ADMIN) — " +
               "Nếu PRESENT và có ca SCHEDULED → tự động COMPLETED")
    @PostMapping("/attendance/mark")
    public ResponseEntity<Void> markAttendance(@Valid @RequestBody AttendanceMarkRequest request) {
        absenceMakeupService.markAttendance(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Giai đoạn 4: Hoàn thành ca học bù thủ công qua ID (TEACHER / STAFF / ADMIN)")
    @PutMapping("/makeup-requests/{id}/complete")
    public ResponseEntity<MakeupRegistrationResponse> completeMakeup(@PathVariable Long id) {
        return ResponseEntity.ok(absenceMakeupService.completeMakeup(id));
    }

    // =========================================================================
    // HELPER APIs HỖ TRỢ DROPDOWNS & LỰA CHỌN GIAO DIỆN
    // =========================================================================

    @Operation(summary = "Danh sách học viên để chọn xin nghỉ — PARENT lấy con mình")
    @GetMapping("/students")
    public ResponseEntity<List<StudentOptionResponse>> getSelectableStudents() {
        return ResponseEntity.ok(absenceMakeupService.getSelectableStudents());
    }

    @Operation(summary = "Danh sách buổi học của học viên để chọn xin nghỉ")
    @GetMapping("/students/{studentId}/lessons")
    public ResponseEntity<List<LessonOptionResponse>> getLessonsForAbsenceRequest(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(absenceMakeupService.getLessonsForAbsenceRequest(studentId));
    }

    @Operation(summary = "Danh sách ca học khả dụng để xếp lịch học bù — ưu tiên cùng khóa học")
    @GetMapping("/makeup-requests/{id}/available-lessons")
    public ResponseEntity<List<LessonOptionResponse>> getAvailableLessonsForMakeup(
            @PathVariable Long id) {
        return ResponseEntity.ok(absenceMakeupService.getAvailableLessonsForMakeup(id));
    }
}
