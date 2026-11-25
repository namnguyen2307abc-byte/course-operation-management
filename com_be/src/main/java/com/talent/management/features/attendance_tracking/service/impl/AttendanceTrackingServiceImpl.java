package com.talent.management.features.attendance_tracking.service.impl;

import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.features.attendance_tracking.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_tracking.dto.request.StudentAttendanceMarkItem;
import com.talent.management.features.attendance_tracking.dto.response.*;
import com.talent.management.features.attendance_tracking.service.AttendanceTrackingService;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.notification.service.NotificationService;
import com.talent.management.features.weekly_schedule.repository.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * SERVICE ĐIỂM DANH & THEO DÕI CHUYÊN CẦN (AttendanceTrackingServiceImpl)
 * ============================================================================
 * Chịu trách nhiệm điểm danh buổi học (kèm học viên học bù, time-lock, khóa
 * quyền bù khi vắng mặt và khôi phục khi chấm nhầm) và tổng hợp tiến độ chuyên
 * cần toàn khóa theo ngưỡng cảnh báo 10% / 20%.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceTrackingServiceImpl implements AttendanceTrackingService {

    private final AttendanceLessonRepository attendanceLessonRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceEnrollmentRepository attendanceEnrollmentRepository;
    private final AttendanceClassRepository attendanceClassRepository;
    private final AbsenceRequestRepository absenceRequestRepository;
    private final MakeupRegistrationRepository makeupRegistrationRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserService currentUserService;

    @Autowired(required = false)
    private NotificationService notificationService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_VN_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Set<Role> SHEET_VIEW_ROLES = Set.of(Role.ADMIN, Role.STAFF, Role.BRANCH_MANAGER, Role.TEACHER);
    private static final Set<Role> STAFF_OVERRIDE_ROLES = Set.of(Role.ADMIN, Role.STAFF, Role.BRANCH_MANAGER);
    private static final List<MakeupStatus> ACTIVE_MAKEUP_STATUSES =
            List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);
    private static final List<MakeupStatus> SHEET_TARGET_MAKEUP_STATUSES =
            List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED, MakeupStatus.CANCELLED);
    private static final List<EnrollmentStatus> TRACKED_ENROLLMENT_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.COMPLETED);

    // =========================================================================
    // 1. DANH SÁCH HỌC VIÊN ĐƯỢC PHÉP TRA CỨU THEO VAI TRÒ
    // =========================================================================

    @Override
    public List<Map<String, Object>> getTrackableStudents() {
        User currentUser = currentUserService.getCurrentUser();
        List<Student> students = loadTrackableStudentsForUser(currentUser);

        return students.stream().map(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("fullName", s.getFullName());
            item.put("name", s.getFullName());
            item.put("studentCode", formatStudentCode(s.getId()));
            return item;
        }).toList();
    }

    private List<Student> loadTrackableStudentsForUser(User currentUser) {
        if (currentUser.getRole() == Role.PARENT) {
            return studentRepository.findByParentId(currentUser.getId());
        }
        if (currentUser.getRole() != Role.TEACHER) {
            return studentRepository.findAll();
        }
        Set<Long> teacherClassIds = attendanceClassRepository.findAll().stream()
                .filter(c -> c.getTeacher() != null && currentUser.getId().equals(c.getTeacher().getId()))
                .map(ClassEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Student> uniqueStudents = new LinkedHashMap<>();
        for (Long cid : teacherClassIds) {
            for (Enrollment e : attendanceEnrollmentRepository.findActiveEnrollmentsByClassId(cid)) {
                if (e.getStudent() != null) {
                    uniqueStudents.putIfAbsent(e.getStudent().getId(), e.getStudent());
                }
            }
        }
        return new ArrayList<>(uniqueStudents.values());
    }

    // =========================================================================
    // 2. BẢNG ĐIỂM DANH BUỔI HỌC & RÀNG BUỘC TIME-LOCK
    // =========================================================================

    @Override
    public LessonAttendanceSheetResponse getLessonAttendanceSheet(Long lessonId) {
        Lesson lesson = findValidLesson(lessonId);
        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();
        if (!SHEET_VIEW_ROLES.contains(userRole)) {
            throw new AttendanceMakeupException("Tài khoản không có quyền xem bảng điểm danh của lớp.");
        }

        User effectiveTeacher = resolveEffectiveTeacher(lesson);
        if (userRole == Role.TEACHER && !isSameUser(effectiveTeacher, currentUser)) {
            throw new AttendanceMakeupException("Bạn không phải giáo viên phụ trách buổi học này!");
        }

        LocalDate today = LocalDate.now(CENTER_ZONE);
        SheetEditAccess editAccess = evaluateSheetEditAccess(lesson, userRole, today);

        Long classId = lesson.getClassEntity() != null ? lesson.getClassEntity().getId() : null;
        List<Enrollment> enrollments = classId != null
                ? attendanceEnrollmentRepository.findActiveEnrollmentsByClassId(classId)
                : Collections.emptyList();

        Map<Long, MakeupRegistration> makeupByStudent = loadTargetMakeupRegistrations(lesson.getId());
        Set<Long> movedOutStudentIds = loadMovedOutStudentIds(lesson.getId());
        Map<Long, Attendance> attendanceMap = loadAttendanceMap(lesson.getId());
        Map<Long, AbsenceRequest> approvedAbsenceMap = loadApprovedAbsenceMap(lesson.getId());

        Map<Long, Student> uniqueStudents = collectSheetStudents(enrollments, movedOutStudentIds, makeupByStudent);
        boolean isAlreadySaved = !uniqueStudents.isEmpty() && attendanceMap.keySet().containsAll(uniqueStudents.keySet());

        List<StudentAttendanceItemDto> studentItems = buildSheetStudentItems(
                lesson, today, uniqueStudents, attendanceMap, approvedAbsenceMap, makeupByStudent);
        int presentCount = (int) studentItems.stream().filter(s -> "PRESENT".equals(s.getStatus())).count();
        int absentCount = (int) studentItems.stream().filter(s -> "ABSENT".equals(s.getStatus())).count();

        ClassEntity cls = lesson.getClassEntity();
        return LessonAttendanceSheetResponse.builder()
                .lessonId(lesson.getId())
                .classId(classId)
                .classCode(cls != null ? cls.getClassCode() : "N/A")
                .className(cls != null ? cls.getClassName() : lesson.getTitle())
                .courseName(cls != null && cls.getCourse() != null ? cls.getCourse().getName() : "")
                .lessonDate(lesson.getLessonDate())
                .timeRange(formatTimeRange(lesson.getStartTime(), lesson.getEndTime(), "08:00 - 09:30"))
                .roomName(lesson.getRoom() != null ? lesson.getRoom().getRoomName() : "Chưa xếp phòng")
                .branchName(cls != null && cls.getBranch() != null ? cls.getBranch().getName() : "")
                .teacherId(effectiveTeacher != null ? effectiveTeacher.getId() : null)
                .teacherName(effectiveTeacher != null ? effectiveTeacher.getFullName() : "Chưa phân công")
                .sessionNumber(lesson.getSessionNumber())
                .canEdit(editAccess.canEdit())
                .lockReason(editAccess.lockReason())
                .isAlreadySaved(isAlreadySaved)
                .totalStudents(studentItems.size())
                .presentCount(presentCount)
                .absentCount(absentCount)
                .students(studentItems)
                .build();
    }

    private SheetEditAccess evaluateSheetEditAccess(Lesson lesson, Role userRole, LocalDate today) {
        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            return new SheetEditAccess(false, "Buổi học đã bị hủy, không thể điểm danh.");
        }
        if (lesson.getLessonDate().isAfter(today)) {
            return new SheetEditAccess(false, "Buổi học ngày " + lesson.getLessonDate()
                    + " chưa diễn ra. Không được điểm danh hoặc chỉnh sửa điểm danh cho ngày tương lai.");
        }
        if (STAFF_OVERRIDE_ROLES.contains(userRole)) {
            return new SheetEditAccess(true, null);
        }
        // Quy tắc riêng cho giáo viên: lớp chưa kết thúc, đúng ngày hôm nay và đã vào giờ học
        if (lesson.getClassEntity() != null && lesson.getClassEntity().getStatus() == ClassStatus.FINISHED) {
            return new SheetEditAccess(false, "Lớp học đã kết thúc (FINISHED), giáo viên không thể chỉnh sửa điểm danh.");
        }
        if (lesson.getLessonDate().isBefore(today)) {
            return new SheetEditAccess(false, "Buổi học ngày " + lesson.getLessonDate() + " đã diễn ra trước đó. "
                    + "Theo quy định, Giáo viên chỉ được phép điểm danh trong ngày diễn ra buổi học. "
                    + "Vui lòng liên hệ Giáo vụ hoặc Admin để can thiệp chỉnh sửa!");
        }
        LocalTime nowTime = LocalTime.now(CENTER_ZONE);
        if (lesson.getStartTime() != null && nowTime.isBefore(lesson.getStartTime())) {
            String timeRangeStr = formatTimeRange(lesson.getStartTime(), lesson.getEndTime(), "");
            return new SheetEditAccess(false, "Chưa vào ca học (Ca học diễn ra lúc " + timeRangeStr
                    + "). Bạn chỉ được phép điểm danh khi ca học bắt đầu!");
        }
        return new SheetEditAccess(true, null);
    }

    private Map<Long, MakeupRegistration> loadTargetMakeupRegistrations(Long lessonId) {
        List<MakeupRegistration> makeups = makeupRegistrationRepository
                .findByTargetLessonIdAndStatusIn(lessonId, SHEET_TARGET_MAKEUP_STATUSES).stream()
                .filter(mr -> mr.getStatus() != MakeupStatus.CANCELLED || AttendanceMakeupMapper.isMissedAndLocked(mr))
                .toList();
        Map<Long, MakeupRegistration> makeupByStudent = new HashMap<>();
        for (MakeupRegistration mr : makeups) {
            if (mr.getStudent() != null) {
                Long sid = mr.getStudent().getId();
                MakeupRegistration existing = makeupByStudent.get(sid);
                if (existing == null || existing.getStatus() == MakeupStatus.CANCELLED) {
                    makeupByStudent.put(sid, mr);
                }
            }
        }
        return makeupByStudent;
    }

    private Set<Long> loadMovedOutStudentIds(Long lessonId) {
        return makeupRegistrationRepository
                .findByOriginalLessonIdAndStatusIn(lessonId, ACTIVE_MAKEUP_STATUSES).stream()
                .filter(mr -> mr.getStudent() != null)
                .map(mr -> mr.getStudent().getId())
                .collect(Collectors.toSet());
    }

    private Map<Long, Attendance> loadAttendanceMap(Long lessonId) {
        return attendanceRecordRepository.findByLessonId(lessonId).stream()
                .filter(a -> a.getStudent() != null)
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity(), (a1, a2) -> a1));
    }

    private Map<Long, AbsenceRequest> loadApprovedAbsenceMap(Long lessonId) {
        return absenceRequestRepository.findByLessonId(lessonId).stream()
                .filter(ar -> ar.getStatus() == AbsenceStatus.APPROVED && ar.getStudent() != null)
                .collect(Collectors.toMap(ar -> ar.getStudent().getId(), Function.identity(), (a1, a2) -> a1));
    }

    private Map<Long, Student> collectSheetStudents(
            List<Enrollment> enrollments, Set<Long> movedOutIds, Map<Long, MakeupRegistration> makeupByStudent) {
        Map<Long, Student> uniqueStudents = new LinkedHashMap<>();
        for (Enrollment e : enrollments) {
            if (e.getStudent() != null && !movedOutIds.contains(e.getStudent().getId())) {
                uniqueStudents.put(e.getStudent().getId(), e.getStudent());
            }
        }
        for (MakeupRegistration mr : makeupByStudent.values()) {
            if (mr.getStudent() != null) {
                uniqueStudents.putIfAbsent(mr.getStudent().getId(), mr.getStudent());
            }
        }
        return uniqueStudents;
    }

    private List<StudentAttendanceItemDto> buildSheetStudentItems(
            Lesson lesson, LocalDate today, Map<Long, Student> uniqueStudents,
            Map<Long, Attendance> attendanceMap, Map<Long, AbsenceRequest> approvedAbsenceMap,
            Map<Long, MakeupRegistration> makeupByStudent) {
        boolean autoAbsentPast = lesson.getLessonDate() != null
                && lesson.getLessonDate().isBefore(today)
                && lesson.getStatus() != LessonStatus.CANCELLED;

        List<StudentAttendanceItemDto> items = new ArrayList<>();
        int order = 1;
        for (Student s : uniqueStudents.values()) {
            Attendance att = attendanceMap.get(s.getId());
            AbsenceRequest leaveReq = approvedAbsenceMap.get(s.getId());
            MakeupRegistration mkReg = makeupByStudent.get(s.getId());

            String statusStr = resolveSheetStatus(att, leaveReq, autoAbsentPast);
            String noteStr = resolveSheetNote(att, leaveReq, autoAbsentPast);
            String absenceReasonText = (leaveReq != null && leaveReq.getReason() != null) ? leaveReq.getReason() : null;

            items.add(StudentAttendanceItemDto.builder()
                    .makeup(mkReg != null)
                    .makeupOrigin(formatMakeupOrigin(mkReg))
                    .orderNumber(order++)
                    .studentId(s.getId())
                    .studentCode(formatStudentCode(s.getId()))
                    .fullName(s.getFullName())
                    .avatarUrl(null)
                    .status(statusStr)
                    .note(noteStr)
                    .hasApprovedAbsence(leaveReq != null)
                    .absenceReason(absenceReasonText)
                    .build());
        }
        return items;
    }

    private String resolveSheetStatus(Attendance att, AbsenceRequest leaveReq, boolean autoAbsentPast) {
        if (att != null) return att.getStatus().name();
        if (leaveReq != null || autoAbsentPast) return "ABSENT";
        return null;
    }

    private String resolveSheetNote(Attendance att, AbsenceRequest leaveReq, boolean autoAbsentPast) {
        if (att != null) return att.getNote() != null ? att.getNote() : "";
        if (leaveReq != null) {
            String prefix = leaveReq.wantsMakeup() ? "Nghỉ học: " : "Nghỉ phép (không bù): ";
            return prefix + (leaveReq.getReason() != null ? leaveReq.getReason() : "");
        }
        return autoAbsentPast ? "Vắng mặt (Quá khứ chưa điểm danh)" : "";
    }

    private String formatMakeupOrigin(MakeupRegistration mkReg) {
        if (mkReg == null || mkReg.getOriginalLesson() == null) return null;
        Lesson ol = mkReg.getOriginalLesson();
        int retry = Math.max(1, AttendanceMakeupMapper.extractRetryCount(mkReg));
        return "Học bù (Xếp lịch " + retry + "/" + AttendanceMakeupMapper.MAX_MAKEUP_RETRIES + ") buổi " + ol.getSessionNumber()
                + (ol.getClassEntity() != null ? " lớp " + ol.getClassEntity().getClassName() : "")
                + " (nghỉ ngày " + ol.getLessonDate().format(DateTimeFormatter.ofPattern("dd/MM")) + ")";
    }

    // =========================================================================
    // 3. LƯU ĐIỂM DANH BATCH & ĐỒNG BỘ TRẠNG THÁI HỌC BÙ
    // =========================================================================

    @Override
    @Transactional
    public void saveLessonAttendanceBatch(Long lessonId, BatchAttendanceSaveRequest request) {
        Lesson lesson = findValidLesson(lessonId);
        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();
        if (userRole == Role.PARENT) {
            throw new AttendanceMakeupException("Tài khoản Phụ huynh không có quyền điểm danh!");
        }

        LessonAttendanceSheetResponse attendanceSheet = getLessonAttendanceSheet(lessonId);
        if (!attendanceSheet.isCanEdit()) {
            throw new AttendanceMakeupException(attendanceSheet.getLockReason());
        }

        Set<Long> roster = attendanceSheet.getStudents().stream()
                .map(StudentAttendanceItemDto::getStudentId).collect(Collectors.toSet());
        validateBatchMarkRequest(request, roster);

        for (StudentAttendanceMarkItem item : request.getAttendances()) {
            Student student = studentRepository.findById(item.getStudentId())
                    .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên với ID: " + item.getStudentId()));
            saveSingleStudentAttendance(lesson, student, item, currentUser);
        }

        if (lesson.getStatus() == LessonStatus.SCHEDULED) {
            lesson.setStatus(LessonStatus.COMPLETED);
            attendanceLessonRepository.save(lesson);
        }

        log.info("Lưu điểm danh thành công cho buổi học #{} (Lớp {}) bởi [{}] vai trò [{}]",
                lesson.getId(),
                lesson.getClassEntity() != null ? lesson.getClassEntity().getClassName() : "N/A",
                currentUser.getUsername(), userRole.name());
    }

    private void validateBatchMarkRequest(BatchAttendanceSaveRequest request, Set<Long> roster) {
        if (roster.isEmpty()) {
            throw new AttendanceMakeupException("Buổi học hiện chưa có học viên nào trong danh sách để điểm danh.");
        }
        if (request == null || request.getAttendances() == null || request.getAttendances().isEmpty()) {
            throw new AttendanceMakeupException("Danh sách điểm danh không được để trống.");
        }
        Set<Long> submitted = new HashSet<>();
        for (StudentAttendanceMarkItem item : request.getAttendances()) {
            if (item == null || item.getStudentId() == null || !submitted.add(item.getStudentId())) {
                throw new AttendanceMakeupException("Danh sách điểm danh có học viên trùng hoặc không hợp lệ.");
            }
            String normalizedStatus = item.getStatus() == null ? "" : item.getStatus().trim().toUpperCase();
            if (!"PRESENT".equals(normalizedStatus) && !"ABSENT".equals(normalizedStatus)) {
                throw new AttendanceMakeupException("Trạng thái điểm danh chỉ được phép là PRESENT (Có mặt) hoặc ABSENT (Vắng mặt).");
            }
            if (item.getNote() != null && item.getNote().trim().length() > 500) {
                throw new AttendanceMakeupException("Ghi chú điểm danh không được vượt quá 500 ký tự.");
            }
        }
        if (!submitted.equals(roster)) {
            throw new AttendanceMakeupException(
                    "Danh sách điểm danh phải bao gồm đầy đủ 100% học viên của buổi học (" + roster.size() + " học viên). Vui lòng tải lại và kiểm tra!");
        }
    }

    private void saveSingleStudentAttendance(
            Lesson lesson, Student student, StudentAttendanceMarkItem item, User currentUser) {
        AttendanceStatus status = "ABSENT".equalsIgnoreCase(item.getStatus().trim())
                ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;

        Optional<Attendance> existingOpt = attendanceRecordRepository.findByLessonIdAndStudentId(lesson.getId(), student.getId());
        Attendance attendance = existingOpt.orElseGet(() -> Attendance.builder().lesson(lesson).student(student).build());
        boolean statusChanged = existingOpt.isEmpty() || existingOpt.get().getStatus() != status;

        attendance.setStatus(status);
        attendance.setMarkedAt(LocalDateTime.now(CENTER_ZONE));
        attendance.setNote(item.getNote() != null ? item.getNote().trim() : null);
        attendanceRecordRepository.save(attendance);

        if (!statusChanged) return;

        String dateVnStr = lesson.getLessonDate() != null ? lesson.getLessonDate().format(DATE_VN_FMT) : "";
        notifyAttendanceMarked(lesson, student, status, dateVnStr, currentUser);
        syncMakeupOnAttendanceChange(lesson, student, status, item.getNote(), dateVnStr, currentUser);
    }

    private void notifyAttendanceMarked(
            Lesson lesson, Student student, AttendanceStatus status, String dateVnStr, User sender) {
        if (notificationService == null || student.getParent() == null) return;
        String statusVn = status == AttendanceStatus.PRESENT ? "Có mặt" : "Vắng mặt";
        String notifType = status == AttendanceStatus.PRESENT ? "ATTENDANCE_PRESENT" : "ATTENDANCE_ABSENT";
        notificationService.sendNotification(
                student.getParent(), sender,
                "Cập nhật điểm danh: " + student.getFullName() + " (" + statusVn + ")",
                "Bé " + student.getFullName() + " vừa được điểm danh [" + statusVn + "] tại buổi học ngày " + dateVnStr + ".",
                notifType, lesson.getId(), "/pages/attendance-tracking.html");
    }

    private void syncMakeupOnAttendanceChange(
            Lesson lesson, Student student, AttendanceStatus status, String rawNote, String dateVnStr, User currentUser) {
        if (status == AttendanceStatus.PRESENT) {
            makeupRegistrationRepository
                    .findFirstByTargetLessonIdAndStudentIdAndStatusIn(lesson.getId(), student.getId(),
                            List.of(MakeupStatus.SCHEDULED, MakeupStatus.MISSED, MakeupStatus.CANCELLED))
                    .filter(mr -> mr.getStatus() != MakeupStatus.CANCELLED || AttendanceMakeupMapper.isMissedAndLocked(mr))
                    .ifPresent(mr -> {
                        int retry = Math.max(1, AttendanceMakeupMapper.extractRetryCount(mr));
                        mr.setStatus(MakeupStatus.COMPLETED);
                        mr.setNote(AttendanceMakeupMapper.buildRetryNote(retry, false,
                                "Tự động hoàn thành sau khi giáo viên điểm danh Có mặt ngày " + dateVnStr));
                        makeupRegistrationRepository.save(mr);
                        log.info("Ca học bù #{} của học viên [{}] tự động hoàn thành (COMPLETED)", mr.getId(), student.getFullName());
                    });
            return;
        }

        Optional<MakeupRegistration> makeupOpt = makeupRegistrationRepository
                .findFirstByTargetLessonIdAndStudentIdAndStatusIn(lesson.getId(), student.getId(),
                        List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED));
        if (makeupOpt.isPresent()) {
            lockMissedMakeup(makeupOpt.get(), student, rawNote, dateVnStr, currentUser);
        } else if (lesson.getClassEntity() != null) {
            checkAndSendAttendanceThresholdNotification(student, lesson.getClassEntity(), currentUser);
        }
    }

    private void lockMissedMakeup(
            MakeupRegistration mr, Student student, String rawNote, String dateVnStr, User currentUser) {
        int retry = Math.max(1, AttendanceMakeupMapper.extractRetryCount(mr));
        String reasonSuffix = (rawNote != null && !rawNote.isBlank()) ? " - Lý do: " + rawNote.trim() : "";

        mr.setStatus(MakeupStatus.CANCELLED);
        mr.setNote(AttendanceMakeupMapper.buildRetryNote(retry, true,
                "Hủy quyền học bù do học viên đã vắng mặt tại ca học bù đã xếp (ngày " + dateVnStr + ")" + reasonSuffix));
        makeupRegistrationRepository.save(mr);
        log.info("Ca học bù #{} của học viên [{}] TỰ ĐỘNG KHÓA/HỦY do không tham gia buổi bù", mr.getId(), student.getFullName());

        if (notificationService == null) return;
        String makeupUrl = "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + mr.getId();
        if (student.getParent() != null) {
            notificationService.sendNotification(
                    student.getParent(), currentUser,
                    "Kết thúc quyền học bù: " + student.getFullName(),
                    "Bé " + student.getFullName() + " đã vắng mặt tại buổi học bù ngày " + dateVnStr
                            + ". Mỗi buổi nghỉ chỉ xếp lịch bù " + AttendanceMakeupMapper.MAX_MAKEUP_RETRIES
                            + " lần (tối đa " + AttendanceMakeupMapper.MAKEUP_LIMIT_PER_COURSE + " lần/khóa), ca học bù này đã kết thúc.",
                    "MAKEUP_LIMIT_EXCEEDED", mr.getId(), makeupUrl);
        }
        notificationService.sendToRoles(
                List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER), currentUser,
                "Học viên không tham gia ca học bù: " + student.getFullName(),
                "Học viên " + student.getFullName() + " đã vắng mặt tại buổi học bù ngày " + dateVnStr
                        + " (Ca bù #" + mr.getId() + "). Hệ thống đã tự động khóa quyền xếp lại.",
                "MAKEUP_LIMIT_EXCEEDED", mr.getId(), makeupUrl);
    }

    private void checkAndSendAttendanceThresholdNotification(Student student, ClassEntity classEntity, User sender) {
        if (notificationService == null || student == null || classEntity == null) return;
        try {
            List<Lesson> classLessons = attendanceLessonRepository
                    .findByClassEntityIdOrderBySessionNumberAscLessonDateAscStartTimeAsc(classEntity.getId());
            if (classLessons.isEmpty()) return;

            Course course = classEntity.getCourse();
            int totalSessions = (course != null && course.getTotalSessions() != null && course.getTotalSessions() > 0)
                    ? Math.max(course.getTotalSessions(), classLessons.size())
                    : classLessons.size();
            if (totalSessions <= 0) return;

            List<Long> lessonIds = classLessons.stream().map(Lesson::getId).toList();
            int absentCount = (int) attendanceRecordRepository.findByLessonIdIn(lessonIds).stream()
                    .filter(a -> a.getStudent() != null && a.getStudent().getId().equals(student.getId())
                            && a.getStatus() == AttendanceStatus.ABSENT)
                    .count();

            ThresholdMetrics metrics = computeThresholdMetrics(absentCount, totalSessions);
            if (metrics.isCritical()) {
                String title = "BÁO ĐỘNG ĐỎ (≥ 20%): " + student.getFullName() + " vượt định mức nghỉ";
                String msg = String.format("Học viên %s (Lớp %s) đã vắng %d/%d buổi (%.1f%%), chạm/vượt ngưỡng tối đa 20%% (%d buổi) — Nguy cơ cấm thi / không đạt khóa học!",
                        student.getFullName(), classEntity.getClassName(), absentCount, totalSessions, metrics.absentRate(), metrics.maxAllowedAbsences());
                if (student.getParent() != null) {
                    notificationService.sendNotification(student.getParent(), sender, title, msg,
                            "ATTENDANCE_CRITICAL", classEntity.getId(), "/pages/attendance-tracking.html");
                }
                notificationService.sendToRoles(List.of(Role.STAFF, Role.ADMIN), sender, title, msg,
                        "ATTENDANCE_CRITICAL", classEntity.getId(), "/pages/attendance-tracking.html");
            } else if (metrics.isWarning() && student.getParent() != null) {
                String title = "Cảnh báo chuyên cần (> 10%): " + student.getFullName();
                String msg = String.format("Bé %s (Lớp %s) đã vắng %d/%d buổi (%.1f%%). Bé chỉ còn %d/%d buổi phép trước khi chạm ngưỡng cấm thi 20%%.",
                        student.getFullName(), classEntity.getClassName(), absentCount, totalSessions,
                        metrics.absentRate(), metrics.remainingAllowedAbsences(), metrics.maxAllowedAbsences());
                notificationService.sendNotification(student.getParent(), sender, title, msg,
                        "ATTENDANCE_WARNING", classEntity.getId(), "/pages/attendance-tracking.html");
            }
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo cảnh báo chuyên cần cho học viên {}: {}", student.getId(), e.getMessage());
        }
    }

    // =========================================================================
    // 4. DANH SÁCH LỚP ĐANG GHI DANH & TIẾN ĐỘ CHUYÊN CẦN TOÀN KHÓA
    // =========================================================================

    @Override
    @Transactional
    public List<EnrolledClassOptionResponse> getEnrolledClassesForStudent(Long studentId) {
        User currentUser = currentUserService.getCurrentUser();
        Long effectiveStudentId = resolveEffectiveStudentId(currentUser, studentId);
        Student student = studentRepository.findById(effectiveStudentId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên với ID: " + effectiveStudentId));
        validateStudentAccess(currentUser, student);

        List<Enrollment> enrollments = attendanceEnrollmentRepository.findEnrollmentsByStudentIdAndStatusIn(
                student.getId(), TRACKED_ENROLLMENT_STATUSES);

        if (currentUser.getRole() == Role.TEACHER) {
            enrollments = enrollments.stream()
                    .filter(e -> e.getClassEntity() != null && isSameUser(e.getClassEntity().getTeacher(), currentUser))
                    .toList();
        }

        return enrollments.stream().map(e -> {
            ClassEntity c = e.getClassEntity();
            Course co = c != null ? c.getCourse() : null;
            return EnrolledClassOptionResponse.builder()
                    .classId(c != null ? c.getId() : null)
                    .classCode(c != null ? c.getClassCode() : "")
                    .className(c != null ? c.getClassName() : "")
                    .courseId(co != null ? co.getId() : null)
                    .courseName(co != null ? co.getName() : "")
                    .courseLevel(co != null ? co.getLevel() : "")
                    .totalSessions(co != null ? co.getTotalSessions() : 0)
                    .startDate(c != null ? c.getStartDate() : null)
                    .endDate(c != null ? c.getEndDate() : null)
                    .teacherName(c != null && c.getTeacher() != null ? c.getTeacher().getFullName() : "Chưa phân công")
                    .roomName(c != null && c.getRoom() != null ? c.getRoom().getRoomName() : "Chưa xếp phòng")
                    .branchName(c != null && c.getBranch() != null ? c.getBranch().getName() : "")
                    .build();
        }).toList();
    }

    @Override
    public StudentAttendanceTrackingResponse getStudentAttendanceTracking(Long studentId, Long classId) {
        User currentUser = currentUserService.getCurrentUser();
        Long effectiveStudentId = resolveEffectiveStudentId(currentUser, studentId);
        Student student = studentRepository.findById(effectiveStudentId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên với ID: " + effectiveStudentId));
        validateStudentAccess(currentUser, student);

        List<Enrollment> enrollments = attendanceEnrollmentRepository.findEnrollmentsByStudentIdAndStatusIn(
                student.getId(), TRACKED_ENROLLMENT_STATUSES);
        Long resolvedClassId = resolveTargetClassId(enrollments, classId);
        ClassEntity classEntity = attendanceClassRepository.findById(resolvedClassId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy lớp học với ID: " + resolvedClassId));

        List<Lesson> classLessons = attendanceLessonRepository
                .findByClassEntityIdOrderBySessionNumberAscLessonDateAscStartTimeAsc(classEntity.getId());
        verifyTeacherClassAccess(currentUser, classEntity, classLessons);

        Course course = classEntity.getCourse();
        int totalSessions = (course != null && course.getTotalSessions() != null && course.getTotalSessions() > 0)
                ? course.getTotalSessions() : classLessons.size();

        List<Long> lessonIds = classLessons.stream().map(Lesson::getId).toList();
        Map<Long, Attendance> attendanceByLesson = attendanceRecordRepository.findByLessonIdIn(lessonIds).stream()
                .filter(a -> a.getStudent() != null && a.getStudent().getId().equals(student.getId()))
                .collect(Collectors.toMap(a -> a.getLesson().getId(), Function.identity(), (a1, a2) -> a1));
        Map<Long, AbsenceRequest> absenceByLesson = absenceRequestRepository.findByLessonIdIn(lessonIds).stream()
                .filter(ar -> ar.getStudent() != null && ar.getStudent().getId().equals(student.getId()))
                .collect(Collectors.toMap(ar -> ar.getLesson().getId(), Function.identity(), (a1, a2) -> a1));
        Map<Long, MakeupRegistration> makeupByOriginal = makeupRegistrationRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).stream()
                .filter(m -> m.getOriginalLesson() != null)
                .collect(Collectors.toMap(m -> m.getOriginalLesson().getId(), Function.identity(), (m1, m2) -> m1));

        TrackingAccumulator acc = buildTrackingItems(
                classEntity, classLessons, totalSessions, attendanceByLesson, absenceByLesson, makeupByOriginal);
        int totalForCalc = Math.max(totalSessions, classLessons.size());
        ThresholdMetrics metrics = computeThresholdMetrics(acc.absentCount, totalForCalc);
        WarningSummary summary = buildWarningSummary(metrics, acc.absentCount, totalForCalc, acc.unmarkedCount, acc.makeupMissedCount);

        return StudentAttendanceTrackingResponse.builder()
                .studentId(student.getId())
                .studentName(student.getFullName())
                .studentCode(formatStudentCode(student.getId()))
                .classId(classEntity.getId())
                .classCode(classEntity.getClassCode())
                .className(classEntity.getClassName())
                .courseId(course != null ? course.getId() : null)
                .courseName(course != null ? course.getName() : "")
                .totalSessions(totalSessions)
                .scheduledLessonsCount(classLessons.size())
                .attendedCount(acc.attendedCount)
                .absentCount(acc.absentCount)
                .unmarkedCount(acc.unmarkedCount)
                .absentRate(metrics.absentRate())
                .maxAllowedAbsences(metrics.maxAllowedAbsences())
                .remainingAllowedAbsences(metrics.remainingAllowedAbsences())
                .makeupCompletedCount(acc.makeupCompletedCount)
                .makeupMissedCount(acc.makeupMissedCount)
                .warningLevel(summary.level())
                .warningTitle(summary.title())
                .summaryAlert(summary.alert())
                .lessons(acc.items)
                .build();
    }

    private Long resolveTargetClassId(List<Enrollment> enrollments, Long requestedClassId) {
        if (enrollments.isEmpty()) {
            throw new AttendanceMakeupException("Học viên chưa đăng ký tham gia lớp học nào!");
        }
        if (requestedClassId == null) {
            return enrollments.get(0).getClassEntity().getId();
        }
        boolean enrolled = enrollments.stream()
                .anyMatch(e -> e.getClassEntity() != null && requestedClassId.equals(e.getClassEntity().getId()));
        if (!enrolled) {
            throw new AttendanceMakeupException("Học viên không có hồ sơ ghi danh trong lớp học được chọn!");
        }
        return requestedClassId;
    }

    private void verifyTeacherClassAccess(User currentUser, ClassEntity classEntity, List<Lesson> classLessons) {
        if (currentUser.getRole() != Role.TEACHER) return;
        boolean teachesClass = isSameUser(classEntity.getTeacher(), currentUser)
                || classLessons.stream().anyMatch(l -> isSameUser(l.getTeacher(), currentUser));
        if (!teachesClass) {
            throw new AttendanceMakeupException("Bạn chỉ có quyền tra cứu tiến độ chuyên cần của học viên thuộc lớp mình phụ trách!");
        }
    }

    private TrackingAccumulator buildTrackingItems(
            ClassEntity classEntity, List<Lesson> classLessons, int totalSessions,
            Map<Long, Attendance> attendanceByLesson, Map<Long, AbsenceRequest> absenceByLesson,
            Map<Long, MakeupRegistration> makeupByOriginal) {
        TrackingAccumulator acc = new TrackingAccumulator();
        LocalDate today = LocalDate.now(CENTER_ZONE);
        LocalTime nowTime = LocalTime.now(CENTER_ZONE);

        for (int i = 0; i < classLessons.size(); i++) {
            Lesson l = classLessons.get(i);
            int sessionNum = l.getSessionNumber() != null ? l.getSessionNumber() : (i + 1);
            Attendance att = attendanceByLesson.get(l.getId());
            AbsenceRequest ar = absenceByLesson.get(l.getId());
            MakeupRegistration mr = makeupByOriginal.get(l.getId());

            LessonAttendanceState state = resolveLessonAttendanceState(l, att, ar, mr, today, nowTime);
            switch (state.status()) {
                case "PRESENT" -> acc.attendedCount++;
                case "ABSENT" -> acc.absentCount++;
                default -> acc.unmarkedCount++;
            }

            MakeupDisplay makeupDisplay = resolveMakeupDisplay(mr);
            if ("COMPLETED".equals(makeupDisplay.makeupStatus())) acc.makeupCompletedCount++;
            if ("MISSED".equals(makeupDisplay.makeupStatus())) acc.makeupMissedCount++;

            String teacherName = l.getTeacher() != null ? l.getTeacher().getFullName()
                    : (classEntity.getTeacher() != null ? classEntity.getTeacher().getFullName() : "Giáo viên trung tâm");
            String roomName = l.getRoom() != null ? l.getRoom().getRoomName()
                    : (classEntity.getRoom() != null ? classEntity.getRoom().getRoomName() : "P.Học");

            acc.items.add(StudentLessonAttendanceItemResponse.builder()
                    .lessonId(l.getId())
                    .sessionNumber(sessionNum)
                    .lessonDate(l.getLessonDate())
                    .dayOfWeekText(formatDayOfWeekWithDate(l.getLessonDate()))
                    .slot(formatTimeRange(l.getStartTime(), l.getEndTime(), "07:30 - 09:50"))
                    .roomName(roomName)
                    .teacherName(teacherName)
                    .className(classEntity.getClassName())
                    .classCode(classEntity.getClassCode())
                    .status(state.status())
                    .statusLabel(state.label())
                    .note(state.note())
                    .isMakeup(makeupDisplay.isMakeup())
                    .makeupStatus(makeupDisplay.makeupStatus())
                    .makeupInfo(makeupDisplay.makeupInfo())
                    .build());
        }

        appendPlannedSessions(acc, classEntity, classLessons, Math.max(totalSessions, classLessons.size()));
        return acc;
    }

    private LessonAttendanceState resolveLessonAttendanceState(
            Lesson l, Attendance att, AbsenceRequest ar, MakeupRegistration mr, LocalDate today, LocalTime nowTime) {
        boolean autoAbsentPast = l.getLessonDate() != null
                && l.getLessonDate().isBefore(today)
                && l.getStatus() != LessonStatus.CANCELLED;
        boolean isFutureLesson = l.getLessonDate() != null && (l.getLessonDate().isAfter(today)
                || (l.getLessonDate().isEqual(today) && l.getStartTime() != null && l.getStartTime().isAfter(nowTime)));
        String reasonSuffix = (ar != null && ar.getReason() != null && !ar.getReason().isBlank()) ? ": " + ar.getReason() : "";

        if (att != null && att.getStatus() == AttendanceStatus.PRESENT) {
            String note = (att.getNote() != null && !att.getNote().isBlank()) ? att.getNote().trim() : "-";
            return new LessonAttendanceState("PRESENT", "Có mặt", note);
        }
        if (mr != null && mr.getStatus() == MakeupStatus.COMPLETED) {
            return new LessonAttendanceState("PRESENT", "Có mặt", "Đã học bù" + reasonSuffix);
        }
        if (mr != null && mr.getStatus() == MakeupStatus.SCHEDULED) {
            return new LessonAttendanceState("NOT_MARKED", "Chưa điểm danh", "Nghỉ có phép (Chờ học bù)" + reasonSuffix);
        }
        if (att != null) {
            boolean absent = att.getStatus() == AttendanceStatus.ABSENT || autoAbsentPast;
            String note = (att.getNote() != null && !att.getNote().isBlank())
                    ? att.getNote().trim()
                    : (absent && att.getStatus() != AttendanceStatus.ABSENT ? "Vắng mặt (Quá khứ chưa điểm danh)" : "-");
            return absent
                    ? new LessonAttendanceState("ABSENT", "Vắng mặt", note)
                    : new LessonAttendanceState("NOT_MARKED", "Chưa điểm danh", note);
        }
        if (ar != null && ar.getStatus() == AbsenceStatus.APPROVED) {
            String leavePrefix = ar.wantsMakeup() ? "Nghỉ có phép: " : "Nghỉ phép (không bù): ";
            String note = leavePrefix + (ar.getReason() != null ? ar.getReason() : "");
            return isFutureLesson
                    ? new LessonAttendanceState("NOT_MARKED", "Chưa điểm danh", note)
                    : new LessonAttendanceState("ABSENT", "Vắng mặt", note);
        }
        if (autoAbsentPast) {
            return new LessonAttendanceState("ABSENT", "Vắng mặt", "Vắng mặt (Quá khứ chưa điểm danh)");
        }
        return new LessonAttendanceState("NOT_MARKED", "Chưa điểm danh", "-");
    }

    private MakeupDisplay resolveMakeupDisplay(MakeupRegistration mr) {
        if (mr == null) return new MakeupDisplay(false, null, null);
        boolean missedAndLocked = AttendanceMakeupMapper.isMissedAndLocked(mr);
        boolean noSlotClosed = AttendanceMakeupMapper.isNoSlotClosed(mr);
        boolean isMakeup = mr.getStatus() == MakeupStatus.COMPLETED || mr.getStatus() == MakeupStatus.SCHEDULED || missedAndLocked;

        if (mr.getStatus() == MakeupStatus.COMPLETED) return new MakeupDisplay(isMakeup, "COMPLETED", "Đã học bù");
        if (mr.getStatus() == MakeupStatus.SCHEDULED) return new MakeupDisplay(isMakeup, "SCHEDULED", "Đã xếp lịch bù");
        if (missedAndLocked) {
            String info = mr.getStatus() == MakeupStatus.MISSED ? "Bỏ lỡ buổi bù" : "Hết quyền bù (Vắng buổi bù)";
            return new MakeupDisplay(isMakeup, "MISSED", info);
        }
        if (noSlotClosed) return new MakeupDisplay(isMakeup, "NO_SLOT", "Đã hủy (Chưa có lớp phù hợp)");
        if (mr.getStatus() == MakeupStatus.CANCELLED) return new MakeupDisplay(isMakeup, "CANCELLED", "Đã hủy lịch bù");
        if (mr.getStatus() == MakeupStatus.PENDING) return new MakeupDisplay(isMakeup, "PENDING", "Chờ xếp lịch học bù");
        return new MakeupDisplay(isMakeup, mr.getStatus() != null ? mr.getStatus().name() : null, null);
    }

    private void appendPlannedSessions(
            TrackingAccumulator acc, ClassEntity classEntity, List<Lesson> classLessons, int totalForCalc) {
        if (acc.items.size() >= totalForCalc) return;

        Set<DayOfWeek> scheduleDays = classLessons.stream()
                .map(Lesson::getLessonDate).filter(Objects::nonNull).map(LocalDate::getDayOfWeek)
                .collect(Collectors.toSet());
        if (scheduleDays.isEmpty()) {
            scheduleDays = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        }

        LocalDate cursorDate = !classLessons.isEmpty() && classLessons.get(classLessons.size() - 1).getLessonDate() != null
                ? classLessons.get(classLessons.size() - 1).getLessonDate()
                : (classEntity.getStartDate() != null ? classEntity.getStartDate().minusDays(1) : LocalDate.now());
        Lesson templateLesson = !classLessons.isEmpty() ? classLessons.get(0) : null;
        String defaultSlot = templateLesson != null
                ? formatTimeRange(templateLesson.getStartTime(), templateLesson.getEndTime(), "08:30 - 10:00")
                : "08:30 - 10:00";
        String defaultTeacher = classEntity.getTeacher() != null ? classEntity.getTeacher().getFullName()
                : (templateLesson != null && templateLesson.getTeacher() != null ? templateLesson.getTeacher().getFullName() : "Giáo viên trung tâm");
        String defaultRoom = classEntity.getRoom() != null ? classEntity.getRoom().getRoomName()
                : (templateLesson != null && templateLesson.getRoom() != null ? templateLesson.getRoom().getRoomName() : "P.Học");

        while (acc.items.size() < totalForCalc) {
            do {
                cursorDate = cursorDate.plusDays(1);
            } while (!scheduleDays.contains(cursorDate.getDayOfWeek()));

            acc.unmarkedCount++;
            acc.items.add(StudentLessonAttendanceItemResponse.builder()
                    .lessonId(null)
                    .sessionNumber(acc.items.size() + 1)
                    .lessonDate(cursorDate)
                    .dayOfWeekText(formatDayOfWeekWithDate(cursorDate))
                    .slot(defaultSlot)
                    .roomName(defaultRoom)
                    .teacherName(defaultTeacher)
                    .className(classEntity.getClassName())
                    .classCode(classEntity.getClassCode())
                    .status("NOT_MARKED")
                    .statusLabel("Chưa điểm danh")
                    .note("-")
                    .isMakeup(false)
                    .makeupStatus(null)
                    .makeupInfo(null)
                    .build());
        }
    }

    private ThresholdMetrics computeThresholdMetrics(int absentCount, int totalSessions) {
        double absentRate = totalSessions > 0
                ? Math.round(((double) absentCount / totalSessions * 100.0) * 10.0) / 10.0
                : 0.0;
        int maxAllowed = Math.max(1, (int) Math.floor(totalSessions * 0.20));
        int remaining = Math.max(0, maxAllowed - absentCount);
        boolean critical = absentRate >= 20.0 || (totalSessions >= 5 && absentCount > maxAllowed);
        boolean warning = !critical && absentRate > 10.0;
        return new ThresholdMetrics(absentRate, maxAllowed, remaining, critical, warning);
    }

    private WarningSummary buildWarningSummary(
            ThresholdMetrics metrics, int absentCount, int totalForCalc, int unmarkedCount, int makeupMissedCount) {
        String level;
        String title;
        String alert;
        if (metrics.isCritical()) {
            level = "CRITICAL";
            title = "Báo động đỏ: Vượt ngưỡng vắng cho phép (≥ 20%) — Nguy cơ cấm thi / Không đạt khóa học";
            alert = String.format(
                    "NGUY CƠ CẤM THI / KHÔNG ĐẠT: Đã vắng %.1f%% (%d/%d buổi), chạm/vượt định mức tối đa %d buổi (20%% khóa học). Còn %d buổi chưa điểm danh — vui lòng liên hệ Giáo vụ ngay để xử lý học bù!",
                    metrics.absentRate(), absentCount, totalForCalc, metrics.maxAllowedAbsences(), unmarkedCount);
        } else if (metrics.isWarning()) {
            level = "WARNING";
            title = "Cảnh báo chuyên cần: Tỷ lệ vắng trên 10% — Sắp chạm giới hạn tối đa";
            alert = String.format(
                    "CẢNH BÁO CHUYÊN CẦN: Đã vắng %.1f%% (%d/%d buổi). Học viên chỉ còn %d/%d buổi phép trước khi chạm ngưỡng cấm thi (20%%). Còn %d buổi chưa điểm danh, vui lòng đi học đầy đủ!",
                    metrics.absentRate(), absentCount, totalForCalc, metrics.remainingAllowedAbsences(), metrics.maxAllowedAbsences(), unmarkedCount);
        } else {
            level = "NORMAL";
            title = "Chuyên cần tốt (Trong ngưỡng an toàn ≤ 10%)";
            alert = String.format(
                    "Tình trạng chuyên cần tốt: Vắng %.1f%% (%d/%d buổi), còn %d/%d buổi phép trong định mức 20%% và %d buổi chưa điểm danh.",
                    metrics.absentRate(), absentCount, totalForCalc, metrics.remainingAllowedAbsences(), metrics.maxAllowedAbsences(), unmarkedCount);
        }
        if (makeupMissedCount > 0) {
            alert += String.format(" [Lưu ý: Học viên đã bỏ lỡ %d buổi học bù.]", makeupMissedCount);
        }
        return new WarningSummary(level, title, alert);
    }

    // =========================================================================
    // 5. TIỆN ÍCH NỘI BỘ
    // =========================================================================

    private Lesson findValidLesson(Long lessonId) {
        if (lessonId == null || lessonId <= 0) {
            throw new AttendanceMakeupException("Mã buổi học không hợp lệ.");
        }
        return attendanceLessonRepository.findById(lessonId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + lessonId));
    }

    private User resolveEffectiveTeacher(Lesson lesson) {
        if (lesson == null) return null;
        if (lesson.getTeacher() != null) return lesson.getTeacher();
        return lesson.getClassEntity() != null ? lesson.getClassEntity().getTeacher() : null;
    }

    private boolean isSameUser(User u1, User u2) {
        return u1 != null && u2 != null && Objects.equals(u1.getId(), u2.getId());
    }

    private Long resolveEffectiveStudentId(User currentUser, Long requestedStudentId) {
        if (currentUser.getRole() == Role.PARENT) {
            if (requestedStudentId != null) return requestedStudentId;
            List<Student> children = studentRepository.findByParentId(currentUser.getId());
            if (children.isEmpty()) {
                throw new AttendanceMakeupException("Tài khoản phụ huynh chưa có thông tin học viên liên kết.");
            }
            return children.get(0).getId();
        }
        if (requestedStudentId == null) {
            throw new AttendanceMakeupException("Vui lòng chọn học viên cần tra cứu!");
        }
        return requestedStudentId;
    }

    private void validateStudentAccess(User currentUser, Student student) {
        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AttendanceMakeupException("Bạn chỉ có quyền xem thông tin con của mình!");
        }
    }

    private String formatStudentCode(Long studentId) {
        return String.format("HV%04d", studentId);
    }

    private String formatTimeRange(LocalTime start, LocalTime end, String fallback) {
        return (start != null && end != null) ? start.format(TIME_FMT) + " - " + end.format(TIME_FMT) : fallback;
    }

    private String formatDayOfWeekWithDate(LocalDate date) {
        if (date == null) return "";
        String dow = switch (date.getDayOfWeek()) {
            case MONDAY -> "Thứ Hai";
            case TUESDAY -> "Thứ Ba";
            case WEDNESDAY -> "Thứ Tư";
            case THURSDAY -> "Thứ Năm";
            case FRIDAY -> "Thứ Sáu";
            case SATURDAY -> "Thứ Bảy";
            case SUNDAY -> "Chủ Nhật";
        };
        return dow + " " + date.format(DATE_VN_FMT);
    }

    private record SheetEditAccess(boolean canEdit, String lockReason) {}
    private record LessonAttendanceState(String status, String label, String note) {}
    private record MakeupDisplay(boolean isMakeup, String makeupStatus, String makeupInfo) {}
    private record ThresholdMetrics(double absentRate, int maxAllowedAbsences, int remainingAllowedAbsences,
                                    boolean isCritical, boolean isWarning) {}
    private record WarningSummary(String level, String title, String alert) {}

    private static final class TrackingAccumulator {
        int attendedCount;
        int absentCount;
        int unmarkedCount;
        int makeupCompletedCount;
        int makeupMissedCount;
        final List<StudentLessonAttendanceItemResponse> items = new ArrayList<>();
    }
}
