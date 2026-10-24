package com.kisia.staybooking.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundStatus;

/** 화면과 API가 엔티티를 직접 노출하지 않도록 만드는 예약 응답 모델. */
public record BookingResponse(
    Long bookingId, Long accommodationId, String accommodationName, Long roomId, String roomName,
    LocalDate checkIn, LocalDate checkOut, int guests, long totalAmount, BookingStatus status,
    boolean cancellationRequested, LocalDateTime paymentExpiresAt,
    RefundStatus refundStatus, Long refundAmount
) {
    public static BookingResponse from(BookingEntity booking) {
        return from(booking, false, null);
    }

    public static BookingResponse from(BookingEntity booking, boolean cancellationRequested) {
        return from(booking, cancellationRequested, null);
    }

    public static BookingResponse from(BookingEntity booking, boolean cancellationRequested, RefundEntity refund) {
        return new BookingResponse(booking.getBookingId(), booking.getRoom().getAccommodation().getAccommodationId(),
            booking.getRoom().getAccommodation().getName(), booking.getRoom().getRoomId(), booking.getRoom().getName(),
            booking.getCheckInDate(), booking.getCheckOutDate(), booking.getGuests(), booking.getTotalAmount(),
            booking.getStatus(), cancellationRequested, booking.getPaymentExpiresAt(),
            refund == null ? null : refund.getStatus(), refund == null ? null : refund.getAmount());
    }
}
