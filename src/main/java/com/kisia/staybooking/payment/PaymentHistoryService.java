package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentHistoryService {
    private final PaymentRepository payments;
    private final RefundRepository refunds;

    public PaymentHistoryService(PaymentRepository payments, RefundRepository refunds) {
        this.payments = payments;
        this.refunds = refunds;
    }

    @Transactional(readOnly = true)
    public List<PaymentHistory> mine(Long userId) {
        List<PaymentEntity> mine = payments.findByBookingUserUserIdOrderByCreatedAtDesc(userId);
        if (mine.isEmpty()) return List.of();
        Map<Long, RefundEntity> refundsByBooking = refunds.findByBookingBookingIdIn(
            mine.stream().map(payment -> payment.getBooking().getBookingId()).distinct().toList())
            .stream().collect(Collectors.toMap(refund -> refund.getBooking().getBookingId(), Function.identity()));
        return mine.stream().map(payment -> PaymentHistory.from(payment,
            payment.getStatus() == PaymentStatus.READY || payment.getStatus() == PaymentStatus.FAILED
                ? null : refundsByBooking.get(payment.getBooking().getBookingId()))).toList();
    }

    public record PaymentHistory(Long paymentId, Long bookingId, String orderId, String paymentMethod,
                                 long amount, PaymentStatus status, LocalDateTime paidAt,
                                 Long refundId, RefundStatus refundStatus, Long refundAmount) {
        static PaymentHistory from(PaymentEntity payment, RefundEntity refund) {
            return new PaymentHistory(payment.getPaymentId(), payment.getBooking().getBookingId(),
                payment.getOrderId(), payment.getPaymentMethod(), payment.getPaidAmount(),
                payment.getStatus(), payment.getPaidAt(),
                refund == null ? null : refund.getRefundId(),
                refund == null ? null : refund.getStatus(),
                refund == null ? null : refund.getAmount());
        }
    }
}
