package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.refund.RefundEntity;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"staybooking.toss.client-key=", "staybooking.toss.secret-key="})
@AutoConfigureMockMvc
class PaymentHistoryApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private PaymentRepository payments;
    @Autowired private RefundRepository refunds;

    @Test
    void paginatesOnlyOwnPaymentsAndFiltersByBookingAndStatus() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity owner = users.save(new UserEntity("history-owner-" + tag, "hash", "Owner",
            "history-owner-" + tag + "@example.test"));
        UserEntity other = users.save(new UserEntity("history-other-" + tag, "hash", "Other",
            "history-other-" + tag + "@example.test"));
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("History stay " + tag,
            "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 2));
        BookingEntity first = bookings.save(new BookingEntity(owner, room,
            LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        BookingEntity second = bookings.save(new BookingEntity(owner, room,
            LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        BookingEntity foreign = bookings.save(new BookingEntity(other, room,
            LocalDate.now().plusDays(7), LocalDate.now().plusDays(8), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        PaymentEntity paid = new PaymentEntity(first, "ORDER-PAID-" + tag, "TOSS", 100_000);
        paid.complete("private-payment-key", LocalDateTime.now());
        payments.save(paid);
        PaymentEntity ready = payments.save(new PaymentEntity(second, "ORDER-READY-" + tag,
            "TOSS", 100_000));
        payments.save(new PaymentEntity(foreign, "ORDER-FOREIGN-" + tag, "TOSS", 100_000));
        String token = "Bearer " + jwt.createAccessToken(owner);

        mvc.perform(get("/api/v1/payments/me/search").param("size", "1")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].paymentId").value(ready.getPaymentId()))
            .andExpect(jsonPath("$.content[0].paymentKey").doesNotExist());
        mvc.perform(get("/api/v1/payments/me/search").param("size", "1").param("page", "1")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].paymentId").value(paid.getPaymentId()));
        mvc.perform(get("/api/v1/payments/me/search")
                .param("bookingId", first.getBookingId().toString()).param("status", "PAID")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].paymentId").value(paid.getPaymentId()));
        mvc.perform(get("/api/v1/payments/me/search")
                .param("bookingId", foreign.getBookingId().toString())
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/payments/me/search").param("size", "101")
                .header("Authorization", token))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/payments/me/search"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyTheOwnersPaymentAndRefundWithoutExposingThePaymentKey() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity owner = users.save(new UserEntity("pay-owner-" + tag, "hash", "Owner",
            "pay-owner-" + tag + "@example.test"));
        UserEntity stranger = users.save(new UserEntity("pay-stranger-" + tag, "hash", "Stranger",
            "pay-stranger-" + tag + "@example.test"));
        UserEntity admin = users.save(new UserEntity("pay-admin-" + tag, "hash", "Admin", null,
            "pay-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Stay " + tag,
            "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 1));
        BookingEntity booking = bookings.save(new BookingEntity(owner, room,
            LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        PaymentEntity payment = new PaymentEntity(booking, "ORDER-" + tag, "TOSS", 100_000);
        payment.complete("secret-payment-key", LocalDateTime.now());
        payments.save(payment);
        refunds.save(new RefundEntity(booking, 50_000, "취소 요청"));

        mvc.perform(get("/api/v1/payments/me")
                .header("Authorization", "Bearer " + jwt.createAccessToken(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].bookingId").value(booking.getBookingId()))
            .andExpect(jsonPath("$[0].status").value("PAID"))
            .andExpect(jsonPath("$[0].refundStatus").value("REQUESTED"))
            .andExpect(jsonPath("$[0].refundAmount").value(50_000))
            .andExpect(jsonPath("$[0].paymentKey").doesNotExist());
        mvc.perform(get("/api/v1/payments/me")
                .header("Authorization", "Bearer " + jwt.createAccessToken(stranger)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/payments/me"))
            .andExpect(status().isUnauthorized());

        String search = "/api/v1/admin/payments/search?bookingId=" + booking.getBookingId()
            + "&status=PAID&size=1";
        mvc.perform(get(search).header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].bookingId").value(booking.getBookingId()))
            .andExpect(jsonPath("$.content[0].refundStatus").value("REQUESTED"));
        mvc.perform(get(search + "&page=1")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get(search).header("Authorization", "Bearer " + jwt.createAccessToken(owner)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/payments")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].bookingId").value(booking.getBookingId()));
        mvc.perform(get(search + "&page=-1")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest());

        String reconciliation = "/api/v1/admin/payments/" + payment.getPaymentId() + "/reconciliation";
        mvc.perform(get(reconciliation)
                .header("Authorization", "Bearer " + jwt.createAccessToken(owner)))
            .andExpect(status().isForbidden());
        mvc.perform(get(reconciliation)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_CONFIGURED"));

        String refundSearch = "/api/v1/admin/refunds/search?bookingId=" + booking.getBookingId()
            + "&status=REQUESTED&size=1";
        mvc.perform(get(refundSearch)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].bookingId").value(booking.getBookingId()))
            .andExpect(jsonPath("$.content[0].status").value("REQUESTED"));
        mvc.perform(get(refundSearch + "&page=1")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/admin/refunds")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].bookingId").value(booking.getBookingId()));
        mvc.perform(get(refundSearch)
                .header("Authorization", "Bearer " + jwt.createAccessToken(owner)))
            .andExpect(status().isForbidden());
        mvc.perform(get(refundSearch.replace("&size=1", "&size=0"))
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest());
    }
}
