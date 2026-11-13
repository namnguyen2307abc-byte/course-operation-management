package com.talent.management.features.attendance_tracking.service.impl;

import com.talent.management.features.attendance_tracking.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_tracking.dto.request.StudentAttendanceMarkItem;
import com.talent.management.features.attendance_tracking.dto.response.*;
import com.talent.management.features.attendance_tracking.service.AttendanceTrackingService;
import com.talent.management.features.weekly_schedule.dto.response.TimeSlotDto;
import com.talent.management.features.weekly_schedule.repository.*;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.talent.management.features.notification.service.NotificationService notificationService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private User resolveEffectiveTeacher(Lesson lesson) {
        if (lesson == null) return null;
        if (lesson.getTeacher() != null) return lesson.getTeacher();
        return lesson.getClassEntity() != null ? lesson.getClassEntity().getTeacher() : null;
    }

    @Override
    public List<Map<String, Object>> getTrackableStudents() {
        User currentUser = currentUserService.getCurrentUser();
        Role role = currentUser.getRole();
        List<Student> students;

        if (role == Role.PARENT) {
            students = studentRepository.findByParentId(currentUser.getId());
        } else if (role == Role.STUDENT) {
            students = studentRepository.findByAccountId(currentUser.getId())
                    .map(List::of)
                    .orElseGet(() -> studentRepository.findByParentId(currentUser.getId()));
        } else if (role == Role.TEACHER) {
            Set<Long> classIds = new LinkedHashSet<>();
            attendanceClassRepository.findAll().stream()
                    .filter(c -> c.getTeacher() != null && currentUser.getId().equals(c.getTeacher().getId()))
                    .forEach(c -> classIds.add(c.getId()));
            Map<Long, Student> map = new LinkedHashMap<>();
            for (Long cid : classIds) {
                for (Enrollment e : attendanceEnrollmentRepository.findActiveEnrollmentsByClassId(cid)) {
                    if (e.getStudent() != null) {
                        map.putIfAbsent(e.getStudent().getId(), e.getStudent());
                    }
                }
            }
            students = new ArrayList<>(map.values());
        } else {
            students = studentRepository.findAll();
        }

        return students.stream().map(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("fullName", s.getFullName());
            item.put("name", s.getFullName());
            item.put("studentCode", String.format("HV%04d", s.getId()));
            return item;
        }).toList();
    }

    @Override
    public LessonAttendanceSheetResponse getLessonAttendanceSheet(Long lessonId) {
        if (lessonId == null || lessonId <= 0) {
            throw new AttendanceMakeupException("Mã buổi học không hợp lệ.");
        }
        Lesson lesson = attendanceLessonRepository.findById(lessonId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + lessonId));

        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();
        if (userRole != Role.ADMIN && userRole != Role.STAFF && userRole != Role.BRANCH_MANAGER
                && userRole != Role.TEACHER) {
            throw new AttendanceMakeupException("Tài khoản không có quyền xem bảng điểm danh của lớp.");
        }
        User effectiveTeacher = resolveEffectiveTeacher(lesson);
        if (userRole == Role.TEACHER && (effectiveTeacher == null
                || !effectiveTeacher.getId().equals(currentUser.getId()))) {
            throw new AttendanceMakeupException("Bạn không phải giáo viên phụ trách buổi học này!");
        }
        LocalDate today = LocalDate.now(CENTER_ZONE);

        boolean canEdit;
        String lockReason = null;

        if (lesson.getLessonDate().isAfter(today)) {
            canEdit = false;
            lockReason = "Buổi học ngày " + lesson.getLessonDate()
                    + " chưa diễn ra. Không được điểm danh hoặc chỉnh sửa điểm danh cho ngày tương lai.";
        } else if (userRole == Role.ADMIN || userRole == Role.STAFF || userRole == Role.BRANCH_MANAGER) {
            canEdit = true;
        } else if (userRole == Role.TEACHER) {
            boolean isAssignedTeacher = effectiveTeacher != null
                    && effectiveTeacher.getId().equals(currentUser.getId());
            if (!isAssignedTeacher) {
                canEdit = false;
                lockReason = "Bạn không phải giáo viên phụ trách buổi học này!";
            } else if (lesson.getClassEntity() != null && lesson.getClassEntity().getStatus() == ClassStatus.FINISHED) {
                canEdit = false;
                lockReason = "Lớp học đã kết thúc (FINISHED), giáo viên không thể chỉnh sửa điểm danh.";
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

        // Lấy danh sách học viên học bù được xếp vào buổi này (bao gồm SCHEDULED, COMPLETED, MISSED)
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
                statusStr = "ABSENT";
                noteStr = "Nghỉ học: " + (leaveReq.getReason() != null ? leaveReq.getReason() : "");
            } else if (autoAbsentPast) {
                statusStr = "ABSENT";
                noteStr = "Vắng mặt (Quá khứ chưa điểm danh)";
            } else {
                statusStr = null;
            }

            if ("PRESENT".equals(statusStr)) {
                presentCount++;
            } else if ("ABSENT".equals(statusStr)) {
                absentCount++;
            }

            String studentCode = String.format("HV%04d", s.getId());
            boolean hasApprovedLeave = (leaveReq != null);
            String absenceReasonText = (hasApprovedLeave && leaveReq.getReason() != null) ? leaveReq.getReason() : null;

            MakeupRegistration mkReg = makeupByStudent.get(s.getId());
            String makeupOrigin = null;
            if (mkReg != null && mkReg.getOriginalLesson() != null) {
                Lesson ol = mkReg.getOriginalLesson();
                int retry = Math.max(1, AttendanceMakeupMapper.extractRetryCount(mkReg));
                makeupOrigin = "Học bù (Lần " + retry + "/" + AttendanceMakeupMapper.MAX_MAKEUP_RETRIES + ") buổi " + ol.getSessionNumber()
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
                .teacherId(effectiveTeacher != null ? effectiveTeacher.getId() : null)
                .teacherName(effectiveTeacher != null ? effectiveTeacher.getFullName() : "Chưa phân công")
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
        if (lessonId == null || lessonId <= 0) {
            throw new AttendanceMakeupException("Mã buổi học không hợp lệ.");
        }
        Lesson lesson = attendanceLessonRepository.findById(lessonId)
                .orElseThrow(() -> new AttendanceMakeupException("Không tìm thấy buổi học với ID: " + lessonId));

        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();
        LocalDate today = LocalDate.now(CENTER_ZONE);

        // 1. Phân quyền: Học sinh/phụ huynh không được điểm danh
        if (userRole == Role.PARENT || userRole == Role.STUDENT) {
            throw new AttendanceMakeupException("Tài khoản Phụ huynh/Học viên không có quyền điểm danh!");
        }
        if (lesson.getLessonDate().isAfter(today)) {
            throw new AttendanceMakeupException("Buổi học ngày " + lesson.getLessonDate()
                    + " chưa diễn ra. Không được điểm danh hoặc chỉnh sửa điểm danh cho ngày tương lai.");
        }

        // 2. Ràng buộc thời gian & giáo viên (Crucial Business Rule)
        User effectiveTeacher = resolveEffectiveTeacher(lesson);
        if (userRole == Role.TEACHER) {
            boolean isAssignedTeacher = effectiveTeacher != null
                    && effectiveTeacher.getId().equals(currentUser.getId());
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

        LessonAttendanceSheetResponse attendanceSheet = getLessonAttendanceSheet(lessonId);
        if (!attendanceSheet.isCanEdit()) {
            throw new AttendanceMakeupException(attendanceSheet.getLockReason());
        }
        Set<Long> roster = attendanceSheet.getStudents().stream()
                .map(StudentAttendanceItemDto::getStudentId).collect(Collectors.toSet());
        if (roster.isEmpty()) {
            throw new AttendanceMakeupException("Buổi học hiện chưa có học viên nào trong danh sách để điểm danh.");
        }
        Set<Long> submitted = new HashSet<>();
        if (request == null || request.getAttendances() == null || request.getAttendances().isEmpty()) {
            throw new AttendanceMakeupException("Danh sách điểm danh không được để trống.");
        }
        for (StudentAttendanceMarkItem item : request.getAttendances()) {
            if (item == null || item.getStudentId() == null || !submitted.add(item.getStudentId())) {
                throw new AttendanceMakeupException("Danh sách điểm danh có học viên trùng hoặc không hợp lệ.");
            }
            String normalizedStatus = item.getStatus() == null ? "" : item.getStatus().trim().toUpperCase();
            if (!Set.of("PRESENT", "ABSENT").contains(normalizedStatus)) {
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

        // 3. Xử lý lưu từng bản ghi điểm danh
        for (StudentAttendanceMarkItem item : request.getAttendances()) {
            Student student = studentRepository.findById(item.getStudentId())
                    .orElseThrow(() -> new AttendanceMakeupException(
                            "Không tìm thấy học viên với ID: " + item.getStudentId()));

            String reqStatus = item.getStatus().trim().toUpperCase();
            AttendanceStatus status = "ABSENT".equals(reqStatus) ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;

            Optional<Attendance> existingOpt = attendanceRecordRepository
                    .findByLessonIdAndStudentId(lesson.getId(), student.getId());
            boolean isNewRecord = existingOpt.isEmpty();
            Attendance attendance = existingOpt.orElseGet(() -> Attendance.builder()
                    .lesson(lesson)
                    .student(student)
                    .build());

            AttendanceStatus previousStatus = isNewRecord ? null : attendance.getStatus();
            boolean statusChanged = isNewRecord || (previousStatus != status);

            attendance.setStatus(status);
            attendance.setMarkedAt(LocalDateTime.now(CENTER_ZONE));
            attendance.setNote(item.getNote() != null ? item.getNote().trim() : null);
            attendanceRecordRepository.save(attendance);

            // Chỉ gửi thông báo và kích hoạt cập nhật học bù / cảnh báo vắng khi trạng thái thực sự thay đổi
            if (!statusChanged) {
                continue;
            }

            if (notificationService != null && student.getParent() != null) {
                String statusVn = status == AttendanceStatus.PRESENT ? "Có mặt" : "Vắng mặt";
                String notifType = status == AttendanceStatus.PRESENT ? "ATTENDANCE_PRESENT" : "ATTENDANCE_ABSENT";
                notificationService.sendNotification(
                        student.getParent(), currentUser,
                        "Cập nhật điểm danh: " + student.getFullName() + " (" + statusVn + ")",
                        "Bé " + student.getFullName() + " vừa được điểm danh [" + statusVn + "] tại buổi học ngày " + lesson.getLessonDate() + ".",
                        notifType, lesson.getId(), "/pages/attendance-tracking.html");
            }

            // Nếu học sinh PRESENT và có ca bù SCHEDULED / MISSED / CANCELLED -> tự động COMPLETED
            if (status == AttendanceStatus.PRESENT) {
                makeupRegistrationRepository
                        .findFirstByTargetLessonIdAndStudentIdAndStatusIn(lesson.getId(), student.getId(),
                                List.of(MakeupStatus.SCHEDULED, MakeupStatus.MISSED, MakeupStatus.CANCELLED))
                        .ifPresent(mr -> {
                            int retry = Math.max(1, AttendanceMakeupMapper.extractRetryCount(mr));
                            mr.setStatus(MakeupStatus.COMPLETED);
                            mr.setNote(AttendanceMakeupMapper.buildRetryNote(retry, false,
                                    "Tự động hoàn thành sau khi giáo viên điểm danh Có mặt ngày " + lesson.getLessonDate()));
                            makeupRegistrationRepository.save(mr);
                            log.info("Ca học bù #{} của học viên [{}] tự động hoàn thành (COMPLETED)", mr.getId(),
                                    student.getFullName());
                        });
            } else if (status == AttendanceStatus.ABSENT) {
                // Kiểm tra nếu đây là học viên học bù bị VẮNG MẶT (ABSENT) -> giới hạn tối đa 2 lần
                Optional<MakeupRegistration> makeupOpt = makeupRegistrationRepository
                        .findFirstByTargetLessonIdAndStudentIdAndStatusIn(lesson.getId(), student.getId(),
                                List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED));
                if (makeupOpt.isPresent()) {
                    MakeupRegistration mr = makeupOpt.get();
                    int retry = Math.max(1, AttendanceMakeupMapper.extractRetryCount(mr));
                    String reasonSuffix = (item.getNote() != null && !item.getNote().isBlank())
                            ? " - Lý do: " + item.getNote().trim()
                            : "";

                    if (retry >= AttendanceMakeupMapper.MAX_MAKEUP_RETRIES) {
                        mr.setStatus(MakeupStatus.CANCELLED);
                        String lockBody = "Hủy quyền học bù do học viên đã vắng mặt cả "
                                + AttendanceMakeupMapper.MAX_MAKEUP_RETRIES + "/" + AttendanceMakeupMapper.MAX_MAKEUP_RETRIES
                                + " lần học bù quy định (vắng ngày " + lesson.getLessonDate() + ")" + reasonSuffix;
                        mr.setNote(AttendanceMakeupMapper.buildRetryNote(retry, true, lockBody));
                        makeupRegistrationRepository.save(mr);
                        log.info("Ca học bù #{} của học viên [{}] TỰ ĐỘNG KHÓA/HỦY do bỏ lỡ quá {} lần",
                                mr.getId(), student.getFullName(), AttendanceMakeupMapper.MAX_MAKEUP_RETRIES);

                        if (notificationService != null) {
                            if (student.getParent() != null) {
                                notificationService.sendNotification(
                                        student.getParent(), currentUser,
                                        "Khóa quyền học bù (Vắng 2/2 lần): " + student.getFullName(),
                                        "Bé " + student.getFullName() + " đã vắng mặt tại buổi học bù lần " + retry + "/" + AttendanceMakeupMapper.MAX_MAKEUP_RETRIES
                                                + " ngày " + lesson.getLessonDate() + ". Theo quy định tối đa 2 lần học bù, quyền học bù của buổi nghỉ này đã bị hủy.",
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
                        String missedBody = "Bỏ lỡ / Vắng mặt tại buổi học bù ngày " + lesson.getLessonDate()
                                + " (Còn tối đa 1 lần xếp lại)" + reasonSuffix;
                        mr.setNote(AttendanceMakeupMapper.buildRetryNote(retry, false, missedBody));
                        makeupRegistrationRepository.save(mr);
                        log.info("Ca học bù #{} của học viên [{}] chuyển sang MISSED (Lần {}/{})",
                                mr.getId(), student.getFullName(), retry, AttendanceMakeupMapper.MAX_MAKEUP_RETRIES);

                        if (notificationService != null && student.getParent() != null) {
                            notificationService.sendNotification(
                                    student.getParent(), currentUser,
                                    "Cảnh báo bỏ lỡ học bù (Lần " + retry + "/" + AttendanceMakeupMapper.MAX_MAKEUP_RETRIES + "): " + student.getFullName(),
                                    "Bé " + student.getFullName() + " đã vắng mặt tại buổi học bù ngày "
                                            + lesson.getLessonDate() + ". Bé chỉ còn tối đa 1 lần xếp lịch học bù cuối cùng.",
                                    "MAKEUP_MISSED", mr.getId(), "/pages/absence-makeup.html");
                        }
                    }
                } else if (lesson.getClassEntity() != null) {
                    // Học viên chính thức của lớp bị ABSENT -> Kiểm tra ngưỡng cảnh báo chuyên cần (10% / 20%)
                    checkAndSendAttendanceThresholdNotification(student, lesson.getClassEntity(), currentUser);
                }
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
            long absentCount = attendanceRecordRepository.findByLessonIdIn(lessonIds).stream()
                    .filter(a -> a.getStudent() != null && a.getStudent().getId().equals(student.getId())
                            && a.getStatus() == AttendanceStatus.ABSENT)
                    .count();

            double absentRate = Math.round(((double) absentCount / totalSessions * 100.0) * 10.0) / 10.0;
            int maxAllowed = Math.max(1, (int) Math.floor(totalSessions * 0.20));
            int remaining = Math.max(0, maxAllowed - (int) absentCount);

            if (absentRate >= 20.0 || (totalSessions >= 5 && absentCount > maxAllowed)) {
                String title = "BÁO ĐỘNG ĐỎ (≥ 20%): " + student.getFullName() + " vượt định mức nghỉ";
                String msg = String.format("Học viên %s (Lớp %s) đã vắng %d/%d buổi (%.1f%%), chạm/vượt ngưỡng tối đa 20%% (%d buổi) — Nguy cơ cấm thi / không đạt khóa học!",
                        student.getFullName(), classEntity.getClassName(), absentCount, totalSessions, absentRate, maxAllowed);
                if (student.getParent() != null) {
                    notificationService.sendNotification(student.getParent(), sender, title, msg,
                            "ATTENDANCE_CRITICAL", classEntity.getId(), "/pages/attendance-tracking.html");
                }
                notificationService.sendToRoles(List.of(Role.STAFF, Role.ADMIN), sender, title, msg,
                        "ATTENDANCE_CRITICAL", classEntity.getId(), "/pages/attendance-tracking.html");
            } else if (absentRate > 10.0 && student.getParent() != null) {
                String title = "Cảnh báo chuyên cần (> 10%): " + student.getFullName();
                String msg = String.format("Bé %s (Lớp %s) đã vắng %d/%d buổi (%.1f%%). Bé chỉ còn %d/%d buổi phép trước khi chạm ngưỡng cấm thi 20%%.",
                        student.getFullName(), classEntity.getClassName(), absentCount, totalSessions, absentRate, remaining, maxAllowed);
                notificationService.sendNotification(student.getParent(), sender, title, msg,
                        "ATTENDANCE_WARNING", classEntity.getId(), "/pages/attendance-tracking.html");
            }
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo cảnh báo chuyên cần cho học viên {}: {}", student.getId(), e.getMessage());
        }
    }

    // =========================================================================
    // 4. TRA CỨU TIẾN ĐỘ ĐIỂM DANH & CẢNH BÁO TỶ LỆ CHUYÊN CẦN
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
                student.getId(),
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.COMPLETED)
        );

        if (currentUser.getRole() == Role.TEACHER) {
            enrollments = enrollments.stream()
                    .filter(e -> e.getClassEntity() != null
                            && e.getClassEntity().getTeacher() != null
                            && currentUser.getId().equals(e.getClassEntity().getTeacher().getId()))
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
                student.getId(),
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.COMPLETED)
        );
        if (enrollments.isEmpty()) {
            throw new AttendanceMakeupException("Học viên chưa đăng ký tham gia lớp học nào!");
        }

        Long targetClassId = classId;
        if (targetClassId == null) {
            targetClassId = enrollments.get(0).getClassEntity().getId();
        } else {
            final Long checkCid = targetClassId;
            boolean isEnrolledInClass = enrollments.stream()
                    .anyMatch(e -> e.getClassEntity() != null && checkCid.equals(e.getClassEntity().getId()));
            if (!isEnrolledInClass) {
                throw new AttendanceMakeupException("Học viên không có hồ sơ ghi danh trong lớp học được chọn!");
            }
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

        if (currentUser.getRole() == Role.TEACHER) {
            boolean teachesClass = (classEntity.getTeacher() != null
                    && currentUser.getId().equals(classEntity.getTeacher().getId()))
                    || classLessons.stream().anyMatch(l -> l.getTeacher() != null
                            && currentUser.getId().equals(l.getTeacher().getId()));
            if (!teachesClass) {
                throw new AttendanceMakeupException("Bạn chỉ có quyền tra cứu tiến độ chuyên cần của học viên thuộc lớp mình phụ trách!");
            }
        }

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

            boolean isLocked = AttendanceMakeupMapper.isLimitExceeded(mr);
            int retry = AttendanceMakeupMapper.extractRetryCount(mr);
            boolean isMakeup = (mr != null && (mr.getStatus() == MakeupStatus.COMPLETED
                    || mr.getStatus() == MakeupStatus.SCHEDULED
                    || mr.getStatus() == MakeupStatus.MISSED
                    || isLocked));
            String makeupStatusStr = (mr != null && mr.getStatus() != null)
                    ? (isLocked ? "MISSED" : mr.getStatus().name())
                    : null;
            String makeupInfo = null;
            if (mr != null) {
                if (mr.getStatus() == MakeupStatus.COMPLETED) {
                    makeupInfo = "Đã học bù (Lần " + retry + "/2)";
                    makeupCompletedCount++;
                } else if (mr.getStatus() == MakeupStatus.SCHEDULED) {
                    makeupInfo = "Đã xếp lịch bù (Lần " + retry + "/2)";
                } else if (isLocked) {
                    makeupInfo = "Bỏ lỡ 2/2 lần (Khóa quyền bù)";
                    makeupMissedCount++;
                } else if (mr.getStatus() == MakeupStatus.MISSED) {
                    makeupInfo = "Bỏ lỡ bù (Lần " + retry + "/2 - Còn 1 lần)";
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
                        .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(9, 0)).build(),
                TimeSlotDto.builder().slotNumber(2).slotName("Slot 2").timeRange("09:30 - 11:30")
                        .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(11, 30)).build(),
                TimeSlotDto.builder().slotNumber(3).slotName("Slot 3").timeRange("13:00 - 15:00")
                        .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(15, 0)).build(),
                TimeSlotDto.builder().slotNumber(4).slotName("Slot 4").timeRange("15:30 - 17:30")
                        .startTime(LocalTime.of(15, 30)).endTime(LocalTime.of(17, 30)).build());
    }
}
