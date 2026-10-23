package com.kisia.staybooking.booking;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import com.kisia.staybooking.cancellation.CancellationRequestRepository;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;
import com.kisia.staybooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingQueryService {
    private final BookingRepository bookings;
    private final CancellationRequestRepository cancellations;

    public BookingQueryService(BookingRepository bookings, CancellationRequestRepository cancellations) {
        this.bookings = bookings;
        this.cancellations = cancellations;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(Long userId) {
        var mine = bookings.findByUserUserIdOrderByCreatedAtDesc(userId);
        if (mine.isEmpty()) return List.of();
        var ids = mine.stream().map(BookingEntity::getBookingId).toList();
        Set<Long> requested = cancellations.findBookingIdsWithStatus(ids, CancellationRequestStatus.REQUESTED)
            .stream().collect(Collectors.toSet());
        return mine.stream()
            .map(booking -> BookingResponse.from(booking, requested.contains(booking.getBookingId())))
            .toList();
    }

    /** 예약 번호를 추측해도 다른 회원 예약을 확인할 수 없도록 소유자를 함께 확인한다. */
    @Transactional(readOnly = true)
    public BookingResponse mineDetail(Long userId, Long bookingId) {
        BookingEntity booking = ownedBooking(userId, bookingId);
        return BookingResponse.from(booking, cancellations.existsByBookingBookingIdAndStatus(
            bookingId, CancellationRequestStatus.REQUESTED));
    }

    @Transactional(readOnly = true)
    public List<CancellationRequestHistory> mineCancellationRequests(Long userId, Long bookingId) {
        ownedBooking(userId, bookingId);
        return cancellations.findByBookingBookingIdOrderByCreatedAtDescCancellationRequestIdDesc(bookingId)
            .stream().map(CancellationRequestHistory::from).toList();
    }

    private BookingEntity ownedBooking(Long userId, Long bookingId) {
        return bookings.findById(bookingId)
            .filter(found -> found.getUser().getUserId().equals(userId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "예약을 찾을 수 없습니다."));
    }
}
