package com.talent.management.features.tuition_payment.policy;

import com.talent.management.features.tuition_payment.exception.TuitionValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class TuitionDiscountPolicy {
    private TuitionDiscountPolicy() {}

    public static int placementPercentage(int score) {
        if (score < 0 || score > 100)
            throw invalid("placementTest", "Điểm Placement Test phải nằm trong khoảng 0–100.");
        return score >= 90 ? 30 : score >= 80 ? 20 : score >= 70 ? 10 : 0;
    }

    public static BigDecimal fixedMaximum(BigDecimal original) {
        return original.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.DOWN);
    }

    public static void validate(BigDecimal original, String method, BigDecimal value) {
        if (original == null || original.signum() <= 0)
            throw invalid(
                    "originalAmount", "Học phí gốc phải lớn hơn 0đ để áp dụng chính sách ưu đãi.");
        if (!"NONE".equals(method)
                && !"PERCENTAGE".equals(method)
                && !"FIXED_AMOUNT".equals(method))
            throw invalid(
                    "discountMethod",
                    "Vui lòng chọn Không giảm, Giảm theo phần trăm hoặc Giảm theo số tiền.");
        if (value == null) throw invalid("discountValue", "Vui lòng nhập mức giảm học phí.");
        if (value.signum() < 0)
            throw invalid("discountValue", "Mức giảm học phí không được là số âm.");
        if (value.scale() > 0)
            throw invalid(
                    "discountValue",
                    "Mức giảm học phí phải là số nguyên, không được nhập số thập phân.");
        if ("NONE".equals(method)) {
            if (value.signum() != 0)
                throw invalid("discountValue", "Khi chọn Không giảm, mức giảm phải bằng 0.");
            return;
        }
        if ("PERCENTAGE".equals(method)) {
            if (value.signum() == 0)
                throw invalid(
                        "discountValue",
                        "Phần trăm giảm phải từ 1% đến 30%. Nếu không áp dụng ưu đãi, hãy chọn"
                            + " Không giảm.");
            if (value.compareTo(BigDecimal.valueOf(30)) > 0)
                throw invalid("discountValue", "Phần trăm giảm không được vượt 30% học phí gốc.");
        } else {
            if (fixedMaximum(original).compareTo(BigDecimal.valueOf(1000)) < 0)
                throw invalid(
                        "discountValue",
                        "Học phí gốc này không đủ điều kiện giảm theo số tiền: 30% học phí thấp hơn"
                            + " mức giảm tối thiểu 1.000đ. Vui lòng chọn Không giảm hoặc giảm theo"
                            + " phần trăm.");
            if (value.compareTo(BigDecimal.valueOf(1000)) < 0)
                throw invalid(
                        "discountValue",
                        "Số tiền giảm phải từ 1.000đ trở lên. Nếu không áp dụng ưu đãi, hãy chọn"
                            + " Không giảm.");
            if (value.compareTo(fixedMaximum(original)) > 0)
                throw invalid(
                        "discountValue",
                        "Số tiền giảm không được vượt "
                                + fixedMaximum(original).toPlainString()
                                + "đ, tương đương tối đa 30% học phí gốc "
                                + original.toPlainString()
                                + "đ.");
        }
    }

    public static BigDecimal calculate(BigDecimal original, String method, BigDecimal value) {
        validate(original, method, value);
        BigDecimal reduction =
                "NONE".equals(method)
                        ? BigDecimal.ZERO
                        : "PERCENTAGE".equals(method)
                                ? original.multiply(value)
                                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                                : value;
        if (!"NONE".equals(method) && reduction.signum() <= 0)
            throw invalid(
                    "discountValue",
                    "Số tiền giảm sau làm tròn đến đồng bằng 0đ. Vui lòng chọn Không giảm hoặc mức"
                        + " giảm hợp lệ khác.");
        if (original.subtract(reduction).signum() <= 0)
            throw invalid(
                    "discountValue",
                    "Học phí sau giảm phải lớn hơn 0đ; chính sách hiện tại chỉ cho giảm tối đa"
                        + " 30%.");
        return reduction;
    }

    private static TuitionValidationException invalid(String field, String message) {
        return new TuitionValidationException(Map.of(field, message));
    }
}
