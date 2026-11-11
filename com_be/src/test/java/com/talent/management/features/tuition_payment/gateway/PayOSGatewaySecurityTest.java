package com.talent.management.features.tuition_payment.gateway;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

class PayOSGatewaySecurityTest {
    @Test
    void webhookRequiresChecksumAndAuthenticSignature() {
        PayOSGateway gateway = new PayOSGateway();
        Map<String, Object> data =
                Map.of(
                        "orderCode",
                        123,
                        "reference",
                        "TF230204212323",
                        "amount",
                        1000,
                        "code",
                        "00");
        assertThat(gateway.verifyWebhookSignature(Map.of("data", data, "signature", "SIMULATED")))
                .isFalse();
        ReflectionTestUtils.setField(gateway, "checksumKey", "unit-test-key");
        assertThat(gateway.verifyWebhookSignature(Map.of("data", data, "signature", "TEST")))
                .isFalse();
        String signature = gateway.sign(data, "unit-test-key");
        assertThat(gateway.verifyWebhookSignature(Map.of("data", data, "signature", signature)))
                .isTrue();
        assertThat(
                        gateway.verifyWebhookSignature(
                                Map.of(
                                        "data",
                                        Map.of("orderCode", 123, "amount", 999),
                                        "signature",
                                        signature)))
                .isFalse();
    }
}
