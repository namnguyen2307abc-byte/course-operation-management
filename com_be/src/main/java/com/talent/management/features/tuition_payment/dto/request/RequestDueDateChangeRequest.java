package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RequestDueDateChangeRequest(
        @NotNull Long installmentId,
        @NotNull LocalDate proposedDate,
        @NotBlank @Size(max = 500) String reason) {}
