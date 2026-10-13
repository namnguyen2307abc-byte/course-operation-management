package com.talent.management.features.attendance_makeup.service.impl;

import com.talent.management.features.attendance_makeup.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_makeup.dto.request.CreateClassWithLessonsRequest;
import com.talent.management.features.attendance_makeup.dto.request.StudentAttendanceMarkItem;
import com.talent.management.features.attendance_makeup.dto.response.*;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.repository.*;
import com.talent.management.features.attendance_makeup.service.ScheduleWeeklyService;
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

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // =========================================================================
    // 1. LẤY THỜI KHÓA BIỂU TUẦN
    // =========================================================================

    @Override
    public WeeklyScheduleResponse getWeeklySchedule(LocalDate date, Long studentId, Long teacherId, Long branchId) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        LocalDate monday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = targetDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        int weekNumber = targetDate.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = targetDate.get(WeekFields.ISO.weekBasedYear());

        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();

        List<Lesson> lessons;

        switch (userRole) {
            case STUDENT -> {
                // Tìm học viên theo tài khoản
                Optional<Student> studentOpt = studentRepository.findByParentId(currentUser.getId()).stream()
                        .findFirst();
                Long effectiveStudentId = studentOpt.map(Student::getId).orElse(studentId);
                if (effectiveStudentId != null) {
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

        // BATCH FETCH toàn bộ attendance của các buổi học trong tuần để tránh N+1
        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Map<Long, List<Attendance>> attendanceMap = lessonIds.isEmpty()
                ? Collections.emptyMap()
                : attendanceRecordRepository.findByLessonIdIn(lessonIds).stream()
                        .collect(Collectors.groupingBy(att -> att.getLesson().getId()));

        LocalDate today = LocalDate.now();

        // Chuẩn bị danh sách Slots chuẩn theo mô hình LMS
        List<TimeSlotDto> standardSlots = getStandardSlots();

        List<LessonSlotItemResponse> lessonItems = lessons.stream().map(l -> {
            int slotNumber = mapTimeToSlotNumber(l.getStartTime());
            boolean isToday = l.getLessonDate().isEqual(today);
            boolean isPast = l.getLessonDate().isBefore(today);

            boolean canEdit;
            if (userRole == Role.ADMIN || userRole == Role.STAFF || userRole == Role.BRANCH_MANAGER) {
                canEdit = true;
            } else if (userRole == Role.TEACHER) {
                boolean isAssignedTeacher = l.getTeacher() != null
                        && l.getTeacher().getId().equals(currentUser.getId());
                canEdit = isToday && isAssignedTeacher;
            } else {
                canEdit = false;
            }

            List<Attendance> attendancesOfLesson = attendanceMap.getOrDefault(l.getId(), Collections.emptyList());
            int totalStudents = (l.getClassEntity() != null && l.getClassEntity().getCurrentStudents() != null)
                    ? l.getClassEntity().getCurrentStudents()
                    : attendancesOfLesson.size();
            long presentCount = attendancesOfLesson.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT)
                    .count();
            long absentCount = attendancesOfLesson.stream().filter(a -> a.getStatus() == AttendanceStatus.ABSENT)
                    .count();

            // Xác định trạng thái điểm danh cho người xem
            String attendanceStatus = "NOT_MARKED";
            if (userRole == Role.STUDENT || userRole == Role.PARENT) {
                // Tìm trạng thái của học viên này
                for (Attendance att : attendancesOfLesson) {
                    if (att.getStudent() != null) {
                        attendanceStatus = att.getStatus().name();
                        break;
                    }
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
                    .teacherId(l.getTeacher() != null ? l.getTeacher().getId() : null)
                    .teacherName(l.getTeacher() != null ? l.getTeacher().getFullName() : "Chưa phân công")
                    .teacherUsername(l.getTeacher() != null ? l.getTeacher().getUsername() : "")
                    .lessonDate(l.getLessonDate())
                    .dayOfWeek(l.getLessonDate().getDayOfWeek().name())
                    .dayOfWeekValue(l.getLessonDate().getDayOfWeek().getValue())
                    .slotNumber(slotNumber)
                    .timeRange(timeRangeStr)
                    .startTime(l.getStartTime())
                    .endTime(l.getEndTime())
                    .sessionNumber(l.getSessionNumber())
                    .status(l.getStatus() != null ? l.getStatus().name() : "SCHEDULED")
                    .isToday(isToday)
                    .isPast(isPast)
                    .canEdit(canEdit)
                    .attendanceStatus(attendanceStatus)
                    .attendanceCompleted(!attendancesOfLesson.isEmpty())
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

    // =========================================================================
    // 2. LẤY BẢNG ĐIỂM DANH HỌC VIÊN CỦA BUỔI HỌC
    // =========================================================================

    @Override
    public LessonAttendanceSheetResponse getLessonAttendanceSheet(Long lessonId) {
        Lesson lesson = attendanceLessonRepository.findById(lessonId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + lessonId));

        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();
        LocalDate today = LocalDate.now();

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
                canEdit = true;
            }
        } else {
            canEdit = false;
            lockReason = "Tài khoản của bạn chỉ có quyền xem thông tin điểm danh.";
        }

        // Lấy danh sách học viên chính thức của lớp
        Long classId = lesson.getClassEntity() != null ? lesson.getClassEntity().getId() : null;
        List<Enrollment> enrollments = (classId != null)
                ? attendanceEnrollmentRepository.findActiveEnrollmentsByClassId(classId)
                : Collections.emptyList();

        // Lấy danh sách học viên học bù được xếp vào buổi này
        List<MakeupRegistration> makeups = makeupRegistrationRepository
                .findByTargetLessonIdAndStatus(lesson.getId(), MakeupStatus.SCHEDULED);

        // Lấy tất cả bản ghi điểm danh hiện có của buổi học
        Map<Long, Attendance> attendanceMap = attendanceRecordRepository.findByLessonId(lesson.getId()).stream()
                .filter(a -> a.getStudent() != null)
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity(), (a1, a2) -> a1));

        // Lấy danh sách đơn xin nghỉ phép đã duyệt cho buổi này
        Map<Long, AbsenceRequest> approvedAbsenceMap = absenceRequestRepository.findByLessonId(lesson.getId()).stream()
                .filter(ar -> (ar.getStatus() == AbsenceStatus.APPROVED || ar.getStatus() == AbsenceStatus.EXCUSED)
                        && ar.getStudent() != null)
                .collect(Collectors.toMap(ar -> ar.getStudent().getId(), Function.identity(), (a1, a2) -> a1));

        boolean isAlreadySaved = !attendanceMap.isEmpty();

        // Tổng hợp danh sách học sinh: Lớp chính + Học viên học bù
        Map<Long, Student> uniqueStudents = new LinkedHashMap<>();
        for (Enrollment e : enrollments) {
            if (e.getStudent() != null) {
                uniqueStudents.put(e.getStudent().getId(), e.getStudent());
            }
        }
        for (MakeupRegistration mr : makeups) {
            if (mr.getStudent() != null) {
                uniqueStudents.putIfAbsent(mr.getStudent().getId(), mr.getStudent());
            }
        }

        List<StudentAttendanceItemDto> studentItems = new ArrayList<>();
        int order = 1;
        int presentCount = 0;
        int absentCount = 0;

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

            studentItems.add(StudentAttendanceItemDto.builder()
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
        LocalDate today = LocalDate.now();

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

            // Nếu học sinh PRESENT và có ca bù SCHEDULED -> tự động COMPLETED
            if (status == AttendanceStatus.PRESENT) {
                makeupRegistrationRepository
                        .findFirstByTargetLessonIdAndStudentIdAndStatus(lesson.getId(), student.getId(),
                                MakeupStatus.SCHEDULED)
                        .ifPresent(mr -> {
                            mr.setStatus(MakeupStatus.COMPLETED);
                            mr.setNote("Tự động hoàn thành sau khi giáo viên điểm danh Có mặt");
                            makeupRegistrationRepository.save(mr);
                            log.info("Ca học bù #{} của học viên [{}] tự động hoàn thành", mr.getId(),
                                    student.getFullName());
                        });
            }
        }

        // Cập nhật trạng thái buổi học sang COMPLETED nếu đang là SCHEDULED
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

        if (attendanceClassRepository.existsByClassCode(request.getClassCode())) {
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
            room = roomRepository.findById(request.getRoomId()).orElse(null);
        }

        User teacher = null;
        if (request.getTeacherId() != null) {
            teacher = userRepository.findById(request.getTeacherId()).orElse(null);
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

        // Chạy tối đa 365 ngày để tìm đủ số buổi học
        int safetyLoop = 0;
        while (sessionCount < totalSessions && safetyLoop < 365) {
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
    // HÀM TIỆN ÍCH NỘI BỘ
    // =========================================================================

    private int mapTimeToSlotNumber(LocalTime time) {
        if (time == null)
            return 1;
        if (time.isBefore(LocalTime.of(9, 30)))
            return 1;
        if (time.isBefore(LocalTime.of(12, 30)))
            return 2;
        if (time.isBefore(LocalTime.of(15, 30)))
            return 3;
        return 4;
    }

    private List<TimeSlotDto> getStandardSlots() {
        return List.of(
                TimeSlotDto.builder().slotNumber(1).slotName("Slot 1").timeRange("07:00 - 09:00")
                        .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(9, 0)).build(),
                TimeSlotDto.builder().slotNumber(2).slotName("Slot 2").timeRange("09:30 - 11:30")
                        .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(11, 30)).build(),
                TimeSlotDto.builder().slotNumber(3).slotName("Slot 3").timeRange("13:00 - 15:00")
                        .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(15, 0)).build(),
                TimeSlotDto.builder().slotNumber(4).slotName("Slot 4").timeRange("15:30 - 17:30")
                        .startTime(LocalTime.of(15, 30)).endTime(LocalTime.of(17, 30)).build());
    }
}
