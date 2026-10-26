package com.kisia.staybooking.payment;

import java.util.regex.Pattern;

import com.kisia.staybooking.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * PAYMENT_003 결제 Webhook. 토스의 결제 상태 변경 Webhook은 서명이 없어서 본문 내용(상태·금액)을 믿지 않는다.
 * 본문에서는 orderId만 꺼내고, 실제 상태는 우리 시크릿 키로 토스에 다시 조회해 확인한다.
 * 결제 완료 처리는 승인 응답 유실 복구(TossPaymentConfirmationService.recover)와 같은 검증을 거친다.
 *   - 상태가 DONE이고 금액·주문번호가 사전 등록한 결제와 같을 때만 완료 처리
 *   - 이미 PAID인 주문은 그대로 두므로 같은 이벤트가 여러 번 와도 한 번만 반영된다
 */
@Service
public class TossWebhookService {
    private static final Logger log = LoggerFactory.getLogger(TossWebhookService.class);
    private static final Pattern ORDER_ID = Pattern.compile("^[A-Za-z0-9_-]{6,64}$");

    private final PaymentRepository payments;
    private final TossPaymentConfirmationService confirmations;
    private final TossPaymentGateway gateway;

    public TossWebhookService(PaymentRepository payments, TossPaymentConfirmationService confirmations,
                              TossPaymentGateway gateway) {
        this.payments = payments;
        this.confirmations = confirmations;
        this.gateway = gateway;
    }

    public Result handle(String eventType, String orderId) {
        if (!"PAYMENT_STATUS_CHANGED".equals(eventType)) return Result.IGNORED;
        if (orderId == null || !ORDER_ID.matcher(orderId).matches()) return Result.IGNORED;
        if (!gateway.isConfigured()) return Result.IGNORED;
        Long userId = payments.findUserIdByOrderId(orderId).orElse(null);
        if (userId == null) return Result.IGNORED; // 우리 서버에서 만든 주문이 아니다
        try {
            confirmations.recover(userId, orderId);
            return Result.APPLIED;
        } catch (ApiException e) {
            // 토스 조회가 잠깐 실패한 경우에만 토스가 다시 보내도록 실패로 응답한다.
            if (e.getStatus() == HttpStatus.BAD_GATEWAY && "PAYMENT_LOOKUP_FAILED".equals(e.getCode())) {
                throw e;
            }
            // 결제 취소·만료, 이미 처리된 주문, 금액 불일치 등은 다시 받아도 결과가 같으므로 받기만 한다.
            log.info("Toss webhook for order {} not applied: {}", orderId, e.getCode());
            return Result.IGNORED;
        }
    }

    public enum Result { APPLIED, IGNORED }
}
