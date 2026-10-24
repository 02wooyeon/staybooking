package com.kisia.staybooking;

import java.time.LocalDate;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "staybooking.payment.demo-enabled=true")
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class DemoPaymentIsolationApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private PaymentService service;
    @Autowired private PaymentRepository payments;

    @Test
    void demoCompletionCannotCompleteTossOrder() throws Exception {
        UserEntity owner = fixtures.user("예약자");
        RoomEntity room = fixtures.room(100_000, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);
        BookingEntity booking = fixtures.booking(owner, room, checkIn, checkIn.plusDays(1),
            BookingStatus.PAYMENT_PENDING);
        PaymentEntity tossOrder = service.prepare(owner.getUserId(), booking.getBookingId(), "TOSS");

        mvc.perform(post("/api/v1/payments/complete")
                .header("Authorization", fixtures.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeBody(tossOrder.getOrderId())))
            .andExpect(status().isConflict());
        assertEquals(PaymentStatus.READY, payments.findById(tossOrder.getPaymentId()).orElseThrow().getStatus());

        BookingEntity demoBooking = fixtures.booking(owner, room, checkIn.plusDays(2),
            checkIn.plusDays(3), BookingStatus.PAYMENT_PENDING);
        PaymentEntity demoOrder = service.prepare(owner.getUserId(), demoBooking.getBookingId(), "DEMO");
        mvc.perform(post("/api/v1/payments/complete")
                .header("Authorization", fixtures.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeBody(demoOrder.getOrderId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"));
        PaymentEntity completed = payments.findById(demoOrder.getPaymentId()).orElseThrow();
        assertTrue(completed.getPaymentKey().startsWith("DEMO-"));
    }

    @Test
    void publicPreparationAcceptsOnlyConfiguredToss() throws Exception {
        UserEntity owner = fixtures.user("예약자");
        String path = "/api/v1/payments/prepare";

        mvc.perform(post(path).header("Authorization", fixtures.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":1,\"paymentMethod\":\"CARD\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_PAYMENT_METHOD"));
        mvc.perform(post(path).header("Authorization", fixtures.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":1,\"paymentMethod\":\"TOSS\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_CONFIGURED"));
    }

    private static String completeBody(String orderId) {
        return "{\"orderId\":\"" + orderId + "\",\"paymentKey\":\"client-value\",\"amount\":100000}";
    }
}
