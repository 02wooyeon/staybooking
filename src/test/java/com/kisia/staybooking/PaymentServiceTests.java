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
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.TossPaymentConfirmationService;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(PaymentService.class)
class PaymentServiceTests {
    @Autowired private EntityManager entityManager;
    @Autowired private PaymentService payments;
    @Autowired private PaymentRepository paymentRepository;

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

    @Test
    void tossConfirmationChecksStoredAmountAndProviderResponseBeforeMarkingPaid() {
        UserEntity owner = new UserEntity("toss-owner", "hash", "Owner", "toss-owner@example.test");
        AccommodationEntity accommodation = new AccommodationEntity(
            "Stay", "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Standard", 120_000, 2, 1);
        entityManager.persist(owner);
        entityManager.persist(accommodation);
        entityManager.persist(room);
        BookingEntity booking = new BookingEntity(owner, room, LocalDate.now().plusDays(3),
            LocalDate.now().plusDays(4), 2, 120_000, 0, LocalDateTime.now().plusMinutes(10));
        entityManager.persist(booking);
        entityManager.flush();
        PaymentEntity prepared = payments.prepare(owner.getUserId(), booking.getBookingId(), "TOSS");
        TossPaymentGateway gateway = mock(TossPaymentGateway.class);
        TossPaymentConfirmationService confirmations = new TossPaymentConfirmationService(
            paymentRepository, payments, gateway);

        assertThrows(ApiException.class, () -> confirmations.confirm(owner.getUserId(),
            prepared.getOrderId(), "payment-key", 1));
        verifyNoInteractions(gateway);

        when(gateway.confirm("payment-key", prepared.getOrderId(), 120_000))
            .thenReturn(new TossPaymentGateway.Confirmation("payment-key", prepared.getOrderId(), 1, "DONE"))
            .thenReturn(new TossPaymentGateway.Confirmation("payment-key", prepared.getOrderId(), 120_000, "DONE"));
        assertThrows(ApiException.class, () -> confirmations.confirm(owner.getUserId(),
            prepared.getOrderId(), "payment-key", 120_000));
        assertEquals(PaymentStatus.READY, prepared.getStatus());
        assertEquals(BookingStatus.PAYMENT_PENDING, booking.getStatus());

        PaymentEntity paid = confirmations.confirm(owner.getUserId(), prepared.getOrderId(),
            "payment-key", 120_000);
        assertEquals(PaymentStatus.PAID, paid.getStatus());
        assertEquals(BookingStatus.PENDING, booking.getStatus());
    }
}
