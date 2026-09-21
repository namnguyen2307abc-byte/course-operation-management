package com.talent.management.features.attendance_makeup.service;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceRequestCreateRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest;
import com.talent.management.features.attendance_makeup.dto.request.AttendanceMarkRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupScheduleRequest;
import com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse;
import com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse;
import com.talent.management.features.attendance_makeup.dto.response.MakeupRegistrationResponse;
import com.talent.management.features.attendance_makeup.dto.response.StudentOptionResponse;
import com.talent.management.shared.enums.AbsenceStatus;
import com.talent.management.shared.enums.MakeupStatus;

import java.util.List;

/**
 * Service xử lý toàn bộ nghiệp vụ Nghỉ học & Học bù (Bản Base)
 * Bao gồm 4 luồng nghiệp vụ và 9 chức năng cốt lõi.
 */
public interface AbsenceMakeupService {

    // ==========================================
    // LUỒNG 1: TẠO VÀ QUẢN LÝ YÊU CẦU NGHỈ HỌC
    // ==========================================

    /**
     * Chức năng 1: Tạo yêu cầu nghỉ học (Phụ huynh hoặc giáo viên tạo)
     */
    AbsenceRequestResponse createAbsenceRequest(AbsenceRequestCreateRequest request);

    /**
     * Chức năng 2: Danh sách yêu cầu nghỉ học (Hỗ trợ lọc theo trạng thái, học viên)
     */
    List<AbsenceRequestResponse> getAbsenceRequests(AbsenceStatus status, Long studentId, Long classId);

    /**
     * Chức năng 9: Xem chi tiết yêu cầu nghỉ học
     */
    AbsenceRequestResponse getAbsenceRequestById(Long id);

    // ==========================================
    // LUỒNG 2: GIÁO VIÊN XỬ LÝ YÊU CẦU NGHỈ HỌC
    // ==========================================

    /**
     * Chức năng 3 & 6: Duyệt đơn nghỉ học -> APPROVED + Tự động tạo ca học bù (PENDING)
     */
    AbsenceRequestResponse approveAbsenceRequest(Long id, AbsenceReviewRequest request);

    /**
     * Chức năng 4 & 6: Duyệt đặc biệt -> SPECIAL_APPROVED (Bắt buộc ghi chú) + Tự động tạo ca học bù (PENDING)
     */
    AbsenceRequestResponse specialApproveAbsenceRequest(Long id, AbsenceReviewRequest request);

    /**
     * Chức năng 5: Từ chối đơn nghỉ học -> REJECTED (Bắt buộc nhập lý do từ chối)
     */
    AbsenceRequestResponse rejectAbsenceRequest(Long id, AbsenceReviewRequest request);

    // ==========================================
    // LUỒNG 3: XẾP LỊCH HỌC BÙ
    // ==========================================

    /**
     * Danh sách các yêu cầu học bù (Lọc theo PENDING, SCHEDULED, COMPLETED, CANCELLED)
     */
    List<MakeupRegistrationResponse> getMakeupRegistrations(MakeupStatus status, Long studentId);

    /**
     * Chức năng 9: Xem chi tiết yêu cầu học bù
     */
    MakeupRegistrationResponse getMakeupRegistrationById(Long id);

    /**
     * Chức năng 7: Xếp lịch học bù -> Chọn buổi học bù và đổi trạng thái sang SCHEDULED
     */
    MakeupRegistrationResponse scheduleMakeup(Long id, MakeupScheduleRequest request);

    // ==========================================
    // LUỒNG 4: HOÀN THÀNH HỌC BÙ & ĐIỂM DANH
    // ==========================================

    /**
     * Chức năng 8: Hoàn thành học bù (Điểm danh có mặt tại buổi bù -> COMPLETED)
     */
    MakeupRegistrationResponse completeMakeup(Long id);

    /**
     * Điểm danh buổi học và tự động cập nhật trạng thái học bù liên quan
     */
    void markAttendance(AttendanceMarkRequest request);

    // ==========================================
    // HELPER APIs HỖ TRỢ GIAO DIỆN
    // ==========================================

    /**
     * Lấy danh sách học viên có thể chọn (Phụ huynh lấy con mình, GV/Admin lấy học viên)
     */
    List<StudentOptionResponse> getSelectableStudents();

    /**
     * Lấy danh sách các buổi học của học viên để phụ huynh chọn buổi xin nghỉ
     */
    List<LessonOptionResponse> getLessonsForAbsenceRequest(Long studentId);

    /**
     * Lấy danh sách các ca học khả dụng để giáo viên chọn xếp học bù
     */
    List<LessonOptionResponse> getAvailableLessonsForMakeup(Long makeupId);
}
