package com.kisia.staybooking.refund;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.TossPaymentGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 환불 재시도 전에 로컬 기록과 PG 잔액을 비교한다. 조회 결과로 상태를 자동 변경하지 않는다. */
@Service
public class RefundReconciliationService {
    private static final List<PaymentStatus> REFUNDABLE_STATUSES = List.of(
        PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);

    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final TossPaymentGateway gateway;

    public RefundReconciliationService(RefundRepository refunds, PaymentRepository payments,
                                       TossPaymentGateway gateway) {
        this.refunds = refunds;
        this.payments = payments;
        this.gateway = gateway;
    }

    @Transactional(readOnly = true)
    public Report check(Long refundId) {
        RefundEntity refund = refunds.findById(refundId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND",
                "환불 요청을 찾을 수 없습니다."));
        Long bookingId = refund.getBooking().getBookingId();
        List<PaymentEntity> candidates = payments.findByBookingBookingIdAndStatusIn(
            bookingId, REFUNDABLE_STATUSES);
        if (candidates.size() != 1) {
            return report(refund, null, null, Result.REVIEW_REQUIRED);
        }

        PaymentEntity payment = candidates.getFirst();
        if (!"TOSS".equals(payment.getPaymentMethod())) {
            return report(refund, payment, null, Result.NOT_APPLICABLE);
        }
        if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()
                || refund.getAmount() < 0 || refund.getAmount() > payment.getPaidAmount()) {
            return report(refund, payment, null, Result.REVIEW_REQUIRED);
        }

        TossPaymentGateway.Confirmation provider;
        try {
            provider = gateway.findByOrderId(payment.getOrderId());
        } catch (ApiException e) {
            if ("PAYMENT_LOOKUP_FAILED".equals(e.getCode())
                    || "PAYMENT_NOT_CONFIGURED".equals(e.getCode())) {
                return report(refund, payment, null, Result.LOOKUP_FAILED);
            }
            if ("PROVIDER_PAYMENT_NOT_FOUND".equals(e.getCode())) {
                return report(refund, payment, null, Result.REVIEW_REQUIRED);
            }
            throw e;
        }

        if (!payment.getOrderId().equals(provider.orderId())
                || !payment.getPaymentKey().equals(provider.paymentKey())
                || payment.getPaidAmount() != provider.totalAmount()
                || provider.balanceAmount() == null) {
            return report(refund, payment, provider, Result.REVIEW_REQUIRED);
        }
        long balance = provider.balanceAmount();
        Result result;
        if ("DONE".equals(provider.status()) && balance == payment.getPaidAmount()) {
            result = Result.NO_CANCELLATION_RECORDED;
        } else if (("CANCELED".equals(provider.status()) || "PARTIAL_CANCELED".equals(provider.status()))
                && balance == payment.getPaidAmount() - refund.getAmount()) {
            // 금액만 일치한다. 외부에서 별도로 취소한 건과 구별할 수 없어 자동 확정하지 않는다.
            result = Result.PROVIDER_BALANCE_MATCHES;
        } else {
            result = Result.REVIEW_REQUIRED;
        }
        return report(refund, payment, provider, result);
    }

    private static Report report(RefundEntity refund, PaymentEntity payment,
                                 TossPaymentGateway.Confirmation provider, Result result) {
        return new Report(refund.getRefundId(), refund.getBooking().getBookingId(),
            refund.getStatus(), refund.getAmount(), payment == null ? null : payment.getPaymentId(),
            payment == null ? null : payment.getStatus(),
            provider == null ? null : provider.status(),
            provider == null ? null : provider.balanceAmount(), result,
            LocalDateTime.now(ZoneOffset.UTC));
    }

    public enum Result {
        NO_CANCELLATION_RECORDED, PROVIDER_BALANCE_MATCHES,
        REVIEW_REQUIRED, LOOKUP_FAILED, NOT_APPLICABLE
    }

    public record Report(Long refundId, Long bookingId, RefundStatus localRefundStatus,
                         long refundAmount, Long paymentId, PaymentStatus localPaymentStatus,
                         String providerStatus, Long providerBalanceAmount, Result result,
                         LocalDateTime checkedAt) { }
}
