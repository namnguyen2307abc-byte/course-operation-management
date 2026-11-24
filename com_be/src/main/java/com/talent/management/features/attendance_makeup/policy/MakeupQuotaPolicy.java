package com.talent.management.features.attendance_makeup.policy;

import com.talent.management.features.attendance_makeup.exception.AttendanceMakeupException;
import com.talent.management.features.attendance_makeup.mapper.AttendanceMakeupMapper;
import com.talent.management.features.attendance_makeup.repository.AbsenceRequestRepository;
import com.talent.management.features.attendance_makeup.repository.MakeupRegistrationRepository;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.enums.MakeupStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ============================================================================
 * CHÍNH SÁCH HẠN MỨC HỌC BÙ (QUOTA POLICY: TỐI ĐA 3 LƯỢT / 1 KHÓA HỌC)
 * ============================================================================
 * Đóng gói tập trung toàn bộ quy tắc tính và kiểm tra giới hạn quyền học bù:
 *   1. Hằng số giới hạn khóa: MAKEUP_LIMIT = 3 lượt/khóa học.
 *   2. Trạng thái tính lượt đã dùng: SCHEDULED, COMPLETED, MISSED + ca CANCELLED có tag "- KHÓA]" (vắng buổi bù).
 *   3. Trạng thái giữ chỗ/đang chờ: bổ sung thêm PENDING + đơn xin nghỉ PENDING có đăng ký bù (needMakeup = true).
 *   4. Hai điểm chốt chặn:
 *        - {@link #assertCanCreateMakeupAbsence}: chặn khi gửi đơn xin nghỉ kèm học bù.
 *        - {@link #assertCanApproveMakeup}: chặn lần 2 lúc giáo viên/nhân sự duyệt tạo ca bù.
 */
@Component
@RequiredArgsConstructor
public class MakeupQuotaPolicy {

    /** Số lượt học bù tối đa của một học viên trong một khóa học. */
    public static final int MAKEUP_LIMIT = AttendanceMakeupMapper.MAKEUP_LIMIT_PER_COURSE;

    /** Ca bù đã xếp lịch: đã được xếp dù tham gia hay vắng buổi bù đều tính 1 lượt bù. */
    public static final List<MakeupStatus> COUNTED_MAKEUP_STATUSES =
            List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED);

    /** Lượt bù đang dùng hoặc đang giữ chỗ: ca đã xếp + ca chờ xếp lịch (PENDING). */
    public static final List<MakeupStatus> QUOTA_MAKEUP_STATUSES =
            List.of(MakeupStatus.SCHEDULED, MakeupStatus.COMPLETED, MakeupStatus.MISSED, MakeupStatus.PENDING);

    /** Tag ghi chú nhận diện ca bù bị khóa do học viên vắng mặt ở buổi bù (vẫn tính vào hạn mức 3/3). */
    public static final String LOCK_NOTE_PATTERN = "%- KHÓA]%";

    private final AbsenceRequestRepository absenceRequestRepository;
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

    /** Đếm số ca bù của học viên trong khóa học (hoặc lớp), bao gồm cả ca bị khóa do vắng buổi bù. */
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

    /** Đếm tổng ca bù giữ/đã dùng trên toàn hồ sơ học viên (dùng hiển thị danh sách học viên). */
    public int countTotalMakeupsForStudent(Long studentId) {
        if (studentId == null) return 0;
        return (int) makeupRegistrationRepository.countQuotaByStudent(studentId, QUOTA_MAKEUP_STATUSES, LOCK_NOTE_PATTERN);
    }

    /** Đếm đơn xin nghỉ đang chờ duyệt (PENDING) có đăng ký học bù trong cùng khóa/lớp. */
    public int countPendingMakeupAbsences(Student student, Lesson lesson) {
        if (student == null) return 0;
        Long courseId = courseIdOf(lesson);
        Long classId = classIdOf(lesson);
        if (courseId != null) {
            return (int) absenceRequestRepository.countPendingMakeupByStudentAndCourse(student.getId(), courseId);
        }
        return classId != null
                ? (int) absenceRequestRepository.countPendingMakeupByStudentAndClass(student.getId(), classId)
                : 0;
    }

    /**
     * Số lượt bù khóa đang sử dụng/giữ thống nhất cho mọi màn (Tab 1, Tab 2, Tab 3, Thông báo):
     * ca đã xếp/học/khóa + ca PENDING + đơn xin nghỉ PENDING có đăng ký bù.
     */
    public int resolveUsedCourseQuota(Student student, Lesson lesson) {
        return countMakeups(student, lesson, QUOTA_MAKEUP_STATUSES) + countPendingMakeupAbsences(student, lesson);
    }

    /** Kiểm tra chặn khi tạo mới đơn xin nghỉ kèm đăng ký học bù. */
    public void assertCanCreateMakeupAbsence(Student student, Lesson lesson) {
        int counted = countMakeups(student, lesson, COUNTED_MAKEUP_STATUSES);
        if (counted >= MAKEUP_LIMIT) {
            throw new AttendanceMakeupException(
                    "Học viên " + student.getFullName() + " đã từng học bù " + counted + " lần" + courseScopeText(lesson)
                            + " (đạt giới hạn tối đa " + MAKEUP_LIMIT + " lần học bù trong 1 khóa học). "
                            + "Chọn \"Chỉ xin nghỉ\" nếu không cần học bù.");
        }
        int used = resolveUsedCourseQuota(student, lesson);
        if (used >= MAKEUP_LIMIT) {
            throw new AttendanceMakeupException(
                    "Học viên " + student.getFullName() + " hiện đã có " + counted + " ca học bù đã xếp lịch và "
                            + (used - counted) + " ca/đơn đang chờ xử lý/xếp lịch" + courseScopeText(lesson)
                            + " (tối đa " + MAKEUP_LIMIT + " lần học bù trong 1 khóa học). "
                            + "Chọn \"Chỉ xin nghỉ\" nếu không cần học bù.");
        }
    }

    /** Kiểm tra chặn khi duyệt đơn xin nghỉ (khi chưa có ca bù gắn sẵn). */
    public void assertCanApproveMakeup(Student student, Lesson lesson) {
        int quotaCount = countMakeups(student, lesson, QUOTA_MAKEUP_STATUSES);
        if (quotaCount >= MAKEUP_LIMIT) {
            throw new AttendanceMakeupException(
                    "Học viên đã sử dụng đủ " + MAKEUP_LIMIT + "/" + MAKEUP_LIMIT
                            + " lượt học bù trong khóa học, không thể duyệt tạo thêm ca học bù.");
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
