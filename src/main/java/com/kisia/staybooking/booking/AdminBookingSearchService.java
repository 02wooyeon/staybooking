package com.kisia.staybooking.booking;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.cancellation.CancellationRequestRepository;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;
import com.kisia.staybooking.common.PageResponse;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBookingSearchService {
    private final UserRepository users;
    private final BookingRepository bookings;
    private final CancellationRequestRepository requests;

    public AdminBookingSearchService(UserRepository users, BookingRepository bookings,
                                     CancellationRequestRepository requests) {
        this.users = users;
        this.bookings = bookings;
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public PageResponse<ApproverBookingResponse> search(Long adminId, AdminBookingSearchRequest request) {
        var admin = users.findById(adminId)
            .orElseThrow(() -> new IllegalStateException("Administrator permission required"));
        if (admin.getStatus() != UserStatus.ACTIVE || admin.getRole() != UserRole.ROLE_ADMIN) {
            throw new IllegalStateException("Administrator permission required");
        }
        return searchPage(request);
    }

    /** 승인자 화면에도 관리자와 동일한 예약 검색 조건·응답을 제공한다. */
    @Transactional(readOnly = true)
    public PageResponse<ApproverBookingResponse> searchForOperator(Long actorId, AdminBookingSearchRequest request) {
        var actor = users.findById(actorId)
            .orElseThrow(() -> new IllegalStateException("Operator permission required"));
        if (actor.getStatus() != UserStatus.ACTIVE
                || (actor.getRole() != UserRole.ROLE_APPROVER && actor.getRole() != UserRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Operator permission required");
        }
        return searchPage(request);
    }

    private PageResponse<ApproverBookingResponse> searchPage(AdminBookingSearchRequest request) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        PageRequest paging = PageRequest.of(page, size);
        Page<BookingEntity> result;
        if (request.cancellationRequested() != null) {
            result = bookings.findByPendingCancellation(request.bookingId(), request.status(),
                request.cancellationRequested(), CancellationRequestStatus.REQUESTED, paging);
        } else if (request.bookingId() != null && request.status() != null) {
            result = bookings.findByBookingIdAndStatusOrderByCreatedAtDescBookingIdDesc(
                request.bookingId(), request.status(), paging);
        } else if (request.bookingId() != null) {
            result = bookings.findByBookingIdOrderByCreatedAtDescBookingIdDesc(request.bookingId(), paging);
        } else if (request.status() != null) {
            result = bookings.findByStatusOrderByCreatedAtDescBookingIdDesc(request.status(), paging);
        } else {
            result = bookings.findAllByOrderByCreatedAtDescBookingIdDesc(paging);
        }
        List<BookingEntity> found = result.getContent();
        if (found.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, result.getTotalElements(), result.getTotalPages());
        }
        Map<Long, CancellationRequestEntity> pendingByBooking = requests.findByBookingBookingIdInAndStatus(
            found.stream().map(BookingEntity::getBookingId).toList(), CancellationRequestStatus.REQUESTED)
            .stream().collect(Collectors.toMap(r -> r.getBooking().getBookingId(), Function.identity()));
        return new PageResponse<>(found.stream().map(booking -> ApproverBookingResponse.from(booking,
            pendingByBooking.get(booking.getBookingId()))).toList(), page, size,
            result.getTotalElements(), result.getTotalPages());
    }
}
