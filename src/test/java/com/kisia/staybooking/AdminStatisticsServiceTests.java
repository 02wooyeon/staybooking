package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationStatus;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.booking.CancellationSource;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.qna.QnaAnswerEntity;
import com.kisia.staybooking.qna.QnaEntity;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.statistics.AdminOverview;
import com.kisia.staybooking.statistics.AdminPaymentSummary;
import com.kisia.staybooking.statistics.AdminDailyPaymentSummary;
import com.kisia.staybooking.statistics.AdminDailyBookingSummary;
import com.kisia.staybooking.statistics.AdminStatisticsService;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import(AdminStatisticsService.class)
class AdminStatisticsServiceTests {
    @Autowired private EntityManager em;
    @Autowired private AdminStatisticsService statistics;

    @Test
    void countsCurrentOperationalStatesWithoutTreatingRefundApprovalAsSettlement() {
        UserEntity admin = new UserEntity("adminstats", "hash", "Admin", null,
            "adminstats@example.test", null, UserRole.ROLE_ADMIN);
        em.persist(admin);
        AccommodationEntity stay = new AccommodationEntity("Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        em.persist(stay);
        RoomEntity room = new RoomEntity(stay, "Standard", 100_000, 2, 1);
        em.persist(room);
        AccommodationEntity inactiveStay = new AccommodationEntity("Inactive Stay", "Busan",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        inactiveStay.update("Inactive Stay", "Busan", LocalTime.of(15, 0), LocalTime.of(11, 0),
            null, AccommodationStatus.INACTIVE);
        em.persist(inactiveStay);
        em.persist(new RoomEntity(inactiveStay, "Still Active Room", 100_000, 2, 1));
        BookingEntity waitingForPayment = new BookingEntity(admin, room,
            LocalDate.now().plusDays(2), LocalDate.now().plusDays(3), 1, 100_000, 0,
            LocalDateTime.now().plusDays(1));
        BookingEntity waitingForApproval = new BookingEntity(admin, room,
            LocalDate.now().plusDays(4), LocalDate.now().plusDays(5), 1, 100_000, 0,
            LocalDateTime.now().plusDays(1));
        em.persist(waitingForPayment);
        em.persist(waitingForApproval);
        waitingForApproval.markPaid(LocalDateTime.now());
        PaymentEntity paid = new PaymentEntity(waitingForApproval, "stats-order", "CARD", 100_000);
        em.persist(paid);
        paid.complete("stats-payment-key", LocalDateTime.now());
        em.persist(new RefundEntity(waitingForApproval, 100_000, "Test"));
        QnaEntity unanswered = new QnaEntity(admin, null, "etc", "Question 1", "Question content", false);
        QnaEntity answered = new QnaEntity(admin, null, "etc", "Question 2", "Question content", false);
        em.persist(unanswered);
        em.persist(answered);
        em.persist(new QnaAnswerEntity(answered, admin, "Answer"));
        em.flush();

        AdminOverview result = statistics.overview();
        assertEquals(1, result.activeMembers());
        assertEquals(1, result.activeAccommodations());
        assertEquals(1, result.activeRooms());
        assertEquals(1, result.paymentPendingBookings());
        assertEquals(1, result.approvalPendingBookings());
        assertEquals(0, result.approvedBookings());
        assertEquals(1, result.paidPayments());
        assertEquals(1, result.requestedRefunds());
        assertEquals(1, result.unansweredQuestions());
    }

    @Test
    void paymentSummaryCountsOriginalPaidAmountsAndOnlyApprovedRefunds() {
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate todayUtc = nowUtc.toLocalDate();
        UserEntity admin = new UserEntity("paymentsummary", "hash", "Admin", null,
            "paymentsummary@example.test", null, UserRole.ROLE_ADMIN);
        em.persist(admin);
        AccommodationEntity stay = new AccommodationEntity("Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        em.persist(stay);
        RoomEntity room = new RoomEntity(stay, "Standard", 100_000, 2, 1);
        em.persist(room);

        long[] amounts = {100_000, 80_000, 50_000, 20_000, 30_000};
        BookingEntity[] bookings = new BookingEntity[amounts.length];
        PaymentEntity[] payments = new PaymentEntity[amounts.length];
        for (int i = 0; i < amounts.length; i++) {
            bookings[i] = new BookingEntity(admin, room,
                LocalDate.now().plusDays(2 + i * 2), LocalDate.now().plusDays(3 + i * 2),
                1, amounts[i], 0, LocalDateTime.now().plusDays(1));
            em.persist(bookings[i]);
            payments[i] = new PaymentEntity(bookings[i], "summary-order-" + i, "CARD", amounts[i]);
            em.persist(payments[i]);
            if (i != 3) payments[i].complete("summary-key-" + i,
                i == 4 ? nowUtc.minusDays(2) : nowUtc);
        }
        payments[1].refund(20_000);
        payments[2].refund(50_000);

        RefundEntity requested = new RefundEntity(bookings[0], 10_000, "Pending");
        RefundEntity partial = new RefundEntity(bookings[1], 20_000, "Partial");
        RefundEntity full = new RefundEntity(bookings[2], 50_000, "Full");
        RefundEntity rejected = new RefundEntity(bookings[4], 5_000, "Rejected");
        em.persist(requested);
        em.persist(partial);
        em.persist(full);
        em.persist(rejected);
        partial.start(nowUtc.minusDays(2));
        partial.refunded(nowUtc.minusDays(2));
        full.start(nowUtc);
        full.refunded(nowUtc);
        rejected.process(admin, RefundStatus.REJECTED, nowUtc);
        em.flush();

        AdminPaymentSummary result = statistics.paymentSummary();
        assertEquals(260_000, result.grossPaidAmount());
        assertEquals(70_000, result.approvedRefundAmount());
        assertEquals(190_000, result.netPaidAmount());
        assertEquals(10_000, result.requestedRefundAmount());

        AdminPaymentSummary today = statistics.paymentSummary(todayUtc, todayUtc);
        assertEquals(230_000, today.grossPaidAmount());
        assertEquals(50_000, today.approvedRefundAmount());
        assertEquals(180_000, today.netPaidAmount());
        assertEquals(10_000, today.requestedRefundAmount());

        AdminPaymentSummary earlier = statistics.paymentSummary(todayUtc.minusDays(2), todayUtc.minusDays(2));
        assertEquals(30_000, earlier.grossPaidAmount());
        assertEquals(20_000, earlier.approvedRefundAmount());
        assertEquals(10_000, earlier.netPaidAmount());
        assertEquals(0, earlier.requestedRefundAmount());

        AdminPaymentSummary empty = statistics.paymentSummary(todayUtc.plusDays(1), todayUtc.plusDays(1));
        assertEquals(0, empty.grossPaidAmount());
        assertEquals(0, empty.approvedRefundAmount());
        assertEquals(0, empty.netPaidAmount());
        assertEquals(0, empty.requestedRefundAmount());

        var daily = statistics.paymentDaily(todayUtc.minusDays(2), todayUtc);
        assertEquals(3, daily.size());
        assertEquals(new AdminDailyPaymentSummary(todayUtc.minusDays(2), 30_000, 20_000, 10_000, 0),
            daily.get(0));
        assertEquals(new AdminDailyPaymentSummary(todayUtc.minusDays(1), 0, 0, 0, 0), daily.get(1));
        assertEquals(new AdminDailyPaymentSummary(todayUtc, 230_000, 50_000, 180_000, 10_000),
            daily.get(2));
    }

    @Test
    void bookingDailyCountsApprovalEvenWhenBookingIsLaterCancelled() {
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate todayUtc = nowUtc.toLocalDate();
        UserEntity admin = new UserEntity("bookingdaily", "hash", "Admin", null,
            "bookingdaily@example.test", null, UserRole.ROLE_ADMIN);
        em.persist(admin);
        AccommodationEntity stay = new AccommodationEntity("Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        em.persist(stay);
        RoomEntity room = new RoomEntity(stay, "Standard", 100_000, 2, 1);
        em.persist(room);

        BookingEntity cancelled = new BookingEntity(admin, room, todayUtc.plusDays(2),
            todayUtc.plusDays(3), 1, 100_000, 0, nowUtc.plusDays(1));
        BookingEntity approved = new BookingEntity(admin, room, todayUtc.plusDays(4),
            todayUtc.plusDays(5), 1, 100_000, 0, nowUtc.plusDays(1));
        BookingEntity pending = new BookingEntity(admin, room, todayUtc.plusDays(6),
            todayUtc.plusDays(7), 1, 100_000, 0, nowUtc.plusDays(1));
        em.persist(cancelled);
        em.persist(approved);
        em.persist(pending);
        cancelled.markPaid(nowUtc);
        cancelled.accept(admin, nowUtc);
        cancelled.cancel(admin, CancellationSource.ADMIN_DIRECT, CancellationReasonType.USER_FAULT,
            "Test", nowUtc);
        approved.markPaid(nowUtc);
        approved.accept(admin, nowUtc);
        em.flush();

        var daily = statistics.bookingDaily(todayUtc.minusDays(1), todayUtc.plusDays(1));
        assertEquals(3, daily.size());
        assertEquals(new AdminDailyBookingSummary(todayUtc.minusDays(1), 0, 0, 0), daily.get(0));
        assertEquals(new AdminDailyBookingSummary(todayUtc, 3, 2, 1), daily.get(1));
        assertEquals(new AdminDailyBookingSummary(todayUtc.plusDays(1), 0, 0, 0), daily.get(2));
    }
}
