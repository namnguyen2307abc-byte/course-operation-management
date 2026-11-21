package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;

import com.talent.management.features.tuition_payment.dto.request.ReviewTuitionRequest;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.repository.PaymentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionReversalRepository;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.features.tuition_payment.service.TuitionReversalService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.Payment;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.TuitionReversal;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.InvoiceStatus;
import com.talent.management.shared.enums.PaymentMethod;
import com.talent.management.shared.enums.PaymentStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TuitionReversalServiceImpl implements TuitionReversalService {
    private final TuitionLedgerService tuitionLedgerService;
    private final PaymentRepository payments;
    private final TuitionReversalRepository reversals;
    private final CurrentUserService currentUserService;

    @Transactional
    @Override
    public void request(Long paymentId, String reason) {
        User actor = currentUserService.getCurrentUser();
        if (!List.of(Role.STAFF, Role.ADMIN, Role.BRANCH_MANAGER).contains(actor.getRole()))
            throw new TuitionOperationException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Chỉ nhân viên hoặc quản lý được đề nghị điều chỉnh khoản thu.");
        Payment payment =
                payments.findById(paymentId).orElseThrow(TuitionOperationException::notFound);
        tuitionLedgerService.lock(payment.getInvoice().getId());
        if (actor.getRole() != Role.ADMIN
                && (payment.getCashier() == null
                        || !actor.getId().equals(payment.getCashier().getId())))
            throw new TuitionOperationException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Bạn chỉ được đề nghị điều chỉnh giao dịch tiền mặt do chính mình thu.");
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
        r.setRequestedBy(actor);
        reversals.save(r);
    }

    @Transactional
    @Override
    public void review(Long id, ReviewTuitionRequest input) {
        requireManager();
        TuitionReversal snapshot =
                reversals.findById(id).orElseThrow(TuitionOperationException::notFound);
        Invoice invoice = tuitionLedgerService.lock(snapshot.getPayment().getInvoice().getId());
        TuitionReversal r = reversals.findById(id).orElseThrow(TuitionOperationException::notFound);
        TuitionPlan plan = tuitionLedgerService.ensure(invoice);
        if (plan.isReviewRequired() || !r.getStatus().equals("PENDING"))
            throw conflict("Đề nghị đã xử lý hoặc hóa đơn đang cần đối chiếu.");
        if (r.getRequestedBy().getId().equals(currentUserService.getCurrentUser().getId()))
            throw conflict("Người đề nghị không được tự duyệt đảo giao dịch.");
        r.setStatus(input.approve() ? "APPROVED" : "REJECTED");
        r.setReviewedBy(currentUserService.getCurrentUser());
        r.setReviewNote(input.note());
        r.setReviewedAt(LocalDateTime.now());
        reversals.saveAndFlush(r);
        if (input.approve()) {
            BigDecimal paid = tuitionLedgerService.paid(invoice);
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
    @Override
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
        if (!List.of(Role.ADMIN, Role.BRANCH_MANAGER)
                .contains(currentUserService.getCurrentUser().getRole()))
            throw new TuitionOperationException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Chỉ quản lý được xử lý khoản thu nhầm.");
    }
}
