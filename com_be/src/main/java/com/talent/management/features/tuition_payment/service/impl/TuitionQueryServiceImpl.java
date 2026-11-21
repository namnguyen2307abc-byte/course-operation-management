package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.support.TuitionDates.today;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.Due;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.IntentView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.OverdueContact;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.Receipt;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.ReservationAction;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.mapper.PaymentReference;
import com.talent.management.features.tuition_payment.repository.PaymentAllocationRepository;
import com.talent.management.features.tuition_payment.repository.PaymentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionInstallmentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionIntentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionLedgerRepository;
import com.talent.management.features.tuition_payment.repository.TuitionPlanRepository;
import com.talent.management.features.tuition_payment.repository.TuitionReversalRepository;
import com.talent.management.features.tuition_payment.service.TuitionDiscountService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.features.tuition_payment.service.TuitionQueryService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.TuitionInstallment;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.enums.PaymentStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TuitionQueryServiceImpl implements TuitionQueryService {
    private final TuitionDiscountService discountService;
    private final TuitionLedgerRepository ledgerRepository;
    private final TuitionPlanRepository plans;
    private final TuitionInstallmentRepository installments;
    private final PaymentAllocationRepository allocations;
    private final PaymentRepository payments;
    private final CurrentUserService currentUserService;
    private final TuitionReversalRepository reversals;
    private final TuitionIntentRepository intents;
    private final TuitionLedgerService tuitionLedgerService;

    @Transactional(readOnly = true)
    @Override
    public BigDecimal outstanding(LocalDate overdueBefore) {
        BigDecimal total = BigDecimal.ZERO;
        for (Invoice invoice : ledgerRepository.findOutstandingInvoices()) {
            Optional<TuitionPlan> plan = plans.findByInvoiceId(invoice.getId());
            if (plan.isPresent()) {
                for (TuitionInstallment due :
                        installments.findByPlanIdOrderBySequenceNumber(plan.get().getId())) {
                    if (overdueBefore == null || due.getDueDate().isBefore(overdueBefore)) {
                        total =
                                total.add(
                                        due.getAmountDue()
                                                .subtract(allocations.paid(due.getId()))
                                                .max(BigDecimal.ZERO));
                    }
                }
            } else if (overdueBefore == null
                    || (invoice.getDueDate() != null
                            && invoice.getDueDate().isBefore(overdueBefore))) {
                total = total.add(tuitionLedgerService.balance(invoice));
            }
        }
        return total;
    }

    @Transactional
    @Override
    public InvoiceView view(Long id) {
        Invoice invoice = tuitionLedgerService.lock(id);
        tuitionLedgerService.checkParent(invoice);
        return map(invoice, tuitionLedgerService.ensure(invoice));
    }

    @Transactional
    @Override
    public List<InvoiceView> list() {
        User actor = currentUserService.getCurrentUser();
        List<Invoice> invoices =
                actor.getRole() == Role.PARENT
                        ? ledgerRepository.findInvoicesForParent(actor.getId())
                        : ledgerRepository.findAllInvoicesNewestFirst();
        return invoices.stream().map(i -> view(i.getId())).toList();
    }

    @Transactional
    @Override
    public List<OverdueContact> overdueContacts() {
        if (currentUserService.getCurrentUser().getRole() != Role.ADMIN)
            throw new TuitionOperationException(
                    FORBIDDEN, "Chỉ admin được xem danh sách cần liên hệ.");
        return list().stream()
                .filter(i -> i.confirmed() && !i.status().equals("CANCELLED"))
                .flatMap(
                        i ->
                                i.installments().stream()
                                        .filter(
                                                d ->
                                                        d.remaining().signum() > 0
                                                                && java.time.temporal.ChronoUnit
                                                                                .DAYS
                                                                                .between(
                                                                                        d.date(),
                                                                                        today())
                                                                        >= 7)
                                        .map(
                                                d ->
                                                        new OverdueContact(
                                                                i.id(),
                                                                i.code(),
                                                                i.student(),
                                                                i.parent(),
                                                                i.phone(),
                                                                d.sequence(),
                                                                d.date(),
                                                                d.remaining(),
                                                                java.time.temporal.ChronoUnit.DAYS
                                                                        .between(
                                                                                d.date(),
                                                                                today()))))
                .toList();
    }

    @Override
    public InvoiceView map(Invoice invoice, TuitionPlan plan) {
        List<Due> schedule =
                installments.findByPlanIdOrderBySequenceNumber(plan.getId()).stream()
                        .map(
                                due -> {
                                    BigDecimal received = allocations.paid(due.getId());
                                    BigDecimal remaining =
                                            due.getAmountDue()
                                                    .subtract(received)
                                                    .max(BigDecimal.ZERO);
                                    return new Due(
                                            due.getId(),
                                            due.getSequenceNumber(),
                                            due.getAmountDue(),
                                            received,
                                            remaining,
                                            due.getDueDate(),
                                            remaining.signum() > 0
                                                    && due.getDueDate().isBefore(today()));
                                })
                        .toList();
        List<Receipt> receipts =
                payments.findByInvoiceId(invoice.getId()).stream()
                        .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                        .map(
                                p ->
                                        new Receipt(
                                                p.getId(),
                                                PaymentReference.receipt(p),
                                                p.getAmount(),
                                                p.getCashGiven(),
                                                p.getChangeAmount(),
                                                p.getPaymentMethod().name(),
                                                p.getCashier() == null
                                                        ? "PayOS tự động"
                                                        : p.getCashier().getFullName(),
                                                p.getPaymentDate(),
                                                p.getBalanceAfter(),
                                                reversals.existsByPaymentIdAndStatus(
                                                        p.getId(), "APPROVED"),
                                                p.getCashRoundingAmount(),
                                                p.getCashier() == null
                                                        ? null
                                                        : p.getCashier().getId()))
                        .toList();
        User parent = invoice.getStudent().getParent();
        return new InvoiceView(
                invoice.getId(),
                invoice.getInvoiceCode(),
                invoice.getStudent().getFullName(),
                parent == null ? "" : parent.getFullName(),
                parent == null ? "" : parent.getPhone(),
                invoice.getEnrollment() == null
                        ? ""
                        : invoice.getEnrollment().getClassEntity().getClassName(),
                invoice.getFinalAmount(),
                tuitionLedgerService.paid(invoice),
                tuitionLedgerService.balance(invoice),
                invoice.getStatus().name(),
                plan.getMode(),
                plan.getRevision(),
                plan.isReviewRequired(),
                plan.getReviewReason(),
                schedule.stream()
                        .anyMatch(
                                d ->
                                        d.remaining().signum() > 0
                                                && !d.date().plusDays(7).isAfter(today())),
                schedule,
                receipts,
                java.util.stream.Stream.of("CREATING", "PENDING", "UNCERTAIN", "RECONCILE")
                        .flatMap(
                                status ->
                                        intents
                                                .findByInvoiceIdAndStatus(invoice.getId(), status)
                                                .stream())
                        .map(
                                i ->
                                        new IntentView(
                                                i.getId(),
                                                i.getOrderCode(),
                                                i.getExpectedAmount(),
                                                i.getStatus(),
                                                i.getCheckoutUrl(),
                                                i.getQrCode(),
                                                i.isSimulated()))
                        .toList(),
                plan.getCreatedBy() != null || tuitionLedgerService.paid(invoice).signum() > 0,
                invoice.getOriginalAmount(),
                invoice.getDiscountAmount(),
                invoice.getDiscountReason(),
                invoice.getReservationDeadline(),
                false,
                invoice.getEnrollment() != null
                        && invoice.getEnrollment().getStatus() == EnrollmentStatus.PENDING_PAYMENT,
                ledgerRepository.findReservationActions(invoice.getId()).stream()
                        .map(
                                a ->
                                        new ReservationAction(
                                                a.getAction(),
                                                a.getReason(),
                                                a.getActor().getFullName(),
                                                a.getCreatedAt(),
                                                a.getOldDeadline(),
                                                a.getNewDeadline()))
                        .toList(),
                invoice.getEnrollment() == null || invoice.getEnrollment().getClassEntity() == null
                        ? null
                        : invoice.getEnrollment().getClassEntity().getEndDate(),
                discountService.preview(
                        invoice,
                        plan.getCreatedBy() != null
                                || tuitionLedgerService.paid(invoice).signum() > 0));
    }
}
