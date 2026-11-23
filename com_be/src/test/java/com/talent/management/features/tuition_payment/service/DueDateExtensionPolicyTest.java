package com.talent.management.features.tuition_payment.service;

import static org.assertj.core.api.Assertions.*;

import com.talent.management.features.tuition_payment.policy.DueDateExtensionPolicy;
import com.talent.management.shared.entity.TuitionInstallment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

class DueDateExtensionPolicyTest {
    private final LocalDate today = LocalDate.of(2030, 11, 23);

    private TuitionInstallment due(int sequence, String date) {
        var due = new TuitionInstallment();
        due.setSequenceNumber(sequence);
        due.setDueDate(LocalDate.parse(date));
        return due;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2030-11-22",
                "2030-11-23",
                "2030-11-24",
                "2030-11-25",
                "2030-11-30",
                "2030-12-01"
            })
    void rejectsPastOldOrNextPeriodDateWithExactRange(String proposed) {
        var second = due(2, "2030-11-25");
        assertThatThrownBy(
                        () ->
                                DueDateExtensionPolicy.validate(
                                        second,
                                        List.of(due(1, "2030-11-23"), second, due(3, "2030-11-30")),
                                        LocalDate.parse(proposed),
                                        today,
                                        LocalDate.of(2030, 12, 31)))
                .hasMessageContaining("26/11/2030 đến 29/11/2030");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-11-26", "2030-11-29"})
    void acceptsBothDateRangeBoundaries(String proposed) {
        var second = due(2, "2030-11-25");
        assertThatCode(
                        () ->
                                DueDateExtensionPolicy.validate(
                                        second,
                                        List.of(due(1, "2030-11-23"), second, due(3, "2030-11-30")),
                                        LocalDate.parse(proposed),
                                        today,
                                        LocalDate.of(2030, 12, 31)))
                .doesNotThrowAnyException();
    }

    @Test
    void lastPeriodCannotExceedClassEnd() {
        var last = due(3, "2030-11-25");
        assertThatThrownBy(
                        () ->
                                DueDateExtensionPolicy.validate(
                                        last,
                                        List.of(last),
                                        LocalDate.of(2030, 12, 2),
                                        today,
                                        LocalDate.of(2030, 12, 1)))
                .hasMessageContaining("26/11/2030 đến 01/12/2030");
    }

    @Test
    void noRemainingDaysAndUnknownEndAreExplicitErrors() {
        var first = due(1, "2030-11-25");
        assertThatThrownBy(
                        () ->
                                DueDateExtensionPolicy.validate(
                                        first,
                                        List.of(first, due(2, "2030-11-26")),
                                        today,
                                        today,
                                        LocalDate.of(2030, 12, 1)))
                .hasMessageContaining("không còn khoảng ngày");
        assertThatThrownBy(
                        () ->
                                DueDateExtensionPolicy.validate(
                                        first, List.of(first), today, today, null))
                .hasMessageContaining("Chưa có ngày kết thúc");
    }

    @Test
    void overduePeriodCanExtendToTodayButNotEqualPreviousPeriod() {
        var second = due(2, "2030-11-20");
        assertThatCode(
                        () ->
                                DueDateExtensionPolicy.validate(
                                        second,
                                        List.of(due(1, "2030-11-19"), second),
                                        today,
                                        today,
                                        LocalDate.of(2030, 12, 1)))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                DueDateExtensionPolicy.validate(
                                        second,
                                        List.of(due(1, "2030-11-24"), second),
                                        LocalDate.of(2030, 11, 24),
                                        today,
                                        LocalDate.of(2030, 12, 1)))
                .hasMessageContaining("25/11/2030");
    }
}
