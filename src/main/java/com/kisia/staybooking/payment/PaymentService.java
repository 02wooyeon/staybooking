package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 예약 결제 준비와 검증된 결제 결과의 저장을 담당한다. */
@Service
public class PaymentService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;

    public PaymentService(BookingRepository bookings, PaymentRepository payments) {
        this.bookings = bookings;
        this.payments = payments;
    }

    @Transactional
    public PaymentEntity prepare(Long userId, Long bookingId, String paymentMethod) {
        BookingEntity booking = ownedBooking(userId, bookingId);
        ensurePaymentPending(booking);
        PaymentEntity ready = payments.findFirstByBookingBookingIdAndStatusOrderByCreatedAtDesc(
            bookingId, PaymentStatus.READY).orElse(null);
        if (ready != null) {
            if (!ready.getPaymentMethod().equals(paymentMethod)) {
                throw new IllegalStateException("A different payment method is already prepared");
            }
            return ready;
        }
        return payments.save(new PaymentEntity(booking, newOrderId(), paymentMethod,
            booking.getTotalAmount() - booking.getUsedMileage()));
    }

    @Transactional
    public PaymentEntity complete(Long userId, String orderId, String paymentKey, long amount) {
        return completeLocked(userId, orderId, paymentKey, amount, null);
    }

    private PaymentEntity completeLocked(Long userId, String orderId, String paymentKey,
                                         long amount, String requiredMethod) {
        Long bookingId = payments.findBookingIdByOrderId(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        BookingEntity booking = ownedBooking(userId, bookingId);
        ensurePaymentPending(booking);
        PaymentEntity payment = payments.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        if (!payment.getBooking().getBookingId().equals(bookingId)) {
            throw new IllegalStateException("Payment booking changed");
        }
        if (requiredMethod != null && !requiredMethod.equals(payment.getPaymentMethod())) {
            throw new IllegalStateException("Only demo payments can use demo completion");
        }
        if (payment.getPaidAmount() != amount) {
            throw new IllegalArgumentException("Payment amount does not match");
        }
        payment.complete(paymentKey, LocalDateTime.now(ZoneOffset.UTC));
        booking.markPaid(LocalDateTime.now(ZoneOffset.UTC));
        return payment;
    }

    @Transactional
    public PaymentEntity completeDemo(Long userId, Long bookingId) {
        PaymentEntity prepared = prepare(userId, bookingId, "DEMO");
        return completeDemoOrder(userId, prepared.getOrderId(), prepared.getPaidAmount());
    }

    /** 모의 결제 경로에서는 토스 주문을 임의로 결제 완료 처리할 수 없다. */
    @Transactional
    public PaymentEntity completeDemoOrder(Long userId, String orderId, long amount) {
        return completeLocked(userId, orderId, "DEMO-" + UUID.randomUUID(), amount, "DEMO");
    }

    /** PG 호출 전 읽기 전용 확인. 결제 확정 시에는 예약 상태를 다시 검증한다. */
    @Transactional(readOnly = true)
    public PaymentAttempt attempt(Long userId, String orderId) {
        PaymentEntity payment = ownedPayment(userId, orderId);
        BookingEntity booking = payment.getBooking();
        return new PaymentAttempt(payment.getStatus(), payment.getPaymentKey(), payment.getPaidAmount(),
            payment.getPaymentMethod(), booking.getStatus(), booking.getPaymentExpiresAt());
    }

    @Transactional(readOnly = true)
    public PaymentEntity current(Long userId, String orderId) {
        return ownedPayment(userId, orderId);
    }

    private PaymentEntity ownedPayment(Long userId, String orderId) {
        PaymentEntity payment = payments.findByOrderId(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND",
                "결제를 찾을 수 없습니다."));
        if (!payment.getBooking().getUser().getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
        }
        return payment;
    }

    public record PaymentAttempt(PaymentStatus status, String paymentKey, long paidAmount,
                                 String paymentMethod, BookingStatus bookingStatus,
                                 LocalDateTime paymentExpiresAt) { }

    private BookingEntity ownedBooking(Long userId, Long bookingId) {
        BookingEntity booking = bookings.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (!booking.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Booking not found");
        }
        return booking;
    }

    private static void ensurePaymentPending(BookingEntity booking) {
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING
                || !LocalDateTime.now(ZoneOffset.UTC).isBefore(booking.getPaymentExpiresAt())) {
            throw new IllegalStateException("Payment is unavailable");
        }
    }

    private static String newOrderId() {
        return "ORDER-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
