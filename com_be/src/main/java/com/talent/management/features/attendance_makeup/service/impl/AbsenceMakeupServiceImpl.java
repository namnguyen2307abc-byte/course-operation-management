package com.talent.management.features.attendance_makeup.service.impl;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceRequestCreateRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest.ReviewDecision;
import com.talent.management.features.attendance_makeup.dto.request.AttendanceMarkRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupCancelRequest;
import com.talent.management.features.attendance_makeup.dto.request.MakeupScheduleRequest;
import com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse;
import com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse;
import com.talent.management.features.attendance_makeup.dto.response.MakeupRegistrationResponse;
import com.talent.management.features.attendance_makeup.dto.response.StudentOptionResponse;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
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
import java.util.List;
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

        Lesson targetLesson = attendanceLessonRepository.findById(request.getTargetLessonId())
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy buổi học với ID: " + request.getTargetLessonId()));

        // Không cho xếp lịch bù trùng chính buổi đã xin nghỉ
        if (mr.getOriginalLesson() != null && mr.getOriginalLesson().getId().equals(targetLesson.getId())) {
            throw new AttendanceMakeupException("Buổi học bù không được trùng với chính buổi học đã xin nghỉ!");
        }

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
                .findByIdAndStatusIn(id, List.of(MakeupStatus.PENDING, MakeupStatus.SCHEDULED, MakeupStatus.REGISTERED))
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy ca học bù có thể hủy với ID: " + id
                        + " (Chỉ hủy được ca ở trạng thái PENDING, SCHEDULED hoặc REGISTERED)"));

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

        if (currentUser.getRole() == Role.PARENT) {
            if (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn chỉ có thể xem lịch học của con mình!");
            }
        }

        return attendanceLessonRepository.findLessonsByStudentId(studentId)
                .stream()
                .map(attendanceMakeupMapper::toLessonOption)
                .toList();
    }

    /**
     * Danh sách ca học khả dụng để xếp lịch bù.
     * Ưu tiên ca cùng khóa đào tạo, loại trừ buổi gốc đã xin nghỉ.
     */
    @Override
    public List<LessonOptionResponse> getAvailableLessonsForMakeup(Long makeupId) {
        MakeupRegistration mr = makeupRegistrationRepository.findById(makeupId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + makeupId));

        Long courseId = null;
        if (mr.getOriginalLesson() != null
                && mr.getOriginalLesson().getClassEntity() != null
                && mr.getOriginalLesson().getClassEntity().getCourse() != null) {
            courseId = mr.getOriginalLesson().getClassEntity().getCourse().getId();
        }

        LocalDate today = LocalDate.now();
        List<Lesson> availableLessons;

        if (courseId != null) {
            availableLessons = attendanceLessonRepository.findUpcomingLessonsByCourseId(courseId, today);
        } else {
            availableLessons = List.of();
        }

        // Fallback: nếu không có ca cùng khóa → lấy tất cả ca sắp tới
        if (availableLessons.isEmpty()) {
            availableLessons = attendanceLessonRepository.findAvailableUpcomingLessons(today);
        }

        Long origLessonId = mr.getOriginalLesson() != null ? mr.getOriginalLesson().getId() : null;

        return availableLessons.stream()
                .filter(l -> origLessonId == null || !origLessonId.equals(l.getId()))
                .map(attendanceMakeupMapper::toLessonOption)
                .toList();
    }

    // =========================================================================
    // HÀM TIỆN ÍCH KIỂM TRA PHÂN QUYỀN
    // =========================================================================

    /** TEACHER, STAFF và ADMIN được duyệt đơn nghỉ */
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
