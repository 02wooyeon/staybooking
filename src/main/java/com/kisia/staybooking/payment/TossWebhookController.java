package com.kisia.staybooking.payment;

import com.kisia.staybooking.auth.AttemptLimiter;
import com.kisia.staybooking.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 엑셀 API 명세서 PAY-03 (POST /api/v1/payments/webhook). 토스 개발자센터의 웹훅 주소에 이 경로를 등록한다.
// 2xx가 아니면 토스가 다시 보내므로, 처리할 필요가 없는 이벤트도 200으로 받는다.
@RestController
@RequestMapping("/api/v1/payments")
public class TossWebhookController {
    private static final int MAX_CALLS_PER_IP = 200;

    private final TossWebhookService webhooks;
    private final AttemptLimiter limiter;

    public TossWebhookController(TossWebhookService webhooks, AttemptLimiter limiter) {
        this.webhooks = webhooks;
        this.limiter = limiter;
    }

    @PostMapping("/webhook")
    public WebhookResponse receive(@RequestBody TossWebhook body, HttpServletRequest request) {
        // 누구나 부를 수 있는 주소라서, 한 IP가 토스 조회를 계속 일으키지 못하게 횟수를 제한한다.
        String key = "toss-webhook:" + request.getRemoteAddr();
        if (limiter.isLocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "요청이 너무 많습니다.");
        }
        limiter.recordFailure(key, MAX_CALLS_PER_IP);
        String orderId = body.data() == null ? null : body.data().orderId();
        return new WebhookResponse(webhooks.handle(body.eventType(), orderId).name());
    }

    // 토스 PAYMENT_STATUS_CHANGED 본문 중 쓰는 값만 받는다. status 등 나머지 값은 토스에 다시 조회한다.
    public record TossWebhook(String eventType, Data data) {
        public record Data(String orderId) { }
    }

    public record WebhookResponse(String result) { }
}
