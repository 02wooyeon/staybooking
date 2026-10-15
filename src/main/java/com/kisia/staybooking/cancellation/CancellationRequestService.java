package com.kisia.staybooking.cancellation;

import java.time.LocalDateTime;
import java.time.ZoneId;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancellationRequestService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final UserRepository users;
    private final BookingRepository bookings;
    private final CancellationRequestRepository requests;

    public CancellationRequestService(UserRepository users, BookingRepository bookings,
                                      CancellationRequestRepository requests) {
        this.users = users;
        this.bookings = bookings;
        this.requests = requests;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancellationRequestEntity request(Long userId, Long bookingId,
                                             CancellationReasonType reasonType, String reasonDetail) {
        UserEntity user = users.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getRole() != UserRole.ROLE_USER || user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Only active users can request cancellation");
        }
        // 신청 중복 여부를 검사하는 동안 같은 예약의 신청을 직렬화한다.
        BookingEntity booking = bookings.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (!booking.getUser().getUserId().equals(userId)) {
            throw new IllegalStateException("Only the booking owner can request cancellation");
        }
        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Booking cannot be cancelled in its current state");
        }
        LocalDateTime checkIn = booking.getCheckInDate()
            .atTime(booking.getRoom().getAccommodation().getCheckInTime());
        if (!LocalDateTime.now(KOREA).isBefore(checkIn)) {
            throw new IllegalStateException("Cancellation after check-in is unavailable");
        }
        if (requests.existsByBookingBookingIdAndStatus(bookingId, CancellationRequestStatus.REQUESTED)) {
            throw new IllegalStateException("A cancellation request is already pending");
        }
        return requests.save(new CancellationRequestEntity(booking, user, reasonType, reasonDetail));
    }
}
