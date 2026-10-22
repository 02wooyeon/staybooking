package com.kisia.staybooking.statistics;

import java.util.List;

import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.accommodation.AccommodationStatus;
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
}
