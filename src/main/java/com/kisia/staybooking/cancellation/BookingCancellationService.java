package com.kisia.staybooking.cancellation;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.booking.CancellationSource;
import com.kisia.staybooking.refund.RefundRequestService;
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
    private final CancellationRequestRepository requests;
    private final RefundRequestService refunds;

    public BookingCancellationService(UserRepository users, BookingRepository bookings,
                                      CancellationRequestRepository requests, RefundRequestService refunds) {
        this.users = users;
        this.bookings = bookings;
        this.requests = requests;
        this.refunds = refunds;
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
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING
                && booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Booking cannot be cancelled in its current state");
        }
        boolean paymentCompleted = booking.getStatus() != BookingStatus.PAYMENT_PENDING;
        LocalDateTime checkIn = booking.getCheckInDate()
            .atTime(booking.getRoom().getAccommodation().getCheckInTime());
        if (!LocalDateTime.now(KOREA).isBefore(checkIn)) {
            throw new IllegalStateException("Cancellation after check-in is unavailable");
        }
        CancellationSource source = actor.getRole() == UserRole.ROLE_ADMIN
            ? CancellationSource.ADMIN_DIRECT : CancellationSource.APPROVER_DIRECT;
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        booking.cancel(actor, source, reasonType, reason, nowUtc);
        // 예약 잠금 아래에서 미처리 신청도 함께 마무리해 사용자 화면에 대기로 남지 않게 한다.
        requests.findByBookingBookingIdAndStatus(bookingId, CancellationRequestStatus.REQUESTED)
            .ifPresent(request -> request.approve(actor, nowUtc, "운영자 직접 취소로 처리됨"));
        if (paymentCompleted) {
            refunds.record(booking);
        }
        return booking;
    }
}
