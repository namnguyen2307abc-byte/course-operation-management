package com.talent.management.features.tuition_payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.talent.management.features.tuition_payment.dto.request.ApplyDiscountRequest;
import com.talent.management.features.tuition_payment.gateway.PayOSGateway;
import com.talent.management.features.tuition_payment.repository.TuitionLedgerRepository;
import com.talent.management.features.tuition_payment.service.impl.TuitionPaymentServiceImpl;
import com.talent.management.shared.entity.Invoice;
import com.talent.management.shared.enums.DiscountType;
import com.talent.management.shared.exception.ApplicationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class LegacyDiscountCompatibilityTest {
    @Mock TuitionLedgerService tuitionLedgerService;
    @Mock TuitionLedgerRepository ledgerRepository;
    @Mock TuitionDiscountService discountService;
    @Mock PayOSGateway payOSGateway;
    @InjectMocks TuitionPaymentServiceImpl service;

    @Test
    void existingSqlDecimalDiscountIsNormalizedWithoutAcceptingFractionalUserInput() {
        var invoice =
                Invoice.builder()
                        .id(1L)
                        .originalAmount(new BigDecimal("6000000.00"))
                        .discountAmount(new BigDecimal("600000.00"))
                        .discountReason("Ưu đãi đã nhập")
                        .build();
        var discounts =
                new com.talent.management.features.tuition_payment.service.impl
                        .TuitionDiscountServiceImpl(ledgerRepository);
        assertThat(discounts.resolve(invoice, null, null, null).amount())
                .isEqualByComparingTo("600000");
        assertThatThrownBy(
                        () ->
                                discounts.resolve(
                                        invoice,
                                        "FIXED_AMOUNT",
                                        new BigDecimal("600000.00"),
                                        "Nhập mới"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("thập phân");
    }

    @Test
    void oldQrEndpointCannotChangeDiscountOrCallGatewayDirectly() {
        assertThatThrownBy(
                        () ->
                                service.createPayOSPaymentLink(
                                        1L,
                                        new BigDecimal("10"),
                                        "FULL_FREE",
                                        new BigDecimal("90"),
                                        "Override"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("Không được thay đổi ưu đãi khi tạo QR");
        verifyNoInteractions(payOSGateway, discountService, tuitionLedgerService);
    }

    @Test
    void oldDiscountEndpointAlsoRejectsFullWaiver() {
        var invoice =
                Invoice.builder()
                        .originalAmount(new BigDecimal("10000"))
                        .finalAmount(new BigDecimal("10000"))
                        .build();
        when(tuitionLedgerService.lock(1L)).thenReturn(invoice);
        when(tuitionLedgerService.paid(invoice)).thenReturn(BigDecimal.ZERO);
        assertThatThrownBy(
                        () ->
                                service.calculateDiscount(
                                        1L,
                                        ApplyDiscountRequest.builder()
                                                .discountType(DiscountType.FULL_FREE)
                                                .build()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("tối đa 30%");
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("10000");
        verifyNoInteractions(payOSGateway, discountService);
    }
}
