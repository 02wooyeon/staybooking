package com.kisia.staybooking.booking;

import java.time.LocalDate;

/** 화면과 API가 엔티티를 직접 노출하지 않도록 만드는 예약 응답 모델. */
public record BookingResponse(
    Long bookingId, Long accommodationId, String accommodationName, Long roomId, String roomName,
    LocalDate checkIn, LocalDate checkOut, int guests, long totalAmount, BookingStatus status
) {
    public static BookingResponse from(BookingEntity booking) {
        return new BookingResponse(booking.getBookingId(), booking.getRoom().getAccommodation().getAccommodationId(),
            booking.getRoom().getAccommodation().getName(), booking.getRoom().getRoomId(), booking.getRoom().getName(),
            booking.getCheckInDate(), booking.getCheckOutDate(), booking.getGuests(), booking.getTotalAmount(),
            booking.getStatus());
    }
}
