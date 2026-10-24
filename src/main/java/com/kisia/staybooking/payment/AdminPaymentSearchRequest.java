package com.kisia.staybooking.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminPaymentSearchRequest(
    @Positive Long bookingId,
    PaymentStatus status,
    @Size(max = 100) String orderId,
    @Min(0) @Max(1000) Integer page,
    @Min(1) @Max(100) Integer size
) { }
