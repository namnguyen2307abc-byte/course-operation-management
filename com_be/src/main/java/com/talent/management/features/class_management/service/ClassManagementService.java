package com.talent.management.features.class_management.service;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.class_management.dto.ClassUpsertRequest;
import com.talent.management.features.class_management.dto.ManagedClassResponse;
import com.talent.management.features.class_management.repository.ManagedClassEnrollmentRepository;
import com.talent.management.features.class_management.repository.ManagedClassLessonRepository;
import com.talent.management.features.class_management.repository.ManagedClassRepository;
import com.talent.management.features.course_enrollment.repository.CourseRepository;
import com.talent.management.shared.entity.Branch;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.Room;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.ClassType;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.enums.LessonStatus;
import com.talent.management.shared.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassManagementService {
    private final ManagedClassRepository classRepository;
    private final ManagedClassEnrollmentRepository enrollmentRepository;
    private final ManagedClassLessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    @Transactional(readOnly = true)
    public List<ManagedClassResponse> search(String keyword, Long courseId, Long branchId, String status) {
        String term = keyword == null ? "" : keyword.trim();
        return classRepository.search(term, courseId, branchId, parseStatus(status)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagedClassResponse get(Long id) {
        return toResponse(findClass(id));
    }

    @Transactional
    public ManagedClassResponse create(ClassUpsertRequest request) {
        String code = normalizeCode(request.classCode());
        validateDates(request);
        validateSchedule(request);
        ClassStatus status = requiredStatus(request.status());
        if (classRepository.existsByClassCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại");
        }

        Branch branch = findBranch(request.branchId());
        User teacher = request.teacherId() == null ? null : findTeacher(request.teacherId());
        Room room = request.roomId() == null ? null : findRoom(request.roomId(), branch.getId());

        String scheduleDesc = request.scheduleDescription();
        if ((scheduleDesc == null || scheduleDesc.isBlank()) && request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()) {
            scheduleDesc = buildScheduleDescription(request.daysOfWeek(), request.startTime(), request.endTime());
        }

        LocalDate endDate = request.endDate();
        if (endDate == null) {
            endDate = request.startDate().plusMonths(3);
        }

        ClassEntity entity = ClassEntity.builder()
                .course(findCourse(request.courseId()))
                .branch(branch)
                .teacher(teacher)
                .room(room)
                .scheduleDescription(scheduleDesc)
                .classCode(code)
                .className(request.className().trim())
                .classType(request.maxStudents() <= 1 ? ClassType.ONE_ON_ONE : ClassType.GROUP)
                .maxStudents(request.maxStudents())
                .currentStudents(0)
                .status(status)
                .startDate(request.startDate())
                .endDate(endDate)
                .build();

        try {
            ClassEntity saved = classRepository.saveAndFlush(entity);

            // Tự động sinh danh sách buổi học (lessons) vào thời khóa biểu nếu chọn lịch
            if (request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()
                    && request.startTime() != null && request.endTime() != null) {
                List<Lesson> lessons = generateLessons(saved, request.daysOfWeek(), request.startTime(), request.endTime(), request.totalSessions());
                if (!lessons.isEmpty()) {
                    saved.setEndDate(lessons.get(lessons.size() - 1).getLessonDate());
                    lessonRepository.saveAll(lessons);
                    saved = classRepository.saveAndFlush(saved);
                    log.info("Đã sinh tự động {} buổi học cho lớp {}", lessons.size(), saved.getClassCode());
                }
            }

            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại hoặc dữ liệu liên kết không hợp lệ", ex);
        }
    }

    @Transactional
    public ManagedClassResponse update(Long id, ClassUpsertRequest request) {
        ClassEntity entity = findClass(id);
        String code = normalizeCode(request.classCode());
        validateDates(request);
        validateSchedule(request);
        ClassStatus status = requiredStatus(request.status());
        if (classRepository.existsByClassCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại");
        }
        boolean hasLessons = lessonRepository.existsByClassEntityId(id);
        if (hasLessons && (!Objects.equals(entity.getStartDate(), request.startDate())
                || request.endDate() != null && !Objects.equals(entity.getEndDate(), request.endDate())
                || request.scheduleDescription() != null && !request.scheduleDescription().isBlank()
                && !Objects.equals(entity.getScheduleDescription(), request.scheduleDescription().trim())
                || request.daysOfWeek() != null && !request.daysOfWeek().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lớp đã có buổi học. Không thể thay đổi ngày hoặc khung lịch tại đây; hãy giữ lịch hiện có.");
        }
        boolean courseOrBranchChanged = !Objects.equals(entity.getCourse().getId(), request.courseId())
                || !Objects.equals(entity.getBranch().getId(), request.branchId());
        if (courseOrBranchChanged && (entity.getCurrentStudents() != null && entity.getCurrentStudents() > 0
                || enrollmentRepository.existsByClassEntityId(id)
                || hasLessons)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể đổi khóa học hoặc cơ sở khi lớp đã có ghi danh hoặc buổi học");
        }
        long occupied = (entity.getCurrentStudents() == null ? 0 : entity.getCurrentStudents())
                + enrollmentRepository.countByClassEntityIdAndStatus(id, EnrollmentStatus.PENDING_PAYMENT);
        if (request.maxStudents() < occupied) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sĩ số tối đa không được nhỏ hơn số học viên đã ghi danh và đang chờ thanh toán");
        }

        Long previousTeacherId = entity.getTeacher() == null ? null : entity.getTeacher().getId();
        Long previousRoomId = entity.getRoom() == null ? null : entity.getRoom().getId();
        entity.setCourse(findCourse(request.courseId()));
        entity.setBranch(findBranch(request.branchId()));
        if (request.teacherId() != null) {
            entity.setTeacher(findTeacher(request.teacherId()));
        }
        if (request.roomId() != null) {
            entity.setRoom(findRoom(request.roomId(), request.branchId()));
        } else if (courseOrBranchChanged && entity.getRoom() != null) {
            entity.setRoom(findRoom(entity.getRoom().getId(), request.branchId()));
        }
        boolean assignmentsChanged = !Objects.equals(previousTeacherId,
                entity.getTeacher() == null ? null : entity.getTeacher().getId())
                || !Objects.equals(previousRoomId, entity.getRoom() == null ? null : entity.getRoom().getId());

        if (request.scheduleDescription() != null && !request.scheduleDescription().isBlank()) {
            entity.setScheduleDescription(request.scheduleDescription().trim());
        } else if (request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()) {
            entity.setScheduleDescription(buildScheduleDescription(request.daysOfWeek(), request.startTime(), request.endTime()));
        }

        entity.setClassCode(code);
        entity.setClassName(request.className().trim());
        entity.setMaxStudents(request.maxStudents());
        entity.setClassType(request.maxStudents() <= 1 ? ClassType.ONE_ON_ONE : ClassType.GROUP);
        entity.setStatus(status);
        entity.setStartDate(request.startDate());
        if (request.endDate() != null) {
            entity.setEndDate(request.endDate());
        }

        try {
            // Nếu lớp chưa có buổi học và có truyền lịch -> sinh buổi học mới
            if (!hasLessons && request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()
                    && request.startTime() != null && request.endTime() != null) {
                List<Lesson> lessons = generateLessons(entity, request.daysOfWeek(), request.startTime(), request.endTime(), request.totalSessions());
                if (!lessons.isEmpty()) {
                    entity.setEndDate(lessons.get(lessons.size() - 1).getLessonDate());
                    lessonRepository.saveAll(lessons);
                }
            } else if (hasLessons && assignmentsChanged) {
                // Nếu lớp đã có buổi học, đồng bộ giáo viên và phòng học sang các buổi học sắp tới (SCHEDULED)
                List<Lesson> existingLessons = lessonRepository.findByClassEntityId(id);
                boolean updated = false;
                for (Lesson l : existingLessons) {
                    if (l.getStatus() == LessonStatus.SCHEDULED
                            && !l.getLessonDate().isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
                        l.setTeacher(entity.getTeacher());
                        l.setRoom(entity.getRoom());
                        updated = true;
                    }
                }
                if (updated) {
                    lessonRepository.saveAll(existingLessons);
                }
            }

            return toResponse(classRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại hoặc dữ liệu liên kết không hợp lệ", ex);
        }
    }

    private List<Lesson> generateLessons(ClassEntity classEntity, List<Integer> daysOfWeek,
                                         LocalTime startTime, LocalTime endTime, Integer totalSessionsReq) {
        if (daysOfWeek == null || daysOfWeek.isEmpty() || startTime == null || endTime == null) {
            return Collections.emptyList();
        }
        List<Lesson> lessons = new ArrayList<>();
        LocalDate currentDate = classEntity.getStartDate();
        int totalSessions = (totalSessionsReq != null && totalSessionsReq > 0) ? Math.min(totalSessionsReq, 60) : 12;
        int sessionCount = 0;
        Set<Integer> targetDays = new HashSet<>(daysOfWeek);
        int safetyLoop = 0;

        while (sessionCount < totalSessions && safetyLoop < totalSessions * 7) {
            int day = currentDate.getDayOfWeek().getValue();
            if (targetDays.contains(day)) {
                sessionCount++;
                Lesson lesson = Lesson.builder()
                        .classEntity(classEntity)
                        .room(classEntity.getRoom())
                        .teacher(classEntity.getTeacher())
                        .sessionNumber(sessionCount)
                        .lessonDate(currentDate)
                        .startTime(startTime)
                        .endTime(endTime)
                        .title("Buổi " + sessionCount + ": " + classEntity.getClassName())
                        .lessonNote("Lịch học tự động theo phân công lớp")
                        .status(LessonStatus.SCHEDULED)
                        .build();
                lessons.add(lesson);
            }
            currentDate = currentDate.plusDays(1);
            safetyLoop++;
        }
        return lessons;
    }

    private String buildScheduleDescription(List<Integer> daysOfWeek, LocalTime startTime, LocalTime endTime) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) return null;
        Map<Integer, String> dayNames = Map.of(
                1, "T2", 2, "T3", 3, "T4", 4, "T5", 5, "T6", 6, "T7", 7, "CN"
        );
        String days = daysOfWeek.stream()
                .sorted()
                .map(d -> dayNames.getOrDefault(d, "T" + d))
                .collect(Collectors.joining("-"));
        if (startTime != null && endTime != null) {
            return String.format("%s (%s - %s)", days, startTime, endTime);
        }
        return days;
    }

    private ManagedClassResponse toResponse(ClassEntity entity) {
        long pending = entity.getId() == null ? 0
                : enrollmentRepository.countByClassEntityIdAndStatus(entity.getId(), EnrollmentStatus.PENDING_PAYMENT);
        int current = entity.getCurrentStudents() == null ? 0 : entity.getCurrentStudents();
        int totalLessons = entity.getId() == null ? 0 : lessonRepository.countByClassEntityId(entity.getId());

        return new ManagedClassResponse(
                entity.getId(),
                entity.getCourse().getId(),
                entity.getCourse().getCode(),
                entity.getCourse().getName(),
                entity.getBranch().getId(),
                entity.getBranch().getName(),
                entity.getRoom() == null ? null : entity.getRoom().getId(),
                entity.getRoom() == null ? null : entity.getRoom().getRoomName(),
                entity.getTeacher() == null ? null : entity.getTeacher().getId(),
                entity.getTeacher() == null ? null : entity.getTeacher().getFullName(),
                entity.getScheduleDescription(),
                totalLessons,
                entity.getClassCode(),
                entity.getClassName(),
                entity.getClassType(),
                entity.getMaxStudents(),
                current,
                pending,
                Math.max((long) entity.getMaxStudents() - current - pending, 0),
                entity.getStatus(),
                entity.getStartDate(),
                entity.getEndDate()
        );
    }

    private ClassEntity findClass(Long id) {
        return classRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp"));
    }

    private Course findCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học"));
    }

    private Branch findBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở"));
    }

    private User findTeacher(Long id) {
        User teacher = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giáo viên"));
        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản được phân công phải là giáo viên");
        }
        return teacher;
    }

    private Room findRoom(Long id, Long branchId) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng học"));
        if (room.getBranch() == null || !Objects.equals(room.getBranch().getId(), branchId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phòng học không thuộc cơ sở được chọn");
        }
        return room;
    }

    private void validateSchedule(ClassUpsertRequest request) {
        if (request.daysOfWeek() == null || request.daysOfWeek().isEmpty()) return;
        if (request.daysOfWeek().stream().anyMatch(day -> day == null || day < 1 || day > 7)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thứ trong tuần phải từ 1 đến 7");
        }
        if (request.startTime() == null || request.endTime() == null || !request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (request.totalSessions() != null && (request.totalSessions() < 1 || request.totalSessions() > 60)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tổng số buổi phải từ 1 đến 60");
        }
    }

    private String normalizeCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9_-]*") || normalized.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mã lớp chỉ gồm chữ không dấu, số, dấu gạch ngang hoặc gạch dưới (tối đa 50 ký tự)");
        }
        return normalized;
    }

    private void validateDates(ClassUpsertRequest request) {
        if (request.endDate() != null && request.startDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc dự kiến phải từ ngày bắt đầu trở đi");
        }
    }

    private ClassStatus requiredStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái lớp không được để trống");
        }
        return parseStatus(value);
    }

    private ClassStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ClassStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái lớp không hợp lệ");
        }
    }
}

