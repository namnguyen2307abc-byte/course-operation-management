package com.talent.management.features.attendance_makeup.dto.response;

/** Only the children belonging to the current viewer are included. */
public record StudentLessonAttendanceResponse(Long studentId, String fullName, String status) {}
