package com.kisia.staybooking.refund;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.kisia.staybooking.booking.CancellationReasonType;

public final class RefundPolicy {
    private RefundPolicy() {
    }

    public static RefundQuote quote(CancellationReasonType reason, LocalDate checkInDate,
                                    LocalDate cancellationDate, long paidAmount, long usedMileage) {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(checkInDate);
        Objects.requireNonNull(cancellationDate);
        if (paidAmount < 0 || usedMileage < 0) {
            throw new IllegalArgumentException("Amounts must be nonnegative");
        }
        long daysBeforeCheckIn = ChronoUnit.DAYS.between(cancellationDate, checkInDate);
        if (daysBeforeCheckIn < 0) {
            throw new IllegalArgumentException("Cancellation date is after check-in date");
        }
        int rate = switch (reason) {
            case USER_FAULT -> daysBeforeCheckIn >= 3 ? 100 : daysBeforeCheckIn >= 1 ? 50 : 0;
            case ACCOMMODATION_FAULT, FORCE_MAJEURE -> 100;
        };
        // 50%는 정수 나눗셈으로 원 미만을 버린다. 곱셈 오버플로도 피한다.
        long refundAmount = switch (rate) {
            case 100 -> paidAmount;
            case 50 -> paidAmount / 2;
            default -> 0;
        };
        return new RefundQuote(rate, refundAmount, usedMileage);
    }
}
