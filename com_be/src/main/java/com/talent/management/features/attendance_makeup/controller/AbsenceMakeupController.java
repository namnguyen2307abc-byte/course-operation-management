package com.talent.management.features.attendance_makeup.controller;

import com.talent.management.features.attendance_makeup.service.AbsenceMakeupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance-makeup")
@RequiredArgsConstructor
@Tag(name = "2. Nghỉ học & Học bù", description = "Quản lý xin nghỉ, giáo viên duyệt, ghi chú lịch rảnh, xem đề xuất ca bù và đăng ký học bù")
public class AbsenceMakeupController {

    private final AbsenceMakeupService absenceMakeupService;
}
