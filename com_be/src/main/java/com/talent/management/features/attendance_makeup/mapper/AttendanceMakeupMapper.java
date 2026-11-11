package com.talent.management.features.attendance_makeup.mapper;

import com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse;
import com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse;
import com.talent.management.features.attendance_makeup.dto.response.MakeupRegistrationResponse;
import com.talent.management.features.attendance_makeup.dto.response.StudentOptionResponse;
import com.talent.management.shared.entity.AbsenceRequest;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.MakeupRegistration;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.enums.MakeupStatus;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AttendanceMakeupMapper {

    public static final int MAX_MAKEUP_RETRIES = 2;
    private static final Pattern RETRY_TAG_PATTERN = Pattern.compile("\\[Lần bù:\\s*(\\d+)/(\\d+)(?:\\s*-\\s*KHÓA)?\\]\\s*");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Trích xuất số lần đã xếp lịch học bù từ MakeupRegistration (tối đa 2 lần).
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
     * Kiểm tra ca học bù đã vượt quá giới hạn số lần bù (2 lần) hay chưa.
     */
    public static boolean isLimitExceeded(MakeupRegistration mr) {
        if (mr == null) return false;
        if (mr.getNote() != null && mr.getNote().contains("- KHÓA]")) {
            return true;
        }
        int retry = extractRetryCount(mr);
        return retry >= MAX_MAKEUP_RETRIES && (mr.getStatus() == MakeupStatus.MISSED || mr.getStatus() == MakeupStatus.CANCELLED);
    }

    /**
     * Loại bỏ tag [Lần bù: X/Y] cũ khỏi chuỗi note để gắn tag mới sạch sẽ.
     */
    public static String stripRetryTag(String note) {
        if (note == null) return "";
        return RETRY_TAG_PATTERN.matcher(note).replaceFirst("").trim();
    }

    /**
     * Tạo chuỗi note chuẩn kèm tag [Lần bù: X/2].
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
        if (mr != null && mr.getTargetLesson() != null && mr.getTargetLesson().getLessonDate() != null) {
            makeupDateStr = mr.getTargetLesson().getLessonDate().format(DATE_FMT);
        }

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
                .roomName(lesson != null && lesson.getRoom() != null ? lesson.getRoom().getRoomName() : null)
                .teacherName(lesson != null && lesson.getTeacher() != null ? lesson.getTeacher().getFullName() : null)
                .teacherId(lesson != null && lesson.getTeacher() != null ? lesson.getTeacher().getId() : null)
                .reason(ar.getReason())
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
                .makeupTargetClassName(mr != null && mr.getTargetLesson() != null && mr.getTargetLesson().getClassEntity() != null
                        ? mr.getTargetLesson().getClassEntity().getClassName() : null)
                .makeupNote(mr != null ? mr.getNote() : null)
                .build();
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

        return MakeupRegistrationResponse.builder()
                .id(mr.getId())
                .absenceRequestId(mr.getAbsenceRequest() != null ? mr.getAbsenceRequest().getId() : null)
                .absenceReason(mr.getAbsenceRequest() != null ? mr.getAbsenceRequest().getReason() : null)
                .studentId(student != null ? student.getId() : null)
                .studentName(student != null ? student.getFullName() : null)
                .parentName(student != null && student.getParent() != null ? student.getParent().getFullName() : null)
                .originalLessonId(origLesson != null ? origLesson.getId() : null)
                .originalSessionNumber(origLesson != null ? origLesson.getSessionNumber() : null)
                .originalLessonDate(origLesson != null ? origLesson.getLessonDate() : null)
                .originalStartTime(origLesson != null ? origLesson.getStartTime() : null)
                .originalEndTime(origLesson != null ? origLesson.getEndTime() : null)
                .originalTimeSlot(origTimeSlot)
                .originalClassName(origLesson != null && origLesson.getClassEntity() != null ? origLesson.getClassEntity().getClassName() : null)
                .originalTeacherId(origLesson != null && origLesson.getTeacher() != null ? origLesson.getTeacher().getId() : null)
                .originalTeacherName(origLesson != null && origLesson.getTeacher() != null ? origLesson.getTeacher().getFullName() : null)
                .targetLessonId(targetLesson != null ? targetLesson.getId() : null)
                .targetSessionNumber(targetLesson != null ? targetLesson.getSessionNumber() : null)
                .targetLessonDate(targetLesson != null ? targetLesson.getLessonDate() : null)
                .targetStartTime(targetLesson != null ? targetLesson.getStartTime() : null)
                .targetEndTime(targetLesson != null ? targetLesson.getEndTime() : null)
                .targetTimeSlot(targetTimeSlot)
                .targetClassName(targetLesson != null && targetLesson.getClassEntity() != null ? targetLesson.getClassEntity().getClassName() : null)
                .targetRoomName(targetLesson != null && targetLesson.getRoom() != null ? targetLesson.getRoom().getRoomName() : null)
                .targetTeacherId(targetLesson != null && targetLesson.getTeacher() != null ? targetLesson.getTeacher().getId() : null)
                .targetTeacherName(targetLesson != null && targetLesson.getTeacher() != null ? targetLesson.getTeacher().getFullName() : null)
                .status(mr.getStatus())
                .retryCount(retryCount)
                .maxRetries(MAX_MAKEUP_RETRIES)
                .limitExceeded(limitExceeded)
                .note(mr.getNote())
                .createdAt(mr.getCreatedAt())
                .build();
    }

    /**
     * Chuyển đổi Student sang StudentOptionResponse DTO
     */
    public StudentOptionResponse toStudentOption(Student s, String currentClassName) {
        if (s == null) return null;
        return StudentOptionResponse.builder()
                .id(s.getId())
                .fullName(s.getFullName())
                .gender(s.getGender())
                .schoolName(s.getSchoolName())
                .parentId(s.getParent() != null ? s.getParent().getId() : null)
                .parentName(s.getParent() != null ? s.getParent().getFullName() : null)
                .currentClassName(currentClassName)
                .build();
    }

    /**
     * Chuyển đổi Lesson sang LessonOptionResponse DTO
     */
    public LessonOptionResponse toLessonOption(Lesson l) {
        return toLessonOption(l, null, true, null);
    }

    /**
     * Chuyển đổi Lesson sang LessonOptionResponse DTO kèm thông tin level và số chỗ còn trống.
     */
    public LessonOptionResponse toLessonOption(Lesson l, Integer occupiedSeats) {
        return toLessonOption(l, occupiedSeats, true, null);
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
                .roomName(l.getRoom() != null ? l.getRoom().getRoomName() : null)
                .teacherName(l.getTeacher() != null ? l.getTeacher().getFullName() : null)
                .maxStudents(maxStudents)
                .occupiedSeats(occupiedSeats)
                .availableSeats(available)
                .exactSessionMatch(exactSessionMatch)
                .matchTypeLabel(matchTypeLabel)
                .build();
    }
}
