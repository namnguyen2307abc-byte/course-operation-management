package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;

import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.tuition_payment.dto.request.CancelReservationRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.repository.TuitionIntentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionLedgerRepository;
import com.talent.management.features.tuition_payment.service.PaymentIntentService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.features.tuition_payment.service.TuitionQueryService;
import com.talent.management.features.tuition_payment.service.TuitionReservationService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.TuitionPaymentIntent;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.TuitionReservationAction;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.EnrollmentRequestStatus;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.enums.InvoiceStatus;
import com.talent.management.shared.enums.Role;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TuitionReservationServiceImpl implements TuitionReservationService {
    private final TuitionLedgerRepository ledgerRepository;
    private final TuitionLedgerService tuitionLedgerService;
    private final TuitionQueryService tuitionQueryService;
    private final CurrentUserService currentUserService;
    private final TuitionIntentRepository intents;
    private final EnrollmentRequestRepository requests;
    private final PaymentIntentService paymentIntentService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public InvoiceView decide(Long invoiceId, CancelReservationRequest input) {
        User actor = currentUserService.getCurrentUser();
        if (actor.getRole() != Role.ADMIN)
            throw new TuitionOperationException(
                    HttpStatus.FORBIDDEN, "Chỉ admin được hủy hồ sơ chờ thanh toán.");
        if (!"CANCEL".equals(input.action()))
            throw conflict("Không còn chức năng gia hạn giữ chỗ.");
        if (!input.contactConfirmed()
                || input.reason() == null
                || input.reason().isBlank()
                || input.reason().length() > 500)
            throw conflict(
                    "Cần xác nhận đã liên hệ phụ huynh, kiểm tra chưa nhận tiền và nhập lý do"
                            + " hủy.");
        List<Long> pending =
                transactionTemplate.execute(
                        status -> {
                            Invoice invoice = tuitionLedgerService.lock(invoiceId);
                            validate(invoice);
                            if (List.of("CREATING", "UNCERTAIN", "RECONCILE").stream()
                                    .anyMatch(
                                            s ->
                                                    !intents.findByInvoiceIdAndStatus(invoiceId, s)
                                                            .isEmpty()))
                                throw conflict(
                                        "Ngân hàng/QR chưa rõ kết quả. Kiểm tra và xác nhận khoản"
                                                + " thu trước khi hủy hồ sơ.");
                            return intents.findByInvoiceIdAndStatus(invoiceId, "PENDING").stream()
                                    .map(TuitionPaymentIntent::getId)
                                    .toList();
                        });
        // Close unpaid paymentIntentService requests outside the database transaction; never
        // silently abandon
        // them.
        for (Long id : pending) paymentIntentService.cancel(id);
        return transactionTemplate.execute(
                status -> {
                    Invoice invoice = tuitionLedgerService.lock(invoiceId);
                    validate(invoice);
                    if (List.of("CREATING", "PENDING", "UNCERTAIN", "RECONCILE").stream()
                            .anyMatch(
                                    s -> !intents.findByInvoiceIdAndStatus(invoiceId, s).isEmpty()))
                        throw conflict("QR đã thay đổi. Vui lòng kiểm tra lại trước khi hủy.");
                    invoice.setStatus(InvoiceStatus.CANCELLED);
                    invoice.getEnrollment().setStatus(EnrollmentStatus.CANCELLED);
                    LocalDateTime cancelledAt = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
                    requests.findByEnrollmentId(invoice.getEnrollment().getId())
                            .ifPresent(
                                    r -> {
                                        r.setStatus(EnrollmentRequestStatus.CANCELLED);
                                        r.setReviewedBy(actor);
                                        r.setReviewedAt(cancelledAt);
                                        r.setReviewNote(input.reason().trim());
                                    });
                    TuitionPlan plan = tuitionLedgerService.ensure(invoice);
                    plan.setRevision(plan.getRevision() + 1);
                    TuitionReservationAction audit = new TuitionReservationAction();
                    audit.setInvoice(invoice);
                    audit.setActor(actor);
                    audit.setAction("CANCEL");
                    audit.setReason(input.reason().trim());
                    audit.setOldDeadline(invoice.getReservationDeadline());
                    audit.setCreatedAt(cancelledAt);
                    ledgerRepository.saveReservationAction(audit);
                    return tuitionQueryService.map(invoice, plan);
                });
    }

    private void validate(Invoice invoice) {
        if (invoice.getEnrollment() == null
                || invoice.getEnrollment().getStatus() != EnrollmentStatus.PENDING_PAYMENT
                || invoice.getStatus() == InvoiceStatus.CANCELLED
                || tuitionLedgerService.paid(invoice).signum() > 0)
            throw conflict("Chỉ hủy hồ sơ chưa nhận tiền và chưa ghi danh chính thức.");
        if (tuitionLedgerService.ensure(invoice).isReviewRequired())
            throw conflict("Dữ liệu tiền thu chưa khớp. Kiểm tra ngân hàng trước khi hủy.");
        if (ledgerRepository.hasUnpostedBankEvents(invoice.getId()))
            throw conflict(
                    "Ngân hàng đã báo tiền chưa được ghi nhận. Kiểm tra và xác nhận khoản thu trước"
                            + " khi hủy.");
    }
}
