package com.talent.management.features.attendance_makeup.controller;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceRequestCreateRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupCancelRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupResolveRequest;
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
 * ============================================================================
 * CONTROLLER REST API — PHÂN HỆ NGHỈ HỌC & HỌC BÙ (4 GIAI ĐOẠN)
 * ============================================================================
 * Đường dẫn gốc: /api/attendance-makeup
 * Phân quyền theo vai trò thực tế:
 *  - PARENT                         -> Tạo đơn (/absence-requests), hủy đơn chờ, xem đơn/lịch của con
 *  - TEACHER                        -> Xét duyệt đơn (/absence-requests/{id}/review), điểm danh hoàn thành (/complete)
 *  - STAFF / BRANCH_MANAGER / ADMIN -> Duyệt đơn, xếp/hủy/báo chưa có lớp (/makeup-requests/{id}/schedule|cancel|no-slot), hoàn thành
 *
 * Tra nhanh theo tầng xử lý:
 *  - Service điều phối      -> {@code AbsenceMakeupServiceImpl}
 *  - Quy tắc 3 lượt/khóa    -> {@code MakeupQuotaPolicy}
 *  - Validate & Trùng lịch  -> {@code AbsenceMakeupValidator}
 *  - Gửi thông báo chuông   -> {@code AttendanceMakeupNotificationHelper}
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

    @Operation(summary = "Giai đoạn 1: Tạo yêu cầu nghỉ học (PARENT / TEACHER / STAFF / BRANCH_MANAGER / ADMIN)")
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

    @Operation(summary = "Giai đoạn 1: Phụ huynh hủy đơn xin nghỉ đang chờ duyệt (PENDING)")
    @PatchMapping("/absence-requests/{id}/cancel")
    public ResponseEntity<Void> cancelAbsenceRequest(@PathVariable Long id) {
        absenceMakeupService.cancelAbsenceRequest(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/absence-requests/{id}")
    public ResponseEntity<Void> deletePendingAbsenceRequest(@PathVariable Long id) {
        absenceMakeupService.cancelAbsenceRequest(id);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // GIAI ĐOẠN 2: GIÁO VIÊN XÉT DUYỆT
    // =========================================================================

    /**
     * Endpoint duy nhất xét duyệt đơn nghỉ.
     *
     * Body: { "decision": "APPROVED" | "REJECTED", "reviewNote": "..." }
     *
     * Phân quyền: TEACHER phụ trách lớp/buổi, STAFF, BRANCH_MANAGER, ADMIN
     */
    @Operation(summary = "Giai đoạn 2: Xét duyệt đơn nghỉ (TEACHER / STAFF / BRANCH_MANAGER / ADMIN) — " +
               "APPROVED: đồng ý (tạo ca bù nếu needMakeup=true) | REJECTED: từ chối")
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

    @Operation(summary = "Giai đoạn 3: Điều phối viên xếp lịch học bù (STAFF / BRANCH_MANAGER / ADMIN) → SCHEDULED")
    @PutMapping("/makeup-requests/{id}/schedule")
    public ResponseEntity<MakeupRegistrationResponse> scheduleMakeup(
            @PathVariable Long id,
            @Valid @RequestBody MakeupScheduleRequest request) {
        return ResponseEntity.ok(absenceMakeupService.scheduleMakeup(id, request));
    }

    @Operation(summary = "Giai đoạn 3: Điều phối viên hủy ca học bù (STAFF / BRANCH_MANAGER / ADMIN) → CANCELLED")
    @PatchMapping("/makeup-requests/{id}/cancel")
    public ResponseEntity<MakeupRegistrationResponse> cancelMakeup(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) MakeupCancelRequest request) {
        return ResponseEntity.ok(absenceMakeupService.cancelMakeup(id, request));
    }

    @Operation(summary = "Giai đoạn 3: Không có lớp bù phù hợp — đóng ca bù và báo phụ huynh (STAFF / BRANCH_MANAGER / ADMIN) → CANCELLED")
    @PatchMapping("/makeup-requests/{id}/no-slot")
    public ResponseEntity<MakeupRegistrationResponse> markNoSlotAvailable(
            @PathVariable Long id,
            @Valid @RequestBody MakeupResolveRequest request) {
        return ResponseEntity.ok(absenceMakeupService.markNoSlotAvailable(id, request));
    }

    // =========================================================================
    // GIAI ĐOẠN 4: HOÀN THÀNH HỌC BÙ THỦ CÔNG
    // =========================================================================

    @Operation(summary = "Giai đoạn 4: Hoàn thành ca học bù thủ công (TEACHER lớp bù / STAFF / BRANCH_MANAGER / ADMIN)")
    @PutMapping("/makeup-requests/{id}/complete")
    public ResponseEntity<MakeupRegistrationResponse> completeMakeup(@PathVariable Long id) {
        return ResponseEntity.ok(absenceMakeupService.completeMakeup(id));
    }

    // =========================================================================
    // HELPER APIs HỖ TRỢ DROPDOWNS & LỰA CHỌN GIAO DIỆN
    // =========================================================================

    @Operation(summary = "Danh sách học viên để chọn xin nghỉ — PARENT lấy con mình")
    @GetMapping({"/students", "/students/my-children"})
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
