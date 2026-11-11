package com.talent.management.features.weekly_schedule.service.impl;

import com.talent.management.features.attendance_tracking.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_tracking.dto.request.StudentAttendanceMarkItem;
import com.talent.management.features.attendance_tracking.dto.response.*;
import com.talent.management.features.weekly_schedule.dto.request.CreateClassWithLessonsRequest;

import com.talent.management.features.weekly_schedule.dto.response.*;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.features.weekly_schedule.repository.*;
import com.talent.management.features.weekly_schedule.service.ScheduleWeeklyService;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Function;
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
    private final AbsenceRequestRepository absenceRequestRepository;
    private final MakeupRegistrationRepository makeupRegistrationRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final CurrentUserService currentUserService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.talent.management.features.notification.service.NotificationService notificationService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // =========================================================================
    // 1. LẤY THỜI KHÓA BIỂU TUẦN
    // =========================================================================

    @Override
    public WeeklyScheduleResponse getWeeklySchedule(LocalDate date, Long studentId, Long teacherId, Long branchId) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now(CENTER_ZONE);
        LocalDate monday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = targetDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        int weekNumber = targetDate.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = targetDate.get(WeekFields.ISO.weekBasedYear());

        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();

        List<Lesson> lessons;
        Set<Long> viewedStudentIds = new LinkedHashSet<>();

        switch (userRole) {
            case STUDENT -> {
                // Tìm học viên theo tài khoản
                Optional<Student> studentOpt = studentRepository.findByParentId(currentUser.getId()).stream()
                        .findFirst();
                // Do not trust an arbitrary studentId when the account has no linked profile.
                Long effectiveStudentId = studentOpt.map(Student::getId).orElse(null);
                if (effectiveStudentId != null) {
                    viewedStudentIds.add(effectiveStudentId);
                    lessons = attendanceLessonRepository.findLessonsByStudentIdAndDateBetween(effectiveStudentId,
                            monday, sunday);
                } else {
                    lessons = List.of();
                }
            }
            case PARENT -> {
                if (studentId != null) {
                    // Xem lịch của bé được chọn
                    Student child = studentRepository.findByIdAndParentId(studentId, currentUser.getId())
                            .orElseThrow(() -> new AttendanceMakeupException(
                                    "Không tìm thấy học viên thuộc quyền quản lý của bạn"));
                    viewedStudentIds.add(child.getId());
                    lessons = attendanceLessonRepository.findLessonsByStudentIdAndDateBetween(child.getId(), monday,
                            sunday);
                } else {
                    // Lấy tất cả các con của phụ huynh
                    List<Student> children = studentRepository.findByParentId(currentUser.getId());
                    if (children.isEmpty()) {
                        lessons = List.of();
                    } else {
                        Set<Long> lessonIds = new HashSet<>();
                        lessons = new ArrayList<>();
                        for (Student child : children) {
                            viewedStudentIds.add(child.getId());
                            List<Lesson> childLessons = attendanceLessonRepository
                                    .findLessonsByStudentIdAndDateBetween(child.getId(), monday, sunday);
                            for (Lesson l : childLessons) {
                                if (lessonIds.add(l.getId())) {
                                    lessons.add(l);
                                }
                            }
                        }
                    }
                }
            }
            case TEACHER -> {
                Long effectiveTeacherId = (teacherId != null && (userRole == Role.ADMIN || userRole == Role.STAFF))
                        ? teacherId
                        : currentUser.getId();
                lessons = attendanceLessonRepository
                        .findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(
                                effectiveTeacherId, monday, sunday);
            }
            default -> {
                // ADMIN, STAFF, BRANCH_MANAGER
                if (teacherId != null) {
                    lessons = attendanceLessonRepository
                            .findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(
                                    teacherId, monday, sunday);
                } else if (studentId != null) {
                    viewedStudentIds.add(studentId);
                    lessons = attendanceLessonRepository.findLessonsByStudentIdAndDateBetween(studentId, monday,
                            sunday);
                } else if (branchId != null) {
                    lessons = attendanceLessonRepository
                            .findByClassEntityBranchIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(
                                    branchId, monday, sunday);
                } else {
                    lessons = attendanceLessonRepository.findByLessonDateBetweenOrderByLessonDateAscStartTimeAsc(monday,
                            sunday);
                }
            }
        }

        // Adjust each child's schedule separately so moving one sibling does not
        // remove the original lesson still attended by another sibling.
        Map<Long, Set<Long>> studentsByLesson = new HashMap<>();
        Map<Long, String> studentNames = new HashMap<>();
        Set<Long> viewedMakeupLessonIds = new HashSet<>();
        if (!viewedStudentIds.isEmpty()) {
            Map<Long, Lesson> merged = new LinkedHashMap<>();
            for (Long sid : viewedStudentIds) {
                studentRepository.findById(sid).ifPresent(s -> studentNames.put(sid, s.getFullName()));
                List<Lesson> personalLessons = attendanceLessonRepository
                        .findLessonsByStudentIdAndDateBetween(sid, monday, sunday);
                Set<Long> regularIds = personalLessons.stream().map(Lesson::getId).collect(Collectors.toSet());
                for (Lesson lesson : applyMakeupAdjustments(personalLessons, Set.of(sid), monday, sunday)) {
                    merged.put(lesson.getId(), lesson);
                    studentsByLesson.computeIfAbsent(lesson.getId(), ignored -> new LinkedHashSet<>()).add(sid);
                    if (!regularIds.contains(lesson.getId())) {
                        viewedMakeupLessonIds.add(lesson.getId());
                    }
                }
            }
            lessons = new ArrayList<>(merged.values());
        }
        if (branchId != null) {
            lessons = lessons.stream().filter(l -> l.getClassEntity() != null
                    && l.getClassEntity().getBranch() != null
                    && branchId.equals(l.getClassEntity().getBranch().getId())).toList();
        }

        // BATCH FETCH toàn bộ attendance của các buổi học trong tuần để tránh N+1
        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Map<Long, List<Attendance>> attendanceMap = lessonIds.isEmpty()
                ? Collections.emptyMap()
                : attendanceRecordRepository.findByLessonIdIn(lessonIds).stream()
                        .collect(Collectors.groupingBy(att -> att.getLesson().getId()));

        // Điều chỉnh sĩ số và danh sách học viên theo từng buổi: trừ HS chuyển đi, cộng HS học bù đến
        Map<Long, Integer> seatDelta = new HashMap<>();
        Map<Long, List<String>> makeupInDetailsMap = new HashMap<>();
        Map<Long, List<String>> makeupOutDetailsMap = new HashMap<>();
        Map<Long, Set<Long>> movedOutIdsMap = new HashMap<>();
        Map<Long, List<Student>> makeupInStudentsMap = new HashMap<>();
        if (!lessonIds.isEmpty()) {
            List<MakeupStatus> active = List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);
            for (MakeupRegistration mr : makeupRegistrationRepository.findByOriginalLessonIdInAndStatusIn(lessonIds, active)) {
                Long origId = mr.getOriginalLesson().getId();
                seatDelta.merge(origId, -1, Integer::sum);
                if (mr.getStudent() != null) {
                    movedOutIdsMap.computeIfAbsent(origId, k -> new HashSet<>()).add(mr.getStudent().getId());
                    String targetCls = (mr.getTargetLesson() != null && mr.getTargetLesson().getClassEntity() != null)
                            ? " → " + mr.getTargetLesson().getClassEntity().getClassName() : "";
                    makeupOutDetailsMap.computeIfAbsent(origId, k -> new ArrayList<>())
                            .add(mr.getStudent().getFullName() + targetCls);
                }
            }
            for (MakeupRegistration mr : makeupRegistrationRepository.findByTargetLessonIdInAndStatusIn(lessonIds, active)) {
                Long targetId = mr.getTargetLesson().getId();
                seatDelta.merge(targetId, 1, Integer::sum);
                if (mr.getStudent() != null) {
                    makeupInStudentsMap.computeIfAbsent(targetId, k -> new ArrayList<>()).add(mr.getStudent());
                    String origCls = (mr.getOriginalLesson() != null && mr.getOriginalLesson().getClassEntity() != null)
                            ? " (từ " + mr.getOriginalLesson().getClassEntity().getClassName() + ")" : "";
                    makeupInDetailsMap.computeIfAbsent(targetId, k -> new ArrayList<>())
                            .add(mr.getStudent().getFullName() + origCls);
                }
            }
        }

        // Batch fetch học viên chính thức theo lớp để đồng bộ danh sách & sĩ số từng buổi cho GV/Staff
        Map<Long, List<Student>> enrolledByClass = new HashMap<>();
        if (userRole != Role.STUDENT && userRole != Role.PARENT) {
            Set<Long> classIds = lessons.stream()
                    .map(Lesson::getClassEntity)
                    .filter(Objects::nonNull)
                    .map(ClassEntity::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!classIds.isEmpty()) {
                // Chỉ lấy học viên đã ghi danh chính thức (ENROLLED); không đưa học viên PENDING_PAYMENT (chưa đóng học phí) vào bảng điểm danh/TKB
                List<EnrollmentStatus> activeStatuses = List.of(EnrollmentStatus.ENROLLED);
                for (Enrollment e : attendanceEnrollmentRepository.findEnrollmentsByClassIdInAndStatusIn(classIds, activeStatuses)) {
                    if (e.getClassEntity() != null && e.getStudent() != null) {
                        enrolledByClass.computeIfAbsent(e.getClassEntity().getId(), k -> new ArrayList<>())
                                .add(e.getStudent());
                    }
                }
            }
        }

        LocalDate today = LocalDate.now(CENTER_ZONE);

        // Chuẩn bị danh sách Slots chuẩn theo mô hình LMS
        List<TimeSlotDto> standardSlots = getStandardSlots();

        List<LessonSlotItemResponse> lessonItems = lessons.stream().map(l -> {
            int slotNumber = mapTimeToSlotNumber(l.getStartTime());
            boolean isToday = l.getLessonDate().isEqual(today);
            boolean isPast = l.getLessonDate().isBefore(today);

            User assignedTeacher = l.getTeacher() != null
                    ? l.getTeacher()
                    : (l.getClassEntity() != null ? l.getClassEntity().getTeacher() : null);

            boolean canEdit;
            if (userRole == Role.ADMIN || userRole == Role.STAFF || userRole == Role.BRANCH_MANAGER) {
                canEdit = true;
            } else if (userRole == Role.TEACHER) {
                boolean isAssignedTeacher = assignedTeacher != null
                        && assignedTeacher.getId().equals(currentUser.getId());
                canEdit = isToday && isAssignedTeacher;
            } else {
                canEdit = false;
            }
            if (l.getStatus() == LessonStatus.CANCELLED) canEdit = false;

            List<Attendance> attendancesOfLesson = attendanceMap.getOrDefault(l.getId(), Collections.emptyList());
            List<String> makeupInDetails = makeupInDetailsMap.getOrDefault(l.getId(), Collections.emptyList());
            List<String> makeupOutDetails = makeupOutDetailsMap.getOrDefault(l.getId(), Collections.emptyList());
            int makeupInCount = makeupInDetails.size();
            int makeupOutCount = makeupOutDetails.size();

            long presentCount = attendancesOfLesson.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT)
                    .count();
            long absentCount = attendancesOfLesson.stream().filter(a -> a.getStatus() == AttendanceStatus.ABSENT)
                    .count();

            // Xác định danh sách học viên, sĩ số và trạng thái điểm danh của từng buổi
            String attendanceStatus = "NOT_MARKED";
            List<StudentLessonAttendanceResponse> studentAttendances = new ArrayList<>();
            int totalStudents;
            boolean attendanceCompleted;

            boolean autoAbsentPast = isPast && l.getStatus() != LessonStatus.CANCELLED;

            if (userRole == Role.STUDENT || userRole == Role.PARENT) {
                totalStudents = (l.getClassEntity() != null && l.getClassEntity().getCurrentStudents() != null)
                        ? l.getClassEntity().getCurrentStudents()
                        : attendancesOfLesson.size();
                totalStudents = Math.max(0, totalStudents + seatDelta.getOrDefault(l.getId(), 0));
                for (Long sid : studentsByLesson.getOrDefault(l.getId(), Set.of())) {
                    String personalStatus = attendancesOfLesson.stream()
                            .filter(a -> a.getStudent() != null && sid.equals(a.getStudent().getId()))
                            .map(a -> a.getStatus().name()).findFirst().orElse("NOT_MARKED");
                    if ("NOT_MARKED".equals(personalStatus) && autoAbsentPast) {
                        personalStatus = "ABSENT";
                    }
                    studentAttendances.add(new StudentLessonAttendanceResponse(sid,
                            studentNames.getOrDefault(sid, "Học viên " + sid), personalStatus));
                }
                if (studentAttendances.size() == 1) {
                    attendanceStatus = studentAttendances.get(0).status();
                }
                attendanceCompleted = totalStudents > 0 && attendancesOfLesson.size() >= totalStudents;
            } else {
                Long clsId = l.getClassEntity() != null ? l.getClassEntity().getId() : null;
                List<Student> classEnrolled = clsId != null
                        ? enrolledByClass.getOrDefault(clsId, Collections.emptyList())
                        : Collections.emptyList();
                Set<Long> movedOutIds = movedOutIdsMap.getOrDefault(l.getId(), Collections.emptySet());
                List<Student> makeupInStudents = makeupInStudentsMap.getOrDefault(l.getId(), Collections.emptyList());

                Map<Long, Student> sessionStudents = new LinkedHashMap<>();
                Set<Long> makeupInIds = new HashSet<>();
                for (Student s : classEnrolled) {
                    if (!movedOutIds.contains(s.getId())) {
                        sessionStudents.put(s.getId(), s);
                    }
                }
                for (Student s : makeupInStudents) {
                    sessionStudents.putIfAbsent(s.getId(), s);
                    makeupInIds.add(s.getId());
                }

                Map<Long, String> statusByStudent = attendancesOfLesson.stream()
                        .filter(a -> a.getStudent() != null && a.getStatus() != null)
                        .collect(Collectors.toMap(a -> a.getStudent().getId(), a -> a.getStatus().name(), (a1, a2) -> a1));

                for (Student s : sessionStudents.values()) {
                    String st = statusByStudent.getOrDefault(s.getId(), "NOT_MARKED");
                    if ("NOT_MARKED".equals(st) && autoAbsentPast) {
                        st = "ABSENT";
                    }
                    String label = makeupInIds.contains(s.getId()) ? s.getFullName() + " (Học bù)" : s.getFullName();
                    studentAttendances.add(new StudentLessonAttendanceResponse(s.getId(), label, st));
                }

                if (!classEnrolled.isEmpty()) {
                    totalStudents = sessionStudents.size();
                    attendanceCompleted = totalStudents > 0 && statusByStudent.keySet().containsAll(sessionStudents.keySet());
                } else {
                    int baseCount = (l.getClassEntity() != null && l.getClassEntity().getCurrentStudents() != null)
                            ? l.getClassEntity().getCurrentStudents()
                            : attendancesOfLesson.size();
                    totalStudents = Math.max(0, baseCount + seatDelta.getOrDefault(l.getId(), 0));
                    attendanceCompleted = totalStudents > 0 && attendancesOfLesson.size() >= totalStudents;
                }

                // Nếu giáo viên dạy lớp mà toàn bộ học viên của buổi đã chuyển đi học bù (0 HV) -> ẩn buổi trống khỏi lịch GV
                if (userRole == Role.TEACHER && totalStudents == 0 && makeupOutCount > 0) {
                    return null;
                }
            }

            String timeRangeStr = (l.getStartTime() != null && l.getEndTime() != null)
                    ? l.getStartTime().format(TIME_FMT) + " - " + l.getEndTime().format(TIME_FMT)
                    : "08:00 - 09:30";

            boolean isMakeupSession = !viewedStudentIds.isEmpty()
                    && (viewedMakeupLessonIds.contains(l.getId())
                            || makeupInStudentsMap.getOrDefault(l.getId(), Collections.emptyList()).stream()
                                    .anyMatch(s -> s.getId() != null && viewedStudentIds.contains(s.getId())));

            return LessonSlotItemResponse.builder()
                    .lessonId(l.getId())
                    .classId(l.getClassEntity() != null ? l.getClassEntity().getId() : null)
                    .classCode(l.getClassEntity() != null ? l.getClassEntity().getClassCode() : "N/A")
                    .className(l.getClassEntity() != null ? l.getClassEntity().getClassName() : l.getTitle())
                    .courseName(l.getClassEntity() != null && l.getClassEntity().getCourse() != null
                            ? l.getClassEntity().getCourse().getName()
                            : "")
                    .roomName(l.getRoom() != null ? l.getRoom().getRoomName() : "Chưa xếp phòng")
                    .branchName(l.getClassEntity() != null && l.getClassEntity().getBranch() != null
                            ? l.getClassEntity().getBranch().getName()
                            : "")
                    .teacherId(l.getTeacher() != null ? l.getTeacher().getId() : null)
                    .teacherName(l.getTeacher() != null ? l.getTeacher().getFullName() : "Chưa phân công")
                    .teacherUsername(l.getTeacher() != null ? l.getTeacher().getUsername() : "")
                    .lessonDate(l.getLessonDate())
                    .dayOfWeek(l.getLessonDate().getDayOfWeek().name())
                    .dayOfWeekValue(l.getLessonDate().getDayOfWeek().getValue())
                    .slotNumber(slotNumber)
                    .slotColor(switch (slotNumber) {
                        case 1 -> "#0369a1";
                        case 2 -> "#047857";
                        case 3 -> "#c2410c";
                        case 4 -> "#6d28d9";
                        default -> "#1e5346";
                    })
                    .slotBgColor(switch (slotNumber) {
                        case 1 -> "#f0f9ff";
                        case 2 -> "#f0fdf4";
                        case 3 -> "#fffbeb";
                        case 4 -> "#faf5ff";
                        default -> "#f1f5f9";
                    })
                    .slotBorderColor(switch (slotNumber) {
                        case 1 -> "#0284c7";
                        case 2 -> "#059669";
                        case 3 -> "#ea580c";
                        case 4 -> "#7c3aed";
                        default -> "#1e5346";
                    })
                    .timeRange(timeRangeStr)
                    .startTime(l.getStartTime())
                    .endTime(l.getEndTime())
                    .sessionNumber(l.getSessionNumber())
                    .status(l.getStatus() != null ? l.getStatus().name() : "SCHEDULED")
                    .isToday(isToday)
                    .isPast(isPast)
                    .canEdit(canEdit)
                    .attendanceStatus(attendanceStatus)
                    .studentAttendances(studentAttendances)
                    .attendanceCompleted(attendanceCompleted)
                    .totalStudents(totalStudents)
                    .presentCount((int) presentCount)
                    .absentCount((int) absentCount)
                    .makeupInCount(makeupInCount)
                    .makeupInDetails(makeupInDetails)
                    .makeupOutCount(makeupOutCount)
                    .makeupOutDetails(makeupOutDetails)
                    .makeupSession(isMakeupSession)
                    .build();
        }).filter(Objects::nonNull).toList();

        return WeeklyScheduleResponse.builder()
                .weekNumber(weekNumber)
                .year(year)
                .startDate(monday)
                .endDate(sunday)
                .currentDate(today)
                .currentUserRole(userRole.name())
                .currentUsername(currentUser.getUsername())
                .slots(standardSlots)
                .lessons(lessonItems)
                .build();
    }

    // =========================================================================
    // 2. LẤY BẢNG ĐIỂM DANH HỌC VIÊN CỦA BUỔI HỌC
    // =========================================================================

    /**
     * Điều chỉnh danh sách buổi học của học viên theo các đăng ký học bù đã xếp:
     * bỏ buổi gốc đã chuyển đi và thêm buổi học bù (trong khoảng tuần đang xem).
     */
    private List<Lesson> applyMakeupAdjustments(List<Lesson> lessons, Set<Long> studentIds,
            LocalDate from, LocalDate to) {
        Map<Long, Lesson> result = new LinkedHashMap<>();
        for (Lesson l : lessons) {
            result.put(l.getId(), l);
        }
        List<MakeupStatus> active = List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);
        for (Long sid : studentIds) {
            for (MakeupRegistration mr : makeupRegistrationRepository.findByStudentIdAndStatusIn(sid, active)) {
                if (mr.getOriginalLesson() != null) {
                    result.remove(mr.getOriginalLesson().getId());
                }
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

    @Override
    public LessonAttendanceSheetResponse getLessonAttendanceSheet(Long lessonId) {
        Lesson lesson = attendanceLessonRepository.findById(lessonId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + lessonId));

        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();
        if (userRole != Role.ADMIN && userRole != Role.STAFF && userRole != Role.BRANCH_MANAGER
                && userRole != Role.TEACHER) {
            throw new AttendanceMakeupException("Tài khoản không có quyền xem bảng điểm danh của lớp.");
        }
        if (userRole == Role.TEACHER && (lesson.getTeacher() == null
                || !lesson.getTeacher().getId().equals(currentUser.getId()))) {
            throw new AttendanceMakeupException("Bạn không phải giáo viên phụ trách buổi học này!");
        }
        LocalDate today = LocalDate.now(CENTER_ZONE);

        boolean canEdit;
        String lockReason = null;

        if (userRole == Role.ADMIN || userRole == Role.STAFF || userRole == Role.BRANCH_MANAGER) {
            canEdit = true;
        } else if (userRole == Role.TEACHER) {
            boolean isAssignedTeacher = lesson.getTeacher() != null
                    && lesson.getTeacher().getId().equals(currentUser.getId());
            if (!isAssignedTeacher) {
                canEdit = false;
                lockReason = "Bạn không phải giáo viên phụ trách buổi học này!";
            } else if (lesson.getLessonDate().isBefore(today)) {
                canEdit = false;
                lockReason = "Buổi học ngày " + lesson.getLessonDate() + " đã diễn ra trước đó. " +
                        "Theo quy định, Giáo viên chỉ được phép điểm danh trong ngày diễn ra buổi học. " +
                        "Vui lòng liên hệ Giáo vụ hoặc Admin để can thiệp chỉnh sửa!";
            } else if (lesson.getLessonDate().isAfter(today)) {
                canEdit = false;
                lockReason = "Buổi học chưa diễn ra (Ngày " + lesson.getLessonDate() + "). " +
                        "Theo quy định, Giáo viên chỉ có thể xem danh sách học viên và chỉ được phép điểm danh vào đúng ngày diễn ra ca học.";
            } else {
                LocalTime nowTime = LocalTime.now(CENTER_ZONE);
                if (lesson.getStartTime() != null && nowTime.isBefore(lesson.getStartTime())) {
                    canEdit = false;
                    String timeRangeStr = (lesson.getStartTime() != null && lesson.getEndTime() != null)
                            ? lesson.getStartTime().format(TIME_FMT) + " - " + lesson.getEndTime().format(TIME_FMT)
                            : lesson.getStartTime().format(TIME_FMT);
                    lockReason = "Chưa vào ca học (Ca học diễn ra lúc " + timeRangeStr + "). Bạn chỉ được phép điểm danh khi ca học bắt đầu!";
                } else {
                    canEdit = true;
                }
            }
        } else {
            canEdit = false;
            lockReason = "Tài khoản của bạn chỉ có quyền xem thông tin điểm danh.";
        }

        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            canEdit = false;
            lockReason = "Buổi học đã bị hủy, không thể điểm danh.";
        }

        // Lấy danh sách học viên chính thức của lớp
        Long classId = lesson.getClassEntity() != null ? lesson.getClassEntity().getId() : null;
        List<Enrollment> enrollments = (classId != null)
                ? attendanceEnrollmentRepository.findActiveEnrollmentsByClassId(classId)
                : Collections.emptyList();

        // Lấy danh sách học viên học bù được xếp vào buổi này
        List<MakeupStatus> activeMakeup = List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);
        List<MakeupRegistration> makeups = makeupRegistrationRepository
                .findByTargetLessonIdAndStatusIn(lesson.getId(), activeMakeup);
        Map<Long, MakeupRegistration> makeupByStudent = new HashMap<>();
        for (MakeupRegistration mr : makeups) {
            if (mr.getStudent() != null) {
                makeupByStudent.put(mr.getStudent().getId(), mr);
            }
        }
        // Học viên đã được chuyển sang buổi học bù ở lớp khác -> không còn trong buổi này
        Set<Long> movedOutStudentIds = makeupRegistrationRepository
                .findByOriginalLessonIdAndStatusIn(lesson.getId(), activeMakeup).stream()
                .filter(mr -> mr.getStudent() != null)
                .map(mr -> mr.getStudent().getId())
                .collect(Collectors.toSet());

        // Lấy tất cả bản ghi điểm danh hiện có của buổi học
        Map<Long, Attendance> attendanceMap = attendanceRecordRepository.findByLessonId(lesson.getId()).stream()
                .filter(a -> a.getStudent() != null)
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity(), (a1, a2) -> a1));

        // Lấy danh sách đơn xin nghỉ phép đã duyệt cho buổi này
        Map<Long, AbsenceRequest> approvedAbsenceMap = absenceRequestRepository.findByLessonId(lesson.getId()).stream()
                .filter(ar -> ar.getStatus() == AbsenceStatus.APPROVED && ar.getStudent() != null)
                .collect(Collectors.toMap(ar -> ar.getStudent().getId(), Function.identity(), (a1, a2) -> a1));

        // Tổng hợp danh sách học sinh: Lớp chính + Học viên học bù
        Map<Long, Student> uniqueStudents = new LinkedHashMap<>();
        for (Enrollment e : enrollments) {
            if (e.getStudent() != null && !movedOutStudentIds.contains(e.getStudent().getId())) {
                uniqueStudents.put(e.getStudent().getId(), e.getStudent());
            }
        }
        for (MakeupRegistration mr : makeups) {
            if (mr.getStudent() != null) {
                uniqueStudents.putIfAbsent(mr.getStudent().getId(), mr.getStudent());
            }
        }
        boolean isAlreadySaved = !uniqueStudents.isEmpty()
                && attendanceMap.keySet().containsAll(uniqueStudents.keySet());

        List<StudentAttendanceItemDto> studentItems = new ArrayList<>();
        int order = 1;
        int presentCount = 0;
        int absentCount = 0;

        boolean autoAbsentPast = lesson.getLessonDate() != null
                && lesson.getLessonDate().isBefore(today)
                && lesson.getStatus() != LessonStatus.CANCELLED;

        for (Student s : uniqueStudents.values()) {
            Attendance att = attendanceMap.get(s.getId());
            AbsenceRequest leaveReq = approvedAbsenceMap.get(s.getId());

            String statusStr;
            String noteStr = "";

            if (att != null) {
                statusStr = att.getStatus().name();
                noteStr = att.getNote() != null ? att.getNote() : "";
            } else if (leaveReq != null) {
                // Đã có đơn nghỉ được duyệt -> gợi ý ABSENT (Nghỉ học)
                statusStr = "ABSENT";
                noteStr = "Nghỉ học: " + (leaveReq.getReason() != null ? leaveReq.getReason() : "");
            } else if (autoAbsentPast) {
                statusStr = "ABSENT";
                noteStr = "Vắng mặt (Quá khứ chưa điểm danh)";
            } else {
                // Chưa điểm danh -> Để null, giáo viên tự chọn từ đầu
                statusStr = null;
            }

            if ("PRESENT".equals(statusStr)) {
                presentCount++;
            } else if (statusStr != null && statusStr.startsWith("ABSENT")) {
                absentCount++;
            }

            String studentCode = String.format("HV%04d", s.getId());
            boolean hasApprovedLeave = (leaveReq != null);
            String absenceReasonText = (hasApprovedLeave && leaveReq.getReason() != null) ? leaveReq.getReason() : null;

            MakeupRegistration mkReg = makeupByStudent.get(s.getId());
            String makeupOrigin = null;
            if (mkReg != null && mkReg.getOriginalLesson() != null) {
                Lesson ol = mkReg.getOriginalLesson();
                makeupOrigin = "Học bù buổi " + ol.getSessionNumber()
                        + (ol.getClassEntity() != null ? " lớp " + ol.getClassEntity().getClassName() : "")
                        + " (nghỉ ngày " + ol.getLessonDate().format(DateTimeFormatter.ofPattern("dd/MM")) + ")";
            }

            studentItems.add(StudentAttendanceItemDto.builder()
                    .makeup(mkReg != null)
                    .makeupOrigin(makeupOrigin)
                    .orderNumber(order++)
                    .studentId(s.getId())
                    .studentCode(studentCode)
                    .fullName(s.getFullName())
                    .avatarUrl(null)
                    .status(statusStr)
                    .note(noteStr)
                    .hasApprovedAbsence(hasApprovedLeave)
                    .absenceReason(absenceReasonText)
                    .build());
        }

        String timeRangeStr = (lesson.getStartTime() != null && lesson.getEndTime() != null)
                ? lesson.getStartTime().format(TIME_FMT) + " - " + lesson.getEndTime().format(TIME_FMT)
                : "08:00 - 09:30";

        return LessonAttendanceSheetResponse.builder()
                .lessonId(lesson.getId())
                .classId(classId)
                .classCode(lesson.getClassEntity() != null ? lesson.getClassEntity().getClassCode() : "N/A")
                .className(lesson.getClassEntity() != null ? lesson.getClassEntity().getClassName() : lesson.getTitle())
                .courseName(lesson.getClassEntity() != null && lesson.getClassEntity().getCourse() != null
                        ? lesson.getClassEntity().getCourse().getName()
                        : "")
                .lessonDate(lesson.getLessonDate())
                .timeRange(timeRangeStr)
                .roomName(lesson.getRoom() != null ? lesson.getRoom().getRoomName() : "Chưa xếp phòng")
                .branchName(lesson.getClassEntity() != null && lesson.getClassEntity().getBranch() != null
                        ? lesson.getClassEntity().getBranch().getName()
                        : "")
                .teacherId(lesson.getTeacher() != null ? lesson.getTeacher().getId() : null)
                .teacherName(lesson.getTeacher() != null ? lesson.getTeacher().getFullName() : "Chưa phân công")
                .sessionNumber(lesson.getSessionNumber())
                .canEdit(canEdit)
                .lockReason(lockReason)
                .isAlreadySaved(isAlreadySaved)
                .totalStudents(studentItems.size())
                .presentCount(presentCount)
                .absentCount(absentCount)
                .students(studentItems)
                .build();
    }

    // =========================================================================
    // 3. LƯU ĐIỂM DANH BATCH CẢ LỚP (SECURITY GUARD: KHÓA SAU 24H ĐỐI VỚI TEACHER)
    // =========================================================================

    @Override
    @Transactional
    public void saveLessonAttendanceBatch(Long lessonId, BatchAttendanceSaveRequest request) {
        Lesson lesson = attendanceLessonRepository.findById(lessonId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + lessonId));

        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();
        LocalDate today = LocalDate.now(CENTER_ZONE);

        // 1. Phân quyền: Học sinh/phụ huynh không được điểm danh
        if (userRole == Role.PARENT || userRole == Role.STUDENT) {
            throw new AttendanceMakeupException("Tài khoản Phụ huynh/Học viên không có quyền điểm danh!");
        }

        // 2. Ràng buộc thời gian & giáo viên (Crucial Business Rule)
        if (userRole == Role.TEACHER) {
            boolean isAssignedTeacher = lesson.getTeacher() != null
                    && lesson.getTeacher().getId().equals(currentUser.getId());
            if (!isAssignedTeacher) {
                throw new AttendanceMakeupException("Bạn không phải giáo viên phụ trách buổi học này!");
            }
            if (!lesson.getLessonDate().isEqual(today)) {
                if (lesson.getLessonDate().isBefore(today)) {
                    throw new AttendanceMakeupException(
                            "Buổi học ngày " + lesson.getLessonDate() + " đã diễn ra trước đó. " +
                                    "Theo quy định, Giáo viên chỉ được phép điểm danh trong ngày hôm đó! " +
                                    "Vui lòng liên hệ Giáo vụ hoặc Admin để can thiệp chỉnh sửa.");
                } else {
                    throw new AttendanceMakeupException(
                            "Buổi học ngày " + lesson.getLessonDate() + " chưa diễn ra. " +
                                    "Theo quy định, Giáo viên chỉ được phép điểm danh vào đúng ngày diễn ra ca học!");
                }
            }
            LocalTime nowTime = LocalTime.now(CENTER_ZONE);
            if (lesson.getStartTime() != null && nowTime.isBefore(lesson.getStartTime())) {
                String timeRangeStr = (lesson.getStartTime() != null && lesson.getEndTime() != null)
                        ? lesson.getStartTime().format(TIME_FMT) + " - " + lesson.getEndTime().format(TIME_FMT)
                        : lesson.getStartTime().format(TIME_FMT);
                throw new AttendanceMakeupException("Chưa vào ca học (Ca học diễn ra lúc " + timeRangeStr + "). Bạn chỉ được phép điểm danh khi ca học bắt đầu!");
            }
        }

        // Validate the complete roster before writing anything. Only registered
        // learners and assigned makeup learners may be marked for this lesson.
        LessonAttendanceSheetResponse attendanceSheet = getLessonAttendanceSheet(lessonId);
        if (!attendanceSheet.isCanEdit()) {
            throw new AttendanceMakeupException(attendanceSheet.getLockReason());
        }
        Set<Long> roster = attendanceSheet.getStudents().stream()
                .map(StudentAttendanceItemDto::getStudentId).collect(Collectors.toSet());
        Set<Long> submitted = new HashSet<>();
        if (request.getAttendances() == null || request.getAttendances().isEmpty()) {
            throw new AttendanceMakeupException("Danh sách điểm danh không được để trống.");
        }
        for (StudentAttendanceMarkItem item : request.getAttendances()) {
            if (item == null || item.getStudentId() == null || !submitted.add(item.getStudentId())) {
                throw new AttendanceMakeupException("Danh sách điểm danh có học viên trùng hoặc không hợp lệ.");
            }
            if (!Set.of("PRESENT", "ABSENT").contains(item.getStatus() == null ? "" : item.getStatus())) {
                throw new AttendanceMakeupException("Trạng thái điểm danh chỉ được là PRESENT hoặc ABSENT.");
            }
        }
        if (!submitted.equals(roster)) {
            throw new AttendanceMakeupException("Danh sách học viên đã thay đổi hoặc chưa đầy đủ. Vui lòng tải lại buổi học.");
        }

        // 3. Xử lý lưu từng bản ghi điểm danh
        for (StudentAttendanceMarkItem item : request.getAttendances()) {
            Student student = studentRepository.findById(item.getStudentId())
                    .orElseThrow(() -> new AttendanceMakeupException(
                            "Không tìm thấy học viên với ID: " + item.getStudentId()));

            AttendanceStatus status = AttendanceStatus.PRESENT;
            if (item.getStatus() != null && !item.getStatus().isBlank()) {
                String reqStatus = item.getStatus().trim().toUpperCase();
                if (reqStatus.contains("ABSENT")) {
                    status = AttendanceStatus.ABSENT;
                } else {
                    status = AttendanceStatus.PRESENT;
                }
            }

            Attendance attendance = attendanceRecordRepository
                    .findByLessonIdAndStudentId(lesson.getId(), student.getId())
                    .orElseGet(() -> Attendance.builder()
                            .lesson(lesson)
                            .student(student)
                            .build());

            attendance.setStatus(status);
            attendance.setMarkedAt(LocalDateTime.now());
            attendance.setNote(item.getNote() != null ? item.getNote().trim() : null);
            attendanceRecordRepository.save(attendance);
            if (notificationService != null && student.getParent() != null) {
                String statusVn = status == AttendanceStatus.PRESENT ? "Có mặt" : "Vắng mặt";
                String notifType = status == AttendanceStatus.PRESENT ? "ATTENDANCE_PRESENT" : "ATTENDANCE_ABSENT";
                notificationService.sendNotification(
                        student.getParent(), currentUser,
                        "Cập nhật điểm danh: " + student.getFullName() + " (" + statusVn + ")",
                        "Bé " + student.getFullName() + " vừa được điểm danh [" + statusVn + "] tại buổi học ngày " + lesson.getLessonDate() + ".",
                        notifType, lesson.getId(), "/pages/attendance-tracking.html");
            }

            // Nếu học sinh PRESENT và có ca bù SCHEDULED / MISSED -> tự động COMPLETED
            if (status == AttendanceStatus.PRESENT) {
                makeupRegistrationRepository
                        .findFirstByTargetLessonIdAndStudentIdAndStatusIn(lesson.getId(), student.getId(),
                                List.of(MakeupStatus.SCHEDULED, MakeupStatus.MISSED))
                        .or(() -> makeupRegistrationRepository.findFirstByTargetLessonIdAndStudentIdAndStatus(
                                lesson.getId(), student.getId(), MakeupStatus.SCHEDULED))
                        .ifPresent(mr -> {
                            int retry = Math.max(1, com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper.extractRetryCount(mr));
                            mr.setStatus(MakeupStatus.COMPLETED);
                            mr.setNote(com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper.buildRetryNote(retry, false,
                                    "Tự động hoàn thành sau khi giáo viên điểm danh Có mặt ngày " + lesson.getLessonDate()));
                            makeupRegistrationRepository.save(mr);
                            log.info("Ca học bù #{} của học viên [{}] tự động hoàn thành (COMPLETED)", mr.getId(),
                                    student.getFullName());
                        });
            } else if (status == AttendanceStatus.ABSENT) {
                Optional<MakeupRegistration> makeupOpt = makeupRegistrationRepository
                        .findFirstByTargetLessonIdAndStudentIdAndStatusIn(lesson.getId(), student.getId(),
                                List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED));
                if (makeupOpt.isPresent()) {
                    MakeupRegistration mr = makeupOpt.get();
                    int retry = Math.max(1, com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper.extractRetryCount(mr));
                    String reasonSuffix = (item.getNote() != null && !item.getNote().isBlank())
                            ? " - Lý do: " + item.getNote().trim()
                            : "";
                    if (retry >= com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper.MAX_MAKEUP_RETRIES) {
                        mr.setStatus(MakeupStatus.CANCELLED);
                        String lockBody = "Hủy quyền học bù do học viên đã vắng mặt cả 2/2 lần học bù quy định (vắng ngày " + lesson.getLessonDate() + ")" + reasonSuffix;
                        mr.setNote(com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper.buildRetryNote(retry, true, lockBody));
                        makeupRegistrationRepository.save(mr);
                        if (notificationService != null) {
                            if (student.getParent() != null) {
                                notificationService.sendNotification(
                                        student.getParent(), currentUser,
                                        "Khóa quyền học bù (Vắng 2/2 lần): " + student.getFullName(),
                                        "Bé " + student.getFullName() + " đã vắng mặt tại buổi học bù lần 2/2 ngày " + lesson.getLessonDate() + ". Theo quy định tối đa 2 lần học bù, quyền học bù của buổi nghỉ này đã bị hủy.",
                                        "MAKEUP_LIMIT_EXCEEDED", mr.getId(), "/pages/absence-makeup.html");
                            }
                            notificationService.sendToRoles(
                                    List.of(Role.STAFF, Role.ADMIN), currentUser,
                                    "Học viên vượt giới hạn 2 lần học bù: " + student.getFullName(),
                                    "Học viên " + student.getFullName() + " đã bỏ lỡ cả 2/2 lần học bù (Ca bù #" + mr.getId() + "). Hệ thống đã tự động khóa quyền xếp lại.",
                                    "MAKEUP_LIMIT_EXCEEDED", mr.getId(), "/pages/absence-makeup.html");
                        }
                    } else {
                        mr.setStatus(MakeupStatus.MISSED);
                        String missedBody = "Bỏ lỡ / Vắng mặt tại buổi học bù ngày " + lesson.getLessonDate() + " (Còn tối đa 1 lần xếp lại)" + reasonSuffix;
                        mr.setNote(com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper.buildRetryNote(retry, false, missedBody));
                        makeupRegistrationRepository.save(mr);
                        if (notificationService != null && student.getParent() != null) {
                            notificationService.sendNotification(
                                    student.getParent(), currentUser,
                                    "Cảnh báo bỏ lỡ học bù (Lần 1/2): " + student.getFullName(),
                                    "Bé " + student.getFullName() + " đã vắng mặt tại buổi học bù ngày "
                                            + lesson.getLessonDate() + ". Bé chỉ còn tối đa 1 lần xếp lịch học bù cuối cùng.",
                                    "MAKEUP_MISSED", mr.getId(), "/pages/absence-makeup.html");
                        }
                    }
                }
            }
        }

        if (lesson.getStatus() == LessonStatus.SCHEDULED) {
            lesson.setStatus(LessonStatus.COMPLETED);
            attendanceLessonRepository.save(lesson);
        }

        log.info("Lưu điểm danh thành công cho buổi học #{} (Lớp {}) bởi [{}] vai trò [{}]",
                lesson.getId(),
                lesson.getClassEntity() != null ? lesson.getClassEntity().getClassName() : "N/A",
                currentUser.getUsername(),
                userRole.name());
    }

    // =========================================================================
    // 4. TẠO LỚP HỌC MỚI VÀ TỰ ĐỘNG SINH CÁC BUỔI HỌC VÀO THỜI KHÓA BIỂU
    // =========================================================================

    @Override
    @Transactional
    public Long createClassWithLessons(CreateClassWithLessonsRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();

        if (userRole != Role.ADMIN && userRole != Role.STAFF && userRole != Role.BRANCH_MANAGER) {
            throw new AttendanceMakeupException(
                    "Chỉ Quản trị viên hoặc Nhân viên Giáo vụ mới có quyền tạo lớp học mới!");
        }

        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getEndTime().isAfter(request.getStartTime())) {
            throw new AttendanceMakeupException("Giờ kết thúc phải sau giờ bắt đầu.");
        }
        if (request.getDaysOfWeek() == null || request.getDaysOfWeek().isEmpty()
                || request.getDaysOfWeek().stream().anyMatch(d -> d == null || d < 1 || d > 7)
                || request.getTotalSessions() == null || request.getTotalSessions() < 1 || request.getTotalSessions() > 60
                || request.getMaxStudents() == null || request.getMaxStudents() < 1) {
            throw new AttendanceMakeupException("Lịch học cần có thứ hợp lệ, 1–60 buổi và sĩ số lớn hơn 0.");
        }
        if (attendanceClassRepository.existsByClassCode(request.getClassCode().trim())) {
            throw new AttendanceMakeupException(
                    "Mã lớp học '" + request.getClassCode() + "' đã tồn tại trong hệ thống!");
        }

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy khóa học với ID: " + request.getCourseId()));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(
                        () -> new AttendanceMakeupException("Không tìm thấy cơ sở với ID: " + request.getBranchId()));

        Room room = null;
        if (request.getRoomId() != null) {
            room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy phòng học."));
            if (room.getBranch() == null || !branch.getId().equals(room.getBranch().getId())) {
                throw new AttendanceMakeupException("Phòng học không thuộc cơ sở được chọn.");
            }
        }

        User teacher = null;
        if (request.getTeacherId() != null) {
            teacher = userRepository.findById(request.getTeacherId())
                    .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy giáo viên."));
            if (teacher.getRole() != Role.TEACHER) {
                throw new AttendanceMakeupException("Tài khoản được phân công phải là giáo viên.");
            }
        }

        // 1. Lưu ClassEntity
        ClassEntity classEntity = ClassEntity.builder()
                .course(course)
                .branch(branch)
                .room(room)
                .teacher(teacher)
                .classCode(request.getClassCode().trim())
                .className(request.getClassName().trim())
                .classType(request.getMaxStudents() <= 1 ? ClassType.ONE_ON_ONE : ClassType.GROUP)
                .maxStudents(request.getMaxStudents())
                .currentStudents(0)
                .startDate(request.getStartDate())
                .scheduleDescription(request.getScheduleDescription())
                .status(ClassStatus.OPEN)
                .build();

        ClassEntity savedClass = attendanceClassRepository.save(classEntity);

        // 2. Thuật toán tự động sinh danh sách N buổi học (Lesson Generator)
        List<Lesson> lessons = new ArrayList<>();
        LocalDate currentDate = request.getStartDate();
        int totalSessions = Math.min(request.getTotalSessions(), 60); // Giới hạn tối đa 60 buổi
        int sessionCount = 0;
        Set<Integer> targetDays = new HashSet<>(request.getDaysOfWeek());

        // At least one weekday is validated above; 60 weekly sessions may span over a year.
        int safetyLoop = 0;
        while (sessionCount < totalSessions && safetyLoop < totalSessions * 7) {
            int currentDayOfWeek = currentDate.getDayOfWeek().getValue();
            if (targetDays.contains(currentDayOfWeek)) {
                sessionCount++;
                Lesson lesson = Lesson.builder()
                        .classEntity(savedClass)
                        .room(room)
                        .teacher(teacher)
                        .sessionNumber(sessionCount)
                        .lessonDate(currentDate)
                        .startTime(request.getStartTime())
                        .endTime(request.getEndTime())
                        .title("Buổi " + sessionCount + ": " + savedClass.getClassName())
                        .lessonNote("Tự động sinh lịch học theo thời khóa biểu")
                        .status(LessonStatus.SCHEDULED)
                        .build();
                lessons.add(lesson);
            }
            currentDate = currentDate.plusDays(1);
            safetyLoop++;
        }

        if (!lessons.isEmpty()) {
            savedClass.setEndDate(lessons.get(lessons.size() - 1).getLessonDate());
            attendanceClassRepository.save(savedClass);
            attendanceLessonRepository.saveAll(lessons);
        }

        log.info("Tạo thành công lớp [{}] (#{}) với {} buổi học tự động sinh bởi [{}]",
                savedClass.getClassName(), savedClass.getId(), lessons.size(), currentUser.getUsername());

        return savedClass.getId();
    }

    // =========================================================================
    // 5. HELPER DATA CHO BỘ LỌC
    // =========================================================================

    @Override
    public List<Map<String, Object>> getFilterTeachers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.TEACHER)
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
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
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", c.getId());
                    map.put("classCode", c.getClassCode());
                    map.put("className", c.getClassName());
                    return map;
                })
                .toList();
    }

    // =========================================================================
    // 6. TRA CỨU TIẾN ĐỘ ĐIỂM DANH THEO KHÓA HỌC DÀNH CHO PHỤ HUYNH / HỌC VIÊN
    // =========================================================================

    @Override
    public List<EnrolledClassOptionResponse> getEnrolledClassesForStudent(Long studentId) {
        User currentUser = currentUserService.getCurrentUser();
        Long effectiveStudentId = resolveEffectiveStudentId(currentUser, studentId);

        Student student = studentRepository.findById(effectiveStudentId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy học viên với ID: " + effectiveStudentId));
        validateStudentAccess(currentUser, student);

        List<Enrollment> enrollments = attendanceEnrollmentRepository.findEnrollmentsByStudentIdAndStatusIn(
                student.getId(),
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.COMPLETED)
        );

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

        Long targetClassId = classId;
        if (targetClassId == null) {
            List<Enrollment> enrollments = attendanceEnrollmentRepository.findEnrollmentsByStudentIdAndStatusIn(
                    student.getId(),
                    List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.COMPLETED)
            );
            if (enrollments.isEmpty()) {
                throw new AttendanceMakeupException("Học viên chưa đăng ký tham gia lớp học nào!");
            }
            targetClassId = enrollments.get(0).getClassEntity().getId();
        }

        final Long resolvedClassId = targetClassId;
        ClassEntity classEntity = attendanceClassRepository.findById(resolvedClassId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy lớp học với ID: " + resolvedClassId));

        Course course = classEntity.getCourse();
        int totalSessions = (course != null && course.getTotalSessions() != null && course.getTotalSessions() > 0)
                ? course.getTotalSessions()
                : 0;

        List<Lesson> classLessons = attendanceLessonRepository
                .findByClassEntityIdOrderBySessionNumberAscLessonDateAscStartTimeAsc(classEntity.getId());

        if (totalSessions == 0) {
            totalSessions = classLessons.size();
        }

        List<Long> lessonIds = classLessons.stream().map(Lesson::getId).toList();
        Map<Long, Attendance> attendanceByLesson = attendanceRecordRepository.findByLessonIdIn(lessonIds).stream()
                .filter(a -> a.getStudent() != null && a.getStudent().getId().equals(student.getId()))
                .collect(Collectors.toMap(a -> a.getLesson().getId(), Function.identity(), (a1, a2) -> a1));

        Map<Long, AbsenceRequest> absenceByLesson = absenceRequestRepository.findByLessonIdIn(lessonIds).stream()
                .filter(ar -> ar.getStudent() != null && ar.getStudent().getId().equals(student.getId()))
                .collect(Collectors.toMap(ar -> ar.getLesson().getId(), Function.identity(), (a1, a2) -> a1));

        List<MakeupRegistration> studentMakeups = makeupRegistrationRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());
        Map<Long, MakeupRegistration> makeupByOriginal = studentMakeups.stream()
                .filter(m -> m.getOriginalLesson() != null)
                .collect(Collectors.toMap(m -> m.getOriginalLesson().getId(), Function.identity(), (m1, m2) -> m1));

        int attendedCount = 0;
        int absentCount = 0;
        int unmarkedCount = 0;
        int makeupCompletedCount = 0;
        int makeupMissedCount = 0;
        List<StudentLessonAttendanceItemResponse> lessonItems = new ArrayList<>();

        LocalDate today = LocalDate.now(CENTER_ZONE);
        for (int i = 0; i < classLessons.size(); i++) {
            Lesson l = classLessons.get(i);
            int sessionNum = l.getSessionNumber() != null ? l.getSessionNumber() : (i + 1);
            Attendance att = attendanceByLesson.get(l.getId());
            AbsenceRequest ar = absenceByLesson.get(l.getId());
            MakeupRegistration mr = makeupByOriginal.get(l.getId());
            boolean autoAbsentPast = l.getLessonDate() != null
                    && l.getLessonDate().isBefore(today)
                    && l.getStatus() != LessonStatus.CANCELLED;

            String status;
            String statusLabel;
            String note = "-";

            if (att != null) {
                if (att.getStatus() == AttendanceStatus.PRESENT) {
                    status = "PRESENT";
                    statusLabel = "Có mặt";
                    attendedCount++;
                } else if (att.getStatus() == AttendanceStatus.ABSENT || autoAbsentPast) {
                    status = "ABSENT";
                    statusLabel = "Vắng mặt";
                    absentCount++;
                } else {
                    status = "NOT_MARKED";
                    statusLabel = "Chưa điểm danh";
                    unmarkedCount++;
                }
                if (att.getNote() != null && !att.getNote().isBlank()) {
                    note = att.getNote().trim();
                } else if ("ABSENT".equals(status) && att.getStatus() != AttendanceStatus.ABSENT) {
                    note = "Vắng mặt (Quá khứ chưa điểm danh)";
                }
            } else if (ar != null && ar.getStatus() == AbsenceStatus.APPROVED) {
                status = "ABSENT";
                statusLabel = "Vắng mặt";
                absentCount++;
                note = "Nghỉ có phép: " + (ar.getReason() != null ? ar.getReason() : "");
            } else if (autoAbsentPast) {
                status = "ABSENT";
                statusLabel = "Vắng mặt";
                absentCount++;
                note = "Vắng mặt (Quá khứ chưa điểm danh)";
            } else {
                status = "NOT_MARKED";
                statusLabel = "Chưa điểm danh";
                unmarkedCount++;
            }

            boolean isMakeup = (mr != null && (mr.getStatus() == MakeupStatus.COMPLETED
                    || mr.getStatus() == MakeupStatus.SCHEDULED
                    || mr.getStatus() == MakeupStatus.MISSED));
            String makeupStatusStr = (mr != null && mr.getStatus() != null) ? mr.getStatus().name() : null;
            String makeupInfo = null;
            if (mr != null) {
                if (mr.getStatus() == MakeupStatus.COMPLETED) {
                    makeupInfo = "Đã học bù ca khác";
                    makeupCompletedCount++;
                } else if (mr.getStatus() == MakeupStatus.SCHEDULED) {
                    makeupInfo = "Đã xếp lịch học bù";
                } else if (mr.getStatus() == MakeupStatus.MISSED) {
                    makeupInfo = "Bỏ lỡ / Vắng buổi học bù";
                    makeupMissedCount++;
                } else if (mr.getStatus() == MakeupStatus.PENDING) {
                    makeupInfo = "Chờ xếp lịch học bù";
                } else if (mr.getStatus() == MakeupStatus.NO_SLOT) {
                    makeupInfo = "Chưa có lớp bù phù hợp";
                }
            }

            String dayOfWeekText = formatDayOfWeekWithDate(l.getLessonDate());
            String slotText = (l.getStartTime() != null && l.getEndTime() != null)
                    ? l.getStartTime().format(TIME_FMT) + " - " + l.getEndTime().format(TIME_FMT)
                    : "07:30 - 09:50";

            String teacherName = l.getTeacher() != null ? l.getTeacher().getFullName() :
                    (classEntity.getTeacher() != null ? classEntity.getTeacher().getFullName() : "Giáo viên trung tâm");
            String roomName = l.getRoom() != null ? l.getRoom().getRoomName() :
                    (classEntity.getRoom() != null ? classEntity.getRoom().getRoomName() : "P.Học");

            lessonItems.add(StudentLessonAttendanceItemResponse.builder()
                    .lessonId(l.getId())
                    .sessionNumber(sessionNum)
                    .lessonDate(l.getLessonDate())
                    .dayOfWeekText(dayOfWeekText)
                    .slot(slotText)
                    .roomName(roomName)
                    .teacherName(teacherName)
                    .className(classEntity.getClassName())
                    .classCode(classEntity.getClassCode())
                    .status(status)
                    .statusLabel(statusLabel)
                    .note(note)
                    .isMakeup(isMakeup)
                    .makeupStatus(makeupStatusStr)
                    .makeupInfo(makeupInfo)
                    .build());
        }

        int totalForCalc = Math.max(totalSessions, classLessons.size());

        // Nếu số buổi đã sinh trong DB ít hơn tổng số buổi của khóa học (VD: khóa 30 hoặc 32 buổi),
        // tự động hiển thị đầy đủ tất cả các buổi còn lại của khóa ở trạng thái "Chưa điểm danh"
        if (lessonItems.size() < totalForCalc) {
            java.util.Set<java.time.DayOfWeek> scheduleDays = new java.util.HashSet<>();
            for (Lesson existing : classLessons) {
                if (existing.getLessonDate() != null) {
                    scheduleDays.add(existing.getLessonDate().getDayOfWeek());
                }
            }
            if (scheduleDays.isEmpty()) {
                scheduleDays.add(java.time.DayOfWeek.MONDAY);
                scheduleDays.add(java.time.DayOfWeek.WEDNESDAY);
                scheduleDays.add(java.time.DayOfWeek.FRIDAY);
            }

            LocalDate cursorDate = !classLessons.isEmpty() && classLessons.get(classLessons.size() - 1).getLessonDate() != null
                    ? classLessons.get(classLessons.size() - 1).getLessonDate()
                    : (classEntity.getStartDate() != null ? classEntity.getStartDate().minusDays(1) : LocalDate.now());

            Lesson templateLesson = !classLessons.isEmpty() ? classLessons.get(0) : null;
            String defaultSlot = (templateLesson != null && templateLesson.getStartTime() != null && templateLesson.getEndTime() != null)
                    ? templateLesson.getStartTime().format(TIME_FMT) + " - " + templateLesson.getEndTime().format(TIME_FMT)
                    : "08:30 - 10:00";

            String defaultTeacherName = classEntity.getTeacher() != null ? classEntity.getTeacher().getFullName()
                    : (templateLesson != null && templateLesson.getTeacher() != null ? templateLesson.getTeacher().getFullName() : "Giáo viên trung tâm");
            String defaultRoomName = classEntity.getRoom() != null ? classEntity.getRoom().getRoomName()
                    : (templateLesson != null && templateLesson.getRoom() != null ? templateLesson.getRoom().getRoomName() : "P.Học");

            while (lessonItems.size() < totalForCalc) {
                cursorDate = cursorDate.plusDays(1);
                while (!scheduleDays.contains(cursorDate.getDayOfWeek())) {
                    cursorDate = cursorDate.plusDays(1);
                }
                int nextSessionNum = lessonItems.size() + 1;
                unmarkedCount++;

                lessonItems.add(StudentLessonAttendanceItemResponse.builder()
                        .lessonId(null)
                        .sessionNumber(nextSessionNum)
                        .lessonDate(cursorDate)
                        .dayOfWeekText(formatDayOfWeekWithDate(cursorDate))
                        .slot(defaultSlot)
                        .roomName(defaultRoomName)
                        .teacherName(defaultTeacherName)
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
        double absentRate = (totalForCalc > 0)
                ? Math.round(((double) absentCount / totalForCalc * 100.0) * 10.0) / 10.0
                : 0.0;

        int maxAllowedAbsences = Math.max(1, (int) Math.floor(totalForCalc * 0.20));
        int remainingAllowedAbsences = Math.max(0, maxAllowedAbsences - absentCount);
        String warningLevel;
        String warningTitle;
        String summaryAlert;

        if (absentRate >= 20.0 || (totalForCalc >= 5 && absentCount > maxAllowedAbsences)) {
            warningLevel = "CRITICAL";
            warningTitle = "Báo động đỏ: Vượt ngưỡng vắng cho phép (≥ 20%) — Nguy cơ cấm thi / Không đạt khóa học";
            summaryAlert = String.format(
                    "NGUY CƠ CẤM THI / KHÔNG ĐẠT: Đã vắng %.1f%% (%d/%d buổi), chạm/vượt định mức tối đa %d buổi (20%% khóa học). Còn %d buổi chưa điểm danh — vui lòng liên hệ Giáo vụ ngay để xử lý học bù!",
                    absentRate, absentCount, totalForCalc, maxAllowedAbsences, unmarkedCount);
        } else if (absentRate > 10.0) {
            warningLevel = "WARNING";
            warningTitle = "Cảnh báo chuyên cần: Tỷ lệ vắng trên 10% — Sắp chạm giới hạn tối đa";
            summaryAlert = String.format(
                    "CẢNH BÁO CHUYÊN CẦN: Đã vắng %.1f%% (%d/%d buổi). Học viên chỉ còn %d/%d buổi phép trước khi chạm ngưỡng cấm thi (20%%). Còn %d buổi chưa điểm danh, vui lòng đi học đầy đủ!",
                    absentRate, absentCount, totalForCalc, remainingAllowedAbsences, maxAllowedAbsences, unmarkedCount);
        } else {
            warningLevel = "NORMAL";
            warningTitle = "Chuyên cần tốt (Trong ngưỡng an toàn ≤ 10%)";
            summaryAlert = String.format(
                    "Tình trạng chuyên cần tốt: Vắng %.1f%% (%d/%d buổi), còn %d/%d buổi phép trong định mức 20%% và %d buổi chưa điểm danh.",
                    absentRate, absentCount, totalForCalc, remainingAllowedAbsences, maxAllowedAbsences, unmarkedCount);
        }

        if (makeupMissedCount > 0) {
            summaryAlert += String.format(" [Lưu ý: Học viên đã bỏ lỡ %d buổi học bù (MISSED)!]", makeupMissedCount);
        }

        return StudentAttendanceTrackingResponse.builder()
                .studentId(student.getId())
                .studentName(student.getFullName())
                .studentCode(String.format("HV%04d", student.getId()))
                .classId(classEntity.getId())
                .classCode(classEntity.getClassCode())
                .className(classEntity.getClassName())
                .courseId(course != null ? course.getId() : null)
                .courseName(course != null ? course.getName() : "")
                .totalSessions(totalSessions)
                .scheduledLessonsCount(classLessons.size())
                .attendedCount(attendedCount)
                .absentCount(absentCount)
                .unmarkedCount(unmarkedCount)
                .absentRate(absentRate)
                .maxAllowedAbsences(maxAllowedAbsences)
                .remainingAllowedAbsences(remainingAllowedAbsences)
                .makeupCompletedCount(makeupCompletedCount)
                .makeupMissedCount(makeupMissedCount)
                .warningLevel(warningLevel)
                .warningTitle(warningTitle)
                .summaryAlert(summaryAlert)
                .lessons(lessonItems)
                .build();
    }

    private Long resolveEffectiveStudentId(User currentUser, Long requestedStudentId) {
        if (currentUser.getRole() == Role.PARENT) {
            if (requestedStudentId != null) {
                return requestedStudentId;
            }
            List<Student> children = studentRepository.findByParentId(currentUser.getId());
            if (children.isEmpty()) {
                throw new AttendanceMakeupException("Tài khoản phụ huynh chưa có thông tin học viên liên kết.");
            }
            return children.get(0).getId();
        } else if (currentUser.getRole() == Role.STUDENT) {
            return studentRepository.findByAccountId(currentUser.getId())
                    .map(Student::getId)
                    .orElseThrow(() -> new AttendanceMakeupException("Tài khoản chưa được liên kết với hồ sơ học viên."));
        } else {
            if (requestedStudentId == null) {
                throw new AttendanceMakeupException("Vui lòng chọn học viên cần tra cứu!");
            }
            return requestedStudentId;
        }
    }

    private void validateStudentAccess(User currentUser, Student student) {
        if (currentUser.getRole() == Role.PARENT) {
            if (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn chỉ có quyền xem thông tin con của mình!");
            }
        } else if (currentUser.getRole() == Role.STUDENT) {
            if (student.getAccount() == null || !student.getAccount().getId().equals(currentUser.getId())) {
                throw new AttendanceMakeupException("Bạn chỉ có quyền xem thông tin của chính mình!");
            }
        }
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
        return dow + " " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    // =========================================================================

    private int mapTimeToSlotNumber(LocalTime time) {
        if (time == null)
            return 1;
        if (time.isBefore(LocalTime.of(9, 15)))
            return 1;
        if (time.isBefore(LocalTime.of(12, 30)))
            return 2;
        if (time.isBefore(LocalTime.of(15, 15)))
            return 3;
        return 4;
    }

    private List<TimeSlotDto> getStandardSlots() {
        return List.of(
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
                        .color("#6d28d9").bgColor("#faf5ff").borderColor("#7c3aed").build());
    }
}
