package com.kisia.staybooking.booking;

public enum BookingStatus {
    PAYMENT_PENDING,
    PENDING,
    COMPLETED, // 예약 승인 완료. 숙박 이용 완료 상태는 별도로 설계한다.
    CANCELLED
}
