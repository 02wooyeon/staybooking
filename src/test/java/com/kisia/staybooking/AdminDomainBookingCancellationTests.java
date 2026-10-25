package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "staybooking.admin.host=admin.localhost",
    "staybooking.payment.demo-enabled=true",
    "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class AdminDomainBookingCancellationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository payments;
    @Autowired private RefundRepository refunds;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private TossPaymentGateway gateway;

    @Test
    void administratorCanCancelUnpaidBookingWithoutCreatingRefund() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("unpaid-admin-" + tag, "hash", "Admin", null,
            "unpaid-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity guest = users.save(new UserEntity("unpaid-guest-" + tag, "hash", "Guest",
            "unpaid-guest-" + tag + "@example.test"));
        AccommodationEntity accommodation = accommodations.save(new AccommodationEntity(
            "Unpaid stay " + tag, "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(accommodation, "Standard", 100_000, 2, 1));
        BookingEntity booking = bookings.save(new BookingEntity(guest, room,
            LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        var prepared = paymentService.prepare(guest.getUserId(), booking.getBookingId(), "DEMO");
        String authorization = "Bearer " + jwt.createAccessToken(admin);
        String path = "/api/v1/admin/bookings/" + booking.getBookingId();
        String action = "{\"action\":\"CANCEL\",\"reasonType\":\"ACCOMMODATION_FAULT\","
            + "\"reason\":\"운영자 직접 취소\"}";

        mvc.perform(patch("http://localhost" + path).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(action))
            .andExpect(status().isNotFound());
        mvc.perform(patch("http://admin.localhost" + path).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(action))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(BookingStatus.CANCELLED, bookings.findById(booking.getBookingId()).orElseThrow().getStatus());
        assertFalse(refunds.existsByBookingBookingId(booking.getBookingId()));
        assertEquals(PaymentStatus.READY, payments.findById(prepared.getPaymentId()).orElseThrow().getStatus());
        mvc.perform(post("http://localhost/api/v1/payments/demo-complete")
                .header("Authorization", "Bearer " + jwt.createAccessToken(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":" + booking.getBookingId() + "}"))
            .andExpect(status().isConflict());
        mvc.perform(get("http://admin.localhost/api/v1/admin/bookings/search")
                .param("bookingId", booking.getBookingId().toString())
                .header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].booking.status").value("CANCELLED"));
    }

    @Test
    void paidBookingCancellationReturnsFinalRefundStateImmediately() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("paid-admin-" + tag, "hash", "Admin", null,
            "paid-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity otherAdmin = users.save(new UserEntity("other-paid-admin-" + tag, "hash", "Other Admin", null,
            "other-paid-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity guest = users.save(new UserEntity("paid-guest-" + tag, "hash", "Guest",
            "paid-guest-" + tag + "@example.test"));
        AccommodationEntity accommodation = accommodations.save(new AccommodationEntity(
            "Paid stay " + tag, "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(accommodation, "Standard", 100_000, 2, 1));
        BookingEntity booking = bookings.save(new BookingEntity(guest, room,
            LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        paymentService.completeDemo(guest.getUserId(), booking.getBookingId());
        String token = "Bearer " + jwt.createAccessToken(admin);
        String path = "http://admin.localhost/api/v1/admin/bookings/" + booking.getBookingId();

        mvc.perform(patch(path).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CANCEL\",\"reasonType\":\"ACCOMMODATION_FAULT\","
                    + "\"reason\":\"운영자 직접 취소\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.refundStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.refundAmount").value(100_000));
        mvc.perform(patch(path).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CANCEL\",\"reasonType\":\"ACCOMMODATION_FAULT\","
                    + "\"reason\":\"운영자 직접 취소\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.refundStatus").value("REFUNDED"));
        assertEquals(1, refunds.findByBookingBookingIdIn(java.util.List.of(booking.getBookingId())).size());
        mvc.perform(patch(path).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CANCEL\",\"reasonType\":\"USER_FAULT\","
                    + "\"reason\":\"다른 사유\"}"))
            .andExpect(status().isConflict());
        mvc.perform(patch(path).header("Authorization", "Bearer " + jwt.createAccessToken(otherAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CANCEL\",\"reasonType\":\"ACCOMMODATION_FAULT\","
                    + "\"reason\":\"운영자 직접 취소\"}"))
            .andExpect(status().isConflict());
        mvc.perform(get(path).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.booking.refundStatus").value("REFUNDED"));
    }

    @Test
    void failedProviderCancellationIsVisibleAndCanBeRetriedByAnAdministrator() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("failed-refund-admin-" + tag, "hash", "Admin", null,
            "failed-refund-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity guest = users.save(new UserEntity("failed-refund-guest-" + tag, "hash", "Guest",
            "failed-refund-guest-" + tag + "@example.test"));
        AccommodationEntity accommodation = accommodations.save(new AccommodationEntity(
            "Failed refund stay " + tag, "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(accommodation, "Standard", 100_000, 2, 1));
        BookingEntity booking = bookings.save(new BookingEntity(guest, room,
            LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        jdbc.update("update bookings set status = ? where booking_id = ?", "PENDING", booking.getBookingId());
        PaymentEntity payment = new PaymentEntity(booking, "FAILED-REFUND-ORDER-" + tag, "TOSS", 100_000);
        payment.complete("FAILED-REFUND-KEY-" + tag, LocalDateTime.now());
        payments.save(payment);
        when(gateway.findByOrderId(payment.getOrderId()))
            .thenReturn(new TossPaymentGateway.Confirmation(payment.getPaymentKey(),
                payment.getOrderId(), 100_000, "DONE", 100_000L));
        when(gateway.cancel(eq(payment.getPaymentKey()), eq("운영자 직접 취소"), eq(100_000L), anyLong()))
            .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CANCEL_FAILED", "PG 응답 확인 실패"))
            .thenReturn(new TossPaymentGateway.Cancellation(payment.getPaymentKey(),
                payment.getOrderId(), 100_000, 0, "CANCELED",
                java.util.List.of(new TossPaymentGateway.Cancel(100_000, "DONE", "cancel-transaction")),
                "cancel-transaction"));
        String token = "Bearer " + jwt.createAccessToken(admin);
        String path = "http://admin.localhost/api/v1/admin/bookings/" + booking.getBookingId();

        mvc.perform(patch(path).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CANCEL\",\"reasonType\":\"ACCOMMODATION_FAULT\","
                    + "\"reason\":\"운영자 직접 취소\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.refundStatus").value("FAILED"))
            .andExpect(jsonPath("$.refundAmount").value(100_000))
            .andExpect(jsonPath("$.refundedAmount").value(0));
        mvc.perform(get(path).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.booking.refundStatus").value("FAILED"));
        mvc.perform(get("http://localhost/api/v1/bookings/" + booking.getBookingId())
                .header("Authorization", "Bearer " + jwt.createAccessToken(guest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refundStatus").value("FAILED"))
            .andExpect(jsonPath("$.refundedAmount").value(0));
        var refund = refunds.findByBookingBookingIdIn(java.util.List.of(booking.getBookingId())).getFirst();
        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertEquals(PaymentStatus.PAID, payments.findById(payment.getPaymentId()).orElseThrow().getStatus());

        mvc.perform(post("http://admin.localhost/api/v1/admin/refunds/" + refund.getRefundId() + "/retry")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"))
            .andExpect(jsonPath("$.processedBy").value(admin.getUserId()));
        mvc.perform(get(path).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.booking.refundStatus").value("REFUNDED"));
        mvc.perform(get("http://localhost/api/v1/payments/me")
                .header("Authorization", "Bearer " + jwt.createAccessToken(guest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].paymentId").value(payment.getPaymentId()))
            .andExpect(jsonPath("$[0].status").value("REFUNDED"))
            .andExpect(jsonPath("$[0].refundId").value(refund.getRefundId()))
            .andExpect(jsonPath("$[0].refundStatus").value("REFUNDED"))
            .andExpect(jsonPath("$[0].refundedAmount").value(100_000));
        mvc.perform(get("http://admin.localhost/api/v1/admin/refunds/" + refund.getRefundId())
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"))
            .andExpect(jsonPath("$.processedBy").value(admin.getUserId()));
        assertEquals(PaymentStatus.REFUNDED, payments.findById(payment.getPaymentId()).orElseThrow().getStatus());
        verify(gateway, times(2)).cancel(eq(payment.getPaymentKey()),
            eq("운영자 직접 취소"), eq(100_000L), eq(refund.getRefundId()));
    }

    @Test
    void mismatchedPaidAmountCannotCancelOrCreateRefund() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("mismatch-admin-" + tag, "hash", "Admin", null,
            "mismatch-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity guest = users.save(new UserEntity("mismatch-guest-" + tag, "hash", "Guest",
            "mismatch-guest-" + tag + "@example.test"));
        AccommodationEntity accommodation = accommodations.save(new AccommodationEntity(
            "Mismatch stay " + tag, "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(accommodation, "Standard", 100_000, 2, 1));
        BookingEntity booking = bookings.save(new BookingEntity(guest, room,
            LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        var payment = paymentService.completeDemo(guest.getUserId(), booking.getBookingId());
        jdbc.update("update payments set paid_amount = ? where payment_id = ?", 90_000,
            payment.getPaymentId());

        mvc.perform(patch("http://admin.localhost/api/v1/admin/bookings/" + booking.getBookingId())
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CANCEL\",\"reasonType\":\"ACCOMMODATION_FAULT\","
                    + "\"reason\":\"운영자 직접 취소\"}"))
            .andExpect(status().isConflict());
        assertEquals(BookingStatus.PENDING, bookings.findById(booking.getBookingId()).orElseThrow().getStatus());
        assertFalse(refunds.existsByBookingBookingId(booking.getBookingId()));
    }
}
