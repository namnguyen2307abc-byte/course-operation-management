package com.talent.management.features.tuition_payment.service.impl;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.tuition_payment.dto.request.ApplyDiscountRequest;
import com.talent.management.features.tuition_payment.dto.request.CreateReservationRequest;
import com.talent.management.features.tuition_payment.dto.request.ProcessPaymentRequest;
import com.talent.management.features.tuition_payment.dto.response.CashierDashboardStatsResponse;
import com.talent.management.features.tuition_payment.dto.response.CashierOptionResponse;
import com.talent.management.features.tuition_payment.dto.response.InvoicePaymentStatusResponse;
import com.talent.management.features.tuition_payment.dto.response.PayOSPaymentLinkResponse;
import com.talent.management.features.tuition_payment.dto.response.PaymentHistoryReportResponse;
import com.talent.management.features.tuition_payment.dto.response.PaymentReceiptResponse;
import com.talent.management.features.tuition_payment.dto.response.PaymentSummaryResponse;
import com.talent.management.features.tuition_payment.dto.response.PendingInvoiceResponse;
import com.talent.management.features.tuition_payment.dto.response.VietQrResponse;
import com.talent.management.features.tuition_payment.exception.TuitionPaymentException;
import com.talent.management.features.tuition_payment.gateway.PayOSGateway;
import com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper;
import com.talent.management.features.tuition_payment.repository.ClassRepository;
import com.talent.management.features.tuition_payment.repository.EnrollmentRepository;
import com.talent.management.features.tuition_payment.repository.InvoiceRepository;
import com.talent.management.features.tuition_payment.repository.PaymentRepository;
import com.talent.management.features.tuition_payment.repository.TuitionLedgerRepository;
import com.talent.management.features.tuition_payment.repository.TuitionReversalRepository;
import com.talent.management.features.tuition_payment.service.CashCollectionService;
import com.talent.management.features.tuition_payment.service.PaymentIntentService;
import com.talent.management.features.tuition_payment.service.PaymentResultService;
import com.talent.management.features.tuition_payment.service.TuitionLedgerService;
import com.talent.management.features.tuition_payment.service.TuitionPaymentService;
import com.talent.management.features.tuition_payment.service.TuitionQueryService;
import com.talent.management.shared.entity.ClassEntity;
import com.talent.management.shared.entity.Course;
import com.talent.management.shared.entity.Enrollment;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.entity.Payment;
import com.talent.management.shared.entity.Student;
import com.talent.management.shared.entity.User;
import com.talent.management.shared.enums.DiscountType;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.enums.InvoiceStatus;
import com.talent.management.shared.enums.PaymentMethod;
import com.talent.management.shared.enums.PaymentStatus;
import com.talent.management.shared.enums.Role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TuitionPaymentServiceImpl implements TuitionPaymentService {

    private final TuitionLedgerService tuitionLedgerService;
    private final TuitionQueryService tuitionQueryService;
    private final CashCollectionService cashCollectionService;
    private final PaymentIntentService paymentIntentService;
    private final PaymentResultService paymentResultService;
    private final TuitionReversalRepository reversals;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TuitionPaymentMapper tuitionPaymentMapper;
    private final PayOSGateway payOSGateway;
    private final TuitionLedgerRepository ledgerRepository;
    private final com.talent.management.features.tuition_payment.service.TuitionDiscountService
            discountService;

    private static final String ACADEMY_BANK_ID = "970405";
    private static final String ACADEMY_BANK_NAME = "Ngân hàng Nông nghiệp & PTNT (Agribank)";
    private static final String ACADEMY_ACCOUNT_NO = "3511205288130";
    private static final String ACADEMY_ACCOUNT_NAME = "HOANG DUC THUAN";

    @Override
    @Transactional(readOnly = true)
    public List<PendingInvoiceResponse> searchPendingInvoices(String keyword) {
        String trimmedKeyword = keyword != null ? keyword.trim() : "";
        log.info(
                "Tra cứu phiếu giữ chỗ / hóa đơn chờ thanh toán với keyword: '{}'", trimmedKeyword);
        List<Invoice> invoices =
                new ArrayList<>(
                        invoiceRepository.searchInvoicesByStatusAndKeyword(
                                InvoiceStatus.UNPAID, trimmedKeyword));
        invoices.addAll(
                invoiceRepository.searchInvoicesByStatusAndKeyword(
                        InvoiceStatus.PARTIALLY_PAID, trimmedKeyword));
        return invoices.stream().map(tuitionPaymentMapper::toPendingInvoiceResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PendingInvoiceResponse getInvoiceDetail(Long invoiceId) {
        Invoice invoice =
                invoiceRepository
                        .findByIdWithDetails(invoiceId)
                        .orElseThrow(
                                () ->
                                        new TuitionPaymentException(
                                                "Không tìm thấy hóa đơn với ID: " + invoiceId));
        return tuitionPaymentMapper.toPendingInvoiceResponse(invoice);
    }

    @Override
    @Transactional
    public PendingInvoiceResponse calculateDiscount(Long invoiceId, ApplyDiscountRequest request) {
        Invoice invoice = tuitionLedgerService.lock(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.UNPAID
                || tuitionLedgerService.paid(invoice).signum() > 0
                || ledgerRepository.hasIntents(
                        invoiceId, List.of("PENDING", "CREATING", "UNCERTAIN"))) {
            throw new TuitionPaymentException(
                    "Không đổi ưu đãi sau khi thu tiền hoặc khi có QR chưa được xử lý.");
        }
        if (ledgerRepository.isPlanConfirmed(invoiceId))
            throw new TuitionPaymentException(
                    "Ưu đãi và lịch đóng đã được chốt. Không được thay đổi ưu đãi của hóa đơn"
                        + " này.");
        if (ledgerRepository.hasInstallmentPlan(invoiceId)) {
            throw new TuitionPaymentException("Chốt ưu đãi trước khi cấu hình nhiều kỳ.");
        }
        if (request.getDiscountType() == DiscountType.FULL_FREE)
            throw new TuitionPaymentException(
                    "Không được miễn 100% học phí. Chính sách hiện tại chỉ cho giảm tối đa 30%.");
        var q =
                discountService.resolve(
                        invoice,
                        request.getDiscountType() == DiscountType.NONE
                                ? "NONE"
                                : request.getDiscountMethod() == null
                                        ? "INVALID"
                                        : request.getDiscountMethod(),
                        request.getDiscountType() == DiscountType.NONE
                                ? BigDecimal.ZERO
                                : request.getDiscountValue(),
                        request.getDiscountReason());
        invoice.setDiscountType(
                q.amount().signum() == 0 ? DiscountType.NONE : DiscountType.PARTIAL_DISCOUNT);
        invoice.setDiscountAmount(q.amount());
        invoice.setDiscountReason(q.reason());
        invoice.setFinalAmount(invoice.getOriginalAmount().subtract(q.amount()));
        invoiceRepository.save(invoice);
        tuitionLedgerService.refreshUnpaidFullPlan(invoice);
        log.info(
                "Áp dụng chiết khấu cho hóa đơn {}: loại={}, giảm={}, còn lại={}",
                invoice.getInvoiceCode(),
                invoice.getDiscountType(),
                invoice.getDiscountAmount(),
                invoice.getFinalAmount());

        return tuitionPaymentMapper.toPendingInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public VietQrResponse generateVietQr(Long invoiceId, BigDecimal customAmount) {
        throw new TuitionPaymentException(
                "Vui lòng tạo QR có mã yêu cầu trong trang Công nợ & thu theo kỳ.");
    }

    @Override
    @Transactional
    public PaymentReceiptResponse processPayment(
            ProcessPaymentRequest request, String cashierUsername) {
        if (request.getPaymentMethod() != PaymentMethod.CASH_AT_DESK) {
            throw new TuitionPaymentException("Giao dịch QR cần được xác nhận từ cổng thanh toán.");
        }
        String key = request.getIdempotencyKey();
        if (key == null || key.isBlank())
            throw new TuitionPaymentException(
                    "Thiếu mã chống gửi lặp. Vui lòng tải lại trang thu học phí.");
        Payment payment =
                cashCollectionService.cash(
                        request.getInvoiceId(),
                        request.getFinalAmount(),
                        request.getCashGiven() == null
                                ? request.getFinalAmount()
                                : request.getCashGiven(),
                        key);
        return tuitionPaymentMapper.toPaymentReceiptResponse(payment, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoicePaymentStatusResponse checkPaymentStatus(Long invoiceId) {
        Invoice invoice =
                invoiceRepository
                        .findByIdWithDetails(invoiceId)
                        .orElseThrow(
                                () ->
                                        new TuitionPaymentException(
                                                "Không tìm thấy hóa đơn với ID: " + invoiceId));

        boolean isPaid = invoice.getStatus() == InvoiceStatus.PAID;
        PaymentReceiptResponse receipt = null;

        if (isPaid) {
            Payment payment =
                    paymentRepository.findFirstByInvoiceIdOrderByIdDesc(invoiceId).orElse(null);
            if (payment != null) {
                receipt = tuitionPaymentMapper.toPaymentReceiptResponse(payment, null, null);
            }
        }

        return InvoicePaymentStatusResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceCode(invoice.getInvoiceCode())
                .status(invoice.getStatus().name())
                .isPaid(isPaid)
                .finalAmount(invoice.getFinalAmount())
                .receipt(receipt)
                .build();
    }

    @Override
    public PaymentReceiptResponse simulateBankWebhook(Long invoiceId, BigDecimal customAmount) {
        var intent =
                paymentIntentService.create(
                        invoiceId,
                        customAmount == null
                                ? tuitionQueryService.view(invoiceId).installments().stream()
                                        .filter(d -> d.remaining().signum() > 0)
                                        .findFirst()
                                        .orElseThrow(
                                                () ->
                                                        new TuitionPaymentException(
                                                                "Hóa đơn không còn kỳ thanh toán"
                                                                    + " nào chưa đóng."))
                                        .remaining()
                                : customAmount);
        return paymentIntentService.simulate(intent.id());
    }

    @Override
    @Transactional
    public PendingInvoiceResponse createDemoReservation(
            CreateReservationRequest request, String registeredByUsername) {
        Student student =
                studentRepository
                        .findById(request.getStudentId())
                        .orElseThrow(
                                () ->
                                        new TuitionPaymentException(
                                                "Không tìm thấy học sinh với ID: "
                                                        + request.getStudentId()));

        ClassEntity classEntity =
                classRepository
                        .findById(request.getClassId())
                        .orElseThrow(
                                () ->
                                        new TuitionPaymentException(
                                                "Không tìm thấy lớp học với ID: "
                                                        + request.getClassId()));

        User registeredByUser = null;
        if (registeredByUsername != null && !registeredByUsername.isBlank()) {
            registeredByUser = userRepository.findByUsername(registeredByUsername).orElse(null);
        }
        if (registeredByUser == null) {
            registeredByUser = userRepository.findByUsername("cashier_mai").orElse(null);
        }

        Course course = classEntity.getCourse();
        BigDecimal tuitionFee =
                course != null ? course.getTuitionFee() : BigDecimal.valueOf(3600000);

        // Tạo Enrollment trạng thái PENDING_PAYMENT (hóa đơn chờ đóng học phí)
        Enrollment enrollment =
                Enrollment.builder()
                        .student(student)
                        .classEntity(classEntity)
                        .registeredByUser(registeredByUser)
                        .enrollmentDate(LocalDateTime.now())
                        .status(EnrollmentStatus.PENDING_PAYMENT)
                        .notes(
                                request.getNotes() != null
                                        ? request.getNotes()
                                        : "Đăng ký khóa học - Chờ nộp học phí")
                        .build();
        enrollmentRepository.save(enrollment);

        // Tạo Invoice trạng thái UNPAID (không giới hạn 24h)
        String invoiceCode =
                "INV-"
                        + LocalDate.now().getYear()
                        + "-"
                        + String.format("%04d", (int) (Math.random() * 9000 + 1000));
        Invoice invoice =
                Invoice.builder()
                        .invoiceCode(invoiceCode)
                        .student(student)
                        .enrollment(enrollment)
                        .originalAmount(tuitionFee)
                        .discountType(DiscountType.NONE)
                        .discountAmount(BigDecimal.ZERO)
                        .finalAmount(tuitionFee)
                        .status(InvoiceStatus.UNPAID)
                        .dueDate(LocalDate.now().plusMonths(1))
                        .notes("Hóa đơn học phí lớp " + classEntity.getClassName())
                        .createdAt(LocalDateTime.now())
                        .build();
        invoiceRepository.save(invoice);

        log.info(
                "Tạo mới hóa đơn chờ thanh toán thành công: {}, học sinh: {}, lớp: {}",
                invoiceCode,
                student.getFullName(),
                classEntity.getClassName());

        return tuitionPaymentMapper.toPendingInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentReceiptResponse> getPaymentHistory(String keyword) {
        List<Payment> payments = paymentRepository.findAllByOrderByPaymentDateDesc();
        List<PaymentReceiptResponse> responses =
                payments.stream()
                        .filter(
                                p ->
                                        p.getStatus() == PaymentStatus.SUCCESS
                                                && !reversals.existsByPaymentIdAndStatus(
                                                        p.getId(), "APPROVED"))
                        .map(p -> tuitionPaymentMapper.toPaymentReceiptResponse(p, null, null))
                        .toList();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase();
            String cleanLower = removeAccents(lower);
            return responses.stream()
                    .filter(
                            r -> {
                                String pCode =
                                        r.getPaymentCode() != null
                                                ? r.getPaymentCode().toLowerCase()
                                                : "";
                                String invCode =
                                        r.getInvoiceCode() != null
                                                ? r.getInvoiceCode().toLowerCase()
                                                : "";
                                String studentName =
                                        r.getStudentName() != null
                                                ? r.getStudentName().toLowerCase()
                                                : "";
                                String parentPhone =
                                        r.getParentPhone() != null
                                                ? r.getParentPhone().toLowerCase()
                                                : "";
                                String parentName =
                                        r.getParentName() != null
                                                ? r.getParentName().toLowerCase()
                                                : "";
                                String className =
                                        r.getClassName() != null
                                                ? r.getClassName().toLowerCase()
                                                : "";

                                boolean exactMatch =
                                        pCode.contains(lower)
                                                || invCode.contains(lower)
                                                || studentName.contains(lower)
                                                || parentPhone.contains(lower)
                                                || parentName.contains(lower)
                                                || className.contains(lower);
                                if (exactMatch) return true;

                                return removeAccents(studentName).contains(cleanLower)
                                        || removeAccents(parentName).contains(cleanLower)
                                        || removeAccents(className).contains(cleanLower);
                            })
                    .toList();
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentHistoryReportResponse getPaymentHistoryReport(
            String timeRange,
            LocalDate startDate,
            LocalDate endDate,
            String cashierUsername,
            String keyword) {
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;
        LocalDate today = LocalDate.now();

        if ("TODAY".equalsIgnoreCase(timeRange)) {
            startDateTime = today.atStartOfDay();
            endDateTime = today.atTime(23, 59, 59);
        } else if ("THIS_WEEK".equalsIgnoreCase(timeRange) || "WEEK".equalsIgnoreCase(timeRange)) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            startDateTime = monday.atStartOfDay();
            endDateTime = sunday.atTime(23, 59, 59);
        } else if ("THIS_MONTH".equalsIgnoreCase(timeRange)
                || "MONTH".equalsIgnoreCase(timeRange)) {
            LocalDate firstDay = today.with(TemporalAdjusters.firstDayOfMonth());
            LocalDate lastDay = today.with(TemporalAdjusters.lastDayOfMonth());
            startDateTime = firstDay.atStartOfDay();
            endDateTime = lastDay.atTime(23, 59, 59);
        } else if ("CUSTOM".equalsIgnoreCase(timeRange)) {
            if (startDate != null) {
                startDateTime = startDate.atStartOfDay();
            }
            if (endDate != null) {
                endDateTime = endDate.atTime(23, 59, 59);
            }
        }

        String filterCashier =
                (cashierUsername != null
                                && !cashierUsername.isBlank()
                                && !"ALL".equalsIgnoreCase(cashierUsername))
                        ? cashierUsername.trim()
                        : null;

        List<Payment> payments =
                paymentRepository.findPaymentsWithFilters(
                        startDateTime, endDateTime, filterCashier);

        List<PaymentReceiptResponse> receiptResponses =
                payments.stream()
                        .filter(
                                p ->
                                        p.getStatus() == PaymentStatus.SUCCESS
                                                && !reversals.existsByPaymentIdAndStatus(
                                                        p.getId(), "APPROVED"))
                        .map(p -> tuitionPaymentMapper.toPaymentReceiptResponse(p, null, null))
                        .toList();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase();
            String cleanLower = removeAccents(lower);
            receiptResponses =
                    receiptResponses.stream()
                            .filter(
                                    r -> {
                                        String pCode =
                                                r.getPaymentCode() != null
                                                        ? r.getPaymentCode().toLowerCase()
                                                        : "";
                                        String invCode =
                                                r.getInvoiceCode() != null
                                                        ? r.getInvoiceCode().toLowerCase()
                                                        : "";
                                        String studentName =
                                                r.getStudentName() != null
                                                        ? r.getStudentName().toLowerCase()
                                                        : "";
                                        String parentPhone =
                                                r.getParentPhone() != null
                                                        ? r.getParentPhone().toLowerCase()
                                                        : "";
                                        String parentName =
                                                r.getParentName() != null
                                                        ? r.getParentName().toLowerCase()
                                                        : "";
                                        String className =
                                                r.getClassName() != null
                                                        ? r.getClassName().toLowerCase()
                                                        : "";
                                        String cashier =
                                                r.getCashierName() != null
                                                        ? r.getCashierName().toLowerCase()
                                                        : "";

                                        boolean exactMatch =
                                                pCode.contains(lower)
                                                        || invCode.contains(lower)
                                                        || studentName.contains(lower)
                                                        || parentPhone.contains(lower)
                                                        || parentName.contains(lower)
                                                        || className.contains(lower)
                                                        || cashier.contains(lower);
                                        if (exactMatch) return true;

                                        return removeAccents(studentName).contains(cleanLower)
                                                || removeAccents(parentName).contains(cleanLower)
                                                || removeAccents(className).contains(cleanLower)
                                                || removeAccents(cashier).contains(cleanLower);
                                    })
                            .toList();
        }

        BigDecimal totalCash = BigDecimal.ZERO;
        long cashCount = 0;
        BigDecimal totalBank = BigDecimal.ZERO;
        long bankCount = 0;

        for (PaymentReceiptResponse item : receiptResponses) {
            BigDecimal amt =
                    item.getNetReceivedAmount() != null
                            ? item.getNetReceivedAmount()
                            : BigDecimal.ZERO;
            String method = item.getPaymentMethod();
            if ("CASH_AT_DESK".equalsIgnoreCase(method)) {
                totalCash = totalCash.add(amt);
                cashCount++;
            } else {
                totalBank = totalBank.add(amt);
                bankCount++;
            }
        }

        BigDecimal totalRevenue = totalCash.add(totalBank);

        PaymentSummaryResponse summary =
                PaymentSummaryResponse.builder()
                        .totalCashAmount(totalCash)
                        .totalCashCount(cashCount)
                        .totalBankAmount(totalBank)
                        .totalBankCount(bankCount)
                        .totalRevenue(totalRevenue)
                        .totalTransactions(receiptResponses.size())
                        .build();

        return PaymentHistoryReportResponse.builder()
                .summary(summary)
                .payments(receiptResponses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashierOptionResponse> getCashierList() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .filter(
                        u ->
                                u.getRole() == Role.ADMIN
                                        || u.getRole() == Role.STAFF
                                        || u.getRole() == Role.BRANCH_MANAGER
                                        || "cashier_mai".equals(u.getUsername()))
                .map(
                        u ->
                                CashierOptionResponse.builder()
                                        .username(u.getUsername())
                                        .fullName(u.getFullName())
                                        .role(u.getRole() != null ? u.getRole().name() : "STAFF")
                                        .build())
                .sorted(Comparator.comparing(CashierOptionResponse::getFullName))
                .toList();
    }

    @Override
    public PayOSPaymentLinkResponse createPayOSPaymentLink(
            Long invoiceId, BigDecimal customAmount) {
        var intent =
                paymentIntentService.create(
                        invoiceId,
                        customAmount == null
                                ? tuitionQueryService.view(invoiceId).installments().stream()
                                        .filter(d -> d.remaining().signum() > 0)
                                        .findFirst()
                                        .orElseThrow(
                                                () ->
                                                        new TuitionPaymentException(
                                                                "Hóa đơn không còn kỳ thanh toán"
                                                                    + " nào chưa đóng."))
                                        .remaining()
                                : customAmount);
        return PayOSPaymentLinkResponse.builder()
                .orderCode(intent.orderCode())
                .amount(intent.amount())
                .checkoutUrl(intent.checkoutUrl())
                .qrCode(intent.qrCode())
                .paymentLinkId(String.valueOf(intent.id()))
                .status(intent.status())
                .simulated(intent.simulated())
                .build();
    }

    @Override
    public PayOSPaymentLinkResponse createPayOSPaymentLink(
            Long invoiceId,
            BigDecimal customAmount,
            String discountTypeStr,
            BigDecimal discountAmount,
            String discountReason) {
        if (discountTypeStr != null || discountAmount != null || discountReason != null)
            throw new TuitionPaymentException(
                    "Không được thay đổi ưu đãi khi tạo QR. Vui lòng chốt ưu đãi và lịch đóng trước"
                        + " khi thanh toán.");
        return createPayOSPaymentLink(invoiceId, customAmount);
    }

    @Override
    public PaymentReceiptResponse processPayOSWebhook(Map<String, Object> payload) {
        return paymentResultService.webhook(payload);
    }

    @Override
    @Transactional(readOnly = true)
    public CashierDashboardStatsResponse getDashboardStats() {
        long pendingCount =
                invoiceRepository.countByStatus(InvoiceStatus.UNPAID)
                        + invoiceRepository.countByStatus(InvoiceStatus.PARTIALLY_PAID);
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long paidTodayCount = paymentRepository.countPaymentsSince(startOfToday);
        BigDecimal revenueToday = paymentRepository.sumAmountSince(startOfToday);
        long totalEnrolled = enrollmentRepository.findByStatus(EnrollmentStatus.ENROLLED).size();

        return CashierDashboardStatsResponse.builder()
                .pendingInvoicesCount(pendingCount)
                .paidTodayCount(paidTodayCount)
                .revenueToday(revenueToday != null ? revenueToday : BigDecimal.ZERO)
                .totalStudentsEnrolled(totalEnrolled)
                .build();
    }

    @Override
    public void fixVietnameseFontData() {
        // Dữ liệu đã được nạp chuẩn Unicode N'...' từ file init_database.sql
    }

    public static String removeAccents(String s) {
        if (s == null) return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replace("Đ", "D").replace("đ", "d");
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 đ";
        return String.format("%,.0f đ", amount);
    }
}
