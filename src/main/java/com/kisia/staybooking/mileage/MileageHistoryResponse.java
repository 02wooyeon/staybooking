package com.kisia.staybooking.mileage;

import java.time.LocalDateTime;

/** 마일리지 이력 한 줄. amount 는 증감량이며 사용·차감은 음수다. */
public record MileageHistoryResponse(Long mileageHistoryId, Long userId, Long bookingId, MileageType type,
                                     long amount, long balanceAfter, String description,
                                     LocalDateTime createdAt) {
    static MileageHistoryResponse from(MileageHistoryEntity history) {
        return new MileageHistoryResponse(history.getMileageHistoryId(), history.getUser().getUserId(),
            history.getBooking() == null ? null : history.getBooking().getBookingId(), history.getType(),
            history.getAmount(), history.getBalanceAfter(), history.getDescription(), history.getCreatedAt());
    }
}
