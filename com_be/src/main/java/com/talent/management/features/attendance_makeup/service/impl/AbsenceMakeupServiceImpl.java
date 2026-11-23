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
import com.talent.management.features.attendance_makeup.policy.AbsenceMakeupValidator;
import com.talent.management.features.attendance_makeup.policy.AttendanceMakeupNotificationHelper;
import com.talent.management.features.attendance_makeup.policy.MakeupQuotaPolicy;
import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.features.attendance_makeup.service.AbsenceMakeupService;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.weekly_schedule.repository.AttendanceClassRepository;
import com.talent.management.features.weekly_schedule.repository.AttendanceEnrollmentRepository;
import com.talent.management.features.weekly_schedule.repository.AttendanceLessonRepository;
import com.talent.management.features.weekly_schedule.repository.AttendanceRecordRepository;
import com.talent.management.shared.entity.AbsenceRequest;
import com.talent.management.shared.entity.Attendance;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.MakeupRegistration;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.AbsenceStatus;
import com.talent.management.shared.enums.AttendanceStatus;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.enums.LessonStatus;
import com.talent.management.shared.enums.MakeupStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * SERVICE ĐIỀU PHỐI NGHIỆP VỤ NGHỈ HỌC & HỌC BÙ (4 GIAI ĐOẠN)
 * ============================================================================
 * Mục lục mã nguồn (dễ tra cứu theo quy trình nghiệp vụ):
 *   - Quy tắc 3 lượt/khóa học      -> {@link MakeupQuotaPolicy}
 *   - Kiểm tra quyền & trùng lịch   -> {@link AbsenceMakeupValidator}
 *   - Soạn & gửi thông báo chuông  -> {@link AttendanceMakeupNotificationHelper}
 *   - [Giai đoạn 1] Tạo, Hủy & Xem đơn xin nghỉ
 *   - [Giai đoạn 2] Giáo viên / Nhân sự xét duyệt đơn -> Sinh ca bù
 *   - [Giai đoạn 3] Điều phối, Xếp lịch học bù, Báo chưa có lớp & Hủy ca bù
 *   - [Giai đoạn 4] Xác nhận hoàn thành buổi học bù
 *   - [Hỗ trợ UI]   Danh sách con, Buổi học xin nghỉ, Gợi ý lớp bù thông minh
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
    private final StudentRepository studentRepository;
    private final AttendanceMakeupMapper attendanceMakeupMapper;
    private final CurrentUserService currentUserService;

    private final MakeupQuotaPolicy quotaPolicy;
    private final AbsenceMakeupValidator validator;
    private final AttendanceMakeupNotificationHelper notificationHelper;

    @Autowired(required = false)
    private AttendanceClassRepository attendanceClassRepository;

    // =========================================================================
    // [GIAI ĐOẠN 1] TẠO, HỦY VÀ TRA CỨU ĐƠN XIN NGHỈ HỌC
    // =========================================================================

    @Override
    @Transactional
    public AbsenceRequestResponse createAbsenceRequest(AbsenceRequestCreateRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        validator.validateParticipantRole(currentUser);

        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new AttendanceMakeupException("Vui lòng nhập lý do xin nghỉ học");
        }

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên với ID: " + request.getStudentId()));
        validator.validateStudentAccess(currentUser, student);

        Lesson lesson = attendanceLessonRepository.findById(request.getLessonId())
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + request.getLessonId()));

        if (currentUser.getRole() == Role.TEACHER && !validator.isTeacherOfLesson(currentUser, lesson)) {
            throw new AttendanceMakeupException("Giáo viên chỉ được tạo đơn xin nghỉ cho lớp hoặc buổi học do mình phụ trách!");
        }

        LocalDateTime now = LocalDateTime.now(AbsenceMakeupValidator.CENTER_ZONE);
        boolean isPast = validator.hasStarted(lesson, now)
                || lesson.getStatus() == LessonStatus.COMPLETED
                || lesson.getStatus() == LessonStatus.CANCELLED;
        if (isPast) {
            throw new AttendanceMakeupException(
                    "Chỉ có thể xin nghỉ cho buổi học chưa diễn ra. Buổi học ngày " + lesson.getLessonDate()
                            + " đã diễn ra hoặc đã kết thúc, không thể gửi đơn xin nghỉ.");
        }

        ClassEntity lessonClass = lesson.getClassEntity();
        if (lessonClass == null || !attendanceEnrollmentRepository.existsByStudentIdAndClassEntityIdAndStatus(
                student.getId(), lessonClass.getId(), EnrollmentStatus.ENROLLED)) {
            throw new AttendanceMakeupException("Học viên chưa ghi danh chính thức vào lớp của buổi học này nên không thể gửi đơn xin nghỉ!");
        }

        if (absenceRequestRepository.existsByStudentIdAndLessonIdAndStatusIn(
                student.getId(), lesson.getId(), List.of(AbsenceStatus.PENDING, AbsenceStatus.APPROVED))) {
            throw new AttendanceMakeupException("Buổi học này đã có đơn xin nghỉ đang chờ duyệt hoặc đã được phê duyệt!");
        }

        boolean needMakeup = !Boolean.FALSE.equals(request.getNeedMakeup());
        if (needMakeup) {
            quotaPolicy.assertCanCreateMakeupAbsence(student, lesson);
        }

        AbsenceRequest absenceRequest = AbsenceRequest.builder()
                .student(student)
                .lesson(lesson)
                .requestedByUser(currentUser)
                .reason(request.getReason().trim())
                .needMakeup(needMakeup)
                .status(AbsenceStatus.PENDING)
                .createdAt(now)
                .build();

        AbsenceRequest saved = absenceRequestRepository.save(absenceRequest);
        log.info("Đơn xin nghỉ mới #{} cho học viên [{}] bởi [{}]",
                saved.getId(), student.getFullName(), currentUser.getUsername());

        notificationHelper.notifyAbsenceCreated(saved, student, lesson, currentUser, needMakeup);
        int mkCount = quotaPolicy.resolveUsedCourseQuota(student, lesson);
        return attendanceMakeupMapper.toAbsenceResponse(saved, null, mkCount, mkCount >= MakeupQuotaPolicy.MAKEUP_LIMIT);
    }

    @Override
    @Transactional
    public void cancelAbsenceRequest(Long id) {
        validator.requireValidId(id, "đơn xin nghỉ");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateParticipantRole(currentUser);
        AbsenceRequest ar = absenceRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        validator.validateStudentAccess(currentUser, ar.getStudent());

        if (currentUser.getRole() == Role.TEACHER && !validator.isTeacherOfLesson(currentUser, ar.getLesson())) {
            throw new AttendanceMakeupException("Chỉ giáo viên phụ trách mới có quyền hủy đơn này.");
        }
        if (ar.getStatus() != AbsenceStatus.PENDING) {
            throw new AttendanceMakeupException("Chỉ có thể hủy đơn khi đang ở trạng thái Chờ duyệt.");
        }

        Student student = ar.getStudent();
        Lesson lesson = ar.getLesson();
        String studentName = student != null ? student.getFullName() : "Học viên";

        absenceRequestRepository.delete(ar);
        log.info("Đơn xin nghỉ #{} của [{}] đã bị hủy bởi [{}]", id, studentName, currentUser.getUsername());

        notificationHelper.notifyAbsenceCancelled(id, student, lesson, currentUser);
    }

    @Override
    public List<AbsenceRequestResponse> getAbsenceRequests(AbsenceStatus status, Long studentId, Long classId) {
        User currentUser = currentUserService.getCurrentUser();
        validator.validateParticipantRole(currentUser);

        List<AbsenceRequest> list = switch (currentUser.getRole()) {
            case PARENT -> (status != null)
                    ? absenceRequestRepository.findByStudentParentIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status)
                    : absenceRequestRepository.findByStudentParentIdOrderByCreatedAtDesc(currentUser.getId());
            case TEACHER -> (status != null)
                    ? absenceRequestRepository.findByTeacherIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status)
                    : absenceRequestRepository.findByTeacherIdOrderByCreatedAtDesc(currentUser.getId());
            case STAFF, BRANCH_MANAGER, ADMIN -> (status != null)
                    ? absenceRequestRepository.findByStatusOrderByCreatedAtDesc(status)
                    : absenceRequestRepository.findAllByOrderByCreatedAtDesc();
            default -> throw new AttendanceMakeupException("Tài khoản của bạn không có quyền xem danh sách đơn xin nghỉ!");
        };

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
        Map<Long, MakeupRegistration> makeupMap = makeupRegistrationRepository.findByAbsenceRequestIdIn(arIds)
                .stream()
                .filter(mr -> mr.getAbsenceRequest() != null)
                .collect(Collectors.toMap(mr -> mr.getAbsenceRequest().getId(), Function.identity(), (m1, m2) -> m1));

        Map<String, Integer> studentCourseCache = new HashMap<>();
        return filtered.stream().map(ar -> {
            Student s = ar.getStudent();
            Lesson l = ar.getLesson();
            int mkCount = studentCourseCache.computeIfAbsent(
                    quotaPolicy.courseQuotaCacheKey(s, l),
                    k -> quotaPolicy.resolveUsedCourseQuota(s, l));
            AbsenceRequestResponse resp = attendanceMakeupMapper.toAbsenceResponse(ar, makeupMap.get(ar.getId()));
            if (resp != null) {
                resp.setCourseMakeupCount(mkCount);
                resp.setCourseMakeupLimit(MakeupQuotaPolicy.MAKEUP_LIMIT);
                resp.setCourseMakeupLimitReached(mkCount >= MakeupQuotaPolicy.MAKEUP_LIMIT);
            }
            return resp;
        }).toList();
    }

    @Override
    public AbsenceRequestResponse getAbsenceRequestById(Long id) {
        validator.requireValidId(id, "đơn xin nghỉ");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateParticipantRole(currentUser);
        AbsenceRequest ar = absenceRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        validator.validateStudentAccess(currentUser, ar.getStudent());
        if (currentUser.getRole() == Role.TEACHER && !validator.isTeacherOfLesson(currentUser, ar.getLesson())) {
            throw new AttendanceMakeupException("Chỉ giáo viên phụ trách môn và buổi học này mới có quyền xem đơn xin nghỉ!");
        }

        MakeupRegistration mr = makeupRegistrationRepository.findByAbsenceRequestId(ar.getId()).orElse(null);
        int mkCount = quotaPolicy.resolveUsedCourseQuota(ar.getStudent(), ar.getLesson());
        return attendanceMakeupMapper.toAbsenceResponse(ar, mr, mkCount, mkCount >= MakeupQuotaPolicy.MAKEUP_LIMIT);
    }

    // =========================================================================
    // [GIAI ĐOẠN 2] XÉT DUYỆT ĐƠN XIN NGHỈ -> SINH CA HỌC BÙ
    // =========================================================================

    @Override
    @Transactional
    public AbsenceRequestResponse reviewAbsenceRequest(Long id, AbsenceReviewRequest request) {
        validator.requireValidId(id, "đơn xin nghỉ");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateReviewPermission(currentUser);

        if (request == null || request.getDecision() == null) {
            throw new AttendanceMakeupException("Vui lòng chọn quyết định xét duyệt (APPROVED / REJECTED)");
        }

        AbsenceRequest ar = absenceRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy đơn xin nghỉ với ID: " + id));

        if (currentUser.getRole() == Role.TEACHER && !validator.isTeacherOfLesson(currentUser, ar.getLesson())) {
            throw new AttendanceMakeupException("Chỉ giáo viên phụ trách môn và buổi học hôm đó mới có quyền xét duyệt đơn xin nghỉ này!");
        }
        if (ar.getStatus() != AbsenceStatus.PENDING) {
            throw new AttendanceMakeupException("Đơn đã được xử lý trước đó (trạng thái hiện tại: " + ar.getStatus() + ")");
        }

        ReviewDecision decision = request.getDecision();
        if (decision == ReviewDecision.REJECTED && (request.getReviewNote() == null || request.getReviewNote().isBlank())) {
            throw new AttendanceMakeupException("Vui lòng nhập lý do từ chối (reviewNote) để thông báo rõ ràng tới phụ huynh.");
        }

        boolean needMakeup = ar.wantsMakeup();
        Optional<MakeupRegistration> existingMakeup = Optional.empty();
        if (decision == ReviewDecision.APPROVED && needMakeup) {
            if (ar.getLesson() != null && ar.getLesson().getStatus() == LessonStatus.CANCELLED) {
                throw new AttendanceMakeupException("Buổi học gốc đã bị hủy nên không thể duyệt tạo ca học bù!");
            }
            existingMakeup = makeupRegistrationRepository.findByAbsenceRequestId(ar.getId());
            if (existingMakeup.isEmpty()) {
                quotaPolicy.assertCanApproveMakeup(ar.getStudent(), ar.getLesson());
            }
        }

        LocalDateTime now = LocalDateTime.now(AbsenceMakeupValidator.CENTER_ZONE);
        ar.setStatus(mapDecisionToStatus(decision));
        ar.setApprovedByTeacher(currentUser);
        ar.setReviewNote(request.getReviewNote() != null ? request.getReviewNote().trim() : null);
        ar.setReviewedAt(now);
        AbsenceRequest saved = absenceRequestRepository.save(ar);

        MakeupRegistration mr = null;
        if (decision == ReviewDecision.APPROVED && needMakeup) {
            mr = existingMakeup.orElseGet(() -> {
                MakeupRegistration created = MakeupRegistration.builder()
                        .absenceRequest(saved)
                        .student(saved.getStudent())
                        .originalLesson(saved.getLesson())
                        .targetLesson(null)
                        .status(MakeupStatus.PENDING)
                        .note(null)
                        .createdAt(now)
                        .build();
                MakeupRegistration savedMr = makeupRegistrationRepository.save(created);
                log.info("[{}] APPROVED đơn #{} của [{}] → Ca bù #{} tạo mới",
                        currentUser.getUsername(), saved.getId(), saved.getStudent().getFullName(), savedMr.getId());
                return savedMr;
            });
        } else {
            log.info("[{}] {} đơn #{} của [{}]",
                    currentUser.getUsername(), decision, saved.getId(), saved.getStudent().getFullName());
        }

        notificationHelper.notifyAbsenceReviewed(saved, mr, currentUser, decision, needMakeup);
        int mkCount = quotaPolicy.resolveUsedCourseQuota(saved.getStudent(), saved.getLesson());
        return attendanceMakeupMapper.toAbsenceResponse(saved, mr, mkCount, mkCount >= MakeupQuotaPolicy.MAKEUP_LIMIT);
    }

    private AbsenceStatus mapDecisionToStatus(ReviewDecision decision) {
        return switch (decision) {
            case APPROVED -> AbsenceStatus.APPROVED;
            case REJECTED -> AbsenceStatus.REJECTED;
        };
    }

    // =========================================================================
    // [GIAI ĐOẠN 3] ĐIỀU PHỐI, XẾP LỊCH HỌC BÙ, BÁO CHƯA CÓ LỚP & HỦY CA
    // =========================================================================

    @Override
    public List<MakeupRegistrationResponse> getMakeupRegistrations(MakeupStatus status, Long studentId) {
        User currentUser = currentUserService.getCurrentUser();
        validator.validateParticipantRole(currentUser);
        List<MakeupRegistration> list;

        if (currentUser.getRole() == Role.PARENT) {
            list = (status != null)
                    ? makeupRegistrationRepository.findByStudentParentIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status)
                    : makeupRegistrationRepository.findByStudentParentIdOrderByCreatedAtDesc(currentUser.getId());
        } else if (currentUser.getRole() == Role.TEACHER) {
            list = (status != null)
                    ? makeupRegistrationRepository.findByTeacherIdAndStatus(currentUser.getId(), status)
                    : makeupRegistrationRepository.findByTeacherId(currentUser.getId());
        } else if (AbsenceMakeupValidator.COORDINATOR_ROLES.contains(currentUser.getRole())) {
            list = (status != null)
                    ? makeupRegistrationRepository.findByStatusOrderByCreatedAtDesc(status)
                    : makeupRegistrationRepository.findAllByOrderByCreatedAtDesc();
        } else {
            throw new AttendanceMakeupException("Tài khoản của bạn không có quyền xem lịch học bù!");
        }

        Map<String, Integer> quotaCache = new HashMap<>();
        return list.stream()
                .filter(mr -> studentId == null || (mr.getStudent() != null && mr.getStudent().getId().equals(studentId)))
                .sorted(Comparator
                        .comparingInt((MakeupRegistration mr) -> makeupSortPriority(mr.getStatus()))
                        .thenComparing(MakeupRegistration::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MakeupRegistration::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(mr -> toMakeupWithQuota(mr, quotaCache))
                .toList();
    }

    @Override
    public MakeupRegistrationResponse getMakeupRegistrationById(Long id) {
        validator.requireValidId(id, "ca học bù");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateParticipantRole(currentUser);
        MakeupRegistration mr = makeupRegistrationRepository.findById(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));

        validator.validateStudentAccess(currentUser, mr.getStudent());
        if (currentUser.getRole() == Role.TEACHER
                && !validator.isTeacherOfLesson(currentUser, mr.getOriginalLesson())
                && !validator.isTeacherOfLesson(currentUser, mr.getTargetLesson())) {
            throw new AttendanceMakeupException("Chỉ giáo viên phụ trách lớp gốc hoặc lớp bù mới có quyền xem ca học bù này!");
        }

        return toMakeupWithQuota(mr, null);
    }

    @Override
    @Transactional
    public MakeupRegistrationResponse scheduleMakeup(Long id, MakeupScheduleRequest request) {
        validator.requireValidId(id, "ca học bù");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateStaffOrAdmin(currentUser);

        if (request == null || request.getTargetLessonId() == null) {
            throw new AttendanceMakeupException("Vui lòng chọn buổi học bù");
        }

        MakeupRegistration mr = makeupRegistrationRepository.findByIdForUpdate(id)
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

        Lesson targetPeek = attendanceLessonRepository.findById(request.getTargetLessonId())
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + request.getTargetLessonId()));
        if (targetPeek.getClassEntity() != null && attendanceClassRepository != null) {
            attendanceClassRepository.findByIdForUpdate(targetPeek.getClassEntity().getId());
        }
        Lesson targetLesson = attendanceLessonRepository.findByIdForUpdate(request.getTargetLessonId()).orElse(targetPeek);

        if (mr.getOriginalLesson() != null && mr.getOriginalLesson().getId().equals(targetLesson.getId())) {
            throw new AttendanceMakeupException("Buổi học bù không được trùng với chính buổi học đã xin nghỉ!");
        }

        validator.validateMakeupTarget(mr, targetLesson);

        int newRetry = currentRetry + 1;
        mr.setTargetLesson(targetLesson);
        mr.setStatus(MakeupStatus.SCHEDULED);

        String targetClassStr = targetLesson.getClassEntity() != null ? targetLesson.getClassEntity().getClassName() : "lớp bù";
        String defaultNote = "Xếp lịch học bù tại " + targetClassStr + " (Buổi #" + targetLesson.getSessionNumber() + ")";
        String rawBody = (request.getNote() != null && !request.getNote().isBlank()) ? request.getNote().trim() : defaultNote;
        mr.setNote(AttendanceMakeupMapper.buildRetryNote(newRetry, false, rawBody));

        MakeupRegistration saved = makeupRegistrationRepository.save(mr);
        log.info("Ca bù #{} xếp lịch (Lần {}/{}) → buổi [{}] ngày {} bởi [{}]",
                saved.getId(), newRetry, AttendanceMakeupMapper.MAX_MAKEUP_RETRIES,
                targetLesson.getTitle(), targetLesson.getLessonDate(), currentUser.getUsername());

        notificationHelper.notifyMakeupScheduled(saved, targetLesson, currentUser);
        return toMakeupWithQuota(saved, null);
    }

    @Override
    @Transactional
    public MakeupRegistrationResponse cancelMakeup(Long id, MakeupCancelRequest request) {
        validator.requireValidId(id, "ca học bù");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateStaffOrAdmin(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));
        if (!List.of(MakeupStatus.PENDING, MakeupStatus.SCHEDULED, MakeupStatus.MISSED, MakeupStatus.NO_SLOT).contains(mr.getStatus())) {
            throw new AttendanceMakeupException(
                    "Không thể hủy ca học bù #" + id + " ở trạng thái " + mr.getStatus()
                            + " (Chỉ hủy được ca PENDING, SCHEDULED, MISSED hoặc NO_SLOT)");
        }

        mr.setStatus(MakeupStatus.CANCELLED);
        String cancelNote = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? "HỦY: " + request.getReason().trim()
                : "Hủy bởi " + currentUser.getUsername();
        mr.setNote(cancelNote);
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        notificationHelper.notifyMakeupCancelled(saved, cancelNote, currentUser);
        log.info("Ca bù #{} của học viên [{}] đã bị hủy bởi [{}] — Lý do: {}",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername(), cancelNote);

        return toMakeupWithQuota(saved, null);
    }

    @Override
    @Transactional
    public MakeupRegistrationResponse markNoSlotAvailable(Long id, MakeupResolveRequest request) {
        validator.requireValidId(id, "ca học bù");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateStaffOrAdmin(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));
        if (!List.of(MakeupStatus.PENDING, MakeupStatus.NO_SLOT, MakeupStatus.MISSED).contains(mr.getStatus())) {
            throw new AttendanceMakeupException("Chỉ báo 'chưa có lớp phù hợp' cho ca bù đang chờ xếp lịch hoặc bỏ lỡ (ID: " + id + ")");
        }

        String message = validator.requireResolveNote(request,
                "Vui lòng nhập nội dung thông báo gửi phụ huynh (ví dụ: hiện chưa có lớp cùng trình độ còn chỗ, chờ xếp lịch mới).");

        int retry = AttendanceMakeupMapper.extractRetryCount(mr);
        mr.setStatus(MakeupStatus.CANCELLED);
        String body = "ĐÃ HỦY (Chưa có lớp phù hợp): " + message;
        mr.setNote(retry > 0 ? AttendanceMakeupMapper.buildRetryNote(retry, false, body) : body);
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        notificationHelper.notifyNoSlotAvailable(saved, message, currentUser);
        log.info("Ca bù #{} của [{}] — chưa có lớp phù hợp, đã báo phụ huynh bởi [{}]",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername());
        return toMakeupWithQuota(saved, null);
    }

    // =========================================================================
    // [GIAI ĐOẠN 4] GHI NHẬN HOÀN THÀNH BUỔI HỌC BÙ
    // =========================================================================

    @Override
    @Transactional
    public MakeupRegistrationResponse completeMakeup(Long id) {
        validator.requireValidId(id, "ca học bù");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateMarkPermission(currentUser);

        MakeupRegistration mr = makeupRegistrationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + id));

        if (currentUser.getRole() == Role.TEACHER && !validator.isTeacherOfLesson(currentUser, mr.getTargetLesson())) {
            throw new AttendanceMakeupException("Chỉ giáo viên phụ trách buổi học bù mới có quyền xác nhận hoàn thành ca học bù này!");
        }
        if (mr.getStatus() != MakeupStatus.SCHEDULED && mr.getStatus() != MakeupStatus.MISSED) {
            throw new AttendanceMakeupException(
                    "Chỉ có thể hoàn thành ca học bù đã được xếp lịch (SCHEDULED / MISSED). Trạng thái hiện tại: " + mr.getStatus());
        }

        Lesson targetLesson = mr.getTargetLesson();
        if (targetLesson == null) {
            throw new AttendanceMakeupException("Ca học bù này chưa có buổi học bù cụ thể!");
        }
        if (targetLesson.getStatus() == LessonStatus.CANCELLED) {
            throw new AttendanceMakeupException("Buổi học bù đã bị hủy, không thể xác nhận hoàn thành!");
        }

        LocalDateTime now = LocalDateTime.now(AbsenceMakeupValidator.CENTER_ZONE);
        if (targetLesson.getLessonDate() != null
                && (targetLesson.getLessonDate().isAfter(now.toLocalDate())
                    || (targetLesson.getLessonDate().isEqual(now.toLocalDate())
                        && targetLesson.getStartTime() != null
                        && targetLesson.getStartTime().isAfter(now.toLocalTime())))) {
            throw new AttendanceMakeupException("Chưa đến giờ buổi học bù, không thể điểm danh hoàn thành trước giờ học!");
        }

        int retry = Math.max(1, AttendanceMakeupMapper.extractRetryCount(mr));
        mr.setStatus(MakeupStatus.COMPLETED);
        mr.setNote(AttendanceMakeupMapper.buildRetryNote(retry, false,
                "Đã hoàn thành học bù (Xác nhận bởi " + currentUser.getFullName() + ")"));
        MakeupRegistration saved = makeupRegistrationRepository.save(mr);

        Attendance attendance = attendanceRecordRepository
                .findByLessonIdAndStudentId(targetLesson.getId(), mr.getStudent().getId())
                .orElse(Attendance.builder().lesson(targetLesson).student(mr.getStudent()).build());
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setMarkedAt(now);
        attendance.setNote("Hoàn thành học bù (Ca #" + mr.getId() + ")");
        attendanceRecordRepository.save(attendance);

        notificationHelper.notifyMakeupCompleted(saved, currentUser);
        log.info("Ca học bù #{} của học viên [{}] hoàn thành thủ công bởi [{}]",
                saved.getId(), saved.getStudent().getFullName(), currentUser.getUsername());

        return toMakeupWithQuota(saved, null);
    }

    // =========================================================================
    // [HỖ TRỢ UI] CHỌN HỌC VIÊN, CHỌN BUỔI NGHỈ & GỢI Ý LỚP BÙ THÔNG MINH
    // =========================================================================

    @Override
    public List<StudentOptionResponse> getSelectableStudents() {
        User currentUser = currentUserService.getCurrentUser();
        validator.validateParticipantRole(currentUser);
        List<Student> students = (currentUser.getRole() == Role.PARENT)
                ? studentRepository.findByParentId(currentUser.getId())
                : studentRepository.findAll();

        return students.stream().map(s -> {
            List<String> classNames = attendanceLessonRepository.findClassNamesByStudentId(s.getId());
            String currentClass = classNames.isEmpty() ? "Chưa xếp lớp" : String.join(", ", classNames);
            int count = quotaPolicy.countTotalMakeupsForStudent(s.getId());
            return attendanceMakeupMapper.toStudentOption(s, currentClass, count, count >= MakeupQuotaPolicy.MAKEUP_LIMIT);
        }).toList();
    }

    @Override
    public List<LessonOptionResponse> getLessonsForAbsenceRequest(Long studentId) {
        validator.requireValidId(studentId, "học viên");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateParticipantRole(currentUser);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên với ID: " + studentId));

        validator.validateStudentAccess(currentUser, student);

        LocalDateTime now = LocalDateTime.now(AbsenceMakeupValidator.CENTER_ZONE);
        Set<Long> alreadyRequestedLessonIds = absenceRequestRepository
                .findByStudentIdAndStatusIn(studentId, List.of(AbsenceStatus.PENDING, AbsenceStatus.APPROVED))
                .stream()
                .filter(ar -> ar.getLesson() != null)
                .map(ar -> ar.getLesson().getId())
                .collect(Collectors.toSet());

        Map<String, Integer> usedCache = new HashMap<>();
        Map<String, Integer> countedCache = new HashMap<>();
        return attendanceLessonRepository.findLessonsByStudentId(studentId)
                .stream()
                .filter(l -> !validator.hasStarted(l, now)
                        && l.getStatus() != LessonStatus.COMPLETED
                        && l.getStatus() != LessonStatus.CANCELLED
                        && !alreadyRequestedLessonIds.contains(l.getId())
                        && (currentUser.getRole() != Role.TEACHER || validator.isTeacherOfLesson(currentUser, l)))
                .map(l -> {
                    LessonOptionResponse dto = attendanceMakeupMapper.toLessonOption(l);
                    if (dto != null) {
                        Long cId = quotaPolicy.courseIdOf(l);
                        Long clsId = quotaPolicy.classIdOf(l);
                        String scopeKey = cId != null ? ("C_" + cId) : ("L_" + clsId);
                        int used = usedCache.computeIfAbsent(scopeKey, k -> quotaPolicy.resolveUsedCourseQuota(student, l));
                        int counted = countedCache.computeIfAbsent(scopeKey,
                                k -> quotaPolicy.countMakeups(student, l, MakeupQuotaPolicy.COUNTED_MAKEUP_STATUSES));
                        dto.setCourseMakeupUsed(used);
                        dto.setMakeupLimit(MakeupQuotaPolicy.MAKEUP_LIMIT);
                        dto.setMakeupAvailable(used < MakeupQuotaPolicy.MAKEUP_LIMIT && counted < MakeupQuotaPolicy.MAKEUP_LIMIT);
                    }
                    return dto;
                })
                .toList();
    }

    @Override
    public List<LessonOptionResponse> getAvailableLessonsForMakeup(Long makeupId) {
        validator.requireValidId(makeupId, "ca học bù");
        User currentUser = currentUserService.getCurrentUser();
        validator.validateParticipantRole(currentUser);
        MakeupRegistration mr = makeupRegistrationRepository.findById(makeupId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy ca học bù với ID: " + makeupId));

        validator.validateStudentAccess(currentUser, mr.getStudent());
        if (currentUser.getRole() == Role.TEACHER
                && !validator.isTeacherOfLesson(currentUser, mr.getOriginalLesson())
                && !validator.isTeacherOfLesson(currentUser, mr.getTargetLesson())) {
            throw new AttendanceMakeupException("Chỉ giáo viên phụ trách lớp gốc hoặc lớp bù mới có quyền xem ca bù gợi ý!");
        }

        Lesson original = mr.getOriginalLesson();
        if (original == null || original.getClassEntity() == null
                || original.getClassEntity().getCourse() == null || original.getSessionNumber() == null) {
            return List.of();
        }

        ClassEntity originalClass = original.getClassEntity();
        Course course = originalClass.getCourse();
        LocalDateTime nowDt = LocalDateTime.now(AbsenceMakeupValidator.CENTER_ZONE);
        LocalDate today = nowDt.toLocalDate();
        LocalDate fromDate = (original.getLessonDate() != null && original.getLessonDate().isAfter(today))
                ? original.getLessonDate() : today;
        boolean hasLevel = course.getLevel() != null && !course.getLevel().isBlank() && course.getProgram() != null;

        List<Lesson> exactCandidates = hasLevel
                ? attendanceLessonRepository.findMakeupCandidatesByLevel(
                        fromDate, original.getSessionNumber(), originalClass.getId(), course.getProgram().getId(), course.getLevel())
                : attendanceLessonRepository.findMakeupCandidatesByCourse(
                        fromDate, original.getSessionNumber(), originalClass.getId(), course.getId());

        List<Lesson> flexibleCandidates = hasLevel
                ? attendanceLessonRepository.findFlexibleMakeupCandidatesByLevel(
                        fromDate, original.getSessionNumber(), originalClass.getId(), course.getProgram().getId(), course.getLevel())
                : attendanceLessonRepository.findFlexibleMakeupCandidatesByCourse(
                        fromDate, original.getSessionNumber(), originalClass.getId(), course.getId());

        Set<Long> enrolledClassIds = new HashSet<>(attendanceEnrollmentRepository
                .findClassIdsByStudentIdAndStatusIn(mr.getStudent().getId(),
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING_PAYMENT)));

        LinkedHashMap<Long, Lesson> combinedMap = new LinkedHashMap<>();
        Set<Long> exactLessonIds = new HashSet<>();

        for (Lesson l : exactCandidates) {
            if (validator.isLessonUsableForMakeup(l, enrolledClassIds, nowDt, original)
                    && validator.findStudentTimeConflict(mr.getStudent().getId(), l, original, mr.getId()) == null) {
                combinedMap.put(l.getId(), l);
                exactLessonIds.add(l.getId());
            }
        }
        int flexAdded = 0;
        for (Lesson l : flexibleCandidates) {
            if (flexAdded >= 10) break;
            if (!combinedMap.containsKey(l.getId())
                    && validator.isLessonUsableForMakeup(l, enrolledClassIds, nowDt, original)
                    && validator.findStudentTimeConflict(mr.getStudent().getId(), l, original, mr.getId()) == null) {
                combinedMap.put(l.getId(), l);
                flexAdded++;
            }
        }

        if (combinedMap.isEmpty()) {
            return List.of();
        }

        List<Long> lessonIds = new ArrayList<>(combinedMap.keySet());
        Map<Long, Long> makeupSeatMap = makeupRegistrationRepository
                .findByTargetLessonIdInAndStatusIn(lessonIds, AbsenceMakeupValidator.SEAT_HOLDING_STATUSES)
                .stream()
                .collect(Collectors.groupingBy(m -> m.getTargetLesson().getId(), Collectors.counting()));
        Map<Long, Long> pendingSeatCache = new HashMap<>();

        List<LessonOptionResponse> result = new ArrayList<>();
        for (Lesson l : combinedMap.values()) {
            Long clsId = l.getClassEntity().getId();
            int base = l.getClassEntity().getCurrentStudents() != null ? l.getClassEntity().getCurrentStudents() : 0;
            long pendingSeats = pendingSeatCache.computeIfAbsent(clsId,
                    cls -> attendanceEnrollmentRepository.countByClassEntityIdAndStatus(cls, EnrollmentStatus.PENDING_PAYMENT));
            int occupied = base + (int) pendingSeats + makeupSeatMap.getOrDefault(l.getId(), 0L).intValue();
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

    private MakeupRegistrationResponse toMakeupWithQuota(MakeupRegistration mr, Map<String, Integer> quotaCache) {
        MakeupRegistrationResponse resp = attendanceMakeupMapper.toMakeupResponse(mr);
        if (resp != null && mr != null) {
            Student s = mr.getStudent();
            Lesson orig = mr.getOriginalLesson();
            int mkCount = quotaCache != null
                    ? quotaCache.computeIfAbsent(quotaPolicy.courseQuotaCacheKey(s, orig), k -> quotaPolicy.resolveUsedCourseQuota(s, orig))
                    : quotaPolicy.resolveUsedCourseQuota(s, orig);
            resp.setCourseMakeupCount(mkCount);
            resp.setCourseMakeupLimit(MakeupQuotaPolicy.MAKEUP_LIMIT);
        }
        return resp;
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
            default -> 99;
        };
    }
}
