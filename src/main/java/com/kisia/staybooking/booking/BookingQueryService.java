package com.kisia.staybooking.booking;

import java.util.List;

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
}
