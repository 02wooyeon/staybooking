package com.kisia.staybooking.statistics;

import java.time.LocalDate;

public record AdminDailyBookingSummary(
    LocalDate date,
    long createdBookings,
    long approvedBookings,
    long cancelledBookings
) {
}
