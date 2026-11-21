package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.request.CancelReservationRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;

/** Application contract for TuitionReservationService. */
public interface TuitionReservationService {
    InvoiceView decide(Long invoiceId, CancelReservationRequest input);
}
