package com.kisia.staybooking.statistics;

/** Local payment and refund totals, not the payment provider's final settlement. */
public record AdminPaymentSummary(
    long grossPaidAmount,
    long approvedRefundAmount,
    long netPaidAmount,
    long requestedRefundAmount
) {
}
