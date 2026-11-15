package com.talent.management.features.tuition_payment.gateway;

import com.talent.management.features.tuition_payment.dto.response.PayOSPaymentLinkResponse;
import com.talent.management.shared.entity.Invoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PayOSGateway {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long ORDER_CODE_SUFFIX_BASE = 100_000L;

    private final RestClient restClient;

    @Value("${payos.client-id:}")
    private String clientId;

    @Value("${payos.api-key:}")
    private String apiKey;

    @Value("${payos.checksum-key:}")
    private String checksumKey;

    @Value("${payos.create-payment-url:https://api-merchant.payos.vn/v2/payment-requests}")
    private String createPaymentUrl;

    @Value("${payos.return-url:http://127.0.0.1:5500/pages/tuition-payment.html?payment=success}")
    private String returnUrl;

    @Value("${payos.cancel-url:http://127.0.0.1:5500/pages/tuition-payment.html?payment=cancelled}")
    private String cancelUrl;

    public PayOSGateway() {
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(java.time.Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Value("${tuition.simulation.enabled:false}")
    private boolean simulation;

    @Value("${tuition.qr-lifetime-seconds:300}")
    private long qrLifetimeSeconds = 300;

    @Value("${tuition.public-base-url:http://127.0.0.1}")
    private String publicBaseUrl;

    public PayOSPaymentLinkResponse createPaymentLink(Invoice invoice, BigDecimal amount) {
        return createPaymentLink(invoice, amount, buildOrderCode(Instant.now().getEpochSecond(), invoice.getId()), false);
    }

    public PayOSPaymentLinkResponse createPaymentLink(Invoice invoice, BigDecimal paymentAmount, long orderCode, boolean parent) {
        long amount = paymentAmount.longValueExact();
        String route = parent ? "/pages/parent-tuition.html" : "/pages/tuition-receivables.html";
        String returnTarget = publicBaseUrl + route + "?invoiceId=" + invoice.getId();
        String cancelTarget = returnTarget;
        String description = buildPayOSDescription(invoice);

        if (simulation) return PayOSPaymentLinkResponse.builder()
                .orderCode(orderCode).amount(paymentAmount).status("PENDING")
                .transferContent(description).simulated(true).build();
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank() || checksumKey == null || checksumKey.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình PayOS. Liên hệ trung tâm hoặc thanh toán tại quầy.");
        }

        Map<String, Object> signaturePayload = new LinkedHashMap<>();
        signaturePayload.put("amount", amount);
        signaturePayload.put("cancelUrl", cancelTarget);
        signaturePayload.put("description", description);
        signaturePayload.put("orderCode", orderCode);
        signaturePayload.put("returnUrl", returnTarget);

        Map<String, Object> requestBody = new LinkedHashMap<>(signaturePayload);
        requestBody.put("expiredAt", Instant.now().plusSeconds(qrLifetimeSeconds).getEpochSecond());
        requestBody.put("signature", sign(signaturePayload, checksumKey.trim()));
        
        String itemName = "Hoc phi " + invoice.getInvoiceCode();
        if (invoice.getEnrollment() != null && invoice.getEnrollment().getClassEntity() != null) {
            itemName = invoice.getEnrollment().getClassEntity().getClassName();
        }
        requestBody.put("items", List.of(
                Map.of(
                        "name", itemName.length() > 50 ? itemName.substring(0, 50) : itemName,
                        "quantity", 1,
                        "price", amount
                )
        ));

        try {
            log.info("Gọi API PayOS tạo payment-request cho hóa đơn {}: orderCode={}, amount={}", 
                    invoice.getInvoiceCode(), orderCode, amount);
            
            Map<String, Object> response = restClient.post()
                    .uri(createPaymentUrl)
                    .header("x-client-id", clientId.trim())
                    .header("x-api-key", apiKey.trim())
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response != null && "00".equals(String.valueOf(response.get("code")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null) {
                    log.info("PayOS tạo liên kết thanh toán thành công cho hóa đơn {}: checkoutUrl={}", 
                            invoice.getInvoiceCode(), data.get("checkoutUrl"));
                    return PayOSPaymentLinkResponse.builder()
                            .orderCode(orderCode)
                            .checkoutUrl(String.valueOf(data.get("checkoutUrl")))
                            .qrCode(String.valueOf(data.get("qrCode")))
                            .paymentLinkId(String.valueOf(data.get("paymentLinkId")))
                            .amount(paymentAmount)
                            .accountName(String.valueOf(data.get("accountName")))
                            .accountNumber(String.valueOf(data.get("accountNumber")))
                            .bin(String.valueOf(data.get("bin")))
                            .transferContent(description)
                            .status(String.valueOf(data.get("status")))
                            .simulated(false)
                            .build();
                }
            }
            throw new IllegalStateException("Cổng thanh toán chưa tạo được QR. Cần kiểm tra mã đơn trước khi thử lại.");
        } catch (RestClientException ex) {
            throw new IllegalStateException("Chưa xác định kết quả tạo QR. Trung tâm cần kiểm tra với PayOS.", ex);
        }
    }

    public void cancelPayment(long orderCode) {
        if (isMissingOrClosed(orderCode)) return;
        Map<String,Object> response = restClient.post().uri(createPaymentUrl + "/" + orderCode + "/cancel")
                .header("x-client-id", clientId.trim()).header("x-api-key", apiKey.trim())
                .body(Map.of("cancellationReason", "Doi phuong thuc thanh toan"))
                .retrieve().body(new ParameterizedTypeReference<>() {});
        if (response == null || !"00".equals(String.valueOf(response.get("code")))
                || !(response.get("data") instanceof Map<?,?> data)
                || !"CANCELLED".equals(String.valueOf(data.get("status")))
                || new BigDecimal(String.valueOf(data.get("amountPaid"))).signum() != 0) {
            throw new IllegalStateException("Chưa hủy được QR ở cổng thanh toán. Cần kiểm tra ngân hàng trước khi thu lại.");
        }
    }

    /** Only definitive gateway absence/closure permits releasing an uncertain local request. */
    public boolean isMissingOrClosed(long orderCode) {
        GatewayPayment payment = paymentInformation(orderCode);
        return payment.paid().signum() == 0 && List.of("NOT_FOUND", "CANCELLED", "EXPIRED").contains(payment.status());
    }

    public record BankTransfer(BigDecimal amount, String reference) {}
    public record GatewayPayment(String status, BigDecimal paid, List<BankTransfer> transfers) {}

    public GatewayPayment paymentInformation(long orderCode) {
        Map<String, Object> response = restClient.get().uri(createPaymentUrl + "/" + orderCode)
                .header("User-Agent", "TalentCenter/1.0")
                .header("x-client-id", clientId.trim()).header("x-api-key", apiKey.trim())
                .retrieve().body(new ParameterizedTypeReference<>() {});
        if (response != null && "101".equals(String.valueOf(response.get("code"))))
            return new GatewayPayment("NOT_FOUND", BigDecimal.ZERO, List.of());
        if (response == null || !"00".equals(String.valueOf(response.get("code")))
                || !(response.get("data") instanceof Map<?, ?> data))
            throw new IllegalStateException("Chưa kiểm tra được yêu cầu QR với PayOS. Vui lòng thử lại sau.");
        if (!String.valueOf(orderCode).equals(String.valueOf(data.get("orderCode"))))
            throw new IllegalStateException("Mã đơn PayOS trả về không khớp.");
        BigDecimal paid = new BigDecimal(String.valueOf(data.get("amountPaid")));
        List<BankTransfer> transfers = new ArrayList<>();
        if (data.get("transactions") instanceof List<?> rows) {
            for (Object row : rows) if (row instanceof Map<?, ?> item) {
                String reference = String.valueOf(item.get("reference"));
                if (!reference.isBlank() && !"null".equals(reference) && reference.length() <= 120)
                    transfers.add(new BankTransfer(new BigDecimal(String.valueOf(item.get("amount"))), reference));
            }
        }
        return new GatewayPayment(String.valueOf(data.get("status")), paid, transfers);
    }

    public boolean verifyWebhookSignature(Map<String, Object> body) {
        if (body == null || !body.containsKey("data")) {
            return false;
        }

        Object dataObj = body.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            return false;
        }

        if (checksumKey == null || checksumKey.isBlank() || !body.containsKey("signature")) return false;
        String receivedSignature = String.valueOf(body.get("signature"));
        Map<String, Object> sortedMap = new TreeMap<>();
        dataMap.forEach((k, v) -> {
            String valStr = (v == null || "null".equals(v) || "undefined".equals(v)) ? "" : String.valueOf(v);
            sortedMap.put(String.valueOf(k), valStr);
        });

        String calculatedSignature = sign(sortedMap, checksumKey.trim());
        boolean matches = Objects.equals(receivedSignature, calculatedSignature);
        if (!matches) {
            log.warn("Xác thực chữ ký PayOS thất bại.");
        }
        return matches;
    }

    public String sign(Map<String, Object> data, String key) {
        try {
            String rawData = new TreeMap<>(data).entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                    .collect(Collectors.joining("&"));
            Mac hmac = Mac.getInstance(HMAC_SHA256);
            hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = hmac.doFinal(rawData.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo chữ ký HMAC-SHA256 cho PayOS.", ex);
        }
    }

    private static long buildOrderCode(long epochSecond, long invoiceId) {
        return Math.addExact(
                Math.multiplyExact(epochSecond, ORDER_CODE_SUFFIX_BASE),
                Math.floorMod(invoiceId, ORDER_CODE_SUFFIX_BASE)
        );
    }

    private String buildPayOSDescription(Invoice invoice) {
        String cleanCode = invoice.getInvoiceCode().replaceAll("[^a-zA-Z0-9]", "");
        String studentName = "";
        if (invoice.getStudent() != null && invoice.getStudent().getFullName() != null) {
            String fullName = invoice.getStudent().getFullName();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([^)]+)\\)").matcher(fullName);
            if (m.find()) {
                studentName = m.group(1);
            } else {
                String[] parts = fullName.trim().split("\\s+");
                if (parts.length >= 2) {
                    studentName = parts[parts.length - 2] + " " + parts[parts.length - 1];
                } else {
                    studentName = parts[0];
                }
            }
        }

        String unaccented = removeAccents(studentName).replaceAll("[^a-zA-Z0-9 ]", "").trim();
        String desc = "HP " + cleanCode;
        if (!unaccented.isBlank()) {
            desc = desc + " " + unaccented;
        }

        if (desc.length() > 25) {
            desc = desc.substring(0, 25).trim();
        }
        return desc;
    }

    private String removeAccents(String input) {
        if (input == null) return "";
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replace("đ", "d").replace("Đ", "D");
    }
}
