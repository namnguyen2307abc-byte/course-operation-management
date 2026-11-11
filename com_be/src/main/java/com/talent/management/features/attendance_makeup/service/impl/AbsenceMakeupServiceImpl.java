package com.talent.management.features.attendance_makeup.service.impl;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceRequestCreateRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest;
import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest.ReviewDecision;
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
import com.talent.management.features.weekly_schedule.repository.AttendanceEnrollmentRepository;
import com.talent.management.features.weekly_schedule.repository.AttendanceLessonRepository;
import com.talent.management.features.weekly_schedule.repository.AttendanceRecordRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation nghiệp vụ Phân hệ Nghỉ học & Học bù — Luồng Mới (4 giai đoạn).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbsenceMakeupServiceImpl implements AbsenceMakeupService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AbsenceRequestRepository absenceRequestRepository;
    private final MakeupRegistrationRepository makeupRegistrationRepository;
    private final AttendanceLessonRepository attendanceLessonRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceEnrollmentRepository attendanceEnrollmentRepository;

    /** Trạng thái ca bù đang chiếm chỗ trong lớp đích */
    private static final List<MakeupStatus> SEAT_HOLDING_STATUSES =
            List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);
    private final StudentRepository studentRepository;
    private final AttendanceMakeupMapper attendanceMakeupMapper;
    private final CurrentUserService currentUserService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.talent.management.features.notification.service.NotificationService notificationService;

    // =========================================================================
    // GIAI ĐOẠN 1: TẠO VÀ QUẢN LÝ YÊU CẦU NGHỈ HỌC
    // =========================================================================

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

        if (notificationService != null) {
            User lessonTeacher = resolvePrimaryTeacher(lesson);
            String className = lesson.getClassEntity() != null ? lesson.getClassEntity().getClassName() : "";
            String dateStr = lesson.getLessonDate() != null ? lesson.getLessonDate().toString() : "";
            String absenceUrl = "/pages/absence-makeup.html?tab=ABSENCE&absenceId=" + saved.getId();
            if (lessonTeacher != null && !lessonTeacher.getId().equals(currentUser.getId())) {
                notificationService.sendNotification(
                        lessonTeacher, currentUser,
                        "Đơn xin nghỉ mới: " + student.getFullName(),
                        student.getFullName() + " (" + className + ") xin nghỉ ngày " + dateStr + ". Lý do: " + saved.getReason(),
                        "ABSENCE_CREATED", saved.getId(), absenceUrl);
            }
            notificationService.sendToRoles(
                    List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER), currentUser,
                    "Đơn xin nghỉ mới chờ duyệt",
                    "Phụ huynh gửi đơn xin nghỉ cho " + student.getFullName() + " (" + className + ", ngày " + dateStr + ").",
                    "ABSENCE_CREATED", saved.getId(), absenceUrl);
        }
        return attendanceMakeupMapper.toAbsenceResponse(saved, null);
    }

    @Override
    @Transactional
    public void cancelAbsenceRequest(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        AbsenceRequest ar = absenceRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        validateStudentAccess(currentUser, ar.getStudent());

        if (ar.getStatus() != AbsenceStatus.PENDING) {
            throw new AttendanceMakeupException("Chỉ có thể hủy đơn khi đang ở trạng thái Chờ duyệt.");
        }

        Student student = ar.getStudent();
        Lesson lesson = ar.getLesson();
        String studentName = student != null ? student.getFullName() : "Học viên";
        String className = lesson != null && lesson.getClassEntity() != null ? lesson.getClassEntity().getClassName() : "";
        String dateStr = lesson != null && lesson.getLessonDate() != null ? lesson.getLessonDate().toString() : "";
        User primaryTeacher = resolvePrimaryTeacher(lesson);

        absenceRequestRepository.delete(ar);
        log.info("Đơn xin nghỉ #{} của [{}] đã bị hủy bởi [{}]", id, studentName, currentUser.getUsername());

        if (notificationService != null) {
            String absenceListUrl = "/pages/absence-makeup.html?tab=ABSENCE";
            if (primaryTeacher != null && !primaryTeacher.getId().equals(currentUser.getId())) {
                notificationService.sendNotification(
                        primaryTeacher, currentUser,
                        "Phụ huynh đã hủy đơn xin nghỉ",
                        "Đơn xin nghỉ ngày " + dateStr + " của " + studentName + " (" + className + ") đã được phụ huynh hủy.",
                        "ABSENCE_CANCELLED", id, absenceListUrl);
            }
            notificationService.sendToRoles(
                    List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER), currentUser,
                    "Đơn xin nghỉ #" + id + " đã hủy",
                    "Phụ huynh đã hủy đơn xin nghỉ ngày " + dateStr + " của " + studentName + " (" + className + ").",
                    "ABSENCE_CANCELLED", id, absenceListUrl);
        }
    }

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
                list = (status != null)
                        ? absenceRequestRepository.findByStudentParentIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status)
                        : absenceRequestRepository.findByStudentParentIdOrderByCreatedAtDesc(currentUser.getId());
            }
            case TEACHER -> {
                // Giáo viên chỉ xem các đơn nghỉ thuộc môn và buổi học do chính mình phụ trách
                list = (status != null)
                        ? absenceRequestRepository.findByTeacherIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status)
                        : absenceRequestRepository.findByTeacherIdOrderByCreatedAtDesc(currentUser.getId());
            }
            default -> {
                // STAFF, ADMIN, BRANCH_MANAGER: đều nhìn thấy toàn bộ đơn xin nghỉ
                list = (status != null)
                        ? absenceRequestRepository.findByStatusOrderByCreatedAtDesc(status)
                        : absenceRequestRepository.findAllByOrderByCreatedAtDesc();
            }
        }

        List<AbsenceRequest> filtered = list.stream()
                .filter(ar -> studentId == null || (ar.getStudent() != null && ar.getStudent().getId().equals(studentId)))
                .filter(ar -> classId == null || (ar.getLesson() != null
                        && ar.getLesson().getClassEntity() != null
                        && ar.getLesson().getClassEntity().getId().equals(classId)))
                .sorted(Comparator
                        .comparingInt((AbsenceRequest ar) -> ar.getStatus() == AbsenceStatus.PENDING ? 0 : 1)
                        .thenComparing(AbsenceRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AbsenceRequest::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        if (filtered.isEmpty()) {
            return List.of();
        }

        List<Long> arIds = filtered.stream().map(AbsenceRequest::getId).toList();
        Map<Long, MakeupRegistration> makeupMap = makeupRegistrationRepository
                .findByAbsenceRequestIdIn(arIds)
                .stream()
                .filter(mr -> mr.getAbsenceRequest() != null)
                .collect(Collectors.toMap(
                        mr -> mr.getAbsenceRequest().getId(),
                        Function.identity(),
                        (m1, m2) -> m1));

        return filtered.stream()
                .map(ar -> attendanceMakeupMapper.toAbsenceResponse(ar, makeupMap.get(ar.getId())))
                .toList();
    }

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
        } else if (currentUser.getRole() == Role.TEACHER) {
            if (!isTeacherOfLesson(currentUser, ar.getLesson())) {
                throw new AttendanceMakeupException("Chỉ giáo viên phụ trách môn và buổi học này mới có quyền xem đơn xin nghỉ!");
            }
        }

        MakeupRegistration mr = makeupRegistrationRepository.findByAbsenceRequestId(ar.getId()).orElse(null);
        return attendanceMakeupMapper.toAbsenceResponse(ar, mr);
    }

    // =========================================================================
    // GIAI ĐOẠN 2: GIÁO VIÊN XÉT DUYỆT
    // =========================================================================

    @Override
    @Transactional
    public AbsenceRequestResponse reviewAbsenceRequest(Long id, AbsenceReviewRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateReviewPermission(currentUser);

        AbsenceRequest ar = absenceRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        // Kiểm tra giáo viên: Chỉ giáo viên phụ trách môn và buổi học hôm đó mới được duyệt đơn
        if (currentUser.getRole() == Role.TEACHER) {
            if (!isTeacherOfLesson(currentUser, ar.getLesson())) {
                throw new AttendanceMakeupException("Chỉ giáo viên phụ trách môn và buổi học hôm đó mới có quyền xét duyệt đơn xin nghỉ này!");
            }
        }

        // Guard: chỉ xử lý đơn PENDING
        if (ar.getStatus() != AbsenceStatus.PENDING) {
            throw new AttendanceMakeupException(
                    "Đơn đã được xử lý trước đó (trạng thái hiện tại: " + ar.getStatus() + ")");
        }

        ReviewDecision decision = request.getDecision();
        boolean noteIsBlank = request.getReviewNote() == null || request.getReviewNote().isBlank();

        if (decision == ReviewDecision.REJECTED && noteIsBlank) {
            throw new AttendanceMakeupException("Vui lòng nhập lý do từ chối (reviewNote) để thông báo rõ ràng tới phụ huynh.");
        }

        ar.setStatus(mapDecisionToStatus(decision));
        ar.setApprovedByTeacher(currentUser);
        ar.setReviewNote(request.getReviewNote() != null ? request.getReviewNote().trim() : null);
        ar.setReviewedAt(LocalDateTime.now());
        AbsenceRequest saved = absenceRequestRepository.save(ar);

        MakeupRegistration mr = null;
        if (decision == ReviewDecision.APPROVED) {
            mr = makeupRegistrationRepository.findByAbsenceRequestId(saved.getId())
                    .orElseGet(() -> MakeupRegistration.builder()
                            .absenceRequest(saved)
                            .student(saved.getStudent())
                            .originalLesson(saved.getLesson())
                            .targetLesson(null)
                            .status(MakeupStatus.PENDING)
                            .note(null)
                            .createdAt(LocalDateTime.now())
                            .build());
            mr = makeupRegistrationRepository.save(mr);
            log.info("[{}] APPROVED đơn #{} của [{}] → Ca bù #{} tạo mới",
                    currentUser.getUsername(), saved.getId(), saved.getStudent().getFullName(), mr.getId());
        } else {
            log.info("[{}] {} đơn #{} của [{}] — Ghi chú: {}",
                    currentUser.getUsername(), decision, saved.getId(),
                    saved.getStudent().getFullName(), saved.getReviewNote());
        }

        if (notificationService != null && saved.getStudent() != null) {
            User parentUser = saved.getStudent().getParent();
            String studentName = saved.getStudent().getFullName();
            String dateStr = saved.getLesson() != null && saved.getLesson().getLessonDate() != null
                    ? saved.getLesson().getLessonDate().toString() : "";
            String className = saved.getLesson() != null && saved.getLesson().getClassEntity() != null
                    ? saved.getLesson().getClassEntity().getClassName() : "";

            String absenceUrl = "/pages/absence-makeup.html?tab=ABSENCE&absenceId=" + saved.getId();
            Long makeupId = mr != null ? mr.getId() : saved.getId();
            String makeupUrl = mr != null
                    ? "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + mr.getId()
                    : absenceUrl;

            if (decision == ReviewDecision.APPROVED) {
                if (parentUser != null) {
                    notificationService.sendNotification(
                            parentUser, currentUser,
                            "Đơn xin nghỉ đã được phê duyệt",
                            "Đơn xin nghỉ buổi ngày " + dateStr + " của bé " + studentName + " đã được duyệt. Bộ phận học vụ sẽ tìm ca phù hợp để xếp lịch học bù.",
                            "ABSENCE_APPROVED", saved.getId(), absenceUrl);
                }
                notificationService.sendToRoles(
                        List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER), currentUser,
                        "Cần xếp lịch học bù mới",
                        "Đơn nghỉ #" + saved.getId() + " của học viên " + studentName + " (lớp " + className + ", ngày " + dateStr + ") đã được duyệt, vui lòng tìm ca phù hợp để xếp học bù.",
                        "MAKEUP_PENDING", makeupId, makeupUrl);
            } else if (decision == ReviewDecision.REJECTED) {
                if (parentUser != null) {
                    notificationService.sendNotification(
                            parentUser, currentUser,
                            "Đơn xin nghỉ bị từ chối",
                            "Đơn xin nghỉ buổi ngày " + dateStr + " của bé " + studentName + " đã bị từ chối. Lý do: " + (saved.getReviewNote() != null ? saved.getReviewNote() : ""),
                            "ABSENCE_REJECTED", saved.getId(), absenceUrl);
                }
            }

            // Nếu Staff/Admin/BranchManager duyệt thay trong tình huống khẩn cấp hoặc GV bận -> thông báo lại cho GV phụ trách
            User primaryTeacher = resolvePrimaryTeacher(saved.getLesson());
            if (currentUser.getRole() != Role.TEACHER
                    && primaryTeacher != null
                    && !primaryTeacher.getId().equals(currentUser.getId())) {
                String statusAction = decision == ReviewDecision.APPROVED ? "đã duyệt (chờ xếp bù)" : "đã từ chối";
                notificationService.sendNotification(
                        primaryTeacher, currentUser,
                        "Đơn xin nghỉ lớp phụ trách đã được xử lý thay",
                        String.format("%s đã thay mặt bạn %s đơn nghỉ #%d của học viên %s (lớp %s, ngày %s).",
                                currentUser.getFullName(), statusAction, saved.getId(), studentName, className, dateStr),
                        decision == ReviewDecision.APPROVED ? "ABSENCE_APPROVED" : "ABSENCE_REJECTED",
                        saved.getId(), absenceUrl);
            }
        }
        return attendanceMakeupMapper.toAbsenceResponse(saved, mr);
    }

    private AbsenceStatus mapDecisionToStatus(ReviewDecision decision) {
        return switch (decision) {
            case APPROVED -> AbsenceStatus.APPROVED;
            case REJECTED -> AbsenceStatus.REJECTED;
        };
    }

    // =========================================================================
    // GIAI ĐOẠN 3: NHÂN VIÊN/ADMIN XẾP LỊCH BÙ (GIỚI HẠN TỐI ĐA 2 LẦN)
    // =========================================================================

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
        } else if (currentUser.getRole() == Role.TEACHER) {
            List<MakeupRegistration> all = (status != null)
                    ? makeupRegistrationRepository.findByStatusOrderByCreatedAtDesc(status)
                    : makeupRegistrationRepository.findAllByOrderByCreatedAtDesc();
            list = all.stream()
                    .filter(mr -> isTeacherOfLesson(currentUser, mr.getOriginalLesson())
                            || isTeacherOfLesson(currentUser, mr.getTargetLesson()))
                    .toList();
        } else {
            list = (status != null)
                    ? makeupRegistrationRepository.findByStatusOrderByCreatedAtDesc(status)
                    : makeupRegistrationRepository.findAllByOrderByCreatedAtDesc();
        }

        return list.stream()
                .filter(mr -> studentId == null || (mr.getStudent() != null && mr.getStudent().getId().equals(studentId)))
                .sorted(Comparator
                        .comparingInt((MakeupRegistration mr) -> makeupSortPriority(mr.getStatus()))
                        .thenComparing(MakeupRegistration::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MakeupRegistration::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(attendanceMakeupMapper::toMakeupResponse)
                .toList();
    }

    private int makeupSortPriority(MakeupStatus status) {
        if (status == null) return 99;
        return switch (status) {
            case PENDING -> 0;
            case NO_SLOT -> 1;
            case MISSED -> 2;
            case SCHEDULED -> 3;
            case COMPLETED -> 4;
            case CANCELLED -> 5;
        };
    }

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

        int currentRetry = AttendanceMakeupMapper.extractRetryCount(mr);
        if (AttendanceMakeupMapper.isLimitExceeded(mr) || currentRetry >= AttendanceMakeupMapper.MAX_MAKEUP_RETRIES) {
            throw new AttendanceMakeupException(
                    "Học viên đã sử dụng hết tối đa " + AttendanceMakeupMapper.MAX_MAKEUP_RETRIES
                            + "/" + AttendanceMakeupMapper.MAX_MAKEUP_RETRIES
                            + " lần xếp lịch học bù cho buổi nghỉ này theo quy định!");
        }

        Lesson targetLesson = attendanceLessonRepository.findById(request.getTargetLessonId())
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy buổi học với ID: " + request.getTargetLessonId()));

        if (mr.getOriginalLesson() != null && mr.getOriginalLesson().getId().equals(targetLesson.getId())) {
            throw new AttendanceMakeupException("Buổi học bù không được trùng với chính buổi học đã xin nghỉ!");
        }

        validateMakeupTarget(mr, targetLesson);

        int newRetry = currentRetry + 1;
        mr.setTargetLesson(targetLesson);
        mr.setStatus(MakeupStatus.SCHEDULED);

        String targetClassStr = targetLesson.getClassEntity() != null ? targetLesson.getClassEntity().getClassName() : "lớp bù";
        String defaultNote = "Xếp lịch học bù tại " + targetClassStr + " (Buổi #" + targetLesson.getSessionNumber() + ")";
        String rawBody = (request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : defaultNote;
        mr.setNote(AttendanceMakeupMapper.buildRetryNote(newRetry, false, rawBody));

        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        log.info("Ca bù #{} xếp lịch (Lần {}/{}) → buổi [{}] ngày {} bởi [{}]",
                saved.getId(), newRetry, AttendanceMakeupMapper.MAX_MAKEUP_RETRIES,
                targetLesson.getTitle(), targetLesson.getLessonDate(), currentUser.getUsername());

        if (notificationService != null) {
            Lesson origLesson = saved.getOriginalLesson();
            Student student = saved.getStudent();
            String studentName = (student != null) ? student.getFullName() : "Học viên";
            String targetDateStr = (targetLesson.getLessonDate() != null)
                    ? targetLesson.getLessonDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";
            String targetTimeStr = (targetLesson.getStartTime() != null && targetLesson.getEndTime() != null)
                    ? targetLesson.getStartTime().format(TIME_FMT) + " - " + targetLesson.getEndTime().format(TIME_FMT) : "";
            String targetClassName = (targetLesson.getClassEntity() != null)
                    ? targetLesson.getClassEntity().getClassName()
                    : (targetLesson.getTitle() != null ? targetLesson.getTitle() : "Lớp học bù");
            User resolvedTargetTeacher = resolvePrimaryTeacher(targetLesson);
            String targetTeacherName = (resolvedTargetTeacher != null)
                    ? resolvedTargetTeacher.getFullName() : "Giáo viên trung tâm";
            String targetRoomName = (targetLesson.getRoom() != null)
                    ? targetLesson.getRoom().getRoomName() : "Phòng học";
            String origClassName = (origLesson != null && origLesson.getClassEntity() != null)
                    ? origLesson.getClassEntity().getClassName() : "lớp phụ trách";
            String retryLabel = String.format(" (Lần xếp bù %d/%d)", newRetry, AttendanceMakeupMapper.MAX_MAKEUP_RETRIES);

            String makeupUrl = "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + saved.getId();
            User resolvedOrigTeacher = resolvePrimaryTeacher(origLesson);
            if (resolvedOrigTeacher != null) {
                String title = "Học viên điều phối học bù: " + studentName + retryLabel;
                String message = String.format("Học viên %s (lớp %s) đã được xếp lịch học bù%s sang lớp %s vào ngày %s (%s, Phòng %s, GV: %s).",
                        studentName, origClassName, retryLabel, targetClassName, targetDateStr, targetTimeStr, targetRoomName, targetTeacherName);
                notificationService.sendNotification(resolvedOrigTeacher, currentUser, title, message, "MAKEUP_COORDINATED_OUT", saved.getId(), makeupUrl);
            }

            if (resolvedTargetTeacher != null) {
                String title = "Có học viên học bù vào lớp bạn: " + studentName + retryLabel;
                String message = String.format("Buổi học ngày %s (%s, lớp %s, Phòng %s) của bạn sẽ có học viên %s (từ lớp %s) đến học bù%s.",
                        targetDateStr, targetTimeStr, targetClassName, targetRoomName, studentName, origClassName, retryLabel);
                notificationService.sendNotification(resolvedTargetTeacher, currentUser, title, message, "MAKEUP_COORDINATED_IN", saved.getId(), makeupUrl);
            }

            if (student != null && student.getParent() != null) {
                User parent = student.getParent();
                String title = "Đã xếp lịch học bù cho bé " + studentName + retryLabel;
                String message = String.format("Bé %s đã được xếp lịch học bù%s tại lớp %s vào ngày %s (ca %s, Phòng %s, GV: %s). Lưu ý: Mỗi buổi nghỉ chỉ được xếp bù tối đa %d lần.",
                        studentName, retryLabel, targetClassName, targetDateStr, targetTimeStr, targetRoomName, targetTeacherName, AttendanceMakeupMapper.MAX_MAKEUP_RETRIES);
                notificationService.sendNotification(parent, currentUser, title, message, "MAKEUP_SCHEDULED", saved.getId(), makeupUrl);
            }
        }

        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    @Override
    @Transactional
    public MakeupRegistrationResponse cancelMakeup(Long id, MakeupCancelRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateStaffOrAdmin(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository
                .findByIdAndStatusIn(id, List.of(MakeupStatus.PENDING, MakeupStatus.SCHEDULED,
                        MakeupStatus.MISSED, MakeupStatus.NO_SLOT))
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy ca học bù có thể hủy với ID: " + id
                        + " (Chỉ hủy được ca ở trạng thái PENDING, SCHEDULED, MISSED hoặc NO_SLOT)"));

        int retry = AttendanceMakeupMapper.extractRetryCount(mr);
        mr.setStatus(MakeupStatus.CANCELLED);
        String cancelNote = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? "HỦY: " + request.getReason().trim()
                : "Hủy bởi " + currentUser.getUsername();
        mr.setNote(retry > 0 ? AttendanceMakeupMapper.buildRetryNote(retry, false, cancelNote) : cancelNote);
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        if (notificationService != null && saved.getStudent() != null) {
            Student stu = saved.getStudent();
            String stuName = stu.getFullName();
            String makeupUrl = "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + saved.getId();
            if (stu.getParent() != null) {
                notificationService.sendNotification(
                        stu.getParent(), currentUser,
                        "Hủy lịch học bù: " + stuName,
                        "Ca học bù của bé " + stuName + " đã được hủy. Ghi chú: " + cancelNote,
                        "MAKEUP_CANCELLED", saved.getId(), makeupUrl);
            }
            User origTeacher = resolvePrimaryTeacher(saved.getOriginalLesson());
            if (origTeacher != null && !origTeacher.getId().equals(currentUser.getId())) {
                notificationService.sendNotification(
                        origTeacher, currentUser,
                        "Hủy lịch học bù của " + stuName,
                        "Ca học bù của học viên " + stuName + " đã bị hủy. Ghi chú: " + cancelNote,
                        "MAKEUP_CANCELLED", saved.getId(), makeupUrl);
            }
            User targetTeacher = resolvePrimaryTeacher(saved.getTargetLesson());
            if (targetTeacher != null && !targetTeacher.getId().equals(currentUser.getId())
                    && (origTeacher == null || !targetTeacher.getId().equals(origTeacher.getId()))) {
                notificationService.sendNotification(
                        targetTeacher, currentUser,
                        "Hủy học viên bù vào lớp: " + stuName,
                        "Học viên " + stuName + " không còn tham gia học bù tại lớp bạn (" + cancelNote + ").",
                        "MAKEUP_CANCELLED", saved.getId(), makeupUrl);
            }
        }

        log.info("Ca bù #{} của học viên [{}] đã bị hủy bởi [{}] — Lý do: {}",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername(), cancelNote);

        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    @Override
    @Transactional
    public MakeupRegistrationResponse markNoSlotAvailable(Long id, MakeupResolveRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validateStaffOrAdmin(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository
                .findByIdAndStatusIn(id, List.of(MakeupStatus.PENDING, MakeupStatus.NO_SLOT, MakeupStatus.MISSED))
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Chỉ báo 'chưa có lớp phù hợp' cho ca bù đang chờ xếp lịch hoặc bỏ lỡ (ID: " + id + ")"));

        String message = requireResolveNote(request,
                "Vui lòng nhập nội dung thông báo gửi phụ huynh (ví dụ: hiện chưa có lớp cùng trình độ còn chỗ, chờ xếp lịch mới).");

        int retry = AttendanceMakeupMapper.extractRetryCount(mr);
        mr.setStatus(MakeupStatus.NO_SLOT);
        String body = "CHƯA CÓ LỚP PHÙ HỢP: " + message;
        mr.setNote(retry > 0 ? AttendanceMakeupMapper.buildRetryNote(retry, false, body) : body);
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        if (notificationService != null && saved.getStudent() != null) {
            String stuName = saved.getStudent().getFullName();
            String makeupUrl = "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + saved.getId();
            if (saved.getStudent().getParent() != null) {
                notificationService.sendNotification(
                        saved.getStudent().getParent(), currentUser,
                        "Chưa có lớp học bù phù hợp: " + stuName,
                        "Hiện tại trung tâm chưa có ca học bù phù hợp cho bé " + stuName + ". Ghi chú: " + message,
                        "MAKEUP_NO_SLOT", saved.getId(), makeupUrl);
            }
            User origTeacher = resolvePrimaryTeacher(saved.getOriginalLesson());
            if (origTeacher != null && !origTeacher.getId().equals(currentUser.getId())) {
                notificationService.sendNotification(
                        origTeacher, currentUser,
                        "Chưa xếp được lớp bù cho " + stuName,
                        "Giáo vụ đã thông báo tới phụ huynh của " + stuName + " rằng hiện chưa có ca học bù phù hợp: " + message,
                        "MAKEUP_NO_SLOT", saved.getId(), makeupUrl);
            }
        }

        log.info("Ca bù #{} của [{}] — chưa có lớp phù hợp, đã báo phụ huynh bởi [{}]",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername());
        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    private String requireResolveNote(MakeupResolveRequest request, String errorMessage) {
        if (request == null || request.getNote() == null || request.getNote().isBlank()) {
            throw new AttendanceMakeupException(errorMessage);
        }
        return request.getNote().trim();
    }

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

        Set<Long> enrolledClassIds = new HashSet<>(attendanceEnrollmentRepository
                .findClassIdsByStudentIdAndStatusIn(mr.getStudent().getId(),
                        List.of(com.talent.management.shared.enums.EnrollmentStatus.ENROLLED,
                                com.talent.management.shared.enums.EnrollmentStatus.PENDING_PAYMENT)));
        if (enrolledClassIds.contains(targetClass.getId())) {
            throw new AttendanceMakeupException("Học viên đã thuộc lớp này, hãy chọn lớp khác để học bù!");
        }

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

        Lesson conflictLesson = findStudentTimeConflict(mr.getStudent().getId(), target, mr.getOriginalLesson(), mr.getId());
        if (conflictLesson != null) {
            String conflictClass = conflictLesson.getClassEntity() != null
                    ? conflictLesson.getClassEntity().getClassName() : "lớp khác";
            String conflictTime = conflictLesson.getStartTime() + "-" + conflictLesson.getEndTime();
            throw new AttendanceMakeupException("Học viên bị trùng lịch học với " + conflictClass
                    + " vào ngày " + conflictLesson.getLessonDate() + " (" + conflictTime + "), vui lòng chọn ca khác!");
        }
    }

    @Override
    @Transactional
    public MakeupRegistrationResponse completeMakeup(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        validateMarkPermission(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));

        if (mr.getStatus() != MakeupStatus.SCHEDULED && mr.getStatus() != MakeupStatus.MISSED) {
            throw new AttendanceMakeupException(
                    "Chỉ có thể hoàn thành ca học bù đã được xếp lịch (SCHEDULED / MISSED). Trạng thái hiện tại: " + mr.getStatus());
        }

        if (mr.getTargetLesson() == null) {
            throw new AttendanceMakeupException("Ca học bù này chưa có buổi học bù cụ thể!");
        }

        int retry = Math.max(1, AttendanceMakeupMapper.extractRetryCount(mr));
        mr.setStatus(MakeupStatus.COMPLETED);
        mr.setNote(AttendanceMakeupMapper.buildRetryNote(retry, false, "Đã hoàn thành học bù (Xác nhận bởi " + currentUser.getFullName() + ")"));
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        Attendance attendance = attendanceRecordRepository
                .findByLessonIdAndStudentId(mr.getTargetLesson().getId(), mr.getStudent().getId())
                .orElse(Attendance.builder()
                        .lesson(mr.getTargetLesson())
                        .student(mr.getStudent())
                        .build());
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setMarkedAt(LocalDateTime.now());
        attendance.setNote("Hoàn thành học bù (Ca #" + mr.getId() + ")");
        attendanceRecordRepository.save(attendance);

        if (notificationService != null && saved.getStudent() != null) {
            String stuName = saved.getStudent().getFullName();
            Lesson target = saved.getTargetLesson();
            String targetCls = target != null && target.getClassEntity() != null ? target.getClassEntity().getClassName() : "lớp học bù";
            String targetDate = target != null && target.getLessonDate() != null ? target.getLessonDate().toString() : "";
            String makeupUrl = "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + saved.getId();
            if (saved.getStudent().getParent() != null) {
                notificationService.sendNotification(
                        saved.getStudent().getParent(), currentUser,
                        "Đã hoàn thành buổi học bù: " + stuName,
                        "Bé " + stuName + " đã tham gia đầy đủ buổi học bù tại " + targetCls + " ngày " + targetDate + ".",
                        "MAKEUP_COMPLETED", saved.getId(), makeupUrl);
            }
            User origTeacher = resolvePrimaryTeacher(saved.getOriginalLesson());
            if (origTeacher != null && !origTeacher.getId().equals(currentUser.getId())) {
                notificationService.sendNotification(
                        origTeacher, currentUser,
                        "Học viên đã hoàn thành học bù: " + stuName,
                        "Học viên " + stuName + " đã hoàn thành buổi học bù tại " + targetCls + " ngày " + targetDate + ".",
                        "MAKEUP_COMPLETED", saved.getId(), makeupUrl);
            }
            User targetTeacher = resolvePrimaryTeacher(target);
            if (targetTeacher != null && !targetTeacher.getId().equals(currentUser.getId())
                    && (origTeacher == null || !targetTeacher.getId().equals(origTeacher.getId()))) {
                notificationService.sendNotification(
                        targetTeacher, currentUser,
                        "Xác nhận hoàn thành học bù: " + stuName,
                        "Học viên " + stuName + " đã được ghi nhận hoàn thành buổi học bù ngày " + targetDate + ".",
                        "MAKEUP_COMPLETED", saved.getId(), makeupUrl);
            }
        }

        log.info("Ca học bù #{} của học viên [{}] hoàn thành thủ công bởi [{}]",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername());

        return attendanceMakeupMapper.toMakeupResponse(saved);
    }

    // =========================================================================
    // HELPER APIs HỖ TRỢ GIAO DIỆN & ĐIỀU PHỐI HỌC BÙ THÔNG MINH
    // =========================================================================

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
        boolean hasLevel = course.getLevel() != null && !course.getLevel().isBlank() && course.getProgram() != null;

        // 1. Tìm các buổi khớp chính xác số thứ tự bài học (sessionNumber) ở các lớp song song
        List<Lesson> exactCandidates = hasLevel
                ? attendanceLessonRepository.findMakeupCandidatesByLevel(
                        today, original.getSessionNumber(), originalClass.getId(),
                        course.getProgram().getId(), course.getLevel())
                : attendanceLessonRepository.findMakeupCandidatesByCourse(
                        today, original.getSessionNumber(), originalClass.getId(), course.getId());

        // 2. Tìm các buổi điều phối ghép bù linh hoạt cùng trình độ/khóa học (khi các lớp khác đã dạy qua buổi đó hoặc cần phương án thay thế)
        List<Lesson> flexibleCandidates = hasLevel
                ? attendanceLessonRepository.findFlexibleMakeupCandidatesByLevel(
                        today, original.getSessionNumber(), originalClass.getId(),
                        course.getProgram().getId(), course.getLevel())
                : attendanceLessonRepository.findFlexibleMakeupCandidatesByCourse(
                        today, original.getSessionNumber(), originalClass.getId(), course.getId());

        Set<Long> enrolledClassIds = new HashSet<>(attendanceEnrollmentRepository
                .findClassIdsByStudentIdAndStatusIn(mr.getStudent().getId(),
                        List.of(com.talent.management.shared.enums.EnrollmentStatus.ENROLLED,
                                com.talent.management.shared.enums.EnrollmentStatus.PENDING_PAYMENT)));

        LinkedHashMap<Long, Lesson> combinedMap = new LinkedHashMap<>();
        Set<Long> exactLessonIds = new HashSet<>();

        for (Lesson l : exactCandidates) {
            if (isLessonUsableForMakeup(l, enrolledClassIds, today, now)
                    && findStudentTimeConflict(mr.getStudent().getId(), l, original, mr.getId()) == null) {
                combinedMap.put(l.getId(), l);
                exactLessonIds.add(l.getId());
            }
        }
        // Bổ sung tối đa 10 ca ghép bù linh hoạt gần nhất để giáo vụ điều phối khi không còn lớp dạy đúng buổi gốc
        int flexAdded = 0;
        for (Lesson l : flexibleCandidates) {
            if (flexAdded >= 10) break;
            if (!combinedMap.containsKey(l.getId())
                    && isLessonUsableForMakeup(l, enrolledClassIds, today, now)
                    && findStudentTimeConflict(mr.getStudent().getId(), l, original, mr.getId()) == null) {
                combinedMap.put(l.getId(), l);
                flexAdded++;
            }
        }

        if (combinedMap.isEmpty()) {
            return List.of();
        }

        List<Long> lessonIds = new ArrayList<>(combinedMap.keySet());
        Map<Long, Long> makeupSeatMap = makeupRegistrationRepository
                .findByTargetLessonIdInAndStatusIn(lessonIds, SEAT_HOLDING_STATUSES)
                .stream()
                .collect(Collectors.groupingBy(m -> m.getTargetLesson().getId(), Collectors.counting()));

        List<LessonOptionResponse> result = new ArrayList<>();
        for (Lesson l : combinedMap.values()) {
            int base = l.getClassEntity().getCurrentStudents() != null ? l.getClassEntity().getCurrentStudents() : 0;
            int occupied = base + makeupSeatMap.getOrDefault(l.getId(), 0L).intValue();
            Integer max = l.getClassEntity().getMaxStudents();
            if (max != null && occupied >= max) continue;

            boolean isExact = exactLessonIds.contains(l.getId());
            String matchLabel = isExact
                    ? "Đúng bài (#Buổi " + l.getSessionNumber() + ")"
                    : "Ghép bù linh hoạt (#Buổi " + l.getSessionNumber() + " - Khác bài gốc #" + original.getSessionNumber() + ")";
            result.add(attendanceMakeupMapper.toLessonOption(l, occupied, isExact, matchLabel));
        }
        return result;
    }

    private boolean isLessonUsableForMakeup(Lesson l, Set<Long> enrolledClassIds, LocalDate today, LocalTime now) {
        if (l.getStatus() == com.talent.management.shared.enums.LessonStatus.CANCELLED) return false;
        if (l.getClassEntity() == null || enrolledClassIds.contains(l.getClassEntity().getId())) return false;
        if (l.getLessonDate() == null || l.getLessonDate().isBefore(today)) return false;
        if (l.getLessonDate().isEqual(today) && l.getEndTime() != null && l.getEndTime().isBefore(now)) return false;
        return true;
    }

    private void validateStudentAccess(User user, Student student) {
        boolean denied = user.getRole() == Role.STUDENT
                ? student.getAccount() == null || !student.getAccount().getId().equals(user.getId())
                : user.getRole() == Role.PARENT && (student.getParent() == null || !student.getParent().getId().equals(user.getId()));
        if (denied) throw new AttendanceMakeupException("Bạn không có quyền truy cập hồ sơ học viên này");
    }

    /**
     * Tìm buổi học bị trùng khung giờ với ca học bù dự kiến (kiểm tra cả lịch học chính và các ca bù SCHEDULED khác).
     */
    private Lesson findStudentTimeConflict(Long studentId, Lesson target, Lesson originalLesson, Long excludeMakeupId) {
        if (studentId == null || target == null || target.getLessonDate() == null
                || target.getStartTime() == null || target.getEndTime() == null) {
            return null;
        }
        LocalDate targetDate = target.getLessonDate();
        Long origLessonId = originalLesson != null ? originalLesson.getId() : null;

        List<Lesson> regularLessons = attendanceLessonRepository
                .findLessonsByStudentIdAndDateBetween(studentId, targetDate, targetDate);
        if (regularLessons != null) {
            for (Lesson l : regularLessons) {
                if (l == null || Objects.equals(l.getId(), target.getId())) continue;
                if (origLessonId != null && Objects.equals(l.getId(), origLessonId)) continue;
                if (l.getStatus() == com.talent.management.shared.enums.LessonStatus.CANCELLED) continue;
                if (isTimeOverlapping(l, target)) {
                    return l;
                }
            }
        }

        List<MakeupRegistration> scheduledMakeups = makeupRegistrationRepository
                .findByStudentIdAndStatusIn(studentId, List.of(MakeupStatus.SCHEDULED));
        if (scheduledMakeups != null) {
            for (MakeupRegistration other : scheduledMakeups) {
                if (other == null || Objects.equals(other.getId(), excludeMakeupId)) continue;
                Lesson otherTarget = other.getTargetLesson();
                if (otherTarget == null || !targetDate.equals(otherTarget.getLessonDate())) continue;
                if (isTimeOverlapping(otherTarget, target)) {
                    return otherTarget;
                }
            }
        }
        return null;
    }

    private boolean isTimeOverlapping(Lesson a, Lesson b) {
        if (a.getStartTime() == null || a.getEndTime() == null
                || b.getStartTime() == null || b.getEndTime() == null) {
            return false;
        }
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }

    /** Trả về giáo viên phụ trách chính của buổi học (ưu tiên GV của Lesson, fallback sang GV của ClassEntity). */
    private User resolvePrimaryTeacher(Lesson lesson) {
        if (lesson == null) return null;
        if (lesson.getTeacher() != null) return lesson.getTeacher();
        return lesson.getClassEntity() != null ? lesson.getClassEntity().getTeacher() : null;
    }

    /**
     * Kiểm tra xem user có phải là giáo viên phụ trách của buổi học (Lesson) hoặc lớp học (ClassEntity) chứa buổi học đó không.
     */
    private boolean isTeacherOfLesson(User teacher, Lesson lesson) {
        if (teacher == null || lesson == null) {
            return false;
        }
        Long teacherId = teacher.getId();
        // 1. Kiểm tra giáo viên được phân công trực tiếp trên buổi học (Lesson)
        if (lesson.getTeacher() != null && teacherId.equals(lesson.getTeacher().getId())) {
            return true;
        }
        // 2. Kiểm tra giáo viên chủ nhiệm / phụ trách của lớp học (ClassEntity)
        if (lesson.getClassEntity() != null && lesson.getClassEntity().getTeacher() != null
                && teacherId.equals(lesson.getClassEntity().getTeacher().getId())) {
            return true;
        }
        return false;
    }

    /** TEACHER, STAFF và ADMIN được duyệt đơn nghỉ */
    private void validateReviewPermission(User user) {
        if (user.getRole() == Role.PARENT || user.getRole() == Role.STUDENT) {
            throw new AttendanceMakeupException(
                    "Tài khoản Phụ huynh/Học viên không có quyền xét duyệt đơn xin nghỉ học!");
        }
    }

    private void validateStaffOrAdmin(User user) {
        if (user.getRole() == Role.PARENT || user.getRole() == Role.STUDENT
                || user.getRole() == Role.TEACHER) {
            throw new AttendanceMakeupException(
                    "Chỉ Nhân viên (STAFF) hoặc Admin mới có quyền xếp/hủy lịch học bù!");
        }
    }

    private void validateMarkPermission(User user) {
        if (user.getRole() == Role.PARENT || user.getRole() == Role.STUDENT) {
            throw new AttendanceMakeupException(
                    "Tài khoản Phụ huynh/Học viên không có quyền điểm danh!");
        }
    }
}
