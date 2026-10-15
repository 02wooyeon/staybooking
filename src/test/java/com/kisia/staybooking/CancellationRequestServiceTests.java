package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.cancellation.CancellationDecisionService;
import com.kisia.staybooking.cancellation.CancellationRequestService;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({CancellationRequestService.class, CancellationDecisionService.class})
class CancellationRequestServiceTests {
    @Autowired private EntityManager entityManager;
    @Autowired private CancellationRequestService requests;
    @Autowired private CancellationDecisionService decisions;

    @Test
    void recordsOneOwnerRequestWithoutCancellingBooking() {
        UserEntity owner = new UserEntity("owner", "hash", "Owner", "owner@example.test");
        UserEntity stranger = new UserEntity("stranger", "hash", "Stranger", "stranger@example.test");
        AccommodationEntity accommodation = new AccommodationEntity(
            "Stay", "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Standard", 100_000, 2, 1);
        entityManager.persist(owner);
        entityManager.persist(stranger);
        entityManager.persist(accommodation);
        entityManager.persist(room);
        LocalDate checkIn = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(10);
        BookingEntity booking = new BookingEntity(owner, room, checkIn, checkIn.plusDays(1),
            1, 100_000, 0, LocalDateTime.now().plusMinutes(15));
        entityManager.persist(booking);
        entityManager.flush();

        // 결제 서비스 연결 전이므로 테스트 픽스처에서만 승인 대기 상태를 만든다.
        entityManager.createNativeQuery("update bookings set status = 'PENDING' where booking_id = :id")
            .setParameter("id", booking.getBookingId()).executeUpdate();
        entityManager.clear();

        assertThrows(IllegalStateException.class, () -> requests.request(
            stranger.getUserId(), booking.getBookingId(), CancellationReasonType.USER_FAULT, "Change of plans"));

        CancellationRequestEntity request = requests.request(owner.getUserId(), booking.getBookingId(),
            CancellationReasonType.USER_FAULT, "Change of plans");
        assertEquals(CancellationRequestStatus.REQUESTED, request.getStatus());
        assertEquals(BookingStatus.PENDING, request.getBooking().getStatus());
        assertThrows(IllegalStateException.class, () -> requests.request(owner.getUserId(),
            booking.getBookingId(), CancellationReasonType.USER_FAULT, "Again"));

        entityManager.createNativeQuery("update users set role = 'ROLE_APPROVER' where user_id = :id")
            .setParameter("id", stranger.getUserId()).executeUpdate();
        entityManager.clear();
        CancellationRequestEntity rejected = decisions.reject(stranger.getUserId(), booking.getBookingId(),
            request.getCancellationRequestId(), "Policy exception");
        assertEquals(CancellationRequestStatus.REJECTED, rejected.getStatus());
        assertEquals(stranger.getUserId(), rejected.getDecidedBy().getUserId());
        assertEquals(BookingStatus.PENDING, rejected.getBooking().getStatus());
    }
}
