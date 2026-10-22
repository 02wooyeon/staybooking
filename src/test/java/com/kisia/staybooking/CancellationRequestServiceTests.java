package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingQueryService;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.cancellation.CancellationDecisionService;
import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.cancellation.CancellationRequestService;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.refund.RefundRequestService;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({CancellationRequestService.class, CancellationDecisionService.class,
    BookingCancellationService.class, BookingQueryService.class, RefundRequestService.class})
class CancellationRequestServiceTests {
    @Autowired private EntityManager entityManager;
    @Autowired private CancellationRequestService requests;
    @Autowired private CancellationDecisionService decisions;
    @Autowired private BookingCancellationService directCancellations;
    @Autowired private BookingQueryService bookingQueries;
    @Autowired private RefundRepository refunds;

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
        assertEquals(true, bookingQueries.mine(owner.getUserId()).getFirst().cancellationRequested());
        assertEquals(true, bookingQueries.mineDetail(owner.getUserId(), booking.getBookingId()).cancellationRequested());
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
        assertEquals(false, bookingQueries.mine(owner.getUserId()).getFirst().cancellationRequested());
    }

    @Test
    void approvalCancelsRequestedBookingAndAdministratorCanCancelDirectly() {
        UserEntity owner = new UserEntity("owner2", "hash", "Owner", "owner2@example.test");
        UserEntity approver = new UserEntity("approver2", "hash", "Approver", "approver2@example.test");
        UserEntity admin = new UserEntity("admin2", "hash", "Admin", "admin2@example.test");
        AccommodationEntity accommodation = new AccommodationEntity(
            "Stay", "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Standard", 100_000, 2, 1);
        entityManager.persist(owner);
        entityManager.persist(approver);
        entityManager.persist(admin);
        entityManager.persist(accommodation);
        entityManager.persist(room);
        LocalDate checkIn = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(10);
        BookingEntity requestedBooking = new BookingEntity(owner, room, checkIn, checkIn.plusDays(1),
            1, 100_000, 0, LocalDateTime.now().plusMinutes(15));
        BookingEntity directBooking = new BookingEntity(owner, room, checkIn.plusDays(2), checkIn.plusDays(3),
            1, 100_000, 0, LocalDateTime.now().plusMinutes(15));
        entityManager.persist(requestedBooking);
        entityManager.persist(directBooking);
        PaymentEntity requestedPayment = new PaymentEntity(requestedBooking, "ORDER-requested", "DEMO", 100_000);
        requestedPayment.complete("DEMO-requested", LocalDateTime.now());
        PaymentEntity directPayment = new PaymentEntity(directBooking, "ORDER-direct", "DEMO", 100_000);
        directPayment.complete("DEMO-direct", LocalDateTime.now());
        entityManager.persist(requestedPayment);
        entityManager.persist(directPayment);
        entityManager.flush();
        entityManager.createNativeQuery("update users set role = 'ROLE_APPROVER' where user_id = :approverId")
            .setParameter("approverId", approver.getUserId()).executeUpdate();
        entityManager.createNativeQuery("update users set role = 'ROLE_ADMIN' where user_id = :adminId")
            .setParameter("adminId", admin.getUserId()).executeUpdate();
        entityManager.createNativeQuery("update bookings set status = 'PENDING' where booking_id in (:firstId, :secondId)")
            .setParameter("firstId", requestedBooking.getBookingId())
            .setParameter("secondId", directBooking.getBookingId()).executeUpdate();
        entityManager.clear();

        CancellationRequestEntity request = requests.request(owner.getUserId(), requestedBooking.getBookingId(),
            CancellationReasonType.USER_FAULT, "Change of plans");
        CancellationRequestEntity approved = decisions.approve(approver.getUserId(), requestedBooking.getBookingId(),
            request.getCancellationRequestId(), "Approved");
        assertEquals(CancellationRequestStatus.APPROVED, approved.getStatus());
        assertEquals(BookingStatus.CANCELLED, approved.getBooking().getStatus());
        assertEquals("USER_REQUEST", approved.getBooking().getCancelSource().name());

        BookingEntity directlyCancelled = directCancellations.cancel(admin.getUserId(), directBooking.getBookingId(),
            CancellationReasonType.ACCOMMODATION_FAULT, "Operational issue");
        assertEquals(BookingStatus.CANCELLED, directlyCancelled.getStatus());
        assertEquals("ADMIN_DIRECT", directlyCancelled.getCancelSource().name());
        assertEquals(admin.getUserId(), directlyCancelled.getCancelledBy().getUserId());
        assertEquals(2, refunds.count());
    }
}
