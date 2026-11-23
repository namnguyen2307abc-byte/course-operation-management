package com.talent.management.features.tuition_payment.policy;

import com.talent.management.features.tuition_payment.exception.TuitionValidationException;
import com.talent.management.shared.entity.TuitionInstallment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class DueDateExtensionPolicy {
    private DueDateExtensionPolicy() {}

    public static void validate(
            TuitionInstallment due,
            List<TuitionInstallment> schedule,
            LocalDate proposed,
            LocalDate today,
            LocalDate end) {
        if (end == null) fail("Chưa có ngày kết thúc lớp để xác định khoảng gia hạn.");
        LocalDate min = due.getDueDate().plusDays(1);
        if (min.isBefore(today)) min = today;
        LocalDate max = end;
        for (var other : schedule) {
            if (other.getSequenceNumber() < due.getSequenceNumber()) {
                var lower = other.getDueDate().plusDays(1);
                if (lower.isAfter(min)) min = lower;
            } else if (other.getSequenceNumber() > due.getSequenceNumber()) {
                var upper = other.getDueDate().minusDays(1);
                if (upper.isBefore(max)) max = upper;
            }
        }
        if (min.isAfter(max))
            fail(
                    "Kỳ "
                            + due.getSequenceNumber()
                            + " không còn khoảng ngày hợp lệ để gia hạn trước kỳ sau và ngày kết"
                            + " thúc lớp.");
        String range = "Hạn đóng mới chỉ được chọn từ " + format(min) + " đến " + format(max) + ".";
        if (proposed == null) fail("Vui lòng chọn hạn đóng mới. " + range);
        if (proposed.isBefore(min) || proposed.isAfter(max)) fail(range);
    }

    private static String format(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static void fail(String message) {
        throw new TuitionValidationException(Map.of("proposedDate", message));
    }
}
