package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.request.ReviewTuitionRequest;

import java.math.BigDecimal;
import java.util.List;

/** Application contract for TuitionReversalService. */
public interface TuitionReversalService {
    public record View(
            Long id,
            String code,
            String student,
            BigDecimal amount,
            String status,
            String reason,
            String reviewNote,
            String requestedBy,
            String reviewedBy) {}

    void request(Long paymentId, String reason);

    void review(Long id, ReviewTuitionRequest input);

    List<View> list();
}
