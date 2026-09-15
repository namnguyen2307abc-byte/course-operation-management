package com.talent.management.features.tuition_payment.service.impl;

import com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper;
import com.talent.management.features.tuition_payment.repository.InvoiceRepository;
import com.talent.management.features.tuition_payment.service.TuitionPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TuitionPaymentServiceImpl implements TuitionPaymentService {

    private final InvoiceRepository invoiceRepository;
    private final TuitionPaymentMapper tuitionPaymentMapper;
}
