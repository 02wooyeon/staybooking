package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재는 PG 웹훅 대신 개발용 결제 완료 요청을 사용한다.
 * 실제 PG 도입 시 complete의 paymentKey·amount 검증 자리를 PG 승인 응답 검증으로 교체한다.
 */
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
        PaymentEntity payment = payments.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        BookingEntity booking = ownedBooking(userId, payment.getBooking().getBookingId());
        ensurePaymentPending(booking);
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
        return complete(userId, prepared.getOrderId(), "DEMO-" + UUID.randomUUID(),
            prepared.getPaidAmount());
    }

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
