package com.kisia.staybooking.refund;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record RefundSearchRequest(
    @Positive Long bookingId,
    RefundStatus status,
    @Min(0) @Max(1000) Integer page,
    @Min(1) @Max(100) Integer size
) { }
