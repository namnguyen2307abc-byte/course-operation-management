package com.talent.management.features.tuition_payment.service;

import static com.talent.management.features.tuition_payment.service.TuitionPlanService.conflict;

import com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository;
import com.talent.management.features.tuition_payment.dto.request.InstallmentRequests.Reservation;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;
import com.talent.management.features.tuition_payment.repository.TuitionIntentRepository;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TuitionReservationService {
    private final TuitionPlanService ledger;
    private final CurrentUserService current;
    private final TuitionIntentRepository intents;
    private final EnrollmentRequestRepository requests;
    private final InstallmentGatewayService gateway;
    private final TransactionTemplate tx;
    private final EntityManager em;

    public InvoiceView decide(Long invoiceId, Reservation input) {
        User actor = current.getCurrentUser();
        if (actor.getRole() != Role.ADMIN)
            throw new ResponseStatusException(
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
                tx.execute(
                        status -> {
                            Invoice invoice = ledger.lock(invoiceId);
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
        // Close unpaid gateway requests outside the database transaction; never silently abandon
        // them.
        for (Long id : pending) gateway.cancel(id);
        return tx.execute(
                status -> {
                    Invoice invoice = ledger.lock(invoiceId);
                    validate(invoice);
                    if (List.of("CREATING", "PENDING", "UNCERTAIN", "RECONCILE").stream()
                            .anyMatch(
                                    s -> !intents.findByInvoiceIdAndStatus(invoiceId, s).isEmpty()))
                        throw conflict("QR đã thay đổi. Vui lòng kiểm tra lại trước khi hủy.");
                    invoice.setStatus(InvoiceStatus.CANCELLED);
                    invoice.getEnrollment().setStatus(EnrollmentStatus.CANCELLED);
                    requests.findByEnrollmentId(invoice.getEnrollment().getId())
                            .ifPresent(r -> r.setStatus(EnrollmentRequestStatus.CANCELLED));
                    TuitionPlan plan = ledger.ensure(invoice);
                    plan.setRevision(plan.getRevision() + 1);
                    TuitionReservationAction audit = new TuitionReservationAction();
                    audit.setInvoice(invoice);
                    audit.setActor(actor);
                    audit.setAction("CANCEL");
                    audit.setReason(input.reason().trim());
                    audit.setOldDeadline(invoice.getReservationDeadline());
                    audit.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
                    em.persist(audit);
                    em.flush();
                    return ledger.map(invoice, plan);
                });
    }

    private void validate(Invoice invoice) {
        if (invoice.getEnrollment() == null
                || invoice.getEnrollment().getStatus() != EnrollmentStatus.PENDING_PAYMENT
                || invoice.getStatus() == InvoiceStatus.CANCELLED
                || ledger.paid(invoice).signum() > 0)
            throw conflict("Chỉ hủy hồ sơ chưa nhận tiền và chưa ghi danh chính thức.");
        if (ledger.ensure(invoice).isReviewRequired())
            throw conflict("Dữ liệu tiền thu chưa khớp. Kiểm tra ngân hàng trước khi hủy.");
        long money =
                em.createQuery(
                                "select count(e) from TuitionBankEvent e where"
                                    + " e.intent.invoice.id=:id and e.status='RECONCILE'",
                                Long.class)
                        .setParameter("id", invoice.getId())
                        .getSingleResult();
        if (money > 0)
            throw conflict(
                    "Ngân hàng đã báo tiền chưa được ghi nhận. Kiểm tra và xác nhận khoản thu trước"
                        + " khi hủy.");
    }
}
