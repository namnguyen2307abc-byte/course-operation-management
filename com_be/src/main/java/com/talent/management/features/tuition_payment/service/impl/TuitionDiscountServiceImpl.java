package com.talent.management.features.tuition_payment.service.impl;

import com.talent.management.features.tuition_payment.dto.response.TuitionDiscountQuote;
import com.talent.management.features.tuition_payment.entity.TuitionDiscountSnapshot;
import com.talent.management.features.tuition_payment.exception.TuitionValidationException;
import com.talent.management.features.tuition_payment.policy.TuitionDiscountPolicy;
import com.talent.management.features.tuition_payment.repository.TuitionLedgerRepository;
import com.talent.management.features.tuition_payment.service.TuitionDiscountService;
import com.talent.management.shared.entity.Invoice;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TuitionDiscountServiceImpl implements TuitionDiscountService {
    private final TuitionLedgerRepository repository;
    public static final String VERSION = "PLACEMENT_TUITION_20261120_V1";

    @Override
    public TuitionDiscountQuote preview(Invoice invoice, boolean confirmed) {
        var stored = repository.discountSnapshot(invoice.getId());
        if (stored.isPresent()) {
            var s = stored.get();
            return new TuitionDiscountQuote(
                    s.getSource(),
                    true,
                    s.getPlacementTestId(),
                    s.getPlacementScore(),
                    s.getPercentage(),
                    s.getMethod(),
                    s.getValue(),
                    s.getAmount(),
                    s.getReason(),
                    s.getPolicyVersion(),
                    null);
        }
        if (confirmed)
            return new TuitionDiscountQuote(
                    "LEGACY",
                    true,
                    null,
                    null,
                    0,
                    "FIXED_AMOUNT",
                    invoice.getDiscountAmount(),
                    invoice.getDiscountAmount(),
                    invoice.getDiscountReason(),
                    "LEGACY",
                    null);
        var request =
                invoice.getEnrollment() == null
                        ? null
                        : repository
                                .enrollmentRequest(invoice.getEnrollment().getId())
                                .orElse(null);
        if (request == null || !request.isPlacementRequested())
            return new TuitionDiscountQuote(
                    "MANUAL",
                    false,
                    null,
                    null,
                    0,
                    "NONE",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    VERSION,
                    null);
        var course = invoice.getEnrollment().getClassEntity().getCourse();
        var test =
                repository
                        .completedPlacement(
                                invoice.getStudent().getId(),
                                course.getId(),
                                request.getReviewedAt())
                        .orElse(null);
        if (test == null)
            return blocked(
                    "Hồ sơ đăng ký qua Placement Test nhưng chưa có kết quả đã hoàn tất phù hợp với"
                        + " khóa học được xếp. Vui lòng kiểm tra lại kết quả test trước khi chốt"
                        + " học phí.");
        if (test.getScore() == null || test.getScore() < 0 || test.getScore() > 100)
            return blocked(
                    "Điểm Placement Test #"
                            + test.getId()
                            + " không hợp lệ: phải nằm trong thang 0–100 (tương ứng 0–10 điểm). Vui"
                            + " lòng kiểm tra kết quả test.");
        int rate = TuitionDiscountPolicy.placementPercentage(test.getScore());
        var amount =
                TuitionDiscountPolicy.calculate(
                        invoice.getOriginalAmount(),
                        rate == 0 ? "NONE" : "PERCENTAGE",
                        BigDecimal.valueOf(rate));
        String reason =
                "Placement Test #"
                        + test.getId()
                        + ": "
                        + BigDecimal.valueOf(test.getScore(), 1).toPlainString()
                        + "/10 điểm; ưu đãi "
                        + rate
                        + "%.";
        return new TuitionDiscountQuote(
                "PLACEMENT_TEST",
                true,
                test.getId(),
                BigDecimal.valueOf(test.getScore(), 1),
                rate,
                rate == 0 ? "NONE" : "PERCENTAGE",
                BigDecimal.valueOf(rate),
                amount,
                reason,
                VERSION,
                null);
    }

    private TuitionDiscountQuote blocked(String reason) {
        return new TuitionDiscountQuote(
                "PLACEMENT_TEST",
                true,
                null,
                null,
                0,
                "NONE",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                VERSION,
                reason);
    }

    @Override
    public TuitionDiscountQuote resolve(
            Invoice invoice, String method, BigDecimal value, String reason) {
        var quote = preview(invoice, false);
        if (quote.blockingReason() != null) throw invalid("placementTest", quote.blockingReason());
        if ("PLACEMENT_TEST".equals(quote.source())) {
            if ((method != null && (!method.equals(quote.method()) || value == null))
                    || (value != null && value.compareTo(quote.value()) != 0))
                throw invalid(
                        "discountValue",
                        "Ưu đãi được cố định ở "
                                + quote.percentage()
                                + "% theo Placement Test #"
                                + quote.placementTestId()
                                + " ("
                                + quote.placementScore()
                                + "/10 điểm). Không được sửa mức giảm hoặc chuyển kiểu ưu đãi.");
            return quote;
        }
        if (method == null && value != null)
            throw invalid("discountMethod", "Vui lòng chọn kiểu ưu đãi trước khi nhập mức giảm.");
        if (method == null) {
            method =
                    invoice.getDiscountAmount() != null && invoice.getDiscountAmount().signum() > 0
                            ? "FIXED_AMOUNT"
                            : "NONE";
            value =
                    "NONE".equals(method)
                            ? BigDecimal.ZERO
                            : invoice.getDiscountAmount().stripTrailingZeros();
            reason = invoice.getDiscountReason();
        }
        BigDecimal reduction =
                TuitionDiscountPolicy.calculate(invoice.getOriginalAmount(), method, value);
        if (reduction.signum() > 0 && (reason == null || reason.isBlank()))
            throw invalid("discountReason", "Vui lòng nhập lý do áp dụng ưu đãi học phí.");
        if (reason != null && reason.length() > 255)
            throw invalid("discountReason", "Lý do ưu đãi không được vượt 255 ký tự.");
        return new TuitionDiscountQuote(
                "MANUAL",
                false,
                null,
                null,
                "PERCENTAGE".equals(method) ? value.intValueExact() : 0,
                method,
                value,
                reduction,
                "NONE".equals(method) ? null : reason.trim(),
                VERSION,
                null);
    }

    @Override
    public void snapshot(Invoice invoice, TuitionDiscountQuote q) {
        if (repository.discountSnapshot(invoice.getId()).isPresent())
            throw invalid(
                    "discountValue",
                    "Ưu đãi của hóa đơn đã được chốt. Không được ghi đè chính sách đã lưu.");
        var s = new TuitionDiscountSnapshot();
        s.setInvoiceId(invoice.getId());
        s.setSource(q.source());
        s.setPlacementTestId(q.placementTestId());
        s.setPlacementScore(q.placementScore());
        s.setPercentage(q.percentage());
        s.setMethod(q.method());
        s.setValue(q.value());
        s.setAmount(q.amount());
        s.setOriginalAmount(invoice.getOriginalAmount());
        s.setReason(q.reason());
        s.setPolicyVersion(q.policyVersion());
        s.setConfirmedAt(LocalDateTime.now());
        repository.saveDiscountSnapshot(s);
    }

    private TuitionValidationException invalid(String field, String message) {
        return new TuitionValidationException(Map.of(field, message));
    }
}
