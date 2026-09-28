package com.talent.management.features.course_enrollment.mapper;

import com.talent.management.features.course_enrollment.dto.response.ChildResponse;
import com.talent.management.features.course_enrollment.dto.response.ClassResponse;
import com.talent.management.features.course_enrollment.dto.response.CourseResponse;
import com.talent.management.features.course_enrollment.dto.response.EnrollmentRequestResponse;
import com.talent.management.features.course_enrollment.entity.EnrollmentRequest;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Student;
import org.springframework.stereotype.Component;

@Component
public class CourseEnrollmentMapper {

    public ChildResponse toChildResponse(Student student) {
        return new ChildResponse(
                student.getId(),
                student.getFullName(),
                student.getDateOfBirth(),
                student.getGender(),
                student.getSchoolName()
        );
    }

    public CourseResponse toCourseResponse(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getProgram().getName(),
                course.getCode(),
                course.getName(),
                course.getLevel(),
                course.getDurationWeeks(),
                course.getTotalSessions(),
                course.getTuitionFee(),
                course.getDescription()
        );
    }

    public ClassResponse toClassResponse(ClassEntity classEntity) {
        int currentStudents = classEntity.getCurrentStudents() == null ? 0 : classEntity.getCurrentStudents();
        int maxStudents = classEntity.getMaxStudents() == null ? 0 : classEntity.getMaxStudents();
        return new ClassResponse(
                classEntity.getId(),
                classEntity.getCourse().getId(),
                classEntity.getCourse().getName(),
                classEntity.getClassCode(),
                classEntity.getClassName(),
                classEntity.getClassType(),
                classEntity.getBranch().getId(),
                classEntity.getBranch().getName(),
                classEntity.getRoom() == null ? null : classEntity.getRoom().getId(),
                classEntity.getRoom() == null ? null : classEntity.getRoom().getRoomName(),
                classEntity.getTeacher() == null ? null : classEntity.getTeacher().getFullName(),
                maxStudents,
                currentStudents,
                Math.max(maxStudents - currentStudents, 0),
                classEntity.getStartDate(),
                classEntity.getEndDate(),
                classEntity.getScheduleDescription(),
                classEntity.getStatus()
        );
    }

    public EnrollmentRequestResponse toRequestResponse(EnrollmentRequest request) {
        return new EnrollmentRequestResponse(
                request.getId(),
                request.getStudent().getId(),
                request.getStudent().getFullName(),
                request.getClassEntity().getId(),
                request.getClassEntity().getClassCode(),
                request.getClassEntity().getClassName(),
                request.getClassEntity().getCourse().getName(),
                request.getStatus(),
                request.getNote(),
                request.getReviewNote(),
                request.getCreatedAt(),
                request.getReviewedAt(),
                request.getEnrollment() == null ? null : request.getEnrollment().getId(),
                request.getRequestedBy().getFullName(),
                request.getReviewedBy() == null ? null : request.getReviewedBy().getFullName()
        );
    }
}
