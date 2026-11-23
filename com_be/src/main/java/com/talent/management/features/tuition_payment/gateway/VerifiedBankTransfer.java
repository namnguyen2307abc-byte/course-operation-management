package com.talent.management.features.tuition_payment.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Internal evidence, constructed only after signed webhook/authenticated gateway validation. */
public record VerifiedBankTransfer(
        Long orderCode,
        BigDecimal amount,
        String reference,
        String content,
        String senderName,
        String senderAccount,
        String senderBank,
        LocalDateTime transferredAt,
        boolean simulated) {
    public VerifiedBankTransfer(Long code, BigDecimal amount, String ref, boolean simulated) {
        this(code, amount, ref, null, null, null, null, null, simulated);
    }
}
