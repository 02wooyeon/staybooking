package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingApprovalService;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import com.kisia.staybooking.room.RoomEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(BookingApprovalService.class)
class BookingApprovalServiceTests {
    @Autowired private EntityManager entityManager;
    @Autowired private BookingApprovalService approvals;

    @Test
    void rejectsUnpaidBookingAndRecordsApproverAfterVerifiedPayment() {
        UserEntity customer = new UserEntity("customer", "hash", "Customer", "customer@example.test");
        UserEntity approver = new UserEntity("approver", "hash", "Approver", "approver@example.test");
        AccommodationEntity accommodation = new AccommodationEntity(
            "Stay", "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Standard", 100_000, 2, 1);
        entityManager.persist(customer);
        entityManager.persist(approver);
        entityManager.persist(accommodation);
        entityManager.persist(room);
        BookingEntity booking = new BookingEntity(customer, room,
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
            1, 100_000, 0, LocalDateTime.of(2026, 10, 31, 12, 0));
        entityManager.persist(booking);
        PaymentEntity payment = new PaymentEntity(booking, "order-1", "CARD", 100_000);
        entityManager.persist(payment);
        entityManager.flush();

        // PG 연동이 아직 없으므로 테스트 픽스처에서만 결제 확인 결과를 반영한다.
        entityManager.createNativeQuery("update users set role = 'ROLE_APPROVER' where user_id = :id")
            .setParameter("id", approver.getUserId()).executeUpdate();
        entityManager.createNativeQuery("update bookings set status = 'PENDING' where booking_id = :id")
            .setParameter("id", booking.getBookingId()).executeUpdate();
        entityManager.clear();

        assertThrows(IllegalStateException.class,
            () -> approvals.accept(approver.getUserId(), booking.getBookingId()));

        entityManager.createNativeQuery("update payments set status = 'PAID', paid_at = CURRENT_TIMESTAMP where payment_id = :id")
            .setParameter("id", payment.getPaymentId()).executeUpdate();
        entityManager.clear();

        BookingEntity paidBooking = entityManager.find(BookingEntity.class, booking.getBookingId());
        UserEntity requester = entityManager.find(UserEntity.class, customer.getUserId());
        UserEntity activeApprover = entityManager.find(UserEntity.class, approver.getUserId());
        CancellationRequestEntity cancellation = new CancellationRequestEntity(paidBooking, requester,
            CancellationReasonType.USER_FAULT, "일정 변경");
        entityManager.persist(cancellation);
        entityManager.flush();

        IllegalStateException blocked = assertThrows(IllegalStateException.class,
            () -> approvals.accept(approver.getUserId(), booking.getBookingId()));
        assertTrue(blocked.getMessage().contains("Pending cancellation request"));
        assertEquals(BookingStatus.PENDING, paidBooking.getStatus());

        cancellation.reject(activeApprover, LocalDateTime.now(), "취소 신청 반려");
        entityManager.flush();

        BookingEntity approved = approvals.accept(approver.getUserId(), booking.getBookingId());
        assertEquals(BookingStatus.COMPLETED, approved.getStatus());
        assertEquals(approver.getUserId(), approved.getApprovedBy().getUserId());
        assertEquals(approver.getUserId(), approved.getCompletedBy().getUserId());
        assertEquals(approved.getApprovedAt(), approved.getCompletedAt());
    }
}
