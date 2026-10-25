package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
        return summaries(mine);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentHistory> mineSearch(Long userId, PaymentSearchRequest request) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        PageRequest paging = PageRequest.of(page, size);
        Page<PaymentEntity> result;
        if (request.bookingId() != null && request.status() != null) {
            result = payments.findByBookingUserUserIdAndBookingBookingIdAndStatusOrderByCreatedAtDescPaymentIdDesc(
                userId, request.bookingId(), request.status(), paging);
        } else if (request.bookingId() != null) {
            result = payments.findByBookingUserUserIdAndBookingBookingIdOrderByCreatedAtDescPaymentIdDesc(
                userId, request.bookingId(), paging);
        } else if (request.status() != null) {
            result = payments.findByBookingUserUserIdAndStatusOrderByCreatedAtDescPaymentIdDesc(
                userId, request.status(), paging);
        } else {
            result = payments.findByBookingUserUserIdOrderByCreatedAtDescPaymentIdDesc(userId, paging);
        }
        return new PageResponse<>(summaries(result.getContent()), page, size,
            result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentHistory> adminSearch(AdminPaymentSearchRequest request) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        PageRequest paging = PageRequest.of(page, size);
        Page<PaymentEntity> result;
        String orderId = request.orderId() == null || request.orderId().isBlank()
            ? null : request.orderId().trim();
        if (orderId != null) {
            result = payments.searchByOrderIdForAdmin(orderId, request.bookingId(), request.status(), paging);
        } else if (request.bookingId() != null && request.status() != null) {
            result = payments.findByBookingBookingIdAndStatusOrderByCreatedAtDescPaymentIdDesc(
                request.bookingId(), request.status(), paging);
        } else if (request.bookingId() != null) {
            result = payments.findByBookingBookingIdOrderByCreatedAtDescPaymentIdDesc(request.bookingId(), paging);
        } else if (request.status() != null) {
            result = payments.findByStatusOrderByCreatedAtDescPaymentIdDesc(request.status(), paging);
        } else {
            result = payments.findAllByOrderByCreatedAtDescPaymentIdDesc(paging);
        }
        return new PageResponse<>(summaries(result.getContent()), page, size,
            result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PaymentHistory adminDetail(Long paymentId) {
        PaymentEntity payment = payments.findById(paymentId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND",
                "결제를 찾을 수 없습니다."));
        return summaries(List.of(payment)).getFirst();
    }

    private List<PaymentHistory> summaries(List<PaymentEntity> mine) {
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
                                 Long refundId, RefundStatus refundStatus, Long refundAmount,
                                 Long refundedAmount) {
        static PaymentHistory from(PaymentEntity payment, RefundEntity refund) {
            return new PaymentHistory(payment.getPaymentId(), payment.getBooking().getBookingId(),
                payment.getOrderId(), payment.getPaymentMethod(), payment.getPaidAmount(),
                payment.getStatus(), payment.getPaidAt(),
                refund == null ? null : refund.getRefundId(),
                refund == null ? null : refund.getStatus(),
                refund == null ? null : refund.getAmount(),
                refund == null ? null : refund.getStatus() == RefundStatus.REFUNDED ? refund.getAmount() : 0L);
        }
    }
}
