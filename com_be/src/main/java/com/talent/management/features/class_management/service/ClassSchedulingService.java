package com.talent.management.features.class_management.service;

import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.branch_facility_enrollment.repository.RoomRepository;
import com.talent.management.features.branch_facility_enrollment.service.FacilityRulesService;
import com.talent.management.features.class_management.repository.ManagedClassLessonRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class ClassSchedulingService {
    public static final int TEACHER_BREAK_MINUTES = 30;
    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ManagedClassLessonRepository lessonRepository;
    private final FacilityRulesService facilityRules;

    public User getTeacher(Long id) {
        if (id == null) return null;
        // Both class-creation APIs lock teacher first, then room, until the transaction finishes.
        return userRepository.findByIdForScheduleUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy giáo viên"));
    }

    public Room getRoom(Long id) {
        if (id == null) return null;
        return roomRepository.findByIdForScheduleUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy phòng học"));
    }

    public boolean isTeacherSuitable(Course course, User teacher) {
        String subject = teacherSubject(teacher);
        return teacher.getRole() == Role.TEACHER && teacher.getStatus() == UserStatus.ACTIVE
                && !normalizeSubject(subject).isEmpty()
                && normalizeSubject(subject).equals(courseSubject(course));
    }

    public String teacherSubject(User teacher) {
        return teacher.getTeachingSubject() == null || teacher.getTeachingSubject().isBlank()
                ? teacher.getSubject() : teacher.getTeachingSubject();
    }

    public boolean isRoomSuitable(Course course, Room room, int maxStudents) {
        return facilityRules.assignmentProblems(course, room, maxStudents).isEmpty();
    }

    public void validateAssignments(Course course, Branch branch, User teacher, Room room, int maxStudents) {
        if (!Boolean.TRUE.equals(branch.getActive())) {
            throw error(HttpStatus.CONFLICT, "Cơ sở đã ngừng hoạt động");
        }
        if (teacher != null && !isTeacherSuitable(course, teacher)) {
            throw error(HttpStatus.CONFLICT,
                    "Giáo viên phải đang hoạt động và có chuyên môn phù hợp với môn của khóa học. "
                            + "Vui lòng khai báo chuyên môn giảng dạy cụ thể, ví dụ Piano hoặc Guitar.");
        }
        if (room != null) {
            if (room.getBranch() == null || !Objects.equals(room.getBranch().getId(), branch.getId())) {
                throw error(HttpStatus.BAD_REQUEST, "Phòng học không thuộc cơ sở được chọn");
            }
            if (room.getCapacity() == null || maxStudents > room.getCapacity()) {
                throw error(HttpStatus.CONFLICT, "Sĩ số tối đa của lớp vượt sức chứa phòng học");
            }
            List<String> problems = facilityRules.assignmentProblems(course, room, maxStudents);
            if (!problems.isEmpty()) throw error(HttpStatus.CONFLICT, String.join("; ", problems));
        }
    }

    public void validateReadiness(ClassStatus status, boolean hasLessons, User teacher, Room room) {
        if ((status == ClassStatus.OPEN || status == ClassStatus.IN_PROGRESS)
                && (teacher == null || room == null || !hasLessons)) {
            throw error(HttpStatus.CONFLICT,
                    "Lớp đang mở hoặc đang học phải có giáo viên, phòng và lịch học. "
                            + "Hãy chọn trạng thái Dự kiến nếu chưa phân công đầy đủ.");
        }
    }

    public List<Lesson> generateLessons(ClassEntity classEntity, List<Integer> daysOfWeek,
                                        LocalTime startTime, LocalTime endTime, Integer requestedSessions,
                                        LocalDate requestedEndDate) {
        List<Lesson> lessons = new ArrayList<>();
        if (daysOfWeek == null || daysOfWeek.isEmpty()) return lessons;
        if (classEntity.getStartDate() == null || startTime == null || endTime == null
                || !endTime.isAfter(startTime)) {
            throw error(HttpStatus.BAD_REQUEST, "Cần có ngày bắt đầu và giờ kết thúc sau giờ bắt đầu");
        }
        for (Integer day : daysOfWeek) {
            if (day == null || day < 1 || day > 7) {
                throw error(HttpStatus.BAD_REQUEST, "Thứ trong tuần phải từ 1 đến 7");
            }
        }
        Integer courseSessions = classEntity.getCourse().getTotalSessions();
        int totalSessions = requestedSessions != null ? requestedSessions : courseSessions != null ? courseSessions : 12;
        if (totalSessions < 1 || totalSessions > 60) {
            throw error(HttpStatus.BAD_REQUEST, "Tổng số buổi phải từ 1 đến 60");
        }
        Set<Integer> days = new HashSet<>(daysOfWeek);
        LocalDate date = classEntity.getStartDate();
        while (lessons.size() < totalSessions) {
            if (days.contains(date.getDayOfWeek().getValue())) {
                lessons.add(Lesson.builder().classEntity(classEntity)
                        .teacher(classEntity.getTeacher()).room(classEntity.getRoom())
                        .sessionNumber(lessons.size() + 1).lessonDate(date)
                        .startTime(startTime).endTime(endTime)
                        .title("Buổi " + (lessons.size() + 1) + ": " + classEntity.getClassName())
                        .lessonNote("Lịch học tự động theo phân công lớp")
                        .status(LessonStatus.SCHEDULED).build());
            }
            date = date.plusDays(1);
        }
        LocalDateTime firstStart = lessons.get(0).getLessonDate().atTime(startTime);
        if (firstStart.isBefore(LocalDateTime.now(CENTER_ZONE))) {
            throw error(HttpStatus.BAD_REQUEST, "Không thể sinh buổi học bắt đầu trong quá khứ");
        }
        LocalDate calculatedEndDate = lessons.get(lessons.size() - 1).getLessonDate();
        if (requestedEndDate != null && !requestedEndDate.equals(calculatedEndDate)) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc phải khớp với buổi học cuối: " + calculatedEndDate);
        }
        classEntity.setEndDate(calculatedEndDate);
        return lessons;
    }

    public boolean canReassign(Lesson lesson) {
        return lesson.getStatus() == LessonStatus.SCHEDULED
                && !lesson.getLessonDate().atTime(lesson.getStartTime()).isBefore(LocalDateTime.now(CENTER_ZONE));
    }

    public String describeSchedule(List<Integer> daysOfWeek, LocalTime startTime, LocalTime endTime) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) return null;
        List<Integer> sortedDays = new ArrayList<>(new HashSet<>(daysOfWeek));
        sortedDays.sort(Integer::compareTo);
        List<String> labels = new ArrayList<>();
        for (int day : sortedDays) {
            labels.add(day == 7 ? "CN" : "T" + (day + 1));
        }
        return String.join("-", labels) + " (" + startTime + " - " + endTime + ")";
    }

    public void validateLessonConflicts(List<Lesson> lessons) {
        if (lessons.isEmpty()) return;
        LocalDate from = lessons.get(0).getLessonDate();
        LocalDate to = from;
        Set<Long> editedLessonIds = new HashSet<>();
        for (Lesson lesson : lessons) {
            if (lesson.getLessonDate().isBefore(from)) from = lesson.getLessonDate();
            if (lesson.getLessonDate().isAfter(to)) to = lesson.getLessonDate();
            if (lesson.getId() != null) editedLessonIds.add(lesson.getId());
        }
        Map<Long, List<Lesson>> teachers = new HashMap<>();
        Map<Long, List<Lesson>> rooms = new HashMap<>();
        for (int i = 0; i < lessons.size(); i++) {
            Lesson candidate = lessons.get(i);
            User teacher = candidate.getTeacher();
            Room room = candidate.getRoom();
            if (teacher != null && !teachers.containsKey(teacher.getId())) {
                teachers.put(teacher.getId(), lessonRepository.findByTeacherIdAndLessonDateBetween(
                        teacher.getId(), from.minusDays(1), to.plusDays(1)));
            }
            if (room != null && !rooms.containsKey(room.getId())) {
                rooms.put(room.getId(), lessonRepository.findByRoomIdAndLessonDateBetween(room.getId(), from, to));
            }
            List<Lesson> teacherLessons = teacher == null ? List.of() : teachers.get(teacher.getId());
            List<Lesson> roomLessons = room == null ? List.of() : rooms.get(room.getId());
            for (Lesson existing : teacherLessons) {
                if (existing.getStatus() != LessonStatus.CANCELLED && !editedLessonIds.contains(existing.getId())) {
                    checkTeacherConflict(candidate, existing);
                }
            }
            for (Lesson existing : roomLessons) {
                if (existing.getStatus() != LessonStatus.CANCELLED && !editedLessonIds.contains(existing.getId())) {
                    checkRoomConflict(candidate, existing);
                }
            }
            // Reassigned lessons must not conflict with each other either.
            for (int j = 0; j < i; j++) {
                checkTeacherConflict(candidate, lessons.get(j));
                checkRoomConflict(candidate, lessons.get(j));
            }
        }
    }

    private void checkTeacherConflict(Lesson candidate, Lesson existing) {
        User existingTeacher = existing.getTeacher();
        if (existingTeacher == null && existing.getClassEntity() != null) existingTeacher = existing.getClassEntity().getTeacher();
        if (candidate.getTeacher() != null && existingTeacher != null
                && Objects.equals(candidate.getTeacher().getId(), existingTeacher.getId())
                && overlaps(candidate, existing, TEACHER_BREAK_MINUTES)) {
            throw error(HttpStatus.CONFLICT, "Giáo viên bị trùng lịch hoặc chưa đủ 30 phút nghỉ với buổi "
                    + describe(existing));
        }
    }

    private void checkRoomConflict(Lesson candidate, Lesson existing) {
        Room existingRoom = existing.getRoom();
        if (existingRoom == null && existing.getClassEntity() != null) existingRoom = existing.getClassEntity().getRoom();
        if (candidate.getRoom() != null && existingRoom != null
                && Objects.equals(candidate.getRoom().getId(), existingRoom.getId())
                && overlaps(candidate, existing, 0)) {
            throw error(HttpStatus.CONFLICT, "Phòng học bị trùng lịch với buổi " + describe(existing));
        }
    }

    private boolean overlaps(Lesson first, Lesson second, int breakMinutes) {
        LocalDateTime firstStart = first.getLessonDate().atTime(first.getStartTime());
        LocalDateTime firstEnd = first.getLessonDate().atTime(first.getEndTime());
        LocalDateTime secondStart = second.getLessonDate().atTime(second.getStartTime());
        LocalDateTime secondEnd = second.getLessonDate().atTime(second.getEndTime());
        return firstStart.isBefore(secondEnd.plusMinutes(breakMinutes))
                && secondStart.isBefore(firstEnd.plusMinutes(breakMinutes));
    }

    private String describe(Lesson lesson) {
        String classCode = lesson.getClassEntity() == null ? "" : lesson.getClassEntity().getClassCode();
        return classCode + " ngày " + lesson.getLessonDate() + " (" + lesson.getStartTime() + " - " + lesson.getEndTime() + ")";
    }

    private String courseSubject(Course course) {
        return course.getProgram() == null ? "" : normalizeSubject(course.getProgram().getCode());
    }

    public String normalizeSubject(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_-]+", " ");
        if (normalized.startsWith("BO MON ")) normalized = normalized.substring(7);
        return switch (normalized) {
            case "DAN PIANO" -> "PIANO";
            case "DAN GUITAR" -> "GUITAR";
            case "CO VUA" -> "CHESS";
            case "BOI", "BOI LOI", "SWIM" -> "SWIMMING";
            case "MUA", "BALLET" -> "DANCE";
            case "VO", "VO THUAT" -> "MARTIAL ARTS";
            default -> normalized;
        };
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
