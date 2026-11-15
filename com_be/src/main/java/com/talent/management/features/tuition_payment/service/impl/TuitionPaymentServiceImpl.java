package com.talent.management.features.tuition_payment.service.impl;

import com.talent.management.features.auth.repository.StudentRepository;
import com.talent.management.features.auth.repository.UserRepository;
import com.talent.management.features.tuition_payment.dto.request.ApplyDiscountRequest;
import com.talent.management.features.tuition_payment.dto.request.CreateReservationRequest;
import com.talent.management.features.tuition_payment.dto.request.ProcessPaymentRequest;
import com.talent.management.features.tuition_payment.dto.response.*;
import com.talent.management.features.tuition_payment.exception.TuitionPaymentException;
import com.talent.management.features.tuition_payment.gateway.PayOSGateway;
import com.talent.management.features.tuition_payment.mapper.TuitionPaymentMapper;
import com.talent.management.features.tuition_payment.repository.ClassRepository;
import com.talent.management.features.tuition_payment.repository.EnrollmentRepository;
import com.talent.management.features.tuition_payment.repository.InvoiceRepository;
import com.talent.management.features.tuition_payment.repository.PaymentRepository;
import com.talent.management.features.tuition_payment.service.TuitionPaymentService;
import com.talent.management.shared.entity.*;
import com.talent.management.shared.enums.DiscountType;
import com.talent.management.shared.enums.EnrollmentStatus;
import com.talent.management.shared.enums.InvoiceStatus;
import com.talent.management.shared.enums.PaymentMethod;
import com.talent.management.shared.enums.PaymentStatus;
import com.talent.management.shared.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TuitionPaymentServiceImpl implements TuitionPaymentService {

    private final com.talent.management.features.tuition_payment.service.TuitionPlanService ledger;
    private final com.talent.management.features.tuition_payment.service.PaymentPostingService posting;
    private final com.talent.management.features.tuition_payment.service.InstallmentGatewayService installmentGateway;
    private final com.talent.management.features.tuition_payment.repository.TuitionReversalRepository reversals;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TuitionPaymentMapper tuitionPaymentMapper;
    private final PayOSGateway payOSGateway;
    private final JdbcTemplate jdbcTemplate;
    private final com.talent.management.features.course_enrollment.repository.EnrollmentRequestRepository enrollmentRequestRepository;

    private static final String ACADEMY_BANK_ID = "970405";
    private static final String ACADEMY_BANK_NAME = "Ngân hàng Nông nghiệp & PTNT (Agribank)";
    private static final String ACADEMY_ACCOUNT_NO = "3511205288130";
    private static final String ACADEMY_ACCOUNT_NAME = "HOANG DUC THUAN";

    @Override
    @Transactional(readOnly = true)
    public List<PendingInvoiceResponse> searchPendingInvoices(String keyword) {
        String trimmedKeyword = keyword != null ? keyword.trim() : "";
        log.info("Tra cứu phiếu giữ chỗ / hóa đơn chờ thanh toán với keyword: '{}'", trimmedKeyword);
        List<Invoice> invoices = new ArrayList<>(invoiceRepository.searchInvoicesByStatusAndKeyword(InvoiceStatus.UNPAID, trimmedKeyword));
        invoices.addAll(invoiceRepository.searchInvoicesByStatusAndKeyword(InvoiceStatus.PARTIALLY_PAID,trimmedKeyword));
        return invoices.stream()
                .map(tuitionPaymentMapper::toPendingInvoiceResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PendingInvoiceResponse getInvoiceDetail(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdWithDetails(invoiceId)
                .orElseThrow(() -> new TuitionPaymentException("Không tìm thấy hóa đơn với ID: " + invoiceId));
        return tuitionPaymentMapper.toPendingInvoiceResponse(invoice);
    }

    @Override
    @Transactional
    public PendingInvoiceResponse calculateDiscount(Long invoiceId, ApplyDiscountRequest request) {
        Invoice invoice=ledger.lock(invoiceId);
        if (invoice.getStatus()!=InvoiceStatus.UNPAID || ledger.paid(invoice).signum()>0
                || jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tuition_payment_intents WHERE invoice_id=? AND status IN ('PENDING','CREATING','UNCERTAIN')",Integer.class,invoiceId)>0) {
            throw new TuitionPaymentException("Không đổi ưu đãi sau khi thu tiền hoặc khi có QR chưa được xử lý.");
        }
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tuition_plans WHERE invoice_id=? AND mode='INSTALLMENTS'",Integer.class,invoiceId)>0) {
            throw new TuitionPaymentException("Chốt ưu đãi trước khi cấu hình nhiều kỳ.");
        }
        BigDecimal originalAmount = invoice.getOriginalAmount();
        BigDecimal discountAmount = BigDecimal.ZERO;
        DiscountType discountType = request.getDiscountType() != null ? request.getDiscountType() : DiscountType.NONE;
        String discountReason = request.getDiscountReason();

        if (discountType == DiscountType.FULL_FREE) {
            discountAmount = originalAmount;
            if (discountReason == null || discountReason.isBlank()) {
                discountReason = "Miễn phí 100% (Học bổng / Ưu đãi đặc biệt)";
            }
        } else if (discountType == DiscountType.PARTIAL_DISCOUNT) {
            String method = request.getDiscountMethod() != null ? request.getDiscountMethod().toUpperCase() : "PERCENTAGE";
            BigDecimal value = request.getDiscountValue() != null ? request.getDiscountValue() : BigDecimal.ZERO;
            ledger.validateDiscountValue(originalAmount, method, value);

            if ("PERCENTAGE".equals(method)) {
                discountAmount = originalAmount.multiply(value).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                if (discountReason == null || discountReason.isBlank()) {
                    discountReason = "Giảm " + value + "% học phí";
                }
            } else {
                discountAmount = value;
                if (discountReason == null || discountReason.isBlank()) {
                    discountReason = "Giảm tiền mặt " + formatCurrency(discountAmount);
                }
            }
            discountAmount = discountAmount.min(originalAmount).max(BigDecimal.ZERO);
        } else {
            discountType = DiscountType.NONE;
            discountAmount = BigDecimal.ZERO;
            discountReason = null;
        }

        BigDecimal finalAmount = originalAmount.subtract(discountAmount).max(BigDecimal.ZERO);

        invoice.setDiscountType(discountType);
        invoice.setDiscountAmount(discountAmount);
        invoice.setDiscountReason(discountReason);
        invoice.setFinalAmount(finalAmount);

        invoiceRepository.save(invoice);
        ledger.refreshUnpaidFullPlan(invoice);
        log.info("Áp dụng chiết khấu cho hóa đơn {}: loại={}, giảm={}, còn lại={}", 
                invoice.getInvoiceCode(), discountType, discountAmount, finalAmount);

        return tuitionPaymentMapper.toPendingInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public VietQrResponse generateVietQr(Long invoiceId, BigDecimal customAmount) {
        throw new TuitionPaymentException("Vui lòng tạo QR có mã yêu cầu trong trang Công nợ & thu theo kỳ.");
    }

    @Override
    @Transactional
    public PaymentReceiptResponse processPayment(ProcessPaymentRequest request, String cashierUsername) {
        if (request.getPaymentMethod()!=PaymentMethod.CASH_AT_DESK) {
            throw new TuitionPaymentException("Giao dịch QR cần được xác nhận từ cổng thanh toán.");
        }
        String key=request.getIdempotencyKey();
        if (key==null || key.isBlank()) throw new TuitionPaymentException("Thiếu mã chống gửi lặp. Vui lòng tải lại trang thu học phí.");
        Payment payment=posting.cash(request.getInvoiceId(),request.getFinalAmount(),
                request.getCashGiven()==null ? request.getFinalAmount() : request.getCashGiven(),key);
        return tuitionPaymentMapper.toPaymentReceiptResponse(payment,null,null);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoicePaymentStatusResponse checkPaymentStatus(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdWithDetails(invoiceId)
                .orElseThrow(() -> new TuitionPaymentException("Không tìm thấy hóa đơn với ID: " + invoiceId));

        boolean isPaid = invoice.getStatus() == InvoiceStatus.PAID;
        PaymentReceiptResponse receipt = null;

        if (isPaid) {
            Payment payment = paymentRepository.findFirstByInvoiceIdOrderByIdDesc(invoiceId).orElse(null);
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
        var intent=installmentGateway.create(invoiceId,customAmount==null ? ledger.view(invoiceId).balance() : customAmount);
        return installmentGateway.simulate(intent.id());
    }

    @Override
    @Transactional
    public PendingInvoiceResponse createDemoReservation(CreateReservationRequest request, String registeredByUsername) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new TuitionPaymentException("Không tìm thấy học sinh với ID: " + request.getStudentId()));

        ClassEntity classEntity = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new TuitionPaymentException("Không tìm thấy lớp học với ID: " + request.getClassId()));

        User registeredByUser = null;
        if (registeredByUsername != null && !registeredByUsername.isBlank()) {
            registeredByUser = userRepository.findByUsername(registeredByUsername).orElse(null);
        }
        if (registeredByUser == null) {
            registeredByUser = userRepository.findByUsername("cashier_mai").orElse(null);
        }

        Course course = classEntity.getCourse();
        BigDecimal tuitionFee = course != null ? course.getTuitionFee() : BigDecimal.valueOf(3600000);

        // Tạo Enrollment trạng thái PENDING_PAYMENT (hóa đơn chờ đóng học phí)
        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .classEntity(classEntity)
                .registeredByUser(registeredByUser)
                .enrollmentDate(LocalDateTime.now())
                .status(EnrollmentStatus.PENDING_PAYMENT)
                .notes(request.getNotes() != null ? request.getNotes() : "Đăng ký khóa học - Chờ nộp học phí")
                .build();
        enrollmentRepository.save(enrollment);

        // Tạo Invoice trạng thái UNPAID (không giới hạn 24h)
        String invoiceCode = "INV-" + LocalDate.now().getYear() + "-" + String.format("%04d", (int) (Math.random() * 9000 + 1000));
        Invoice invoice = Invoice.builder()
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

        log.info("Tạo mới hóa đơn chờ thanh toán thành công: {}, học sinh: {}, lớp: {}", 
                invoiceCode, student.getFullName(), classEntity.getClassName());

        return tuitionPaymentMapper.toPendingInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentReceiptResponse> getPaymentHistory(String keyword) {
        List<Payment> payments = paymentRepository.findAllByOrderByPaymentDateDesc();
        List<PaymentReceiptResponse> responses = payments.stream()
                .filter(p -> p.getStatus()==PaymentStatus.SUCCESS && !reversals.existsByPaymentIdAndStatus(p.getId(),"APPROVED"))
                .map(p -> tuitionPaymentMapper.toPaymentReceiptResponse(p, null, null))
                .toList();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase();
            String cleanLower = removeAccents(lower);
            return responses.stream().filter(r -> {
                String pCode = r.getPaymentCode() != null ? r.getPaymentCode().toLowerCase() : "";
                String invCode = r.getInvoiceCode() != null ? r.getInvoiceCode().toLowerCase() : "";
                String studentName = r.getStudentName() != null ? r.getStudentName().toLowerCase() : "";
                String parentPhone = r.getParentPhone() != null ? r.getParentPhone().toLowerCase() : "";
                String parentName = r.getParentName() != null ? r.getParentName().toLowerCase() : "";
                String className = r.getClassName() != null ? r.getClassName().toLowerCase() : "";

                boolean exactMatch = pCode.contains(lower) || invCode.contains(lower) || studentName.contains(lower)
                        || parentPhone.contains(lower) || parentName.contains(lower) || className.contains(lower);
                if (exactMatch) return true;

                return removeAccents(studentName).contains(cleanLower)
                        || removeAccents(parentName).contains(cleanLower)
                        || removeAccents(className).contains(cleanLower);
            }).toList();
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
            String keyword
    ) {
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
        } else if ("THIS_MONTH".equalsIgnoreCase(timeRange) || "MONTH".equalsIgnoreCase(timeRange)) {
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

        String filterCashier = (cashierUsername != null && !cashierUsername.isBlank() && !"ALL".equalsIgnoreCase(cashierUsername))
                ? cashierUsername.trim() : null;

        List<Payment> payments = paymentRepository.findPaymentsWithFilters(startDateTime, endDateTime, filterCashier);

        List<PaymentReceiptResponse> receiptResponses = payments.stream()
                .filter(p -> p.getStatus()==PaymentStatus.SUCCESS && !reversals.existsByPaymentIdAndStatus(p.getId(),"APPROVED"))
                .map(p -> tuitionPaymentMapper.toPaymentReceiptResponse(p, null, null))
                .toList();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase();
            String cleanLower = removeAccents(lower);
            receiptResponses = receiptResponses.stream().filter(r -> {
                String pCode = r.getPaymentCode() != null ? r.getPaymentCode().toLowerCase() : "";
                String invCode = r.getInvoiceCode() != null ? r.getInvoiceCode().toLowerCase() : "";
                String studentName = r.getStudentName() != null ? r.getStudentName().toLowerCase() : "";
                String parentPhone = r.getParentPhone() != null ? r.getParentPhone().toLowerCase() : "";
                String parentName = r.getParentName() != null ? r.getParentName().toLowerCase() : "";
                String className = r.getClassName() != null ? r.getClassName().toLowerCase() : "";
                String cashier = r.getCashierName() != null ? r.getCashierName().toLowerCase() : "";

                boolean exactMatch = pCode.contains(lower) || invCode.contains(lower) || studentName.contains(lower)
                        || parentPhone.contains(lower) || parentName.contains(lower) || className.contains(lower)
                        || cashier.contains(lower);
                if (exactMatch) return true;

                return removeAccents(studentName).contains(cleanLower)
                        || removeAccents(parentName).contains(cleanLower)
                        || removeAccents(className).contains(cleanLower)
                        || removeAccents(cashier).contains(cleanLower);
            }).toList();
        }

        BigDecimal totalCash = BigDecimal.ZERO;
        long cashCount = 0;
        BigDecimal totalBank = BigDecimal.ZERO;
        long bankCount = 0;

        for (PaymentReceiptResponse item : receiptResponses) {
            BigDecimal amt = item.getNetReceivedAmount() != null ? item.getNetReceivedAmount() : BigDecimal.ZERO;
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

        PaymentSummaryResponse summary = PaymentSummaryResponse.builder()
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
                .filter(u -> u.getRole() == Role.ADMIN || u.getRole() == Role.STAFF || u.getRole() == Role.BRANCH_MANAGER || "cashier_mai".equals(u.getUsername()))
                .map(u -> CashierOptionResponse.builder()
                        .username(u.getUsername())
                        .fullName(u.getFullName())
                        .role(u.getRole() != null ? u.getRole().name() : "STAFF")
                        .build())
                .sorted(Comparator.comparing(CashierOptionResponse::getFullName))
                .toList();
    }

    @Override
    public PayOSPaymentLinkResponse createPayOSPaymentLink(Long invoiceId, BigDecimal customAmount) {
        var intent=installmentGateway.create(invoiceId,customAmount==null ? ledger.view(invoiceId).balance() : customAmount);
        return PayOSPaymentLinkResponse.builder().orderCode(intent.orderCode()).amount(intent.amount())
                .checkoutUrl(intent.checkoutUrl()).qrCode(intent.qrCode()).paymentLinkId(String.valueOf(intent.id()))
                .status(intent.status()).simulated(intent.simulated()).build();
    }

    @Override
    public PayOSPaymentLinkResponse createPayOSPaymentLink(
            Long invoiceId,
            BigDecimal customAmount,
            String discountTypeStr,
            BigDecimal discountAmount,
            String discountReason
    ) {
        Invoice invoice = invoiceRepository.findByIdWithDetails(invoiceId)
                .orElseThrow(() -> new TuitionPaymentException("Không tìm thấy hóa đơn với ID: " + invoiceId));
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new TuitionPaymentException("Hóa đơn " + invoice.getInvoiceCode() + " đã được thanh toán hoàn tất!");
        }

        BigDecimal originalAmount = invoice.getOriginalAmount() != null ? invoice.getOriginalAmount() : customAmount;

        // Lưu trước các thông tin giảm học phí & lý do vào hóa đơn
        if (customAmount != null) {
            invoice.setFinalAmount(customAmount);
        }
        if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setDiscountAmount(discountAmount);
        } else if (customAmount != null && originalAmount != null && customAmount.compareTo(originalAmount) < 0) {
            invoice.setDiscountAmount(originalAmount.subtract(customAmount));
        }

        if (discountTypeStr != null && !discountTypeStr.isBlank()) {
            try {
                invoice.setDiscountType(DiscountType.valueOf(discountTypeStr));
            } catch (Exception e) {
                invoice.setDiscountType(DiscountType.PARTIAL_DISCOUNT);
            }
        } else if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            invoice.setDiscountType(DiscountType.PARTIAL_DISCOUNT);
        }

        if (discountReason != null && !discountReason.isBlank()) {
            invoice.setDiscountReason(discountReason.trim());
        } else if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                && (invoice.getDiscountReason() == null || invoice.getDiscountReason().isBlank())) {
            invoice.setDiscountReason("Ưu đãi giảm học phí");
        }

        invoiceRepository.save(invoice);
        return payOSGateway.createPaymentLink(invoice, customAmount);
    }

    @Override
    public PaymentReceiptResponse processPayOSWebhook(Map<String, Object> payload) {
        return installmentGateway.webhook(payload);
    }

    @Override
    @Transactional(readOnly = true)
    public CashierDashboardStatsResponse getDashboardStats() {
        long pendingCount = invoiceRepository.countByStatus(InvoiceStatus.UNPAID)+invoiceRepository.countByStatus(InvoiceStatus.PARTIALLY_PAID);
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
        return normalized.replaceAll("\\p{M}", "")
                .replace("Đ", "D")
                .replace("đ", "d");
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 đ";
        return String.format("%,.0f đ", amount);
    }
}
