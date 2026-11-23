package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;
import static com.talent.management.features.tuition_payment.support.TuitionDates.today;

import com.talent.management.features.tuition_payment.dto.request.RequestDueDateChangeRequest;
import com.talent.management.features.tuition_payment.dto.request.ReviewTuitionRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.ChangeView;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.exception.TuitionValidationException;
import com.talent.management.features.tuition_payment.policy.DueDateExtensionPolicy;
import com.talent.management.features.tuition_payment.repository.PaymentAllocationRepository;
import com.talent.management.features.tuition_payment.repository.TuitionInstallmentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionIntentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionPlanChangeRepository;
import com.talent.management.features.tuition_payment.service.PaymentIntentService;
import com.talent.management.features.tuition_payment.service.PlanChangeService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.TuitionInstallment;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.TuitionPlanChange;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanChangeServiceImpl implements PlanChangeService {
    private final TuitionLedgerService tuitionLedgerService;
    private final TuitionInstallmentRepository installments;
    private final PaymentAllocationRepository allocations;
    private final TuitionPlanChangeRepository changes;
    private final CurrentUserService currentUserService;
    private final TuitionIntentRepository paymentRequests;
    private final PaymentIntentService paymentIntents;
    private final TransactionTemplate transactions;

    private void requireStaff() {
        var role = currentUserService.getCurrentUser().getRole();
        if (!List.of(Role.ADMIN, Role.STAFF, Role.BRANCH_MANAGER).contains(role))
            throw new TuitionOperationException(
                    HttpStatus.FORBIDDEN, "Chỉ Staff hoặc Admin được gia hạn kỳ thanh toán.");
    }

    private void validatePeriod(Invoice invoice, TuitionPlan plan, TuitionInstallment due) {
        if (!due.getPlan().getId().equals(plan.getId()))
            throw conflict("Kỳ thanh toán không thuộc hóa đơn này.");
        if (invoice.getStatus().name().equals("CANCELLED"))
            throw conflict("Hóa đơn đã hủy, không thể gia hạn.");
        if (plan.getCreatedBy() == null) throw conflict("Phải chốt lịch đóng trước khi gia hạn.");
        if (plan.isReviewRequired())
            throw conflict("Công nợ chưa được xác minh, không thể gia hạn.");
        if (allocations.paid(due.getId()).signum() > 0)
            throw conflict(
                    "Kỳ "
                            + due.getSequenceNumber()
                            + " đã có khoản thanh toán, không được gia hạn.");
    }

    private record ExtensionSnapshot(long revision, java.math.BigDecimal paid, LocalDate oldDate) {}

    @Override
    public void extend(Long invoiceId, RequestDueDateChangeRequest input) {
        requireStaff();
        if (input.reason() == null || input.reason().isBlank() || input.reason().length() > 500)
            throw new TuitionValidationException(
                    Map.of("reason", "Vui lòng nhập lý do gia hạn từ 1 đến 500 ký tự."));
        ExtensionSnapshot snapshot =
                transactions.execute(
                        tx -> {
                            Invoice invoice = tuitionLedgerService.lock(invoiceId);
                            TuitionPlan plan = tuitionLedgerService.ensure(invoice);
                            var due =
                                    installments
                                            .findById(input.installmentId())
                                            .orElseThrow(TuitionOperationException::notFound);
                            validatePeriod(invoice, plan, due);
                            validateDate(plan, due, input.proposedDate());
                            return new ExtensionSnapshot(
                                    plan.getRevision(),
                                    tuitionLedgerService.paid(invoice),
                                    due.getDueDate());
                        });
        // No database locks are held while verifying/cancelling unpaid gateway attempts.
        paymentIntents.closeUnpaidRequests(invoiceId);
        transactions.executeWithoutResult(
                tx -> {
                    Invoice invoice = tuitionLedgerService.lock(invoiceId);
                    TuitionPlan plan = tuitionLedgerService.ensure(invoice);
                    var due =
                            installments
                                    .findById(input.installmentId())
                                    .orElseThrow(TuitionOperationException::notFound);
                    if (snapshot.revision() != plan.getRevision()
                            || snapshot.paid().compareTo(tuitionLedgerService.paid(invoice)) != 0
                            || !snapshot.oldDate().equals(due.getDueDate()))
                        throw conflict(
                                "Có giao dịch hoặc lịch mới. Chưa đổi hạn đóng; vui lòng tải lại"
                                    + " hóa đơn.");
                    validatePeriod(invoice, plan, due);
                    validateDate(plan, due, input.proposedDate());
                    if (paymentRequests.existsByInvoiceIdAndStatusIn(
                            invoiceId, List.of("CREATING", "PENDING", "UNCERTAIN", "RECONCILE")))
                        throw conflict(
                                "Có yêu cầu PayOS mới hoặc chưa xác minh. Chưa đổi hạn đóng; vui"
                                    + " lòng thử lại sau khi xác minh yêu cầu thanh toán.");
                    var actor = currentUserService.getCurrentUser();
                    var now = LocalDateTime.now();
                    var history = new TuitionPlanChange();
                    history.setPlan(plan);
                    history.setInstallment(due);
                    history.setExpectedRevision(plan.getRevision());
                    history.setExpectedPaid(snapshot.paid());
                    history.setOldDate(due.getDueDate());
                    history.setProposedDate(input.proposedDate());
                    history.setReason(input.reason().trim());
                    history.setRequestedBy(actor);
                    history.setStatus("APPROVED");
                    history.setReviewedBy(actor);
                    history.setReviewedAt(now);
                    history.setReviewNote(
                            "Gia hạn trực tiếp bởi Staff/Admin, không qua phê duyệt.");
                    due.setDueDate(input.proposedDate());
                    plan.setRevision(plan.getRevision() + 1);
                    invoice.setDueDate(
                            installments.findByPlanIdOrderBySequenceNumber(plan.getId()).stream()
                                    .map(TuitionInstallment::getDueDate)
                                    .max(LocalDate::compareTo)
                                    .orElseThrow());
                    changes.save(history);
                });
    }

    @Override
    public void request(Long invoiceId, RequestDueDateChangeRequest input) {
        extend(invoiceId, input);
    }

    private void validateDate(TuitionPlan plan, TuitionInstallment due, LocalDate date) {
        LocalDate end =
                plan.getInvoice().getEnrollment() == null
                        ? null
                        : plan.getInvoice().getEnrollment().getClassEntity().getEndDate();
        DueDateExtensionPolicy.validate(
                due,
                installments.findByPlanIdOrderBySequenceNumber(plan.getId()),
                date,
                today(),
                end);
    }

    @Override
    public void review(Long id, ReviewTuitionRequest input) {
        requireStaff();
        throw new TuitionOperationException(
                HttpStatus.GONE,
                "TUITION_EXTENSION_REVIEW_REMOVED",
                "Gia hạn được áp dụng trực tiếp bởi Staff/Admin. Không còn bước duyệt đề nghị.");
    }

    @Transactional(readOnly = true)
    @Override
    public List<ChangeView> list() {
        requireStaff();
        return changes.findAllByOrderByIdDesc().stream()
                .map(
                        c ->
                                new ChangeView(
                                        c.getId(),
                                        c.getPlan().getInvoice().getId(),
                                        c.getPlan().getInvoice().getInvoiceCode(),
                                        c.getPlan().getInvoice().getStudent().getFullName(),
                                        c.getInstallment().getSequenceNumber(),
                                        c.getOldDate(),
                                        c.getProposedDate(),
                                        c.getReason(),
                                        c.getStatus(),
                                        c.getRequestedBy().getFullName(),
                                        c.getReviewedBy() == null
                                                ? null
                                                : c.getReviewedBy().getFullName(),
                                        c.getReviewNote()))
                .toList();
    }
}
