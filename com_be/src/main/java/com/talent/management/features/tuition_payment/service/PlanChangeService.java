package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.request.RequestDueDateChangeRequest;
import com.talent.management.features.tuition_payment.dto.request.ReviewTuitionRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.ChangeView;

import java.util.List;

/** Application contract for PlanChangeService. */
public interface PlanChangeService {
    void request(Long invoiceId, RequestDueDateChangeRequest input);

    void review(Long id, ReviewTuitionRequest input);

    List<ChangeView> list();
}
