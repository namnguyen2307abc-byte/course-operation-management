package com.talent.management.features.tuition_payment.dto.request;

import jakarta.validation.constraints.*;

public record ReviewTuitionRequest(boolean approve, @NotBlank @Size(max = 500) String note) {}
