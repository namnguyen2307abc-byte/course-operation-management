package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentDueRequest(
        @NotNull(message = "Vui lòng nhập số tiền của kỳ thanh toán.") BigDecimal amount,
        @NotNull(message = "Vui lòng chọn hạn đóng của kỳ thanh toán.") LocalDate date) {}
