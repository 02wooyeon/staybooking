package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.accommodation.AccommodationStatus;
import com.kisia.staybooking.accommodation.AdminAccommodationController;
import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.room.RoomStatus;
import com.kisia.staybooking.user.AdminMemberController;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AdminMutationPersistenceTests {
    @Autowired private AdminMemberController members;
    @Autowired private AdminAccommodationController accommodationsAdmin;
    @Autowired private UserRepository users;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RefundRepository refunds;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository payments;
    @Autowired private BookingCancellationService cancellations;

    @Test
    void createdAccommodationAndRoomKeepRequestedDescriptionAndStatus() {
        String tag = Long.toString(System.nanoTime());
        AccommodationEntity stay = accommodationsAdmin.create(
            new AdminAccommodationController.AccommodationRequest("New stay " + tag, "Busan",
                LocalTime.of(15, 0), LocalTime.of(11, 0), "Opening soon", AccommodationStatus.INACTIVE));
        RoomEntity room = accommodationsAdmin.createRoom(stay.getAccommodationId(),
            new AdminAccommodationController.RoomRequest("Suite", 180_000, 3, 2,
                "Under preparation", RoomStatus.INACTIVE));

        AccommodationEntity savedStay = accommodations.findById(stay.getAccommodationId()).orElseThrow();
        RoomEntity savedRoom = rooms.findById(room.getRoomId()).orElseThrow();
        assertEquals("Opening soon", savedStay.getDescription());
        assertEquals(AccommodationStatus.INACTIVE, savedStay.getStatus());
        assertEquals("Under preparation", savedRoom.getDescription());
        assertEquals(RoomStatus.INACTIVE, savedRoom.getStatus());
    }

    @Test
    void deactivatingAccommodationAndRoomKeepsExistingRecords() {
        String tag = Long.toString(System.nanoTime());
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Stay " + tag, "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 1));
        AccommodationEntity otherStay = accommodations.save(new AccommodationEntity("Other " + tag, "Busan",
            LocalTime.of(15, 0), LocalTime.of(11, 0)));

        ApiException wrongAccommodation = assertThrows(ApiException.class,
            () -> accommodationsAdmin.deactivateRoom(otherStay.getAccommodationId(), room.getRoomId()));
        assertEquals("NOT_FOUND", wrongAccommodation.getCode());
        assertEquals(RoomStatus.ACTIVE, rooms.findById(room.getRoomId()).orElseThrow().getStatus());

        accommodationsAdmin.deactivateRoom(stay.getAccommodationId(), room.getRoomId());
        accommodationsAdmin.deactivate(stay.getAccommodationId());

        assertEquals(RoomStatus.INACTIVE, rooms.findById(room.getRoomId()).orElseThrow().getStatus());
        assertEquals(AccommodationStatus.INACTIVE,
            accommodations.findById(stay.getAccommodationId()).orElseThrow().getStatus());
    }

    @Test
    void memberAndInventoryUpdatesSurviveReload() {
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("admin" + tag, "hash", "Admin", null,
            "admin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity member = users.save(new UserEntity("member" + tag, "hash", "Member",
            "member" + tag + "@example.test"));
        members.changeStatus(new AuthenticatedUser(admin.getUserId(), UserRole.ROLE_ADMIN),
            member.getUserId(), new AdminMemberController.StatusRequest(UserStatus.SUSPENDED));
        assertEquals(UserStatus.SUSPENDED, users.findById(member.getUserId()).orElseThrow().getStatus());

        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Old " + tag, "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 1));
        accommodationsAdmin.update(stay.getAccommodationId(),
            new AdminAccommodationController.AccommodationRequest("New " + tag, "Busan",
                LocalTime.of(16, 0), LocalTime.of(12, 0), "Updated", AccommodationStatus.INACTIVE));
        accommodationsAdmin.updateRoom(stay.getAccommodationId(), room.getRoomId(),
            new AdminAccommodationController.RoomRequest("Deluxe", 130_000, 3, 2,
                "Updated room", RoomStatus.INACTIVE));
        assertEquals("New " + tag, accommodations.findById(stay.getAccommodationId()).orElseThrow().getName());
        RoomEntity saved = rooms.findById(room.getRoomId()).orElseThrow();
        assertEquals("Deluxe", saved.getName());
        assertEquals(2, saved.getTotalQuantity());
        assertEquals(RoomStatus.INACTIVE, saved.getStatus());
    }

    @Test
    void roomQuantityCannotFallBelowPeakOccupiedUnits() {
        String tag = Long.toString(System.nanoTime());
        UserEntity guest = users.save(new UserEntity("occupancy" + tag, "hash", "Guest",
            "occupancy" + tag + "@example.test"));
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Occupied " + tag, "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 3));
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        BookingEntity first = bookings.save(new BookingEntity(guest, room, day, day.plusDays(2), 1,
            200_000, 0, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15)));
        BookingEntity second = bookings.save(new BookingEntity(guest, room, day.plusDays(1), day.plusDays(3), 1,
            200_000, 0, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15)));
        jdbc.update("update bookings set status = 'PENDING' where booking_id in (?, ?)",
            first.getBookingId(), second.getBookingId());

        var tooSmall = new AdminAccommodationController.RoomRequest("Standard", 100_000, 2, 1,
            null, RoomStatus.ACTIVE);
        ApiException conflict = assertThrows(ApiException.class,
            () -> accommodationsAdmin.updateRoom(stay.getAccommodationId(), room.getRoomId(), tooSmall));
        assertEquals("ROOM_QUANTITY_BELOW_BOOKINGS", conflict.getCode());
        assertEquals(3, rooms.findById(room.getRoomId()).orElseThrow().getTotalQuantity());

        var enough = new AdminAccommodationController.RoomRequest("Standard", 100_000, 2, 2,
            null, RoomStatus.ACTIVE);
        accommodationsAdmin.updateRoom(stay.getAccommodationId(), room.getRoomId(), enough);
        assertEquals(2, rooms.findById(room.getRoomId()).orElseThrow().getTotalQuantity());
    }

    @Test
    void roomCapacityCannotFallBelowFutureBookingGuests() {
        String tag = Long.toString(System.nanoTime());
        UserEntity guest = users.save(new UserEntity("capacity" + tag, "hash", "Guest",
            "capacity" + tag + "@example.test"));
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Capacity stay " + tag, "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Family", 150_000, 4, 2));
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        BookingEntity booking = bookings.save(new BookingEntity(guest, room, day, day.plusDays(1), 3,
            150_000, 0, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15)));
        jdbc.update("update bookings set status = 'PENDING' where booking_id = ?", booking.getBookingId());

        var tooSmall = new AdminAccommodationController.RoomRequest("Family", 150_000, 2, 2,
            null, RoomStatus.ACTIVE);
        ApiException conflict = assertThrows(ApiException.class,
            () -> accommodationsAdmin.updateRoom(stay.getAccommodationId(), room.getRoomId(), tooSmall));
        assertEquals("ROOM_CAPACITY_BELOW_BOOKINGS", conflict.getCode());
        assertEquals(4, rooms.findById(room.getRoomId()).orElseThrow().getCapacity());

        var enough = new AdminAccommodationController.RoomRequest("Family", 150_000, 3, 2,
            null, RoomStatus.ACTIVE);
        accommodationsAdmin.updateRoom(stay.getAccommodationId(), room.getRoomId(), enough);
        assertEquals(3, rooms.findById(room.getRoomId()).orElseThrow().getCapacity());
    }

    @Test
    void expiredOrCancelledBookingsDoNotBlockRoomCapacityReduction() {
        String tag = Long.toString(System.nanoTime());
        UserEntity guest = users.save(new UserEntity("released" + tag, "hash", "Guest",
            "released" + tag + "@example.test"));
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Released stay " + tag, "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Family", 150_000, 4, 2));
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        bookings.save(new BookingEntity(guest, room, day, day.plusDays(1), 4,
            150_000, 0, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        BookingEntity cancelled = bookings.save(new BookingEntity(guest, room, day, day.plusDays(1), 4,
            150_000, 0, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15)));
        jdbc.update("update bookings set status = 'CANCELLED' where booking_id = ?", cancelled.getBookingId());

        accommodationsAdmin.updateRoom(stay.getAccommodationId(), room.getRoomId(),
            new AdminAccommodationController.RoomRequest("Family", 150_000, 2, 2,
                null, RoomStatus.ACTIVE));
        assertEquals(2, rooms.findById(room.getRoomId()).orElseThrow().getCapacity());
    }

    @Test
    void refundAutomaticallySettlesAfterBookingCancellation() {
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("refundadmin" + tag, "hash", "Admin", null,
            "refundadmin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity guest = users.save(new UserEntity("refundguest" + tag, "hash", "Guest",
            "refundguest" + tag + "@example.test"));
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Refund stay " + tag,
            "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 1));
        BookingEntity booking = bookings.save(new BookingEntity(guest, room,
            LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1, 100_000, 0,
            LocalDateTime.now().plusHours(1)));
        paymentService.completeDemo(guest.getUserId(), booking.getBookingId());
        cancellations.cancel(admin.getUserId(), booking.getBookingId(),
            CancellationReasonType.ACCOMMODATION_FAULT, "Test refund");
        RefundEntity refund = refunds.findAllByOrderByCreatedAtDesc().getFirst();
        RefundEntity saved = refunds.findById(refund.getRefundId()).orElseThrow();
        assertEquals(RefundStatus.REFUNDED, saved.getStatus());
        assertNotNull(saved.getProcessedAt());
        assertEquals(PaymentStatus.REFUNDED, payments.findByBookingBookingIdAndStatus(
            booking.getBookingId(), PaymentStatus.REFUNDED).getFirst().getStatus());
    }
}
