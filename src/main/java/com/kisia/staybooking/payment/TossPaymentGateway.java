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
import org.springframework.web.client.RestClientResponseException;

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
        // 결제위젯 키(test_gck_/test_gsk_)와 API 개별 연동 키(test_ck_/test_sk_) 중 같은 종류 한 쌍만 받는다.
        // 결제 승인·조회·취소 API는 두 종류 모두 같다.
        return usesWidget() || usesPaymentWindow();
    }

    // 결제 화면 방식. 위젯 키는 주문서형 결제위젯, 개별 연동 키는 결제창으로 연다.
    public String mode() {
        if (usesWidget()) return "WIDGET";
        if (usesPaymentWindow()) return "PAYMENT_WINDOW";
        return null;
    }

    private boolean usesWidget() {
        return clientKey.startsWith("test_gck_") && secretKey.startsWith("test_gsk_");
    }

    private boolean usesPaymentWindow() {
        return clientKey.startsWith("test_ck_") && secretKey.startsWith("test_sk_");
    }

    public String clientKey() {
        return isConfigured() ? clientKey : null;
    }

    // 자동결제(빌링)는 API 개별 연동 키로만 쓸 수 있다.
    public boolean billingEnabled() {
        return usesPaymentWindow();
    }

    /** 카드 등록 창에서 받은 authKey로 빌링키를 발급한다. 빌링키는 이 서버만 안다. */
    public BillingIssue issueBillingKey(String authKey, String customerKey) {
        requireBilling();
        try {
            BillingIssue result = client.post().uri("/v1/billing/authorizations/issue")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth())
                .body(new BillingIssueRequest(authKey, customerKey))
                .retrieve().body(BillingIssue.class);
            if (result == null || result.billingKey() == null || result.billingKey().isBlank()) {
                throw new IllegalStateException("Empty billing key");
            }
            return result;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_REGISTER_FAILED",
                    "카드를 등록하지 못했습니다. 카드 정보를 확인하고 다시 시도해 주세요.");
            }
            throw billingUnavailable();
        } catch (RestClientException | IllegalStateException e) {
            throw billingUnavailable();
        }
    }

    /**
     * 빌링키로 결제를 승인한다. 같은 주문번호로 두 번 승인되지 않게 Idempotency-Key를 붙이고,
     * 응답이 유실되면 PAYMENT_LOOKUP_FAILED와 같은 502를 던져 호출한 쪽이 주문을 다시 조회하게 한다.
     */
    public Confirmation chargeBillingKey(String billingKey, String customerKey, String orderId,
                                         String orderName, long amount) {
        requireBilling();
        try {
            Confirmation result = client.post().uri("/v1/billing/{billingKey}", billingKey)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth())
                .header("Idempotency-Key", "staybooking-billing-" + orderId)
                .body(new BillingChargeRequest(customerKey, amount, orderId, orderName))
                .retrieve().body(Confirmation.class);
            if (result == null) throw new IllegalStateException("Empty billing payment");
            return result;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_PAYMENT_REJECTED",
                    "등록된 카드로 결제하지 못했습니다. 다른 결제 방법을 이용해 주세요.");
            }
            throw billingUnavailable();
        } catch (RestClientException | IllegalStateException e) {
            throw billingUnavailable();
        }
    }

    private void requireBilling() {
        if (!billingEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_NOT_CONFIGURED",
                "카드 등록 결제를 사용할 수 없습니다.");
        }
    }

    private String basicAuth() {
        return Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    private static ApiException billingUnavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_LOOKUP_FAILED",
            "결제 서비스 응답을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private record BillingIssueRequest(String authKey, String customerKey) { }

    private record BillingChargeRequest(String customerKey, long amount, String orderId, String orderName) { }

    // 토스 응답 중 쓰는 값만 받는다. cardNumber·card.number는 토스가 가린 번호다.
    public record BillingIssue(String billingKey, String customerKey, String cardCompany, String cardNumber,
                               Card card) {
        public BillingIssue(String billingKey, String customerKey, String cardCompany, String cardNumber) {
            this(billingKey, customerKey, cardCompany, cardNumber, null);
        }

        /** 화면에 보여 줄 카드사 이름. 응답에 이름이 없으면 카드사 코드로 찾는다. */
        public String companyName() {
            if (cardCompany != null && !cardCompany.isBlank()) return cardCompany;
            return card == null ? null : CardCompanies.name(card.issuerCode());
        }

        public String maskedNumber() {
            if (cardNumber != null && !cardNumber.isBlank()) return cardNumber;
            return card == null ? null : card.number();
        }

        public String cardType() {
            return card == null ? null : card.cardType();
        }
    }

    public record Card(String issuerCode, String number, String cardType) { }

    public Confirmation confirm(String paymentKey, String orderId, long amount) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED",
                "토스페이먼츠 결제 키가 설정되지 않았습니다.");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            Confirmation result = client.post().uri("/v1/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .header("Idempotency-Key", "staybooking-confirm-" + orderId)
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

    public record Confirmation(String paymentKey, String orderId, long totalAmount, String status,
                               Long balanceAmount) {
        public Confirmation(String paymentKey, String orderId, long totalAmount, String status) {
            this(paymentKey, orderId, totalAmount, status, null);
        }
    }

    /** 승인 응답을 받지 못했을 때 토스에 저장된 주문 상태를 확인한다. */
    public Confirmation findByOrderId(String orderId) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED",
                "토스페이먼츠 결제 키가 설정되지 않았습니다.");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            Confirmation result = client.get().uri("/v1/payments/orders/{orderId}", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .retrieve().body(Confirmation.class);
            if (result == null) throw new IllegalStateException("Empty payment lookup");
            return result;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_PAYMENT_NOT_FOUND",
                    "토스페이먼츠에 승인된 주문 정보가 없습니다.");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_LOOKUP_FAILED",
                "결제 승인 상태를 조회하지 못했습니다. 결제 내역을 확인해 주세요.");
        } catch (RestClientException | IllegalStateException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_LOOKUP_FAILED",
                "결제 승인 상태를 조회하지 못했습니다. 결제 내역을 확인해 주세요.");
        }
    }

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
                               long balanceAmount, String status, List<Cancel> cancels,
                               String lastTransactionKey) { }

    public record Cancel(long cancelAmount, String cancelStatus, String transactionKey) { }
}
