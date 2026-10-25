package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.PageResponse;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    /** 최근 토스 결제를 페이지 단위로 점검한다. 각 건의 조회 실패도 목록에 남긴다. */
    @Transactional(readOnly = true)
    public PageResponse<Report> scan(int page, int size) {
        Page<PaymentEntity> candidates = payments.findByPaymentMethodOrderByCreatedAtDescPaymentIdDesc(
            "TOSS", PageRequest.of(page, size));
        List<Report> reports = candidates.getContent().stream().map(payment -> {
            try {
                return checkPayment(payment);
            } catch (ApiException e) {
                if (!"PAYMENT_LOOKUP_FAILED".equals(e.getCode())
                        && !"PAYMENT_NOT_CONFIGURED".equals(e.getCode())) throw e;
                return report(payment, refundFor(payment), null, "LOOKUP_FAILED", Result.LOOKUP_FAILED);
            }
        }).toList();
        return new PageResponse<>(reports, page, size, candidates.getTotalElements(),
            candidates.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Report check(Long paymentId) {
        PaymentEntity payment = payments.findById(paymentId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."));
        return checkPayment(payment);
    }

    private Report checkPayment(PaymentEntity payment) {
        RefundEntity refund = refundFor(payment);
        if (!"TOSS".equals(payment.getPaymentMethod())) {
            return report(payment, refund, null, "NOT_APPLICABLE", Result.NOT_APPLICABLE);
        }

        TossPaymentGateway.Confirmation provider;
        try {
            provider = gateway.findByOrderId(payment.getOrderId());
        } catch (ApiException e) {
            if (!"PROVIDER_PAYMENT_NOT_FOUND".equals(e.getCode())) throw e;
            return report(payment, refund, null, "NOT_FOUND",
                payment.getStatus() == PaymentStatus.READY ? Result.MATCHED : Result.REVIEW_REQUIRED);
        }

        boolean identityMatches = payment.getOrderId().equals(provider.orderId())
            && payment.getPaidAmount() == provider.totalAmount()
            && (payment.getStatus() == PaymentStatus.READY
                || payment.getPaymentKey() != null && payment.getPaymentKey().equals(provider.paymentKey()));
        boolean statusMatches = switch (payment.getStatus()) {
            case READY -> "READY".equals(provider.status()) || "IN_PROGRESS".equals(provider.status());
            case PAID -> "DONE".equals(provider.status())
                && balanceIs(provider, payment.getPaidAmount())
                && payment.getBooking().getStatus() != BookingStatus.PAYMENT_PENDING
                && (payment.getBooking().getStatus() == BookingStatus.CANCELLED
                    ? refund != null && settled(refund) && refund.getAmount() == 0
                    : refund == null);
            case PARTIALLY_REFUNDED -> "PARTIAL_CANCELED".equals(provider.status())
                && payment.getBooking().getStatus() == BookingStatus.CANCELLED
                && approvedRefundBetween(refund, 0, payment.getPaidAmount())
                && balanceIs(provider, payment.getPaidAmount() - refund.getAmount());
            case REFUNDED -> "CANCELED".equals(provider.status())
                && payment.getBooking().getStatus() == BookingStatus.CANCELLED
                && approvedRefundOf(refund, payment.getPaidAmount()) && balanceIs(provider, 0);
            case FAILED -> "ABORTED".equals(provider.status()) || "EXPIRED".equals(provider.status());
        };
        Result result;
        if (identityMatches && statusMatches) {
            result = Result.MATCHED;
        } else if (identityMatches && payment.getStatus() == PaymentStatus.READY
                && "DONE".equals(provider.status())
                && provider.paymentKey() != null && !provider.paymentKey().isBlank()
                && balanceIs(provider, payment.getPaidAmount())) {
            result = payment.getBooking().getStatus() == BookingStatus.CANCELLED
                ? Result.CANCELLED_BOOKING_CHARGED : Result.PROVIDER_CHARGED_NOT_RECORDED;
        } else if (identityMatches && payment.getStatus() == PaymentStatus.PAID
                && payment.getBooking().getStatus() == BookingStatus.CANCELLED
                && "DONE".equals(provider.status())
                && balanceIs(provider, payment.getPaidAmount())
                && refund != null && refund.getAmount() >= 0
                && refund.getAmount() <= payment.getPaidAmount()) {
            result = switch (refund.getStatus()) {
                case REQUESTED, PROCESSING -> Result.REFUND_PENDING;
                case FAILED -> Result.REFUND_FAILED;
                default -> Result.REVIEW_REQUIRED;
            };
        } else {
            result = Result.REVIEW_REQUIRED;
        }
        return report(payment, refund, provider, provider.status(), result);
    }

    private RefundEntity refundFor(PaymentEntity payment) {
        if (payment.getStatus() == PaymentStatus.READY || payment.getStatus() == PaymentStatus.FAILED) {
            return null;
        }
        return refunds.findByBookingBookingIdIn(List.of(payment.getBooking().getBookingId()))
            .stream().findFirst().orElse(null);
    }

    private static boolean approvedRefundBetween(RefundEntity refund, long min, long max) {
        return refund != null && settled(refund)
            && refund.getAmount() > min && refund.getAmount() < max;
    }

    private static boolean approvedRefundOf(RefundEntity refund, long amount) {
        return refund != null && settled(refund)
            && refund.getAmount() == amount;
    }

    private static boolean settled(RefundEntity refund) {
        return refund.getStatus() == RefundStatus.REFUNDED || refund.getStatus() == RefundStatus.APPROVED;
    }

    private static boolean balanceIs(TossPaymentGateway.Confirmation provider, long expected) {
        return provider.balanceAmount() != null && provider.balanceAmount() == expected;
    }

    private static Report report(PaymentEntity payment, RefundEntity refund,
                                 TossPaymentGateway.Confirmation provider,
                                 String providerStatus, Result result) {
        return new Report(payment.getPaymentId(), payment.getBooking().getBookingId(),
            payment.getOrderId(), payment.getBooking().getStatus(), payment.getStatus(),
            providerStatus, payment.getPaidAmount(),
            provider == null ? null : provider.totalAmount(),
            provider == null ? null : provider.balanceAmount(),
            refund == null ? null : refund.getRefundId(),
            refund == null ? null : refund.getStatus(),
            refund == null ? null : refund.getAmount(), result, LocalDateTime.now(ZoneOffset.UTC));
    }

    public enum Result {
        MATCHED, PROVIDER_CHARGED_NOT_RECORDED, CANCELLED_BOOKING_CHARGED,
        REFUND_PENDING, REFUND_FAILED, REVIEW_REQUIRED, LOOKUP_FAILED, NOT_APPLICABLE
    }

    public record Report(Long paymentId, Long bookingId, String orderId, BookingStatus bookingStatus,
                         PaymentStatus localStatus,
                         String providerStatus, long localAmount, Long providerAmount,
                         Long providerBalanceAmount, Long refundId, RefundStatus refundStatus,
                         Long refundAmount, Result result, LocalDateTime checkedAt) { }
}
