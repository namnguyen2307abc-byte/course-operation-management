package com.talent.management.features.tuition_payment.dto.response;

import java.math.BigDecimal;

public record TuitionDiscountQuote(
        String source,
        boolean locked,
        Long placementTestId,
        BigDecimal placementScore,
        int percentage,
        String method,
        BigDecimal value,
        BigDecimal amount,
        String reason,
        String policyVersion,
        String blockingReason) {}
