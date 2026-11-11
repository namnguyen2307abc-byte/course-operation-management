package com.talent.management.features.class_management.service.impl;

import com.talent.management.features.class_management.dto.response.TeacherClassDetailResponse;
import com.talent.management.features.class_management.dto.response.TeacherClassLessonItem;
import com.talent.management.features.class_management.dto.response.TeacherClassStudentItem;
import com.talent.management.features.class_management.dto.response.TeacherClassSummaryResponse;
import com.talent.management.features.class_management.repository.ManagedClassLessonRepository;
import com.talent.management.features.class_management.repository.ManagedClassRepository;
import com.talent.management.features.class_management.service.TeacherClassService;
import com.talent.management.features.weekly_schedule.repository.AttendanceEnrollmentRepository;
import com.talent.management.features.weekly_schedule.repository.AttendanceRecordRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.AttendanceStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherClassServiceImpl implements TeacherClassService {

    private final ManagedClassRepository managedClassRepository;
    private final ManagedClassLessonRepository lessonRepository;
    private final AttendanceEnrollmentRepository enrollmentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CurrentUserService currentUserService;

    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public List<TeacherClassSummaryResponse> getTeacherClasses(Long requestedTeacherId) {
        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();

        Long teacherId;
        if (userRole == Role.TEACHER) {
            teacherId = currentUser.getId();
        } else if (requestedTeacherId != null) {
            teacherId = requestedTeacherId;
        } else {
            // Staff / Admin không truyền teacherId -> trả về tất cả lớp đang hoạt động
            teacherId = null;
        }

        List<ClassEntity> classes = (teacherId != null)
                ? managedClassRepository.findClassesForTeacher(teacherId)
                : managedClassRepository.findAll();

        LocalDate today = LocalDate.now(CENTER_ZONE);

        return classes.stream().map(c -> {
            List<Lesson> lessons = lessonRepository.findByClassEntityIdOrderByLessonDateAscStartTimeAsc(c.getId());
            int total = lessons.size();

            // Lấy danh sách ID các buổi học của lớp
            List<Long> lessonIds = lessons.stream().map(Lesson::getId).filter(Objects::nonNull).toList();

            // CHỈ CẬP NHẬT TIẾN ĐỘ KHI ĐÃ ĐIỂM DANH XONG BUỔI ĐÓ
            // Buổi học chỉ được tính là hoàn thành khi có bản ghi điểm danh lưu trong CSDL
            Set<Long> attendedLessonIds = lessonIds.isEmpty() ? Collections.emptySet()
                    : attendanceRecordRepository.findByLessonIdIn(lessonIds).stream()
                            .filter(a -> a.getLesson() != null)
                            .map(a -> a.getLesson().getId())
                            .collect(Collectors.toSet());

            long completed = lessons.stream()
                    .filter(l -> attendedLessonIds.contains(l.getId()))
                    .count();

            int progressPercent = (total > 0) ? (int) Math.round((completed * 100.0) / total) : 0;

            // Tìm buổi học sắp diễn ra gần nhất
            Lesson nextLesson = lessons.stream()
                    .filter(l -> l.getLessonDate() != null && !l.getLessonDate().isBefore(today))
                    .findFirst()
                    .orElse(null);

            String courseName = (c.getCourse() != null) ? c.getCourse().getName() : "";
            String courseSubject = (c.getCourse() != null && c.getCourse().getProgram() != null)
                    ? c.getCourse().getProgram().getName()
                    : ((c.getCourse() != null && c.getCourse().getLevel() != null) ? c.getCourse().getLevel() : "Âm nhạc & Năng khiếu");
            String branchName = (c.getBranch() != null) ? c.getBranch().getName() : "";
            String roomName = (c.getRoom() != null) ? c.getRoom().getRoomName() : "";
            String teacherName = (c.getTeacher() != null) ? c.getTeacher().getFullName() : "Chưa phân công";

            return TeacherClassSummaryResponse.builder()
                    .classId(c.getId())
                    .classCode(c.getClassCode())
                    .className(c.getClassName())
                    .courseId(c.getCourse() != null ? c.getCourse().getId() : null)
                    .courseName(courseName)
                    .courseSubject(courseSubject)
                    .branchId(c.getBranch() != null ? c.getBranch().getId() : null)
                    .branchName(branchName)
                    .roomId(c.getRoom() != null ? c.getRoom().getId() : null)
                    .roomName(roomName)
                    .scheduleDescription(c.getScheduleDescription())
                    .startDate(c.getStartDate())
                    .endDate(c.getEndDate())
                    .status(c.getStatus() != null ? c.getStatus().name() : "OPEN")
                    .currentStudents(c.getCurrentStudents() != null ? c.getCurrentStudents() : 0)
                    .maxStudents(c.getMaxStudents() != null ? c.getMaxStudents() : 0)
                    .totalLessons(total)
                    .completedLessons((int) completed)
                    .progressPercent(progressPercent)
                    .nextLessonDate(nextLesson != null ? nextLesson.getLessonDate() : null)
                    .nextLessonStartTime(nextLesson != null ? nextLesson.getStartTime() : null)
                    .nextLessonEndTime(nextLesson != null ? nextLesson.getEndTime() : null)
                    .teacherName(teacherName)
                    .build();
        }).toList();
    }

    @Override
    public TeacherClassDetailResponse getTeacherClassDetail(Long classId) {
        ClassEntity c = managedClassRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học với ID: " + classId));

        User currentUser = currentUserService.getCurrentUser();
        Role userRole = currentUser.getRole();

        // Kiểm tra quyền: nếu là giáo viên, phải là giáo viên phụ trách lớp
        if (userRole == Role.TEACHER) {
            boolean isTeacher = (c.getTeacher() != null && c.getTeacher().getId().equals(currentUser.getId()));
            if (!isTeacher) {
                // Kiểm tra xem có dạy buổi nào trong lớp không
                boolean teachesLesson = lessonRepository.findByClassEntityId(classId).stream()
                        .anyMatch(l -> l.getTeacher() != null && l.getTeacher().getId().equals(currentUser.getId()));
                if (!teachesLesson) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem thông tin lớp này!");
                }
            }
        }

        LocalDate today = LocalDate.now(CENTER_ZONE);

        // 1. Danh sách buổi học
        List<Lesson> lessons = lessonRepository.findByClassEntityIdOrderByLessonDateAscStartTimeAsc(classId);
        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();

        // 2. Lấy dữ liệu điểm danh
        Map<Long, List<Attendance>> attendanceByLesson = lessonIds.isEmpty()
                ? Collections.emptyMap()
                : attendanceRecordRepository.findByLessonIdIn(lessonIds).stream()
                        .collect(Collectors.groupingBy(att -> att.getLesson().getId()));

        int total = lessons.size();
        // CHỈ CẬP NHẬT TIẾN ĐỘ KHI ĐÃ ĐIỂM DANH XONG BUỔI ĐÓ
        long completed = lessons.stream()
                .filter(l -> {
                    List<Attendance> attList = attendanceByLesson.getOrDefault(l.getId(), Collections.emptyList());
                    return !attList.isEmpty();
                })
                .count();

        // 3. Danh sách học viên chính thức
        List<Enrollment> enrollments = enrollmentRepository.findActiveEnrollmentsByClassId(classId);

        // Đếm số buổi có mặt / vắng của từng học viên
        Map<Long, Integer> presentCountByStudent = new HashMap<>();
        Map<Long, Integer> absentCountByStudent = new HashMap<>();

        for (List<Attendance> attList : attendanceByLesson.values()) {
            for (Attendance a : attList) {
                if (a.getStudent() == null) continue;
                Long sid = a.getStudent().getId();
                if (a.getStatus() == AttendanceStatus.PRESENT) {
                    presentCountByStudent.merge(sid, 1, Integer::sum);
                } else if (a.getStatus() == AttendanceStatus.ABSENT) {
                    absentCountByStudent.merge(sid, 1, Integer::sum);
                }
            }
        }

        List<TeacherClassStudentItem> studentItems = enrollments.stream().map(e -> {
            Student s = e.getStudent();
            User parent = (s != null) ? s.getParent() : null;

            return TeacherClassStudentItem.builder()
                    .studentId(s != null ? s.getId() : null)
                    .fullName(s != null ? s.getFullName() : "Học viên")
                    .dateOfBirth(s != null ? s.getDateOfBirth() : null)
                    .gender(s != null ? s.getGender() : "")
                    .schoolName(s != null ? s.getSchoolName() : "")
                    .parentName(parent != null ? parent.getFullName() : "—")
                    .parentPhone(parent != null ? parent.getPhone() : "—")
                    .parentEmail(parent != null ? parent.getEmail() : "")
                    .attendedLessons(s != null ? presentCountByStudent.getOrDefault(s.getId(), 0) : 0)
                    .absentLessons(s != null ? absentCountByStudent.getOrDefault(s.getId(), 0) : 0)
                    .build();
        }).toList();

        // 4. Danh sách buổi học
        List<TeacherClassLessonItem> lessonItems = lessons.stream().map(l -> {
            List<Attendance> atts = attendanceByLesson.getOrDefault(l.getId(), Collections.emptyList());
            int pCount = (int) atts.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT).count();
            int aCount = (int) atts.stream().filter(a -> a.getStatus() == AttendanceStatus.ABSENT).count();

            boolean isPast = l.getLessonDate() != null && l.getLessonDate().isBefore(today);
            boolean isToday = l.getLessonDate() != null && l.getLessonDate().isEqual(today);
            LocalTime nowTime = LocalTime.now(CENTER_ZONE);
            boolean isSlotStarted = (l.getStartTime() == null || !nowTime.isBefore(l.getStartTime()));
            boolean canTakeAttendance = isToday && isSlotStarted;

            String timeRange = (l.getStartTime() != null && l.getEndTime() != null)
                    ? l.getStartTime().format(TIME_FMT) + " - " + l.getEndTime().format(TIME_FMT)
                    : "";

            String roomName = (l.getRoom() != null) ? l.getRoom().getRoomName()
                    : (c.getRoom() != null ? c.getRoom().getRoomName() : "—");

            return TeacherClassLessonItem.builder()
                    .lessonId(l.getId())
                    .sessionNumber(l.getSessionNumber())
                    .lessonDate(l.getLessonDate())
                    .startTime(l.getStartTime())
                    .endTime(l.getEndTime())
                    .timeRange(timeRange)
                    .status(l.getStatus() != null ? l.getStatus().name() : "SCHEDULED")
                    .roomName(roomName)
                    .isPast(isPast)
                    .isToday(isToday)
                    .canTakeAttendance(canTakeAttendance)
                    .attendanceCompleted(!atts.isEmpty() && (pCount + aCount >= studentItems.size()))
                    .presentCount(pCount)
                    .absentCount(aCount)
                    .totalStudents(studentItems.size())
                    .build();
        }).toList();

        // Tìm buổi học tiếp theo
        Lesson nextLesson = lessons.stream()
                .filter(l -> l.getLessonDate() != null && !l.getLessonDate().isBefore(today))
                .findFirst()
                .orElse(null);

        String courseSubject = (c.getCourse() != null && c.getCourse().getProgram() != null)
                ? c.getCourse().getProgram().getName()
                : ((c.getCourse() != null && c.getCourse().getLevel() != null) ? c.getCourse().getLevel() : "Âm nhạc & Năng khiếu");

        TeacherClassSummaryResponse overview = TeacherClassSummaryResponse.builder()
                .classId(c.getId())
                .classCode(c.getClassCode())
                .className(c.getClassName())
                .courseId(c.getCourse() != null ? c.getCourse().getId() : null)
                .courseName(c.getCourse() != null ? c.getCourse().getName() : "")
                .courseSubject(courseSubject)
                .branchId(c.getBranch() != null ? c.getBranch().getId() : null)
                .branchName(c.getBranch() != null ? c.getBranch().getName() : "")
                .roomId(c.getRoom() != null ? c.getRoom().getId() : null)
                .roomName(c.getRoom() != null ? c.getRoom().getRoomName() : "")
                .scheduleDescription(c.getScheduleDescription())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .status(c.getStatus() != null ? c.getStatus().name() : "OPEN")
                .currentStudents(studentItems.size())
                .maxStudents(c.getMaxStudents() != null ? c.getMaxStudents() : 0)
                .totalLessons(total)
                .completedLessons((int) completed)
                .progressPercent((total > 0) ? (int) Math.round((completed * 100.0) / total) : 0)
                .nextLessonDate(nextLesson != null ? nextLesson.getLessonDate() : null)
                .nextLessonStartTime(nextLesson != null ? nextLesson.getStartTime() : null)
                .nextLessonEndTime(nextLesson != null ? nextLesson.getEndTime() : null)
                .teacherName(c.getTeacher() != null ? c.getTeacher().getFullName() : "Chưa phân công")
                .build();

        return TeacherClassDetailResponse.builder()
                .classOverview(overview)
                .students(studentItems)
                .lessons(lessonItems)
                .build();
    }
}
