package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.support.TuitionDates.today;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.notification.repository.NotificationRepository;
import com.talent.management.features.notification.service.NotificationService;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.Due;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.IntentView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.NoticeView;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.repository.PaymentAllocationRepository;
import com.talent.management.features.tuition_payment.repository.TuitionInstallmentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionIntentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionNoticeRepository;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.features.tuition_payment.service.TuitionNotificationService;
import com.talent.management.features.tuition_payment.service.TuitionQueryService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.TuitionNotice;
import com.talent.management.shared.entity.TuitionPaymentIntent;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TuitionNotificationServiceImpl implements TuitionNotificationService {
    private final TuitionLedgerService tuitionLedgerService;
    private final TuitionQueryService tuitionQueryService;
    private final TuitionNoticeRepository notices;
    private final TuitionInstallmentRepository installments;
    private final PaymentAllocationRepository allocations;
    private final CurrentUserService currentUserService;
    private final TuitionIntentRepository intents;
    private final NotificationRepository bellNotifications;
    private final NotificationService notificationService;

    @Transactional
    @Override
    public List<NoticeView> mine() {
        for (InvoiceView invoice : tuitionQueryService.list()) generate(invoice.id());
        return notices
                .findByRecipientIdOrderByIdDesc(currentUserService.getCurrentUser().getId())
                .stream()
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
                                                .equals(
                                                        currentUserService
                                                                .getCurrentUser()
                                                                .getId()))
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
    @Override
    public void generate(Long invoiceId) {
        Invoice entity = tuitionLedgerService.lock(invoiceId);
        User parent = entity.getStudent().getParent();
        if (parent == null) return;
        TuitionPlan plan = tuitionLedgerService.ensure(entity);
        InvoiceView invoice = tuitionQueryService.map(entity, plan);
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
            TuitionNotice notice = notices.findByEventKey(key).orElse(null);
            if (notice == null) {
                notice = new TuitionNotice();
                notice.setRecipient(parent);
                notice.setInstallment(
                        installments
                                .findById(due.id())
                                .orElseThrow(TuitionOperationException::notFound));
                notice.setRevision(invoice.revision());
                notice.setType(type);
                notice.setEventKey(key);
                notices.saveAndFlush(notice);
            }
            // The invoice lock serializes the scheduler and parent reads. Also bridge notices
            // created before the shared bell was integrated, without resending read notices.
            String bellType = "TUITION_" + type;
            if (!bellNotifications.existsByRecipientIdAndTypeAndReferenceId(
                    parent.getId(), bellType, notice.getId())) {
                String title =
                        switch (type) {
                            case "OVERDUE" -> "Học phí đã quá hạn";
                            case "DUE_TODAY" -> "Học phí đến hạn hôm nay";
                            default -> "Sắp đến hạn đóng học phí";
                        };
                String message =
                        String.format(
                                Locale.forLanguageTag("vi-VN"),
                                "%s - %s: đợt %d còn phải đóng %,.0f đ, hạn %s. Mở hóa đơn để xem"
                                        + " lịch đóng và thanh toán.",
                                invoice.student(),
                                invoice.code(),
                                due.sequence(),
                                due.remaining(),
                                due.date()
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "dd/MM/yyyy")));
                notificationService.sendNotification(
                        parent,
                        null,
                        title,
                        message,
                        bellType,
                        notice.getId(),
                        "/pages/parent-tuition.html?invoiceId=" + invoiceId);
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
    @Override
    public Long pendingQrNotice(Long invoiceId) {
        Invoice invoice = tuitionLedgerService.lock(invoiceId);
        TuitionPlan plan = tuitionLedgerService.ensure(invoice);
        InvoiceView view = tuitionQueryService.map(invoice, plan);
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
    @Override
    public void attachQr(Long noticeId, Long intentId) {
        TuitionNotice notice =
                notices.findById(noticeId).orElseThrow(TuitionOperationException::notFound);
        Invoice invoice =
                tuitionLedgerService.lock(notice.getInstallment().getPlan().getInvoice().getId());
        TuitionPlan plan = tuitionLedgerService.ensure(invoice);
        // Load via the same invoice snapshot: never attach a QR from a different family/invoice.
        IntentView view =
                tuitionQueryService.map(invoice, plan).intents().stream()
                        .filter(i -> i.id().equals(intentId))
                        .findFirst()
                        .orElse(null);
        if (view == null || plan.getRevision() != notice.getRevision()) return;
        notice.setPaymentIntent(intents.getReferenceById(intentId));
    }

    @Transactional
    @Override
    public void markRead(Long id) {
        TuitionNotice n =
                notices.findById(id)
                        .orElseThrow(
                                () ->
                                        new TuitionOperationException(
                                                NOT_FOUND, "Không tìm thấy thông báo."));
        tuitionLedgerService.checkParent(n.getInstallment().getPlan().getInvoice());
        if (!n.getRecipient().getId().equals(currentUserService.getCurrentUser().getId()))
            throw new TuitionOperationException(
                    NOT_FOUND, "TUITION_NOTICE_NOT_FOUND", "Không tìm thấy thông báo của bạn.");
        n.setRead(true);
    }
}
