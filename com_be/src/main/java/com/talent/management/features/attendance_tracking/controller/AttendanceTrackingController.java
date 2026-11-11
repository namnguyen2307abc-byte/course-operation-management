package com.talent.management.features.attendance_tracking.controller;

import com.talent.management.features.attendance_tracking.dto.request.BatchAttendanceSaveRequest;
import com.talent.management.features.attendance_tracking.dto.response.EnrolledClassOptionResponse;
import com.talent.management.features.attendance_tracking.dto.response.LessonAttendanceSheetResponse;
import com.talent.management.features.attendance_tracking.dto.response.StudentAttendanceTrackingResponse;
import com.talent.management.features.attendance_tracking.service.AttendanceTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "3. Điểm Danh & Tiến Độ Khóa Học (Attendance Tracking)",
     description = "Quản lý điểm danh buổi học (Lesson Attendance) và tra cứu tiến độ chuyên cần toàn bộ khóa học")
public class AttendanceTrackingController {

    private final AttendanceTrackingService attendanceTrackingService;

    @Operation(summary = "Lấy bảng điểm danh học viên của một ca học (Kèm cờ canEdit và lý do khóa nếu có)")
    @GetMapping({"/api/schedule/lessons/{lessonId}/attendance", "/api/attendance-tracking/lessons/{lessonId}"})
    public ResponseEntity<LessonAttendanceSheetResponse> getLessonAttendanceSheet(@PathVariable Long lessonId) {
        LessonAttendanceSheetResponse response = attendanceTrackingService.getLessonAttendanceSheet(lessonId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lưu danh sách điểm danh cả lớp (Batch Attendance)")
    @PostMapping({"/api/schedule/lessons/{lessonId}/attendance", "/api/attendance-tracking/lessons/{lessonId}"})
    public ResponseEntity<Map<String, Object>> saveLessonAttendanceBatch(
            @PathVariable Long lessonId,
            @Valid @RequestBody BatchAttendanceSaveRequest request) {
        attendanceTrackingService.saveLessonAttendanceBatch(lessonId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Điểm danh buổi học thành công",
                "lessonId", lessonId
        ));
    }

    @Operation(summary = "Lấy danh sách học viên được phép tra cứu theo phân quyền tài khoản hiện tại")
    @GetMapping({"/api/schedule/students", "/api/attendance-tracking/students"})
    public ResponseEntity<List<Map<String, Object>>> getTrackableStudents() {
        return ResponseEntity.ok(attendanceTrackingService.getTrackableStudents());
    }

    @Operation(summary = "Lấy danh sách lớp học mà học viên đã ghi danh")
    @GetMapping({"/api/schedule/students/{studentId}/enrolled-classes", "/api/attendance-tracking/students/{studentId}/enrolled-classes"})
    public ResponseEntity<List<EnrolledClassOptionResponse>> getEnrolledClassesForStudent(
            @PathVariable Long studentId) {
        List<EnrolledClassOptionResponse> classes = attendanceTrackingService.getEnrolledClassesForStudent(studentId);
        return ResponseEntity.ok(classes);
    }

    @Operation(summary = "Tra cứu chi tiết tiến độ điểm danh toàn bộ khóa học của học viên")
    @GetMapping({"/api/schedule/attendance-tracking", "/api/attendance-tracking"})
    public ResponseEntity<StudentAttendanceTrackingResponse> getStudentAttendanceTracking(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long classId) {
        StudentAttendanceTrackingResponse response = attendanceTrackingService.getStudentAttendanceTracking(studentId, classId);
        return ResponseEntity.ok(response);
    }
}
