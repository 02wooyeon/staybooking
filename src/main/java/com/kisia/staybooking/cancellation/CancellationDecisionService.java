package com.kisia.staybooking.cancellation;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancellationDecisionService {
    private final UserRepository users;
    private final BookingRepository bookings;
    private final CancellationRequestRepository requests;

    public CancellationDecisionService(UserRepository users, BookingRepository bookings,
                                       CancellationRequestRepository requests) {
        this.users = users;
        this.bookings = bookings;
        this.requests = requests;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancellationRequestEntity reject(Long actorId, Long bookingId, Long requestId, String reason) {
        UserEntity actor = users.findById(actorId)
            .orElseThrow(() -> new IllegalArgumentException("Actor not found"));
        if (actor.getStatus() != UserStatus.ACTIVE ||
                (actor.getRole() != UserRole.ROLE_APPROVER && actor.getRole() != UserRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Decision permission required");
        }
        bookings.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        CancellationRequestEntity request = requests.findByIdForUpdate(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Cancellation request not found"));
        if (!request.getBooking().getBookingId().equals(bookingId)) {
            throw new IllegalArgumentException("Cancellation request belongs to another booking");
        }
        request.reject(actor, LocalDateTime.now(ZoneOffset.UTC), reason);
        return request;
    }
}
