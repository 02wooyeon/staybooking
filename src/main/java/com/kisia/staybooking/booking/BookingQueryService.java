package com.kisia.staybooking.booking;

import java.util.List;

import com.kisia.staybooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingQueryService {
    private final BookingRepository bookings;

    public BookingQueryService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(Long userId) {
        return bookings.findByUserUserIdOrderByCreatedAtDesc(userId).stream()
            .map(BookingResponse::from)
            .toList();
    }

    /** 예약 번호를 추측해도 다른 회원 예약을 확인할 수 없도록 소유자를 함께 확인한다. */
    @Transactional(readOnly = true)
    public BookingResponse mineDetail(Long userId, Long bookingId) {
        BookingEntity booking = bookings.findById(bookingId)
            .filter(found -> found.getUser().getUserId().equals(userId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "예약을 찾을 수 없습니다."));
        return BookingResponse.from(booking);
    }
}
