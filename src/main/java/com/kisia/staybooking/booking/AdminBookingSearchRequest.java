package com.kisia.staybooking.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record AdminBookingSearchRequest(
    @Positive Long bookingId,
    BookingStatus status,
    @Min(0) @Max(1000) Integer page,
    @Min(1) @Max(100) Integer size,
    Boolean cancellationRequested
) {
    public AdminBookingSearchRequest(Long bookingId, BookingStatus status, Integer page, Integer size) {
        this(bookingId, status, page, size, null);
    }
}
