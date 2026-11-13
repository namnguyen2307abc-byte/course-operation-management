package com.talent.management.features.tuition_payment.service;

import static com.talent.management.features.tuition_payment.service.TuitionPlanService.conflict;

import com.talent.management.features.tuition_payment.dto.request.InstallmentRequests.Review;
import com.talent.management.features.tuition_payment.repository.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TuitionReversalService {
    private final TuitionPlanService ledger;
    private final PaymentRepository payments;
    private final TuitionReversalRepository reversals;
    private final CurrentUserService current;

    public record View(
            Long id,
            String code,
            String student,
            BigDecimal amount,
            String status,
            String reason,
            String reviewNote,
            String requestedBy,
            String reviewedBy) {}

    @Transactional
    public void request(Long paymentId, String reason) {
        if (!List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER)
                .contains(current.getCurrentUser().getRole()))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Chỉ nhân viên hoặc quản lý được đề nghị điều chỉnh khoản thu.");
        Payment payment = payments.findById(paymentId).orElseThrow();
        ledger.lock(payment.getInvoice().getId());
        if (payment.getPaymentMethod() != PaymentMethod.CASH_AT_DESK
                || payment.getStatus() != PaymentStatus.SUCCESS
                || reversals.existsByPaymentId(paymentId)
                || reason == null
                || reason.isBlank()
                || reason.length() > 500) {
            throw conflict(
                    "Chỉ đề nghị đảo một giao dịch tiền mặt ghi sai, chưa có đề nghị khác. Cần lý"
                            + " do rõ.");
        }
        TuitionReversal r = new TuitionReversal();
        r.setPayment(payment);
        r.setReason(reason);
        r.setRequestedBy(current.getCurrentUser());
        reversals.save(r);
    }

    @Transactional
    public void review(Long id, Review input) {
        requireManager();
        TuitionReversal snapshot = reversals.findById(id).orElseThrow();
        Invoice invoice = ledger.lock(snapshot.getPayment().getInvoice().getId());
        TuitionReversal r = reversals.findById(id).orElseThrow();
        TuitionPlan plan = ledger.ensure(invoice);
        if (plan.isReviewRequired() || !r.getStatus().equals("PENDING"))
            throw conflict("Đề nghị đã xử lý hoặc hóa đơn đang cần đối chiếu.");
        if (r.getRequestedBy().getId().equals(current.getCurrentUser().getId()))
            throw conflict("Người đề nghị không được tự duyệt đảo giao dịch.");
        r.setStatus(input.approve() ? "APPROVED" : "REJECTED");
        r.setReviewedBy(current.getCurrentUser());
        r.setReviewNote(input.note());
        r.setReviewedAt(LocalDateTime.now());
        reversals.saveAndFlush(r);
        if (input.approve()) {
            BigDecimal paid = ledger.paid(invoice);
            invoice.setStatus(
                    paid.compareTo(invoice.getFinalAmount()) == 0
                            ? InvoiceStatus.PAID
                            : paid.signum() == 0
                                    ? InvoiceStatus.UNPAID
                                    : InvoiceStatus.PARTIALLY_PAID);
            plan.setRevision(plan.getRevision() + 1);
            // Enrollment remains active. The receivable now reflects the corrected cash record.
        }
    }

    @Transactional(readOnly = true)
    public List<View> list() {
        requireManager();
        return reversals.findAllByOrderByIdDesc().stream()
                .map(
                        r ->
                                new View(
                                        r.getId(),
                                        com.talent.management.features.tuition_payment.mapper
                                                .PaymentReference.receipt(r.getPayment()),
                                        r.getPayment().getInvoice().getStudent().getFullName(),
                                        r.getPayment().getAmount(),
                                        r.getStatus(),
                                        r.getReason(),
                                        r.getReviewNote(),
                                        r.getRequestedBy().getFullName(),
                                        r.getReviewedBy() == null
                                                ? null
                                                : r.getReviewedBy().getFullName()))
                .toList();
    }

    private void requireManager() {
        if (!List.of(Role.ADMIN, Role.BRANCH_MANAGER).contains(current.getCurrentUser().getRole()))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Chỉ quản lý được xử lý khoản thu nhầm.");
    }
}
