package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TossPaymentConfirmationService {
    private final PaymentRepository repository;
    private final PaymentService payments;
    private final TossPaymentGateway gateway;

    public TossPaymentConfirmationService(PaymentRepository repository, PaymentService payments,
                                          TossPaymentGateway gateway) {
        this.repository = repository;
        this.payments = payments;
        this.gateway = gateway;
    }

    @Transactional
    public PaymentEntity confirm(Long userId, String orderId, String paymentKey, long amount) {
        PaymentEntity prepared = ownedPayment(userId, orderId);
        if (prepared.getStatus() == PaymentStatus.PAID
                && paymentKey.equals(prepared.getPaymentKey())
                && amount == prepared.getPaidAmount()) {
            return prepared;
        }
        requireConfirmable(prepared);
        if (prepared.getPaidAmount() != amount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_MISMATCH", "결제 금액이 일치하지 않습니다.");
        }
        TossPaymentGateway.Confirmation confirmed;
        try {
            confirmed = gateway.confirm(paymentKey, orderId, prepared.getPaidAmount());
        } catch (ApiException confirmationFailure) {
            // 승인 요청은 성공했지만 응답이 유실될 수 있다. 같은 주문의 실제 상태를 조회한다.
            try {
                confirmed = gateway.findByOrderId(orderId);
            } catch (ApiException lookupFailure) {
                throw confirmationFailure;
            }
        }
        requireMatchingConfirmation(prepared, confirmed, paymentKey);
        return payments.complete(userId, confirmed.orderId(), confirmed.paymentKey(), confirmed.totalAmount());
    }

    /** 승인 응답이 유실된 본인 주문만 토스에 조회해 로컬 결제 상태를 복구한다. */
    @Transactional
    public PaymentEntity recover(Long userId, String orderId) {
        PaymentEntity prepared = ownedPayment(userId, orderId);
        if (!"TOSS".equals(prepared.getPaymentMethod())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_UNAVAILABLE", "토스 결제 주문만 복구할 수 있습니다.");
        }
        if (prepared.getStatus() == PaymentStatus.PAID) return prepared;
        requireConfirmable(prepared);
        TossPaymentGateway.Confirmation confirmed = gateway.findByOrderId(orderId);
        requireMatchingConfirmation(prepared, confirmed, null);
        return payments.complete(userId, confirmed.orderId(), confirmed.paymentKey(), confirmed.totalAmount());
    }

    private PaymentEntity ownedPayment(Long userId, String orderId) {
        PaymentEntity payment = repository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."));
        if (!payment.getBooking().getUser().getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
        }
        return payment;
    }

    private static void requireConfirmable(PaymentEntity payment) {
        var booking = payment.getBooking();
        if (payment.getStatus() != PaymentStatus.READY
                || !"TOSS".equals(payment.getPaymentMethod())
                || booking.getStatus() != BookingStatus.PAYMENT_PENDING
                || !LocalDateTime.now(ZoneOffset.UTC).isBefore(booking.getPaymentExpiresAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_UNAVAILABLE", "결제 가능 시간이 지났거나 이미 처리됐습니다.");
        }
    }

    private static void requireMatchingConfirmation(PaymentEntity prepared,
                                                     TossPaymentGateway.Confirmation confirmed,
                                                     String expectedPaymentKey) {
        if (confirmed == null || !"DONE".equals(confirmed.status())
                || !prepared.getOrderId().equals(confirmed.orderId())
                || confirmed.paymentKey() == null || confirmed.paymentKey().isBlank()
                || (expectedPaymentKey != null && !expectedPaymentKey.equals(confirmed.paymentKey()))
                || prepared.getPaidAmount() != confirmed.totalAmount()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CONFIRM_MISMATCH",
                "결제 승인 정보가 주문 정보와 일치하지 않습니다. 결제 내역을 확인해 주세요.");
        }
    }
}
