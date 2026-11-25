package com.talent.management.features.weekly_schedule.service.impl;

import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.features.attendance_tracking.dto.response.StudentLessonAttendanceResponse;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.weekly_schedule.dto.response.LessonSlotItemResponse;
import com.talent.management.features.weekly_schedule.dto.response.TimeSlotDto;
import com.talent.management.features.weekly_schedule.dto.response.WeeklyScheduleResponse;
import com.talent.management.features.weekly_schedule.repository.*;
import com.talent.management.features.weekly_schedule.service.ScheduleWeeklyService;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleWeeklyServiceImpl implements ScheduleWeeklyService {

    private final AttendanceLessonRepository attendanceLessonRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceEnrollmentRepository attendanceEnrollmentRepository;
    private final AttendanceClassRepository attendanceClassRepository;
    private final MakeupRegistrationRepository makeupRegistrationRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<MakeupStatus> ACTIVE_MAKEUP_STATUSES =
            List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);
    private static final List<TimeSlotDto> STANDARD_SLOTS = List.of(
            TimeSlotDto.builder().slotNumber(1).slotName("Slot 1").timeRange("07:00 - 09:00")
                    .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(9, 0))
                    .color("#0369a1").bgColor("#f0f9ff").borderColor("#0284c7").build(),
            TimeSlotDto.builder().slotNumber(2).slotName("Slot 2").timeRange("09:30 - 11:30")
                    .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(11, 30))
                    .color("#047857").bgColor("#f0fdf4").borderColor("#059669").build(),
            TimeSlotDto.builder().slotNumber(3).slotName("Slot 3").timeRange("13:00 - 15:00")
                    .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(15, 0))
                    .color("#c2410c").bgColor("#fffbeb").borderColor("#ea580c").build(),
            TimeSlotDto.builder().slotNumber(4).slotName("Slot 4").timeRange("15:30 - 17:30")
                    .startTime(LocalTime.of(15, 30)).endTime(LocalTime.of(17, 30))
                    .color("#6d28d9").bgColor("#faf5ff").borderColor("#7c3aed").build()
    );

    // =========================================================================
    // 1. THỜI KHÓA BIỂU TUẦN
    // =========================================================================

    @Override
    public WeeklyScheduleResponse getWeeklySchedule(LocalDate date, Long studentId, Long teacherId, Long branchId) {
        LocalDate targetDate = date != null ? date : LocalDate.now(CENTER_ZONE);
        LocalDate monday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = targetDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();

        Set<Long> viewedStudentIds = new LinkedHashSet<>();
        List<Lesson> lessons = loadViewerBaseLessons(currentUser, studentId, teacherId, branchId, monday, sunday, viewedStudentIds);

        ViewerMakeupContext viewerContext = applyViewedStudentMakeups(lessons, viewedStudentIds, monday, sunday);
        lessons = filterLessonsByBranch(viewerContext.lessons(), branchId);

        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Map<Long, List<Attendance>> attendanceMap = lessonIds.isEmpty()
                ? Collections.emptyMap()
                : attendanceRecordRepository.findByLessonIdIn(lessonIds).stream()
                        .collect(Collectors.groupingBy(att -> att.getLesson().getId()));
        SessionMakeupAdjustments sessionAdjustments = loadSessionMakeupAdjustments(lessonIds);
        Map<Long, List<Student>> enrolledByClass = loadEnrolledByClass(lessons, userRole);

        LocalDate today = LocalDate.now(CENTER_ZONE);
        LocalTime nowTime = LocalTime.now(CENTER_ZONE);
        List<LessonSlotItemResponse> lessonItems = lessons.stream()
                .map(l -> buildLessonSlotItem(
                        l, currentUser, userRole, today, nowTime,
                        attendanceMap, sessionAdjustments, enrolledByClass, viewerContext))
                .filter(Objects::nonNull)
                .toList();

        return WeeklyScheduleResponse.builder()
                .weekNumber(targetDate.get(WeekFields.ISO.weekOfWeekBasedYear()))
                .year(targetDate.get(WeekFields.ISO.weekBasedYear()))
                .startDate(monday)
                .endDate(sunday)
                .currentDate(today)
                .currentUserRole(userRole.name())
                .currentUsername(currentUser.getUsername())
                .slots(STANDARD_SLOTS)
                .lessons(lessonItems)
                .build();
    }

    private List<Lesson> loadViewerBaseLessons(
            User currentUser, Long studentId, Long teacherId, Long branchId,
            LocalDate monday, LocalDate sunday, Set<Long> viewedStudentIds) {
        return switch (currentUser.getRole()) {
            case PARENT -> loadParentLessons(currentUser.getId(), studentId, monday, sunday, viewedStudentIds);
            case TEACHER -> attendanceLessonRepository
                    .findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(currentUser.getId(), monday, sunday);
            default -> {
                if (teacherId != null) {
                    yield attendanceLessonRepository
                            .findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(teacherId, monday, sunday);
                }
                if (studentId != null) {
                    viewedStudentIds.add(studentId);
                    yield attendanceLessonRepository.findLessonsByStudentIdAndDateBetween(studentId, monday, sunday);
                }
                if (branchId != null) {
                    yield attendanceLessonRepository
                            .findByClassEntityBranchIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(branchId, monday, sunday);
                }
                yield attendanceLessonRepository.findByLessonDateBetweenOrderByLessonDateAscStartTimeAsc(monday, sunday);
            }
        };
    }

    private List<Lesson> loadParentLessons(
            Long parentId, Long studentId, LocalDate monday, LocalDate sunday, Set<Long> viewedStudentIds) {
        if (studentId != null) {
            Student child = studentRepository.findByIdAndParentId(studentId, parentId)
                    .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên thuộc quyền quản lý của bạn"));
            viewedStudentIds.add(child.getId());
            return attendanceLessonRepository.findLessonsByStudentIdAndDateBetween(child.getId(), monday, sunday);
        }
        List<Student> children = studentRepository.findByParentId(parentId);
        if (children.isEmpty()) return List.of();

        Set<Long> seenIds = new HashSet<>();
        List<Lesson> result = new ArrayList<>();
        for (Student child : children) {
            viewedStudentIds.add(child.getId());
            for (Lesson l : attendanceLessonRepository.findLessonsByStudentIdAndDateBetween(child.getId(), monday, sunday)) {
                if (seenIds.add(l.getId())) result.add(l);
            }
        }
        return result;
    }

    private ViewerMakeupContext applyViewedStudentMakeups(
            List<Lesson> baseLessons, Set<Long> viewedStudentIds, LocalDate monday, LocalDate sunday) {
        Map<Long, Set<Long>> studentsByLesson = new HashMap<>();
        Map<Long, String> studentNames = new HashMap<>();
        Set<Long> viewedMakeupLessonIds = new HashSet<>();
        if (viewedStudentIds.isEmpty()) {
            return new ViewerMakeupContext(baseLessons, viewedStudentIds, studentsByLesson, studentNames, viewedMakeupLessonIds);
        }

        Map<Long, Lesson> merged = new LinkedHashMap<>();
        for (Long sid : viewedStudentIds) {
            studentRepository.findById(sid).ifPresent(s -> studentNames.put(sid, s.getFullName()));
            List<Lesson> personalLessons = attendanceLessonRepository.findLessonsByStudentIdAndDateBetween(sid, monday, sunday);
            Set<Long> regularIds = personalLessons.stream().map(Lesson::getId).collect(Collectors.toSet());
            for (Lesson lesson : applyMakeupAdjustments(personalLessons, Set.of(sid), monday, sunday)) {
                merged.put(lesson.getId(), lesson);
                studentsByLesson.computeIfAbsent(lesson.getId(), ignored -> new LinkedHashSet<>()).add(sid);
                if (!regularIds.contains(lesson.getId())) {
                    viewedMakeupLessonIds.add(lesson.getId());
                }
            }
        }
        return new ViewerMakeupContext(
                new ArrayList<>(merged.values()), viewedStudentIds, studentsByLesson, studentNames, viewedMakeupLessonIds);
    }

    private List<Lesson> filterLessonsByBranch(List<Lesson> lessons, Long branchId) {
        if (branchId == null) return lessons;
        return lessons.stream()
                .filter(l -> l.getClassEntity() != null
                        && l.getClassEntity().getBranch() != null
                        && branchId.equals(l.getClassEntity().getBranch().getId()))
                .toList();
    }

    private SessionMakeupAdjustments loadSessionMakeupAdjustments(List<Long> lessonIds) {
        SessionMakeupAdjustments adj = new SessionMakeupAdjustments();
        if (lessonIds.isEmpty()) return adj;

        for (MakeupRegistration mr : makeupRegistrationRepository.findByOriginalLessonIdInAndStatusIn(lessonIds, ACTIVE_MAKEUP_STATUSES)) {
            Long origId = mr.getOriginalLesson().getId();
            adj.seatDelta.merge(origId, -1, Integer::sum);
            if (mr.getStudent() != null) {
                adj.movedOutIdsMap.computeIfAbsent(origId, k -> new HashSet<>()).add(mr.getStudent().getId());
                String targetCls = (mr.getTargetLesson() != null && mr.getTargetLesson().getClassEntity() != null)
                        ? " → " + mr.getTargetLesson().getClassEntity().getClassName() : "";
                adj.makeupOutDetailsMap.computeIfAbsent(origId, k -> new ArrayList<>())
                        .add(mr.getStudent().getFullName() + targetCls);
            }
        }
        for (MakeupRegistration mr : makeupRegistrationRepository.findByTargetLessonIdInAndStatusIn(lessonIds, ACTIVE_MAKEUP_STATUSES)) {
            Long targetId = mr.getTargetLesson().getId();
            adj.seatDelta.merge(targetId, 1, Integer::sum);
            if (mr.getStudent() != null) {
                adj.makeupInStudentsMap.computeIfAbsent(targetId, k -> new ArrayList<>()).add(mr.getStudent());
                String origCls = (mr.getOriginalLesson() != null && mr.getOriginalLesson().getClassEntity() != null)
                        ? " (từ " + mr.getOriginalLesson().getClassEntity().getClassName() + ")" : "";
                adj.makeupInDetailsMap.computeIfAbsent(targetId, k -> new ArrayList<>())
                        .add(mr.getStudent().getFullName() + origCls);
            }
        }
        return adj;
    }

    private Map<Long, List<Student>> loadEnrolledByClass(List<Lesson> lessons, Role userRole) {
        Map<Long, List<Student>> enrolledByClass = new HashMap<>();
        if (userRole == Role.PARENT) return enrolledByClass;

        Set<Long> classIds = lessons.stream()
                .map(Lesson::getClassEntity).filter(Objects::nonNull)
                .map(ClassEntity::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (classIds.isEmpty()) return enrolledByClass;

        // Chỉ đưa học viên đã ghi danh chính thức (ENROLLED) vào danh sách TKB/điểm danh
        for (Enrollment e : attendanceEnrollmentRepository.findEnrollmentsByClassIdInAndStatusIn(
                classIds, List.of(EnrollmentStatus.ENROLLED))) {
            if (e.getClassEntity() != null && e.getStudent() != null) {
                enrolledByClass.computeIfAbsent(e.getClassEntity().getId(), k -> new ArrayList<>()).add(e.getStudent());
            }
        }
        return enrolledByClass;
    }

    private LessonSlotItemResponse buildLessonSlotItem(
            Lesson l, User currentUser, Role userRole, LocalDate today, LocalTime nowTime,
            Map<Long, List<Attendance>> attendanceMap, SessionMakeupAdjustments adj,
            Map<Long, List<Student>> enrolledByClass, ViewerMakeupContext viewerCtx) {
        int slotNumber = mapTimeToSlotNumber(l.getStartTime());
        boolean isToday = l.getLessonDate().isEqual(today);
        boolean isPast = l.getLessonDate().isBefore(today);
        User effectiveTeacher = resolveEffectiveTeacher(l);
        boolean canEdit = canUserEditWeeklySlot(l, currentUser, userRole, effectiveTeacher, isToday, nowTime);

        List<Attendance> attendances = attendanceMap.getOrDefault(l.getId(), Collections.emptyList());
        List<String> makeupInDetails = adj.makeupInDetailsMap.getOrDefault(l.getId(), Collections.emptyList());
        List<String> makeupOutDetails = adj.makeupOutDetailsMap.getOrDefault(l.getId(), Collections.emptyList());

        SessionAttendanceSummary attendanceSummary = summarizeSlotAttendance(
                l, userRole, isPast, attendances, adj, enrolledByClass, viewerCtx, makeupOutDetails.size());
        if (attendanceSummary == null) return null;

        TimeSlotDto slotTheme = STANDARD_SLOTS.get(Math.max(0, Math.min(slotNumber - 1, STANDARD_SLOTS.size() - 1)));
        String timeRangeStr = (l.getStartTime() != null && l.getEndTime() != null)
                ? l.getStartTime().format(TIME_FMT) + " - " + l.getEndTime().format(TIME_FMT)
                : "08:00 - 09:30";
        boolean isMakeupSession = !viewerCtx.viewedStudentIds.isEmpty()
                && (viewerCtx.viewedMakeupLessonIds.contains(l.getId())
                        || adj.makeupInStudentsMap.getOrDefault(l.getId(), Collections.emptyList()).stream()
                                .anyMatch(s -> s.getId() != null && viewerCtx.viewedStudentIds.contains(s.getId())));

        ClassEntity cls = l.getClassEntity();
        return LessonSlotItemResponse.builder()
                .lessonId(l.getId())
                .classId(cls != null ? cls.getId() : null)
                .classCode(cls != null ? cls.getClassCode() : "N/A")
                .className(cls != null ? cls.getClassName() : l.getTitle())
                .courseName(cls != null && cls.getCourse() != null ? cls.getCourse().getName() : "")
                .roomName(l.getRoom() != null ? l.getRoom().getRoomName() : "Chưa xếp phòng")
                .branchName(cls != null && cls.getBranch() != null ? cls.getBranch().getName() : "")
                .teacherId(effectiveTeacher != null ? effectiveTeacher.getId() : null)
                .teacherName(effectiveTeacher != null ? effectiveTeacher.getFullName() : "Chưa phân công")
                .teacherUsername(effectiveTeacher != null ? effectiveTeacher.getUsername() : "")
                .lessonDate(l.getLessonDate())
                .dayOfWeek(l.getLessonDate().getDayOfWeek().name())
                .dayOfWeekValue(l.getLessonDate().getDayOfWeek().getValue())
                .slotNumber(slotNumber)
                .slotColor(slotTheme.getColor())
                .slotBgColor(slotTheme.getBgColor())
                .slotBorderColor(slotTheme.getBorderColor())
                .timeRange(timeRangeStr)
                .startTime(l.getStartTime())
                .endTime(l.getEndTime())
                .sessionNumber(l.getSessionNumber())
                .status(l.getStatus() != null ? l.getStatus().name() : "SCHEDULED")
                .isToday(isToday)
                .isPast(isPast)
                .canEdit(canEdit)
                .attendanceStatus(attendanceSummary.attendanceStatus())
                .studentAttendances(attendanceSummary.studentAttendances())
                .attendanceCompleted(attendanceSummary.attendanceCompleted())
                .totalStudents(attendanceSummary.totalStudents())
                .presentCount((int) attendances.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT).count())
                .absentCount((int) attendances.stream().filter(a -> a.getStatus() == AttendanceStatus.ABSENT).count())
                .makeupInCount(makeupInDetails.size())
                .makeupInDetails(makeupInDetails)
                .makeupOutCount(makeupOutDetails.size())
                .makeupOutDetails(makeupOutDetails)
                .makeupSession(isMakeupSession)
                .build();
    }

    private User resolveEffectiveTeacher(Lesson l) {
        if (l.getTeacher() != null) return l.getTeacher();
        return l.getClassEntity() != null ? l.getClassEntity().getTeacher() : null;
    }

    private boolean canUserEditWeeklySlot(
            Lesson l, User currentUser, Role userRole, User effectiveTeacher, boolean isToday, LocalTime nowTime) {
        if (l.getStatus() == LessonStatus.CANCELLED) return false;
        if (userRole == Role.ADMIN || userRole == Role.STAFF || userRole == Role.BRANCH_MANAGER) return true;
        if (userRole != Role.TEACHER) return false;
        if (l.getClassEntity() != null && l.getClassEntity().getStatus() == ClassStatus.FINISHED) return false;
        boolean isAssigned = effectiveTeacher != null && effectiveTeacher.getId().equals(currentUser.getId());
        boolean hasStarted = l.getStartTime() == null || !nowTime.isBefore(l.getStartTime());
        return isToday && isAssigned && hasStarted;
    }

    private SessionAttendanceSummary summarizeSlotAttendance(
            Lesson l, Role userRole, boolean isPast, List<Attendance> attendances,
            SessionMakeupAdjustments adj, Map<Long, List<Student>> enrolledByClass,
            ViewerMakeupContext viewerCtx, int makeupOutCount) {
        boolean autoAbsentPast = isPast && l.getStatus() != LessonStatus.CANCELLED;
        List<StudentLessonAttendanceResponse> studentAttendances = new ArrayList<>();

        if (userRole == Role.PARENT) {
            int baseCount = (l.getClassEntity() != null && l.getClassEntity().getCurrentStudents() != null)
                    ? l.getClassEntity().getCurrentStudents() : attendances.size();
            int totalStudents = Math.max(0, baseCount + adj.seatDelta.getOrDefault(l.getId(), 0));
            for (Long sid : viewerCtx.studentsByLesson.getOrDefault(l.getId(), Set.of())) {
                String status = attendances.stream()
                        .filter(a -> a.getStudent() != null && sid.equals(a.getStudent().getId()))
                        .map(a -> a.getStatus().name()).findFirst().orElse("NOT_MARKED");
                if ("NOT_MARKED".equals(status) && autoAbsentPast) status = "ABSENT";
                studentAttendances.add(new StudentLessonAttendanceResponse(
                        sid, viewerCtx.studentNames.getOrDefault(sid, "Học viên " + sid), status));
            }
            String overall = studentAttendances.size() == 1 ? studentAttendances.get(0).status() : "NOT_MARKED";
            boolean completed = totalStudents > 0 && attendances.size() >= totalStudents;
            return new SessionAttendanceSummary(overall, studentAttendances, totalStudents, completed);
        }

        Long clsId = l.getClassEntity() != null ? l.getClassEntity().getId() : null;
        List<Student> classEnrolled = clsId != null ? enrolledByClass.getOrDefault(clsId, Collections.emptyList()) : Collections.emptyList();
        Set<Long> movedOutIds = adj.movedOutIdsMap.getOrDefault(l.getId(), Collections.emptySet());
        List<Student> makeupInStudents = adj.makeupInStudentsMap.getOrDefault(l.getId(), Collections.emptyList());

        Map<Long, Student> sessionStudents = new LinkedHashMap<>();
        Set<Long> makeupInIds = new HashSet<>();
        for (Student s : classEnrolled) {
            if (!movedOutIds.contains(s.getId())) sessionStudents.put(s.getId(), s);
        }
        for (Student s : makeupInStudents) {
            sessionStudents.putIfAbsent(s.getId(), s);
            makeupInIds.add(s.getId());
        }

        Map<Long, String> statusByStudent = attendances.stream()
                .filter(a -> a.getStudent() != null && a.getStatus() != null)
                .collect(Collectors.toMap(a -> a.getStudent().getId(), a -> a.getStatus().name(), (a1, a2) -> a1));
        for (Student s : sessionStudents.values()) {
            String st = statusByStudent.getOrDefault(s.getId(), "NOT_MARKED");
            if ("NOT_MARKED".equals(st) && autoAbsentPast) st = "ABSENT";
            String label = makeupInIds.contains(s.getId()) ? s.getFullName() + " (Học bù)" : s.getFullName();
            studentAttendances.add(new StudentLessonAttendanceResponse(s.getId(), label, st));
        }

        int totalStudents;
        boolean completed;
        if (!classEnrolled.isEmpty()) {
            totalStudents = sessionStudents.size();
            completed = totalStudents > 0 && statusByStudent.keySet().containsAll(sessionStudents.keySet());
        } else {
            int baseCount = (l.getClassEntity() != null && l.getClassEntity().getCurrentStudents() != null)
                    ? l.getClassEntity().getCurrentStudents() : attendances.size();
            totalStudents = Math.max(0, baseCount + adj.seatDelta.getOrDefault(l.getId(), 0));
            completed = totalStudents > 0 && attendances.size() >= totalStudents;
        }

        // Ẩn buổi trống khỏi lịch giáo viên khi toàn bộ học viên của buổi đã chuyển đi học bù
        if (userRole == Role.TEACHER && totalStudents == 0 && makeupOutCount > 0) return null;
        return new SessionAttendanceSummary("NOT_MARKED", studentAttendances, totalStudents, completed);
    }

    /**
     * Điều chỉnh danh sách buổi học của học viên theo các đăng ký học bù đã xếp:
     * bỏ buổi gốc đã chuyển đi và thêm buổi học bù (trong khoảng tuần đang xem).
     */
    private List<Lesson> applyMakeupAdjustments(List<Lesson> lessons, Set<Long> studentIds, LocalDate from, LocalDate to) {
        Map<Long, Lesson> result = new LinkedHashMap<>();
        for (Lesson l : lessons) result.put(l.getId(), l);
        for (Long sid : studentIds) {
            for (MakeupRegistration mr : makeupRegistrationRepository.findByStudentIdAndStatusIn(sid, ACTIVE_MAKEUP_STATUSES)) {
                if (mr.getOriginalLesson() != null) result.remove(mr.getOriginalLesson().getId());
                Lesson target = mr.getTargetLesson();
                if (target != null && !target.getLessonDate().isBefore(from) && !target.getLessonDate().isAfter(to)) {
                    result.putIfAbsent(target.getId(), target);
                }
            }
        }
        List<Lesson> merged = new ArrayList<>(result.values());
        merged.sort(Comparator.comparing(Lesson::getLessonDate)
                .thenComparing(Lesson::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return merged;
    }

    // =========================================================================
    // 2. DỮ LIỆU BỘ LỌC TKB
    // =========================================================================

    @Override
    public List<Map<String, Object>> getFilterTeachers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.TEACHER)
                .map(u -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", u.getId());
                    map.put("fullName", u.getFullName());
                    map.put("username", u.getUsername());
                    return map;
                })
                .toList();
    }

    @Override
    public List<Map<String, Object>> getFilterClasses() {
        return attendanceClassRepository.findAll().stream()
                .map(c -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", c.getId());
                    map.put("classCode", c.getClassCode());
                    map.put("className", c.getClassName());
                    return map;
                })
                .toList();
    }

    private int mapTimeToSlotNumber(LocalTime time) {
        if (time == null || time.isBefore(LocalTime.of(9, 15))) return 1;
        if (time.isBefore(LocalTime.of(12, 30))) return 2;
        if (time.isBefore(LocalTime.of(15, 15))) return 3;
        return 4;
    }

    private record ViewerMakeupContext(
            List<Lesson> lessons,
            Set<Long> viewedStudentIds,
            Map<Long, Set<Long>> studentsByLesson,
            Map<Long, String> studentNames,
            Set<Long> viewedMakeupLessonIds) {}

    private static final class SessionMakeupAdjustments {
        final Map<Long, Integer> seatDelta = new HashMap<>();
        final Map<Long, List<String>> makeupInDetailsMap = new HashMap<>();
        final Map<Long, List<String>> makeupOutDetailsMap = new HashMap<>();
        final Map<Long, Set<Long>> movedOutIdsMap = new HashMap<>();
        final Map<Long, List<Student>> makeupInStudentsMap = new HashMap<>();
    }

    private record SessionAttendanceSummary(
            String attendanceStatus,
            List<StudentLessonAttendanceResponse> studentAttendances,
            int totalStudents,
            boolean attendanceCompleted) {}
}
