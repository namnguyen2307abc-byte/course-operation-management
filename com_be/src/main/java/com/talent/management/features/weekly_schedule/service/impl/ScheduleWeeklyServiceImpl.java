package com.talent.management.features.weekly_schedule.service.impl;

import com.talent.management.features.attendance_tracking.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_tracking.dto.response.*;
import com.talent.management.features.attendance_tracking.service.AttendanceTrackingService;
import com.talent.management.features.weekly_schedule.dto.request.CreateClassWithLessonsRequest;
import com.talent.management.features.weekly_schedule.dto.response.*;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.features.weekly_schedule.repository.*;
import com.talent.management.features.weekly_schedule.service.ScheduleWeeklyService;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.features.class_management.service.ClassSchedulingService;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
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
    private final CourseRepository courseRepository;
    private final BranchRepository branchRepository;
    private final CurrentUserService currentUserService;
    private final AttendanceTrackingService attendanceTrackingService;
    private final ClassSchedulingService schedulingService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_VN_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
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
                Optional<Student> studentOpt = studentRepository.findByAccountId(currentUser.getId());
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
                    Student child = studentRepository.findByIdAndParentId(studentId, currentUser.getId())
                            .orElseThrow(() -> new AttendanceMakeupException(
                                    "Không tìm thấy học viên thuộc quyền quản lý của bạn"));
                    viewedStudentIds.add(child.getId());
                    lessons = attendanceLessonRepository.findLessonsByStudentIdAndDateBetween(child.getId(), monday,
                            sunday);
                } else {
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
                Long effectiveTeacherId = currentUser.getId();
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

        // Điều chỉnh lịch học theo đăng ký học bù cho từng học viên đang xem
        Map<Long, Set<Long>> studentsByLesson = new HashMap<>();
        Map<Long, String> studentNames = new HashMap<>();
        if (!viewedStudentIds.isEmpty()) {
            Map<Long, Lesson> merged = new LinkedHashMap<>();
            for (Long sid : viewedStudentIds) {
                studentRepository.findById(sid).ifPresent(s -> studentNames.put(sid, s.getFullName()));
                List<Lesson> personalLessons = attendanceLessonRepository
                        .findLessonsByStudentIdAndDateBetween(sid, monday, sunday);
                for (Lesson lesson : applyMakeupAdjustments(personalLessons, Set.of(sid), monday, sunday)) {
                    merged.put(lesson.getId(), lesson);
                    studentsByLesson.computeIfAbsent(lesson.getId(), ignored -> new LinkedHashSet<>()).add(sid);
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

        // Tính sĩ số thực tế của từng lớp từ bảng Enrollment để luôn đồng bộ với bảng điểm danh
        Map<Long, Integer> activeClassEnrollmentCount = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.getClassEntity() != null && l.getClassEntity().getId() != null) {
                Long cid = l.getClassEntity().getId();
                activeClassEnrollmentCount.computeIfAbsent(cid,
                        id -> attendanceEnrollmentRepository.findActiveEnrollmentsByClassId(id).size());
            }
        }

        // Điều chỉnh sĩ số: trừ HS chuyển đi, cộng HS học bù đến (bao gồm SCHEDULED, COMPLETED, MISSED)
        Map<Long, Integer> seatDelta = new HashMap<>();
        if (!lessonIds.isEmpty()) {
            List<MakeupStatus> active = List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);
            for (MakeupRegistration mr : makeupRegistrationRepository.findByOriginalLessonIdInAndStatusIn(lessonIds, active)) {
                seatDelta.merge(mr.getOriginalLesson().getId(), -1, Integer::sum);
            }
            for (MakeupRegistration mr : makeupRegistrationRepository.findByTargetLessonIdInAndStatusIn(lessonIds, active)) {
                seatDelta.merge(mr.getTargetLesson().getId(), 1, Integer::sum);
            }
        }

        LocalDate today = LocalDate.now(CENTER_ZONE);
        LocalTime nowTime = LocalTime.now(CENTER_ZONE);
        List<TimeSlotDto> standardSlots = getStandardSlots();

        List<LessonSlotItemResponse> lessonItems = lessons.stream().map(l -> {
            int slotNumber = mapTimeToSlotNumber(l.getStartTime());
            boolean isToday = l.getLessonDate().isEqual(today);
            boolean isPast = l.getLessonDate().isBefore(today);

            User effectiveTeacher = l.getTeacher() != null
                    ? l.getTeacher()
                    : (l.getClassEntity() != null ? l.getClassEntity().getTeacher() : null);

            boolean canEdit;
            if (userRole == Role.ADMIN || userRole == Role.STAFF || userRole == Role.BRANCH_MANAGER) {
                canEdit = true;
            } else if (userRole == Role.TEACHER) {
                boolean isAssignedTeacher = effectiveTeacher != null
                        && effectiveTeacher.getId().equals(currentUser.getId());
                boolean hasStarted = (l.getStartTime() == null || !nowTime.isBefore(l.getStartTime()));
                canEdit = isToday && isAssignedTeacher && hasStarted;
            } else {
                canEdit = false;
            }
            if (l.getStatus() == LessonStatus.CANCELLED
                    || (l.getClassEntity() != null && l.getClassEntity().getStatus() == ClassStatus.FINISHED && userRole == Role.TEACHER)) {
                canEdit = false;
            }

            List<Attendance> attendancesOfLesson = attendanceMap.getOrDefault(l.getId(), Collections.emptyList());
            int baseClassCount = 0;
            if (l.getClassEntity() != null && l.getClassEntity().getId() != null) {
                int enrolledActive = activeClassEnrollmentCount.getOrDefault(l.getClassEntity().getId(), 0);
                int classCurrent = l.getClassEntity().getCurrentStudents() != null ? l.getClassEntity().getCurrentStudents() : 0;
                baseClassCount = enrolledActive > 0 ? enrolledActive : classCurrent;
            } else {
                baseClassCount = attendancesOfLesson.size();
            }
            int totalStudents = Math.max(0, baseClassCount + seatDelta.getOrDefault(l.getId(), 0));
            long presentCount = attendancesOfLesson.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT)
                    .count();
            long absentCount = attendancesOfLesson.stream().filter(a -> a.getStatus() == AttendanceStatus.ABSENT)
                    .count();

            // Xác định trạng thái điểm danh cho người xem
            String attendanceStatus = "NOT_MARKED";
            List<StudentLessonAttendanceResponse> studentAttendances = new ArrayList<>();
            if (userRole == Role.STUDENT || userRole == Role.PARENT) {
                for (Long sid : studentsByLesson.getOrDefault(l.getId(), Set.of())) {
                    String personalStatus = attendancesOfLesson.stream()
                            .filter(a -> a.getStudent() != null && sid.equals(a.getStudent().getId()))
                            .map(a -> a.getStatus().name()).findFirst().orElse("NOT_MARKED");
                    studentAttendances.add(new StudentLessonAttendanceResponse(sid,
                            studentNames.getOrDefault(sid, "Học viên " + sid), personalStatus));
                }
                if (studentAttendances.size() == 1) {
                    attendanceStatus = studentAttendances.get(0).status();
                }
            }

            String timeRangeStr = (l.getStartTime() != null && l.getEndTime() != null)
                    ? l.getStartTime().format(TIME_FMT) + " - " + l.getEndTime().format(TIME_FMT)
                    : "08:00 - 09:30";

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
                    .teacherId(effectiveTeacher != null ? effectiveTeacher.getId() : null)
                    .teacherName(effectiveTeacher != null ? effectiveTeacher.getFullName() : "Chưa phân công")
                    .teacherUsername(effectiveTeacher != null ? effectiveTeacher.getUsername() : "")
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
                    .attendanceCompleted(totalStudents > 0 && attendancesOfLesson.size() >= totalStudents)
                    .totalStudents(totalStudents)
                    .presentCount((int) presentCount)
                    .absentCount((int) absentCount)
                    .build();
        }).toList();

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

    // =========================================================================
    // 2. TẠO LỚP HỌC MỚI VÀ TỰ ĐỘNG SINH CÁC BUỔI HỌC VÀO THỜI KHÓA BIỂU
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

        if (request == null) {
            throw new AttendanceMakeupException("Dữ liệu tạo lớp học không hợp lệ.");
        }
        if (request.getClassCode() == null || request.getClassCode().trim().isEmpty()) {
            throw new AttendanceMakeupException("Mã lớp không được để trống.");
        }
        if (request.getClassCode().trim().length() > 50) {
            throw new AttendanceMakeupException("Mã lớp không được vượt quá 50 ký tự.");
        }
        if (request.getClassName() == null || request.getClassName().trim().isEmpty()) {
            throw new AttendanceMakeupException("Tên lớp không được để trống.");
        }
        if (request.getClassName().trim().length() > 150) {
            throw new AttendanceMakeupException("Tên lớp không được vượt quá 150 ký tự.");
        }
        if (request.getStartDate() == null) {
            throw new AttendanceMakeupException("Ngày bắt đầu khai giảng không được để trống.");
        }
        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getEndTime().isAfter(request.getStartTime())) {
            throw new AttendanceMakeupException("Giờ kết thúc phải sau giờ bắt đầu.");
        }
        if (Duration.between(request.getStartTime(), request.getEndTime()).toMinutes() < 30) {
            throw new AttendanceMakeupException("Thời lượng một buổi học phải tối thiểu từ 30 phút trở lên.");
        }
        if (request.getDaysOfWeek() == null || request.getDaysOfWeek().isEmpty()
                || request.getDaysOfWeek().stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
            throw new AttendanceMakeupException("Vui lòng chọn ít nhất một thứ hợp lệ trong tuần (Thứ 2 đến Chủ nhật).");
        }
        if (request.getTotalSessions() == null || request.getTotalSessions() < 1 || request.getTotalSessions() > 60) {
            throw new AttendanceMakeupException("Tổng số buổi học phải nằm trong khoảng từ 1 đến 60 buổi.");
        }
        if (request.getMaxStudents() == null || request.getMaxStudents() < 1 || request.getMaxStudents() > 100) {
            throw new AttendanceMakeupException("Sĩ số tối đa của lớp phải nằm trong khoảng từ 1 đến 100 học viên.");
        }
        if (attendanceClassRepository.existsByClassCode(request.getClassCode().trim())) {
            throw new AttendanceMakeupException(
                    "Mã lớp học '" + request.getClassCode().trim() + "' đã tồn tại trong hệ thống!");
        }

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new AttendanceMakeupException(
                        "Không tìm thấy khóa học với ID: " + request.getCourseId()));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(
                        () -> new AttendanceMakeupException("Không tìm thấy cơ sở với ID: " + request.getBranchId()));

        User teacher = schedulingService.getTeacher(request.getTeacherId());
        Room room = schedulingService.getRoom(request.getRoomId());
        schedulingService.validateAssignments(course, branch, teacher, room, request.getMaxStudents());

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
                .scheduleDescription(schedulingService.describeSchedule(request.getDaysOfWeek(), request.getStartTime(), request.getEndTime()))
                .status(ClassStatus.OPEN)
                .build();

        List<Lesson> lessons = schedulingService.generateLessons(classEntity, request.getDaysOfWeek(),
                request.getStartTime(), request.getEndTime(), request.getTotalSessions(), null);
        schedulingService.validateReadiness(ClassStatus.OPEN, !lessons.isEmpty(), teacher, room);
        schedulingService.validateLessonConflicts(lessons);
        ClassEntity savedClass = attendanceClassRepository.save(classEntity);
        attendanceLessonRepository.saveAll(lessons);

        log.info("Tạo thành công lớp [{}] (#{}) với {} buổi học tự động sinh bởi [{}]",
                savedClass.getClassName(), savedClass.getId(), lessons.size(), currentUser.getUsername());

        return savedClass.getId();
    }

    // =========================================================================
    // 3. HELPER DATA CHO BỘ LỌC
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
    // 4. ỦY QUYỀN SANG ATTENDANCE TRACKING SERVICE (TRÁNH TRÙNG LẶP CODE)
    // =========================================================================

    @Override
    public LessonAttendanceSheetResponse getLessonAttendanceSheet(Long lessonId) {
        return attendanceTrackingService.getLessonAttendanceSheet(lessonId);
    }

    @Override
    @Transactional
    public void saveLessonAttendanceBatch(Long lessonId, BatchAttendanceSaveRequest request) {
        attendanceTrackingService.saveLessonAttendanceBatch(lessonId, request);
    }

    @Override
    public List<EnrolledClassOptionResponse> getEnrolledClassesForStudent(Long studentId) {
        return attendanceTrackingService.getEnrolledClassesForStudent(studentId);
    }

    @Override
    public StudentAttendanceTrackingResponse getStudentAttendanceTracking(Long studentId, Long classId) {
        return attendanceTrackingService.getStudentAttendanceTracking(studentId, classId);
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
