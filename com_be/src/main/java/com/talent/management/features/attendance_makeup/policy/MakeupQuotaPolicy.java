package com.talent.management.features.attendance_makeup.policy;

import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.MakeupRegistration;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.enums.MakeupStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ============================================================================
 * CHÍNH SÁCH HẠN MỨC HỌC BÙ (TỐI ĐA 3 BUỔI ĐƯỢC XẾP HỌC BÙ / 1 KHÓA HỌC)
 * ============================================================================
 * Quy tắc tính 1 lần học bù:
 *   - Chỉ tính khi học viên ĐÃ ĐƯỢC ĐỔI SANG LỚP KHÁC (SCHEDULED):
 *       * Đã tham gia và điểm danh Có mặt (COMPLETED)
 *       * Hoặc đã đổi sang lớp khác nhưng không đi / chưa điểm danh (SCHEDULED / MISSED / CANCELLED tag "- KHÓA]")
 *   - KHÔNG tính khi mới gửi đơn xin nghỉ (PENDING), mới duyệt chờ xếp lớp (MakeupStatus.PENDING),
 *     hay ca đã hủy trước khi xếp/chưa có lớp phù hợp.
 */
@Component
@RequiredArgsConstructor
public class MakeupQuotaPolicy {

    /** Số buổi học bù tối đa của một học viên trong một khóa học. */
    public static final int MAKEUP_LIMIT = AttendanceMakeupMapper.MAKEUP_LIMIT_PER_COURSE;

    /** Chỉ các ca đã được xếp đổi sang lớp khác mới tính vào hạn mức 3 buổi/khóa. */
    public static final List<MakeupStatus> COUNTED_MAKEUP_STATUSES =
            List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);

    /** Tag ghi chú nhận diện ca bù bị khóa do học viên vắng mặt ở buổi bù (vẫn tính vào hạn mức 3/3). */
    public static final String LOCK_NOTE_PATTERN = "%- KHÓA]%";

    private final MakeupRegistrationRepository makeupRegistrationRepository;

    public Long courseIdOf(Lesson lesson) {
        return lesson != null && lesson.getClassEntity() != null && lesson.getClassEntity().getCourse() != null
                ? lesson.getClassEntity().getCourse().getId() : null;
    }

    public Long classIdOf(Lesson lesson) {
        return lesson != null && lesson.getClassEntity() != null ? lesson.getClassEntity().getId() : null;
    }

    public String courseQuotaCacheKey(Student student, Lesson lesson) {
        Long cId = courseIdOf(lesson);
        Long clsId = classIdOf(lesson);
        return (student != null ? student.getId() : "null") + "_"
                + (cId != null ? "C_" + cId : (clsId != null ? "L_" + clsId : "none"));
    }

    /** Đếm số buổi đã được xếp học bù của học viên trong khóa học (hoặc lớp). */
    public int countMakeups(Student student, Lesson lesson, List<MakeupStatus> statuses) {
        if (student == null) return 0;
        Long courseId = courseIdOf(lesson);
        Long classId = classIdOf(lesson);
        long count = courseId != null
                ? makeupRegistrationRepository.countQuotaByStudentAndCourse(student.getId(), courseId, statuses, LOCK_NOTE_PATTERN)
                : (classId != null
                    ? makeupRegistrationRepository.countQuotaByStudentAndClass(student.getId(), classId, statuses, LOCK_NOTE_PATTERN)
                    : makeupRegistrationRepository.countQuotaByStudent(student.getId(), statuses, LOCK_NOTE_PATTERN));
        return (int) count;
    }

    /** Đếm tổng số buổi đã được xếp học bù trên toàn hồ sơ học viên. */
    public int countTotalMakeupsForStudent(Long studentId) {
        if (studentId == null) return 0;
        return (int) makeupRegistrationRepository.countQuotaByStudent(studentId, COUNTED_MAKEUP_STATUSES, LOCK_NOTE_PATTERN);
    }

    /**
     * Số buổi học bù đã được xếp/sử dụng trong khóa học:
     * chỉ tính các ca đã đổi sang lớp khác (SCHEDULED, COMPLETED, MISSED, CANCELLED - KHÓA]).
     */
    public int resolveUsedCourseQuota(Student student, Lesson lesson) {
        return countMakeups(student, lesson, COUNTED_MAKEUP_STATUSES);
    }

    /** Kiểm tra chặn khi tạo mới đơn xin nghỉ kèm đăng ký học bù. */
    public void assertCanCreateMakeupAbsence(Student student, Lesson lesson) {
        int counted = resolveUsedCourseQuota(student, lesson);
        if (counted >= MAKEUP_LIMIT) {
            throw new AttendanceMakeupException(
                    "Học viên " + student.getFullName() + " đã dùng đủ " + counted + "/" + MAKEUP_LIMIT
                            + " buổi học bù" + courseScopeText(lesson) + ". Chọn \"Chỉ xin nghỉ\" nếu không cần học bù.");
        }
    }

    /** Kiểm tra chặn khi duyệt đơn xin nghỉ (khi chưa có ca bù gắn sẵn). */
    public void assertCanApproveMakeup(Student student, Lesson lesson) {
        int counted = resolveUsedCourseQuota(student, lesson);
        if (counted >= MAKEUP_LIMIT) {
            throw new AttendanceMakeupException(
                    "Học viên đã dùng đủ " + MAKEUP_LIMIT + "/" + MAKEUP_LIMIT
                            + " buổi học bù trong khóa học, không thể duyệt tạo thêm ca học bù.");
        }
    }

    /** Kiểm tra chặn khi xếp lịch đổi sang lớp học bù (thời điểm bắt đầu tính 1 lượt bù). */
    public void assertCanScheduleMakeup(MakeupRegistration mr) {
        if (mr == null || mr.getStudent() == null || mr.getOriginalLesson() == null) return;
        boolean alreadyCounted = COUNTED_MAKEUP_STATUSES.contains(mr.getStatus())
                || AttendanceMakeupMapper.isMissedAndLocked(mr);
        if (alreadyCounted) return;
        int counted = resolveUsedCourseQuota(mr.getStudent(), mr.getOriginalLesson());
        if (counted >= MAKEUP_LIMIT) {
            throw new AttendanceMakeupException(
                    "Học viên " + mr.getStudent().getFullName() + " đã dùng đủ " + MAKEUP_LIMIT + "/" + MAKEUP_LIMIT
                            + " buổi học bù trong khóa học, không thể xếp thêm sang lớp khác!");
        }
    }

    private String courseScopeText(Lesson lesson) {
        if (lesson.getClassEntity() != null && lesson.getClassEntity().getCourse() != null) {
            return " trong khóa học " + lesson.getClassEntity().getCourse().getName();
        }
        return lesson.getClassEntity() != null && lesson.getClassEntity().getClassName() != null
                ? " trong lớp " + lesson.getClassEntity().getClassName() : "";
    }
}
