package com.talent.management.features.attendance_makeup.controller;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceRequestCreateRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest;
import com.talent.management.features.attendance_makeup.dto.request.AttendanceMarkRequest;
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
 * Controller cung cấp RESTful APIs cho phân hệ Nghỉ học & Học bù (Bản Base)
 * Tuân thủ chuẩn REST, OpenAPI/Swagger và phân quyền bảo mật JWT.
 */
@RestController
@RequestMapping("/api/attendance-makeup")
@RequiredArgsConstructor
@Tag(name = "2. Nghỉ học & Học bù", description = "Quản lý đơn xin nghỉ học, phê duyệt giáo viên, tự động sinh ca học bù, xếp lịch và điểm danh hoàn thành")
public class AbsenceMakeupController {

    private final AbsenceMakeupService absenceMakeupService;

    // =========================================================================
    // LUỒNG 1 & 2: QUẢN LÝ ĐƠN XIN NGHỈ HỌC
    // =========================================================================

    @Operation(summary = "Chức năng 1: Tạo yêu cầu nghỉ học (Phụ huynh hoặc giáo viên tạo)")
    @PostMapping("/absence-requests")
    public ResponseEntity<AbsenceRequestResponse> createAbsenceRequest(@Valid @RequestBody AbsenceRequestCreateRequest request) {
        AbsenceRequestResponse response = absenceMakeupService.createAbsenceRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Chức năng 2: Lấy danh sách yêu cầu nghỉ học (Hỗ trợ lọc theo trạng thái, học viên, lớp)")
    @GetMapping("/absence-requests")
    public ResponseEntity<List<AbsenceRequestResponse>> getAbsenceRequests(
            @RequestParam(required = false) AbsenceStatus status,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long classId) {
        List<AbsenceRequestResponse> list = absenceMakeupService.getAbsenceRequests(status, studentId, classId);
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "Chức năng 9: Xem chi tiết yêu cầu nghỉ học")
    @GetMapping("/absence-requests/{id}")
    public ResponseEntity<AbsenceRequestResponse> getAbsenceRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(absenceMakeupService.getAbsenceRequestById(id));
    }

    @Operation(summary = "Chức năng 3 & 6: Giáo viên duyệt thông thường -> APPROVED + Tự động tạo ca học bù (PENDING)")
    @PutMapping("/absence-requests/{id}/approve")
    public ResponseEntity<AbsenceRequestResponse> approveAbsenceRequest(
            @PathVariable Long id,
            @RequestBody(required = false) AbsenceReviewRequest request) {
        return ResponseEntity.ok(absenceMakeupService.approveAbsenceRequest(id, request));
    }

    @Operation(summary = "Chức năng 4 & 6: Giáo viên duyệt đặc biệt -> SPECIAL_APPROVED (Bắt buộc ghi chú) + Tự động tạo ca học bù")
    @PutMapping("/absence-requests/{id}/special-approve")
    public ResponseEntity<AbsenceRequestResponse> specialApproveAbsenceRequest(
            @PathVariable Long id,
            @RequestBody AbsenceReviewRequest request) {
        return ResponseEntity.ok(absenceMakeupService.specialApproveAbsenceRequest(id, request));
    }

    @Operation(summary = "Chức năng 5: Giáo viên từ chối đơn xin nghỉ -> REJECTED (Bắt buộc nhập lý do từ chối)")
    @PutMapping("/absence-requests/{id}/reject")
    public ResponseEntity<AbsenceRequestResponse> rejectAbsenceRequest(
            @PathVariable Long id,
            @RequestBody AbsenceReviewRequest request) {
        return ResponseEntity.ok(absenceMakeupService.rejectAbsenceRequest(id, request));
    }

    // =========================================================================
    // LUỒNG 3 & 4: ĐIỀU PHỐI VÀ ĐIỂM DANH HỌC BÙ
    // =========================================================================

    @Operation(summary = "Lấy danh sách các yêu cầu học bù (Lọc theo PENDING, SCHEDULED, COMPLETED, CANCELLED)")
    @GetMapping("/makeup-requests")
    public ResponseEntity<List<MakeupRegistrationResponse>> getMakeupRegistrations(
            @RequestParam(required = false) MakeupStatus status,
            @RequestParam(required = false) Long studentId) {
        return ResponseEntity.ok(absenceMakeupService.getMakeupRegistrations(status, studentId));
    }

    @Operation(summary = "Chức năng 9: Xem chi tiết yêu cầu học bù")
    @GetMapping("/makeup-requests/{id}")
    public ResponseEntity<MakeupRegistrationResponse> getMakeupRegistrationById(@PathVariable Long id) {
        return ResponseEntity.ok(absenceMakeupService.getMakeupRegistrationById(id));
    }

    @Operation(summary = "Chức năng 7: Xếp lịch học bù (Gán buổi học bù và đổi trạng thái sang SCHEDULED)")
    @PutMapping("/makeup-requests/{id}/schedule")
    public ResponseEntity<MakeupRegistrationResponse> scheduleMakeup(
            @PathVariable Long id,
            @Valid @RequestBody MakeupScheduleRequest request) {
        return ResponseEntity.ok(absenceMakeupService.scheduleMakeup(id, request));
    }

    @Operation(summary = "Chức năng 8: Hoàn thành học bù (Đổi trạng thái sang COMPLETED và lưu điểm danh PRESENT)")
    @PutMapping("/makeup-requests/{id}/complete")
    public ResponseEntity<MakeupRegistrationResponse> completeMakeup(@PathVariable Long id) {
        return ResponseEntity.ok(absenceMakeupService.completeMakeup(id));
    }

    @Operation(summary = "Điểm danh buổi học và tự động hoàn thành ca học bù liên quan nếu có mặt")
    @PostMapping("/attendance/mark")
    public ResponseEntity<Void> markAttendance(@Valid @RequestBody AttendanceMarkRequest request) {
        absenceMakeupService.markAttendance(request);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // HELPER APIs HỖ TRỢ DROPDOWNS & LỰA CHỌN GIAO DIỆN
    // =========================================================================

    @Operation(summary = "Lấy danh sách học viên để chọn (Phụ huynh lấy con mình, GV/Admin lấy học viên)")
    @GetMapping("/students")
    public ResponseEntity<List<StudentOptionResponse>> getSelectableStudents() {
        return ResponseEntity.ok(absenceMakeupService.getSelectableStudents());
    }

    @Operation(summary = "Lấy danh sách các buổi học của học viên để phụ huynh chọn buổi xin nghỉ")
    @GetMapping("/students/{studentId}/lessons")
    public ResponseEntity<List<LessonOptionResponse>> getLessonsForAbsenceRequest(@PathVariable Long studentId) {
        return ResponseEntity.ok(absenceMakeupService.getLessonsForAbsenceRequest(studentId));
    }

    @Operation(summary = "Lấy danh sách các ca học khả dụng để giáo viên chọn xếp học bù")
    @GetMapping("/makeup-requests/{id}/available-lessons")
    public ResponseEntity<List<LessonOptionResponse>> getAvailableLessonsForMakeup(@PathVariable Long id) {
        return ResponseEntity.ok(absenceMakeupService.getAvailableLessonsForMakeup(id));
    }
}
