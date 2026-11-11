package com.talent.management.features.tuition_payment.service;

import static com.talent.management.features.tuition_payment.service.TuitionPlanService.*;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.*;
import com.talent.management.features.tuition_payment.repository.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TuitionNotificationService {
    private final TuitionPlanService ledger;
    private final TuitionNoticeRepository notices;
    private final TuitionInstallmentRepository installments;
    private final PaymentAllocationRepository allocations;
    private final CurrentUserService current;
    private final TuitionIntentRepository intents;

    @Transactional
    public List<NoticeView> mine() {
        for (InvoiceView invoice : ledger.list()) generate(invoice.id());
        return notices.findByRecipientIdOrderByIdDesc(current.getCurrentUser().getId()).stream()
                .filter(
                        n ->
                                n.getInstallment().getPlan().getInvoice().getStudent().getParent()
                                                != null
                                        && n.getInstallment()
                                                .getPlan()
                                                .getInvoice()
                                                .getStudent()
                                                .getParent()
                                                .getId()
                                                .equals(current.getCurrentUser().getId()))
                .filter(
                        n ->
                                n.getRevision() == n.getInstallment().getPlan().getRevision()
                                        && !n.getInstallment()
                                                .getPlan()
                                                .getInvoice()
                                                .getStatus()
                                                .name()
                                                .equals("CANCELLED")
                                        && allocations
                                                        .paid(n.getInstallment().getId())
                                                        .compareTo(
                                                                n.getInstallment().getAmountDue())
                                                < 0)
                .filter(
                        n ->
                                n.getType()
                                        .equals(
                                                n.getInstallment().getDueDate().isBefore(today())
                                                        ? "OVERDUE"
                                                        : n.getInstallment()
                                                                        .getDueDate()
                                                                        .equals(today())
                                                                ? "DUE_TODAY"
                                                                : "UPCOMING"))
                .map(
                        n ->
                                new NoticeView(
                                        n.getId(),
                                        n.getInstallment().getPlan().getInvoice().getId(),
                                        n.getInstallment()
                                                .getPlan()
                                                .getInvoice()
                                                .getStudent()
                                                .getFullName(),
                                        n.getInstallment().getPlan().getInvoice().getInvoiceCode(),
                                        n.getInstallment().getDueDate(),
                                        n.getInstallment()
                                                .getAmountDue()
                                                .subtract(
                                                        allocations.paid(
                                                                n.getInstallment().getId())),
                                        n.getType(),
                                        n.isRead(),
                                        n.getInstallment().getSequenceNumber(),
                                        mapIntent(n.getPaymentIntent())))
                .toList();
    }

    @Transactional
    public void generate(Long invoiceId) {
        Invoice entity = ledger.lock(invoiceId);
        User parent = entity.getStudent().getParent();
        if (parent == null) return;
        TuitionPlan plan = ledger.ensure(entity);
        InvoiceView invoice = ledger.map(entity, plan);
        if (!invoice.confirmed()
                || invoice.reviewRequired()
                || invoice.status().equals("CANCELLED")) return;
        for (Due due : invoice.installments()) {
            if (due.remaining().signum() == 0 || due.date().isAfter(today().plusDays(3))) continue;
            String type =
                    due.overdue()
                            ? "OVERDUE"
                            : due.date().equals(today()) ? "DUE_TODAY" : "UPCOMING";
            String key = parent.getId() + "-" + due.id() + "-" + invoice.revision() + "-" + type;
            if (!notices.existsByEventKey(key)) {
                TuitionNotice notice = new TuitionNotice();
                notice.setRecipient(parent);
                notice.setInstallment(installments.findById(due.id()).orElseThrow());
                notice.setRevision(invoice.revision());
                notice.setType(type);
                notice.setEventKey(key);
                notices.saveAndFlush(notice);
            }
        }
    }

    private IntentView mapIntent(TuitionPaymentIntent intent) {
        if (intent == null
                || !List.of("PENDING", "UNCERTAIN", "CREATING").contains(intent.getStatus()))
            return null;
        return new IntentView(
                intent.getId(),
                intent.getOrderCode(),
                intent.getExpectedAmount(),
                intent.getStatus(),
                intent.getCheckoutUrl(),
                intent.getQrCode(),
                intent.isSimulated());
    }

    @Transactional
    public Long pendingQrNotice(Long invoiceId) {
        Invoice invoice = ledger.lock(invoiceId);
        TuitionPlan plan = ledger.ensure(invoice);
        InvoiceView view = ledger.map(invoice, plan);
        if (!view.confirmed() || view.reviewRequired() || view.status().equals("CANCELLED"))
            return null;
        Due due =
                view.installments().stream()
                        .filter(d -> d.remaining().signum() > 0)
                        .findFirst()
                        .orElse(null);
        if (due == null || due.date().isAfter(today())) return null;
        return notices.findByInstallmentPlanInvoiceIdOrderByIdDesc(invoiceId).stream()
                .filter(
                        n ->
                                n.getRevision() == plan.getRevision()
                                        && n.getInstallment().getId().equals(due.id())
                                        && n.getType()
                                                .equals(due.overdue() ? "OVERDUE" : "DUE_TODAY")
                                        && n.getPaymentIntent() == null)
                .map(TuitionNotice::getId)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void attachQr(Long noticeId, Long intentId) {
        TuitionNotice notice = notices.findById(noticeId).orElseThrow();
        Invoice invoice = ledger.lock(notice.getInstallment().getPlan().getInvoice().getId());
        TuitionPlan plan = ledger.ensure(invoice);
        // Load via the same invoice snapshot: never attach a QR from a different family/invoice.
        IntentView view =
                ledger.map(invoice, plan).intents().stream()
                        .filter(i -> i.id().equals(intentId))
                        .findFirst()
                        .orElse(null);
        if (view == null || plan.getRevision() != notice.getRevision()) return;
        notice.setPaymentIntent(intents.getReferenceById(intentId));
    }

    @Transactional
    public void markRead(Long id) {
        TuitionNotice n =
                notices.findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                NOT_FOUND, "Không tìm thấy thông báo."));
        ledger.checkParent(n.getInstallment().getPlan().getInvoice());
        if (!n.getRecipient().getId().equals(current.getCurrentUser().getId()))
            throw new ResponseStatusException(NOT_FOUND);
        n.setRead(true);
    }
}
