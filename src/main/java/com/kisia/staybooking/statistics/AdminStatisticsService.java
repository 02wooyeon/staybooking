package com.kisia.staybooking.statistics;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.accommodation.AccommodationStatus;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.qna.QnaRepository;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.room.RoomStatus;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatisticsService {
    private final UserRepository users;
    private final AccommodationRepository accommodations;
    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final QnaRepository questions;

    public AdminStatisticsService(UserRepository users, AccommodationRepository accommodations,
                                  RoomRepository rooms, BookingRepository bookings,
                                  PaymentRepository payments, RefundRepository refunds,
                                  QnaRepository questions) {
        this.users = users;
        this.accommodations = accommodations;
        this.rooms = rooms;
        this.bookings = bookings;
        this.payments = payments;
        this.refunds = refunds;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public AdminOverview overview() {
        return new AdminOverview(
            users.countByStatus(UserStatus.ACTIVE),
            accommodations.countByStatus(AccommodationStatus.ACTIVE),
            rooms.countByStatusAndAccommodationStatus(RoomStatus.ACTIVE, AccommodationStatus.ACTIVE),
            bookings.countByStatus(BookingStatus.PAYMENT_PENDING),
            bookings.countByStatus(BookingStatus.PENDING),
            bookings.countByStatus(BookingStatus.COMPLETED),
            bookings.countByStatus(BookingStatus.CANCELLED),
            payments.countByStatus(PaymentStatus.PAID),
            refunds.countByStatus(RefundStatus.REQUESTED) + refunds.countByStatus(RefundStatus.PROCESSING)
                + refunds.countByStatus(RefundStatus.FAILED),
            questions.countVisibleUnanswered()
        );
    }

    @Transactional(readOnly = true)
    public AdminPaymentSummary paymentSummary() {
        long grossPaidAmount = payments.sumPaidAmountByStatusIn(List.of(
            PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
        long approvedRefundAmount = refunds.sumAmountByStatus(RefundStatus.REFUNDED)
            + refunds.sumAmountByStatus(RefundStatus.APPROVED);
        long requestedRefundAmount = refunds.sumAmountByStatus(RefundStatus.REQUESTED)
            + refunds.sumAmountByStatus(RefundStatus.PROCESSING)
            + refunds.sumAmountByStatus(RefundStatus.FAILED);
        return new AdminPaymentSummary(grossPaidAmount, approvedRefundAmount,
            Math.subtractExact(grossPaidAmount, approvedRefundAmount), requestedRefundAmount);
    }

    @Transactional(readOnly = true)
    public AdminPaymentSummary paymentSummary(LocalDate from, LocalDate to) {
        if (from == null && to == null) return paymentSummary();
        if (from == null || to == null || from.isAfter(to) || to.equals(LocalDate.MAX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                "조회 시작일과 종료일을 올바르게 입력해 주세요.", List.of("from", "to"));
        }
        LocalDateTime fromUtc = from.atStartOfDay();
        LocalDateTime toUtc = to.plusDays(1).atStartOfDay();
        long grossPaidAmount = payments.sumPaidAmountByStatusInAndPaidAtRange(List.of(
            PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED), fromUtc, toUtc);
        long approvedRefundAmount = refunds.sumProcessedAmountByStatusAndRange(
            RefundStatus.REFUNDED, fromUtc, toUtc)
            + refunds.sumProcessedAmountByStatusAndRange(RefundStatus.APPROVED, fromUtc, toUtc);
        long requestedRefundAmount = refunds.sumCreatedAmountByStatusAndRange(
            RefundStatus.REQUESTED, fromUtc, toUtc)
            + refunds.sumCreatedAmountByStatusAndRange(RefundStatus.PROCESSING, fromUtc, toUtc)
            + refunds.sumCreatedAmountByStatusAndRange(RefundStatus.FAILED, fromUtc, toUtc);
        return new AdminPaymentSummary(grossPaidAmount, approvedRefundAmount,
            Math.subtractExact(grossPaidAmount, approvedRefundAmount), requestedRefundAmount);
    }

    @Transactional(readOnly = true)
    public List<AdminDailyPaymentSummary> paymentDaily(LocalDate from, LocalDate to) {
        validateDailyRange(from, to);

        LocalDateTime fromUtc = from.atStartOfDay();
        LocalDateTime toUtc = to.plusDays(1).atStartOfDay();
        Map<LocalDate, Long> paidByDay = sumByDay(payments.findPaidAmountsInRange(List.of(
            PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED), fromUtc, toUtc));
        Map<LocalDate, Long> refundedByDay = sumByDay(Stream.concat(
            refunds.findProcessedAmountsInRange(RefundStatus.REFUNDED, fromUtc, toUtc).stream(),
            refunds.findProcessedAmountsInRange(RefundStatus.APPROVED, fromUtc, toUtc).stream()).toList());
        Map<LocalDate, Long> requestedByDay = sumByDay(Stream.of(
            RefundStatus.REQUESTED, RefundStatus.PROCESSING, RefundStatus.FAILED)
            .flatMap(status -> refunds.findCreatedAmountsInRange(status, fromUtc, toUtc).stream()).toList());

        return from.datesUntil(to.plusDays(1)).map(date -> {
            long paid = paidByDay.getOrDefault(date, 0L);
            long refunded = refundedByDay.getOrDefault(date, 0L);
            return new AdminDailyPaymentSummary(date, paid, refunded,
                Math.subtractExact(paid, refunded), requestedByDay.getOrDefault(date, 0L));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDailyBookingSummary> bookingDaily(LocalDate from, LocalDate to) {
        validateDailyRange(from, to);
        LocalDateTime fromUtc = from.atStartOfDay();
        LocalDateTime toUtc = to.plusDays(1).atStartOfDay();
        Map<LocalDate, Long> createdByDay = countByDay(bookings.findCreatedTimesInRange(fromUtc, toUtc));
        Map<LocalDate, Long> approvedByDay = countByDay(bookings.findApprovedTimesInRange(fromUtc, toUtc));
        Map<LocalDate, Long> cancelledByDay = countByDay(bookings.findCancelledTimesInRange(fromUtc, toUtc));

        return from.datesUntil(to.plusDays(1)).map(date -> new AdminDailyBookingSummary(date,
            createdByDay.getOrDefault(date, 0L), approvedByDay.getOrDefault(date, 0L),
            cancelledByDay.getOrDefault(date, 0L))).toList();
    }

    private static void validateDailyRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || to.equals(LocalDate.MAX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                "조회 시작일과 종료일을 올바르게 입력해 주세요.", List.of("from", "to"));
        }
        if (ChronoUnit.DAYS.between(from, to) >= 31) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_TOO_LARGE",
                "일별 통계는 최대 31일까지 조회할 수 있습니다.", List.of("from", "to"));
        }
    }

    private static Map<LocalDate, Long> countByDay(List<LocalDateTime> times) {
        return times.stream().collect(Collectors.groupingBy(LocalDateTime::toLocalDate, Collectors.counting()));
    }

    private static Map<LocalDate, Long> sumByDay(List<TimedAmount> amounts) {
        return amounts.stream().collect(Collectors.toMap(
            amount -> amount.occurredAt().toLocalDate(), TimedAmount::amount, Math::addExact));
    }
}
