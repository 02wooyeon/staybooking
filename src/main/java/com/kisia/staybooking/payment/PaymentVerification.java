package com.kisia.staybooking.payment;

import com.kisia.staybooking.booking.BookingEntity;

/** 예약 처리와 환불에서 동일하게 적용하는 결제 기록 검증. */
public final class PaymentVerification {
    private PaymentVerification() { }

    public static boolean matches(BookingEntity booking, PaymentEntity payment) {
        return payment.getStatus() == PaymentStatus.PAID
            && payment.getPaidAt() != null
            && payment.getBooking().getBookingId().equals(booking.getBookingId())
            && payment.getPaidAmount() == booking.getTotalAmount() - booking.getUsedMileage();
    }
}
