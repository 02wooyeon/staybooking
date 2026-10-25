package com.kisia.staybooking;

import java.time.LocalDate;

import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class FreeBookingPaymentApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private PaymentService service;
    @Autowired private PaymentRepository payments;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingCancellationService cancellations;
    @Autowired private RefundRepository refunds;

    @Test
    void freeBookingCompletesWithoutPgAndRepeatedRequestReturnsTheSamePayment() throws Exception {
        UserEntity guest = fixtures.user("무료 예약자");
        UserEntity stranger = fixtures.user("다른 사용자");
        RoomEntity room = fixtures.room(0, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);
        var booking = fixtures.booking(guest, room, checkIn, checkIn.plusDays(1),
            BookingStatus.PAYMENT_PENDING);
        String body = "{\"bookingId\":" + booking.getBookingId() + "}";
        String endpoint = "/api/v1/payments/free-complete";

        ApiException preparationError = assertThrows(ApiException.class,
            () -> service.prepare(guest.getUserId(), booking.getBookingId(), "TOSS"));
        assertEquals("PAYMENT_NOT_REQUIRED", preparationError.getCode());

        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(endpoint).header("Authorization", fixtures.bearer(stranger))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        mvc.perform(post(endpoint).header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.amount").value(0));
        Long paymentId = payments.findByBookingBookingIdAndStatus(booking.getBookingId(), PaymentStatus.PAID)
            .getFirst().getPaymentId();

        mvc.perform(post(endpoint).header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(paymentId))
            .andExpect(jsonPath("$.amount").value(0));
        assertEquals(1, payments.findByBookingBookingIdAndStatus(booking.getBookingId(), PaymentStatus.PAID).size());
        assertEquals("FREE", payments.findById(paymentId).orElseThrow().getPaymentMethod());
        assertEquals(BookingStatus.PENDING, bookings.findById(booking.getBookingId()).orElseThrow().getStatus());
    }

    @Test
    void nonzeroBookingCannotUseFreeCompletion() throws Exception {
        UserEntity guest = fixtures.user("유료 예약자");
        RoomEntity room = fixtures.room(100_000, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);
        var booking = fixtures.booking(guest, room, checkIn, checkIn.plusDays(1),
            BookingStatus.PAYMENT_PENDING);

        mvc.perform(post("/api/v1/payments/free-complete")
                .header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":" + booking.getBookingId() + "}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_REQUIRED"));
        assertEquals(BookingStatus.PAYMENT_PENDING,
            bookings.findById(booking.getBookingId()).orElseThrow().getStatus());
    }

    @Test
    void cancellingAFreeBookingRecordsZeroRefundWithoutCallingPg() {
        UserEntity guest = fixtures.user("무료 취소 예약자");
        UserEntity admin = fixtures.user("무료 취소 관리자", UserRole.ROLE_ADMIN);
        RoomEntity room = fixtures.room(0, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);
        var booking = fixtures.booking(guest, room, checkIn, checkIn.plusDays(1),
            BookingStatus.PAYMENT_PENDING);
        var payment = service.completeFree(guest.getUserId(), booking.getBookingId());

        cancellations.cancel(admin.getUserId(), booking.getBookingId(),
            CancellationReasonType.ACCOMMODATION_FAULT, "운영자 취소");

        var refund = refunds.findByBookingBookingIdIn(java.util.List.of(booking.getBookingId())).getFirst();
        assertEquals(0, refund.getAmount());
        assertEquals(RefundStatus.REFUNDED, refund.getStatus());
        assertEquals(PaymentStatus.PAID, payments.findById(payment.getPaymentId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CANCELLED, bookings.findById(booking.getBookingId()).orElseThrow().getStatus());
    }
}
