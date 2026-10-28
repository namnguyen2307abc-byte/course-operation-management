package com.talent.management.features.attendance_makeup.service.impl;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceRequestCreateRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest.ReviewDecision;
import com.talent.management.features.attendance_makeup.dto.request.AttendanceMarkRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupCancelRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupResolveRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupScheduleRequest;
import com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse;
import com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse;
import com.talent.management.features.attendance_makeup.dto.response.MakeupRegistrationResponse;
import com.talent.management.features.attendance_makeup.dto.response.StudentOptionResponse;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
import com.talent.management.features.attendance_makeup.repository.AttendanceEnrollmentRepository;
import com.talent.management.features.attendance_makeup.repository.AttendanceLessonRepository;
import com.talent.management.features.attendance_makeup.repository.AttendanceRecordRepository;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.features.attendance_makeup.service.AbsenceMakeupService;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.AbsenceStatus;
import com.talent.management.shared.enums.AttendanceStatus;
import com.talent.management.shared.enums.MakeupStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation nghiệp vụ Phân hệ Nghỉ học & Học bù — Luồng Mới (4 giai đoạn).
 *
 * Các cải tiến so với luồng cũ:
 *  1. Gộp 3 endpoint (approve/special-approve/reject) → 1 method reviewAbsenceRequest()
 *  2. Thêm trạng thái EXCUSED (nghỉ có lý do chính đáng, không cần học bù)
 *  3. Fix N+1 query trong getAbsenceRequests() bằng batch fetch
 *  4. Xóa Teacher fallback nguy hiểm (load toàn bộ dữ liệu)
 *  5. Thêm cancelMakeup() — nhân viên/admin hủy ca bù
 *  6. Phân quyền chính xác: TEACHER duyệt, STAFF xếp lịch
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbsenceMakeupServiceImpl implements AbsenceMakeupService {

    private final AbsenceRequestRepository absenceRequestRepository;
    private final MakeupRegistrationRepository makeupRegistrationRepository;
    private final AttendanceLessonRepository attendanceLessonRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceEnrollmentRepository attendanceEnrollmentRepository;

    /** Trạng thái ca bù đang chiếm chỗ trong lớp đích */
    private static final List<MakeupStatus> SEAT_HOLDING_STATUSES =
            List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED);
    private final StudentRepository studentRepository;
    private final AttendanceMakeupMapper attendanceMakeupMapper;
    private final CurrentUserService currentUserService;

    // =========================================================================
    // GIAI ĐOẠN 1: TẠO VÀ QUẢN LÝ YÊU CẦU NGHỈ HỌC
    // =========================================================================

    /**
     * Tạo yêu cầu nghỉ học — Giai đoạn 1.
     * Phụ huynh (hoặc Giáo viên/Staff tạo hộ) gửi đơn xin nghỉ kèm lý do.
     * Validation: nếu PARENT, học viên phải là con của mình.
     */
    @Override
    @Transactional
    public AbsenceRequestResponse createAbsenceRequest(AbsenceRequestCreateRequest request) {
        User currentUser = currentUserService.getCurrentUser();

        // 1. Học viên tồn tại
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy học viên với ID: " + request.getStudentId()));
        validateStudentAccess(currentUser, student);

        // 2. Nếu PARENT, học viên phải là con mình
        if (currentUser.getRole() == Role.PARENT) {
            if (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn chỉ có thể gửi đơn xin nghỉ học cho con của mình!");
            }
        }

        // 3. Buổi học tồn tại
        Lesson lesson = attendanceLessonRepository.findById(request.getLessonId())
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy buổi học với ID: " + request.getLessonId()));

        // 3b. Phụ huynh chỉ được xin nghỉ buổi chưa diễn ra (nghỉ trước, không hợp thức hóa sau)
        if ((currentUser.getRole() == Role.PARENT || currentUser.getRole() == Role.STUDENT)
                && lesson.getLessonDate() != null
                && lesson.getLessonDate().isBefore(LocalDate.now())) {
            throw new AttendanceMakeupException(
                    "Chỉ có thể xin nghỉ cho buổi học chưa diễn ra. Buổi học ngày " + lesson.getLessonDate()
                            + " đã qua, vui lòng liên hệ giáo vụ nếu cần hỗ trợ.");
        }

        // 4. Chống trùng lặp đơn — Không cho tạo đơn mới khi đã có PENDING hoặc APPROVED cho cùng buổi
        Optional<AbsenceRequest> existing = absenceRequestRepository
                .findByStudentIdAndLessonId(student.getId(), lesson.getId());
        if (existing.isPresent()) {
            AbsenceStatus existingStatus = existing.get().getStatus();
            if (existingStatus == AbsenceStatus.PENDING || existingStatus == AbsenceStatus.APPROVED) {
                throw new AttendanceMakeupException(
                        "Buổi học này đã có đơn xin nghỉ đang chờ duyệt hoặc đã được phê duyệt!");
            }
        }

        // 5. Tạo đơn mới ở trạng thái PENDING
        AbsenceRequest absenceRequest = AbsenceRequest.builder()
                .student(student)
                .lesson(lesson)
                .requestedByUser(currentUser)
                .reason(request.getReason().trim())
                .status(AbsenceStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        AbsenceRequest saved = absenceRequestRepository.save(absenceRequest);
        log.info("Đơn xin nghỉ mới #{} cho học viên [{}] bởi [{}]",
                saved.getId(), student.getFullName(), currentUser.getUsername());

        return attendanceMakeupMapper.toAbsenceResponse(saved, null);
    }

    /**
     * Danh sách yêu cầu nghỉ học — Phân quyền theo Role.
     *
     * FIX N+1: Dùng batch fetch (findByAbsenceRequestIdIn) thay vì gọi DB trong loop.
     * FIX TEACHER FALLBACK: TEACHER chỉ xem đơn thuộc lớp mình, không fallback load toàn bộ.
     */
    @Override
    public List<AbsenceRequestResponse> getAbsenceRequests(AbsenceStatus status, Long studentId, Long classId) {
        User currentUser = currentUserService.getCurrentUser();
        List<AbsenceRequest> list;

        switch (currentUser.getRole()) {
            case STUDENT -> {
                Optional<Student> own = studentRepository.findByAccountId(currentUser.getId());
                if (own.isEmpty()) return List.of();
                list = status != null
                        ? absenceRequestRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(own.get().getId(), status)
                        : absenceRequestRepository.findByStudentIdOrderByCreatedAtDesc(own.get().getId());
            }
            case PARENT -> {
                // Phụ huynh chỉ xem đơn của các con mình
                list = (status != null)
                        ? absenceRequestRepository.findByStudentParentIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status)
                        : absenceRequestRepository.findByStudentParentIdOrderByCreatedAtDesc(currentUser.getId());
            }
            default -> {
                // TEACHER, STAFF, ADMIN, BRANCH_MANAGER: đều nhìn thấy toàn bộ đơn xin nghỉ
                list = (status != null)
                        ? absenceRequestRepository.findByStatusOrderByCreatedAtDesc(status)
                        : absenceRequestRepository.findAllByOrderByCreatedAtDesc();
            }
        }

        // Áp dụng bộ lọc bổ sung theo học viên hoặc lớp nếu có
        List<AbsenceRequest> filtered = list.stream()
                .filter(ar -> studentId == null || (ar.getStudent() != null && ar.getStudent().getId().equals(studentId)))
                .filter(ar -> classId == null || (ar.getLesson() != null
                        && ar.getLesson().getClassEntity() != null
                        && ar.getLesson().getClassEntity().getId().equals(classId)))
                .toList();

        if (filtered.isEmpty()) {
            return List.of();
        }

        // BATCH FETCH — Fix N+1: 1 query lấy toàn bộ MakeupRegistration liên quan
        List<Long> arIds = filtered.stream().map(AbsenceRequest::getId).toList();
        Map<Long, MakeupRegistration> makeupMap = makeupRegistrationRepository
                .findByAbsenceRequestIdIn(arIds)
                .stream()
                .collect(Collectors.toMap(
                        mr -> mr.getAbsenceRequest().getId(),
                        Function.identity()));

        return filtered.stream()
                .map(ar -> attendanceMakeupMapper.toAbsenceResponse(ar, makeupMap.get(ar.getId())))
                .toList();
    }

    /** Chi tiết yêu cầu nghỉ học — Phụ huynh chỉ xem đơn của con mình */
    @Override
    public AbsenceRequestResponse getAbsenceRequestById(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        AbsenceRequest ar = absenceRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        validateStudentAccess(currentUser, ar.getStudent());

        if (currentUser.getRole() == Role.PARENT) {
            if (ar.getStudent().getParent() == null
                    || !ar.getStudent().getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn không có quyền truy cập vào đơn xin nghỉ này!");
            }
        }

        MakeupRegistration mr = makeupRegistrationRepository.findByAbsenceRequestId(ar.getId()).orElse(null);
        return attendanceMakeupMapper.toAbsenceResponse(ar, mr);
    }

    // =========================================================================
    // GIAI ĐOẠN 2: GIÁO VIÊN XÉT DUYỆT
    // =========================================================================

    /**
     * Xét duyệt đơn nghỉ học — Giai đoạn 2 (TEACHER / ADMIN).
     *
     * Gộp 3 endpoint cũ (approve/special-approve/reject) thành 1 method sạch hơn.
     *
     * APPROVED → Tạo MakeupRegistration{PENDING} tự động
     * EXCUSED  → KHÔNG tạo ca bù, luồng kết thúc
     * REJECTED → KHÔNG tạo ca bù, bắt buộc reviewNote (gửi lý do tới phụ huynh)
     */
    @Override
    @Transactional
    public AbsenceRequestResponse reviewAbsenceRequest(Long id, AbsenceReviewRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateReviewPermission(currentUser);

        AbsenceRequest ar = absenceRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        // Guard: giáo viên chỉ duyệt đơn của buổi học do chính mình phụ trách (STAFF/ADMIN duyệt thay khi cần)
        if (currentUser.getRole() == Role.TEACHER) {
            Lesson reviewedLesson = ar.getLesson();
            boolean isLessonTeacher = reviewedLesson != null
                    && reviewedLesson.getTeacher() != null
                    && reviewedLesson.getTeacher().getId().equals(currentUser.getId());
            if (!isLessonTeacher) {
                throw new AttendanceMakeupException(
                        "Chỉ giáo viên phụ trách buổi học này mới được xác nhận hoặc từ chối đơn xin nghỉ!");
            }
        }

        // Guard: chỉ xử lý đơn PENDING
        if (ar.getStatus() != AbsenceStatus.PENDING) {
            throw new AttendanceMakeupException(
                    "Đơn đã được xử lý trước đó (trạng thái hiện tại: " + ar.getStatus() + ")");
        }

        // Validate reviewNote theo decision
        ReviewDecision decision = request.getDecision();
        boolean noteIsBlank = request.getReviewNote() == null || request.getReviewNote().isBlank();

        if (decision == ReviewDecision.EXCUSED && noteIsBlank) {
            throw new AttendanceMakeupException("Vui lòng nhập lý do miễn học bù (reviewNote) khi chọn EXCUSED.");
        }
        if (decision == ReviewDecision.REJECTED && noteIsBlank) {
            throw new AttendanceMakeupException("Vui lòng nhập lý do từ chối (reviewNote) để thông báo rõ ràng tới phụ huynh.");
        }

        // Cập nhật đơn
        ar.setStatus(mapDecisionToStatus(decision));
        ar.setApprovedByTeacher(currentUser);
        ar.setReviewNote(request.getReviewNote() != null ? request.getReviewNote().trim() : null);
        ar.setReviewedAt(LocalDateTime.now());
        AbsenceRequest saved = absenceRequestRepository.save(ar);

        // Chỉ tạo MakeupRegistration khi APPROVED
        MakeupRegistration mr = null;
        if (decision == ReviewDecision.APPROVED) {
            mr = makeupRegistrationRepository.findByAbsenceRequestId(saved.getId())
                    .orElseGet(() -> MakeupRegistration.builder()
                            .absenceRequest(saved)
                            .student(saved.getStudent())
                            .originalLesson(saved.getLesson())
                            .targetLesson(null)
                            .status(MakeupStatus.PENDING)
                            .note("Tự động tạo ca học bù sau khi giáo viên phê duyệt đơn nghỉ #" + saved.getId())
                            .createdAt(LocalDateTime.now())
                            .build());
            mr = makeupRegistrationRepository.save(mr);
            log.info("Giáo viên [{}] APPROVED đơn #{} của [{}] → Ca bù #{} tạo mới",
                    currentUser.getUsername(), saved.getId(), saved.getStudent().getFullName(), mr.getId());
        } else {
            log.info("Giáo viên [{}] {} đơn #{} của [{}] — Ghi chú: {}",
                    currentUser.getUsername(), decision, saved.getId(),
                    saved.getStudent().getFullName(), saved.getReviewNote());
        }

        return attendanceMakeupMapper.toAbsenceResponse(saved, mr);
    }

    /** Ánh xạ ReviewDecision → AbsenceStatus */
    private AbsenceStatus mapDecisionToStatus(ReviewDecision decision) {
        return switch (decision) {
            case APPROVED -> AbsenceStatus.APPROVED;
            case EXCUSED  -> AbsenceStatus.EXCUSED;
            case REJECTED -> AbsenceStatus.REJECTED;
        };
    }

    // =========================================================================
    // GIAI ĐOẠN 3: NHÂN VIÊN/ADMIN XẾP LỊCH BÙ
    // =========================================================================

    /** Danh sách yêu cầu học bù — Phân quyền theo Role */
    @Override
    public List<MakeupRegistrationResponse> getMakeupRegistrations(MakeupStatus status, Long studentId) {
        User currentUser = currentUserService.getCurrentUser();
        List<MakeupRegistration> list;

        if (currentUser.getRole() == Role.PARENT) {
            list = (status != null)
                    ? makeupRegistrationRepository.findByStudentParentIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status)
                    : makeupRegistrationRepository.findByStudentParentIdOrderByCreatedAtDesc(currentUser.getId());
        } else if (currentUser.getRole() == Role.STUDENT) {
            Optional<Student> own = studentRepository.findByAccountId(currentUser.getId());
            if (own.isEmpty()) return List.of();
            list = status != null
                    ? makeupRegistrationRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(own.get().getId(), status)
                    : makeupRegistrationRepository.findByStudentIdOrderByCreatedAtDesc(own.get().getId());
        } else {
            list = (status != null)
                    ? makeupRegistrationRepository.findByStatusOrderByCreatedAtDesc(status)
                    : makeupRegistrationRepository.findAllByOrderByCreatedAtDesc();
        }

        return list.stream()
                .filter(mr -> studentId == null || (mr.getStudent() != null && mr.getStudent().getId().equals(studentId)))
                .map(attendanceMakeupMapper::toMakeupResponse)
                .toList();
    }

    /** Chi tiết yêu cầu học bù — Phụ huynh chỉ xem ca bù của con mình */
    @Override
    public MakeupRegistrationResponse getMakeupRegistrationById(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        MakeupRegistration mr = makeupRegistrationRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));

        validateStudentAccess(currentUser, mr.getStudent());

        if (currentUser.getRole() == Role.PARENT) {
            if (mr.getStudent().getParent() == null
                    || !mr.getStudent().getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn không có quyền truy cập vào ca học bù này!");
            }
        }

        return attendanceMakeupMapper.toMakeupResponse(mr);
    }

    /**
     * Xếp lịch học bù — Giai đoạn 3 (STAFF / ADMIN).
     * Chọn targetLesson → chuyển trạng thái sang SCHEDULED.
     */
    @Override
    @Transactional
    public MakeupRegistrationResponse scheduleMakeup(Long id, MakeupScheduleRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateStaffOrAdmin(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));

        if (mr.getStatus() == MakeupStatus.COMPLETED) {
            throw new AttendanceMakeupException("Ca học bù đã hoàn thành, không thể xếp lại lịch!");
        }
        if (mr.getStatus() == MakeupStatus.CANCELLED) {
            throw new AttendanceMakeupException("Ca học bù đã bị hủy, không thể xếp lịch!");
        }
        if (mr.getStatus() == MakeupStatus.DEFERRED) {
            throw new AttendanceMakeupException("Ca học bù đã được chuyển sang khóa học kỳ sau, không thể xếp lịch!");
        }

        Lesson targetLesson = attendanceLessonRepository.findById(request.getTargetLessonId())
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy buổi học với ID: " + request.getTargetLessonId()));

        // Không cho xếp lịch bù trùng chính buổi đã xin nghỉ
        if (mr.getOriginalLesson() != null && mr.getOriginalLesson().getId().equals(targetLesson.getId())) {
            throw new AttendanceMakeupException("Buổi học bù không được trùng với chính buổi học đã xin nghỉ!");
        }

        validateMakeupTarget(mr, targetLesson);

        mr.setTargetLesson(targetLesson);
        mr.setStatus(MakeupStatus.SCHEDULED);
        if (request.getNote() != null && !request.getNote().isBlank()) {
            mr.setNote(request.getNote().trim());
        }
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        log.info("Ca bù #{} xếp lịch → buổi [{}] ngày {} bởi [{}]",
                saved.getId(), targetLesson.getTitle(), targetLesson.getLessonDate(), currentUser.getUsername());

        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    /**
     * Hủy ca học bù — Giai đoạn 3 (STAFF / ADMIN).
     * Chỉ hủy được ca PENDING hoặc SCHEDULED, không hủy ca COMPLETED.
     */
    @Override
    @Transactional
    public MakeupRegistrationResponse cancelMakeup(Long id, MakeupCancelRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateStaffOrAdmin(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository
                .findByIdAndStatusIn(id, List.of(MakeupStatus.PENDING, MakeupStatus.SCHEDULED,
                        MakeupStatus.REGISTERED, MakeupStatus.NO_SLOT))
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy ca học bù có thể hủy với ID: " + id
                        + " (Chỉ hủy được ca ở trạng thái PENDING, SCHEDULED hoặc NO_SLOT)"));

        mr.setStatus(MakeupStatus.CANCELLED);
        String cancelNote = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? "HỦY: " + request.getReason().trim()
                : "Hủy bởi " + currentUser.getUsername();
        mr.setNote(cancelNote);
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        log.info("Ca bù #{} của học viên [{}] đã bị hủy bởi [{}] — Lý do: {}",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername(), cancelNote);

        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    /**
     * Không tìm được lớp bù phù hợp — Giai đoạn 3 (STAFF / ADMIN).
     * Ca bù chuyển sang NO_SLOT; nội dung thông báo hiển thị ngay trên đơn của phụ huynh
     * để phụ huynh biết cần tự sắp xếp cho con đi học hoặc chờ chuyển kỳ sau.
     */
    @Override
    @Transactional
    public MakeupRegistrationResponse markNoSlotAvailable(Long id, MakeupResolveRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateStaffOrAdmin(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository
                .findByIdAndStatusIn(id, List.of(MakeupStatus.PENDING, MakeupStatus.NO_SLOT))
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Chỉ báo 'chưa có lớp phù hợp' cho ca bù đang chờ xếp lịch (ID: " + id + ")"));

        String message = requireResolveNote(request,
                "Vui lòng nhập nội dung thông báo gửi phụ huynh (ví dụ: buổi quan trọng, đề nghị cho bé đi học nếu sắp xếp được).");

        mr.setStatus(MakeupStatus.NO_SLOT);
        mr.setNote("CHƯA CÓ LỚP PHÙ HỢP: " + message);
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        log.info("Ca bù #{} của [{}] — chưa có lớp phù hợp, đã báo phụ huynh bởi [{}]",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername());
        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    /**
     * Chuyển học viên sang khóa học kỳ sau — Giai đoạn 3 (STAFF / ADMIN).
     * Dùng khi buổi nghỉ quan trọng, không có lớp bù và phụ huynh không thể cho con đi học.
     */
    @Override
    @Transactional
    public MakeupRegistrationResponse deferToNextTerm(Long id, MakeupResolveRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateStaffOrAdmin(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository
                .findByIdAndStatusIn(id, List.of(MakeupStatus.PENDING, MakeupStatus.NO_SLOT))
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Chỉ chuyển kỳ sau cho ca bù đang chờ xếp lịch hoặc chưa có lớp phù hợp (ID: " + id + ")"));

        String message = requireResolveNote(request,
                "Vui lòng nhập nội dung thông báo gửi phụ huynh về việc chuyển sang khóa học kỳ sau.");

        mr.setStatus(MakeupStatus.DEFERRED);
        mr.setNote("CHUYỂN KHÓA KỲ SAU: " + message);
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        log.info("Ca bù #{} của [{}] — chuyển sang khóa học kỳ sau bởi [{}]",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername());
        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    private String requireResolveNote(MakeupResolveRequest request, String errorMessage) {
        if (request == null || request.getNote() == null || request.getNote().isBlank()) {
            throw new AttendanceMakeupException(errorMessage);
        }
        return request.getNote().trim();
    }

    /**
     * Kiểm tra ca học đích hợp lệ: chưa diễn ra, lớp còn chỗ, học viên chưa thuộc lớp đó, cùng trình độ.
     */
    private void validateMakeupTarget(MakeupRegistration mr, Lesson target) {
        if (target.getStatus() == com.talent.management.shared.enums.LessonStatus.CANCELLED) {
            throw new AttendanceMakeupException("Buổi học được chọn đã bị hủy!");
        }
        if (target.getLessonDate() != null && target.getLessonDate().isBefore(LocalDate.now())) {
            throw new AttendanceMakeupException("Không thể xếp học bù vào buổi học đã diễn ra!");
        }

        ClassEntity targetClass = target.getClassEntity();
        if (targetClass == null) {
            return;
        }

        // Cùng trình độ (level) với khóa học của buổi gốc
        Lesson original = mr.getOriginalLesson();
        String originalLevel = (original != null && original.getClassEntity() != null
                && original.getClassEntity().getCourse() != null)
                ? original.getClassEntity().getCourse().getLevel() : null;
        String targetLevel = targetClass.getCourse() != null ? targetClass.getCourse().getLevel() : null;
        if (originalLevel != null && !originalLevel.isBlank() && targetLevel != null && !targetLevel.isBlank()
                && !originalLevel.equalsIgnoreCase(targetLevel)) {
            throw new AttendanceMakeupException(
                    "Lớp bù khác trình độ (" + targetLevel + " ≠ " + originalLevel + "). Vui lòng chọn lớp cùng level!");
        }

        // Học viên không được thuộc sẵn lớp đích
        Set<Long> enrolledClassIds = new HashSet<>(attendanceEnrollmentRepository
                .findClassIdsByStudentIdAndStatusIn(mr.getStudent().getId(),
                        List.of(com.talent.management.shared.enums.EnrollmentStatus.ENROLLED,
                                com.talent.management.shared.enums.EnrollmentStatus.PENDING_PAYMENT)));
        if (enrolledClassIds.contains(targetClass.getId())) {
            throw new AttendanceMakeupException("Học viên đã thuộc lớp này, hãy chọn lớp khác để học bù!");
        }

        // Sĩ số: sĩ số lớp + học viên bù đã xếp (không tính chính ca đang xếp)
        int base = targetClass.getCurrentStudents() != null ? targetClass.getCurrentStudents() : 0;
        long makeupSeats = makeupRegistrationRepository
                .countByTargetLessonIdAndStatusIn(target.getId(), SEAT_HOLDING_STATUSES);
        boolean alreadyCounted = mr.getTargetLesson() != null
                && mr.getTargetLesson().getId().equals(target.getId())
                && SEAT_HOLDING_STATUSES.contains(mr.getStatus());
        long occupied = base + makeupSeats - (alreadyCounted ? 1 : 0);
        Integer max = targetClass.getMaxStudents();
        if (max != null && occupied >= max) {
            throw new AttendanceMakeupException("Lớp " + targetClass.getClassName() + " đã đủ sĩ số ("
                    + occupied + "/" + max + "), không thể thêm học viên học bù!");
        }
    }

    // =========================================================================
    // GIAI ĐOẠN 4: GIÁO VIÊN ĐIỂM DANH & HOÀN THÀNH
    // =========================================================================

    /**
     * Điểm danh buổi học — Giai đoạn 4 (TEACHER / STAFF / ADMIN).
     * Nếu status = PRESENT và có MakeupRegistration{SCHEDULED} trỏ vào lesson này → auto COMPLETED.
     */
    @Override
    @Transactional
    public void markAttendance(AttendanceMarkRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateMarkPermission(currentUser);

        Lesson lesson = attendanceLessonRepository.findById(request.getLessonId())
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy buổi học với ID: " + request.getLessonId()));

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy học viên với ID: " + request.getStudentId()));

        // Parse AttendanceStatus (mặc định PRESENT nếu không hợp lệ)
        AttendanceStatus attendanceStatus = AttendanceStatus.PRESENT;
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                attendanceStatus = AttendanceStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Giữ PRESENT mặc định
            }
        }

        // Lưu bản ghi điểm danh (tạo mới hoặc cập nhật)
        Attendance attendance = attendanceRecordRepository
                .findByLessonIdAndStudentId(lesson.getId(), student.getId())
                .orElse(Attendance.builder().lesson(lesson).student(student).build());

        attendance.setStatus(attendanceStatus);
        attendance.setMarkedAt(LocalDateTime.now());
        if (request.getNote() != null && !request.getNote().isBlank()) {
            attendance.setNote(request.getNote().trim());
        }
        attendanceRecordRepository.save(attendance);

        // Nếu PRESENT → kiểm tra và tự động hoàn thành ca học bù liên quan
        if (attendanceStatus == AttendanceStatus.PRESENT) {
            makeupRegistrationRepository
                    .findFirstByTargetLessonIdAndStudentIdAndStatus(
                            lesson.getId(), student.getId(), MakeupStatus.SCHEDULED)
                    .ifPresent(mr -> {
                        mr.setStatus(MakeupStatus.COMPLETED);
                        if (request.getNote() != null && !request.getNote().isBlank()) {
                            mr.setNote(request.getNote().trim());
                        }
                        makeupRegistrationRepository.save(mr);
                        log.info("Ca học bù #{} tự động COMPLETED sau điểm danh PRESENT của học viên [{}]",
                                mr.getId(), student.getFullName());
                    });
        }
    }

    /**
     * Hoàn thành ca học bù thủ công qua ID — Giai đoạn 4 (TEACHER / STAFF / ADMIN).
     * Dùng khi không cần điểm danh tự động.
     */
    @Override
    @Transactional
    public MakeupRegistrationResponse completeMakeup(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        validateMarkPermission(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));

        if (mr.getStatus() != MakeupStatus.SCHEDULED) {
            throw new AttendanceMakeupException(
                    "Chỉ có thể hoàn thành ca học bù đã được xếp lịch (SCHEDULED). Trạng thái hiện tại: " + mr.getStatus());
        }

        if (mr.getTargetLesson() == null) {
            throw new AttendanceMakeupException("Ca học bù này chưa có buổi học bù cụ thể!");
        }

        mr.setStatus(MakeupStatus.COMPLETED);
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        // Lưu bản ghi điểm danh PRESENT
        Attendance attendance = attendanceRecordRepository
                .findByLessonIdAndStudentId(mr.getTargetLesson().getId(), mr.getStudent().getId())
                .orElse(Attendance.builder()
                        .lesson(mr.getTargetLesson())
                        .student(mr.getStudent())
                        .build());
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setMarkedAt(LocalDateTime.now());
        attendance.setNote("Hoàn thành học bù thủ công (Ca bù #" + mr.getId() + ")");
        attendanceRecordRepository.save(attendance);

        log.info("Ca học bù #{} của học viên [{}] hoàn thành thủ công bởi [{}]",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername());

        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    // =========================================================================
    // HELPER APIs HỖ TRỢ GIAO DIỆN
    // =========================================================================

    /** Danh sách học viên — PARENT lấy con mình, các role khác lấy tất cả */
    @Override
    public List<StudentOptionResponse> getSelectableStudents() {
        User currentUser = currentUserService.getCurrentUser();
        List<Student> students = (currentUser.getRole() == Role.PARENT)
                ? studentRepository.findByParentId(currentUser.getId())
                : currentUser.getRole() == Role.STUDENT
                ? studentRepository.findByAccountId(currentUser.getId()).stream().toList()
                : studentRepository.findAll();

        return students.stream().map(s -> {
            List<String> classNames = attendanceLessonRepository.findClassNamesByStudentId(s.getId());
            String currentClass = classNames.isEmpty() ? "Chưa xếp lớp" : String.join(", ", classNames);
            return attendanceMakeupMapper.toStudentOption(s, currentClass);
        }).toList();
    }

    /** Danh sách buổi học của học viên — Phụ huynh chỉ xem con mình */
    @Override
    public List<LessonOptionResponse> getLessonsForAbsenceRequest(Long studentId) {
        User currentUser = currentUserService.getCurrentUser();
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên với ID: " + studentId));

        validateStudentAccess(currentUser, student);

        if (currentUser.getRole() == Role.PARENT) {
            if (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn chỉ có thể xem lịch học của con mình!");
            }
        }

        LocalDate today = LocalDate.now();
        boolean onlyUpcoming = currentUser.getRole() == Role.PARENT || currentUser.getRole() == Role.STUDENT;
        return attendanceLessonRepository.findLessonsByStudentId(studentId)
                .stream()
                .filter(l -> !onlyUpcoming || (l.getLessonDate() != null && !l.getLessonDate().isBefore(today)))
                .map(attendanceMakeupMapper::toLessonOption)
                .toList();
    }

    /**
     * Danh sách ca học khả dụng để xếp lịch bù — lọc thông minh:
     *  - Cùng chương trình + cùng level (hoặc cùng khóa học nếu khóa chưa có level)
     *  - Cùng số thứ tự buổi với buổi nghỉ (lớp đích chưa học tới buổi đó → không học lặp/nhảy cóc)
     *  - Diễn ra từ hiện tại trở đi, lớp khác lớp gốc, học viên chưa thuộc lớp đó
     *  - Lớp còn chỗ (sĩ số lớp + học viên bù đã xếp < sĩ số tối đa)
     * Không còn fallback "lấy mọi ca sắp tới": nếu rỗng, nhân viên sẽ báo phụ huynh / chuyển kỳ sau.
     */
    @Override
    public List<LessonOptionResponse> getAvailableLessonsForMakeup(Long makeupId) {
        MakeupRegistration mr = makeupRegistrationRepository.findById(makeupId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + makeupId));

        validateStudentAccess(currentUserService.getCurrentUser(), mr.getStudent());

        Lesson original = mr.getOriginalLesson();
        if (original == null || original.getClassEntity() == null
                || original.getClassEntity().getCourse() == null || original.getSessionNumber() == null) {
            return List.of();
        }

        ClassEntity originalClass = original.getClassEntity();
        Course course = originalClass.getCourse();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<Lesson> candidates;
        if (course.getLevel() != null && !course.getLevel().isBlank() && course.getProgram() != null) {
            candidates = attendanceLessonRepository.findMakeupCandidatesByLevel(
                    today, original.getSessionNumber(), originalClass.getId(),
                    course.getProgram().getId(), course.getLevel());
        } else {
            candidates = attendanceLessonRepository.findMakeupCandidatesByCourse(
                    today, original.getSessionNumber(), originalClass.getId(), course.getId());
        }

        Set<Long> enrolledClassIds = new HashSet<>(attendanceEnrollmentRepository
                .findClassIdsByStudentIdAndStatusIn(mr.getStudent().getId(),
                        List.of(com.talent.management.shared.enums.EnrollmentStatus.ENROLLED,
                                com.talent.management.shared.enums.EnrollmentStatus.PENDING_PAYMENT)));

        List<Lesson> usable = new ArrayList<>();
        for (Lesson l : candidates) {
            if (l.getStatus() == com.talent.management.shared.enums.LessonStatus.CANCELLED) continue;
            if (l.getClassEntity() == null || enrolledClassIds.contains(l.getClassEntity().getId())) continue;
            if (l.getLessonDate().isEqual(today) && l.getEndTime() != null && l.getEndTime().isBefore(now)) continue;
            usable.add(l);
        }
        if (usable.isEmpty()) {
            return List.of();
        }

        // Batch: đếm học viên bù đang chiếm chỗ ở từng buổi (tránh N+1)
        List<Long> lessonIds = usable.stream().map(Lesson::getId).toList();
        Map<Long, Long> makeupSeatMap = makeupRegistrationRepository
                .findByTargetLessonIdInAndStatusIn(lessonIds, SEAT_HOLDING_STATUSES)
                .stream()
                .collect(Collectors.groupingBy(m -> m.getTargetLesson().getId(), Collectors.counting()));

        List<LessonOptionResponse> result = new ArrayList<>();
        for (Lesson l : usable) {
            int base = l.getClassEntity().getCurrentStudents() != null ? l.getClassEntity().getCurrentStudents() : 0;
            int occupied = base + makeupSeatMap.getOrDefault(l.getId(), 0L).intValue();
            Integer max = l.getClassEntity().getMaxStudents();
            if (max != null && occupied >= max) continue;   // lớp đầy
            result.add(attendanceMakeupMapper.toLessonOption(l, occupied));
        }
        return result;
    }

    // =========================================================================
    // HÀM TIỆN ÍCH KIỂM TRA PHÂN QUYỀN
    // =========================================================================

    /** TEACHER, STAFF và ADMIN được duyệt đơn nghỉ */
    private void validateStudentAccess(User user, Student student) {
        boolean denied = user.getRole() == Role.STUDENT
                ? student.getAccount() == null || !student.getAccount().getId().equals(user.getId())
                : user.getRole() == Role.PARENT && (student.getParent() == null || !student.getParent().getId().equals(user.getId()));
        if (denied) throw new AttendanceMakeupException("Bạn không có quyền truy cập hồ sơ học viên này");
    }

    private void validateReviewPermission(User user) {
        if (user.getRole() == Role.PARENT || user.getRole() == Role.STUDENT) {
            throw new AttendanceMakeupException(
                    "Tài khoản Phụ huynh/Học viên không có quyền xét duyệt đơn xin nghỉ học!");
        }
    }

    /** STAFF và ADMIN được xếp/hủy lịch bù */
    private void validateStaffOrAdmin(User user) {
        if (user.getRole() == Role.PARENT || user.getRole() == Role.STUDENT
                || user.getRole() == Role.TEACHER) {
            throw new AttendanceMakeupException(
                    "Chỉ Nhân viên (STAFF) hoặc Admin mới có quyền xếp/hủy lịch học bù!");
        }
    }

    /** TEACHER, STAFF và ADMIN được điểm danh */
    private void validateMarkPermission(User user) {
        if (user.getRole() == Role.PARENT || user.getRole() == Role.STUDENT) {
            throw new AttendanceMakeupException(
                    "Tài khoản Phụ huynh/Học viên không có quyền điểm danh!");
        }
    }
}
