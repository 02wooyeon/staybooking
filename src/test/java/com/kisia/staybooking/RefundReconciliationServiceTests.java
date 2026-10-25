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
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundReconciliationService;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundReconciliationServiceTests {
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final TossPaymentGateway gateway = mock(TossPaymentGateway.class);
    private final RefundReconciliationService service =
        new RefundReconciliationService(refunds, payments, gateway);

    @Test
    void showsProviderBalanceWhenCancellationMayHaveSucceededButLocalRefundFailed() {
        Fixture fixture = setup("TOSS", 40_000);
        fixture.refund().start(LocalDateTime.now());
        fixture.refund().failed("PAYMENT_CANCEL_FAILED");
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100",
                100_000, "PARTIAL_CANCELED", 60_000L));

        var report = service.check(9L);

        assertEquals(RefundReconciliationService.Result.PROVIDER_BALANCE_MATCHES, report.result());
        assertEquals(60_000L, report.providerBalanceAmount());
        assertEquals(PaymentStatus.PAID, fixture.payment().getStatus());
        assertEquals(com.kisia.staybooking.refund.RefundStatus.FAILED, fixture.refund().getStatus());
    }

    @Test
    void distinguishesNoRecordedCancellationFromAProviderMismatch() {
        setup("TOSS", 40_000);
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100",
                100_000, "DONE", 100_000L))
            .thenReturn(new TossPaymentGateway.Confirmation("other-key", "ORDER-100",
                100_000, "PARTIAL_CANCELED", 60_000L));

        assertEquals(RefundReconciliationService.Result.NO_CANCELLATION_RECORDED,
            service.check(9L).result());
        assertEquals(RefundReconciliationService.Result.REVIEW_REQUIRED,
            service.check(9L).result());
    }

    @Test
    void exposesLookupFailureWithoutChangingTheRefund() {
        RefundEntity refund = setup("TOSS", 40_000).refund();
        when(gateway.findByOrderId("ORDER-100"))
            .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_LOOKUP_FAILED", "조회 실패"));

        assertEquals(RefundReconciliationService.Result.LOOKUP_FAILED, service.check(9L).result());
        assertEquals(com.kisia.staybooking.refund.RefundStatus.REQUESTED, refund.getStatus());
    }

    @Test
    void skipsTheProviderForDemoPaymentsAndFlagsAmbiguousLocalPayments() {
        setup("DEMO", 40_000);
        assertEquals(RefundReconciliationService.Result.NOT_APPLICABLE, service.check(9L).result());
        verifyNoInteractions(gateway);

        when(payments.findByBookingBookingIdAndStatusIn(eq(3L), anyList())).thenReturn(List.of());
        assertEquals(RefundReconciliationService.Result.REVIEW_REQUIRED, service.check(9L).result());
    }

    private Fixture setup(String method, long refundAmount) {
        UserEntity guest = new UserEntity("guest", "hash", "Guest", "guest@example.test");
        AccommodationEntity stay = new AccommodationEntity("Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(stay, "Standard", 100_000, 2, 1);
        BookingEntity booking = new BookingEntity(guest, room, LocalDate.now().plusDays(2),
            LocalDate.now().plusDays(3), 1, 100_000, 0, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(booking, "bookingId", 3L);
        RefundEntity refund = new RefundEntity(booking, refundAmount, "예약 취소");
        ReflectionTestUtils.setField(refund, "refundId", 9L);
        PaymentEntity payment = new PaymentEntity(booking, "ORDER-100", method, 100_000);
        payment.complete("toss-key", LocalDateTime.now());
        ReflectionTestUtils.setField(payment, "paymentId", 7L);
        when(refunds.findById(9L)).thenReturn(Optional.of(refund));
        when(payments.findByBookingBookingIdAndStatusIn(eq(3L), anyList()))
            .thenReturn(List.of(payment));
        return new Fixture(refund, payment);
    }

    private record Fixture(RefundEntity refund, PaymentEntity payment) { }
}
