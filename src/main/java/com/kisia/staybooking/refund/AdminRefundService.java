package com.kisia.staybooking.refund;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRefundService {
    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final TossPaymentGateway gateway;

    public AdminRefundService(RefundRepository refunds, PaymentRepository payments,
                              UserRepository users, TossPaymentGateway gateway) {
        this.refunds = refunds;
        this.payments = payments;
        this.users = users;
        this.gateway = gateway;
    }

    @Transactional
    public RefundEntity process(Long adminId, Long refundId, RefundStatus decision) {
        var admin = users.findById(adminId)
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다."));
        if (admin.getRole() != UserRole.ROLE_ADMIN || admin.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");
        }
        RefundEntity refund = refunds.findByIdForUpdate(refundId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "환불 건을 찾을 수 없습니다."));
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_ALREADY_PROCESSED", "이미 처리된 환불 건입니다.");
        }
        if (decision != RefundStatus.APPROVED && decision != RefundStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_DECISION", "환불 승인 또는 반려만 가능합니다.");
        }
        if (decision == RefundStatus.APPROVED) {
            settle(refund);
        }
        refund.process(admin, decision, LocalDateTime.now(ZoneOffset.UTC));
        return refund;
    }

    private void settle(RefundEntity refund) {
        var paid = payments.findByBookingBookingIdAndStatus(
            refund.getBooking().getBookingId(), com.kisia.staybooking.payment.PaymentStatus.PAID);
        if (paid.size() != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_REFUNDABLE", "환불 가능한 결제를 확인할 수 없습니다.");
        }
        PaymentEntity payment = paid.getFirst();
        long amount = refund.getAmount();
        if (amount < 0 || amount > payment.getPaidAmount()) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_AMOUNT_MISMATCH", "환불 금액이 결제 금액과 일치하지 않습니다.");
        }
        if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_REFUNDABLE", "결제 키를 확인할 수 없습니다.");
        }
        if (amount > 0 && !payment.getPaymentKey().startsWith("DEMO-")) {
            String reason = refund.getReason() == null || refund.getReason().isBlank()
                ? "예약 취소" : refund.getReason();
            var result = gateway.cancel(payment.getPaymentKey(),
                reason.length() > 200 ? reason.substring(0, 200) : reason,
                amount, refund.getRefundId());
            String expectedStatus = amount == payment.getPaidAmount() ? "CANCELED" : "PARTIAL_CANCELED";
            if (!payment.getPaymentKey().equals(result.paymentKey())
                    || !payment.getOrderId().equals(result.orderId())
                    || result.totalAmount() != payment.getPaidAmount()
                    || result.balanceAmount() != payment.getPaidAmount() - amount
                    || !expectedStatus.equals(result.status())
                    || result.cancels() == null
                    || result.cancels().stream().noneMatch(c -> c.cancelAmount() == amount)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CANCEL_MISMATCH",
                    "토스 결제 취소 결과가 환불 요청과 일치하지 않습니다. 결제 내역을 확인해 주세요.");
            }
        }
        payment.refund(amount);
    }
}
