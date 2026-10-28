package com.talent.management.features.class_management.service;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.branch_facility_enrollment.repository.BranchRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.class_management.dto.ClassUpsertRequest;
import com.talent.management.features.class_management.dto.ManagedClassResponse;
import com.talent.management.features.class_management.dto.ClassTeacherOption;
import com.talent.management.features.class_management.dto.ClassRoomOption;
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
import com.talent.management.shared.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.talent.management.shared.enums.LessonStatus;
import java.util.*;

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
    private final ClassSchedulingService schedulingService;

    @Transactional(readOnly = true)
    public List<ClassTeacherOption> getTeacherOptions(Long courseId) {
        Course course = findCourse(courseId);
        List<ClassTeacherOption> options = new ArrayList<>();
        for (User teacher : userRepository.findByRole(Role.TEACHER)) {
            if (schedulingService.isTeacherSuitable(course, teacher)) {
                options.add(new ClassTeacherOption(teacher.getId(), teacher.getFullName(), schedulingService.teacherSubject(teacher)));
            }
        }
        return options;
    }

    @Transactional(readOnly = true)
    public List<ClassRoomOption> getRoomOptions(Long courseId, Long branchId, int maxStudents) {
        if (maxStudents < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sĩ số tối đa phải lớn hơn 0");
        }
        Course course = findCourse(courseId);
        Branch branch = findBranch(branchId);
        List<ClassRoomOption> options = new ArrayList<>();
        if (!Boolean.TRUE.equals(branch.getActive())) return options;
        for (Room room : roomRepository.findByBranchId(branchId)) {
            if (schedulingService.isRoomSuitable(course, room, maxStudents)) {
                options.add(new ClassRoomOption(room.getId(), room.getRoomCode(), room.getRoomName(),
                        room.getCapacity(), room.getRoomType()));
            }
        }
        return options;
    }

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
        Course course = findCourse(request.courseId());
        User teacher = schedulingService.getTeacher(request.teacherId());
        Room room = schedulingService.getRoom(request.roomId());
        schedulingService.validateAssignments(course, branch, teacher, room, request.maxStudents());

        String scheduleDesc = request.scheduleDescription();
        if (request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()) {
            scheduleDesc = schedulingService.describeSchedule(request.daysOfWeek(), request.startTime(), request.endTime());
        }

        LocalDate endDate = request.endDate();
        if (endDate == null) {
            endDate = request.startDate().plusMonths(3);
        }

        ClassEntity entity = ClassEntity.builder()
                .course(course)
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
            List<Lesson> lessons = schedulingService.generateLessons(entity, request.daysOfWeek(),
                    request.startTime(), request.endTime(), request.totalSessions(), request.endDate());
            schedulingService.validateReadiness(status, !lessons.isEmpty(), teacher, room);
            schedulingService.validateLessonConflicts(lessons);
            ClassEntity saved = classRepository.saveAndFlush(entity);
            if (!lessons.isEmpty()) {
                lessonRepository.saveAll(lessons);
                log.info("Đã sinh tự động {} buổi học cho lớp {}", lessons.size(), saved.getClassCode());
            }

            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại hoặc dữ liệu liên kết không hợp lệ", ex);
        }
    }

    @Transactional
    public ManagedClassResponse update(Long id, ClassUpsertRequest request) {
        ClassEntity entity = classRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp"));
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

        User previousTeacher = entity.getTeacher();
        Room previousRoom = entity.getRoom();
        Long previousTeacherId = previousTeacher == null ? null : previousTeacher.getId();
        Long previousRoomId = previousRoom == null ? null : previousRoom.getId();
        boolean capacityChanged = !Objects.equals(request.maxStudents(), entity.getMaxStudents());
        boolean becomingActive = status != entity.getStatus()
                && (status == ClassStatus.OPEN || status == ClassStatus.IN_PROGRESS);
        entity.setCourse(findCourse(request.courseId()));
        entity.setBranch(findBranch(request.branchId()));
        if (request.clearTeacher() && request.teacherId() != null || request.clearRoom() && request.roomId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể vừa bỏ vừa chọn cùng một phân công");
        }
        // Missing IDs preserve older clients; explicit clear flags remove an assignment on the edit form.
        Long teacherId = request.clearTeacher() ? null : request.teacherId() != null ? request.teacherId() : previousTeacherId;
        Long roomId = request.clearRoom() ? null : request.roomId() != null ? request.roomId() : previousRoomId;
        boolean teacherChanged = !Objects.equals(previousTeacherId, teacherId);
        boolean roomChanged = !Objects.equals(previousRoomId, roomId);
        List<Lesson> existingLessons = hasLessons && (teacherChanged || roomChanged || capacityChanged || becomingActive)
                ? lessonRepository.findByClassEntityId(id) : List.of();
        // Preserve the effective old assignment before changing a default used by legacy lesson rows.
        for (Lesson lesson : existingLessons) {
            if (!schedulingService.canReassign(lesson)) {
                if (teacherChanged && lesson.getTeacher() == null) lesson.setTeacher(previousTeacher);
                if (roomChanged && lesson.getRoom() == null) lesson.setRoom(previousRoom);
            }
        }
        entity.setTeacher(schedulingService.getTeacher(teacherId));
        // Lock all involved rooms in ID order so a facility edit cannot invalidate this update midway.
        Set<Long> roomIds = new TreeSet<>();
        if (roomId != null) roomIds.add(roomId);
        for (Lesson lesson : existingLessons) {
            Room actualRoom = lesson.getRoom() == null ? previousRoom : lesson.getRoom();
            if (actualRoom != null && isRemainingLesson(lesson)) roomIds.add(actualRoom.getId());
        }
        Map<Long, Room> lockedRooms = new HashMap<>();
        for (Long selectedId : roomIds) lockedRooms.put(selectedId, schedulingService.getRoom(selectedId));
        entity.setRoom(roomId == null ? null : lockedRooms.get(roomId));
        if (!hasLessons || courseOrBranchChanged || roomChanged || capacityChanged) {
            schedulingService.validateAssignments(entity.getCourse(), entity.getBranch(), entity.getTeacher(),
                    entity.getRoom(), request.maxStudents());
        } else if (teacherChanged) {
            schedulingService.validateAssignments(entity.getCourse(), entity.getBranch(), entity.getTeacher(),
                    null, request.maxStudents());
        }

        if (request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()) {
            entity.setScheduleDescription(schedulingService.describeSchedule(request.daysOfWeek(), request.startTime(), request.endTime()));
        } else if (request.scheduleDescription() != null && !request.scheduleDescription().isBlank()) {
            entity.setScheduleDescription(request.scheduleDescription().trim());
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
            List<Lesson> changedLessons = new ArrayList<>();
            // Nếu lớp chưa có buổi học và có truyền lịch -> sinh buổi học mới
            if (!hasLessons && request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()
                    && request.startTime() != null && request.endTime() != null) {
                changedLessons = schedulingService.generateLessons(entity, request.daysOfWeek(), request.startTime(),
                        request.endTime(), request.totalSessions(), request.endDate());
            } else if (!existingLessons.isEmpty()) {
                updateLessonAssignments(entity, existingLessons, previousTeacher, previousRoom,
                        teacherChanged, roomChanged, capacityChanged || becomingActive);
            } else if (becomingActive) {
                schedulingService.validateAssignments(entity.getCourse(), entity.getBranch(), entity.getTeacher(),
                        entity.getRoom(), request.maxStudents());
            }
            schedulingService.validateReadiness(status, hasLessons || !changedLessons.isEmpty(),
                    entity.getTeacher(), entity.getRoom());
            schedulingService.validateLessonConflicts(changedLessons);
            if (!changedLessons.isEmpty()) lessonRepository.saveAll(changedLessons);
            return toResponse(classRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp đã tồn tại hoặc dữ liệu liên kết không hợp lệ", ex);
        }
    }

    private boolean isRemainingLesson(Lesson lesson) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        return lesson.getStatus() == LessonStatus.SCHEDULED
                && lesson.getLessonDate().atTime(lesson.getEndTime()).isAfter(now);
    }

    private void updateLessonAssignments(ClassEntity entity, List<Lesson> lessons, User oldTeacher, Room oldRoom,
                                          boolean teacherChanged, boolean roomChanged, boolean validateConditions) {
        List<Lesson> candidates = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (!isRemainingLesson(lesson)) continue;
            User actualTeacher = lesson.getTeacher() == null ? oldTeacher : lesson.getTeacher();
            Room actualRoom = lesson.getRoom() == null ? oldRoom : lesson.getRoom();
            if (schedulingService.canReassign(lesson)) {
                if (teacherChanged) actualTeacher = entity.getTeacher();
                if (roomChanged) actualRoom = entity.getRoom();
            }
            if (validateConditions || roomChanged || (teacherChanged && entity.getStatus() != ClassStatus.PLANNING)) {
                schedulingService.validateAssignments(entity.getCourse(), entity.getBranch(), actualTeacher,
                        actualRoom, entity.getMaxStudents());
            }
            candidates.add(Lesson.builder().id(lesson.getId()).classEntity(entity).teacher(actualTeacher).room(actualRoom)
                    .lessonDate(lesson.getLessonDate()).startTime(lesson.getStartTime()).endTime(lesson.getEndTime())
                    .status(lesson.getStatus()).build());
        }
        if (candidates.isEmpty() && validateConditions) {
            schedulingService.validateAssignments(entity.getCourse(), entity.getBranch(), entity.getTeacher(),
                    entity.getRoom(), entity.getMaxStudents());
        }
        if (!teacherChanged && !roomChanged) return;
        schedulingService.validateLessonConflicts(candidates);

        // Validate copies first. Then update only the changed assignment, preserving overrides and history.
        List<Lesson> changed = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (schedulingService.canReassign(lesson)) {
                if (teacherChanged) lesson.setTeacher(entity.getTeacher());
                if (roomChanged) lesson.setRoom(entity.getRoom());
                changed.add(lesson);
            } else {
                boolean savedFallback = false;
                if (teacherChanged && lesson.getTeacher() == null && oldTeacher != null) {
                    lesson.setTeacher(oldTeacher);
                    savedFallback = true;
                }
                if (roomChanged && lesson.getRoom() == null && oldRoom != null) {
                    lesson.setRoom(oldRoom);
                    savedFallback = true;
                }
                if (savedFallback) changed.add(lesson);
            }
        }
        if (!changed.isEmpty()) lessonRepository.saveAll(changed);
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

