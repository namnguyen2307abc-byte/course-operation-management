package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;
import static com.talent.management.features.tuition_payment.support.TuitionDates.today;

import com.talent.management.features.tuition_payment.dto.request.RequestDueDateChangeRequest;
import com.talent.management.features.tuition_payment.dto.request.ReviewTuitionRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.ChangeView;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.repository.PaymentAllocationRepository;
import com.talent.management.features.tuition_payment.repository.TuitionInstallmentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionPlanChangeRepository;
import com.talent.management.features.tuition_payment.service.PlanChangeService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.TuitionInstallment;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.TuitionPlanChange;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanChangeServiceImpl implements PlanChangeService {
    private final TuitionLedgerService tuitionLedgerService;
    private final TuitionInstallmentRepository installments;
    private final PaymentAllocationRepository allocations;
    private final TuitionPlanChangeRepository changes;
    private final CurrentUserService currentUserService;

    @Transactional
    @Override
    public void request(Long invoiceId, RequestDueDateChangeRequest input) {
        Invoice invoice = tuitionLedgerService.lock(invoiceId);
        TuitionPlan plan = tuitionLedgerService.ensure(invoice);
        TuitionInstallment due =
                installments
                        .findById(input.installmentId())
                        .orElseThrow(TuitionOperationException::notFound);
        if (!due.getPlan().getId().equals(plan.getId())
                || plan.isReviewRequired()
                || invoice.getStatus().name().equals("CANCELLED")
                || allocations.paid(due.getId()).compareTo(due.getAmountDue()) >= 0
                || changes.existsByInstallmentIdAndStatus(due.getId(), "PENDING")) {
            throw conflict("Kỳ đóng không hợp lệ, đã đóng đủ hoặc đã có đề nghị chờ duyệt.");
        }
        validateDate(plan, due, input.proposedDate());
        TuitionPlanChange change = new TuitionPlanChange();
        change.setPlan(plan);
        change.setInstallment(due);
        change.setExpectedRevision(plan.getRevision());
        change.setExpectedPaid(tuitionLedgerService.paid(invoice));
        change.setOldDate(due.getDueDate());
        change.setProposedDate(input.proposedDate());
        change.setReason(input.reason());
        change.setRequestedBy(currentUserService.getCurrentUser());
        changes.save(change);
    }

    private void validateDate(TuitionPlan plan, TuitionInstallment due, LocalDate date) {
        if (date == null || date.isBefore(today()) || !date.isAfter(due.getDueDate()))
            throw conflict("Ngày gia hạn phải sau hạn cũ và từ hôm nay.");
        LocalDate end =
                plan.getInvoice().getEnrollment() == null
                        ? null
                        : plan.getInvoice().getEnrollment().getClassEntity().getEndDate();
        if (end != null && date.isAfter(end))
            throw conflict("Ngày gia hạn vượt quá thời gian khóa học.");
        for (TuitionInstallment other :
                installments.findByPlanIdOrderBySequenceNumber(plan.getId())) {
            if (other.getSequenceNumber() < due.getSequenceNumber()
                            && !other.getDueDate().isBefore(date)
                    || other.getSequenceNumber() > due.getSequenceNumber()
                            && !other.getDueDate().isAfter(date))
                throw conflict("Ngày kỳ đóng cần tăng dần.");
        }
    }

    @Transactional
    @Override
    public void review(Long id, ReviewTuitionRequest input) {
        TuitionPlanChange snapshot =
                changes.findById(id).orElseThrow(TuitionOperationException::notFound);
        Invoice invoice = tuitionLedgerService.lock(snapshot.getPlan().getInvoice().getId());
        TuitionPlanChange change =
                changes.findById(id).orElseThrow(TuitionOperationException::notFound);
        if (!change.getStatus().equals("PENDING")) throw conflict("Đề nghị đã được xử lý.");
        if (input.approve()) {
            if (change.getExpectedRevision() != change.getPlan().getRevision()
                    || tuitionLedgerService.paid(invoice).compareTo(change.getExpectedPaid()) != 0)
                throw conflict("Có giao dịch hoặc lịch mới. Từ chối đề nghị cũ rồi lập lại.");
            validateDate(change.getPlan(), change.getInstallment(), change.getProposedDate());
            change.getInstallment().setDueDate(change.getProposedDate());
            change.getPlan().setRevision(change.getPlan().getRevision() + 1);
            invoice.setDueDate(
                    installments
                            .findByPlanIdOrderBySequenceNumber(change.getPlan().getId())
                            .stream()
                            .map(TuitionInstallment::getDueDate)
                            .max(LocalDate::compareTo)
                            .orElseThrow(TuitionOperationException::notFound));
        }
        change.setStatus(input.approve() ? "APPROVED" : "REJECTED");
        change.setReviewNote(input.note());
        change.setReviewedBy(currentUserService.getCurrentUser());
        change.setReviewedAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    @Override
    public List<ChangeView> list() {
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
