package com.kisia.staybooking.booking;

import java.util.List;

import com.kisia.staybooking.cancellation.CancellationRequestRepository;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ROLE_APPROVER와 ROLE_ADMIN이 처리할 예약을 조회한다. */
@Service
public class ApproverBookingQueryService {
    private final UserRepository users;
    private final BookingRepository bookings;
    private final CancellationRequestRepository cancellationRequests;

    public ApproverBookingQueryService(UserRepository users, BookingRepository bookings,
                                       CancellationRequestRepository cancellationRequests) {
        this.users = users;
        this.bookings = bookings;
        this.cancellationRequests = cancellationRequests;
    }

    @Transactional(readOnly = true)
    public List<ApproverBookingResponse> list(Long actorId, BookingStatus status) {
        UserEntity actor = users.findById(actorId)
            .orElseThrow(() -> new IllegalArgumentException("Actor not found"));
        if (actor.getStatus() != UserStatus.ACTIVE ||
            (actor.getRole() != UserRole.ROLE_APPROVER && actor.getRole() != UserRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Approval permission required");
        }

        return bookings.findByStatusOrderByCreatedAtAsc(status).stream()
            .map(booking -> ApproverBookingResponse.from(booking,
                cancellationRequests.findByBookingBookingIdAndStatus(booking.getBookingId(),
                    CancellationRequestStatus.REQUESTED).orElse(null)))
            .toList();
    }
}
