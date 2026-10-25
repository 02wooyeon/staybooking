package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.booking.CancellationSource;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

        assertEquals(PaymentReconciliationService.Result.PROVIDER_CHARGED_NOT_RECORDED, report.result());
        assertEquals(BookingStatus.PAYMENT_PENDING, report.bookingStatus());
        assertEquals("DONE", report.providerStatus());
        assertEquals(PaymentStatus.READY, payment.getStatus());
    }

    @Test
    void separatesACancelledBookingChargedByTheProviderFromOtherMismatches() {
        PaymentEntity payment = payment("TOSS");
        payment.getBooking().cancel(payment.getBooking().getUser(), CancellationSource.SYSTEM,
            CancellationReasonType.ACCOMMODATION_FAULT, "운영 취소", LocalDateTime.now());
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100", 100_000,
                "DONE", 100_000L));

        var report = service.check(7L);

        assertEquals(PaymentReconciliationService.Result.CANCELLED_BOOKING_CHARGED, report.result());
        assertEquals(BookingStatus.CANCELLED, report.bookingStatus());
        assertEquals(PaymentStatus.READY, payment.getStatus());
    }

    @Test
    void doesNotClassifyAnUnverifiedProviderAmountAsAnUnrecordedCharge() {
        payment("TOSS");
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100", 99_000,
                "DONE", 99_000L));

        assertEquals(PaymentReconciliationService.Result.REVIEW_REQUIRED, service.check(7L).result());
    }

    @Test
    void doesNotMatchACancelledPaidBookingWithoutARefundRecord() {
        PaymentEntity payment = payment("TOSS");
        payment.complete("toss-key", LocalDateTime.now());
        payment.getBooking().cancel(payment.getBooking().getUser(), CancellationSource.SYSTEM,
            CancellationReasonType.ACCOMMODATION_FAULT, "운영 취소", LocalDateTime.now());
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100", 100_000,
                "DONE", 100_000L));

        assertEquals(PaymentReconciliationService.Result.REVIEW_REQUIRED, service.check(7L).result());
    }

    @Test
    void doesNotMatchARefundedPaymentForAnActiveBooking() {
        PaymentEntity payment = payment("TOSS");
        payment.complete("toss-key", LocalDateTime.now());
        payment.getBooking().markPaid(LocalDateTime.now());
        payment.refund(100_000);
        RefundEntity refund = new RefundEntity(payment.getBooking(), 100_000, "예약 취소");
        refund.start(LocalDateTime.now());
        refund.refunded(LocalDateTime.now());
        when(refunds.findByBookingBookingIdIn(List.of(3L))).thenReturn(List.of(refund));
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100", 100_000,
                "CANCELED", 0L));

        assertEquals(PaymentReconciliationService.Result.REVIEW_REQUIRED, service.check(7L).result());
    }

    @Test
    void doesNotMatchAPaidPaymentWhileTheBookingStillAwaitsPayment() {
        PaymentEntity payment = payment("TOSS");
        payment.complete("toss-key", LocalDateTime.now());
        when(gateway.findByOrderId("ORDER-100"))
            .thenReturn(new TossPaymentGateway.Confirmation("toss-key", "ORDER-100", 100_000,
                "DONE", 100_000L));

        assertEquals(PaymentReconciliationService.Result.REVIEW_REQUIRED, service.check(7L).result());
    }

    @Test
    void matchesARecordedChargeAndACompletedPartialRefund() {
        PaymentEntity payment = payment("TOSS");
        payment.complete("toss-key", LocalDateTime.now());
        payment.getBooking().cancel(payment.getBooking().getUser(), CancellationSource.SYSTEM,
            CancellationReasonType.ACCOMMODATION_FAULT, "운영 취소", LocalDateTime.now());
        RefundEntity refund = new RefundEntity(payment.getBooking(), 50_000, "취소 요청");
        ReflectionTestUtils.setField(refund, "refundId", 9L);
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
        var report = service.check(7L);
        assertEquals(PaymentReconciliationService.Result.MATCHED, report.result());
        assertEquals(9L, report.refundId());
        assertEquals(RefundStatus.REFUNDED, report.refundStatus());
        assertEquals(50_000L, report.refundAmount());
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
        assertEquals(PaymentReconciliationService.Result.PROVIDER_CHARGED_NOT_RECORDED,
            page.content().getFirst().result());
        assertNull(page.content().getFirst().refundId());
        assertEquals(PaymentStatus.READY, payment.getStatus());
        verify(payments, never()).findById(7L);
    }

    @Test
    void keepsAProviderLookupFailureVisibleInTheScan() {
        PaymentEntity payment = payment("TOSS");
        payment.complete("toss-key", LocalDateTime.now());
        RefundEntity refund = new RefundEntity(payment.getBooking(), 50_000, "취소 요청");
        ReflectionTestUtils.setField(refund, "refundId", 9L);
        when(refunds.findByBookingBookingIdIn(List.of(3L))).thenReturn(List.of(refund));
        when(payments.findByPaymentMethodOrderByCreatedAtDescPaymentIdDesc("TOSS", PageRequest.of(0, 10)))
            .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 10), 1));
        when(gateway.findByOrderId("ORDER-100"))
            .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_LOOKUP_FAILED", "조회 실패"));

        var page = service.scan(0, 10);

        assertEquals(PaymentReconciliationService.Result.LOOKUP_FAILED,
            page.content().getFirst().result());
        assertEquals("LOOKUP_FAILED", page.content().getFirst().providerStatus());
        assertEquals(9L, page.content().getFirst().refundId());
        assertEquals(RefundStatus.REQUESTED, page.content().getFirst().refundStatus());
        assertEquals(50_000L, page.content().getFirst().refundAmount());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
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
