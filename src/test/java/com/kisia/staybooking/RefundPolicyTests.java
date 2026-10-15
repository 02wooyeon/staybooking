package com.kisia.staybooking;

import java.time.LocalDate;

import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.refund.RefundPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefundPolicyTests {
    private final LocalDate checkIn = LocalDate.of(2026, 11, 10);

    @Test
    void calculatesUserFaultRefundByDaysBeforeCheckIn() {
        assertEquals(101, RefundPolicy.quote(CancellationReasonType.USER_FAULT,
            checkIn, checkIn.minusDays(3), 101, 20).refundAmount());
        assertEquals(50, RefundPolicy.quote(CancellationReasonType.USER_FAULT,
            checkIn, checkIn.minusDays(2), 101, 20).refundAmount());
        assertEquals(0, RefundPolicy.quote(CancellationReasonType.USER_FAULT,
            checkIn, checkIn, 101, 20).refundAmount());
        assertEquals(20, RefundPolicy.quote(CancellationReasonType.USER_FAULT,
            checkIn, checkIn, 101, 20).mileageRefund());
    }

    @Test
    void accommodationFaultReturnsFullPayment() {
        assertEquals(101, RefundPolicy.quote(CancellationReasonType.ACCOMMODATION_FAULT,
            checkIn, checkIn.minusDays(1), 101, 20).refundAmount());
    }
}
