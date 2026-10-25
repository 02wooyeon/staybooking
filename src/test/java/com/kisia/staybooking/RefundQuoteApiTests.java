package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class RefundQuoteApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private PaymentRepository payments;

    @Test
    void quotesOnlyTheOwnersPaidAndCancellableBooking() throws Exception {
        UserEntity owner = fixtures.user("예약자");
        UserEntity other = fixtures.user("다른 사용자");
        RoomEntity room = fixtures.room(100_000, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(2);
        BookingEntity booking = fixtures.booking(owner, room, checkIn, checkIn.plusDays(1), BookingStatus.PENDING);
        String path = "/api/v1/bookings/" + booking.getBookingId() + "/refund-quote";

        mvc.perform(get(path).param("reasonType", "USER_FAULT")
                .header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_VERIFIED"));

        PaymentEntity payment = new PaymentEntity(booking, "QUOTE-" + System.nanoTime(), "DEMO", 100_000);
        payment.complete("DEMO-QUOTE-" + System.nanoTime(), LocalDateTime.now(ZoneOffset.UTC));
        payments.save(payment);

        mvc.perform(get(path).param("reasonType", "USER_FAULT")
                .header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refundRate").value(50))
            .andExpect(jsonPath("$.refundAmount").value(50_000))
            .andExpect(jsonPath("$.mileageRefund").value(0));
        mvc.perform(get(path).param("reasonType", "ACCOMMODATION_FAULT")
                .header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refundRate").value(100))
            .andExpect(jsonPath("$.refundAmount").value(100_000));
        mvc.perform(get(path).param("reasonType", "USER_FAULT")
                .header("Authorization", fixtures.bearer(other)))
            .andExpect(status().isNotFound());
        mvc.perform(get(path).param("reasonType", "USER_FAULT"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(path).param("reasonType", "UNKNOWN")
                .header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnpaidAndPastCheckInBookings() throws Exception {
        UserEntity owner = fixtures.user("예약자");
        RoomEntity room = fixtures.room(100_000, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(3);
        BookingEntity unpaid = fixtures.booking(owner, room, checkIn, checkIn.plusDays(1),
            BookingStatus.PAYMENT_PENDING);
        BookingEntity pastStay = fixtures.finishedStay(owner, room);

        mvc.perform(get("/api/v1/bookings/" + unpaid.getBookingId() + "/refund-quote")
                .param("reasonType", "USER_FAULT").header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));
        mvc.perform(get("/api/v1/bookings/" + pastStay.getBookingId() + "/refund-quote")
                .param("reasonType", "USER_FAULT").header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CANCELLATION_CLOSED"));
    }

    @Test
    void rejectsQuoteWhenPaidAmountDoesNotMatchBooking() throws Exception {
        UserEntity owner = fixtures.user("예약자");
        RoomEntity room = fixtures.room(100_000, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);
        BookingEntity booking = fixtures.booking(owner, room, checkIn, checkIn.plusDays(1), BookingStatus.PENDING);
        PaymentEntity payment = new PaymentEntity(booking, "QUOTE-MISMATCH-" + System.nanoTime(), "DEMO", 90_000);
        payment.complete("DEMO-MISMATCH-" + System.nanoTime(), LocalDateTime.now(ZoneOffset.UTC));
        payments.save(payment);

        mvc.perform(get("/api/v1/bookings/" + booking.getBookingId() + "/refund-quote")
                .param("reasonType", "USER_FAULT")
                .header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_VERIFIED"));
    }
}
