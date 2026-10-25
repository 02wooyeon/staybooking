package com.kisia.staybooking.refund;

/** 예약 응답에 필요한 환불 값만 DB에서 읽는다. */
public record RefundSummary(Long bookingId, RefundStatus status, long amount) { }
