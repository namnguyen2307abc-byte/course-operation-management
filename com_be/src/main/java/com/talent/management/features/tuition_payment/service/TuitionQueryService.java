package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.OverdueContact;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.TuitionPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read models for tuition. view/list may initialize a legacy plan inside a write transaction. */
public interface TuitionQueryService {
    BigDecimal outstanding(LocalDate overdueBefore);

    InvoiceView view(Long id);

    List<InvoiceView> list();

    List<OverdueContact> overdueContacts();

    InvoiceView map(Invoice invoice, TuitionPlan plan);
}
