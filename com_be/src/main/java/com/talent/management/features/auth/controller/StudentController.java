package com.talent.management.features.auth.controller;

import com.talent.management.features.auth.dto.CreateStudentRequest;
import com.talent.management.features.auth.dto.StudentDto;
import com.talent.management.features.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Tag(name = "Students", description = "Quản lý hồ sơ học viên (Hồ sơ con em do phụ huynh quản lý hoặc học viên độc lập)")
public class StudentController {

    private final AuthService authService;

    @GetMapping
    @Operation(summary = "Lấy danh sách học viên", description = "Lấy toàn bộ hoặc lọc theo ID phụ huynh")
    public ResponseEntity<List<StudentDto>> getAllStudents(@RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(authService.getAllStudents(parentId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết học viên")
    public ResponseEntity<StudentDto> getStudentById(@PathVariable Long id) {
        return ResponseEntity.ok(authService.getStudentById(id));
    }

    @PostMapping
    @Operation(summary = "Thêm mới hồ sơ học viên")
    public ResponseEntity<StudentDto> createStudent(@Valid @RequestBody CreateStudentRequest request) {
        return ResponseEntity.ok(authService.createStudent(request));
    }
}
