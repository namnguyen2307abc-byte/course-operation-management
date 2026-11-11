package com.talent.management.features.tuition_payment.mapper;

import com.talent.management.shared.entity.Payment;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Stable human-readable reference; stored gateway and technical identifiers stay unchanged. */
public final class PaymentReference {
    private PaymentReference() {}

    public static String receipt(Payment payment) {
        if (payment.getId() == null) return payment.getPaymentCode();
        String day =
                payment.getPaymentDate() == null
                        ? ""
                        : payment.getPaymentDate().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        return "PT-" + day + String.format(Locale.ROOT, "%06d", payment.getId());
    }
}
