package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class CoreEntityMappingTests {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsBookingWithSemanticForeignKeysAndRoomQuantity() {
        UserEntity user = new UserEntity("guest01", "hashed-password", "Guest", "guest@example.test");
        AccommodationEntity accommodation = new AccommodationEntity(
            "Stay A", "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Deluxe", 100_000, 2, 3);
        BookingEntity booking = new BookingEntity(user, room,
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3),
            2, 200_000, 0, LocalDateTime.of(2026, 10, 31, 12, 0));

        entityManager.persist(user);
        entityManager.persist(accommodation);
        entityManager.persist(room);
        entityManager.persist(booking);
        entityManager.flush();

        assertNotNull(booking.getBookingId());
        assertEquals(UserRole.ROLE_USER, user.getRole());
        assertEquals(BookingStatus.PAYMENT_PENDING, booking.getStatus());
        assertEquals(3, jdbc.queryForObject(
            "SELECT total_quantity FROM rooms WHERE room_id = ?", Integer.class, room.getRoomId()));
        assertEquals(user.getUserId(), jdbc.queryForObject(
            "SELECT user_id FROM bookings WHERE booking_id = ?", Long.class, booking.getBookingId()));
        assertEquals(room.getRoomId(), jdbc.queryForObject(
            "SELECT room_id FROM bookings WHERE booking_id = ?", Long.class, booking.getBookingId()));
        assertEquals("PAYMENT_PENDING", jdbc.queryForObject(
            "SELECT status FROM bookings WHERE booking_id = ?", String.class, booking.getBookingId()));
    }
}
