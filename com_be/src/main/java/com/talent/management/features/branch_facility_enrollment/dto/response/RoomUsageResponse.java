package com.talent.management.features.branch_facility_enrollment.dto.response;

import com.talent.management.shared.enums.ClassStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RoomUsageResponse(RoomResponse room, List<EquipmentResponse> equipments,
                                List<ClassUsage> classes, List<LessonUsage> upcomingLessons,
                                List<String> warnings, int upcomingLessonCount) {
    public record ClassUsage(Long id, String classCode, String className, String courseName,
                              Integer maxStudents, ClassStatus status) {}

    public record LessonUsage(Long id, Long classId, String classCode, String className,
                               LocalDate lessonDate, LocalTime startTime, LocalTime endTime,
                               String teacherName) {}
}
