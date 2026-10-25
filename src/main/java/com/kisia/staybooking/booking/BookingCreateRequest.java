package com.kisia.staybooking.booking;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record BookingCreateRequest(
    @NotNull @Positive Long roomId,
    @NotNull @FutureOrPresent LocalDate checkIn,
    @NotNull @FutureOrPresent LocalDate checkOut,
    @Positive int guests,
    // 쓰겠다고 신청한 마일리지. 보내지 않으면 0이며, 실제 차감은 결제가 확정될 때 일어난다.
    @PositiveOrZero Long usedMileage
) {
    public long usedMileageOrZero() {
        return usedMileage == null ? 0 : usedMileage;
    }
}
