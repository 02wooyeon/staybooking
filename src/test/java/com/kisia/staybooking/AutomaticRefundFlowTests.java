package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.refund.AutomaticRefundService;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundRecoveryService;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestFixtures.class)
class AutomaticRefundFlowTests {
    @Autowired private TestFixtures fixtures;
    @Autowired private BookingCancellationService cancellations;
    @Autowired private AutomaticRefundService settlement;
    @Autowired private RefundRepository refunds;
    @Autowired private RefundRecoveryService recovery;
    @Autowired private PaymentRepository payments;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private TossPaymentGateway gateway;

    @Test
    void processingRefundBecomesRetryableOnlyAfterTimeout() {
        LocalDateTime started = LocalDateTime.now(ZoneOffset.UTC);
        RefundEntity refund = new RefundEntity(null, 1_000, "예약 취소");
        assertTrue(refund.canRetry(started));
        refund.start(started);
        assertFalse(refund.canRetry(started.plusSeconds(30)));
        assertTrue(refund.canRetry(started.plusSeconds(31)));
        refund.refunded(started.plusSeconds(31));
        assertFalse(refund.canRetry(started.plusSeconds(60)));
    }

    @Test
    void recoverySettlesOnlyNewAutomaticRefundsLeftPendingOrStale() {
        UserEntity guest = fixtures.user("예약자");
        RoomEntity room = fixtures.room(100_000, 2, 3);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);
        BookingEntity missed = fixtures.booking(guest, room, checkIn, checkIn.plusDays(1), BookingStatus.PENDING);
        BookingEntity stale = fixtures.booking(guest, room, checkIn.plusDays(2), checkIn.plusDays(3), BookingStatus.PENDING);
        BookingEntity legacy = fixtures.booking(guest, room, checkIn.plusDays(4), checkIn.plusDays(5), BookingStatus.PENDING);
        BookingEntity active = fixtures.booking(guest, room, checkIn.plusDays(6), checkIn.plusDays(7), BookingStatus.PENDING);

        for (BookingEntity booking : java.util.List.of(missed, stale, legacy, active)) {
            PaymentEntity payment = new PaymentEntity(booking, "DEMO-ORDER-" + booking.getBookingId(), "DEMO", 100_000);
            payment.complete("DEMO-KEY-" + booking.getBookingId(), LocalDateTime.now(ZoneOffset.UTC));
            payments.save(payment);
        }
        RefundEntity missedRefund = refunds.save(new RefundEntity(missed, 100_000, "자동 환불 대기"));
        RefundEntity staleRefund = new RefundEntity(stale, 100_000, "자동 환불 처리 중");
        staleRefund.start(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(3));
        staleRefund = refunds.save(staleRefund);
        RefundEntity legacyRefund = refunds.save(new RefundEntity(legacy, 100_000, "이전 수동 환불"));
        RefundEntity activeRefund = new RefundEntity(active, 100_000, "진행 중인 환불");
        activeRefund.start(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        activeRefund = refunds.save(activeRefund);
        jdbc.update("update refunds set created_at = ?, auto_processing = null where refund_id = ?",
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(3), legacyRefund.getRefundId());
        jdbc.update("update refunds set created_at = ? where refund_id = ?",
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(3), missedRefund.getRefundId());

        assertEquals(2, recovery.recover());
        assertEquals(RefundStatus.REFUNDED, refunds.findById(missedRefund.getRefundId()).orElseThrow().getStatus());
        assertEquals(RefundStatus.REFUNDED, refunds.findById(staleRefund.getRefundId()).orElseThrow().getStatus());
        assertEquals(RefundStatus.REQUESTED, refunds.findById(legacyRefund.getRefundId()).orElseThrow().getStatus());
        assertEquals(RefundStatus.PROCESSING, refunds.findById(activeRefund.getRefundId()).orElseThrow().getStatus());
    }

    @Test
    void failedProviderCallLeavesBookingCancelledAndRetryUsesSameRefundId() {
        UserEntity guest = fixtures.user("예약자");
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        RoomEntity room = fixtures.room(100_000, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);
        BookingEntity booking = fixtures.booking(guest, room, checkIn, checkIn.plusDays(1), BookingStatus.PENDING);
        PaymentEntity payment = new PaymentEntity(booking, "REFUND-ORDER-" + System.nanoTime(), "TOSS", 100_000);
        payment.complete("REFUND-KEY-" + System.nanoTime(), LocalDateTime.now(ZoneOffset.UTC));
        payments.save(payment);
        String key = payment.getPaymentKey();
        when(gateway.cancel(eq(key), eq("운영자 취소"), eq(100_000L), anyLong()))
            .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CANCEL_FAILED", "응답 확인 실패"))
            .thenReturn(new TossPaymentGateway.Cancellation(key, payment.getOrderId(), 100_000,
                0, "CANCELED", java.util.List.of(new TossPaymentGateway.Cancel(100_000))));

        cancellations.cancel(admin.getUserId(), booking.getBookingId(),
            CancellationReasonType.ACCOMMODATION_FAULT, "운영자 취소");
        var refund = refunds.findByBookingBookingIdIn(java.util.List.of(booking.getBookingId())).getFirst();
        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertEquals("PAYMENT_CANCEL_FAILED", refund.getFailureCode());
        assertEquals(PaymentStatus.PAID, payments.findById(payment.getPaymentId()).orElseThrow().getStatus());

        var retried = settlement.retry(admin.getUserId(), refund.getRefundId());
        assertEquals(RefundStatus.REFUNDED, retried.getStatus());
        assertNotNull(retried.getProcessedAt());
        assertEquals(PaymentStatus.REFUNDED, payments.findById(payment.getPaymentId()).orElseThrow().getStatus());
        verify(gateway, times(2)).cancel(eq(key), eq("운영자 취소"), eq(100_000L), eq(refund.getRefundId()));
        assertThrows(ApiException.class, () -> settlement.retry(admin.getUserId(), refund.getRefundId()));
    }
}
