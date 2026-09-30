package com.talent.management.features.attendance_makeup.service;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceRequestCreateRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest;
import com.talent.management.features.attendance_makeup.dto.request.AttendanceMarkRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupCancelRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupScheduleRequest;
import com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse;
import com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse;
import com.talent.management.features.attendance_makeup.dto.response.MakeupRegistrationResponse;
import com.talent.management.features.attendance_makeup.dto.response.StudentOptionResponse;
import com.talent.management.shared.enums.AbsenceStatus;
import com.talent.management.shared.enums.MakeupStatus;

import java.util.List;

/**
 * Service xử lý toàn bộ nghiệp vụ Phân hệ Nghỉ học & Học bù — Luồng Mới (4 giai đoạn).
 *
 * Giai đoạn 1: Phụ huynh gửi đơn
 * Giai đoạn 2: Giáo viên xét duyệt (1 endpoint /review, 3 quyết định: APPROVED/EXCUSED/REJECTED)
 * Giai đoạn 3: Nhân viên/Admin xếp lịch bù (Schedule / Cancel)
 * Giai đoạn 4: Giáo viên điểm danh & hoàn thành
 */
public interface AbsenceMakeupService {

    // ==========================================
    // GIAI ĐOẠN 1: TẠO VÀ QUẢN LÝ YÊU CẦU NGHỈ HỌC
    // ==========================================

    /** Tạo yêu cầu nghỉ học — Phụ huynh, Giáo viên, Staff, Admin */
    AbsenceRequestResponse createAbsenceRequest(AbsenceRequestCreateRequest request);

    /** Danh sách yêu cầu nghỉ học — Phân quyền theo Role (PARENT/TEACHER/STAFF/ADMIN) */
    List<AbsenceRequestResponse> getAbsenceRequests(AbsenceStatus status, Long studentId, Long classId);

    /** Chi tiết yêu cầu nghỉ học theo ID */
    AbsenceRequestResponse getAbsenceRequestById(Long id);

    // ==========================================
    // GIAI ĐOẠN 2: GIÁO VIÊN XÉT DUYỆT
    // ==========================================

    /**
     * Xét duyệt đơn nghỉ — 1 endpoint duy nhất thay thế 3 endpoint cũ (approve/special-approve/reject).
     *
     * APPROVED  → Tự động tạo MakeupRegistration{PENDING}
     * EXCUSED   → Đóng luồng, KHÔNG tạo ca bù
     * REJECTED  → Đóng luồng, bắt buộc reviewNote
     */
    AbsenceRequestResponse reviewAbsenceRequest(Long id, AbsenceReviewRequest request);

    // ==========================================
    // GIAI ĐOẠN 3: NHÂN VIÊN/ADMIN XẾP LỊCH BÙ
    // ==========================================

    /** Danh sách yêu cầu học bù — Lọc theo trạng thái và học viên */
    List<MakeupRegistrationResponse> getMakeupRegistrations(MakeupStatus status, Long studentId);

    /** Chi tiết yêu cầu học bù theo ID */
    MakeupRegistrationResponse getMakeupRegistrationById(Long id);

    /** Xếp lịch học bù — Nhân viên/Admin chọn targetLesson → SCHEDULED */
    MakeupRegistrationResponse scheduleMakeup(Long id, MakeupScheduleRequest request);

    /** Hủy ca học bù — Nhân viên/Admin hủy ca PENDING hoặc SCHEDULED → CANCELLED */
    MakeupRegistrationResponse cancelMakeup(Long id, MakeupCancelRequest request);

    // ==========================================
    // GIAI ĐOẠN 4: GIÁO VIÊN ĐIỂM DANH & HOÀN THÀNH
    // ==========================================

    /**
     * Điểm danh buổi học bù — Nếu PRESENT và có MakeupRegistration{SCHEDULED} → auto COMPLETED.
     */
    void markAttendance(AttendanceMarkRequest request);

    /**
     * Hoàn thành ca học bù thủ công qua ID (khi không dùng điểm danh tự động).
     */
    MakeupRegistrationResponse completeMakeup(Long id);

    // ==========================================
    // HELPER APIs HỖ TRỢ GIAO DIỆN
    // ==========================================

    /** Lấy danh sách học viên để chọn — Phụ huynh lấy con mình, GV/Admin lấy tất cả */
    List<StudentOptionResponse> getSelectableStudents();

    /** Lấy danh sách buổi học của học viên để phụ huynh chọn xin nghỉ */
    List<LessonOptionResponse> getLessonsForAbsenceRequest(Long studentId);

    /** Lấy danh sách ca học khả dụng để nhân viên xếp lịch học bù */
    List<LessonOptionResponse> getAvailableLessonsForMakeup(Long makeupId);
}
