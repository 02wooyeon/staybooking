package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.refund.AdminRefundService;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminRefundServiceTests {
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TossPaymentGateway gateway = mock(TossPaymentGateway.class);
    private final AdminRefundService service = new AdminRefundService(refunds, payments, users, gateway);

    @Test
    void partialRefundChangesLocalStateOnlyAfterMatchingTossCancellation() {
        Fixture fixture = fixture(50_000);
        when(gateway.cancel("toss-key", "Customer cancellation", 50_000, 17L))
            .thenReturn(new TossPaymentGateway.Cancellation("toss-key", "ORDER-100", 100_000,
                50_000, "PARTIAL_CANCELED", List.of(new TossPaymentGateway.Cancel(50_000))));

        RefundEntity processed = service.process(2L, 17L, RefundStatus.APPROVED);

        assertEquals(RefundStatus.APPROVED, processed.getStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, fixture.payment().getStatus());
        verify(gateway).cancel("toss-key", "Customer cancellation", 50_000, 17L);
    }

    @Test
    void mismatchedTossResponseLeavesRefundAndPaymentUntouched() {
        Fixture fixture = fixture(100_000);
        when(gateway.cancel("toss-key", "Customer cancellation", 100_000, 17L))
            .thenReturn(new TossPaymentGateway.Cancellation("other-key", "ORDER-100", 100_000,
                0, "CANCELED", List.of(new TossPaymentGateway.Cancel(100_000))));

        assertThrows(ApiException.class, () -> service.process(2L, 17L, RefundStatus.APPROVED));
        assertEquals(RefundStatus.REQUESTED, fixture.refund().getStatus());
        assertEquals(PaymentStatus.PAID, fixture.payment().getStatus());
    }

    private Fixture fixture(long refundAmount) {
        UserEntity owner = new UserEntity("owner", "hash", "Owner", "owner@example.test");
        UserEntity admin = new UserEntity("admin", "hash", "Admin", null,
            "admin@example.test", null, UserRole.ROLE_ADMIN);
        AccommodationEntity accommodation = new AccommodationEntity("Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Standard", 100_000, 2, 1);
        BookingEntity booking = new BookingEntity(owner, room, LocalDate.now().plusDays(5),
            LocalDate.now().plusDays(6), 1, 100_000, 0, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(booking, "bookingId", 3L);
        PaymentEntity payment = new PaymentEntity(booking, "ORDER-100", "TOSS", 100_000);
        payment.complete("toss-key", LocalDateTime.now());
        RefundEntity refund = new RefundEntity(booking, refundAmount, "Customer cancellation");
        ReflectionTestUtils.setField(refund, "refundId", 17L);
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(refunds.findByIdForUpdate(17L)).thenReturn(Optional.of(refund));
        when(payments.findByBookingBookingIdAndStatus(3L, PaymentStatus.PAID))
            .thenReturn(List.of(payment));
        return new Fixture(refund, payment);
    }

    private record Fixture(RefundEntity refund, PaymentEntity payment) { }
}
