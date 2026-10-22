package com.kisia.staybooking.payment;

import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import com.kisia.staybooking.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TossPaymentGateway {
    private final RestClient client;
    private final String clientKey;
    private final String secretKey;

    public TossPaymentGateway(@Value("${staybooking.toss.client-key:}") String clientKey,
                              @Value("${staybooking.toss.secret-key:}") String secretKey) {
        this.clientKey = clientKey;
        this.secretKey = secretKey;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl("https://api.tosspayments.com")
            .requestFactory(factory).build();
    }

    public boolean isConfigured() {
        // 실제 금전 거래와 환불 자동화가 준비되기 전에는 테스트 키만 허용한다.
        return clientKey.startsWith("test_gck_") && secretKey.startsWith("test_gsk_");
    }

    public String clientKey() {
        return isConfigured() ? clientKey : null;
    }

    public Confirmation confirm(String paymentKey, String orderId, long amount) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED",
                "토스페이먼츠 결제 키가 설정되지 않았습니다.");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            Confirmation result = client.post().uri("/v1/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .body(new ConfirmRequest(paymentKey, orderId, amount))
                .retrieve().body(Confirmation.class);
            if (result == null) throw new IllegalStateException("Empty payment confirmation");
            return result;
        } catch (RestClientException | IllegalStateException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CONFIRM_FAILED",
                "결제 승인 결과를 확인하지 못했습니다. 결제 내역을 확인해 주세요.");
        }
    }

    private record ConfirmRequest(String paymentKey, String orderId, long amount) { }

    public record Confirmation(String paymentKey, String orderId, long totalAmount, String status) { }

    public Cancellation cancel(String paymentKey, String reason, long amount, Long refundId) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED",
                "토스페이먼츠 테스트 키가 설정되지 않았습니다.");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            Cancellation result = client.post().uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .header("Idempotency-Key", "staybooking-refund-" + refundId)
                .body(new CancelRequest(reason, amount))
                .retrieve().body(Cancellation.class);
            if (result == null) throw new IllegalStateException("Empty payment cancellation");
            return result;
        } catch (RestClientException | IllegalStateException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CANCEL_FAILED",
                "결제 취소 결과를 확인하지 못했습니다. 토스 결제 내역을 확인해 주세요.");
        }
    }

    private record CancelRequest(String cancelReason, long cancelAmount) { }

    public record Cancellation(String paymentKey, String orderId, long totalAmount,
                               long balanceAmount, String status, List<Cancel> cancels) { }

    public record Cancel(long cancelAmount) { }
}
