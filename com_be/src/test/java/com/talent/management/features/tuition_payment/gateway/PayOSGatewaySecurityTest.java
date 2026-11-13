package com.talent.management.features.tuition_payment.gateway;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

class PayOSGatewaySecurityTest {
    @Test
    void absentRequestIsRecoverableButUnknownGatewayErrorsAreNot() {
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        PayOSGateway gateway = new PayOSGateway();
        ReflectionTestUtils.setField(gateway, "restClient", builder.build());
        ReflectionTestUtils.setField(gateway, "clientId", "test");
        ReflectionTestUtils.setField(gateway, "apiKey", "test");
        ReflectionTestUtils.setField(gateway, "createPaymentUrl", "https://gateway.test/payment-requests");
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://gateway.test/payment-requests/123"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"code\":\"101\",\"desc\":\"Not found\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        assertThat(gateway.isMissingOrClosed(123)).isTrue();
        server.verify();
        server.reset();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://gateway.test/payment-requests/123"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"code\":\"401\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.isMissingOrClosed(123)).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

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
