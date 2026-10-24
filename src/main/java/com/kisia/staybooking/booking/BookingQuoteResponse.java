package com.kisia.staybooking.booking;

import java.time.LocalDate;

// 할인·세금은 아직 정책이 없어 0이며, 내부 할인 정책 식별자 같은 값은 넣지 않는다.
public record BookingQuoteResponse(Long accommodationId, String accommodationName, Long roomId, String roomName,
                                   LocalDate checkIn, LocalDate checkOut, long nights, int guests,
                                   long nightlyPrice, long roomTotal, long discountAmount, long totalAmount,
                                   int availableCount, boolean available) {
}
