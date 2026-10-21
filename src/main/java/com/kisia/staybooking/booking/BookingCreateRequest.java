package com.kisia.staybooking.booking;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingCreateRequest(
    @NotNull @Positive Long roomId,
    @NotNull @FutureOrPresent LocalDate checkIn,
    @NotNull @FutureOrPresent LocalDate checkOut,
    @Positive int guests
) {
}
