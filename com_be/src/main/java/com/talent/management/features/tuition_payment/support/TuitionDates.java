package com.talent.management.features.tuition_payment.support;

import java.time.LocalDate;
import java.time.ZoneId;

public final class TuitionDates {
    private TuitionDates() {}

    public static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
