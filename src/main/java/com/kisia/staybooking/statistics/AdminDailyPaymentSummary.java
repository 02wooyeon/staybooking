package com.kisia.staybooking.statistics;

import java.time.LocalDate;

public record AdminDailyPaymentSummary(
    LocalDate date,
    long grossPaidAmount,
    long approvedRefundAmount,
    long netPaidAmount,
    long requestedRefundAmount
) {
}
