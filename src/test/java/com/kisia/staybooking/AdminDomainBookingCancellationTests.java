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
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "staybooking.admin.host=admin.localhost",
    "staybooking.payment.demo-enabled=true"
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
        mvc.perform(get(path).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.booking.refundStatus").value("REFUNDED"));
    }
}
