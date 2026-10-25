package com.kisia.staybooking;

import java.time.LocalDate;

import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.payment.PaymentReconciliationService;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.TossPaymentConfirmationService;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestFixtures.class)
class TossPaymentCancellationRaceTests {
    @Autowired private TestFixtures fixtures;
    @Autowired private PaymentService paymentService;
    @Autowired private TossPaymentConfirmationService confirmations;
    @Autowired private BookingCancellationService cancellations;
    @Autowired private BookingRepository bookings;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentReconciliationService reconciliation;
    @MockitoBean private TossPaymentGateway gateway;

    @Test
    void cancellationCommittedDuringProviderApprovalCannotReopenTheBooking() {
        UserEntity guest = fixtures.user("결제 예약자");
        UserEntity admin = fixtures.user("취소 관리자", UserRole.ROLE_ADMIN);
        RoomEntity room = fixtures.room(100_000, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);
        var booking = fixtures.booking(guest, room, checkIn, checkIn.plusDays(1),
            BookingStatus.PAYMENT_PENDING);
        var prepared = paymentService.prepare(guest.getUserId(), booking.getBookingId(), "TOSS");
        String key = "RACE-KEY-" + System.nanoTime();
        when(gateway.confirm(eq(key), eq(prepared.getOrderId()), eq(100_000L)))
            .thenAnswer(invocation -> {
                cancellations.cancel(admin.getUserId(), booking.getBookingId(),
                    CancellationReasonType.ACCOMMODATION_FAULT, "운영자 취소");
                return new TossPaymentGateway.Confirmation(key, prepared.getOrderId(), 100_000, "DONE");
            });
        when(gateway.findByOrderId(prepared.getOrderId()))
            .thenReturn(new TossPaymentGateway.Confirmation(key, prepared.getOrderId(),
                100_000, "DONE", 100_000L));

        assertThrows(IllegalStateException.class, () -> confirmations.confirm(guest.getUserId(),
            prepared.getOrderId(), key, 100_000));

        assertEquals(BookingStatus.CANCELLED,
            bookings.findById(booking.getBookingId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.READY,
            payments.findById(prepared.getPaymentId()).orElseThrow().getStatus());
        assertEquals(PaymentReconciliationService.Result.REVIEW_REQUIRED,
            reconciliation.check(prepared.getPaymentId()).result());
    }
}
