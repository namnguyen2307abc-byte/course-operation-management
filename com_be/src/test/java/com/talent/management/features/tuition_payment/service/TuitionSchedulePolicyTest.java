package com.talent.management.features.tuition_payment.service;

import static org.assertj.core.api.Assertions.*;

import com.talent.management.features.tuition_payment.dto.request.InstallmentDueRequest;
import com.talent.management.features.tuition_payment.exception.TuitionValidationException;
import com.talent.management.features.tuition_payment.policy.TuitionSchedulePolicy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

class TuitionSchedulePolicyTest {
    final LocalDate today = LocalDate.of(2026, 11, 20);
    final LocalDate end = today.plusDays(30);

    InstallmentDueRequest due(String amount, LocalDate date) {
        return new InstallmentDueRequest(new BigDecimal(amount), date);
    }

    void invalid(List<InstallmentDueRequest> dues, String field, String message) {
        assertThatThrownBy(
                        () ->
                                TuitionSchedulePolicy.validate(
                                        dues, new BigDecimal("10000"), today, end))
                .isInstanceOfSatisfying(
                        TuitionValidationException.class,
                        e -> assertThat(e.getDetails().get(field)).contains(message));
    }

    @Test
    void endDateIsInclusiveAndFirstThirtyPercentIsAccepted() {
        assertThatCode(
                        () ->
                                TuitionSchedulePolicy.validate(
                                        List.of(due("3000", today), due("7000", end)),
                                        new BigDecimal("10000"),
                                        today,
                                        end))
                .doesNotThrowAnyException();
    }

    @Test
    void namesThePeriodSelectedDateAndLastAllowedDate() {
        invalid(
                List.of(due("3000", today), due("7000", end.plusDays(1))),
                "installments[1].date",
                "21/12/2026 vượt ngày kết thúc lớp 20/12/2026");
    }

    @Test
    void zeroCannotBecomeAPaidInstallment() {
        invalid(
                List.of(due("10000", today), due("0", end)),
                "installments[1].amount",
                "lớn hơn 0đ");
    }

    @Test
    void negativeAndFractionalAmountsAreRejectedSeparately() {
        invalid(List.of(due("-1", today)), "installments[0].amount", "lớn hơn 0đ");
        invalid(List.of(due("9999.5", today)), "installments[0].amount", "số thập phân");
    }

    @Test
    void pastAndUnorderedDatesAreRejectedSeparately() {
        invalid(List.of(due("10000", today.minusDays(1))), "installments[0].date", "trước hôm nay");
        invalid(
                List.of(due("3000", today), due("7000", today)),
                "installments[1].date",
                "sau hạn kỳ 1");
    }

    @Test
    void totalAndFirstPaymentHaveSeparateErrors() {
        invalid(List.of(due("3000", today), due("6000", end)), "installments", "còn thiếu");
        invalid(List.of(due("2999", today), due("7001", end)), "installments[0].amount", "30%");
    }

    @Test
    void zeroTotalAndMoreThanThreePeriodsCannotCreateSchedules() {
        assertThatThrownBy(
                        () ->
                                TuitionSchedulePolicy.validate(
                                        List.of(due("0", today)), BigDecimal.ZERO, today, end))
                .isInstanceOf(TuitionValidationException.class);
        invalid(
                List.of(
                        due("2500", today),
                        due("2500", today.plusDays(1)),
                        due("2500", today.plusDays(2)),
                        due("2500", end)),
                "installments",
                "1 đến 3");
    }

    @Test
    void firstPeriodCannotUseLastDayWhenAnotherPeriodIsRequired() {
        assertThatThrownBy(
                        () ->
                                TuitionSchedulePolicy.validate(
                                        List.of(
                                                due("648000", LocalDate.of(2027, 1, 14)),
                                                new InstallmentDueRequest(
                                                        null, LocalDate.of(2027, 1, 14))),
                                        new BigDecimal("2160000"),
                                        today,
                                        LocalDate.of(2027, 1, 14)))
                .isInstanceOfSatisfying(
                        TuitionValidationException.class,
                        e -> {
                            assertThat(e.getDetails().get("installments[0].date"))
                                    .contains("13/01/2027", "1 kỳ tiếp theo");
                            assertThat(e.getDetails().get("installments[1].amount"))
                                    .contains("vui lòng nhập");
                            assertThat(e.getDetails()).doesNotContainKey("installments[1].date");
                        });
    }

    @Test
    void threePeriodsReserveTwoDaysAndAcceptConsecutiveLastDays() {
        invalid(
                List.of(due("3000", end.minusDays(1)), due("3000", end), due("4000", end)),
                "installments[0].date",
                "18/12/2026");
        assertThatCode(
                        () ->
                                TuitionSchedulePolicy.validate(
                                        List.of(
                                                due("3000", end.minusDays(2)),
                                                due("3000", end.minusDays(1)),
                                                due("4000", end)),
                                        new BigDecimal("10000"),
                                        today,
                                        end))
                .doesNotThrowAnyException();
    }

    @Test
    void tooFewCalendarDaysRequiresFewerPeriodsButSinglePaymentCanUseLastDay() {
        assertThatThrownBy(
                        () ->
                                TuitionSchedulePolicy.validate(
                                        List.of(due("3000", today), due("7000", today)),
                                        new BigDecimal("10000"),
                                        today,
                                        today))
                .isInstanceOf(TuitionValidationException.class)
                .hasMessageContaining("Không đủ ngày");
        assertThatCode(
                        () ->
                                TuitionSchedulePolicy.validate(
                                        List.of(due("10000", today)),
                                        new BigDecimal("10000"),
                                        today,
                                        today))
                .doesNotThrowAnyException();
    }

    @Test
    void thirdDateCannotFollowAnInvalidSecondDateBackBeforeTheFirstDate() {
        var last = LocalDate.of(2027, 1, 14);
        assertThatThrownBy(
                        () ->
                                TuitionSchedulePolicy.validate(
                                        List.of(
                                                due("648000", last.minusDays(2)),
                                                due("756000", today.plusDays(5)),
                                                due("756000", today.plusDays(7))),
                                        new BigDecimal("2160000"),
                                        today,
                                        last))
                .isInstanceOfSatisfying(
                        TuitionValidationException.class,
                        e -> {
                            assertThat(e.getDetails().get("installments[1].date"))
                                    .contains("13/01/2027");
                            assertThat(e.getDetails().get("installments[2].date"))
                                    .contains(
                                            "Hạn đóng chỉ được chọn từ 14/01/2027 đến 14/01/2027");
                        });
    }

    @Test
    void missingSecondDateStillReservesADayBetweenFirstAndThird() {
        var last = LocalDate.of(2027, 1, 14);
        assertThatThrownBy(
                        () ->
                                TuitionSchedulePolicy.validate(
                                        List.of(
                                                due("648000", last.minusDays(2)),
                                                due("756000", null),
                                                due("756000", last.minusDays(1))),
                                        new BigDecimal("2160000"),
                                        today,
                                        last))
                .isInstanceOfSatisfying(
                        TuitionValidationException.class,
                        e ->
                                assertThat(e.getDetails().get("installments[2].date"))
                                        .contains("14/01/2027"));
    }
}
