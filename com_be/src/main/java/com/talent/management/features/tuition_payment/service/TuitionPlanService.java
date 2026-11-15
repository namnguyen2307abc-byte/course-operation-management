package com.talent.management.features.tuition_payment.service;

import static org.springframework.http.HttpStatus.*;

import com.talent.management.features.tuition_payment.dto.request.InstallmentRequests;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.*;
import com.talent.management.features.tuition_payment.repository.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;
import com.talent.management.features.notification.service.NotificationService;

import jakarta.persistence.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TuitionPlanService {
    private final EntityManager em;
    private final TuitionPlanRepository plans;
    private final TuitionInstallmentRepository installments;
    private final PaymentAllocationRepository allocations;
    private final PaymentRepository payments;
    private final CurrentUserService current;
    private final TuitionReversalRepository reversals;
    private final TuitionIntentRepository intents;
    private final NotificationService notificationService;

    public static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    public static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(CONFLICT, message);
    }

    public Invoice lock(Long id) {
        Invoice invoice = em.find(Invoice.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (invoice == null)
            throw new ResponseStatusException(NOT_FOUND, "Không tìm thấy hóa đơn.");
        return invoice;
    }

    public void checkParent(Invoice invoice) {
        User actor = current.getCurrentUser();
        if (actor.getRole() == Role.PARENT
                && (invoice.getStudent().getParent() == null
                        || !Objects.equals(
                                invoice.getStudent().getParent().getId(), actor.getId()))) {
            throw new ResponseStatusException(NOT_FOUND, "Không tìm thấy học phí của con bạn.");
        }
    }

    public BigDecimal paid(Invoice invoice) {
        return payments.findByInvoiceId(invoice.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .filter(p -> !reversals.existsByPaymentIdAndStatus(p.getId(), "APPROVED"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal balance(Invoice invoice) {
        return invoice.getFinalAmount().subtract(paid(invoice)).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public BigDecimal outstanding(LocalDate overdueBefore) {
        BigDecimal total = BigDecimal.ZERO;
        for (Invoice invoice :
                em.createQuery(
                                "select i from Invoice i where i.status in"
                                        + " ('UNPAID','PARTIALLY_PAID')",
                                Invoice.class)
                        .getResultList()) {
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
                total = total.add(balance(invoice));
            }
        }
        return total;
    }

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
        TuitionInstallment due = new TuitionInstallment();
        due.setPlan(plan);
        due.setSequenceNumber(1);
        due.setAmountDue(invoice.getFinalAmount().max(BigDecimal.ZERO));
        due.setDueDate(invoice.getDueDate() == null ? today() : invoice.getDueDate());
        installments.save(due);
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

    public void requireCollectible(Invoice invoice, TuitionPlan plan, BigDecimal amount) {
        if(plan.getCreatedBy()==null && paid(invoice).signum()==0 && invoice.getFinalAmount().signum()>0) {
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

    public void validateMoney(BigDecimal amount) {
        if (amount == null
                || amount.signum() < 0
                || amount.stripTrailingZeros().scale() > 0
                || amount.compareTo(new BigDecimal("9999999999")) > 0) {
            throw conflict("Số tiền VND phải là số nguyên không âm trong giới hạn cho phép.");
        }
    }

    public void requireCurrentInstallment(Invoice invoice, TuitionPlan plan, BigDecimal amount) {
        requireCollectible(invoice, plan, amount);
        BigDecimal expected = installments.findByPlanIdOrderBySequenceNumber(plan.getId()).stream()
                .map(due -> due.getAmountDue().subtract(allocations.paid(due.getId())).max(BigDecimal.ZERO))
                .filter(left -> left.signum() > 0).findFirst().orElse(BigDecimal.ZERO);
        if (amount.compareTo(expected) != 0)
            throw conflict("Số tiền thanh toán cố định theo đợt còn thiếu: " + expected.toPlainString() + "đ. Vui lòng tải lại hóa đơn.");
        if (em.createQuery("select count(i) from TuitionPaymentIntent i where i.invoice.id=:id and i.status='RECONCILE'", Long.class)
                .setParameter("id", invoice.getId()).getSingleResult() > 0)
            throw conflict("Có tiền ngân hàng cần xác minh trước khi tiếp tục thu.");
    }

    public void validateDiscountValue(BigDecimal original, String method, BigDecimal value) {
        if (!List.of("PERCENTAGE", "FIXED_AMOUNT").contains(method))
            throw conflict("Kiểu giảm không hợp lệ.");
        if (value == null || value.signum() < 0 || value.scale() > 0)
            throw conflict("Mức giảm phải là số nguyên không âm, không được nhập số thập phân.");
        BigDecimal maximum = "PERCENTAGE".equals(method) ? BigDecimal.valueOf(100) : original;
        if ("FIXED_AMOUNT".equals(method) && value.compareTo(BigDecimal.valueOf(1000)) < 0)
            throw conflict("Số tiền giảm phải từ 1.000đ đến học phí gốc.");
        if (value.compareTo(maximum) > 0)
            throw conflict("PERCENTAGE".equals(method)
                    ? "Phần trăm giảm phải từ 0 đến 100."
                    : "Số tiền giảm phải từ 1.000đ đến học phí gốc.");
    }

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

    @Transactional
    public InvoiceView view(Long id) {
        Invoice invoice = lock(id);
        checkParent(invoice);
        return map(invoice, ensure(invoice));
    }

    @Transactional
    public List<InvoiceView> list() {
        User actor = current.getCurrentUser();
        List<Invoice> invoices =
                actor.getRole() == Role.PARENT
                        ? em.createQuery(
                                        "select i from Invoice i where i.student.parent.id=:id"
                                                + " order by i.id desc",
                                        Invoice.class)
                                .setParameter("id", actor.getId())
                                .getResultList()
                        : em.createQuery(
                                        "select i from Invoice i order by i.id desc", Invoice.class)
                                .getResultList();
        return invoices.stream().map(i -> view(i.getId())).toList();
    }

    @Transactional
    public List<OverdueContact> overdueContacts() {
        if (current.getCurrentUser().getRole()!=Role.ADMIN)
            throw new ResponseStatusException(FORBIDDEN,"Chỉ admin được xem danh sách cần liên hệ.");
        return list().stream().filter(i -> i.confirmed() && !i.status().equals("CANCELLED"))
                .flatMap(i -> i.installments().stream().filter(d -> d.remaining().signum()>0
                        && java.time.temporal.ChronoUnit.DAYS.between(d.date(),today())>=7)
                        .map(d -> new OverdueContact(i.id(),i.code(),i.student(),i.parent(),i.phone(),d.sequence(),d.date(),d.remaining(),java.time.temporal.ChronoUnit.DAYS.between(d.date(),today()))))
                .toList();
    }

    @Transactional
    public InvoiceView configure(Long id, InstallmentRequests.Plan input) {
        Invoice invoice = lock(id);
        TuitionPlan plan = ensure(invoice);
        if (invoice.getStatus() != InvoiceStatus.UNPAID
                || paid(invoice).signum() != 0
                || plan.isReviewRequired()) {
            throw conflict("Chỉ chốt lịch khi hóa đơn chưa thu tiền và không cần đối chiếu.");
        }
        if (em.createQuery(
                                "select count(i) from TuitionPaymentIntent i where i.invoice.id=:id"
                                        + " and i.status in ('CREATING','PENDING','UNCERTAIN')",
                                Long.class)
                        .setParameter("id", id)
                        .getSingleResult()
                > 0) throw conflict("Hóa đơn đang có QR chờ thanh toán.");
        if (input.discountMethod()!=null) {
            BigDecimal value=input.discountValue()==null ? BigDecimal.ZERO : input.discountValue();
            validateDiscountValue(invoice.getOriginalAmount(), input.discountMethod(), value);
            validateMoney(value);
            BigDecimal reduction;
            if ("PERCENTAGE".equals(input.discountMethod())) {
                if(value.compareTo(BigDecimal.valueOf(100))>0) throw conflict("Phần trăm giảm từ 0 đến 100.");
                reduction=invoice.getOriginalAmount().multiply(value).divide(BigDecimal.valueOf(100),0,java.math.RoundingMode.HALF_UP);
            } else if ("FIXED_AMOUNT".equals(input.discountMethod())) reduction=value;
            else throw conflict("Kiểu giảm không hợp lệ.");
            if(reduction.compareTo(invoice.getOriginalAmount())>0) throw conflict("Mức giảm vượt học phí.");
            if(reduction.signum()>0 && (input.discountReason()==null || input.discountReason().isBlank())) throw conflict("Vui lòng nhập lý do ưu đãi.");
            invoice.setDiscountAmount(reduction);
            invoice.setDiscountReason(input.discountReason());
            invoice.setDiscountType(reduction.signum()==0 ? DiscountType.NONE : reduction.compareTo(invoice.getOriginalAmount())==0 ? DiscountType.FULL_FREE : DiscountType.PARTIAL_DISCOUNT);
            invoice.setFinalAmount(invoice.getOriginalAmount().subtract(reduction));
        }
        BigDecimal total = BigDecimal.ZERO;
        LocalDate previous = null;
        LocalDate end =
                invoice.getEnrollment() == null
                        ? null
                        : invoice.getEnrollment().getClassEntity().getEndDate();
        for (InstallmentRequests.Due due : input.installments()) {
            validateMoney(due.amount());
            if ((invoice.getFinalAmount().signum() > 0 && due.amount().signum() == 0)
                    || due.date().isBefore(today())
                    || (previous != null && !due.date().isAfter(previous))
                    || (end != null && due.date().isAfter(end))) {
                throw conflict(
                        "Kỳ đóng cần có số tiền hợp lệ, ngày tăng dần từ hôm nay và trong thời gian"
                                + " khóa học.");
            }
            total = total.add(due.amount());
            previous = due.date();
        }
        if (total.compareTo(invoice.getFinalAmount()) != 0
                || input.installments()
                                .get(0)
                                .amount()
                                .multiply(BigDecimal.valueOf(2))
                                .compareTo(total)
                        < 0) {
            throw conflict("Tổng các kỳ phải khớp học phí, kỳ đầu tối thiểu 50%.");
        }
        if (em.createQuery(
                                "select count(c) from TuitionPlanChange c where c.plan.id=:id",
                                Long.class)
                        .setParameter("id", plan.getId())
                        .getSingleResult()
                > 0) throw conflict("Lịch đã có lịch sử gia hạn. Không cấu hình lại.");
        em.createQuery("delete from TuitionNotice n where n.installment.plan.id=:id")
                .setParameter("id", plan.getId())
                .executeUpdate();
        installments.deleteAll(installments.findByPlanIdOrderBySequenceNumber(plan.getId()));
        installments.flush();
        plan.setMode(input.installments().size() == 1 ? "FULL" : "INSTALLMENTS");
        plan.setRevision(plan.getRevision() + 1);
        plan.setCreatedBy(current.getCurrentUser());
        int sequence = 1;
        for (InstallmentRequests.Due item : input.installments()) {
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
            for (InstallmentRequests.Due due : input.installments()) {
                if (!schedule.isEmpty()) schedule.append("; ");
                schedule.append(String.format(Locale.forLanguageTag("vi-VN"),
                        "đợt %d: %,.0f đ, hạn %s", period++, due.amount(),
                        due.date().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
            }
            notificationService.sendNotification(parent, current.getCurrentUser(),
                    "Đã chốt lịch đóng học phí",
                    invoice.getStudent().getFullName() + " - " + invoice.getInvoiceCode() + ": "
                            + schedule + ". Mở hóa đơn để xem chi tiết và thanh toán.",
                    "TUITION_PLAN_CONFIRMED", plan.getId(),
                    "/pages/parent-tuition.html?invoiceId=" + invoice.getId());
        }
        return map(invoice, plan);
    }

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
                                                com.talent.management.features.tuition_payment.mapper.PaymentReference.receipt(p),
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
                                                p.getCashier() == null ? null : p.getCashier().getId()))
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
                paid(invoice),
                balance(invoice),
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
                        .toList(), plan.getCreatedBy()!=null || paid(invoice).signum()>0, invoice.getOriginalAmount(), invoice.getDiscountAmount(), invoice.getDiscountReason(),
                invoice.getReservationDeadline(),false,
                invoice.getEnrollment()!=null && invoice.getEnrollment().getStatus()==EnrollmentStatus.PENDING_PAYMENT,
                em.createQuery("select a from TuitionReservationAction a where a.invoice.id=:id order by a.id desc",TuitionReservationAction.class)
                        .setParameter("id",invoice.getId()).getResultList().stream()
                        .map(a -> new ReservationAction(a.getAction(),a.getReason(),a.getActor().getFullName(),a.getCreatedAt(),a.getOldDeadline(),a.getNewDeadline())).toList());
    }
}
