package com.talent.management.features.tuition_payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cash settlement is separate from the tuition credited to the student's account. */
public final class CashTenderPolicy {
    private CashTenderPolicy() {}

    public record Quote(BigDecimal change, BigDecimal support) {}

    public static Quote quote(BigDecimal amount, BigDecimal given) {
        if (given == null || given.signum() < 0 || given.scale() > 0
                || given.compareTo(new BigDecimal("9999999999")) > 0)
            throw TuitionPlanService.conflict("Tiền khách đưa phải là số nguyên VND không âm trong giới hạn cho phép.");
        if (given.compareTo(amount) < 0)
            throw TuitionPlanService.conflict("Tiền khách đưa phải lớn hơn hoặc bằng số tiền thanh toán.");
        BigDecimal raw = given.subtract(amount);
        if (raw.signum() == 0) return new Quote(BigDecimal.ZERO, BigDecimal.ZERO);
        if (!representable(given))
            throw TuitionPlanService.conflict("Tiền khách đưa dư phải ghép được từ các mệnh giá từ 200đ trở lên.");
        BigDecimal rounded = raw.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING)
                .multiply(BigDecimal.valueOf(100));
        if (rounded.compareTo(BigDecimal.valueOf(100)) == 0 || rounded.compareTo(BigDecimal.valueOf(300)) == 0)
            rounded = rounded.add(BigDecimal.valueOf(100));
        return new Quote(rounded, rounded.subtract(raw));
    }

    private static boolean representable(BigDecimal amount) {
        return amount.remainder(BigDecimal.valueOf(100)).signum() == 0
                && amount.compareTo(BigDecimal.valueOf(100)) != 0
                && amount.compareTo(BigDecimal.valueOf(300)) != 0;
    }
}
