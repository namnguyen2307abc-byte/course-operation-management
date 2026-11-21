package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ConfirmBankPaymentRequest(
        @NotNull @DecimalMin("1") BigDecimal amount,
        @NotBlank @Size(max = 120) String reference,
        @NotBlank @Size(max = 400) String reason,
        @AssertTrue(message = "Cần xác nhận đã kiểm tra tiền thực nhận tại ngân hàng.")
                boolean bankVerified) {}
