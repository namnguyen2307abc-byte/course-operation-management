package com.talent.management.features.tuition_payment.service;

import static com.talent.management.features.tuition_payment.service.TuitionPlanService.*;

import com.talent.management.features.tuition_payment.dto.request.InstallmentRequests.*;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.*;
import com.talent.management.features.tuition_payment.repository.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlanChangeService {
    private final TuitionPlanService ledger;
    private final TuitionInstallmentRepository installments;
    private final PaymentAllocationRepository allocations;
    private final TuitionPlanChangeRepository changes;
    private final CurrentUserService current;

    @Transactional
    public void request(Long invoiceId, Change input) {
        Invoice invoice = ledger.lock(invoiceId);
        TuitionPlan plan = ledger.ensure(invoice);
        TuitionInstallment due = installments.findById(input.installmentId()).orElseThrow();
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
        change.setExpectedPaid(ledger.paid(invoice));
        change.setOldDate(due.getDueDate());
        change.setProposedDate(input.proposedDate());
        change.setReason(input.reason());
        change.setRequestedBy(current.getCurrentUser());
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
    public void review(Long id, Review input) {
        TuitionPlanChange snapshot = changes.findById(id).orElseThrow();
        Invoice invoice = ledger.lock(snapshot.getPlan().getInvoice().getId());
        TuitionPlanChange change = changes.findById(id).orElseThrow();
        if (!change.getStatus().equals("PENDING")) throw conflict("Đề nghị đã được xử lý.");
        if (input.approve()) {
            if (change.getExpectedRevision() != change.getPlan().getRevision()
                    || ledger.paid(invoice).compareTo(change.getExpectedPaid()) != 0)
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
                            .orElseThrow());
        }
        change.setStatus(input.approve() ? "APPROVED" : "REJECTED");
        change.setReviewNote(input.note());
        change.setReviewedBy(current.getCurrentUser());
        change.setReviewedAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
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
