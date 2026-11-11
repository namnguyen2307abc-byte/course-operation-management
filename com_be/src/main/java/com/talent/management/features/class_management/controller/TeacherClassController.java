package com.talent.management.features.class_management.controller;

import com.talent.management.features.class_management.dto.response.TeacherClassDetailResponse;
import com.talent.management.features.class_management.dto.response.TeacherClassSummaryResponse;
import com.talent.management.features.class_management.service.TeacherClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/classes")
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'STAFF', 'BRANCH_MANAGER')")
@RequiredArgsConstructor
@Tag(name = "Lớp Phụ Trách (Teacher Classes)", description = "Quản lý danh sách lớp phụ trách, học viên và tiến độ buổi học của giáo viên")
public class TeacherClassController {

    private final TeacherClassService teacherClassService;

    @Operation(summary = "Lấy danh sách các lớp phụ trách giảng dạy của giáo viên hiện tại")
    @GetMapping
    public ResponseEntity<List<TeacherClassSummaryResponse>> getMyClasses(
            @RequestParam(required = false) Long teacherId) {
        return ResponseEntity.ok(teacherClassService.getTeacherClasses(teacherId));
    }

    @Operation(summary = "Lấy chi tiết lớp phụ trách: danh sách học viên, phụ huynh & tiến độ buổi học")
    @GetMapping("/{classId}")
    public ResponseEntity<TeacherClassDetailResponse> getClassDetail(@PathVariable Long classId) {
        return ResponseEntity.ok(teacherClassService.getTeacherClassDetail(classId));
    }
}
