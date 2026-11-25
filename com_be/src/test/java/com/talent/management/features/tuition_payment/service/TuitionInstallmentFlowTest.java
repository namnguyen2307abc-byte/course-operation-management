package com.talent.management.features.tuition_payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.talent.management.features.notification.repository.NotificationRepository;
import com.talent.management.features.notification.service.impl.NotificationServiceImpl;
import com.talent.management.features.tuition_payment.dto.request.CancelReservationRequest;
import com.talent.management.features.tuition_payment.dto.request.ConfigureTuitionPlanRequest;
import com.talent.management.features.tuition_payment.dto.request.ConfirmBankPaymentRequest;
import com.talent.management.features.tuition_payment.dto.request.InstallmentDueRequest;
import com.talent.management.features.tuition_payment.dto.request.RequestDueDateChangeRequest;
import com.talent.management.features.tuition_payment.dto.request.ReviewTuitionRequest;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.*;
import com.talent.management.features.tuition_payment.dto.response.PayOSPaymentLinkResponse;
import com.talent.management.features.tuition_payment.gateway.PayOSGateway;
import com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper;
import com.talent.management.features.tuition_payment.policy.CashTenderPolicy;
import com.talent.management.features.tuition_payment.repository.*;
import com.talent.management.features.tuition_payment.service.impl.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.exception.ApplicationException;
import com.talent.management.shared.service.CurrentUserService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@DataJpaTest
@Import({
    TuitionPlanServiceImpl.class,
    BankTransferServiceImpl.class,
    PaymentIntentLedgerRepository.class,
    TuitionDiscountServiceImpl.class,
    TuitionLedgerServiceImpl.class,
    TuitionQueryServiceImpl.class,
    CashCollectionServiceImpl.class,
    PaymentResultServiceImpl.class,
    TuitionLedgerRepository.class,
    PaymentPostingServiceImpl.class,
    PaymentIntentServiceImpl.class,
    PlanChangeServiceImpl.class,
    TuitionNotificationServiceImpl.class,
    TuitionReversalServiceImpl.class,
    TuitionReservationServiceImpl.class,
    TuitionPaymentMapper.class,
    NotificationServiceImpl.class,
    com.talent.management.features.admin_management.repository.AdminDashboardRepository.class,
    TuitionInstallmentFlowTest.Transactions.class
})
@TestPropertySource(
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.show-sql=false",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "tuition.simulation.enabled=true"
        })
class TuitionInstallmentFlowTest {
    @TestConfiguration
    static class Transactions {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    @Autowired TestEntityManager db;
    @Autowired TuitionLedgerService ledger;
    @Autowired TuitionPlanService tuitionPlans;
    @Autowired TuitionQueryService queries;
    @Autowired CashCollectionService cashCollections;
    @Autowired PaymentResultService paymentResults;
    @Autowired PaymentPostingService posting;
    @Autowired PaymentIntentService gateway;
    @Autowired PlanChangeService changes;
    @Autowired TuitionNotificationService notices;
    @Autowired TuitionReversalService reversals;
    @Autowired TuitionReservationService reservations;
    @Autowired TuitionInstallmentRepository installments;
    @Autowired TuitionBankEventRepository events;
    @Autowired TuitionIntentRepository intents;
    @Autowired PaymentRepository payments;
    @MockBean CurrentUserService current;
    @MockBean PayOSGateway payos;
    @SpyBean NotificationServiceImpl notificationService;
    @Autowired NotificationRepository bellNotifications;
    @Autowired TuitionNoticeRepository tuitionNotices;

    @Autowired
    com.talent.management.features.admin_management.repository.AdminDashboardRepository dashboard;

    User parent, other, staff, manager;
    Invoice invoice;
    ClassEntity clazz;
    LocalDate today = com.talent.management.features.tuition_payment.support.TuitionDates.today();

    @Test
    void signedWebhookRegistrationProbeDoesNotCreateFinancialRecords() {
        Map<String, Object> data =
                Map.of(
                        "code",
                        "00",
                        "orderCode",
                        123,
                        "amount",
                        3000,
                        "paymentLinkId",
                        "124c33293c43417ab7879e14c8d9eb18",
                        "reference",
                        "TF230204212323");
        Map<String, Object> body = Map.of("code", "00", "success", true, "data", data);
        when(payos.verifyWebhookSignature(body)).thenReturn(true);
        long beforeEvents = events.count();
        long beforePayments = payments.count();
        assertThat(paymentResults.webhook(body)).isNull();
        assertThat(events.count()).isEqualTo(beforeEvents);
        assertThat(payments.count()).isEqualTo(beforePayments);
        verify(payos).verifyWebhookSignature(body);
    }

    @BeforeEach
    void setup() {
        parent = user("parent", Role.PARENT);
        other = user("other", Role.PARENT);
        staff = user("staff", Role.STAFF);
        manager = user("manager", Role.ADMIN);
        Student child =
                db.persist(Student.builder().fullName("Con thử nghiệm").parent(parent).build());
        Program program = db.persist(Program.builder().code("PI").name("Piano").build());
        Course course =
                db.persist(
                        Course.builder()
                                .code("PI-1")
                                .name("Piano 1")
                                .program(program)
                                .totalSessions(10)
                                .tuitionFee(new BigDecimal("6000000"))
                                .build());
        Branch branch =
                db.persist(Branch.builder().code("B1").name("Cơ sở 1").address("Hà Nội").build());
        clazz =
                db.persist(
                        ClassEntity.builder()
                                .course(course)
                                .branch(branch)
                                .classCode("C1")
                                .className("Lớp 1")
                                .currentStudents(0)
                                .maxStudents(5)
                                .startDate(today)
                                .endDate(today.plusMonths(4))
                                .build());
        Enrollment enrollment =
                db.persist(Enrollment.builder().student(child).classEntity(clazz).build());
        invoice =
                db.persist(
                        Invoice.builder()
                                .invoiceCode("INV-FLOW")
                                .student(child)
                                .enrollment(enrollment)
                                .originalAmount(new BigDecimal("6000000"))
                                .finalAmount(new BigDecimal("6000000"))
                                .dueDate(today.plusMonths(1))
                                .build());
        as(staff);
        when(payos.createPaymentLink(any(), any(), anyLong(), anyBoolean()))
                .thenAnswer(
                        call ->
                                PayOSPaymentLinkResponse.builder()
                                        .orderCode(call.getArgument(2))
                                        .amount(call.getArgument(1))
                                        .status("PENDING")
                                        .simulated(true)
                                        .build());
        when(payos.verifyWebhookSignature(any())).thenReturn(true);
    }

    @Test
    void cashReceiptClosesOldQrButReplayDoesNotCancelANewInstallmentQr() {
        configure();
        var oldQr = gateway.create(invoice.getId(), money(3000000));
        var receipt =
                cashCollections.collectReceipt(
                        invoice.getId(), money(3000000), money(3000000), "receipt-replay");
        assertThat(intents.findById(oldQr.id()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        var nextQr = gateway.create(invoice.getId(), money(1500000));
        var replay =
                cashCollections.collectReceipt(
                        invoice.getId(), money(3000000), money(3000000), "receipt-replay");
        assertThat(replay.getPaymentId()).isEqualTo(receipt.getPaymentId());
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        assertThat(intents.findById(nextQr.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(queries.view(invoice.getId()).balance()).isEqualByComparingTo("3000000");
    }

    @Test
    void installmentNotificationsUseBalanceAndDoNotDuplicateEnrollmentOrReplays() {
        User teacher = user("teacher-notice", Role.TEACHER);
        User branchManager = user("branch-notice", Role.BRANCH_MANAGER);
        clazz.setTeacher(teacher);
        tuitionPlans.configure(
                invoice.getId(),
                new ConfigureTuitionPlanRequest(
                        List.of(
                                new InstallmentDueRequest(money(3000000), today),
                                new InstallmentDueRequest(money(3000000), today.plusDays(30)))));
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "notice-first");
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "notice-first");
        verify(notificationService)
                .sendNotification(
                        eq(parent),
                        eq(staff),
                        eq("Đã nhận học phí"),
                        contains("còn phải đóng 3.000.000"),
                        eq("PAYMENT_SUCCESS"),
                        anyLong(),
                        eq("/pages/parent-tuition.html?invoiceId=" + invoice.getId()));
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "notice-last");
        verify(notificationService, times(1))
                .sendNotification(
                        eq(teacher),
                        eq(staff),
                        eq("Học viên nhập học chính thức"),
                        anyString(),
                        eq("ENROLLMENT_COMPLETED"),
                        anyLong(),
                        anyString());
        verify(notificationService, times(2))
                .sendNotification(
                        eq(parent),
                        eq(staff),
                        eq("Đã nhận học phí"),
                        anyString(),
                        eq("PAYMENT_SUCCESS"),
                        anyLong(),
                        anyString());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paymentNotifications(parent)).hasSize(2);
        assertThat(paymentNotifications(staff)).hasSize(2);
        assertThat(paymentNotifications(manager)).hasSize(2);
        assertThat(paymentNotifications(branchManager)).hasSize(2);
        assertThat(paymentNotifications(other)).isEmpty();
    }

    @Test
    void confirmedScheduleAppearsOnParentBellWithAmountsDatesAndInvoiceLink() {
        configure();
        assertThat(bellNotifications.findByRecipientIdAndIsReadFalse(parent.getId()))
                .singleElement()
                .satisfies(
                        n -> {
                            assertThat(n.getType()).isEqualTo("TUITION_PLAN_CONFIRMED");
                            assertThat(n.getMessage())
                                    .contains("Con thử nghiệm", "INV-FLOW", "3.000.000", "đợt 3");
                            assertThat(n.getTargetUrl())
                                    .isEqualTo(
                                            "/pages/parent-tuition.html?invoiceId="
                                                    + invoice.getId());
                            assertThat(n.getSender().getId()).isEqualTo(staff.getId());
                        });
        assertThat(bellNotifications.findByRecipientIdAndIsReadFalse(other.getId())).isEmpty();
    }

    @Test
    void scheduledRemindersReachBellOncePerStageAndDoNotResendReadNotifications() {
        tuitionPlans.configure(
                invoice.getId(),
                new ConfigureTuitionPlanRequest(
                        List.of(
                                new InstallmentDueRequest(money(3000000), today.plusDays(1)),
                                new InstallmentDueRequest(money(3000000), today.plusDays(30)))));
        var due =
                installments
                        .findById(queries.view(invoice.getId()).installments().get(0).id())
                        .orElseThrow();
        when(current.getCurrentUser())
                .thenThrow(new IllegalStateException("No login in scheduler"));
        notices.generate(invoice.getId());
        notices.generate(invoice.getId());
        var upcoming =
                bellNotifications.findByRecipientIdAndIsReadFalse(parent.getId()).stream()
                        .filter(n -> n.getType().equals("TUITION_UPCOMING"))
                        .toList();
        assertThat(upcoming)
                .singleElement()
                .satisfies(
                        n -> {
                            assertThat(n.getMessage()).contains("đợt 1", "3.000.000");
                            assertThat(n.getSender()).isNull();
                            n.setIsRead(true);
                        });
        db.flush();
        notices.generate(invoice.getId());
        due.setDueDate(today);
        notices.generate(invoice.getId());
        notices.generate(invoice.getId());
        due.setDueDate(today.minusDays(1));
        notices.generate(invoice.getId());
        notices.generate(invoice.getId());
        var all = bellNotifications.findTop50ByRecipientIdOrderByCreatedAtDesc(parent.getId());
        assertThat(
                        all.stream()
                                .filter(
                                        n ->
                                                n.getType().startsWith("TUITION_")
                                                        && !n.getType()
                                                                .equals("TUITION_PLAN_CONFIRMED")))
                .extracting(Notification::getType)
                .containsExactlyInAnyOrder(
                        "TUITION_UPCOMING", "TUITION_DUE_TODAY", "TUITION_OVERDUE");
        assertThat(bellNotifications.findByRecipientIdAndIsReadFalse(other.getId())).isEmpty();
    }

    @Test
    void existingPrivateReminderIsBridgedToSharedBellWithoutDuplication() {
        configure();
        TuitionPlan plan = ledger.ensure(invoice);
        var due = installments.findByPlanIdOrderBySequenceNumber(plan.getId()).get(0);
        TuitionNotice legacy = new TuitionNotice();
        legacy.setRecipient(parent);
        legacy.setInstallment(due);
        legacy.setRevision(plan.getRevision());
        legacy.setType("DUE_TODAY");
        legacy.setEventKey(
                parent.getId() + "-" + due.getId() + "-" + plan.getRevision() + "-DUE_TODAY");
        tuitionNotices.saveAndFlush(legacy);
        notices.generate(invoice.getId());
        notices.generate(invoice.getId());
        assertThat(
                        bellNotifications
                                .findTop50ByRecipientIdOrderByCreatedAtDesc(parent.getId())
                                .stream()
                                .filter(n -> n.getType().equals("TUITION_DUE_TODAY")))
                .singleElement()
                .satisfies(n -> assertThat(n.getReferenceId()).isEqualTo(legacy.getId()));
    }

    @Test
    void settledInvoiceDoesNotCreateDueBellNotifications() {
        configure();
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "settled-first");
        cashCollections.cash(invoice.getId(), money(1500000), money(1500000), "settled-second");
        cashCollections.cash(invoice.getId(), money(1500000), money(1500000), "settled-last");
        notices.generate(invoice.getId());
        assertThat(
                        bellNotifications
                                .findTop50ByRecipientIdOrderByCreatedAtDesc(parent.getId())
                                .stream()
                                .filter(
                                        n ->
                                                List.of(
                                                                "TUITION_UPCOMING",
                                                                "TUITION_DUE_TODAY",
                                                                "TUITION_OVERDUE")
                                                        .contains(n.getType())))
                .isEmpty();
    }

    private List<Notification> paymentNotifications(User recipient) {
        return bellNotifications
                .findTop50ByRecipientIdOrderByCreatedAtDesc(recipient.getId())
                .stream()
                .filter(n -> n.getType().equals("PAYMENT_SUCCESS"))
                .toList();
    }

    @Test
    void cashInstallmentsAllocateEarliestAndEnrollOnlyOnce() {
        configure();
        Payment first =
                cashCollections.cash(invoice.getId(), money(3000000), money(3500000), "first");
        assertThat(first.getChangeAmount()).isEqualByComparingTo("500000");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(clazz.getCurrentStudents()).isEqualTo(1);
        assertThat(
                        cashCollections
                                .cash(invoice.getId(), money(3000000), money(3500000), "first")
                                .getId())
                .isEqualTo(first.getId());
        cashCollections.cash(invoice.getId(), money(1500000), money(1500000), "second");
        var after = queries.view(invoice.getId());
        assertThat(after.installments().get(1).remaining()).isEqualByComparingTo("0");
        cashCollections.cash(invoice.getId(), money(1500000), money(1500000), "last");
        assertThat(queries.view(invoice.getId()).balance()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(clazz.getCurrentStudents()).isEqualTo(1);
        as(parent);
        assertThat(queries.list())
                .singleElement()
                .satisfies(i -> assertThat(i.receipts()).hasSize(3));
    }

    @Test
    void zeroPaymentCannotAutoActivateEnrollment() {
        invoice.setOriginalAmount(BigDecimal.ZERO);
        invoice.setFinalAmount(BigDecimal.ZERO);
        assertThatThrownBy(
                        () ->
                                cashCollections.cash(
                                        invoice.getId(), BigDecimal.ZERO, BigDecimal.ZERO, "free"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("0đ");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
        assertThat(clazz.getCurrentStudents()).isEqualTo(0);
    }

    @Test
    void parentCannotReadAnotherChild() {
        as(other);
        assertThat(queries.list()).isEmpty();
        assertThatThrownBy(() -> queries.view(invoice.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(
                        error ->
                                assertThat(((ApplicationException) error).getStatusCode().value())
                                        .isEqualTo(404));
    }

    @Test
    void parentCanRetryMissingGatewayRequestWithoutLosingPayments() {
        configure();
        when(payos.createPaymentLink(any(), any(), anyLong(), anyBoolean()))
                .thenThrow(new IllegalStateException("Network result unknown"));
        as(parent);
        assertThatThrownBy(() -> gateway.create(invoice.getId(), money(3000000)))
                .isInstanceOf(IllegalStateException.class);
        var failed = intents.findByInvoiceIdAndStatus(invoice.getId(), "UNCERTAIN").get(0);
        when(payos.isMissingOrClosed(failed.getOrderCode())).thenReturn(true);
        useRealGatewayStub();
        var retry = gateway.create(invoice.getId(), money(3000000));
        assertThat(retry.status()).isEqualTo("PENDING");
        assertThat(retry.id()).isNotEqualTo(failed.getId());
        assertThat(intents.findById(failed.getId()).orElseThrow().getStatus()).isEqualTo("FAILED");
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    void unknownGatewayResultStaysBlockedUntilConfirmedAbsent() {
        configure();
        when(payos.createPaymentLink(any(), any(), anyLong(), anyBoolean()))
                .thenThrow(new IllegalStateException("Timeout"));
        assertThatThrownBy(() -> gateway.create(invoice.getId(), money(3000000)))
                .isInstanceOf(IllegalStateException.class);
        useRealGatewayStub();
        assertThatThrownBy(() -> gateway.create(invoice.getId(), money(3000000)))
                .isInstanceOf(ApplicationException.class);
        assertThat(intents.findByInvoiceIdAndStatus(invoice.getId(), "UNCERTAIN")).hasSize(1);
    }

    @Test
    void reopeningHostedCheckoutCancelsThePreviousAttemptAndCreatesANewCode() {
        configure();
        useRealGatewayStub();
        as(parent);
        var first = gateway.create(invoice.getId(), money(3000000));
        var reopened = gateway.create(invoice.getId(), money(3000000));
        assertThat(reopened.id()).isNotEqualTo(first.id());
        assertThat(reopened.orderCode()).isNotEqualTo(first.orderCode());
        assertThat(intents.findById(first.id()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(reopened.status()).isEqualTo("PENDING");
        verify(payos).cancelPayment(first.orderCode());
        verify(payos, org.mockito.Mockito.times(2))
                .createPaymentLink(any(), any(), anyLong(), anyBoolean());
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    void switchingToCashClosesQrAndReturnsExcessCash() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        gateway.prepareCashPayment(invoice.getId());
        verify(payos).cancelPayment(qr.orderCode());
        var payment =
                cashCollections.cash(
                        invoice.getId(), money(3000000), money(3500000), "cash-after-qr");
        assertThat(payment.getChangeAmount()).isEqualByComparingTo("500000");
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(queries.view(invoice.getId()).paid()).isEqualByComparingTo("3000000");
    }

    @Test
    void failedGatewayCancellationDoesNotAllowCashCollection() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        org.mockito.Mockito.doThrow(new IllegalStateException("Gateway unavailable"))
                .when(payos)
                .cancelPayment(qr.orderCode());
        assertThatThrownBy(() -> gateway.prepareCashPayment(invoice.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                cashCollections.cash(
                                        invoice.getId(), money(3000000), money(3500000), "blocked"))
                .isInstanceOf(ApplicationException.class);
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    void belowFirstThresholdIsRejected() {
        configure();
        assertThatThrownBy(
                        () ->
                                cashCollections.cash(
                                        invoice.getId(), money(1000000), money(1000000), "small"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("kỳ đầu");
    }

    @Test
    void delayedQrAfterCashIsRecordedForReconciliation() {
        configure();
        var qr = gateway.create(invoice.getId(), money(3000000));
        gateway.cancel(qr.id());
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "cash-first");
        cashCollections.cash(invoice.getId(), money(1500000), money(1500000), "cash-second");
        cashCollections.cash(invoice.getId(), money(1500000), money(1500000), "cash-last");
        var payload = webhook(qr.orderCode(), "REF-LATE", money(3000000));
        // Simulated events enter only through the explicitly enabled admin simulation endpoint.
        gateway.simulate(qr.id());
        assertThat(queries.view(invoice.getId()).balance()).isEqualByComparingTo("0");
        assertThat(events.findByStatusOrderByIdDesc("RECONCILE"))
                .singleElement()
                .satisfies(e -> assertThat(e.getReceivedAmount()).isEqualByComparingTo("3000000"));
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(3);
    }

    @Test
    void onlineReceiptIsIdempotentAndSharesBalanceWithCashier() {
        configure();
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "first");
        as(parent);
        var qr = gateway.create(invoice.getId(), money(1500000));
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("6000000");
        gateway.simulate(qr.id());
        gateway.simulate(qr.id());
        as(staff);
        assertThat(queries.view(invoice.getId()).balance()).isEqualByComparingTo("1500000");
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(2);
        assertThat(clazz.getCurrentStudents()).isEqualTo(1);
    }

    @Test
    void manualBankConfirmationAndLateWebhookProduceOneReceipt() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        var input =
                new ConfirmBankPaymentRequest(
                        money(3000000),
                        "BANK-MANUAL-1",
                        "Đã kiểm tra ghi có, webhook chưa đến",
                        true);
        paymentResults.confirmBank(qr.id(), input);
        paymentResults.confirmBank(qr.id(), input);
        paymentResults.webhook(webhook(qr.orderCode(), "BANK-MANUAL-1", money(3000000)));
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        assertThat(queries.view(invoice.getId()).paid()).isEqualByComparingTo("3000000");
        assertThat(gateway.status(qr.id()).status()).isEqualTo("PAID");
        assertThat(events.findByReference("BANK-MANUAL-1").orElseThrow().getReviewedBy().getId())
                .isEqualTo(staff.getId());
        assertThat(events.findByStatusOrderByIdDesc("RECONCILE")).isEmpty();
        assertThat(paymentNotifications(parent)).hasSize(1);
        assertThat(paymentNotifications(staff)).hasSize(1);
    }

    @Test
    void automaticWebhookNotifiesParentAndStaffOnceWithoutLoggedInUser() {
        configure();
        useRealGatewayStub();
        as(parent);
        var qr = gateway.create(invoice.getId(), money(3000000));
        when(current.getCurrentUser()).thenThrow(new IllegalStateException("Webhook has no login"));
        paymentResults.webhook(webhook(qr.orderCode(), "BANK-NOTIFY-ONCE", money(3000000)));
        paymentResults.webhook(webhook(qr.orderCode(), "BANK-NOTIFY-ONCE", money(3000000)));
        assertThat(paymentNotifications(parent))
                .singleElement()
                .satisfies(
                        n ->
                                assertThat(n.getTargetUrl())
                                        .contains(
                                                "parent-tuition.html?invoiceId="
                                                        + invoice.getId()));
        assertThat(paymentNotifications(staff))
                .singleElement()
                .satisfies(
                        n ->
                                assertThat(n.getTargetUrl())
                                        .contains(
                                                "tuition-receivables.html?invoiceId="
                                                        + invoice.getId()));
        assertThat(paymentNotifications(manager)).hasSize(1);
        assertThat(paymentNotifications(other)).isEmpty();
    }

    @Test
    void manualConfirmationAfterAutomaticWebhookDoesNotCollectAgain() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        paymentResults.webhook(webhook(qr.orderCode(), "BANK-AUTO-1", money(3000000)));
        paymentResults.confirmBank(
                qr.id(),
                new ConfirmBankPaymentRequest(money(3000000), "BANK-AUTO-1", "Kiểm tra lại", true));
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        assertThatThrownBy(
                        () ->
                                paymentResults.confirmBank(
                                        qr.id(),
                                        new ConfirmBankPaymentRequest(
                                                money(3000000),
                                                "ANOTHER-REF",
                                                "Kiểm tra lại",
                                                true)))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    void manualConfirmationRequiresStaffExactAmountAndBankVerification() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        as(parent);
        assertThatThrownBy(
                        () ->
                                paymentResults.confirmBank(
                                        qr.id(),
                                        new ConfirmBankPaymentRequest(
                                                money(3000000), "BANK-1", "Đã xem", true)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(
                        error ->
                                assertThat(((ApplicationException) error).getStatusCode().value())
                                        .isEqualTo(403));
        as(staff);
        for (var input :
                List.of(
                        new ConfirmBankPaymentRequest(money(1500000), "BANK-1", "Đã xem", true),
                        new ConfirmBankPaymentRequest(money(3000000), "BANK-1", "Đã xem", false),
                        new ConfirmBankPaymentRequest(money(3000000), " ", "Đã xem", true))) {
            assertThatThrownBy(() -> paymentResults.confirmBank(qr.id(), input))
                    .isInstanceOf(ApplicationException.class);
        }
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    void dueReminderCarriesReusableQrWithoutLoggedInStaffAndParentCanSeeReceipt() {
        configure();
        notices.generate(invoice.getId());
        Long noticeId = notices.pendingQrNotice(invoice.getId());
        assertThat(noticeId).isNotNull();
        when(current.getCurrentUser())
                .thenThrow(new IllegalStateException("Scheduled job has no login"));
        var qr = gateway.createForReminder(invoice.getId());
        notices.attachQr(noticeId, qr.id());
        assertThat(notices.pendingQrNotice(invoice.getId())).isNull();
        assertThat(gateway.createForReminder(invoice.getId()).id()).isEqualTo(qr.id());
        doReturn(parent).when(current).getCurrentUser();
        assertThat(notices.mine())
                .singleElement()
                .satisfies(
                        n -> {
                            assertThat(n.sequence()).isEqualTo(1);
                            assertThat(n.paymentIntent().id()).isEqualTo(qr.id());
                        });
        gateway.simulate(qr.id());
        assertThat(notices.mine()).isEmpty();
        assertThat(queries.view(invoice.getId()).receipts()).hasSize(1);
        as(other);
        assertThat(notices.mine()).isEmpty();
    }

    private void useRealGatewayStub() {
        doAnswer(
                        call ->
                                PayOSPaymentLinkResponse.builder()
                                        .orderCode(call.getArgument(2))
                                        .amount(call.getArgument(1))
                                        .status("PENDING")
                                        .simulated(false)
                                        .build())
                .when(payos)
                .createPaymentLink(any(), any(), anyLong(), anyBoolean());
    }

    @Test
    void paidPeriodCannotBeExtendedDirectly() {
        configure();
        cashCollections.cash(
                invoice.getId(), money(3000000), money(3000000), "first-before-extension");
        var first = queries.view(invoice.getId()).installments().get(0);
        assertThatThrownBy(
                        () ->
                                changes.extend(
                                        invoice.getId(),
                                        new RequestDueDateChangeRequest(
                                                first.id(), today.plusDays(1), "Xin đổi")))
                .hasMessageContaining("đã có khoản thanh toán");
        assertThat(changes.list()).isEmpty();
    }

    @Test
    void remindersDoNotDuplicateAndExtensionReplacesOldNotice() {
        configure();
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "first");
        var due =
                installments
                        .findById(queries.view(invoice.getId()).installments().get(1).id())
                        .orElseThrow();
        due.setDueDate(today.minusDays(1));
        as(parent);
        assertThat(notices.mine())
                .singleElement()
                .satisfies(n -> assertThat(n.type()).isEqualTo("OVERDUE"));
        assertThat(notices.mine()).hasSize(1);
        as(staff);
        changes.request(
                invoice.getId(),
                new RequestDueDateChangeRequest(due.getId(), today.plusDays(10), "Xin gia hạn"));
        as(parent);
        assertThat(notices.mine()).isEmpty();
    }

    @Test
    void failedLegacyTransactionDoesNotReduceDebtAndBadPaidInvoiceIsFlagged() {
        invoice.setStatus(InvoiceStatus.PAID);
        db.persist(
                Payment.builder()
                        .invoice(invoice)
                        .paymentCode("FAILED")
                        .paymentMethod(PaymentMethod.VIET_QR)
                        .amount(money(6000000))
                        .status(PaymentStatus.FAILED)
                        .build());
        var result = queries.view(invoice.getId());
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.paid()).isEqualByComparingTo("0");
        assertThat(result.receipts()).isEmpty();
    }

    @Test
    void retiredCashCorrectionCannotChangeReceiptsOrBalancesForAnyRole() {
        configure();
        var payment = cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "retired-correction");
        for (var actor : List.of(staff, manager, parent)) {
            as(actor);
            for (org.assertj.core.api.ThrowableAssert.ThrowingCallable call : List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                    () -> reversals.request(payment.getId(), "Thu nhầm"),
                    () -> reversals.review(1L, new ReviewTuitionRequest(true, "Duyệt")),
                    () -> reversals.list())) {
                assertThatThrownBy(call).isInstanceOfSatisfying(ApplicationException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(410);
                    assertThat(ex.getCode()).isEqualTo("TUITION_CASH_CORRECTION_REMOVED");
                });
            }
        }
        as(staff);
        assertThat(queries.view(invoice.getId()).paid()).isEqualByComparingTo("3000000");
        assertThat(queries.view(invoice.getId()).receipts().get(0).reversed()).isFalse();
    }

    @Test
    void historicalApprovedCorrectionStillExcludesOriginalReceiptFromPaidBalance() {
        configure();
        var payment = cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "historical-correction");
        var historical = new com.talent.management.shared.entity.TuitionReversal();
        historical.setPayment(payment);
        historical.setReason("Lịch sử điều chỉnh trước khi ngừng chức năng");
        historical.setRequestedBy(staff);
        historical.setReviewedBy(manager);
        historical.setStatus("APPROVED");
        db.persistAndFlush(historical);

        var view = queries.view(invoice.getId());
        assertThat(view.paid()).isEqualByComparingTo("0");
        assertThat(view.receipts().get(0).reversed()).isTrue();
        assertThat(view.balance()).isEqualByComparingTo(invoice.getFinalAmount());
    }

    @Test
    void amountMismatchFromBankIsPreservedWithoutPosting() {
        configure();
        var qr = gateway.create(invoice.getId(), money(3000000));
        // Model a genuine gateway intent and a correctly signed callback with a different received
        // amount.
        var intent = db.find(TuitionPaymentIntent.class, qr.id());
        intent.setSimulated(false);
        paymentResults.webhook(webhook(qr.orderCode(), "DIFFERENT", money(3500000)));
        assertThat(events.findByReference("DIFFERENT")).isPresent();
        assertThat(events.findByStatusOrderByIdDesc("RECONCILE")).hasSize(1);
        assertThat(ledger.paid(invoice)).isEqualByComparingTo("0");
    }

    @Test
    void authenticatedUnmatchedBankTransferIsRetainedForReview() {
        paymentResults.webhook(webhook(987654321, "UNKNOWN-ORDER", money(1000000)));
        paymentResults.webhook(webhook(987654321, "UNKNOWN-ORDER", money(1000000)));
        assertThat(events.findByReference("UNKNOWN-ORDER")).isPresent();
        assertThat(events.findByStatusOrderByIdDesc("RECONCILE"))
                .singleElement()
                .satisfies(e -> assertThat(e.getIntent()).isNull());
    }

    @Test
    void cashRoundingExamplesRespectExactTenderAndDenominations() {
        int[][] examples = {
            {4500, 5000, 500, 0}, {4700, 5000, 400, 100}, {4850, 5000, 200, 50}, {4701, 4701, 0, 0}
        };
        for (int[] example : examples) {
            var quote = CashTenderPolicy.quote(money(example[0]), money(example[1]));
            assertThat(quote.change()).isEqualByComparingTo(String.valueOf(example[2]));
            assertThat(quote.support()).isEqualByComparingTo(String.valueOf(example[3]));
        }
        for (String invalid : List.of("-1", "4499", "5000.5", "5001"))
            assertThatThrownBy(() -> CashTenderPolicy.quote(money(4500), new BigDecimal(invalid)))
                    .isInstanceOf(ApplicationException.class);
    }

    @Test
    void roundedCashSettlesTuitionButRevenueAndReceiptUseActualCash() {
        invoice.setOriginalAmount(money(4700));
        invoice.setFinalAmount(money(4700));
        tuitionPlans.configure(
                invoice.getId(),
                new ConfigureTuitionPlanRequest(
                        List.of(new InstallmentDueRequest(money(4700), today))));
        var receipt =
                cashCollections.collectReceipt(
                        invoice.getId(), money(4700), money(5000), "rounding");
        assertThat(receipt.getChangeAmount()).isEqualByComparingTo("400");
        assertThat(receipt.getCashRoundingAmount()).isEqualByComparingTo("100");
        assertThat(receipt.getNetReceivedAmount()).isEqualByComparingTo("4600");
        assertThat(queries.view(invoice.getId()).balance()).isEqualByComparingTo("0");
        assertThat(queries.view(invoice.getId()).receipts().get(0).cashRoundingAmount())
                .isEqualByComparingTo("100");
        assertThat(payments.sumAmountSince(today.atStartOfDay())).isEqualByComparingTo("4600");
        assertThat(dashboard.revenue(today, today))
                .singleElement()
                .satisfies(point -> assertThat(point.amount()).isEqualByComparingTo("4600"));
        assertThat(
                        cashCollections.replayCashReceipt(
                                invoice.getId(), money(4700), money(5000), "rounding"))
                .isPresent();
        assertThat(
                        cashCollections
                                .cash(invoice.getId(), money(4700), money(5000), "rounding")
                                .getId())
                .isEqualTo(receipt.getPaymentId());
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        assertThatThrownBy(() -> reversals.request(receipt.getPaymentId(), "Thu nhầm"))
                .isInstanceOf(ApplicationException.class);
        assertThat(payments.sumAmountSince(today.atStartOfDay())).isEqualByComparingTo("4600");
        assertThat(queries.view(invoice.getId()).balance()).isEqualByComparingTo("0");
    }

    @Test
    void changedAmountsAndInvalidTenderCannotCancelQrOrCollect() {
        configure();
        var qr = gateway.create(invoice.getId(), money(3000000));
        assertThatThrownBy(
                        () ->
                                gateway.prepareCashPayment(
                                        invoice.getId(), money(3000001), money(5000000)))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(
                        () ->
                                gateway.prepareCashPayment(
                                        invoice.getId(),
                                        money(3000000),
                                        new BigDecimal("3500000.5")))
                .isInstanceOf(ApplicationException.class);
        verify(payos, never()).cancelPayment(anyLong());
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
        as(parent);
        assertThatThrownBy(() -> gateway.create(invoice.getId(), money(4000000)))
                .isInstanceOf(ApplicationException.class);
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    void equalNextInstallmentCannotCauseCashCollectionAfterQrWasPaidDuringCancel() {
        tuitionPlans.configure(
                invoice.getId(),
                new ConfigureTuitionPlanRequest(
                        List.of(
                                new InstallmentDueRequest(money(3000000), today),
                                new InstallmentDueRequest(money(3000000), today.plusDays(30)))));
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        doAnswer(
                        call -> {
                            paymentResults.webhook(
                                    webhook(qr.orderCode(), "PAID-NO-CASH", money(3000000)));
                            return null;
                        })
                .when(payos)
                .cancelPayment(qr.orderCode());
        assertThatThrownBy(
                        () ->
                                gateway.prepareCashPayment(
                                        invoice.getId(), money(3000000), money(3500000)))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("Không thu thêm tiền mặt");
        assertThat(payments.findByInvoiceId(invoice.getId()))
                .singleElement()
                .satisfies(
                        payment ->
                                assertThat(payment.getPaymentMethod())
                                        .isEqualTo(PaymentMethod.VIET_QR));
    }

    @Test
    void closedQrIsNotReusedAndCancellationIsIdempotent() {
        configure();
        var first = gateway.create(invoice.getId(), money(3000000));
        gateway.cancel(first.id());
        gateway.cancel(first.id());
        var next = gateway.create(invoice.getId(), money(3000000));
        assertThat(next.id()).isNotEqualTo(first.id());
        assertThat(next.orderCode()).isNotEqualTo(first.orderCode());
        assertThat(intents.findById(first.id()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(next.status()).isEqualTo("PENDING");
    }

    @Test
    void checkoutDoesNotExpireAutomaticallyByDefault() {
        configure();
        var qr = gateway.create(invoice.getId(), money(3000000));
        var intent = intents.findById(qr.id()).orElseThrow();
        intent.setCreatedAt(LocalDateTime.now().minusMinutes(6));
        intents.flush();
        when(current.getCurrentUser()).thenThrow(new IllegalStateException("No user on scheduler"));
        gateway.expireUnpaidRequests();
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void paymentArrivingDuringCancellationWinsAndIsNotPostedTwice() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        doAnswer(
                        call -> {
                            paymentResults.webhook(
                                    webhook(qr.orderCode(), "BANK-RACE", money(3000000)));
                            return null;
                        })
                .when(payos)
                .cancelPayment(qr.orderCode());
        gateway.cancel(qr.id());
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("PAID");
        paymentResults.webhook(webhook(qr.orderCode(), "BANK-RACE", money(3000000)));
        gateway.cancel(qr.id());
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
    }

    @Test
    void missedWebhookIsRecoveredBeforeCancellingPaidGatewayRequest() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        doThrow(new IllegalStateException("Already paid"))
                .when(payos)
                .cancelPayment(qr.orderCode());
        when(payos.paymentInformation(qr.orderCode()))
                .thenReturn(
                        new PayOSGateway.GatewayPayment(
                                "PAID",
                                money(3000000),
                                List.of(
                                        new PayOSGateway.BankTransfer(
                                                money(3000000), "BANK-LOOKUP"))));
        gateway.cancel(qr.id());
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("PAID");
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
    }

    @Test
    void parentCannotCancelAnotherParentsQr() {
        configure();
        var qr = gateway.create(invoice.getId(), money(3000000));
        as(other);
        assertThatThrownBy(() -> gateway.cancel(qr.id())).isInstanceOf(ApplicationException.class);
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void discountInputsAreWholeNumbersWithinInclusiveBounds() {
        for (String rate : List.of("1", "10", "30"))
            com.talent.management.features.tuition_payment.policy.TuitionDiscountPolicy.validate(
                    money(3200000), "PERCENTAGE", new BigDecimal(rate));
        for (String amount : List.of("1000", "1001", "960000"))
            com.talent.management.features.tuition_payment.policy.TuitionDiscountPolicy.validate(
                    money(3200000), "FIXED_AMOUNT", new BigDecimal(amount));
        for (String rate : List.of("-1", "0", "31", "100", "10.5", "10.0"))
            assertThatThrownBy(
                            () ->
                                    com.talent.management.features.tuition_payment.policy
                                            .TuitionDiscountPolicy.validate(
                                            money(3200000), "PERCENTAGE", new BigDecimal(rate)))
                    .isInstanceOf(ApplicationException.class);
        for (String amount : List.of("-1", "0", "999", "960001", "3200001", "1000.5", "1000.00"))
            assertThatThrownBy(
                            () ->
                                    com.talent.management.features.tuition_payment.policy
                                            .TuitionDiscountPolicy.validate(
                                            money(3200000), "FIXED_AMOUNT", new BigDecimal(amount)))
                    .isInstanceOf(ApplicationException.class);
    }

    @Test
    void invalidDiscountCannotBeAppliedThroughPlanApiService() {
        for (String method : List.of("PERCENTAGE", "FIXED_AMOUNT"))
            for (String value : List.of("-1", "10.5", "6000001"))
                assertThatThrownBy(
                                () ->
                                        tuitionPlans.configure(
                                                invoice.getId(),
                                                new ConfigureTuitionPlanRequest(
                                                        List.of(
                                                                new InstallmentDueRequest(
                                                                        money(6000000), today)),
                                                        method,
                                                        new BigDecimal(value),
                                                        "Ưu đãi")))
                        .isInstanceOf(ApplicationException.class);
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("6000000");
    }

    @Test
    void discountAndScheduleAreCommittedTogether() {
        var plan =
                new ConfigureTuitionPlanRequest(
                        List.of(
                                new InstallmentDueRequest(money(2700000), today),
                                new InstallmentDueRequest(money(2700000), today.plusDays(30))),
                        "PERCENTAGE",
                        money(10),
                        "Ưu đãi đăng ký");
        var result = tuitionPlans.configure(invoice.getId(), plan);
        assertThat(result.total()).isEqualByComparingTo("5400000");
        assertThat(result.confirmed()).isTrue();
        assertThat(result.discountAmount()).isEqualByComparingTo("600000");
        cashCollections.cash(invoice.getId(), money(2700000), money(2700000), "discount-first");
        assertThat(queries.view(invoice.getId()).installments().get(1).remaining())
                .isEqualByComparingTo("2700000");
    }

    @Test
    void unconfirmedScheduleCannotBeCollected() {
        assertThat(queries.view(invoice.getId()).confirmed()).isFalse();
        assertThatThrownBy(
                        () ->
                                cashCollections.cash(
                                        invoice.getId(),
                                        money(6000000),
                                        money(6000000),
                                        "unconfirmed"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("chốt lịch");
    }

    private com.talent.management.features.placement_test.entity.PlacementSchedule placement(
            int score) {
        var request =
                db.persist(
                        EnrollmentRequest.builder()
                                .student(invoice.getStudent())
                                .requestedBy(parent)
                                .enrollment(invoice.getEnrollment())
                                .classEntity(clazz)
                                .placementRequested(true)
                                .reviewedAt(LocalDateTime.now())
                                .build());
        return db.persist(
                com.talent.management.features.placement_test.entity.PlacementSchedule.builder()
                        .enrollmentRequest(request)
                        .title("Test piano")
                        .roomName("P1")
                        .testDate(LocalDateTime.now().minusDays(1))
                        .status(
                                com.talent.management.features.placement_test.entity
                                        .PlacementScheduleStatus.COMPLETED)
                        .score(score)
                        .recommendedCourse(clazz.getCourse())
                        .evaluatedAt(LocalDateTime.now().minusHours(1))
                        .build());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "69,0", "70,10", "79,10", "80,20", "89,20", "90,30", "100,30"
    })
    void placementDiscountBoundariesAreLockedAndSnapshotted(int score, int percentage) {
        var test = placement(score);
        var preview = queries.view(invoice.getId()).discountPolicy();
        assertThat(preview.locked()).isTrue();
        assertThat(preview.percentage()).isEqualTo(percentage);
        var expected =
                money(6000000)
                        .subtract(
                                money(6000000)
                                        .multiply(BigDecimal.valueOf(percentage))
                                        .divide(money(100)));
        var result =
                tuitionPlans.configure(
                        invoice.getId(),
                        new ConfigureTuitionPlanRequest(
                                List.of(new InstallmentDueRequest(expected, today)),
                                preview.method(),
                                preview.value(),
                                null));
        assertThat(result.total()).isEqualByComparingTo(expected);
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        test.setScore(1);
        db.flush();
        db.clear();
        var reread = queries.view(invoice.getId());
        assertThat(reread.discountPolicy().placementScore())
                .isEqualByComparingTo(BigDecimal.valueOf(score, 1));
        assertThat(reread.discountPolicy().percentage()).isEqualTo(percentage);
        assertThat(reread.total()).isEqualByComparingTo(expected);
    }

    @Test
    void placementCannotBeOverriddenThroughPlanOrLegacyApi() {
        placement(85);
        assertThatThrownBy(
                        () ->
                                tuitionPlans.configure(
                                        invoice.getId(),
                                        new ConfigureTuitionPlanRequest(
                                                List.of(
                                                        new InstallmentDueRequest(
                                                                money(5400000), today)),
                                                "PERCENTAGE",
                                                money(10),
                                                "Override")))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("20%");
    }

    @Test
    void missingPlacementOrWrongCourseBlocksConfirmationWithSpecificReason() {
        var test = placement(85);
        test.setRecommendedCourse(null);
        db.flush();
        assertThat(queries.view(invoice.getId()).discountPolicy().blockingReason())
                .contains("chưa có kết quả");
        assertThatThrownBy(
                        () ->
                                tuitionPlans.configure(
                                        invoice.getId(),
                                        new ConfigureTuitionPlanRequest(
                                                List.of(
                                                        new InstallmentDueRequest(
                                                                money(6000000), today)))))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("chưa có kết quả");
    }

    @Test
    void invalidScheduleDoesNotWriteDiscountSnapshotOrActivateEnrollment() {
        placement(85);
        assertThatThrownBy(
                        () ->
                                tuitionPlans.configure(
                                        invoice.getId(),
                                        new ConfigureTuitionPlanRequest(
                                                List.of(
                                                        new InstallmentDueRequest(
                                                                BigDecimal.ZERO, today)))))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("lớn hơn 0đ");
        assertThat(
                        db.getEntityManager()
                                .find(
                                        com.talent.management.features.tuition_payment.entity
                                                .TuitionDiscountSnapshot.class,
                                        invoice.getId()))
                .isNull();
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("6000000");
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    private Map<String, Object> webhook(long code, String ref, BigDecimal amount) {
        return Map.of(
                "code",
                "00",
                "success",
                true,
                "data",
                Map.of("code", "00", "orderCode", code, "reference", ref, "amount", amount),
                "signature",
                "test-signed");
    }

    private void configure() {
        tuitionPlans.configure(
                invoice.getId(),
                new ConfigureTuitionPlanRequest(
                        List.of(
                                new InstallmentDueRequest(money(3000000), today),
                                new InstallmentDueRequest(money(1500000), today.plusDays(30)),
                                new InstallmentDueRequest(money(1500000), today.plusDays(60)))));
    }

    @Test
    void overdueFirstPaymentRemainsPayableWithoutReservationExtension() {
        configure();
        invoice.setReservationDeadline(
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(9));
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "late-first");
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(queries.view(invoice.getId()).reservationExpired()).isFalse();
    }

    @Test
    void onlyAdminCanCancelAfterContactAndUnpaidQrClosesAutomatically() {
        configure();
        EnrollmentRequest request =
                db.persist(
                        EnrollmentRequest.builder()
                                .student(invoice.getStudent())
                                .requestedBy(parent)
                                .classEntity(clazz)
                                .enrollment(invoice.getEnrollment())
                                .reviewedBy(staff)
                                .reviewNote("Đã xếp lớp, chờ thanh toán")
                                .status(EnrollmentRequestStatus.PENDING_PAYMENT)
                                .build());
        var qr = gateway.create(invoice.getId(), money(3000000));
        var cancel =
                new CancelReservationRequest(
                        "CANCEL", null, "Đã gọi phụ huynh, không tiếp tục đăng ký", true);
        assertThatThrownBy(() -> reservations.decide(invoice.getId(), cancel))
                .isInstanceOf(ApplicationException.class)
                .satisfies(
                        error ->
                                assertThat(((ApplicationException) error).getStatusCode().value())
                                        .isEqualTo(403));
        as(user("branch-leader", Role.BRANCH_MANAGER));
        assertThatThrownBy(() -> reservations.decide(invoice.getId(), cancel))
                .isInstanceOf(ApplicationException.class)
                .satisfies(
                        error ->
                                assertThat(((ApplicationException) error).getStatusCode().value())
                                        .isEqualTo(403));
        as(manager);
        assertThatThrownBy(
                        () ->
                                reservations.decide(
                                        invoice.getId(),
                                        new CancelReservationRequest(
                                                "CANCEL", null, "Chưa gọi", false)))
                .isInstanceOf(ApplicationException.class);
        var result = reservations.decide(invoice.getId(), cancel);
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(gateway.status(qr.id()).status()).isEqualTo("CANCELLED");
        assertThat(result.reservationHistory()).hasSize(1);
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        db.flush();
        db.clear();
        EnrollmentRequest cancelled = db.find(EnrollmentRequest.class, request.getId());
        assertThat(cancelled.getStatus()).isEqualTo(EnrollmentRequestStatus.CANCELLED);
        assertThat(cancelled.getReviewedBy().getId()).isEqualTo(manager.getId());
        assertThat(cancelled.getReviewNote()).isEqualTo(cancel.reason());
        assertThat(cancelled.getReviewedAt()).isNotNull();
    }

    @Test
    void extensionIsRemovedAndPaidEnrollmentCannotBeCancelled() {
        configure();
        as(manager);
        assertThatThrownBy(
                        () ->
                                reservations.decide(
                                        invoice.getId(),
                                        new CancelReservationRequest(
                                                "EXTEND",
                                                today.plusDays(1).atTime(10, 0),
                                                "Không dùng gia hạn",
                                                true)))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("Không còn");
        as(staff);
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "first-paid");
        as(manager);
        assertThatThrownBy(
                        () ->
                                reservations.decide(
                                        invoice.getId(),
                                        new CancelReservationRequest(
                                                "CANCEL", null, "Đã liên hệ", true)))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("chưa nhận tiền");
    }

    @Test
    void overdueContactDataIsAdminOnlyAndDoesNotCancelEnrollment() {
        configure();
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "first");
        var due =
                installments
                        .findById(queries.view(invoice.getId()).installments().get(1).id())
                        .orElseThrow();
        due.setDueDate(today.minusDays(9));
        assertThatThrownBy(() -> queries.overdueContacts())
                .isInstanceOf(ApplicationException.class)
                .satisfies(
                        error ->
                                assertThat(((ApplicationException) error).getStatusCode().value())
                                        .isEqualTo(403));
        as(manager);
        assertThat(queries.overdueContacts())
                .singleElement()
                .satisfies(
                        c -> {
                            assertThat(c.overdueDays()).isEqualTo(9);
                            assertThat(c.sequence()).isEqualTo(2);
                        });
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {2500000, 3500000})
    void unexpectedGatewayAmountIsRetainedWithoutPartialPaymentOrRefundWorkflow(long amount) {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        paymentResults.webhook(webhook(qr.orderCode(), "UNEXPECTED-" + amount, money(amount)));
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
        assertThat(queries.view(invoice.getId()).paid()).isEqualByComparingTo("0");
        assertThat(events.findByReference("UNEXPECTED-" + amount)).isPresent();
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("RECONCILE");
    }

    @Test
    void virtualAccountOrderWithDifferentDescriptionIsPaidByVerifiedOrderAndAmount() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        var body = webhook(qr.orderCode(), "VALID-VA", money(3000000));
        var data =
                new java.util.HashMap<String, Object>(
                        (java.util.Map<String, Object>) body.get("data"));
        data.put("description", "Different bank transfer description");
        body = new java.util.HashMap<>(body);
        body.put("data", data);
        paymentResults.webhook(body);
        paymentResults.webhook(body);
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        assertThat(queries.view(invoice.getId()).paid()).isEqualByComparingTo("3000000");
    }

    @Test
    void directExtensionClosesUnpaidQrAndOnlyChangesDate() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        var first = queries.view(invoice.getId()).installments().get(0);
        changes.request(
                invoice.getId(),
                new RequestDueDateChangeRequest(first.id(), today.plusDays(1), "Đã trao đổi"));
        verify(payos).cancelPayment(qr.orderCode());
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(installments.findById(first.id()).orElseThrow().getDueDate())
                .isEqualTo(today.plusDays(1));
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    void staffAppliesExtensionImmediatelyButPaidPeriodsAndParentsAreRejected() {
        configure();
        var first = queries.view(invoice.getId()).installments().get(0);
        as(parent);
        assertThatThrownBy(
                        () ->
                                changes.request(
                                        invoice.getId(),
                                        new RequestDueDateChangeRequest(
                                                first.id(), today.plusDays(1), "Xin đổi")))
                .hasMessageContaining("Chỉ Staff hoặc Admin");
        as(staff);
        changes.request(
                invoice.getId(),
                new RequestDueDateChangeRequest(first.id(), today.plusDays(1), "Xin đổi"));
        long request = changes.list().get(0).id();
        assertThatThrownBy(
                        () -> changes.review(request, new ReviewTuitionRequest(true, "Tự duyệt")))
                .hasMessageContaining("Không còn bước duyệt");
        cashCollections.cash(invoice.getId(), money(3000000), money(3000000), "before-extension");
        assertThatThrownBy(
                        () ->
                                changes.request(
                                        invoice.getId(),
                                        new RequestDueDateChangeRequest(
                                                first.id(), today.plusDays(1), "Xin đổi")))
                .hasMessageContaining("đã có khoản thanh toán");
    }

    @Test
    void failedQrCancellationLeavesDateAndHistoryUnchanged() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        var first = queries.view(invoice.getId()).installments().get(0);
        doThrow(new IllegalStateException("Gateway unavailable"))
                .when(payos)
                .cancelPayment(qr.orderCode());
        assertThatThrownBy(
                        () ->
                                changes.extend(
                                        invoice.getId(),
                                        new RequestDueDateChangeRequest(
                                                first.id(), today.plusDays(1), "Xin đổi")))
                .isInstanceOf(RuntimeException.class);
        assertThat(installments.findById(first.id()).orElseThrow().getDueDate())
                .isEqualTo(first.date());
        assertThat(changes.list()).isEmpty();
    }

    @Test
    void adminCanApplyExtensionWithoutAnyApprovalAndKeepsAudit() {
        configure();
        as(manager);
        var first = queries.view(invoice.getId()).installments().get(0);
        changes.extend(
                invoice.getId(),
                new RequestDueDateChangeRequest(first.id(), today.plusDays(1), "Đã trao đổi"));
        assertThat(installments.findById(first.id()).orElseThrow().getDueDate())
                .isEqualTo(today.plusDays(1));
        assertThat(changes.list())
                .singleElement()
                .satisfies(
                        h -> {
                            assertThat(h.status()).isEqualTo("APPROVED");
                            assertThat(h.requestedBy()).isEqualTo(manager.getFullName());
                            assertThat(h.reviewedBy()).isEqualTo(manager.getFullName());
                        });
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    void paymentArrivingWhileClosingQrPreventsDirectDateChange() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        var first = queries.view(invoice.getId()).installments().get(0);
        doAnswer(
                        invocation -> {
                            paymentResults.webhook(
                                    webhook(qr.orderCode(), "PAY-DURING-EXTEND", money(3000000)));
                            return null;
                        })
                .when(payos)
                .cancelPayment(qr.orderCode());
        assertThatThrownBy(
                        () ->
                                changes.extend(
                                        invoice.getId(),
                                        new RequestDueDateChangeRequest(
                                                first.id(), today.plusDays(1), "Xin đổi")))
                .isInstanceOf(ApplicationException.class);
        assertThat(installments.findById(first.id()).orElseThrow().getDueDate())
                .isEqualTo(first.date());
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        assertThat(changes.list()).isEmpty();
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    private void as(User user) {
        when(current.getCurrentUser()).thenReturn(user);
    }

    private User user(String name, Role role) {
        return db.persist(
                User.builder().username(name).password("test").fullName(name).role(role).build());
    }
}
