package com.kisia.staybooking.booking;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 숙소 검색과 예약 생성·견적이 공유하는 숙박 기간 제한. */
public final class StayDatePolicy {
    public static final long MAX_NIGHTS = 30;

    private StayDatePolicy() { }

    public static boolean isValid(LocalDate checkIn, LocalDate checkOut, LocalDate today) {
        if (checkIn == null || checkOut == null || checkIn.isBefore(today)) return false;
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return nights >= 1 && nights <= MAX_NIGHTS;
    }
}
