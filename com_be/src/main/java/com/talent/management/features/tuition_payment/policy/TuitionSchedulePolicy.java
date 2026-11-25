package com.talent.management.features.tuition_payment.policy;

import com.talent.management.features.tuition_payment.dto.request.InstallmentDueRequest;
import com.talent.management.features.tuition_payment.exception.TuitionValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TuitionSchedulePolicy {
    private TuitionSchedulePolicy() {}

    public static void validate(
            List<InstallmentDueRequest> dues, BigDecimal total, LocalDate today, LocalDate end) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (dues == null || dues.isEmpty() || dues.size() > 3) {
            errors.put("installments", "Chỉ được chọn từ 1 đến 3 kỳ thanh toán.");
            throw new TuitionValidationException(errors);
        }
        if (end != null && end.isBefore(today)) {
            errors.put(
                    "installments",
                    "Lớp đã kết thúc ngày "
                            + date(end)
                            + ". Không thể lập lịch đóng mới từ hôm nay "
                            + date(today)
                            + ".");
            throw new TuitionValidationException(errors);
        }
        if (end != null && end.isBefore(today.plusDays(dues.size() - 1))) {
            errors.put(
                    "installments",
                    "Không đủ ngày để chia "
                            + dues.size()
                            + " kỳ có hạn đóng khác nhau từ "
                            + date(today)
                            + " đến "
                            + date(end)
                            + ". Vui lòng chọn ít kỳ hơn.");
            throw new TuitionValidationException(errors);
        }
        if (total.signum() <= 0) {
            errors.put(
                    "installments",
                    "Học phí sau giảm phải lớn hơn 0đ để lập lịch đóng; không được tạo kỳ thanh"
                            + " toán 0đ.");
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean amountsValid = true;
        for (int i = 0; i < dues.size(); i++) {
            var due = dues.get(i);
            String key = "installments[" + i + "]";
            String period = "Kỳ " + (i + 1);
            if (due == null) {
                errors.put(key, period + ": thiếu số tiền và hạn đóng.");
                amountsValid = false;
                continue;
            }
            BigDecimal amount = due.amount();
            if (amount == null)
                errors.put(key + ".amount", period + ": vui lòng nhập số tiền phải đóng.");
            else if (amount.signum() <= 0)
                errors.put(
                        key + ".amount",
                        period
                                + ": số tiền phải lớn hơn 0đ. Nếu không cần kỳ này, hãy chọn ít kỳ"
                                + " hơn.");
            else if (amount.compareTo(BigDecimal.valueOf(2000)) < 0)
                errors.put(
                        key + ".amount",
                        period
                                + ": mỗi kỳ phải từ 2.000đ trở lên. Nếu học phí còn lại quá nhỏ,"
                                + " hãy chọn ít kỳ hơn.");
            else if (amount.stripTrailingZeros().scale() > 0)
                errors.put(
                        key + ".amount",
                        period + ": số tiền phải là số nguyên đồng, không được nhập số thập phân.");
            else if (amount.compareTo(new BigDecimal("9999999999")) > 0)
                errors.put(
                        key + ".amount",
                        period + ": số tiền không được vượt giới hạn 9.999.999.999đ.");
            else if (amount.compareTo(total) > 0)
                errors.put(
                        key + ".amount",
                        period
                                + ": số tiền không được vượt học phí sau giảm "
                                + money(total)
                                + ".");
            if (errors.containsKey(key + ".amount")) amountsValid = false;
            else sum = sum.add(amount);
            LocalDate date = due.date();
            if (date == null) errors.put(key + ".date", period + ": vui lòng chọn hạn đóng.");
            else if (end != null && date.isAfter(end))
                errors.put(
                        key + ".date",
                        period
                                + ": hạn đóng "
                                + date(date)
                                + " vượt ngày kết thúc lớp "
                                + date(end)
                                + ". Vui lòng chọn ngày không sau "
                                + date(end)
                                + ".");
            else if (date.isBefore(today))
                errors.put(
                        key + ".date",
                        period + ": hạn đóng không được trước hôm nay " + date(today) + ".");
            else if (end != null && date.isAfter(end.minusDays(dues.size() - i - 1))) {
                LocalDate latest = end.minusDays(dues.size() - i - 1);
                errors.put(
                        key + ".date",
                        period
                                + ": hạn đóng muộn nhất là "
                                + date(latest)
                                + " để còn đủ ngày cho "
                                + (dues.size() - i - 1)
                                + " kỳ tiếp theo trước hoặc trong ngày kết thúc lớp "
                                + date(end)
                                + ". Vui lòng chọn ngày không sau "
                                + date(latest)
                                + " hoặc giảm số kỳ thanh toán.");
            } else if (date.isBefore(earliest(dues, today, end, i))) {
                LocalDate previous =
                        i > 0 && dues.get(i - 1) != null ? dues.get(i - 1).date() : null;
                boolean direct =
                        previous != null
                                && (end == null
                                        || !previous.isAfter(end.minusDays(dues.size() - i)))
                                && !date.isAfter(previous);
                String explanation =
                        direct
                                ? "hạn đóng phải sau hạn kỳ " + i + " (" + date(previous) + "). "
                                : "Các kỳ thanh toán phải theo thứ tự tăng dần. ";
                LocalDate min = earliest(dues, today, end, i);
                String range =
                        "Hạn đóng chỉ được chọn từ "
                                + date(min)
                                + (end == null
                                        ? " trở đi"
                                        : " đến " + date(end.minusDays(dues.size() - i - 1)))
                                + ".";
                errors.put(key + ".date", period + ": " + explanation + range);
            }
        }
        if (amountsValid && total.signum() > 0) {
            if (sum.compareTo(total) != 0)
                errors.put(
                        "installments",
                        "Tổng các kỳ là "
                                + money(sum)
                                + ", "
                                + (sum.compareTo(total) < 0 ? "còn thiếu " : "vượt ")
                                + money(sum.subtract(total).abs())
                                + " so với học phí sau giảm "
                                + money(total)
                                + ".");
            BigDecimal minimum =
                    total.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.CEILING);
            if (dues.size() > 1 && dues.get(0).amount().compareTo(minimum) < 0)
                errors.put(
                        "installments[0].amount",
                        "Kỳ 1 phải từ "
                                + money(minimum)
                                + " trở lên, tương đương tối thiểu 30% học phí sau giảm "
                                + money(total)
                                + ".");
        }
        if (!errors.isEmpty()) throw new TuitionValidationException(errors);
    }

    private static LocalDate earliest(
            List<InstallmentDueRequest> dues, LocalDate today, LocalDate end, int index) {
        LocalDate minimum = today.plusDays(index);
        for (int j = 0; j < index; j++) {
            LocalDate previous = dues.get(j) == null ? null : dues.get(j).date();
            if (previous != null
                    && !previous.isBefore(today)
                    && (end == null || !previous.isAfter(end.minusDays(dues.size() - j - 1)))) {
                LocalDate candidate = previous.plusDays(index - j);
                if (candidate.isAfter(minimum)) minimum = candidate;
            }
        }
        return minimum;
    }

    private static String date(LocalDate value) {
        return value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static String money(BigDecimal value) {
        return String.format(Locale.forLanguageTag("vi-VN"), "%,.0fđ", value);
    }
}
