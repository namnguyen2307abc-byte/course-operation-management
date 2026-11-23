package com.talent.management.features.attendance_makeup.mapper;

import com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse;
import com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse;
import com.talent.management.features.attendance_makeup.dto.response.MakeupRegistrationResponse;
import com.talent.management.features.attendance_makeup.dto.response.StudentOptionResponse;
import com.talent.management.shared.entity.AbsenceRequest;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.MakeupRegistration;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.MakeupStatus;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ============================================================================
 * MAPPER & HẰNG SỐ QUY ĐỊNH HỌC BÙ (AttendanceMakeupMapper)
 * ============================================================================
 * - {@link #MAX_MAKEUP_RETRIES}: 1 (Mỗi buổi nghỉ chỉ xếp lịch học bù 1 lần).
 * - {@link #MAKEUP_LIMIT_PER_COURSE}: 3 (Mỗi học viên tối đa 3 lượt bù / khóa).
 * - {@link #isMissedAndLocked}: Nhận diện ca vắng mặt tại buổi bù bị khóa quyền.
 * - {@link #isNoSlotClosed}: Nhận diện ca đóng do chưa có lớp phù hợp.
 */
@Component
public class AttendanceMakeupMapper {

    /** Số lần xếp lịch bù tối đa cho một buổi nghỉ. */
    public static final int MAX_MAKEUP_RETRIES = 1;
    /** Số ca bù tối đa của một học viên trong một khóa học. */
    public static final int MAKEUP_LIMIT_PER_COURSE = 3;
    private static final Pattern RETRY_TAG_PATTERN = Pattern.compile("\\[Lần bù:\\s*(\\d+)/(\\d+)(?:\\s*-\\s*KHÓA)?\\]\\s*");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Trích xuất số lần đã xếp lịch học bù từ MakeupRegistration.
     */
    public static int extractRetryCount(MakeupRegistration mr) {
        if (mr == null) return 0;
        if (mr.getNote() != null) {
            Matcher matcher = RETRY_TAG_PATTERN.matcher(mr.getNote());
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (mr.getTargetLesson() != null || mr.getStatus() == MakeupStatus.SCHEDULED
                || mr.getStatus() == MakeupStatus.COMPLETED || mr.getStatus() == MakeupStatus.MISSED) {
            return 1;
        }
        return 0;
    }

    /**
     * Kiểm tra ca học bù bị khóa/mất quyền do học viên vắng mặt ở buổi bù.
     */
    public static boolean isMissedAndLocked(MakeupRegistration mr) {
        if (mr == null) return false;
        if (mr.getNote() != null && mr.getNote().contains("- KHÓA]")) {
            return true;
        }
        return mr.getStatus() == MakeupStatus.MISSED && extractRetryCount(mr) >= MAX_MAKEUP_RETRIES;
    }

    /**
     * Kiểm tra ca học bù bị đóng do chưa có lớp phù hợp.
     */
    public static boolean isNoSlotClosed(MakeupRegistration mr) {
        if (mr == null) return false;
        if (mr.getStatus() == MakeupStatus.NO_SLOT) {
            return true;
        }
        return mr.getStatus() == MakeupStatus.CANCELLED
                && mr.getNote() != null
                && mr.getNote().contains("Chưa có lớp phù hợp");
    }

    /**
     * Kiểm tra ca học bù đã hết lượt xếp lịch hay chưa.
     */
    public static boolean isLimitExceeded(MakeupRegistration mr) {
        return isMissedAndLocked(mr);
    }

    /**
     * Loại bỏ tag [Lần bù: X/Y] cũ khỏi chuỗi note để gắn tag mới sạch sẽ.
     */
    public static String stripRetryTag(String note) {
        if (note == null) return "";
        return RETRY_TAG_PATTERN.matcher(note).replaceFirst("").trim();
    }

    /**
     * Tạo chuỗi note chuẩn kèm tag [Lần bù: X/Y].
     */
    public static String buildRetryNote(int retryCount, boolean locked, String body) {
        String cleanBody = stripRetryTag(body);
        String tag = locked
                ? String.format("[Lần bù: %d/%d - KHÓA] ", retryCount, MAX_MAKEUP_RETRIES)
                : String.format("[Lần bù: %d/%d] ", retryCount, MAX_MAKEUP_RETRIES);
        return tag + cleanBody;
    }

    /**
     * Chuyển đổi AbsenceRequest entity sang AbsenceRequestResponse DTO
     */
    public AbsenceRequestResponse toAbsenceResponse(AbsenceRequest ar, MakeupRegistration mr) {
        if (ar == null) return null;

        Lesson lesson = ar.getLesson();
        Student student = ar.getStudent();

        String timeSlot = null;
        if (lesson != null && lesson.getStartTime() != null && lesson.getEndTime() != null) {
            timeSlot = lesson.getStartTime().format(TIME_FMT) + " - " + lesson.getEndTime().format(TIME_FMT);
        }

        String makeupDateStr = null;
        String makeupTimeSlot = null;
        if (mr != null && mr.getTargetLesson() != null) {
            Lesson target = mr.getTargetLesson();
            if (target.getLessonDate() != null) {
                makeupDateStr = target.getLessonDate().format(DATE_FMT);
            }
            if (target.getStartTime() != null && target.getEndTime() != null) {
                makeupTimeSlot = target.getStartTime().format(TIME_FMT) + " - " + target.getEndTime().format(TIME_FMT);
            }
        }
        User targetTeacher = mr != null ? resolveTeacher(mr.getTargetLesson()) : null;

        User lessonTeacher = resolveTeacher(lesson);

        return AbsenceRequestResponse.builder()
                .id(ar.getId())
                .studentId(student != null ? student.getId() : null)
                .studentName(student != null ? student.getFullName() : null)
                .parentId(student != null && student.getParent() != null ? student.getParent().getId() : null)
                .parentName(student != null && student.getParent() != null ? student.getParent().getFullName() : null)
                .parentPhone(student != null && student.getParent() != null ? student.getParent().getPhone() : null)
                .lessonId(lesson != null ? lesson.getId() : null)
                .sessionNumber(lesson != null ? lesson.getSessionNumber() : null)
                .lessonDate(lesson != null ? lesson.getLessonDate() : null)
                .startTime(lesson != null ? lesson.getStartTime() : null)
                .endTime(lesson != null ? lesson.getEndTime() : null)
                .timeSlot(timeSlot)
                .lessonTitle(lesson != null ? lesson.getTitle() : null)
                .className(lesson != null && lesson.getClassEntity() != null ? lesson.getClassEntity().getClassName() : null)
                .courseName(lesson != null && lesson.getClassEntity() != null && lesson.getClassEntity().getCourse() != null ? lesson.getClassEntity().getCourse().getName() : null)
                .level(lesson != null && lesson.getClassEntity() != null && lesson.getClassEntity().getCourse() != null ? lesson.getClassEntity().getCourse().getLevel() : null)
                .branchName(resolveBranchName(lesson))
                .roomName(resolveRoomName(lesson))
                .teacherName(lessonTeacher != null ? lessonTeacher.getFullName() : null)
                .teacherId(lessonTeacher != null ? lessonTeacher.getId() : null)
                .reason(ar.getReason())
                .needMakeup(ar.wantsMakeup())
                .requestedByName(ar.getRequestedByUser() != null ? ar.getRequestedByUser().getFullName() : null)
                .status(ar.getStatus())
                .reviewNote(ar.getReviewNote())
                .approvedByTeacherName(ar.getApprovedByTeacher() != null ? ar.getApprovedByTeacher().getFullName() : null)
                .reviewedAt(ar.getReviewedAt())
                .createdAt(ar.getCreatedAt())
                .makeupRegistrationId(mr != null ? mr.getId() : null)
                .makeupStatus(mr != null ? mr.getStatus() : null)
                .makeupRetryCount(mr != null ? extractRetryCount(mr) : 0)
                .makeupMaxRetries(MAX_MAKEUP_RETRIES)
                .makeupTargetDate(makeupDateStr)
                .makeupTargetTimeSlot(makeupTimeSlot)
                .makeupTargetClassName(mr != null && mr.getTargetLesson() != null && mr.getTargetLesson().getClassEntity() != null
                        ? mr.getTargetLesson().getClassEntity().getClassName() : null)
                .makeupTargetRoomName(mr != null ? resolveRoomName(mr.getTargetLesson()) : null)
                .makeupTargetTeacherName(targetTeacher != null ? targetTeacher.getFullName() : null)
                .makeupNote(mr != null ? mr.getNote() : null)
                .courseMakeupLimit(MAKEUP_LIMIT_PER_COURSE)
                .build();
    }

    public AbsenceRequestResponse toAbsenceResponse(AbsenceRequest ar, MakeupRegistration mr, int courseMakeupCount, boolean courseMakeupLimitReached) {
        AbsenceRequestResponse resp = toAbsenceResponse(ar, mr);
        if (resp != null) {
            resp.setCourseMakeupCount(courseMakeupCount);
            resp.setCourseMakeupLimit(MAKEUP_LIMIT_PER_COURSE);
            resp.setCourseMakeupLimitReached(courseMakeupLimitReached);
        }
        return resp;
    }

    /**
     * Chuyển đổi MakeupRegistration entity sang MakeupRegistrationResponse DTO
     */
    public MakeupRegistrationResponse toMakeupResponse(MakeupRegistration mr) {
        if (mr == null) return null;

        Student student = mr.getStudent();
        Lesson origLesson = mr.getOriginalLesson();
        Lesson targetLesson = mr.getTargetLesson();

        String origTimeSlot = null;
        if (origLesson != null && origLesson.getStartTime() != null && origLesson.getEndTime() != null) {
            origTimeSlot = origLesson.getStartTime().format(TIME_FMT) + " - " + origLesson.getEndTime().format(TIME_FMT);
        }

        String targetTimeSlot = null;
        if (targetLesson != null && targetLesson.getStartTime() != null && targetLesson.getEndTime() != null) {
            targetTimeSlot = targetLesson.getStartTime().format(TIME_FMT) + " - " + targetLesson.getEndTime().format(TIME_FMT);
        }

        int retryCount = extractRetryCount(mr);
        boolean limitExceeded = isLimitExceeded(mr);

        User origTeacher = resolveTeacher(origLesson);
        User targetTeacher = resolveTeacher(targetLesson);

        return MakeupRegistrationResponse.builder()
                .id(mr.getId())
                .absenceRequestId(mr.getAbsenceRequest() != null ? mr.getAbsenceRequest().getId() : null)
                .absenceReason(mr.getAbsenceRequest() != null ? mr.getAbsenceRequest().getReason() : null)
                .studentId(student != null ? student.getId() : null)
                .studentName(student != null ? student.getFullName() : null)
                .parentName(student != null && student.getParent() != null ? student.getParent().getFullName() : null)
                .parentPhone(student != null && student.getParent() != null ? student.getParent().getPhone() : null)
                .courseName(origLesson != null && origLesson.getClassEntity() != null && origLesson.getClassEntity().getCourse() != null
                        ? origLesson.getClassEntity().getCourse().getName() : null)
                .level(origLesson != null && origLesson.getClassEntity() != null && origLesson.getClassEntity().getCourse() != null
                        ? origLesson.getClassEntity().getCourse().getLevel() : null)
                .branchName(resolveBranchName(origLesson))
                .originalLessonId(origLesson != null ? origLesson.getId() : null)
                .originalSessionNumber(origLesson != null ? origLesson.getSessionNumber() : null)
                .originalLessonDate(origLesson != null ? origLesson.getLessonDate() : null)
                .originalStartTime(origLesson != null ? origLesson.getStartTime() : null)
                .originalEndTime(origLesson != null ? origLesson.getEndTime() : null)
                .originalTimeSlot(origTimeSlot)
                .originalClassName(origLesson != null && origLesson.getClassEntity() != null ? origLesson.getClassEntity().getClassName() : null)
                .originalRoomName(resolveRoomName(origLesson))
                .originalTeacherId(origTeacher != null ? origTeacher.getId() : null)
                .originalTeacherName(origTeacher != null ? origTeacher.getFullName() : null)
                .targetLessonId(targetLesson != null ? targetLesson.getId() : null)
                .targetSessionNumber(targetLesson != null ? targetLesson.getSessionNumber() : null)
                .targetLessonDate(targetLesson != null ? targetLesson.getLessonDate() : null)
                .targetStartTime(targetLesson != null ? targetLesson.getStartTime() : null)
                .targetEndTime(targetLesson != null ? targetLesson.getEndTime() : null)
                .targetTimeSlot(targetTimeSlot)
                .targetClassName(targetLesson != null && targetLesson.getClassEntity() != null ? targetLesson.getClassEntity().getClassName() : null)
                .targetBranchName(resolveBranchName(targetLesson))
                .targetRoomName(resolveRoomName(targetLesson))
                .targetTeacherId(targetTeacher != null ? targetTeacher.getId() : null)
                .targetTeacherName(targetTeacher != null ? targetTeacher.getFullName() : null)
                .status(mr.getStatus())
                .retryCount(retryCount)
                .maxRetries(MAX_MAKEUP_RETRIES)
                .limitExceeded(limitExceeded)
                .courseMakeupLimit(MAKEUP_LIMIT_PER_COURSE)
                .note(mr.getNote())
                .absenceCreatedAt(mr.getAbsenceRequest() != null ? mr.getAbsenceRequest().getCreatedAt() : null)
                .createdAt(mr.getCreatedAt())
                .reviewedAt(mr.getAbsenceRequest() != null && mr.getAbsenceRequest().getReviewedAt() != null
                        ? mr.getAbsenceRequest().getReviewedAt()
                        : mr.getCreatedAt())
                .reviewerName(mr.getAbsenceRequest() != null && mr.getAbsenceRequest().getApprovedByTeacher() != null
                        ? mr.getAbsenceRequest().getApprovedByTeacher().getFullName()
                        : null)
                .build();
    }

    /**
     * Chuyển đổi Student sang StudentOptionResponse DTO
     */
    public StudentOptionResponse toStudentOption(Student s, String currentClassName, int makeupCount, boolean makeupLimitReached) {
        if (s == null) return null;
        return StudentOptionResponse.builder()
                .id(s.getId())
                .fullName(s.getFullName())
                .dateOfBirth(s.getDateOfBirth())
                .gender(s.getGender())
                .schoolName(s.getSchoolName())
                .parentId(s.getParent() != null ? s.getParent().getId() : null)
                .parentName(s.getParent() != null ? s.getParent().getFullName() : null)
                .parentPhone(s.getParent() != null ? s.getParent().getPhone() : null)
                .currentClassName(currentClassName)
                .makeupCount(makeupCount)
                .makeupLimit(MAKEUP_LIMIT_PER_COURSE)
                .makeupLimitReached(makeupLimitReached)
                .build();
    }

    /**
     * Chuyển đổi Lesson sang LessonOptionResponse DTO
     */
    public LessonOptionResponse toLessonOption(Lesson l) {
        return toLessonOption(l, null, true, null);
    }

    public LessonOptionResponse toLessonOption(Lesson l, Integer occupiedSeats, boolean exactSessionMatch, String matchTypeLabel) {
        if (l == null) return null;

        String timeSlot = null;
        if (l.getStartTime() != null && l.getEndTime() != null) {
            timeSlot = l.getStartTime().format(TIME_FMT) + " - " + l.getEndTime().format(TIME_FMT);
        }

        Integer maxStudents = l.getClassEntity() != null ? l.getClassEntity().getMaxStudents() : null;
        Integer available = (maxStudents != null && occupiedSeats != null)
                ? Math.max(0, maxStudents - occupiedSeats) : null;

        User lessonTeacher = resolveTeacher(l);

        return LessonOptionResponse.builder()
                .id(l.getId())
                .sessionNumber(l.getSessionNumber())
                .lessonDate(l.getLessonDate())
                .startTime(l.getStartTime())
                .endTime(l.getEndTime())
                .timeSlot(timeSlot)
                .title(l.getTitle())
                .classId(l.getClassEntity() != null ? l.getClassEntity().getId() : null)
                .className(l.getClassEntity() != null ? l.getClassEntity().getClassName() : null)
                .courseName(l.getClassEntity() != null && l.getClassEntity().getCourse() != null ? l.getClassEntity().getCourse().getName() : null)
                .level(l.getClassEntity() != null && l.getClassEntity().getCourse() != null ? l.getClassEntity().getCourse().getLevel() : null)
                .branchName(resolveBranchName(l))
                .roomName(resolveRoomName(l))
                .teacherName(lessonTeacher != null ? lessonTeacher.getFullName() : null)
                .maxStudents(maxStudents)
                .occupiedSeats(occupiedSeats)
                .availableSeats(available)
                .exactSessionMatch(exactSessionMatch)
                .matchTypeLabel(matchTypeLabel)
                .build();
    }

    private User resolveTeacher(Lesson lesson) {
        if (lesson == null) return null;
        if (lesson.getTeacher() != null) return lesson.getTeacher();
        return lesson.getClassEntity() != null ? lesson.getClassEntity().getTeacher() : null;
    }

    /** Phòng của buổi học; buổi chưa gán phòng lấy phòng của lớp. */
    private String resolveRoomName(Lesson lesson) {
        if (lesson == null) return null;
        if (lesson.getRoom() != null) return lesson.getRoom().getRoomName();
        return lesson.getClassEntity() != null && lesson.getClassEntity().getRoom() != null
                ? lesson.getClassEntity().getRoom().getRoomName() : null;
    }

    private String resolveBranchName(Lesson lesson) {
        return lesson != null && lesson.getClassEntity() != null && lesson.getClassEntity().getBranch() != null
                ? lesson.getClassEntity().getBranch().getName() : null;
    }
}
