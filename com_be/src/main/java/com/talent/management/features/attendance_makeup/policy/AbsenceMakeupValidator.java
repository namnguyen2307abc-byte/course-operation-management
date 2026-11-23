package com.talent.management.features.attendance_makeup.policy;

import com.talent.management.features.attendance_makeup.dto.request.MakeupResolveRequest;
import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.features.weekly_schedule.repository.AttendanceEnrollmentRepository;
import com.talent.management.features.weekly_schedule.repository.AttendanceLessonRepository;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.MakeupRegistration;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.ClassStatus;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.enums.LessonStatus;
import com.talent.management.shared.enums.MakeupStatus;
import com.talent.management.shared.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ============================================================================
 * KIỂM TRA PHÂN QUYỀN, DỮ LIỆU ĐẦU VÀO VÀ ĐIỀU KIỆN XẾP LỊCH HỌC BÙ
 * ============================================================================
 * Đóng gói tập trung toàn bộ quy tắc hợp lệ của phân hệ Nghỉ học & Học bù:
 *   1. Kiểm tra ID đầu vào & ghi chú bắt buộc.
 *   2. Kiểm tra phân quyền vai trò (PARENT, TEACHER, STAFF, BRANCH_MANAGER, ADMIN).
 *   3. Kiểm tra quy tắc chọn buổi học bù: chưa diễn ra, cùng chương trình/trình độ,
 *      từ bài học nghỉ trở đi, còn sĩ số chỗ trống, và không trùng khung giờ học viên.
 */
@Component
@RequiredArgsConstructor
public class AbsenceMakeupValidator {

    public static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Trạng thái ca bù đang chiếm chỗ trong lớp đích. */
    public static final List<MakeupStatus> SEAT_HOLDING_STATUSES =
            List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);

    public static final Set<Role> COORDINATOR_ROLES = EnumSet.of(Role.STAFF, Role.BRANCH_MANAGER, Role.ADMIN);
    public static final Set<Role> REVIEWER_ROLES = EnumSet.of(Role.TEACHER, Role.STAFF, Role.BRANCH_MANAGER, Role.ADMIN);
    public static final Set<Role> PARTICIPANT_ROLES = EnumSet.of(Role.PARENT, Role.TEACHER, Role.STAFF, Role.BRANCH_MANAGER, Role.ADMIN);

    private final AttendanceLessonRepository attendanceLessonRepository;
    private final AttendanceEnrollmentRepository attendanceEnrollmentRepository;
    private final MakeupRegistrationRepository makeupRegistrationRepository;

    // -------------------------------------------------------------------------
    // 1. KIỂM TRA ID, THỜI GIAN & GHI CHÚ ĐẦU VÀO
    // -------------------------------------------------------------------------

    public void requireValidId(Long id, String targetName) {
        if (id == null || id <= 0) {
            throw new AttendanceMakeupException("Mã " + targetName + " không hợp lệ!");
        }
    }

    public String requireResolveNote(MakeupResolveRequest request, String errorMessage) {
        if (request == null || request.getNote() == null || request.getNote().isBlank()) {
            throw new AttendanceMakeupException(errorMessage);
        }
        return request.getNote().trim();
    }

    /** Buổi học đã bắt đầu hoặc đã qua theo giờ trung tâm. */
    public boolean hasStarted(Lesson lesson, LocalDateTime now) {
        if (lesson == null || lesson.getLessonDate() == null) return true;
        LocalDate today = now.toLocalDate();
        if (lesson.getLessonDate().isBefore(today)) return true;
        return lesson.getLessonDate().isEqual(today)
                && lesson.getStartTime() != null
                && !lesson.getStartTime().isAfter(now.toLocalTime());
    }

    // -------------------------------------------------------------------------
    // 2. KIỂM TRA PHÂN QUYỀN VAI TRÒ & QUYỀN TRÊN DỮ LIỆU
    // -------------------------------------------------------------------------

    public void validateParticipantRole(User user) {
        if (user == null || user.getRole() == null || !PARTICIPANT_ROLES.contains(user.getRole())) {
            throw new AttendanceMakeupException("Tài khoản của bạn không có quyền truy cập phân hệ Nghỉ học & Học bù!");
        }
    }

    /** TEACHER, STAFF, BRANCH_MANAGER và ADMIN được duyệt đơn nghỉ. */
    public void validateReviewPermission(User user) {
        if (user == null || user.getRole() == null || !REVIEWER_ROLES.contains(user.getRole())) {
            throw new AttendanceMakeupException(
                    "Chỉ Giáo viên, Nhân viên hoặc Quản trị mới có quyền xét duyệt đơn xin nghỉ học!");
        }
    }

    public void validateStaffOrAdmin(User user) {
        if (user == null || user.getRole() == null || !COORDINATOR_ROLES.contains(user.getRole())) {
            throw new AttendanceMakeupException(
                    "Chỉ Nhân viên, Quản lý chi nhánh hoặc Quản trị viên mới có quyền xếp/hủy lịch học bù!");
        }
    }

    public void validateMarkPermission(User user) {
        if (user == null || user.getRole() == null || !REVIEWER_ROLES.contains(user.getRole())) {
            throw new AttendanceMakeupException(
                    "Chỉ Giáo viên, Nhân viên hoặc Quản trị mới có quyền điểm danh!");
        }
    }

    public void validateStudentAccess(User user, Student student) {
        boolean denied = user.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(user.getId()));
        if (denied) {
            throw new AttendanceMakeupException("Bạn không có quyền truy cập hồ sơ học viên này");
        }
    }

    /** Kiểm tra user có phải giáo viên phụ trách trực tiếp của buổi học hoặc lớp học hay không. */
    public boolean isTeacherOfLesson(User teacher, Lesson lesson) {
        if (teacher == null || lesson == null) {
            return false;
        }
        Long teacherId = teacher.getId();
        if (lesson.getTeacher() != null && teacherId.equals(lesson.getTeacher().getId())) {
            return true;
        }
        return lesson.getClassEntity() != null && lesson.getClassEntity().getTeacher() != null
                && teacherId.equals(lesson.getClassEntity().getTeacher().getId());
    }

    // -------------------------------------------------------------------------
    // 3. KIỂM TRA BUỔI HỌC BÙ HỢP LỆ, SĨ SỐ & XUNG ĐỘT LỊCH HỌC
    // -------------------------------------------------------------------------

    public void validateMakeupTarget(MakeupRegistration mr, Lesson target) {
        if (target.getStatus() == LessonStatus.CANCELLED) {
            throw new AttendanceMakeupException("Buổi học được chọn đã bị hủy!");
        }
        if (target.getStatus() == LessonStatus.COMPLETED) {
            throw new AttendanceMakeupException("Buổi học được chọn đã kết thúc!");
        }
        if (hasStarted(target, LocalDateTime.now(CENTER_ZONE))) {
            throw new AttendanceMakeupException("Không thể xếp học bù vào buổi học đã bắt đầu hoặc đã diễn ra!");
        }

        Lesson original = mr.getOriginalLesson();
        if (original != null && original.getLessonDate() != null && target.getLessonDate() != null) {
            if (target.getLessonDate().isBefore(original.getLessonDate())) {
                throw new AttendanceMakeupException("Không thể xếp học bù vào ngày trước ngày của buổi học gốc bị nghỉ ("
                        + target.getLessonDate() + " < " + original.getLessonDate() + ")!");
            }
            if (target.getLessonDate().isEqual(original.getLessonDate())
                    && original.getStartTime() != null && target.getStartTime() != null
                    && target.getStartTime().isBefore(original.getStartTime())) {
                throw new AttendanceMakeupException("Buổi học bù cùng ngày phải diễn ra sau giờ của buổi học gốc!");
            }
        }

        if (original != null && original.getSessionNumber() != null && target.getSessionNumber() != null) {
            if (target.getSessionNumber() < original.getSessionNumber()) {
                throw new AttendanceMakeupException("Không thể xếp học bù vào bài học cũ hơn buổi nghỉ (Buổi #"
                        + target.getSessionNumber() + " < Buổi #" + original.getSessionNumber()
                        + "). Vui lòng chọn lớp học cùng bài hoặc từ bài này trở đi!");
            }
        }

        ClassEntity targetClass = target.getClassEntity();
        if (targetClass != null) {
            if (targetClass.getStatus() != null
                    && targetClass.getStatus() != ClassStatus.OPEN
                    && targetClass.getStatus() != ClassStatus.IN_PROGRESS) {
                throw new AttendanceMakeupException("Lớp " + targetClass.getClassName()
                        + " hiện ở trạng thái " + targetClass.getStatus() + ", không nhận xếp học bù!");
            }

            Course origCourse = (original != null && original.getClassEntity() != null)
                    ? original.getClassEntity().getCourse() : null;
            Course targetCourse = targetClass.getCourse();
            if (origCourse != null && targetCourse != null) {
                Long origProgramId = origCourse.getProgram() != null ? origCourse.getProgram().getId() : null;
                Long targetProgramId = targetCourse.getProgram() != null ? targetCourse.getProgram().getId() : null;
                if (origProgramId != null && targetProgramId != null && !origProgramId.equals(targetProgramId)) {
                    throw new AttendanceMakeupException("Lớp bù khác chương trình/bộ môn với lớp gốc, không thể xếp học bù!");
                }
                String originalLevel = origCourse.getLevel();
                String targetLevel = targetCourse.getLevel();
                if (originalLevel != null && !originalLevel.isBlank() && targetLevel != null && !targetLevel.isBlank()) {
                    if (!originalLevel.equalsIgnoreCase(targetLevel)) {
                        throw new AttendanceMakeupException(
                                "Lớp bù khác trình độ (" + targetLevel + " ≠ " + originalLevel + "). Vui lòng chọn lớp cùng level!");
                    }
                    if ((origProgramId == null || targetProgramId == null) && !Objects.equals(origCourse.getId(), targetCourse.getId())) {
                        throw new AttendanceMakeupException("Lớp bù khác khóa học với lớp gốc, không thể xếp học bù!");
                    }
                } else if (!Objects.equals(origCourse.getId(), targetCourse.getId())) {
                    throw new AttendanceMakeupException("Lớp bù khác khóa học với lớp gốc (chưa cấu hình trình độ tương đương)!");
                }
            }

            Set<Long> enrolledClassIds = new HashSet<>(attendanceEnrollmentRepository
                    .findClassIdsByStudentIdAndStatusIn(mr.getStudent().getId(),
                            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING_PAYMENT)));
            if (enrolledClassIds.contains(targetClass.getId())) {
                throw new AttendanceMakeupException("Học viên đã thuộc lớp này, hãy chọn lớp khác để học bù!");
            }

            int base = targetClass.getCurrentStudents() != null ? targetClass.getCurrentStudents() : 0;
            long pendingSeats = attendanceEnrollmentRepository
                    .countByClassEntityIdAndStatus(targetClass.getId(), EnrollmentStatus.PENDING_PAYMENT);
            long makeupSeats = makeupRegistrationRepository
                    .countByTargetLessonIdAndStatusIn(target.getId(), SEAT_HOLDING_STATUSES);
            boolean alreadyCounted = mr.getTargetLesson() != null
                    && mr.getTargetLesson().getId().equals(target.getId())
                    && SEAT_HOLDING_STATUSES.contains(mr.getStatus());
            long occupied = base + pendingSeats + makeupSeats - (alreadyCounted ? 1 : 0);
            Integer max = targetClass.getMaxStudents();
            if (max != null && occupied >= max) {
                throw new AttendanceMakeupException("Lớp " + targetClass.getClassName() + " đã đủ sĩ số ("
                        + occupied + "/" + max + "), không thể thêm học viên học bù!");
            }
        }

        Lesson conflictLesson = findStudentTimeConflict(mr.getStudent().getId(), target, mr.getOriginalLesson(), mr.getId());
        if (conflictLesson != null) {
            String conflictClass = conflictLesson.getClassEntity() != null
                    ? conflictLesson.getClassEntity().getClassName() : "lớp khác";
            String conflictTime = conflictLesson.getStartTime() + "-" + conflictLesson.getEndTime();
            throw new AttendanceMakeupException("Học viên bị trùng lịch học với " + conflictClass
                    + " vào ngày " + conflictLesson.getLessonDate() + " (" + conflictTime + "), vui lòng chọn ca khác!");
        }
    }

    public boolean isLessonUsableForMakeup(Lesson l, Set<Long> enrolledClassIds, LocalDateTime nowDt, Lesson original) {
        if (l.getStatus() == LessonStatus.CANCELLED || l.getStatus() == LessonStatus.COMPLETED) return false;
        ClassEntity cls = l.getClassEntity();
        if (cls == null || enrolledClassIds.contains(cls.getId())) return false;
        if (cls.getStatus() != null && cls.getStatus() != ClassStatus.OPEN && cls.getStatus() != ClassStatus.IN_PROGRESS) {
            return false;
        }
        if (hasStarted(l, nowDt)) return false;

        if (original != null && original.getLessonDate() != null) {
            if (l.getLessonDate().isBefore(original.getLessonDate())) return false;
            if (l.getLessonDate().isEqual(original.getLessonDate())
                    && original.getStartTime() != null && l.getStartTime() != null
                    && l.getStartTime().isBefore(original.getStartTime())) {
                return false;
            }
        }

        if (original != null && original.getSessionNumber() != null && l.getSessionNumber() != null) {
            if (l.getSessionNumber() < original.getSessionNumber()) return false;
        }

        return true;
    }

    /** Tìm buổi học bị trùng khung giờ với ca bù dự kiến (gồm cả lịch chính khóa và ca bù SCHEDULED khác). */
    public Lesson findStudentTimeConflict(Long studentId, Lesson target, Lesson originalLesson, Long excludeMakeupId) {
        if (studentId == null || target == null || target.getLessonDate() == null
                || target.getStartTime() == null || target.getEndTime() == null) {
            return null;
        }
        LocalDate targetDate = target.getLessonDate();
        Long origLessonId = originalLesson != null ? originalLesson.getId() : null;

        List<Lesson> regularLessons = attendanceLessonRepository
                .findLessonsByStudentIdAndDateBetween(studentId, targetDate, targetDate);
        if (regularLessons != null) {
            for (Lesson l : regularLessons) {
                if (l == null || Objects.equals(l.getId(), target.getId())) continue;
                if (origLessonId != null && Objects.equals(l.getId(), origLessonId)) continue;
                if (l.getStatus() == LessonStatus.CANCELLED) continue;
                if (isTimeOverlapping(l, target)) {
                    return l;
                }
            }
        }

        List<MakeupRegistration> scheduledMakeups = makeupRegistrationRepository
                .findByStudentIdAndStatusIn(studentId, List.of(MakeupStatus.SCHEDULED));
        if (scheduledMakeups != null) {
            for (MakeupRegistration other : scheduledMakeups) {
                if (other == null || Objects.equals(other.getId(), excludeMakeupId)) continue;
                Lesson otherTarget = other.getTargetLesson();
                if (otherTarget == null || !targetDate.equals(otherTarget.getLessonDate())) continue;
                if (isTimeOverlapping(otherTarget, target)) {
                    return otherTarget;
                }
            }
        }
        return null;
    }

    private boolean isTimeOverlapping(Lesson a, Lesson b) {
        if (a.getStartTime() == null || a.getEndTime() == null
                || b.getStartTime() == null || b.getEndTime() == null) {
            return false;
        }
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }

    // -------------------------------------------------------------------------
    // 4. TRÍCH XUẤT PHÒNG HỌC, GIÁO VIÊN & VAI TRÒ HIỂN THỊ
    // -------------------------------------------------------------------------

    /** Phòng của buổi học; buổi chưa gán phòng lấy phòng của lớp. */
    public String resolveRoomName(Lesson lesson) {
        if (lesson == null) return null;
        if (lesson.getRoom() != null) return lesson.getRoom().getRoomName();
        return lesson.getClassEntity() != null && lesson.getClassEntity().getRoom() != null
                ? lesson.getClassEntity().getRoom().getRoomName() : null;
    }

    /** Giáo viên phụ trách chính của buổi học (ưu tiên GV của Lesson, fallback sang GV của ClassEntity). */
    public User resolvePrimaryTeacher(Lesson lesson) {
        if (lesson == null) return null;
        if (lesson.getTeacher() != null) return lesson.getTeacher();
        return lesson.getClassEntity() != null ? lesson.getClassEntity().getTeacher() : null;
    }

    public String actorRoleLabel(User user) {
        if (user == null || user.getRole() == null) return "Người dùng";
        return switch (user.getRole()) {
            case PARENT -> "Phụ huynh";
            case TEACHER -> "Giáo viên";
            case STAFF -> "Giáo vụ";
            case BRANCH_MANAGER -> "Quản lý chi nhánh";
            case ADMIN -> "Quản trị viên";
            default -> "Nhân sự";
        };
    }
}
