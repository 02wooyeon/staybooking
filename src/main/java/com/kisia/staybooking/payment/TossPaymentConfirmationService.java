package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TossPaymentConfirmationService {
    private static final Logger log = LoggerFactory.getLogger(TossPaymentConfirmationService.class);
    private final PaymentService payments;
    private final TossPaymentGateway gateway;

    public TossPaymentConfirmationService(PaymentService payments, TossPaymentGateway gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    public PaymentEntity confirm(Long userId, String orderId, String paymentKey, long amount) {
        PaymentService.PaymentAttempt prepared = payments.attempt(userId, orderId);
        if (prepared.status() == PaymentStatus.PAID
                && paymentKey.equals(prepared.paymentKey())
                && amount == prepared.paidAmount()) {
            return payments.current(userId, orderId);
        }
        requireConfirmable(prepared);
        if (prepared.paidAmount() != amount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_MISMATCH", "결제 금액이 일치하지 않습니다.");
        }
        TossPaymentGateway.Confirmation confirmed;
        try {
            confirmed = gateway.confirm(paymentKey, orderId, prepared.paidAmount());
        } catch (ApiException confirmationFailure) {
            // 승인 요청은 성공했지만 응답이 유실될 수 있다. 같은 주문의 실제 상태를 조회한다.
            try {
                confirmed = gateway.findByOrderId(orderId);
            } catch (ApiException lookupFailure) {
                throw confirmationFailure;
            }
        }
        requireMatchingConfirmation(orderId, prepared, confirmed, paymentKey);
        return recordConfirmation(userId, confirmed);
    }

    /** 승인 응답이 유실된 본인 주문만 토스에 조회해 로컬 결제 상태를 복구한다. */
    public PaymentEntity recover(Long userId, String orderId) {
        PaymentService.PaymentAttempt prepared = payments.attempt(userId, orderId);
        if (!"TOSS".equals(prepared.paymentMethod())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_UNAVAILABLE", "토스 결제 주문만 복구할 수 있습니다.");
        }
        if (prepared.status() == PaymentStatus.PAID) return payments.current(userId, orderId);
        requireConfirmable(prepared);
        TossPaymentGateway.Confirmation confirmed = gateway.findByOrderId(orderId);
        requireMatchingConfirmation(orderId, prepared, confirmed, null);
        return recordConfirmation(userId, confirmed);
    }

    private PaymentEntity recordConfirmation(Long userId, TossPaymentGateway.Confirmation confirmed) {
        try {
            return payments.complete(userId, confirmed.orderId(), confirmed.paymentKey(), confirmed.totalAmount());
        } catch (RuntimeException completionFailure) {
            // 다른 요청이 같은 결제를 먼저 저장했는지 확인한다. PG 승인액이 로컬에 남지 않은 경우에는
            // 재결제를 유도하지 않고 관리자 대조가 필요한 상태를 명시한다.
            PaymentService.PaymentAttempt latest;
            try {
                latest = payments.attempt(userId, confirmed.orderId());
            } catch (RuntimeException lookupFailure) {
                completionFailure.addSuppressed(lookupFailure);
                throw completionFailure;
            }
            if (latest.status() == PaymentStatus.PAID
                    && confirmed.paymentKey().equals(latest.paymentKey())
                    && confirmed.totalAmount() == latest.paidAmount()) {
                return payments.current(userId, confirmed.orderId());
            }
            log.error("PG payment confirmed but local completion failed for order {}",
                confirmed.orderId(), completionFailure);
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_RECONCILIATION_REQUIRED",
                "결제 승인 후 예약 상태를 확정하지 못했습니다. 다시 결제하지 말고 결제 내역 확인을 요청해 주세요.");
        }
    }

    private static void requireConfirmable(PaymentService.PaymentAttempt payment) {
        if (payment.status() != PaymentStatus.READY
                || !"TOSS".equals(payment.paymentMethod())
                || payment.bookingStatus() != BookingStatus.PAYMENT_PENDING
                || !LocalDateTime.now(ZoneOffset.UTC).isBefore(payment.paymentExpiresAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_UNAVAILABLE", "결제 가능 시간이 지났거나 이미 처리됐습니다.");
        }
    }

    private static void requireMatchingConfirmation(String orderId, PaymentService.PaymentAttempt prepared,
                                                     TossPaymentGateway.Confirmation confirmed,
                                                     String expectedPaymentKey) {
        if (confirmed == null || !"DONE".equals(confirmed.status())
                || !orderId.equals(confirmed.orderId())
                || confirmed.paymentKey() == null || confirmed.paymentKey().isBlank()
                || (expectedPaymentKey != null && !expectedPaymentKey.equals(confirmed.paymentKey()))
                || prepared.paidAmount() != confirmed.totalAmount()
                || confirmed.balanceAmount() == null
                || prepared.paidAmount() != confirmed.balanceAmount()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CONFIRM_MISMATCH",
                "결제 승인 정보가 주문 정보와 일치하지 않습니다. 결제 내역을 확인해 주세요.");
        }
    }
}
