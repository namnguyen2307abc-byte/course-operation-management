package com.talent.management.features.attendance_makeup.mapper;

import com.talent.management.features.attendance_makeup.dto.response.AbsenceRequestResponse;
import com.talent.management.features.attendance_makeup.dto.response.LessonOptionResponse;
import com.talent.management.features.attendance_makeup.dto.response.MakeupRegistrationResponse;
import com.talent.management.features.attendance_makeup.dto.response.StudentOptionResponse;
import com.talent.management.shared.entity.AbsenceRequest;
import com.talent.management.shared.entity.Lesson;
import com.talent.management.shared.entity.MakeupRegistration;
import com.talent.management.shared.entity.Student;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class AttendanceMakeupMapper {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
                .reason(ar.getReason())
                .status(ar.getStatus())
                .reviewNote(ar.getReviewNote())
                .approvedByTeacherName(ar.getApprovedByTeacher() != null ? ar.getApprovedByTeacher().getFullName() : null)
                .createdAt(ar.getCreatedAt())
                .makeupRegistrationId(mr != null ? mr.getId() : null)
                .makeupStatus(mr != null ? mr.getStatus() : null)
                .makeupTargetDate(makeupDateStr)
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
                .targetLessonId(targetLesson != null ? targetLesson.getId() : null)
                .targetSessionNumber(targetLesson != null ? targetLesson.getSessionNumber() : null)
                .targetLessonDate(targetLesson != null ? targetLesson.getLessonDate() : null)
                .targetStartTime(targetLesson != null ? targetLesson.getStartTime() : null)
                .targetEndTime(targetLesson != null ? targetLesson.getEndTime() : null)
                .targetTimeSlot(targetTimeSlot)
                .targetClassName(targetLesson != null && targetLesson.getClassEntity() != null ? targetLesson.getClassEntity().getClassName() : null)
                .targetRoomName(targetLesson != null && targetLesson.getRoom() != null ? targetLesson.getRoom().getRoomName() : null)
                .targetTeacherName(targetLesson != null && targetLesson.getTeacher() != null ? targetLesson.getTeacher().getFullName() : null)
                .status(mr.getStatus())
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
        if (l == null) return null;

        String timeSlot = null;
        if (l.getStartTime() != null && l.getEndTime() != null) {
            timeSlot = l.getStartTime().format(TIME_FMT) + " - " + l.getEndTime().format(TIME_FMT);
        }

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
                .roomName(l.getRoom() != null ? l.getRoom().getRoomName() : null)
                .teacherName(l.getTeacher() != null ? l.getTeacher().getFullName() : null)
                .build();
    }
}
