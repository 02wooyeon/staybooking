package com.kisia.staybooking.booking;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.cancellation.CancellationRequestRepository;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.http.HttpStatus;
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
        requireOperator(actorId);

        List<BookingEntity> found = bookings.findByStatusOrderByCreatedAtAsc(status);
        if (found.isEmpty()) return List.of();
        Map<Long, CancellationRequestEntity> pendingByBooking = cancellationRequests
            .findByBookingBookingIdInAndStatus(found.stream().map(BookingEntity::getBookingId).toList(),
                CancellationRequestStatus.REQUESTED)
            .stream().collect(Collectors.toMap(request -> request.getBooking().getBookingId(), Function.identity()));
        return found.stream().map(booking -> ApproverBookingResponse.from(booking,
            pendingByBooking.get(booking.getBookingId()))).toList();
    }

    @Transactional(readOnly = true)
    public OperatorBookingDetailResponse detail(Long actorId, Long bookingId) {
        requireOperator(actorId);
        BookingEntity booking = bookings.findById(bookingId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "예약을 찾을 수 없습니다."));
        List<CancellationRequestEntity> history = cancellationRequests
            .findByBookingBookingIdOrderByCreatedAtDescCancellationRequestIdDesc(bookingId);
        CancellationRequestEntity pending = history.stream()
            .filter(request -> request.getStatus() == CancellationRequestStatus.REQUESTED)
            .findFirst().orElse(null);
        return new OperatorBookingDetailResponse(ApproverBookingResponse.from(booking, pending),
            history.stream().map(OperatorBookingDetailResponse.CancellationRequestDecision::from).toList());
    }

    private void requireOperator(Long actorId) {
        UserEntity actor = users.findById(actorId)
            .orElseThrow(() -> new IllegalArgumentException("Actor not found"));
        if (actor.getStatus() != UserStatus.ACTIVE ||
            (actor.getRole() != UserRole.ROLE_APPROVER && actor.getRole() != UserRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Approval permission required");
        }
    }
}
