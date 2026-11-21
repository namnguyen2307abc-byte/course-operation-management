package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;
import static com.talent.management.features.tuition_payment.support.TuitionDates.today;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.repository.PaymentAllocationRepository;
import com.talent.management.features.tuition_payment.repository.PaymentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionInstallmentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionLedgerRepository;
import com.talent.management.features.tuition_payment.repository.TuitionPlanRepository;
import com.talent.management.features.tuition_payment.repository.TuitionReversalRepository;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.Payment;
import com.talent.management.shared.entity.PaymentAllocation;
import com.talent.management.shared.entity.TuitionInstallment;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.enums.InvoiceStatus;
import com.talent.management.shared.enums.PaymentStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TuitionLedgerServiceImpl implements TuitionLedgerService {
    private final TuitionLedgerRepository ledgerRepository;
    private final TuitionPlanRepository plans;
    private final TuitionInstallmentRepository installments;
    private final PaymentAllocationRepository allocations;
    private final PaymentRepository payments;
    private final CurrentUserService currentUserService;
    private final TuitionReversalRepository reversals;

    @Override
    public Invoice lock(Long id) {
        return ledgerRepository
                .lockInvoice(id)
                .orElseThrow(
                        () ->
                                new TuitionOperationException(
                                        NOT_FOUND,
                                        "TUITION_INVOICE_NOT_FOUND",
                                        "Không tìm thấy hóa đơn."));
    }

    @Override
    public void checkParent(Invoice invoice) {
        User actor = currentUserService.getCurrentUser();
        if (actor.getRole() == Role.PARENT
                && (invoice.getStudent().getParent() == null
                        || !Objects.equals(
                                invoice.getStudent().getParent().getId(), actor.getId()))) {
            throw new TuitionOperationException(NOT_FOUND, "Không tìm thấy học phí của con bạn.");
        }
    }

    @Override
    public BigDecimal paid(Invoice invoice) {
        return ledgerRepository.sumSuccessfulTuition(invoice.getId());
    }

    @Override
    public BigDecimal balance(Invoice invoice) {
        return invoice.getFinalAmount().subtract(paid(invoice)).max(BigDecimal.ZERO);
    }

    @Override
    public void refreshUnpaidFullPlan(Invoice invoice) {
        plans.findByInvoiceId(invoice.getId())
                .ifPresent(
                        plan -> {
                            for (TuitionInstallment due :
                                    installments.findByPlanIdOrderBySequenceNumber(plan.getId())) {
                                due.setAmountDue(invoice.getFinalAmount());
                            }
                            plan.setRevision(plan.getRevision() + 1);
                        });
    }

    @Override
    public TuitionPlan ensure(Invoice invoice) {
        Optional<TuitionPlan> existing = plans.findByInvoiceId(invoice.getId());
        if (existing.isPresent()) return existing.get();
        TuitionPlan plan = new TuitionPlan();
        plan.setInvoice(invoice);
        BigDecimal received = paid(invoice);
        boolean mismatch =
                received.compareTo(invoice.getFinalAmount()) > 0
                        || (invoice.getStatus() == InvoiceStatus.PAID
                                && received.compareTo(invoice.getFinalAmount()) != 0)
                        || invoice.getFinalAmount().signum() < 0;
        plan.setReviewRequired(mismatch);
        if (mismatch)
            plan.setReviewReason(
                    "Dữ liệu thu cũ không khớp hóa đơn. Cần kiểm tra giao dịch gốc trước khi thu"
                            + " tiếp.");
        plans.save(plan);
        if (invoice.getFinalAmount().signum() > 0) {
            TuitionInstallment due = new TuitionInstallment();
            due.setPlan(plan);
            due.setSequenceNumber(1);
            due.setAmountDue(invoice.getFinalAmount());
            due.setDueDate(invoice.getDueDate() == null ? today() : invoice.getDueDate());
            installments.save(due);
        }
        if (!mismatch) {
            for (Payment payment : payments.findByInvoiceId(invoice.getId())) {
                if (payment.getStatus() == PaymentStatus.SUCCESS
                        && !reversals.existsByPaymentIdAndStatus(payment.getId(), "APPROVED"))
                    allocate(payment, plan);
            }
            if (received.signum() > 0 && invoice.getStatus() != InvoiceStatus.CANCELLED) {
                invoice.setStatus(
                        received.compareTo(invoice.getFinalAmount()) == 0
                                ? InvoiceStatus.PAID
                                : InvoiceStatus.PARTIALLY_PAID);
            }
        }
        return plan;
    }

    @Override
    public void requireCollectible(Invoice invoice, TuitionPlan plan, BigDecimal amount) {
        if (amount == null) throw conflict("Vui lòng nhập số tiền cần thanh toán.");
        if (amount.signum() <= 0)
            throw conflict(
                    "Số tiền thanh toán phải lớn hơn 0đ. Không được ghi nhận giao dịch hoặc biên"
                            + " lai 0đ.");
        if (plan.getCreatedBy() == null
                && paid(invoice).signum() == 0
                && invoice.getFinalAmount().signum() > 0) {
            throw conflict("Vui lòng chốt lịch đóng trước khi thanh toán.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED
                || invoice.getStatus() == InvoiceStatus.PAID
                || plan.isReviewRequired()) {
            throw conflict("Hóa đơn đã hủy hoặc đang cần đối chiếu.");
        }
        validateMoney(amount);
        BigDecimal remaining = balance(invoice);
        if (amount.compareTo(remaining) > 0
                || (remaining.signum() > 0 && amount.signum() == 0)
                || (remaining.signum() == 0 && invoice.getFinalAmount().signum() != 0)) {
            throw conflict("Số tiền thu phải lớn hơn 0 và không vượt số còn thiếu.");
        }
        BigDecimal received = paid(invoice);
        BigDecimal first =
                installments.findByPlanIdOrderBySequenceNumber(plan.getId()).get(0).getAmountDue();
        if (received.signum() == 0
                && amount.compareTo(first) < 0
                && (invoice.getEnrollment() == null
                        || invoice.getEnrollment().getStatus()
                                == EnrollmentStatus.PENDING_PAYMENT)) {
            throw conflict("Lần đầu cần đóng đủ kỳ đầu: " + first.toPlainString() + " đ.");
        }
    }

    @Override
    public void validateMoney(BigDecimal amount) {
        if (amount == null
                || amount.signum() < 0
                || amount.stripTrailingZeros().scale() > 0
                || amount.compareTo(new BigDecimal("9999999999")) > 0) {
            throw conflict("Số tiền VND phải là số nguyên không âm trong giới hạn cho phép.");
        }
    }

    @Override
    public void requireCurrentInstallment(Invoice invoice, TuitionPlan plan, BigDecimal amount) {
        requireCollectible(invoice, plan, amount);
        BigDecimal expected =
                installments.findByPlanIdOrderBySequenceNumber(plan.getId()).stream()
                        .map(
                                due ->
                                        due.getAmountDue()
                                                .subtract(allocations.paid(due.getId()))
                                                .max(BigDecimal.ZERO))
                        .filter(left -> left.signum() > 0)
                        .findFirst()
                        .orElse(BigDecimal.ZERO);
        if (amount.compareTo(expected) != 0)
            throw new TuitionOperationException(
                    CONFLICT,
                    "PAYMENT_AMOUNT_CHANGED",
                    "Số tiền thanh toán cố định theo đợt còn thiếu: "
                            + expected.toPlainString()
                            + "đ. Vui lòng tải lại hóa đơn.");
        if (ledgerRepository.hasIntents(invoice.getId(), List.of("RECONCILE")))
            throw conflict("Có tiền ngân hàng cần xác minh trước khi tiếp tục thu.");
    }

    @Override
    public void allocate(Payment payment, TuitionPlan plan) {
        BigDecimal left = payment.getAmount();
        for (TuitionInstallment due :
                installments.findByPlanIdOrderBySequenceNumber(plan.getId())) {
            BigDecimal portion =
                    due.getAmountDue()
                            .subtract(allocations.paid(due.getId()))
                            .max(BigDecimal.ZERO)
                            .min(left);
            if (portion.signum() > 0) {
                PaymentAllocation allocation = new PaymentAllocation();
                allocation.setPayment(payment);
                allocation.setInstallment(due);
                allocation.setAmount(portion);
                allocations.saveAndFlush(allocation);
                left = left.subtract(portion);
            }
        }
        if (left.signum() != 0) throw conflict("Không phân bổ được toàn bộ số tiền vào lịch đóng.");
    }
}
