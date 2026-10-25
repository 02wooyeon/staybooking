package com.kisia.staybooking.booking;

import java.time.LocalDate;

/**
 * 할인·세금은 아직 정책이 없어 0이며, 내부 할인 정책 식별자 같은 값은 넣지 않는다.
 * totalAmount 는 마일리지를 빼기 전 총액이고, 실제 결제할 금액은 payAmount 다.
 */
public record BookingQuoteResponse(Long accommodationId, String accommodationName, Long roomId, String roomName,
                                   LocalDate checkIn, LocalDate checkOut, long nights, int guests,
                                   long nightlyPrice, long roomTotal, long discountAmount, long totalAmount,
                                   int availableCount, boolean available,
                                   long usedMileage, long payAmount, long mileageBalance, long usableMileage) {
}
