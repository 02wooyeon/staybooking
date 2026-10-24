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
import com.kisia.staybooking.payment.PaymentReconciliationService;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentReconciliationServiceTests {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final TossPaymentGateway gateway = mock(TossPaymentGateway.class);
    private final PaymentReconciliationService service =
        new PaymentReconciliationService(payments, refunds, gateway);

    @Test
    void flagsAProviderChargeThatWasNeverRecordedLocallyWithoutMutatingThePayment() {
        PaymentEntity payment = payment("TOSS");
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100", 100_000,
                "DONE", 100_000L));

        var report = service.check(7L);

        assertEquals(PaymentReconciliationService.Result.REVIEW_REQUIRED, report.result());
        assertEquals("DONE", report.providerStatus());
        assertEquals(PaymentStatus.READY, payment.getStatus());
    }

    @Test
    void matchesARecordedChargeAndACompletedPartialRefund() {
        PaymentEntity payment = payment("TOSS");
        payment.complete("toss-key", LocalDateTime.now());
        RefundEntity refund = new RefundEntity(payment.getBooking(), 50_000, "취소 요청");
        refund.start(LocalDateTime.now());
        refund.refunded(LocalDateTime.now());
        when(refunds.findByBookingBookingIdIn(List.of(3L))).thenReturn(List.of(refund));
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100", 100_000,
                "DONE", 100_000L))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100", 100_000,
                "PARTIAL_CANCELED", 50_000L));

        assertEquals(PaymentReconciliationService.Result.REVIEW_REQUIRED, service.check(7L).result());
        payment.refund(50_000);
        assertEquals(PaymentReconciliationService.Result.MATCHED, service.check(7L).result());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
    }

    @Test
    void treatsMissingUnapprovedOrderAsNormalButMissingPaidOrderAsReviewRequired() {
        PaymentEntity payment = payment("TOSS");
        when(gateway.findByOrderId("ORDER-100")).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
            "PROVIDER_PAYMENT_NOT_FOUND", "조회 결과 없음"));

        assertEquals(PaymentReconciliationService.Result.MATCHED, service.check(7L).result());
        payment.complete("toss-key", LocalDateTime.now());
        assertEquals(PaymentReconciliationService.Result.REVIEW_REQUIRED, service.check(7L).result());
    }

    @Test
    void skipsTheProviderForLocalDemoPayments() {
        payment("DEMO");
        assertEquals(PaymentReconciliationService.Result.NOT_APPLICABLE, service.check(7L).result());
        verifyNoInteractions(gateway);
    }

    @Test
    void scansOnlyTossPaymentsAndFlagsAChargeMissingFromLocalRecords() {
        PaymentEntity payment = payment("TOSS");
        when(payments.findByPaymentMethodOrderByCreatedAtDescPaymentIdDesc("TOSS", PageRequest.of(0, 10)))
            .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 10), 1));
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100", 100_000,
                "DONE", 100_000L));

        var page = service.scan(0, 10);

        assertEquals(1, page.totalElements());
        assertEquals(1, page.content().size());
        assertEquals(PaymentReconciliationService.Result.REVIEW_REQUIRED,
            page.content().getFirst().result());
        assertEquals(PaymentStatus.READY, payment.getStatus());
    }

    @Test
    void keepsAProviderLookupFailureVisibleInTheScan() {
        PaymentEntity payment = payment("TOSS");
        when(payments.findByPaymentMethodOrderByCreatedAtDescPaymentIdDesc("TOSS", PageRequest.of(0, 10)))
            .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 10), 1));
        when(gateway.findByOrderId("ORDER-100"))
            .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_LOOKUP_FAILED", "조회 실패"));

        var page = service.scan(0, 10);

        assertEquals(PaymentReconciliationService.Result.LOOKUP_FAILED,
            page.content().getFirst().result());
        assertEquals("LOOKUP_FAILED", page.content().getFirst().providerStatus());
        assertEquals(PaymentStatus.READY, payment.getStatus());
    }

    private PaymentEntity payment(String method) {
        UserEntity guest = new UserEntity("guest", "hash", "Guest", "guest@example.test");
        AccommodationEntity stay = new AccommodationEntity("Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(stay, "Standard", 100_000, 2, 1);
        BookingEntity booking = new BookingEntity(guest, room, LocalDate.now().plusDays(2),
            LocalDate.now().plusDays(3), 1, 100_000, 0, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(booking, "bookingId", 3L);
        PaymentEntity payment = new PaymentEntity(booking, "ORDER-100", method, 100_000);
        ReflectionTestUtils.setField(payment, "paymentId", 7L);
        when(payments.findById(7L)).thenReturn(Optional.of(payment));
        return payment;
    }
}
