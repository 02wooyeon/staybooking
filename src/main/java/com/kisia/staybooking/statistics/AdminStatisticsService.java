package com.kisia.staybooking.statistics;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
            rooms.countByStatus(RoomStatus.ACTIVE),
            bookings.countByStatus(BookingStatus.PAYMENT_PENDING),
            bookings.countByStatus(BookingStatus.PENDING),
            bookings.countByStatus(BookingStatus.COMPLETED),
            bookings.countByStatus(BookingStatus.CANCELLED),
            payments.countByStatus(PaymentStatus.PAID),
            refunds.countByStatus(RefundStatus.REQUESTED),
            questions.countVisibleUnanswered()
        );
    }

    @Transactional(readOnly = true)
    public AdminPaymentSummary paymentSummary() {
        long grossPaidAmount = payments.sumPaidAmountByStatusIn(List.of(
            PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
        long approvedRefundAmount = refunds.sumAmountByStatus(RefundStatus.APPROVED);
        long requestedRefundAmount = refunds.sumAmountByStatus(RefundStatus.REQUESTED);
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
            RefundStatus.APPROVED, fromUtc, toUtc);
        long requestedRefundAmount = refunds.sumCreatedAmountByStatusAndRange(
            RefundStatus.REQUESTED, fromUtc, toUtc);
        return new AdminPaymentSummary(grossPaidAmount, approvedRefundAmount,
            Math.subtractExact(grossPaidAmount, approvedRefundAmount), requestedRefundAmount);
    }
}
