package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RequestDueDateChangeRequest(
        @NotNull(message = "Vui lòng chọn kỳ thanh toán cần gia hạn.") Long installmentId,
        @NotNull(message = "Vui lòng chọn hạn đóng mới.") LocalDate proposedDate,
        @NotBlank(message = "Vui lòng nhập lý do gia hạn.")
                @Size(max = 500, message = "Lý do gia hạn tối đa 500 ký tự.")
                String reason) {}
