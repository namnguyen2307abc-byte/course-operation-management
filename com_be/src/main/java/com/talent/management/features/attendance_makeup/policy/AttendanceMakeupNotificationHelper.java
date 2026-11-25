package com.talent.management.features.attendance_makeup.policy;

import com.talent.management.features.attendance_makeup.dto.request.AbsenceReviewRequest.ReviewDecision;
import com.talent.management.features.notification.service.NotificationService;
import com.talent.management.shared.entity.AbsenceRequest;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.MakeupRegistration;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ============================================================================
 * ĐIỀU PHỐI THÔNG BÁO CHUÔNG (NOTIFICATION HELPER) - PHÂN HỆ NGHỈ HỌC & HỌC BÙ
 * ============================================================================
 * Tập trung toàn bộ nội dung và đối tượng nhận thông báo qua từng sự kiện:
 *   - Tạo đơn mới / Hủy đơn đang chờ
 *   - Duyệt đơn (kèm tạo ca bù hoặc chỉ xin nghỉ) / Từ chối đơn
 *   - Xếp lịch học bù / Hủy ca bù / Báo chưa có lớp phù hợp / Hoàn thành học bù
 */
@Component
@RequiredArgsConstructor
public class AttendanceMakeupNotificationHelper {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_VN_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AbsenceMakeupValidator validator;
    private final MakeupQuotaPolicy quotaPolicy;

    @Autowired(required = false)
    private NotificationService notificationService;

    public void notifyAbsenceCreated(AbsenceRequest saved, Student student, Lesson lesson, User currentUser, boolean needMakeup) {
        if (notificationService == null) return;

        User lessonTeacher = validator.resolvePrimaryTeacher(lesson);
        String className = lesson.getClassEntity() != null ? lesson.getClassEntity().getClassName() : "";
        String dateStr = formatLessonDateVn(lesson);
        String absenceUrl = "/pages/absence-makeup.html?tab=ABSENCE&absenceId=" + saved.getId();
        int quotaUsed = needMakeup ? quotaPolicy.resolveUsedCourseQuota(student, lesson) : 0;
        String makeupText = needMakeup
                ? String.format(" (Nghỉ kèm học bù — Đã xếp bù khóa: %d/%d)", quotaUsed, MakeupQuotaPolicy.MAKEUP_LIMIT)
                : " (Chỉ xin nghỉ, không học bù)";
        String actorText = validator.actorRoleLabel(currentUser);

        if (lessonTeacher != null && !lessonTeacher.getId().equals(currentUser.getId())) {
            notificationService.sendNotification(
                    lessonTeacher, currentUser,
                    "Đơn xin nghỉ mới: " + student.getFullName(),
                    student.getFullName() + " (" + className + ") xin nghỉ ngày " + dateStr + ". Lý do: " + saved.getReason() + makeupText,
                    "ABSENCE_CREATED", saved.getId(), absenceUrl);
        }
        notificationService.sendToRoles(
                List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER), currentUser,
                "Đơn xin nghỉ mới chờ duyệt",
                actorText + " gửi đơn xin nghỉ cho " + student.getFullName() + " (" + className + ", ngày " + dateStr + ")." + makeupText,
                "ABSENCE_CREATED", saved.getId(), absenceUrl);
    }

    public void notifyAbsenceCancelled(Long absenceId, Student student, Lesson lesson, User currentUser) {
        if (notificationService == null) return;

        String studentName = student != null ? student.getFullName() : "Học viên";
        String className = lesson != null && lesson.getClassEntity() != null ? lesson.getClassEntity().getClassName() : "";
        String dateStr = formatLessonDateVn(lesson);
        User primaryTeacher = validator.resolvePrimaryTeacher(lesson);
        String absenceListUrl = "/pages/absence-makeup.html?tab=ABSENCE";
        String actorText = validator.actorRoleLabel(currentUser);

        if (primaryTeacher != null && !primaryTeacher.getId().equals(currentUser.getId())) {
            notificationService.sendNotification(
                    primaryTeacher, currentUser,
                    actorText + " đã hủy đơn xin nghỉ",
                    "Đơn xin nghỉ ngày " + dateStr + " của " + studentName + " (" + className + ") đã được " + actorText.toLowerCase() + " hủy.",
                    "ABSENCE_CANCELLED", absenceId, absenceListUrl);
        }
        notificationService.sendToRoles(
                List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER), currentUser,
                "Đơn xin nghỉ #" + absenceId + " đã hủy",
                actorText + " đã hủy đơn xin nghỉ ngày " + dateStr + " của " + studentName + " (" + className + ").",
                "ABSENCE_CANCELLED", absenceId, absenceListUrl);
    }

    public void notifyAbsenceReviewed(AbsenceRequest saved, MakeupRegistration mr, User currentUser, ReviewDecision decision, boolean needMakeup) {
        if (notificationService == null || saved.getStudent() == null) return;

        User parentUser = saved.getStudent().getParent();
        String studentName = saved.getStudent().getFullName();
        String dateStr = formatLessonDateVn(saved.getLesson());
        String className = saved.getLesson() != null && saved.getLesson().getClassEntity() != null
                ? saved.getLesson().getClassEntity().getClassName() : "";

        String absenceUrl = "/pages/absence-makeup.html?tab=ABSENCE&absenceId=" + saved.getId();
        Long makeupId = mr != null ? mr.getId() : saved.getId();
        String makeupUrl = mr != null ? "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + mr.getId() : absenceUrl;

        if (decision == ReviewDecision.APPROVED) {
            if (needMakeup) {
                int quotaUsed = quotaPolicy.resolveUsedCourseQuota(saved.getStudent(), saved.getLesson());
                if (parentUser != null) {
                    notificationService.sendNotification(
                            parentUser, currentUser,
                            "Đơn xin nghỉ đã được phê duyệt: " + studentName,
                            String.format("Đơn xin nghỉ buổi ngày %s của bé %s đã được duyệt (Đã xếp bù khóa: %d/%d). Bộ phận học vụ sẽ tìm ca phù hợp để xếp lịch học bù.",
                                    dateStr, studentName, quotaUsed, MakeupQuotaPolicy.MAKEUP_LIMIT),
                            "ABSENCE_APPROVED", saved.getId(), absenceUrl);
                }
                notificationService.sendToRoles(
                        List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER), currentUser,
                        "Cần xếp lịch học bù mới: " + studentName,
                        String.format("Đơn nghỉ #%d của học viên %s (lớp %s, ngày %s) đã được duyệt (Đã xếp bù khóa: %d/%d), vui lòng tìm ca phù hợp để xếp học bù.",
                                saved.getId(), studentName, className, dateStr, quotaUsed, MakeupQuotaPolicy.MAKEUP_LIMIT),
                        "MAKEUP_PENDING", makeupId, makeupUrl);
            } else if (parentUser != null) {
                notificationService.sendNotification(
                        parentUser, currentUser,
                        "Đơn xin nghỉ phép đã được phê duyệt",
                        "Đơn xin nghỉ phép buổi ngày " + dateStr + " của bé " + studentName + " đã được duyệt (chỉ xin nghỉ, không đăng ký học bù).",
                        "ABSENCE_APPROVED", saved.getId(), absenceUrl);
            }
        } else if (decision == ReviewDecision.REJECTED && parentUser != null) {
            notificationService.sendNotification(
                    parentUser, currentUser,
                    "Đơn xin nghỉ bị từ chối",
                    "Đơn xin nghỉ buổi ngày " + dateStr + " của bé " + studentName + " đã bị từ chối. Lý do: "
                            + (saved.getReviewNote() != null ? saved.getReviewNote() : ""),
                    "ABSENCE_REJECTED", saved.getId(), absenceUrl);
        }

        // Nếu Nhân sự duyệt thay Giáo viên phụ trách -> báo cho Giáo viên nắm lịch lớp
        User primaryTeacher = validator.resolvePrimaryTeacher(saved.getLesson());
        if (currentUser.getRole() != Role.TEACHER
                && primaryTeacher != null
                && !primaryTeacher.getId().equals(currentUser.getId())) {
            String statusAction = decision == ReviewDecision.APPROVED
                    ? (needMakeup ? "đã duyệt (chờ xếp bù)" : "đã duyệt (chỉ xin nghỉ)")
                    : "đã từ chối";
            notificationService.sendNotification(
                    primaryTeacher, currentUser,
                    "Đơn xin nghỉ lớp phụ trách đã được xử lý thay",
                    String.format("%s đã thay mặt bạn %s đơn nghỉ #%d của học viên %s (lớp %s, ngày %s).",
                            currentUser.getFullName(), statusAction, saved.getId(), studentName, className, dateStr),
                    decision == ReviewDecision.APPROVED ? "ABSENCE_APPROVED" : "ABSENCE_REJECTED",
                    saved.getId(), absenceUrl);
        }
    }

    public void notifyMakeupScheduled(MakeupRegistration saved, Lesson targetLesson, User currentUser) {
        if (notificationService == null) return;

        Lesson origLesson = saved.getOriginalLesson();
        Student student = saved.getStudent();
        String studentName = (student != null) ? student.getFullName() : "Học viên";
        String targetDateStr = formatLessonDateVn(targetLesson);
        String targetTimeStr = (targetLesson.getStartTime() != null && targetLesson.getEndTime() != null)
                ? targetLesson.getStartTime().format(TIME_FMT) + " - " + targetLesson.getEndTime().format(TIME_FMT) : "";
        String targetClassName = (targetLesson.getClassEntity() != null)
                ? targetLesson.getClassEntity().getClassName()
                : (targetLesson.getTitle() != null ? targetLesson.getTitle() : "Lớp học bù");
        User resolvedTargetTeacher = validator.resolvePrimaryTeacher(targetLesson);
        String targetTeacherName = (resolvedTargetTeacher != null)
                ? resolvedTargetTeacher.getFullName() : "Giáo viên trung tâm";
        String targetRoomLabel = formatRoomLabel(validator.resolveRoomName(targetLesson));
        String origClassName = (origLesson != null && origLesson.getClassEntity() != null)
                ? origLesson.getClassEntity().getClassName() : "lớp phụ trách";
        int courseUsed = Math.max(1, quotaPolicy.resolveUsedCourseQuota(student, origLesson));
        String quotaTag = String.format("Bù khóa %d/%d", courseUsed, MakeupQuotaPolicy.MAKEUP_LIMIT);

        String makeupUrl = "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + saved.getId();
        User resolvedOrigTeacher = validator.resolvePrimaryTeacher(origLesson);
        boolean sameTeacher = resolvedOrigTeacher != null && resolvedTargetTeacher != null
                && resolvedOrigTeacher.getId().equals(resolvedTargetTeacher.getId());

        if (sameTeacher) {
            if (!resolvedTargetTeacher.getId().equals(currentUser.getId())) {
                String title = "Học viên chuyển lịch học bù: " + studentName + " (" + quotaTag + ")";
                String message = String.format(
                        "Học viên %s (lớp %s) đã được xếp học bù sang lớp %s của bạn vào ngày %s (%s, %s).",
                        studentName, origClassName, targetClassName, targetDateStr, targetTimeStr, targetRoomLabel);
                notificationService.sendNotification(resolvedTargetTeacher, currentUser, title, message,
                        "MAKEUP_COORDINATED_IN", saved.getId(), makeupUrl);
            }
        } else {
            if (resolvedOrigTeacher != null && !resolvedOrigTeacher.getId().equals(currentUser.getId())) {
                String title = "Học viên điều phối học bù: " + studentName + " (" + quotaTag + ")";
                String message = String.format(
                        "Học viên %s (lớp %s) đã được xếp lịch học bù sang lớp %s vào ngày %s (%s, %s, GV: %s).",
                        studentName, origClassName, targetClassName, targetDateStr, targetTimeStr,
                        targetRoomLabel, targetTeacherName);
                notificationService.sendNotification(resolvedOrigTeacher, currentUser, title, message,
                        "MAKEUP_COORDINATED_OUT", saved.getId(), makeupUrl);
            }

            if (resolvedTargetTeacher != null && !resolvedTargetTeacher.getId().equals(currentUser.getId())) {
                String title = "Có học viên học bù vào lớp bạn: " + studentName + " (" + quotaTag + ")";
                String message = String.format(
                        "Buổi học ngày %s (%s, lớp %s, %s) của bạn sẽ có học viên %s (từ lớp %s) đến học bù.",
                        targetDateStr, targetTimeStr, targetClassName, targetRoomLabel, studentName, origClassName);
                notificationService.sendNotification(resolvedTargetTeacher, currentUser, title, message,
                        "MAKEUP_COORDINATED_IN", saved.getId(), makeupUrl);
            }
        }

        if (student != null && student.getParent() != null) {
            String title = "Đã xếp lịch học bù cho bé " + studentName;
            String message = String.format(
                    "Bé %s đã được xếp lịch học bù tại lớp %s vào ngày %s (%s, %s, GV: %s). Quyền học bù khóa đã dùng: %d/%d lượt.",
                    studentName, targetClassName, targetDateStr, targetTimeStr,
                    targetRoomLabel, targetTeacherName, courseUsed, MakeupQuotaPolicy.MAKEUP_LIMIT);
            notificationService.sendNotification(student.getParent(), currentUser, title, message,
                    "MAKEUP_SCHEDULED", saved.getId(), makeupUrl);
        }
    }

    public void notifyMakeupCancelled(MakeupRegistration saved, String cancelNote, User currentUser) {
        if (notificationService == null || saved.getStudent() == null) return;

        Student stu = saved.getStudent();
        String stuName = stu.getFullName();
        String makeupUrl = "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + saved.getId();
        if (stu.getParent() != null) {
            notificationService.sendNotification(
                    stu.getParent(), currentUser,
                    "Hủy lịch học bù: " + stuName,
                    "Ca học bù của bé " + stuName + " đã được hủy. Ghi chú: " + cancelNote,
                    "MAKEUP_CANCELLED", saved.getId(), makeupUrl);
        }
        User origTeacher = validator.resolvePrimaryTeacher(saved.getOriginalLesson());
        if (origTeacher != null && !origTeacher.getId().equals(currentUser.getId())) {
            notificationService.sendNotification(
                    origTeacher, currentUser,
                    "Hủy lịch học bù của " + stuName,
                    "Ca học bù của học viên " + stuName + " đã bị hủy. Ghi chú: " + cancelNote,
                    "MAKEUP_CANCELLED", saved.getId(), makeupUrl);
        }
        User targetTeacher = validator.resolvePrimaryTeacher(saved.getTargetLesson());
        if (targetTeacher != null && !targetTeacher.getId().equals(currentUser.getId())
                && (origTeacher == null || !targetTeacher.getId().equals(origTeacher.getId()))) {
            notificationService.sendNotification(
                    targetTeacher, currentUser,
                    "Hủy học viên bù vào lớp: " + stuName,
                    "Học viên " + stuName + " không còn tham gia học bù tại lớp bạn (" + cancelNote + ").",
                    "MAKEUP_CANCELLED", saved.getId(), makeupUrl);
        }
    }

    public void notifyNoSlotAvailable(MakeupRegistration saved, String message, User currentUser) {
        if (notificationService == null || saved.getStudent() == null) return;

        String stuName = saved.getStudent().getFullName();
        String makeupUrl = "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + saved.getId();
        if (saved.getStudent().getParent() != null) {
            notificationService.sendNotification(
                    saved.getStudent().getParent(), currentUser,
                    "Hủy ca học bù (Chưa có lớp phù hợp): " + stuName,
                    "Hiện tại trung tâm chưa có ca học bù phù hợp cho bé " + stuName + " nên đã hủy ca bù này. Ghi chú: " + message,
                    "MAKEUP_CANCELLED", saved.getId(), makeupUrl);
        }
        User origTeacher = validator.resolvePrimaryTeacher(saved.getOriginalLesson());
        if (origTeacher != null && !origTeacher.getId().equals(currentUser.getId())) {
            notificationService.sendNotification(
                    origTeacher, currentUser,
                    "Đã hủy ca bù của " + stuName + " (Chưa có lớp phù hợp)",
                    "Giáo vụ đã thông báo tới phụ huynh của " + stuName + " và hủy ca học bù do chưa có lớp phù hợp: " + message,
                    "MAKEUP_CANCELLED", saved.getId(), makeupUrl);
        }
    }

    public void notifyMakeupCompleted(MakeupRegistration saved, User currentUser) {
        if (notificationService == null || saved.getStudent() == null) return;

        String stuName = saved.getStudent().getFullName();
        Lesson target = saved.getTargetLesson();
        String targetCls = target != null && target.getClassEntity() != null ? target.getClassEntity().getClassName() : "lớp học bù";
        String targetDate = formatLessonDateVn(target);
        String makeupUrl = "/pages/absence-makeup.html?tab=MAKEUP&makeupId=" + saved.getId();
        if (saved.getStudent().getParent() != null) {
            notificationService.sendNotification(
                    saved.getStudent().getParent(), currentUser,
                    "Đã hoàn thành buổi học bù: " + stuName,
                    "Bé " + stuName + " đã tham gia đầy đủ buổi học bù tại " + targetCls + " ngày " + targetDate + ".",
                    "MAKEUP_COMPLETED", saved.getId(), makeupUrl);
        }
        User origTeacher = validator.resolvePrimaryTeacher(saved.getOriginalLesson());
        if (origTeacher != null && !origTeacher.getId().equals(currentUser.getId())) {
            notificationService.sendNotification(
                    origTeacher, currentUser,
                    "Học viên đã hoàn thành học bù: " + stuName,
                    "Học viên " + stuName + " đã hoàn thành buổi học bù tại " + targetCls + " ngày " + targetDate + ".",
                    "MAKEUP_COMPLETED", saved.getId(), makeupUrl);
        }
        User targetTeacher = validator.resolvePrimaryTeacher(target);
        if (targetTeacher != null && !targetTeacher.getId().equals(currentUser.getId())
                && (origTeacher == null || !targetTeacher.getId().equals(origTeacher.getId()))) {
            notificationService.sendNotification(
                    targetTeacher, currentUser,
                    "Xác nhận hoàn thành học bù: " + stuName,
                    "Học viên " + stuName + " đã được ghi nhận hoàn thành buổi học bù ngày " + targetDate + ".",
                    "MAKEUP_COMPLETED", saved.getId(), makeupUrl);
        }
    }

    private String formatLessonDateVn(Lesson lesson) {
        return (lesson != null && lesson.getLessonDate() != null)
                ? lesson.getLessonDate().format(DATE_VN_FMT) : "";
    }

    private String formatRoomLabel(String rawRoom) {
        if (rawRoom == null || rawRoom.isBlank()) return "Phòng học";
        String trimmed = rawRoom.trim();
        return trimmed.toLowerCase().startsWith("phòng") ? trimmed : "Phòng " + trimmed;
    }
}
