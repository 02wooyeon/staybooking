package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.PaymentStatus;
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
@Import(PaymentService.class)
class PaymentServiceTests {
    @Autowired private EntityManager entityManager;
    @Autowired private PaymentService payments;

    @Test
    void completesOnlyOneMatchingPaymentAndMakesBookingApprovable() {
        UserEntity user = new UserEntity("guest", "hash", "Guest", "guest@example.test");
        AccommodationEntity accommodation = new AccommodationEntity(
            "Stay", "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Standard", 120_000, 2, 1);
        entityManager.persist(user);
        entityManager.persist(accommodation);
        entityManager.persist(room);
        BookingEntity booking = new BookingEntity(user, room, LocalDate.now().plusDays(3),
            LocalDate.now().plusDays(4), 2, 120_000, 0, LocalDateTime.now().plusMinutes(10));
        entityManager.persist(booking);
        entityManager.flush();

        PaymentEntity prepared = payments.prepare(user.getUserId(), booking.getBookingId(), "CARD");
        PaymentEntity completed = payments.complete(user.getUserId(), prepared.getOrderId(), "payment-key-001",
            120_000);
        entityManager.flush();

        assertEquals(PaymentStatus.PAID, completed.getStatus());
        assertEquals(BookingStatus.PENDING, completed.getBooking().getStatus());
        assertThrows(IllegalStateException.class,
            () -> payments.complete(user.getUserId(), prepared.getOrderId(), "payment-key-002", 120_000));
    }

    @Test
    void demoPaymentStillRequiresOwnerAndTransitionsBookingOnlyOnce() {
        UserEntity owner = new UserEntity("demo-owner", "hash", "Owner", "demo-owner@example.test");
        UserEntity other = new UserEntity("demo-other", "hash", "Other", "demo-other@example.test");
        AccommodationEntity accommodation = new AccommodationEntity(
            "Stay", "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Standard", 120_000, 2, 1);
        entityManager.persist(owner);
        entityManager.persist(other);
        entityManager.persist(accommodation);
        entityManager.persist(room);
        BookingEntity booking = new BookingEntity(owner, room, LocalDate.now().plusDays(3),
            LocalDate.now().plusDays(4), 2, 120_000, 0, LocalDateTime.now().plusMinutes(10));
        entityManager.persist(booking);
        entityManager.flush();

        assertThrows(IllegalArgumentException.class,
            () -> payments.completeDemo(other.getUserId(), booking.getBookingId()));
        PaymentEntity completed = payments.completeDemo(owner.getUserId(), booking.getBookingId());
        assertEquals(PaymentStatus.PAID, completed.getStatus());
        assertEquals(BookingStatus.PENDING, completed.getBooking().getStatus());
        assertThrows(IllegalStateException.class,
            () -> payments.completeDemo(owner.getUserId(), booking.getBookingId()));
    }
}
