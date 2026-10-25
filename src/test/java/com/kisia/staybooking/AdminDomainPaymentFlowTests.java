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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "staybooking.admin.host=admin.localhost")
@AutoConfigureMockMvc
class AdminDomainPaymentFlowTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private PaymentRepository payments;
    @Autowired private RefundRepository refunds;

    @Test
    void administratorCanSearchAndRefundDemoPaymentOnlyOnAdminDomain() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("pay-admin-" + tag, "hash", "Admin", null,
            "pay-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity guest = users.save(new UserEntity("pay-guest-" + tag, "hash", "Guest",
            "pay-guest-" + tag + "@example.test"));
        AccommodationEntity accommodation = accommodations.save(new AccommodationEntity(
            "Admin payment test " + tag, "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(accommodation, "Standard", 100_000, 2, 1));
        BookingEntity booking = bookings.save(new BookingEntity(guest, room,
            LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        PaymentEntity payment = new PaymentEntity(booking, "DEMO-ORDER-" + tag, "DEMO", 100_000);
        payment.complete("DEMO-KEY-" + tag, LocalDateTime.now());
        payments.save(payment);
        RefundEntity refund = refunds.save(new RefundEntity(booking, 100_000, "예약 취소"));
        String authorization = "Bearer " + jwt.createAccessToken(admin);
        String paymentPath = "/api/v1/admin/payments/search?bookingId=" + booking.getBookingId();

        mvc.perform(get("http://localhost" + paymentPath).header("Authorization", authorization))
            .andExpect(status().isNotFound());
        mvc.perform(get("http://admin.localhost" + paymentPath).header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].paymentId").value(payment.getPaymentId()))
            .andExpect(jsonPath("$.content[0].amount").value(100_000))
            .andExpect(jsonPath("$.content[0].refundStatus").value("REQUESTED"));
        String refundPath = "/api/v1/admin/refunds/search?bookingId=" + booking.getBookingId();
        mvc.perform(get("http://admin.localhost" + refundPath).header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].refundId").value(refund.getRefundId()))
            .andExpect(jsonPath("$.content[0].status").value("REQUESTED"))
            .andExpect(jsonPath("$.content[0].retryable").value(true));
        String reconciliationPath = "/api/v1/admin/refunds/" + refund.getRefundId() + "/reconciliation";
        mvc.perform(get("http://localhost" + reconciliationPath).header("Authorization", authorization))
            .andExpect(status().isNotFound());
        mvc.perform(get("http://admin.localhost" + reconciliationPath)
                .header("Authorization", "Bearer " + jwt.createAccessToken(guest)))
            .andExpect(status().isForbidden());
        mvc.perform(get("http://admin.localhost" + reconciliationPath)
                .header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("NOT_APPLICABLE"))
            .andExpect(jsonPath("$.paymentKey").doesNotExist());

        refund.start(LocalDateTime.now().minusMinutes(1));
        refund.failed("PAYMENT_CANCEL_FAILED");
        refunds.save(refund);
        mvc.perform(post("http://admin.localhost/api/v1/admin/refunds/" + refund.getRefundId() + "/retry")
                .header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"));

        mvc.perform(get("http://admin.localhost" + paymentPath).header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("REFUNDED"))
            .andExpect(jsonPath("$.content[0].refundStatus").value("REFUNDED"));
        mvc.perform(get("http://admin.localhost" + refundPath).header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("REFUNDED"))
            .andExpect(jsonPath("$.content[0].retryable").value(false))
            .andExpect(jsonPath("$.content[0].processedAt").isNotEmpty());
        mvc.perform(get("http://localhost/api/v1/bookings/" + booking.getBookingId())
                .header("Authorization", "Bearer " + jwt.createAccessToken(guest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refundStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.refundAmount").value(100_000));
    }
}
