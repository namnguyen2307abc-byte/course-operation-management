package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreatePaymentIntentRequest(@NotNull @DecimalMin("1") BigDecimal amount) {}
