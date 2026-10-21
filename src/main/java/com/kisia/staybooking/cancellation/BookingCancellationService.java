package com.kisia.staybooking.cancellation;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.booking.CancellationSource;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 승인자 또는 관리자가 운영 사유로 예약을 직접 취소하는 서비스. */
@Service
public class BookingCancellationService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final UserRepository users;
    private final BookingRepository bookings;

    public BookingCancellationService(UserRepository users, BookingRepository bookings) {
        this.users = users;
        this.bookings = bookings;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingEntity cancel(Long actorId, Long bookingId,
                                CancellationReasonType reasonType, String reason) {
        UserEntity actor = users.findById(actorId)
            .orElseThrow(() -> new IllegalArgumentException("Actor not found"));
        if (actor.getStatus() != UserStatus.ACTIVE ||
                (actor.getRole() != UserRole.ROLE_APPROVER && actor.getRole() != UserRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Cancellation permission required");
        }
        BookingEntity booking = bookings.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Booking cannot be cancelled in its current state");
        }
        LocalDateTime checkIn = booking.getCheckInDate()
            .atTime(booking.getRoom().getAccommodation().getCheckInTime());
        if (!LocalDateTime.now(KOREA).isBefore(checkIn)) {
            throw new IllegalStateException("Cancellation after check-in is unavailable");
        }
        CancellationSource source = actor.getRole() == UserRole.ROLE_ADMIN
            ? CancellationSource.ADMIN_DIRECT : CancellationSource.APPROVER_DIRECT;
        booking.cancel(actor, source, reasonType, reason, LocalDateTime.now(ZoneOffset.UTC));
        return booking;
    }
}
