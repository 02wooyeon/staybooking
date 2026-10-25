package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.ApproverBookingQueryService;
import com.kisia.staybooking.booking.ApproverBookingResponse;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(ApproverBookingQueryService.class)
class ApproverBookingQueryServiceTests {
    @Autowired private EntityManager entityManager;
    @Autowired private ApproverBookingQueryService queries;

    @Test
    void approverListsPendingBookingsWithPendingCancellationRequest() {
        UserEntity customer = new UserEntity("customer", "hash", "Customer", "customer@example.test");
        UserEntity approver = new UserEntity("approver", "hash", "Approver", "approver@example.test");
        AccommodationEntity accommodation = new AccommodationEntity("Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Standard", 100_000, 2, 1);
        entityManager.persist(customer);
        entityManager.persist(approver);
        entityManager.persist(accommodation);
        entityManager.persist(room);
        BookingEntity first = booking(customer, room, 1);
        BookingEntity second = booking(customer, room, 3);
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.flush();
        entityManager.createNativeQuery("update users set role = 'ROLE_APPROVER' where user_id = :id")
            .setParameter("id", approver.getUserId()).executeUpdate();
        entityManager.createNativeQuery("update bookings set status = 'PENDING' where booking_id in (:firstId, :secondId)")
            .setParameter("firstId", first.getBookingId()).setParameter("secondId", second.getBookingId())
            .executeUpdate();
        entityManager.clear();

        BookingEntity requestedBooking = entityManager.find(BookingEntity.class, first.getBookingId());
        UserEntity requestedBy = entityManager.find(UserEntity.class, customer.getUserId());
        entityManager.persist(new CancellationRequestEntity(requestedBooking, requestedBy,
            CancellationReasonType.USER_FAULT, "Change of plans"));
        entityManager.flush();
        entityManager.clear();

        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        var results = queries.list(approver.getUserId(), BookingStatus.PENDING);
        assertEquals(2, results.size());
        assertNotNull(results.getFirst().cancellationRequest());
        assertEquals("USER_FAULT", results.getFirst().cancellationRequest().reasonType());
        assertNull(results.get(1).cancellationRequest());
        assertTrue(statistics.getPrepareStatementCount() <= 4,
            "승인자 목록은 권한, 예약·객실, 취소 요청, 환불을 각각 한 번씩 조회해야 합니다.");
    }

    @Test
    void customerCannotListApprovalQueue() {
        UserEntity customer = new UserEntity("customer", "hash", "Customer", "customer@example.test");
        entityManager.persist(customer);
        entityManager.flush();

        assertThrows(IllegalStateException.class, () -> queries.list(customer.getUserId(), BookingStatus.PENDING));
    }

    private static BookingEntity booking(UserEntity customer, RoomEntity room, int offset) {
        LocalDate checkIn = LocalDate.of(2026, 11, 1).plusDays(offset);
        return new BookingEntity(customer, room, checkIn, checkIn.plusDays(1), 1,
            100_000, 0, LocalDateTime.of(2026, 10, 21, 0, 0));
    }
}
