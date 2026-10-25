package com.kisia.staybooking.cancellation;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationSource;
import com.kisia.staybooking.refund.RefundRequestService;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancellationDecisionService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final UserRepository users;
    private final BookingRepository bookings;
    private final CancellationRequestRepository requests;
    private final RefundRequestService refunds;

    public CancellationDecisionService(UserRepository users, BookingRepository bookings,
                                       CancellationRequestRepository requests, RefundRequestService refunds) {
        this.users = users;
        this.bookings = bookings;
        this.requests = requests;
        this.refunds = refunds;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancellationRequestEntity reject(Long actorId, Long bookingId, Long requestId, String reason) {
        UserEntity actor = approver(actorId);
        bookings.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        CancellationRequestEntity request = requests.findByIdForUpdate(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Cancellation request not found"));
        if (!request.getBooking().getBookingId().equals(bookingId)) {
            throw new IllegalArgumentException("Cancellation request belongs to another booking");
        }
        if (request.getStatus() == CancellationRequestStatus.REJECTED
                && request.getDecidedBy() != null
                && actorId.equals(request.getDecidedBy().getUserId())
                && Objects.equals(request.getDecisionReason(), reason)) {
            return request;
        }
        request.reject(actor, LocalDateTime.now(ZoneOffset.UTC), reason);
        return request;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancellationRequestEntity approve(Long actorId, Long bookingId, Long requestId, String reason) {
        UserEntity actor = approver(actorId);
        BookingEntity booking = bookings.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        CancellationRequestEntity request = requests.findByIdForUpdate(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Cancellation request not found"));
        if (!request.getBooking().getBookingId().equals(bookingId)) {
            throw new IllegalArgumentException("Cancellation request belongs to another booking");
        }
        if (request.getStatus() == CancellationRequestStatus.APPROVED
                && booking.getStatus() == BookingStatus.CANCELLED
                && booking.getCancelSource() == CancellationSource.USER_REQUEST
                && request.getDecidedBy() != null
                && actorId.equals(request.getDecidedBy().getUserId())
                && booking.getCancelledBy() != null
                && actorId.equals(booking.getCancelledBy().getUserId())
                && Objects.equals(request.getDecisionReason(), reason)) {
            return request;
        }
        requireCancellableBeforeCheckIn(booking);
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        request.approve(actor, nowUtc, reason);
        booking.cancel(actor, CancellationSource.USER_REQUEST, request.getReasonType(),
            request.getReasonDetail(), nowUtc);
        refunds.record(booking);
        return request;
    }

    private UserEntity approver(Long actorId) {
        UserEntity actor = users.findById(actorId)
            .orElseThrow(() -> new IllegalArgumentException("Actor not found"));
        if (actor.getStatus() != UserStatus.ACTIVE ||
                (actor.getRole() != UserRole.ROLE_APPROVER && actor.getRole() != UserRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Decision permission required");
        }
        return actor;
    }

    private static void requireCancellableBeforeCheckIn(BookingEntity booking) {
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Booking cannot be cancelled in its current state");
        }
        LocalDateTime checkIn = booking.getCheckInDate()
            .atTime(booking.getRoom().getAccommodation().getCheckInTime());
        if (!LocalDateTime.now(KOREA).isBefore(checkIn)) {
            throw new IllegalStateException("Cancellation after check-in is unavailable");
        }
    }
}
