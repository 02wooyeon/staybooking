package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.AdminBookingSearchRequest;
import com.kisia.staybooking.booking.AdminBookingSearchService;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(AdminBookingSearchService.class)
class AdminBookingSearchServiceTests {
    @Autowired private EntityManager em;
    @Autowired private AdminBookingSearchService search;

    @Test
    void filtersAllBookingsAndIncludesPendingCancellationWithoutChangingLegacyList() {
        UserEntity owner = new UserEntity("searchowner", "hash", "Owner", "searchowner@example.test");
        UserEntity admin = new UserEntity("searchadmin", "hash", "Admin", null,
            "searchadmin@example.test", null, UserRole.ROLE_ADMIN);
        em.persist(owner);
        em.persist(admin);
        AccommodationEntity stay = new AccommodationEntity("Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        em.persist(stay);
        RoomEntity room = new RoomEntity(stay, "Standard", 100_000, 2, 1);
        em.persist(room);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        BookingEntity waitingForPayment = booking(owner, room, today.plusDays(5), now);
        BookingEntity pending = booking(owner, room, today.plusDays(7), now);
        BookingEntity approved = booking(owner, room, today.plusDays(9), now);
        em.persist(waitingForPayment);
        em.persist(pending);
        em.persist(approved);
        pending.markPaid(now);
        approved.markPaid(now);
        approved.accept(admin, now);
        em.persist(new CancellationRequestEntity(pending, owner,
            CancellationReasonType.USER_FAULT, "Change of plans"));
        em.flush();

        var firstPage = search.search(admin.getUserId(),
            new AdminBookingSearchRequest(null, null, 0, 2));
        assertEquals(3, firstPage.totalElements());
        assertEquals(2, firstPage.content().size());
        assertEquals(2, firstPage.totalPages());
        var secondPage = search.search(admin.getUserId(),
            new AdminBookingSearchRequest(null, null, 1, 2));
        assertEquals(1, secondPage.content().size());
        assertEquals(3, secondPage.totalElements());
        assertEquals(1, search.search(admin.getUserId(),
            new AdminBookingSearchRequest(waitingForPayment.getBookingId(), null, 0, 10))
            .totalElements());

        var pendingPage = search.search(admin.getUserId(),
            new AdminBookingSearchRequest(null, BookingStatus.PENDING, 0, 10));
        assertEquals(1, pendingPage.totalElements());
        assertEquals(pending.getBookingId(), pendingPage.content().getFirst().booking().bookingId());
        assertTrue(pendingPage.content().getFirst().booking().cancellationRequested());
        assertEquals("USER_FAULT", pendingPage.content().getFirst().cancellationRequest().reasonType());

        var cancellationQueue = search.search(admin.getUserId(),
            new AdminBookingSearchRequest(null, null, 0, 1, true));
        assertEquals(1, cancellationQueue.totalElements());
        assertEquals(1, cancellationQueue.totalPages());
        assertEquals(pending.getBookingId(), cancellationQueue.content().getFirst().booking().bookingId());
        assertEquals(0, search.search(admin.getUserId(),
            new AdminBookingSearchRequest(approved.getBookingId(), null, 0, 10, true)).totalElements());
        assertEquals(2, search.search(admin.getUserId(),
            new AdminBookingSearchRequest(null, null, 0, 10, false)).totalElements());
        assertEquals(0, search.search(admin.getUserId(),
            new AdminBookingSearchRequest(null, BookingStatus.CANCELLED, 0, 10, true)).totalElements());

        assertEquals(1, search.search(admin.getUserId(),
            new AdminBookingSearchRequest(approved.getBookingId(), BookingStatus.COMPLETED, 0, 10))
            .totalElements());
        assertEquals(0, search.search(admin.getUserId(),
            new AdminBookingSearchRequest(approved.getBookingId(), BookingStatus.CANCELLED, 0, 10))
            .totalElements());
        assertThrows(IllegalStateException.class, () -> search.search(owner.getUserId(),
            new AdminBookingSearchRequest(null, null, 0, 10)));
    }

    private static BookingEntity booking(UserEntity owner, RoomEntity room, LocalDate checkIn,
                                         LocalDateTime nowUtc) {
        return new BookingEntity(owner, room, checkIn, checkIn.plusDays(1), 1,
            100_000, 0, nowUtc.plusDays(1));
    }
}
