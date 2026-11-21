package com.talent.management.features.tuition_payment.service.impl;

import static com.talent.management.features.tuition_payment.exception.TuitionOperationException.conflict;
import static com.talent.management.features.tuition_payment.support.TuitionDates.today;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.notification.service.NotificationService;
import com.talent.management.features.tuition_payment.dto.request.ConfigureTuitionPlanRequest;
import com.talent.management.features.tuition_payment.dto.request.InstallmentDueRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.InvoiceView;
import com.talent.management.features.tuition_payment.policy.TuitionSchedulePolicy;
import com.talent.management.features.tuition_payment.repository.TuitionInstallmentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionLedgerRepository;
import com.talent.management.features.tuition_payment.service.TuitionDiscountService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.features.tuition_payment.service.TuitionPlanService;
import com.talent.management.features.tuition_payment.service.TuitionQueryService;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.TuitionInstallment;
import com.talent.management.shared.entity.TuitionPlan;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.DiscountType;
import com.talent.management.shared.enums.InvoiceStatus;
import com.talent.management.shared.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TuitionPlanServiceImpl implements TuitionPlanService {
    private final TuitionLedgerRepository ledgerRepository;
    private final TuitionInstallmentRepository installments;
    private final CurrentUserService currentUserService;
    private final TuitionLedgerService tuitionLedgerService;
    private final TuitionQueryService tuitionQueryService;
    private final NotificationService notificationService;
    private final TuitionDiscountService discountService;

    @Transactional
    @Override
    public InvoiceView configure(Long id, ConfigureTuitionPlanRequest input) {
        Invoice invoice = tuitionLedgerService.lock(id);
        TuitionPlan plan = tuitionLedgerService.ensure(invoice);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED)
            throw conflict("Hóa đơn đã bị hủy. Không thể chốt lịch đóng.");
        if (invoice.getStatus() != InvoiceStatus.UNPAID
                || tuitionLedgerService.paid(invoice).signum() != 0)
            throw conflict(
                    "Hóa đơn đã ghi nhận tiền thanh toán. Không được thay đổi ưu đãi hoặc chốt lại"
                        + " lịch đóng.");
        if (plan.isReviewRequired())
            throw conflict(
                    "Hóa đơn có dữ liệu thu chưa khớp. Vui lòng kiểm tra giao dịch gốc trước khi"
                        + " chốt lịch đóng.");
        if (plan.getCreatedBy() != null)
            throw conflict(
                    "Lịch đóng đã được xác nhận. Không được ghi đè ưu đãi và các kỳ thanh toán đã"
                        + " chốt.");
        if (ledgerRepository.hasIntents(
                id, List.of("CREATING", "PENDING", "UNCERTAIN", "RECONCILE")))
            throw conflict(
                    "Hóa đơn đang có yêu cầu QR chưa xử lý xong. Vui lòng kiểm tra kết quả QR trước"
                        + " khi thay đổi lịch đóng.");
        var discount =
                discountService.resolve(
                        invoice,
                        input.discountMethod(),
                        input.discountValue(),
                        input.discountReason());
        BigDecimal finalAmount = invoice.getOriginalAmount().subtract(discount.amount());
        LocalDate end =
                invoice.getEnrollment() == null || invoice.getEnrollment().getClassEntity() == null
                        ? null
                        : invoice.getEnrollment().getClassEntity().getEndDate();
        TuitionSchedulePolicy.validate(input.installments(), finalAmount, today(), end);
        if (ledgerRepository.hasPlanChanges(plan.getId()))
            throw conflict("Lịch đã có lịch sử gia hạn. Không cấu hình lại.");
        discountService.snapshot(invoice, discount);
        invoice.setDiscountAmount(discount.amount());
        invoice.setDiscountReason(discount.reason());
        invoice.setDiscountType(
                discount.amount().signum() == 0
                        ? DiscountType.NONE
                        : DiscountType.PARTIAL_DISCOUNT);
        invoice.setFinalAmount(finalAmount);
        ledgerRepository.deleteNoticesForPlan(plan.getId());
        installments.deleteAll(installments.findByPlanIdOrderBySequenceNumber(plan.getId()));
        installments.flush();
        plan.setMode(input.installments().size() == 1 ? "FULL" : "INSTALLMENTS");
        plan.setRevision(plan.getRevision() + 1);
        plan.setCreatedBy(currentUserService.getCurrentUser());
        int sequence = 1;
        for (InstallmentDueRequest item : input.installments()) {
            TuitionInstallment due = new TuitionInstallment();
            due.setPlan(plan);
            due.setSequenceNumber(sequence++);
            due.setAmountDue(item.amount());
            due.setDueDate(item.date());
            installments.save(due);
        }
        invoice.setDueDate(input.installments().get(input.installments().size() - 1).date());
        User parent = invoice.getStudent().getParent();
        if (parent != null) {
            StringBuilder schedule = new StringBuilder();
            int period = 1;
            for (InstallmentDueRequest due : input.installments()) {
                if (!schedule.isEmpty()) schedule.append("; ");
                schedule.append(
                        String.format(
                                Locale.forLanguageTag("vi-VN"),
                                "đợt %d: %,.0f đ, hạn %s",
                                period++,
                                due.amount(),
                                due.date()
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "dd/MM/yyyy"))));
            }
            notificationService.sendNotification(
                    parent,
                    currentUserService.getCurrentUser(),
                    "Đã chốt lịch đóng học phí",
                    invoice.getStudent().getFullName()
                            + " - "
                            + invoice.getInvoiceCode()
                            + ": "
                            + schedule
                            + ". Mở hóa đơn để xem chi tiết và thanh toán.",
                    "TUITION_PLAN_CONFIRMED",
                    plan.getId(),
                    "/pages/parent-tuition.html?invoiceId=" + invoice.getId());
        }
        return tuitionQueryService.map(invoice, plan);
    }
}
