package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TuitionPaymentService {

    private final InvoiceRepository invoiceRepository;
}
