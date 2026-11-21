package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.response.TuitionDiscountQuote;
import com.talent.management.shared.entity.Invoice;

import java.math.BigDecimal;

public interface TuitionDiscountService {
    TuitionDiscountQuote preview(Invoice invoice, boolean confirmed);

    TuitionDiscountQuote resolve(Invoice invoice, String method, BigDecimal value, String reason);

    void snapshot(Invoice invoice, TuitionDiscountQuote quote);
}
