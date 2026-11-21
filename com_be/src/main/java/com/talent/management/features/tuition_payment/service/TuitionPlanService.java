package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.request.ConfigureTuitionPlanRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;

/** Application contract for TuitionPlanService. */
public interface TuitionPlanService {
    InvoiceView configure(Long id, ConfigureTuitionPlanRequest input);
}
