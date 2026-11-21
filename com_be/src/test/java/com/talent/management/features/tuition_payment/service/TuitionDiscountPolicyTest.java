package com.talent.management.features.tuition_payment.service;

import static org.assertj.core.api.Assertions.*;

import com.talent.management.features.tuition_payment.exception.TuitionValidationException;
import com.talent.management.features.tuition_payment.policy.TuitionDiscountPolicy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class TuitionDiscountPolicyTest {
    final BigDecimal original = new BigDecimal("3200000");

    void invalid(String method, String value, String field, String message) {
        assertThatThrownBy(
                        () ->
                                TuitionDiscountPolicy.calculate(
                                        original,
                                        method,
                                        value == null ? null : new BigDecimal(value)))
                .isInstanceOfSatisfying(
                        TuitionValidationException.class,
                        e -> assertThat(e.getDetails().get(field)).contains(message));
    }

    @Test
    void distinguishesMissingNegativeFractionalZeroAndExcessivePercentage() {
        invalid("PERCENTAGE", null, "discountValue", "Vui lòng nhập");
        invalid("PERCENTAGE", "-1", "discountValue", "số âm");
        invalid("PERCENTAGE", "10.5", "discountValue", "thập phân");
        invalid("PERCENTAGE", "0", "discountValue", "Không giảm");
        invalid("PERCENTAGE", "31", "discountValue", "vượt 30%");
        invalid("UNKNOWN", "1", "discountMethod", "Vui lòng chọn");
    }

    @Test
    void fixedAmountHasBothMinimumAndThirtyPercentMaximum() {
        invalid("FIXED_AMOUNT", "999", "discountValue", "1.000đ");
        invalid("FIXED_AMOUNT", "960001", "discountValue", "960000đ");
        assertThat(
                        TuitionDiscountPolicy.calculate(
                                original, "FIXED_AMOUNT", new BigDecimal("960000")))
                .isEqualByComparingTo("960000");
    }

    @Test
    void noDiscountIsExplicitAndPercentUsesHalfUpToOneDong() {
        assertThat(TuitionDiscountPolicy.calculate(original, "NONE", BigDecimal.ZERO)).isZero();
        invalid("NONE", "1", "discountValue", "bằng 0");
        assertThat(
                        TuitionDiscountPolicy.calculate(
                                new BigDecimal("10005"), "PERCENTAGE", BigDecimal.TEN))
                .isEqualByComparingTo("1001");
    }
}
