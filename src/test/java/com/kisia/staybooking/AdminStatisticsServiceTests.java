package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.qna.QnaAnswerEntity;
import com.kisia.staybooking.qna.QnaEntity;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.statistics.AdminOverview;
import com.kisia.staybooking.statistics.AdminStatisticsService;
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
}
