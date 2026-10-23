package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PG 기록과 로컬 기록을 비교만 한다. 불일치 건의 예약·환불 상태는 자동 수정하지 않는다. */
@Service
public class PaymentReconciliationService {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final TossPaymentGateway gateway;

    public PaymentReconciliationService(PaymentRepository payments, RefundRepository refunds,
                                        TossPaymentGateway gateway) {
        this.payments = payments;
        this.refunds = refunds;
        this.gateway = gateway;
    }

    @Transactional(readOnly = true)
    public Report check(Long paymentId) {
        PaymentEntity payment = payments.findById(paymentId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."));
        if (!"TOSS".equals(payment.getPaymentMethod())) {
            return report(payment, null, "NOT_APPLICABLE", Result.NOT_APPLICABLE);
        }

        TossPaymentGateway.Confirmation provider;
        try {
            provider = gateway.findByOrderId(payment.getOrderId());
        } catch (ApiException e) {
            if (!"PROVIDER_PAYMENT_NOT_FOUND".equals(e.getCode())) throw e;
            return report(payment, null, "NOT_FOUND",
                payment.getStatus() == PaymentStatus.READY ? Result.MATCHED : Result.REVIEW_REQUIRED);
        }

        boolean identityMatches = payment.getOrderId().equals(provider.orderId())
            && payment.getPaidAmount() == provider.totalAmount()
            && (payment.getStatus() == PaymentStatus.READY
                || payment.getPaymentKey() != null && payment.getPaymentKey().equals(provider.paymentKey()));
        RefundEntity refund = refunds.findByBookingBookingIdIn(
            List.of(payment.getBooking().getBookingId())).stream().findFirst().orElse(null);
        boolean statusMatches = switch (payment.getStatus()) {
            case READY -> "READY".equals(provider.status()) || "IN_PROGRESS".equals(provider.status());
            case PAID -> "DONE".equals(provider.status())
                && balanceIs(provider, payment.getPaidAmount())
                && (refund == null || refund.getStatus() != RefundStatus.APPROVED || refund.getAmount() == 0);
            case PARTIALLY_REFUNDED -> "PARTIAL_CANCELED".equals(provider.status())
                && approvedRefundBetween(refund, 0, payment.getPaidAmount())
                && balanceIs(provider, payment.getPaidAmount() - refund.getAmount());
            case REFUNDED -> "CANCELED".equals(provider.status())
                && approvedRefundOf(refund, payment.getPaidAmount()) && balanceIs(provider, 0);
            case FAILED -> "ABORTED".equals(provider.status()) || "EXPIRED".equals(provider.status());
        };
        return report(payment, provider, provider.status(),
            identityMatches && statusMatches ? Result.MATCHED : Result.REVIEW_REQUIRED);
    }

    private static boolean approvedRefundBetween(RefundEntity refund, long min, long max) {
        return refund != null && refund.getStatus() == RefundStatus.APPROVED
            && refund.getAmount() > min && refund.getAmount() < max;
    }

    private static boolean approvedRefundOf(RefundEntity refund, long amount) {
        return refund != null && refund.getStatus() == RefundStatus.APPROVED
            && refund.getAmount() == amount;
    }

    private static boolean balanceIs(TossPaymentGateway.Confirmation provider, long expected) {
        return provider.balanceAmount() != null && provider.balanceAmount() == expected;
    }

    private static Report report(PaymentEntity payment, TossPaymentGateway.Confirmation provider,
                                 String providerStatus, Result result) {
        return new Report(payment.getPaymentId(), payment.getBooking().getBookingId(),
            payment.getOrderId(), payment.getStatus(), providerStatus, payment.getPaidAmount(),
            provider == null ? null : provider.totalAmount(),
            provider == null ? null : provider.balanceAmount(), result, LocalDateTime.now(ZoneOffset.UTC));
    }

    public enum Result { MATCHED, REVIEW_REQUIRED, NOT_APPLICABLE }

    public record Report(Long paymentId, Long bookingId, String orderId, PaymentStatus localStatus,
                         String providerStatus, long localAmount, Long providerAmount,
                         Long providerBalanceAmount, Result result, LocalDateTime checkedAt) { }
}
