package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CollectCashRequest(
        @NotNull @DecimalMin("0") BigDecimal amount,
        BigDecimal cashGiven,
        @NotBlank @Size(max = 80) String idempotencyKey) {}
