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
        PaymentEntity prepared = repository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."));
        var booking = prepared.getBooking();
        if (!booking.getUser().getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
        }
        if (prepared.getStatus() == PaymentStatus.PAID
                && paymentKey.equals(prepared.getPaymentKey())
                && amount == prepared.getPaidAmount()) {
            return prepared;
        }
        if (prepared.getStatus() != PaymentStatus.READY
                || !"TOSS".equals(prepared.getPaymentMethod())
                || booking.getStatus() != BookingStatus.PAYMENT_PENDING
                || !LocalDateTime.now(ZoneOffset.UTC).isBefore(booking.getPaymentExpiresAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_UNAVAILABLE", "결제 가능 시간이 지났거나 이미 처리됐습니다.");
        }
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
        if (!"DONE".equals(confirmed.status()) || !orderId.equals(confirmed.orderId())
                || !paymentKey.equals(confirmed.paymentKey()) || prepared.getPaidAmount() != confirmed.totalAmount()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CONFIRM_MISMATCH",
                "결제 승인 정보가 주문 정보와 일치하지 않습니다. 결제 내역을 확인해 주세요.");
        }
        return payments.complete(userId, confirmed.orderId(), confirmed.paymentKey(), confirmed.totalAmount());
    }
}
