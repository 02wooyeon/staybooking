package com.kisia.staybooking.booking;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kisia.staybooking.cancellation.CancellationRequestRepository;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.PaymentVerification;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundPolicy;
import com.kisia.staybooking.refund.RefundQuote;
import com.kisia.staybooking.refund.RefundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingQueryService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final BookingRepository bookings;
    private final CancellationRequestRepository cancellations;
    private final RefundRepository refunds;
    private final PaymentRepository payments;

    public BookingQueryService(BookingRepository bookings, CancellationRequestRepository cancellations,
                               RefundRepository refunds, PaymentRepository payments) {
        this.bookings = bookings;
        this.cancellations = cancellations;
        this.refunds = refunds;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(Long userId) {
        var mine = bookings.findByUserUserIdOrderByCreatedAtDesc(userId);
        if (mine.isEmpty()) return List.of();
        var ids = mine.stream().map(BookingEntity::getBookingId).toList();
        Set<Long> requested = cancellations.findBookingIdsWithStatus(ids, CancellationRequestStatus.REQUESTED)
            .stream().collect(Collectors.toSet());
        Map<Long, RefundEntity> refundsByBooking = refunds.findByBookingBookingIdIn(ids).stream()
            .collect(Collectors.toMap(refund -> refund.getBooking().getBookingId(), Function.identity()));
        return mine.stream()
            .map(booking -> BookingResponse.from(booking, requested.contains(booking.getBookingId()),
                refundsByBooking.get(booking.getBookingId())))
            .toList();
    }

    /** 예약 번호를 추측해도 다른 회원 예약을 확인할 수 없도록 소유자를 함께 확인한다. */
    @Transactional(readOnly = true)
    public BookingResponse mineDetail(Long userId, Long bookingId) {
        BookingEntity booking = ownedBooking(userId, bookingId);
        RefundEntity refund = refunds.findByBookingBookingIdIn(List.of(bookingId)).stream().findFirst().orElse(null);
        return BookingResponse.from(booking, cancellations.existsByBookingBookingIdAndStatus(
            bookingId, CancellationRequestStatus.REQUESTED), refund);
    }

    @Transactional(readOnly = true)
    public RefundQuote refundQuote(Long userId, Long bookingId, CancellationReasonType reasonType) {
        BookingEntity booking = ownedBooking(userId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_CANCELLABLE", "취소 가능한 예약이 아닙니다.");
        }
        LocalDateTime now = LocalDateTime.now(KOREA);
        LocalDateTime checkIn = booking.getCheckInDate()
            .atTime(booking.getRoom().getAccommodation().getCheckInTime());
        if (!now.isBefore(checkIn)) {
            throw new ApiException(HttpStatus.CONFLICT, "CANCELLATION_CLOSED", "체크인 이후에는 취소할 수 없습니다.");
        }
        var paid = payments.findByBookingBookingIdAndStatus(bookingId, PaymentStatus.PAID);
        if (paid.size() != 1 || !PaymentVerification.matches(booking, paid.getFirst())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_VERIFIED", "결제 내역을 확인할 수 없습니다.");
        }
        return RefundPolicy.quote(reasonType, booking.getCheckInDate(), now.toLocalDate(),
            paid.getFirst().getPaidAmount(), booking.getUsedMileage());
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
