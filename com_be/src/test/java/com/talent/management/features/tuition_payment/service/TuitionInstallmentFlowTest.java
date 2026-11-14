package com.talent.management.features.tuition_payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.talent.management.features.tuition_payment.dto.request.InstallmentRequests;
import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.*;
import com.talent.management.features.tuition_payment.dto.response.PayOSPaymentLinkResponse;
import com.talent.management.features.tuition_payment.gateway.PayOSGateway;
import com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper;
import com.talent.management.features.tuition_payment.repository.*;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.*;
import com.talent.management.shared.service.CurrentUserService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import com.talent.management.features.notification.service.impl.NotificationServiceImpl;
import com.talent.management.features.notification.repository.NotificationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@DataJpaTest
@Import({
    TuitionPlanService.class,
    PaymentPostingService.class,
    InstallmentGatewayService.class,
    PlanChangeService.class,
    TuitionNotificationService.class,
    TuitionReversalService.class,
    TuitionReservationService.class,
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
    @Autowired TuitionPlanService ledger;
    @Autowired PaymentPostingService posting;
    @Autowired InstallmentGatewayService gateway;
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
    @Autowired com.talent.management.features.admin_management.repository.AdminDashboardRepository dashboard;
    User parent, other, staff, manager;
    Invoice invoice;
    ClassEntity clazz;
    LocalDate today = TuitionPlanService.today();

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
        assertThat(gateway.webhook(body)).isNull();
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
    void installmentNotificationsUseBalanceAndDoNotDuplicateEnrollmentOrReplays() {
        User teacher = user("teacher-notice", Role.TEACHER);
        User branchManager = user("branch-notice", Role.BRANCH_MANAGER);
        clazz.setTeacher(teacher);
        ledger.configure(invoice.getId(), new InstallmentRequests.Plan(List.of(
                new InstallmentRequests.Due(money(3000000), today),
                new InstallmentRequests.Due(money(3000000), today.plusDays(30)))));
        posting.cash(invoice.getId(), money(3000000), money(3000000), "notice-first");
        posting.cash(invoice.getId(), money(3000000), money(3000000), "notice-first");
        verify(notificationService).sendNotification(eq(parent), eq(staff), eq("Đã nhận học phí"),
                contains("còn phải đóng 3.000.000"), eq("PAYMENT_SUCCESS"), anyLong(),
                eq("/pages/parent-tuition.html?invoiceId=" + invoice.getId()));
        posting.cash(invoice.getId(), money(3000000), money(3000000), "notice-last");
        verify(notificationService, times(1)).sendNotification(eq(teacher), eq(staff),
                eq("Học viên nhập học chính thức"), anyString(), eq("ENROLLMENT_COMPLETED"), anyLong(), anyString());
        verify(notificationService, times(2)).sendNotification(eq(parent), eq(staff),
                eq("Đã nhận học phí"), anyString(), eq("PAYMENT_SUCCESS"), anyLong(), anyString());
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
                .singleElement().satisfies(n -> {
                    assertThat(n.getType()).isEqualTo("TUITION_PLAN_CONFIRMED");
                    assertThat(n.getMessage()).contains("Con thử nghiệm", "INV-FLOW", "3.000.000", "đợt 3");
                    assertThat(n.getTargetUrl()).isEqualTo("/pages/parent-tuition.html?invoiceId=" + invoice.getId());
                    assertThat(n.getSender().getId()).isEqualTo(staff.getId());
                });
        assertThat(bellNotifications.findByRecipientIdAndIsReadFalse(other.getId())).isEmpty();
    }

    @Test
    void scheduledRemindersReachBellOncePerStageAndDoNotResendReadNotifications() {
        ledger.configure(invoice.getId(), new InstallmentRequests.Plan(List.of(
                new InstallmentRequests.Due(money(3000000), today.plusDays(1)),
                new InstallmentRequests.Due(money(3000000), today.plusDays(30)))));
        var due = installments.findById(ledger.view(invoice.getId()).installments().get(0).id()).orElseThrow();
        when(current.getCurrentUser()).thenThrow(new IllegalStateException("No login in scheduler"));
        notices.generate(invoice.getId());
        notices.generate(invoice.getId());
        var upcoming = bellNotifications.findByRecipientIdAndIsReadFalse(parent.getId()).stream()
                .filter(n -> n.getType().equals("TUITION_UPCOMING")).toList();
        assertThat(upcoming).singleElement().satisfies(n -> {
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
        assertThat(all.stream().filter(n -> n.getType().startsWith("TUITION_")
                && !n.getType().equals("TUITION_PLAN_CONFIRMED")))
                .extracting(Notification::getType)
                .containsExactlyInAnyOrder("TUITION_UPCOMING", "TUITION_DUE_TODAY", "TUITION_OVERDUE");
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
        legacy.setEventKey(parent.getId() + "-" + due.getId() + "-" + plan.getRevision() + "-DUE_TODAY");
        tuitionNotices.saveAndFlush(legacy);
        notices.generate(invoice.getId());
        notices.generate(invoice.getId());
        assertThat(bellNotifications.findTop50ByRecipientIdOrderByCreatedAtDesc(parent.getId()).stream()
                .filter(n -> n.getType().equals("TUITION_DUE_TODAY")))
                .singleElement().satisfies(n -> assertThat(n.getReferenceId()).isEqualTo(legacy.getId()));
    }

    @Test
    void settledInvoiceDoesNotCreateDueBellNotifications() {
        configure();
        posting.cash(invoice.getId(), money(3000000), money(3000000), "settled-first");
        posting.cash(invoice.getId(), money(1500000), money(1500000), "settled-second");
        posting.cash(invoice.getId(), money(1500000), money(1500000), "settled-last");
        notices.generate(invoice.getId());
        assertThat(bellNotifications.findTop50ByRecipientIdOrderByCreatedAtDesc(parent.getId()).stream()
                .filter(n -> List.of("TUITION_UPCOMING", "TUITION_DUE_TODAY", "TUITION_OVERDUE").contains(n.getType())))
                .isEmpty();
    }

    private List<Notification> paymentNotifications(User recipient) {
        return bellNotifications.findTop50ByRecipientIdOrderByCreatedAtDesc(recipient.getId()).stream()
                .filter(n -> n.getType().equals("PAYMENT_SUCCESS")).toList();
    }

    @Test
    void cashInstallmentsAllocateEarliestAndEnrollOnlyOnce() {
        configure();
        Payment first = posting.cash(invoice.getId(), money(3000000), money(3500000), "first");
        assertThat(first.getChangeAmount()).isEqualByComparingTo("500000");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(clazz.getCurrentStudents()).isEqualTo(1);
        assertThat(posting.cash(invoice.getId(), money(3000000), money(3500000), "first").getId())
                .isEqualTo(first.getId());
        posting.cash(invoice.getId(), money(1500000), money(1500000), "second");
        var after = ledger.view(invoice.getId());
        assertThat(after.installments().get(1).remaining()).isEqualByComparingTo("0");
        posting.cash(invoice.getId(), money(1500000), money(1500000), "last");
        assertThat(ledger.view(invoice.getId()).balance()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(clazz.getCurrentStudents()).isEqualTo(1);
        as(parent);
        assertThat(ledger.list())
                .singleElement()
                .satisfies(i -> assertThat(i.receipts()).hasSize(3));
    }

    @Test
    void fullFreeEnrollmentWorksWithoutBankTransfer() {
        invoice.setOriginalAmount(BigDecimal.ZERO);
        invoice.setFinalAmount(BigDecimal.ZERO);
        posting.cash(invoice.getId(), BigDecimal.ZERO, BigDecimal.ZERO, "free");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(clazz.getCurrentStudents()).isEqualTo(1);
    }

    @Test
    void parentCannotReadAnotherChild() {
        as(other);
        assertThat(ledger.list()).isEmpty();
        assertThatThrownBy(() -> ledger.view(invoice.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
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
                .isInstanceOf(ResponseStatusException.class);
        assertThat(intents.findByInvoiceIdAndStatus(invoice.getId(), "UNCERTAIN")).hasSize(1);
    }

    @Test
    void switchingToCashClosesQrAndReturnsExcessCash() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        gateway.prepareCashPayment(invoice.getId());
        verify(payos).cancelPayment(qr.orderCode());
        var payment = posting.cash(invoice.getId(), money(3000000), money(3500000), "cash-after-qr");
        assertThat(payment.getChangeAmount()).isEqualByComparingTo("500000");
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(ledger.view(invoice.getId()).paid()).isEqualByComparingTo("3000000");
    }

    @Test
    void failedGatewayCancellationDoesNotAllowCashCollection() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        org.mockito.Mockito.doThrow(new IllegalStateException("Gateway unavailable"))
                .when(payos).cancelPayment(qr.orderCode());
        assertThatThrownBy(() -> gateway.prepareCashPayment(invoice.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> posting.cash(invoice.getId(), money(3000000), money(3500000), "blocked"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    void belowFirstThresholdIsRejected() {
        configure();
        assertThatThrownBy(
                        () ->
                                posting.cash(
                                        invoice.getId(), money(1000000), money(1000000), "small"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("kỳ đầu");
    }

    @Test
    void delayedQrAfterCashIsRecordedForReconciliation() {
        configure();
        var qr = gateway.create(invoice.getId(), money(3000000));
        gateway.cancel(qr.id());
        posting.cash(invoice.getId(), money(3000000), money(3000000), "cash-first");
        posting.cash(invoice.getId(), money(1500000), money(1500000), "cash-second");
        posting.cash(invoice.getId(), money(1500000), money(1500000), "cash-last");
        var payload = webhook(qr.orderCode(), "REF-LATE", money(3000000));
        // Simulated events enter only through the explicitly enabled admin simulation endpoint.
        gateway.simulate(qr.id());
        assertThat(ledger.view(invoice.getId()).balance()).isEqualByComparingTo("0");
        assertThat(events.findByStatusOrderByIdDesc("RECONCILE"))
                .singleElement()
                .satisfies(e -> assertThat(e.getReceivedAmount()).isEqualByComparingTo("3000000"));
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(3);
    }

    @Test
    void onlineReceiptIsIdempotentAndSharesBalanceWithCashier() {
        configure();
        posting.cash(invoice.getId(), money(3000000), money(3000000), "first");
        as(parent);
        var qr = gateway.create(invoice.getId(), money(1500000));
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("6000000");
        gateway.simulate(qr.id());
        gateway.simulate(qr.id());
        as(staff);
        assertThat(ledger.view(invoice.getId()).balance()).isEqualByComparingTo("1500000");
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(2);
        assertThat(clazz.getCurrentStudents()).isEqualTo(1);
    }

    @Test
    void manualBankConfirmationAndLateWebhookProduceOneReceipt() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        var input =
                new InstallmentRequests.ManualBankConfirmation(
                        money(3000000),
                        "BANK-MANUAL-1",
                        "Đã kiểm tra ghi có, webhook chưa đến",
                        true);
        gateway.confirmBank(qr.id(), input);
        gateway.confirmBank(qr.id(), input);
        gateway.webhook(webhook(qr.orderCode(), "BANK-MANUAL-1", money(3000000)));
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        assertThat(ledger.view(invoice.getId()).paid()).isEqualByComparingTo("3000000");
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
        gateway.webhook(webhook(qr.orderCode(), "BANK-NOTIFY-ONCE", money(3000000)));
        gateway.webhook(webhook(qr.orderCode(), "BANK-NOTIFY-ONCE", money(3000000)));
        assertThat(paymentNotifications(parent)).singleElement()
                .satisfies(n -> assertThat(n.getTargetUrl()).contains("parent-tuition.html?invoiceId=" + invoice.getId()));
        assertThat(paymentNotifications(staff)).singleElement()
                .satisfies(n -> assertThat(n.getTargetUrl()).contains("tuition-receivables.html?invoiceId=" + invoice.getId()));
        assertThat(paymentNotifications(manager)).hasSize(1);
        assertThat(paymentNotifications(other)).isEmpty();
    }

    @Test
    void manualConfirmationAfterAutomaticWebhookDoesNotCollectAgain() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        gateway.webhook(webhook(qr.orderCode(), "BANK-AUTO-1", money(3000000)));
        gateway.confirmBank(
                qr.id(),
                new InstallmentRequests.ManualBankConfirmation(
                        money(3000000), "BANK-AUTO-1", "Kiểm tra lại", true));
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        assertThatThrownBy(
                        () ->
                                gateway.confirmBank(
                                        qr.id(),
                                        new InstallmentRequests.ManualBankConfirmation(
                                                money(3000000),
                                                "ANOTHER-REF",
                                                "Kiểm tra lại",
                                                true)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void manualConfirmationRequiresStaffExactAmountAndBankVerification() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        as(parent);
        assertThatThrownBy(
                        () ->
                                gateway.confirmBank(
                                        qr.id(),
                                        new InstallmentRequests.ManualBankConfirmation(
                                                money(3000000), "BANK-1", "Đã xem", true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        as(staff);
        for (var input :
                List.of(
                        new InstallmentRequests.ManualBankConfirmation(
                                money(1500000), "BANK-1", "Đã xem", true),
                        new InstallmentRequests.ManualBankConfirmation(
                                money(3000000), "BANK-1", "Đã xem", false),
                        new InstallmentRequests.ManualBankConfirmation(
                                money(3000000), " ", "Đã xem", true))) {
            assertThatThrownBy(() -> gateway.confirmBank(qr.id(), input))
                    .isInstanceOf(ResponseStatusException.class);
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
        assertThat(ledger.view(invoice.getId()).receipts()).hasSize(1);
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
                .when(payos).createPaymentLink(any(), any(), anyLong(), anyBoolean());
    }

    @Test
    void paymentWhileChangePendingPreventsStaleApproval() {
        configure();
        posting.cash(invoice.getId(), money(3000000), money(3000000), "first");
        var second = ledger.view(invoice.getId()).installments().get(1);
        changes.request(
                invoice.getId(),
                new InstallmentRequests.Change(
                        second.id(), today.plusDays(35), "Gia đình xin đổi hạn"));
        posting.cash(invoice.getId(), money(1500000), money(1500000), "extra");
        as(manager);
        long id = changes.list().get(0).id();
        assertThatThrownBy(
                        () ->
                                changes.review(
                                        id, new InstallmentRequests.Review(true, "Đã trao đổi")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Có giao dịch");
    }

    @Test
    void remindersDoNotDuplicateAndExtensionReplacesOldNotice() {
        configure();
        posting.cash(invoice.getId(), money(3000000), money(3000000), "first");
        var due =
                installments
                        .findById(ledger.view(invoice.getId()).installments().get(1).id())
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
                new InstallmentRequests.Change(due.getId(), today.plusDays(10), "Xin gia hạn"));
        as(manager);
        changes.review(changes.list().get(0).id(), new InstallmentRequests.Review(true, "Đồng ý"));
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
        var result = ledger.view(invoice.getId());
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.paid()).isEqualByComparingTo("0");
        assertThat(result.receipts()).isEmpty();
    }

    @Test
    void cashierCanRequestOnlyTheirOwnCashCorrectionWhileAdminCanRequestAny() {
        configure();
        var payment = posting.cash(invoice.getId(), money(3000000), money(3000000), "owned-receipt");
        assertThat(ledger.view(invoice.getId()).receipts().get(0).cashierId()).isEqualTo(staff.getId());
        as(user("different-cashier", Role.STAFF));
        assertThatThrownBy(() -> reversals.request(payment.getId(), "Khoản người khác thu"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        as(manager);
        reversals.request(payment.getId(), "Admin kiểm tra khoản thu của nhân viên");
        assertThat(reversals.list()).singleElement()
                .satisfies(item -> assertThat(item.requestedBy()).isEqualTo(manager.getFullName()));
    }

    @Test
    void approvedCashCorrectionRestoresDebtAndKeepsEnrollmentHistory() {
        configure();
        Payment payment = posting.cash(invoice.getId(), money(3000000), money(3000000), "wrong");
        reversals.request(payment.getId(), "Nhập nhầm số tiền mặt chưa nhận");
        assertThatThrownBy(() -> reversals.list())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        as(manager);
        var requestId = reversals.list().get(0).id();
        as(staff);
        assertThatThrownBy(
                        () ->
                                reversals.review(
                                        requestId,
                                        new InstallmentRequests.Review(true, "Tự duyệt")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        as(manager);
        reversals.review(
                reversals.list().get(0).id(),
                new InstallmentRequests.Review(true, "Đã kiểm đếm và xác minh"));
        var view = ledger.view(invoice.getId());
        assertThat(view.paid()).isEqualByComparingTo("0");
        assertThat(view.receipts().get(0).reversed()).isTrue();
        assertThat(view.receipts().get(0).code()).matches("PT-\\d{8}-\\d{6,}");
        assertThat(payment.getPaymentCode()).startsWith("PAY-");
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(clazz.getCurrentStudents()).isEqualTo(1);
    }

    @Test
    void cashCorrectionRejectsParentsAndManagerSelfApproval() {
        configure();
        Payment payment = posting.cash(invoice.getId(), money(3000000), money(3000000), "self-review");
        as(parent);
        assertThatThrownBy(() -> reversals.request(payment.getId(), "Nhập nhầm"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        as(manager);
        reversals.request(payment.getId(), "Nhập nhầm");
        var requestId = reversals.list().get(0).id();
        assertThatThrownBy(() -> reversals.review(requestId,
                        new InstallmentRequests.Review(true, "Tự duyệt")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không được tự duyệt");
    }

    @Test
    void amountMismatchFromBankIsPreservedWithoutPosting() {
        configure();
        var qr = gateway.create(invoice.getId(), money(3000000));
        // Model a genuine gateway intent and a correctly signed callback with a different received
        // amount.
        var intent = db.find(TuitionPaymentIntent.class, qr.id());
        intent.setSimulated(false);
        gateway.webhook(webhook(qr.orderCode(), "DIFFERENT", money(3500000)));
        assertThat(events.findByReference("DIFFERENT")).isPresent();
        assertThat(events.findByStatusOrderByIdDesc("RECONCILE")).hasSize(1);
        assertThat(ledger.paid(invoice)).isEqualByComparingTo("0");
    }

    @Test
    void authenticatedUnmatchedBankTransferIsRetainedForReview() {
        gateway.webhook(webhook(987654321, "UNKNOWN-ORDER", money(1000000)));
        gateway.webhook(webhook(987654321, "UNKNOWN-ORDER", money(1000000)));
        assertThat(events.findByReference("UNKNOWN-ORDER")).isPresent();
        assertThat(events.findByStatusOrderByIdDesc("RECONCILE"))
                .singleElement()
                .satisfies(e -> assertThat(e.getIntent()).isNull());
    }

    @Test
    void cashRoundingExamplesRespectExactTenderAndDenominations() {
        int[][] examples = {{4500,5000,500,0},{4700,5000,400,100},{4850,5000,200,50},{4701,4701,0,0}};
        for (int[] example : examples) {
            var quote = CashTenderPolicy.quote(money(example[0]), money(example[1]));
            assertThat(quote.change()).isEqualByComparingTo(String.valueOf(example[2]));
            assertThat(quote.support()).isEqualByComparingTo(String.valueOf(example[3]));
        }
        for (String invalid : List.of("-1","4499","5000.5","5001"))
            assertThatThrownBy(() -> CashTenderPolicy.quote(money(4500), new BigDecimal(invalid)))
                    .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void roundedCashSettlesTuitionButRevenueAndReceiptUseActualCash() {
        invoice.setOriginalAmount(money(4700));invoice.setFinalAmount(money(4700));
        ledger.configure(invoice.getId(), new InstallmentRequests.Plan(List.of(new InstallmentRequests.Due(money(4700),today))));
        var receipt = posting.collectReceipt(invoice.getId(), money(4700), money(5000), "rounding");
        assertThat(receipt.getChangeAmount()).isEqualByComparingTo("400");
        assertThat(receipt.getCashRoundingAmount()).isEqualByComparingTo("100");
        assertThat(receipt.getNetReceivedAmount()).isEqualByComparingTo("4600");
        assertThat(ledger.view(invoice.getId()).balance()).isEqualByComparingTo("0");
        assertThat(ledger.view(invoice.getId()).receipts().get(0).cashRoundingAmount()).isEqualByComparingTo("100");
        assertThat(payments.sumAmountSince(today.atStartOfDay())).isEqualByComparingTo("4600");
        assertThat(dashboard.revenue(today,today)).singleElement()
                .satisfies(point -> assertThat(point.amount()).isEqualByComparingTo("4600"));
        assertThat(posting.replayCashReceipt(invoice.getId(),money(4700),money(5000),"rounding")).isPresent();
        assertThat(posting.cash(invoice.getId(), money(4700), money(5000), "rounding").getId()).isEqualTo(receipt.getPaymentId());
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        reversals.request(receipt.getPaymentId(),"Thu nhầm");as(manager);
        reversals.review(reversals.list().get(0).id(),new InstallmentRequests.Review(true,"Đã kiểm tra"));
        assertThat(payments.sumAmountSince(today.atStartOfDay())).isEqualByComparingTo("0");
        assertThat(ledger.view(invoice.getId()).balance()).isEqualByComparingTo("4700");
    }

    @Test
    void changedAmountsAndInvalidTenderCannotCancelQrOrCollect() {
        configure();var qr=gateway.create(invoice.getId(),money(3000000));
        assertThatThrownBy(() -> gateway.prepareCashPayment(invoice.getId(),money(3000001),money(5000000)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> gateway.prepareCashPayment(invoice.getId(),money(3000000),new BigDecimal("3500000.5")))
                .isInstanceOf(ResponseStatusException.class);
        verify(payos,never()).cancelPayment(anyLong());
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
        as(parent);
        assertThatThrownBy(() -> gateway.create(invoice.getId(),money(4000000)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(payments.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    void equalNextInstallmentCannotCauseCashCollectionAfterQrWasPaidDuringCancel() {
        ledger.configure(invoice.getId(),new InstallmentRequests.Plan(List.of(
                new InstallmentRequests.Due(money(3000000),today),
                new InstallmentRequests.Due(money(3000000),today.plusDays(30)))));
        useRealGatewayStub();var qr=gateway.create(invoice.getId(),money(3000000));
        doAnswer(call -> {gateway.webhook(webhook(qr.orderCode(),"PAID-NO-CASH",money(3000000)));return null;})
                .when(payos).cancelPayment(qr.orderCode());
        assertThatThrownBy(() -> gateway.prepareCashPayment(invoice.getId(),money(3000000),money(3500000)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Không thu thêm tiền mặt");
        assertThat(payments.findByInvoiceId(invoice.getId())).singleElement()
                .satisfies(payment -> assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.VIET_QR));
    }

    @Test
    void closedQrIsNotReusedAndCancellationIsIdempotent() {
        configure();var first=gateway.create(invoice.getId(),money(3000000));
        gateway.cancel(first.id());gateway.cancel(first.id());
        var next=gateway.create(invoice.getId(),money(3000000));
        assertThat(next.id()).isNotEqualTo(first.id());assertThat(next.orderCode()).isNotEqualTo(first.orderCode());
        assertThat(intents.findById(first.id()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(next.status()).isEqualTo("PENDING");
    }

    @Test
    void expiryCancelsUnpaidQrWithoutUserAuthentication() {
        configure();var qr=gateway.create(invoice.getId(),money(3000000));
        var intent=intents.findById(qr.id()).orElseThrow();intent.setCreatedAt(LocalDateTime.now().minusMinutes(6));
        intents.flush();
        when(current.getCurrentUser()).thenThrow(new IllegalStateException("No user on scheduler"));
        gateway.expireUnpaidRequests();
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void paymentArrivingDuringCancellationWinsAndIsNotPostedTwice() {
        configure();useRealGatewayStub();var qr=gateway.create(invoice.getId(),money(3000000));
        doAnswer(call -> {gateway.webhook(webhook(qr.orderCode(),"BANK-RACE",money(3000000)));return null;})
                .when(payos).cancelPayment(qr.orderCode());
        gateway.cancel(qr.id());
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("PAID");
        gateway.webhook(webhook(qr.orderCode(),"BANK-RACE",money(3000000)));
        gateway.cancel(qr.id());
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
    }

    @Test
    void missedWebhookIsRecoveredBeforeCancellingPaidGatewayRequest() {
        configure();useRealGatewayStub();var qr=gateway.create(invoice.getId(),money(3000000));
        doThrow(new IllegalStateException("Already paid")).when(payos).cancelPayment(qr.orderCode());
        when(payos.paymentInformation(qr.orderCode())).thenReturn(new PayOSGateway.GatewayPayment(
                "PAID",money(3000000),List.of(new PayOSGateway.BankTransfer(money(3000000),"BANK-LOOKUP"))));
        gateway.cancel(qr.id());
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("PAID");
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
    }

    @Test
    void parentCannotCancelAnotherParentsQr() {
        configure();var qr=gateway.create(invoice.getId(),money(3000000));as(other);
        assertThatThrownBy(() -> gateway.cancel(qr.id())).isInstanceOf(ResponseStatusException.class);
        assertThat(intents.findById(qr.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void discountInputsAreWholeNumbersWithinInclusiveBounds() {
        for (String rate : List.of("0", "10", "100"))
            ledger.validateDiscountValue(money(3200000), "PERCENTAGE", new BigDecimal(rate));
        for (String amount : List.of("1000", "1001", "3200000"))
            ledger.validateDiscountValue(money(3200000), "FIXED_AMOUNT", new BigDecimal(amount));
        for (String rate : List.of("-1", "101", "10.5", "10.0"))
            assertThatThrownBy(() -> ledger.validateDiscountValue(money(3200000), "PERCENTAGE", new BigDecimal(rate)))
                    .isInstanceOf(ResponseStatusException.class);
        for (String amount : List.of("-1", "0", "999", "3200001", "1000.5", "1000.00"))
            assertThatThrownBy(() -> ledger.validateDiscountValue(money(3200000), "FIXED_AMOUNT", new BigDecimal(amount)))
                    .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void invalidDiscountCannotBeAppliedThroughPlanApiService() {
        for (String method : List.of("PERCENTAGE", "FIXED_AMOUNT"))
            for (String value : List.of("-1", "10.5", "6000001"))
                assertThatThrownBy(() -> ledger.configure(invoice.getId(), new InstallmentRequests.Plan(
                        List.of(new InstallmentRequests.Due(money(6000000), today)),
                        method, new BigDecimal(value), "Ưu đãi")))
                        .isInstanceOf(ResponseStatusException.class);
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("6000000");
    }

    @Test
    void discountAndScheduleAreCommittedTogether() {
        var plan =
                new InstallmentRequests.Plan(
                        List.of(
                                new InstallmentRequests.Due(money(2700000), today),
                                new InstallmentRequests.Due(money(2700000), today.plusDays(30))),
                        "PERCENTAGE",
                        money(10),
                        "Ưu đãi đăng ký");
        var result = ledger.configure(invoice.getId(), plan);
        assertThat(result.total()).isEqualByComparingTo("5400000");
        assertThat(result.confirmed()).isTrue();
        assertThat(result.discountAmount()).isEqualByComparingTo("600000");
        posting.cash(invoice.getId(), money(2700000), money(2700000), "discount-first");
        assertThat(ledger.view(invoice.getId()).installments().get(1).remaining())
                .isEqualByComparingTo("2700000");
    }

    @Test
    void unconfirmedScheduleCannotBeCollected() {
        assertThat(ledger.view(invoice.getId()).confirmed()).isFalse();
        assertThatThrownBy(
                        () ->
                                posting.cash(
                                        invoice.getId(),
                                        money(6000000),
                                        money(6000000),
                                        "unconfirmed"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chốt lịch");
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
        ledger.configure(
                invoice.getId(),
                new InstallmentRequests.Plan(
                        List.of(
                                new InstallmentRequests.Due(money(3000000), today),
                                new InstallmentRequests.Due(money(1500000), today.plusDays(30)),
                                new InstallmentRequests.Due(money(1500000), today.plusDays(60)))));
    }

    @Test
    void overdueFirstPaymentRemainsPayableWithoutReservationExtension() {
        configure();
        invoice.setReservationDeadline(
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(9));
        posting.cash(invoice.getId(), money(3000000), money(3000000), "late-first");
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(ledger.view(invoice.getId()).reservationExpired()).isFalse();
    }

    @Test
    void onlyAdminCanCancelAfterContactAndUnpaidQrClosesAutomatically() {
        configure();
        var qr = gateway.create(invoice.getId(), money(3000000));
        var cancel =
                new InstallmentRequests.Reservation(
                        "CANCEL", null, "Đã gọi phụ huynh, không tiếp tục đăng ký", true);
        assertThatThrownBy(() -> reservations.decide(invoice.getId(), cancel))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        as(user("branch-leader", Role.BRANCH_MANAGER));
        assertThatThrownBy(() -> reservations.decide(invoice.getId(), cancel))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        as(manager);
        assertThatThrownBy(
                        () ->
                                reservations.decide(
                                        invoice.getId(),
                                        new InstallmentRequests.Reservation(
                                                "CANCEL", null, "Chưa gọi", false)))
                .isInstanceOf(ResponseStatusException.class);
        var result = reservations.decide(invoice.getId(), cancel);
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(gateway.status(qr.id()).status()).isEqualTo("CANCELLED");
        assertThat(result.reservationHistory()).hasSize(1);
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void extensionIsRemovedAndPaidEnrollmentCannotBeCancelled() {
        configure();
        as(manager);
        assertThatThrownBy(
                        () ->
                                reservations.decide(
                                        invoice.getId(),
                                        new InstallmentRequests.Reservation(
                                                "EXTEND",
                                                today.plusDays(1).atTime(10, 0),
                                                "Không dùng gia hạn",
                                                true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Không còn");
        as(staff);
        posting.cash(invoice.getId(), money(3000000), money(3000000), "first-paid");
        as(manager);
        assertThatThrownBy(
                        () ->
                                reservations.decide(
                                        invoice.getId(),
                                        new InstallmentRequests.Reservation(
                                                "CANCEL", null, "Đã liên hệ", true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chưa nhận tiền");
    }

    @Test
    void overdueContactDataIsAdminOnlyAndDoesNotCancelEnrollment() {
        configure();
        posting.cash(invoice.getId(), money(3000000), money(3000000), "first");
        var due =
                installments
                        .findById(ledger.view(invoice.getId()).installments().get(1).id())
                        .orElseThrow();
        due.setDueDate(today.minusDays(9));
        assertThatThrownBy(() -> ledger.overdueContacts())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        as(manager);
        assertThat(ledger.overdueContacts())
                .singleElement()
                .satisfies(
                        c -> {
                            assertThat(c.overdueDays()).isEqualTo(9);
                            assertThat(c.sequence()).isEqualTo(2);
                        });
        assertThat(invoice.getEnrollment().getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void adminCanVerifyBankMismatchThroughTheExistingConfirmationAction() {
        configure();
        useRealGatewayStub();
        var qr = gateway.create(invoice.getId(), money(3000000));
        gateway.webhook(webhook(qr.orderCode(), "BANK-CHECK", money(3500000)));
        as(manager);
        gateway.confirmBank(
                qr.id(),
                new InstallmentRequests.ManualBankConfirmation(
                        money(3500000),
                        "BANK-CHECK",
                        "Đã kiểm tra số tiền ngân hàng thực nhận",
                        true));
        gateway.webhook(webhook(qr.orderCode(), "BANK-CHECK", money(3500000)));
        assertThat(payments.findByInvoiceId(invoice.getId())).hasSize(1);
        assertThat(ledger.view(invoice.getId()).paid()).isEqualByComparingTo("3500000");
        assertThat(events.findByStatusOrderByIdDesc("RECONCILE")).isEmpty();
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
