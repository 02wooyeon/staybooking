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
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Autowired private PaymentRepository payments;
    @MockitoBean private TossPaymentGateway gateway;

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
