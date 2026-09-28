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
import jakarta.annotation.PostConstruct;
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

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TuitionPaymentMapper tuitionPaymentMapper;
    private final PayOSGateway payOSGateway;
    private final JdbcTemplate jdbcTemplate;

    private static final String ACADEMY_BANK_ID = "970405";
    private static final String ACADEMY_BANK_NAME = "Ngân hàng Nông nghiệp & PTNT (Agribank)";
    private static final String ACADEMY_ACCOUNT_NO = "3511205288130";
    private static final String ACADEMY_ACCOUNT_NAME = "HOANG DUC THUAN";

    @Override
    @Transactional(readOnly = true)
    public List<PendingInvoiceResponse> searchPendingInvoices(String keyword) {
        String trimmedKeyword = keyword != null ? keyword.trim() : "";
        log.info("Tra cứu phiếu giữ chỗ / hóa đơn chờ thanh toán với keyword: '{}'", trimmedKeyword);
        List<Invoice> invoices = invoiceRepository.searchInvoicesByStatusAndKeyword(InvoiceStatus.UNPAID, trimmedKeyword);
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
        Invoice invoice = invoiceRepository.findByIdWithDetails(invoiceId)
                .orElseThrow(() -> new TuitionPaymentException("Không tìm thấy hóa đơn với ID: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new TuitionPaymentException("Hóa đơn này đã được thanh toán hoàn tất, không thể thay đổi miễn giảm phí!");
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
        log.info("Áp dụng chiết khấu cho hóa đơn {}: loại={}, giảm={}, còn lại={}", 
                invoice.getInvoiceCode(), discountType, discountAmount, finalAmount);

        return tuitionPaymentMapper.toPendingInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public VietQrResponse generateVietQr(Long invoiceId, BigDecimal customAmount) {
        Invoice invoice = invoiceRepository.findByIdWithDetails(invoiceId)
                .orElseThrow(() -> new TuitionPaymentException("Không tìm thấy hóa đơn với ID: " + invoiceId));

        BigDecimal amount = customAmount != null ? customAmount : invoice.getFinalAmount();
        String invoiceCode = invoice.getInvoiceCode();

        // Chuẩn hóa nội dung chuyển khoản: INV-2026-002 Nguyen Mai Chi dang ky Piano Mam Non
        String studentNameNoAccent = removeAccents(invoice.getStudent() != null ? invoice.getStudent().getFullName() : "");
        String classNameNoAccent = "";
        if (invoice.getEnrollment() != null && invoice.getEnrollment().getClassEntity() != null) {
            classNameNoAccent = removeAccents(invoice.getEnrollment().getClassEntity().getClassName());
        }

        String transferContent = String.format("%s %s dang ky lop %s", invoiceCode, studentNameNoAccent, classNameNoAccent)
                .replaceAll("[^a-zA-Z0-9 ]", "")
                .replaceAll(" +", " ")
                .trim();
        if (transferContent.length() > 50) {
            transferContent = transferContent.substring(0, 50).trim();
        }

        String description = "Hoc phi " + invoiceCode;

        // Sinh link ảnh VietQR chuẩn dịch vụ VietQR
        // Cú pháp: https://img.vietqr.io/image/<BANK_ID>-<ACCOUNT_NO>-compact2.png?amount=<AMOUNT>&addInfo=<INFO>&accountName=<NAME>
        String encodedAccountName = URLEncoder.encode(ACADEMY_ACCOUNT_NAME, StandardCharsets.UTF_8);
        String encodedInfo = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String qrImageUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                ACADEMY_BANK_ID,
                ACADEMY_ACCOUNT_NO,
                amount.setScale(0, RoundingMode.HALF_UP).toString(),
                encodedInfo,
                encodedAccountName
        );

        return VietQrResponse.builder()
                .qrImageUrl(qrImageUrl)
                .bankId(ACADEMY_BANK_ID)
                .bankName(ACADEMY_BANK_NAME)
                .accountNo(ACADEMY_ACCOUNT_NO)
                .accountName(ACADEMY_ACCOUNT_NAME)
                .amount(amount)
                .transferContent(transferContent)
                .paymentDescription(description)
                .build();
    }

    @Override
    @Transactional
    public PaymentReceiptResponse processPayment(ProcessPaymentRequest request, String cashierUsername) {
        Invoice invoice = invoiceRepository.findByIdWithDetails(request.getInvoiceId())
                .orElseThrow(() -> new TuitionPaymentException("Không tìm thấy hóa đơn với ID: " + request.getInvoiceId()));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new TuitionPaymentException("Hóa đơn này (" + invoice.getInvoiceCode() + ") đã được thanh toán trước đó!");
        }

        User cashier = null;
        if (cashierUsername != null && !cashierUsername.isBlank()) {
            cashier = userRepository.findByUsername(cashierUsername).orElse(null);
        }
        if (cashier == null) {
            cashier = userRepository.findByUsername("cashier_mai").orElse(null);
        }

        // 1. Cập nhật thông tin số tiền & chiết khấu vào Invoice
        if (request.getDiscountType() != null) {
            invoice.setDiscountType(request.getDiscountType());
        }
        if (request.getDiscountAmount() != null) {
            invoice.setDiscountAmount(request.getDiscountAmount());
        }
        if (request.getDiscountReason() != null) {
            invoice.setDiscountReason(request.getDiscountReason());
        }
        if (request.getFinalAmount() != null) {
            invoice.setFinalAmount(request.getFinalAmount());
        }
        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        // 2. Tính toán tiền trả lại nếu thanh toán bằng tiền mặt (chỉ áp dụng cho CASH_AT_DESK)
        BigDecimal cashGiven = null;
        BigDecimal changeAmount = null;
        BigDecimal finalAmount = invoice.getFinalAmount();
        if (request.getPaymentMethod() == PaymentMethod.CASH_AT_DESK) {
            cashGiven = request.getCashGiven() != null ? request.getCashGiven() : finalAmount;
            if (cashGiven.compareTo(finalAmount) < 0) {
                throw new TuitionPaymentException("Số tiền khách đưa không đủ để thanh toán học phí!");
            }
            changeAmount = cashGiven.subtract(finalAmount);
        } else {
            cashGiven = finalAmount;
            changeAmount = BigDecimal.ZERO;
        }

        // 3. Tạo bản ghi Payment
        String paymentCode = "PAY-" + System.currentTimeMillis();
        Payment payment = Payment.builder()
                .invoice(invoice)
                .paymentCode(paymentCode)
                .paymentMethod(request.getPaymentMethod())
                .amount(finalAmount)
                .cashGiven(cashGiven)
                .changeAmount(changeAmount)
                .paymentDate(LocalDateTime.now())
                .cashier(cashier)
                .bankTransactionId(request.getBankTransactionId())
                .note(request.getNote())
                .status(PaymentStatus.SUCCESS)
                .build();
        paymentRepository.save(payment);

        // 4. Hoàn tất ghi danh: Cập nhật Enrollment thành ENROLLED & tăng sĩ số lớp
        Enrollment enrollment = invoice.getEnrollment();
        if (enrollment != null) {
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setNotes("Đã nộp học phí qua mã phiếu: " + paymentCode);
            enrollmentRepository.save(enrollment);

            ClassEntity classEntity = enrollment.getClassEntity();
            if (classEntity != null) {
                int current = classEntity.getCurrentStudents() != null ? classEntity.getCurrentStudents() : 0;
                classEntity.setCurrentStudents(current + 1);
                classRepository.save(classEntity);
                log.info("Lớp học {} đã tăng sĩ số lên: {}/{}", classEntity.getClassName(), classEntity.getCurrentStudents(), classEntity.getMaxStudents());
            }
        }

        log.info("Xử lý thanh toán thành công cho hóa đơn: {}, mã giao dịch: {}, thu ngân: {}", 
                invoice.getInvoiceCode(), paymentCode, cashier != null ? cashier.getUsername() : "SYSTEM");

        return tuitionPaymentMapper.toPaymentReceiptResponse(payment, cashGiven, changeAmount);
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
    @Transactional
    public PaymentReceiptResponse simulateBankWebhook(Long invoiceId, BigDecimal customAmount) {
        Invoice invoice = invoiceRepository.findByIdWithDetails(invoiceId)
                .orElseThrow(() -> new TuitionPaymentException("Không tìm thấy hóa đơn với ID: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            Payment payment = paymentRepository.findFirstByInvoiceIdOrderByIdDesc(invoiceId).orElse(null);
            if (payment != null) {
                return tuitionPaymentMapper.toPaymentReceiptResponse(payment, null, null);
            }
        }

        BigDecimal amount = customAmount != null ? customAmount : invoice.getFinalAmount();
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .invoiceId(invoiceId)
                .paymentMethod(PaymentMethod.VIET_QR)
                .discountType(invoice.getDiscountType() != null ? invoice.getDiscountType() : DiscountType.NONE)
                .discountAmount(invoice.getDiscountAmount() != null ? invoice.getDiscountAmount() : BigDecimal.ZERO)
                .discountReason(invoice.getDiscountReason())
                .finalAmount(amount)
                .bankTransactionId("AGRI-AUTO-" + System.currentTimeMillis())
                .note("Thanh toán tự động qua Webhook Ngân Hàng Agribank VietQR (Ting-Ting)")
                .build();

        return processPayment(request, "SYSTEM_WEBHOOK");
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
            BigDecimal amt = item.getFinalAmount() != null ? item.getFinalAmount() : BigDecimal.ZERO;
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
    @Transactional
    public PayOSPaymentLinkResponse createPayOSPaymentLink(Long invoiceId, BigDecimal customAmount) {
        return createPayOSPaymentLink(invoiceId, customAmount, null, null, null);
    }

    @Override
    @Transactional
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
    @Transactional
    public PaymentReceiptResponse processPayOSWebhook(Map<String, Object> payload) {
        log.info("Nhận Webhook từ PayOS: {}", payload);
        if (!payOSGateway.verifyWebhookSignature(payload)) {
            log.error("PayOS Webhook: Chữ ký không hợp lệ!");
            throw new TuitionPaymentException("Chữ ký Webhook PayOS không hợp lệ!");
        }

        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            throw new TuitionPaymentException("Dữ liệu webhook PayOS không đúng định dạng!");
        }

        // 1. Phản hồi thành công ngay lập tức nếu là ping test xác thực Webhook (/confirm-webhook) từ PayOS
        Object testRef = dataMap.get("reference");
        Object testOrder = dataMap.get("orderCode");
        Object testDesc = dataMap.get("description");
        if ("TF230204212323".equals(String.valueOf(testRef))
                || "123".equals(String.valueOf(testOrder))
                || (testDesc != null && String.valueOf(testDesc).contains("VQRIO123"))) {
            log.info("Xác thực Webhook thành công qua Ping kiểm tra của PayOS: ref={}, orderCode={}", testRef, testOrder);
            return PaymentReceiptResponse.builder()
                    .paymentId(0L)
                    .invoiceCode("TEST-PAYOS")
                    .paymentCode("CONFIRMED-WEBHOOK")
                    .bankTransactionId(testRef != null ? String.valueOf(testRef) : "CONFIRMED")
                    .studentName("PayOS Ping Test")
                    .finalAmount(BigDecimal.valueOf(3000))
                    .paymentMethod("BANK_TRANSFER")
                    .message("Xác nhận kết nối Webhook PayOS thành công 100%!")
                    .build();
        }

        String description = String.valueOf(dataMap.get("description"));
        Object amountObj = dataMap.get("amount");
        BigDecimal amount = null;
        if (amountObj != null) {
            try {
                amount = new BigDecimal(String.valueOf(amountObj));
            } catch (Exception ignored) {}
        }
        String reference = String.valueOf(dataMap.get("reference"));
        if (reference == null || reference.isBlank() || "null".equals(reference)) {
            reference = "PAYOS-" + dataMap.get("orderCode");
        }

        Invoice invoice = null;
        if (description != null && !description.isBlank() && !"null".equalsIgnoreCase(description)) {
            invoice = invoiceRepository.findByInvoiceCode(description.trim()).orElse(null);
            if (invoice == null) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(INV-\\d{4}-\\d+)");
                java.util.regex.Matcher m = p.matcher(description);
                if (m.find()) {
                    invoice = invoiceRepository.findByInvoiceCode(m.group(1)).orElse(null);
                }
            }
            if (invoice == null) {
                // Hỗ trợ trường hợp PayOS bỏ ký tự đặc biệt: INV2026011 -> INV-2026-011
                java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("INV(\\d{4})(\\d+)");
                java.util.regex.Matcher m2 = p2.matcher(description);
                if (m2.find()) {
                    String formattedCode = "INV-" + m2.group(1) + "-" + m2.group(2);
                    invoice = invoiceRepository.findByInvoiceCode(formattedCode).orElse(null);
                }
            }
        }

        if (invoice == null) {
            Object orderCodeObj = dataMap.get("orderCode");
            if (orderCodeObj != null) {
                try {
                    long orderCode = Long.parseLong(String.valueOf(orderCodeObj));
                    long invoiceId = orderCode % 100_000L;
                    invoice = invoiceRepository.findByIdWithDetails(invoiceId).orElse(null);
                } catch (Exception ignored) {}
            }
        }

        if (invoice == null) {
            log.error("Không tìm thấy hóa đơn phù hợp cho webhook PayOS: {}", dataMap);
            throw new TuitionPaymentException("Không tìm thấy hóa đơn tương ứng với giao dịch PayOS!");
        }

        // Đảm bảo fetch đầy đủ quan hệ để xử lý và sinh receipt
        Invoice detailedInvoice = invoiceRepository.findByIdWithDetails(invoice.getId()).orElse(invoice);

        if (detailedInvoice.getStatus() == InvoiceStatus.PAID) {
            log.info("Hóa đơn {} đã được thanh toán trước đó.", detailedInvoice.getInvoiceCode());
            Payment payment = paymentRepository.findFirstByInvoiceIdOrderByIdDesc(detailedInvoice.getId()).orElse(null);
            if (payment != null) {
                return tuitionPaymentMapper.toPaymentReceiptResponse(payment, null, null);
            }
        }

        BigDecimal finalAmount = amount != null ? amount : detailedInvoice.getFinalAmount();
        BigDecimal discountAmount = detailedInvoice.getDiscountAmount();
        DiscountType discountType = detailedInvoice.getDiscountType();
        String discountReason = detailedInvoice.getDiscountReason();

        if (finalAmount.compareTo(BigDecimal.ZERO) == 0) {
            discountType = DiscountType.FULL_FREE;
            discountAmount = detailedInvoice.getOriginalAmount();
            if (discountReason == null || discountReason.isBlank()) {
                discountReason = "Học bổng tài năng âm nhạc 100%";
            }
        } else if (detailedInvoice.getOriginalAmount() != null && finalAmount.compareTo(detailedInvoice.getOriginalAmount()) < 0) {
            if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) == 0) {
                discountAmount = detailedInvoice.getOriginalAmount().subtract(finalAmount);
                if (discountType == null || discountType == DiscountType.NONE) {
                    discountType = DiscountType.PARTIAL_DISCOUNT;
                }
                if (discountReason == null || discountReason.isBlank()) {
                    discountReason = "Ưu đãi giảm trực tiếp khi thanh toán";
                }
            }
        }

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .invoiceId(detailedInvoice.getId())
                .paymentMethod(PaymentMethod.VIET_QR)
                .discountType(discountType != null ? discountType : DiscountType.NONE)
                .discountAmount(discountAmount != null ? discountAmount : BigDecimal.ZERO)
                .discountReason(discountReason)
                .finalAmount(finalAmount)
                .bankTransactionId(reference)
                .note("Thanh toán trực tuyến PayOS Webhook tự động (orderCode: " + dataMap.get("orderCode") + ")")
                .build();

        return processPayment(request, "PAYOS_GATEWAY");
    }

    @Override
    @Transactional(readOnly = true)
    public CashierDashboardStatsResponse getDashboardStats() {
        long pendingCount = invoiceRepository.countByStatus(InvoiceStatus.UNPAID);
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
    @PostConstruct
    @Transactional
    public void fixVietnameseFontData() {
        try {
            log.info("Bắt đầu chuẩn hóa dữ liệu tiếng Việt (UTF-16) cho CSDL...");
            jdbcTemplate.update("UPDATE users SET full_name = ? WHERE username = ?", "Quản Trị Viên Hệ Thống", "admin");
            jdbcTemplate.update("UPDATE users SET full_name = ? WHERE username = ?", "Cô Vũ Thu Hương (GV Piano)", "teacher_huong");
            jdbcTemplate.update("UPDATE users SET full_name = ? WHERE username = ?", "Thầy Trần Anh Tuấn (GV Guitar)", "teacher_tuan");
            jdbcTemplate.update("UPDATE users SET full_name = ? WHERE username = ?", "Nguyễn Thanh Mai (Thu Ngân)", "cashier_mai");
            jdbcTemplate.update("UPDATE users SET full_name = ? WHERE username = ?", "Phụ Huynh Lê Thị Lan", "parent_lan");
            jdbcTemplate.update("UPDATE users SET full_name = ? WHERE username = ?", "Phụ Huynh Trần Văn Hưng", "parent_hung");
            jdbcTemplate.update("UPDATE users SET full_name = ? WHERE username = ?", "Phụ Huynh Hoàng Đức Thuận", "user_thuan_test");
            jdbcTemplate.update("UPDATE users SET full_name = ? WHERE username = ?", "Phụ Huynh Đỗ Phương Thảo", "parent_thao");
            jdbcTemplate.update("UPDATE users SET full_name = ? WHERE username = ?", "Phụ Huynh Vũ Minh Tuấn", "parent_tuan");

            jdbcTemplate.update("UPDATE branches SET name = ?, address = ? WHERE id = 1", "Cơ Sở 1 - Cầu Giấy", "Số 12 Khúc Thừa Dụ, Dịch Vọng, Cầu Giấy, Hà Nội");
            jdbcTemplate.update("UPDATE branches SET name = ?, address = ? WHERE id = 2", "Cơ Sở 2 - Đống Đa", "Số 85 Hào Nam, Ô Chợ Dừa, Đống Đa, Hà Nội");

            jdbcTemplate.update("UPDATE rooms SET room_name = ? WHERE id = 1", "Phòng Piano Biểu Diễn 1");
            jdbcTemplate.update("UPDATE rooms SET room_name = ? WHERE id = 2", "Phòng Piano Nhóm 1");
            jdbcTemplate.update("UPDATE rooms SET room_name = ? WHERE id = 3", "Phòng Guitar & Cảm Âm");
            jdbcTemplate.update("UPDATE rooms SET room_name = ? WHERE id = 4", "Phòng Piano Thực Hành 2");

            jdbcTemplate.update("UPDATE courses SET name = ?, description = ? WHERE id = 1", "Piano Mầm Non (Cảm thụ âm nhạc)", "Dành cho bé từ 4-6 tuổi làm quen phím đàn");
            jdbcTemplate.update("UPDATE courses SET name = ?, description = ? WHERE id = 2", "Piano Sơ Cấp (Grade 1)", "Học tư thế ngón, nhịp phách, thị tấu và ghép 2 tay");

            jdbcTemplate.update("UPDATE classes SET class_name = ?, schedule_description = ? WHERE id = 1", "Piano 1-1 Bé Bảo Nam", "Thứ 2 & Thứ 5 (18:00 - 19:00)");
            jdbcTemplate.update("UPDATE classes SET class_name = ?, schedule_description = ? WHERE id = 2", "Piano Mầm Non Nhóm Sáng Thứ 7", "Thứ 7 (09:00 - 10:30)");

            jdbcTemplate.update("UPDATE students SET full_name = ?, school_name = ?, notes = ? WHERE id = 1", "Nguyễn Bảo Nam (Bé Bin)", "Tiểu học Thực Nghiệm", "Thích học đàn Piano cổ điển");
            jdbcTemplate.update("UPDATE students SET full_name = ?, school_name = ?, notes = ? WHERE id = 2", "Nguyễn Mai Chi (Bé Bông)", "Mầm non Vinschool", "Học làm quen phím đàn mầm non");
            jdbcTemplate.update("UPDATE students SET full_name = ?, school_name = ?, notes = ? WHERE id = 3", "Trần Hoàng Long (Bé Tí)", "Tiểu học Dịch Vọng B", "Đăng ký học Piano cổ điển");
            jdbcTemplate.update("UPDATE students SET full_name = ?, school_name = ?, notes = ? WHERE id = 4", "Hoàng Đức Thuận (Test 10k)", "Học Viện Âm Nhạc Talent", "Học viên test chuyển khoản VietQR 10k");
            jdbcTemplate.update("UPDATE students SET full_name = ?, school_name = ?, notes = ? WHERE id = 5", "Đỗ Minh Khang (Bé Ken)", "Tiểu học Đoàn Thị Điểm", "Đăng ký khóa Piano mầm non");
            jdbcTemplate.update("UPDATE students SET full_name = ?, school_name = ?, notes = ? WHERE id = 6", "Vũ Thùy Linh (Bé Su)", "Tiểu học Archimedes", "Đăng ký khóa Piano sơ cấp Grade 1");
            jdbcTemplate.update("UPDATE students SET full_name = ?, school_name = ?, notes = ? WHERE id = 7", "Phạm Gia Huy (Bé Tom)", "Mầm non Ban Mai", "Đăng ký học Piano nhóm");

            jdbcTemplate.update("UPDATE invoices SET notes = ? WHERE id = 1", "Hóa đơn học phí khóa Piano Grade 1");
            jdbcTemplate.update("UPDATE invoices SET notes = ? WHERE id = 2", "Phiếu giữ chỗ 24h - Piano Mầm Non");
            jdbcTemplate.update("UPDATE invoices SET notes = ? WHERE id = 3", "Phiếu giữ chỗ 24h - Piano 1-1");
            jdbcTemplate.update("UPDATE invoices SET notes = ? WHERE id = 4", "Phiếu học phí thử nghiệm chuyển khoản 10k");
            jdbcTemplate.update("UPDATE invoices SET notes = ? WHERE id = 5", "Phiếu giữ chỗ 24h - Lớp Piano Mầm Non");
            jdbcTemplate.update("UPDATE invoices SET notes = ? WHERE id = 6", "Phiếu giữ chỗ 24h - Lớp Piano 1-1");
            jdbcTemplate.update("UPDATE invoices SET notes = ? WHERE id = 7", "Phiếu giữ chỗ 24h - Lớp Piano Mầm Non");
            jdbcTemplate.update("UPDATE invoices SET notes = ? WHERE id = 8", "Phiếu giữ chỗ 24h - Lớp Piano Mầm Non");
            jdbcTemplate.update("UPDATE invoices SET notes = ? WHERE id = 9", "Phiếu giữ chỗ 24h - Lớp Piano 1-1");

            jdbcTemplate.update("UPDATE enrollments SET notes = ? WHERE id = 1", "Đã hoàn tất học phí, xếp lớp thành công");
            jdbcTemplate.update("UPDATE enrollments SET notes = ? WHERE id = 2", "Phiếu giữ chỗ tạm thời 24h - Lớp Piano Mầm Non");
            jdbcTemplate.update("UPDATE enrollments SET notes = ? WHERE id = 3", "Phiếu giữ chỗ tạm thời 24h - Lớp Piano 1-1");
            jdbcTemplate.update("UPDATE enrollments SET notes = ? WHERE id = 5", "Phiếu giữ chỗ tạm thời 24h - Lớp Piano Mầm Non");
            jdbcTemplate.update("UPDATE enrollments SET notes = ? WHERE id = 6", "Phiếu giữ chỗ tạm thời 24h - Lớp Piano 1-1");
            jdbcTemplate.update("UPDATE enrollments SET notes = ? WHERE id = 7", "Phiếu giữ chỗ tạm thời 24h - Lớp Piano Mầm Non");

            jdbcTemplate.update("UPDATE payments SET note = ? WHERE id = 9", "Thu tiền mặt tại quầy đợt đầu");
            jdbcTemplate.update("UPDATE payments SET note = ? WHERE id = 10", "Quét VietQR Agribank chuyển khoản");
            log.info("Chuẩn hóa dữ liệu tiếng Việt (UTF-16) cho CSDL hoàn tất thành công 100%!");
        } catch (Exception e) {
            log.warn("Lỗi khi cập nhật font tiếng Việt: {}", e.getMessage());
        }
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
