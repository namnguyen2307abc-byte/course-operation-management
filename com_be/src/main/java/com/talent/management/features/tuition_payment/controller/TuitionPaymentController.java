package com.talent.management.features.tuition_payment.controller;

import com.talent.management.features.tuition_payment.service.TuitionPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tuition-payment")
@RequiredArgsConstructor
@Tag(name = "4. Nghĩa vụ Thanh toán học phí", description = "Quản lý hóa đơn học phí, chính sách miễn giảm phí và thanh toán VietQR")
public class TuitionPaymentController {

    private final TuitionPaymentService tuitionPaymentService;
}
