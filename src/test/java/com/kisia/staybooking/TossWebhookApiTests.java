package com.kisia.staybooking;

import java.time.LocalDate;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class TossWebhookApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository payments;
    @Autowired private BookingRepository bookings;
    @MockitoBean private TossPaymentGateway gateway;

    @BeforeEach
    void configured() {
        when(gateway.isConfigured()).thenReturn(true);
    }

    @Test
    void completesPaymentOnlyAfterTossConfirmsAndIgnoresRepeats() throws Exception {
        PaymentEntity payment = readyTossPayment();
        String orderId = payment.getOrderId();
        when(gateway.findByOrderId(orderId)).thenReturn(new TossPaymentGateway.Confirmation(
            "pay_key_1", orderId, payment.getPaidAmount(), "DONE", payment.getPaidAmount()));

        // 본문의 상태·금액(1원)은 쓰지 않는다. 토스에 다시 조회한 결과로 완료 처리한다.
        webhook(event(orderId, ",\"status\":\"DONE\",\"totalAmount\":1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("APPLIED"));
        assertEquals(PaymentStatus.PAID, payments.findById(payment.getPaymentId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.PENDING,
            bookings.findById(payment.getBooking().getBookingId()).orElseThrow().getStatus());

        // 같은 이벤트가 다시 와도 한 번만 반영된다.
        webhook(event(orderId, "")).andExpect(status().isOk());
        assertEquals(PaymentStatus.PAID, payments.findById(payment.getPaymentId()).orElseThrow().getStatus());
    }

    @Test
    void forgedDoneEventDoesNotCompletePaymentWhenTossSaysOtherwise() throws Exception {
        PaymentEntity payment = readyTossPayment();
        String orderId = payment.getOrderId();
        when(gateway.findByOrderId(orderId)).thenReturn(new TossPaymentGateway.Confirmation(
            "pay_key_2", orderId, payment.getPaidAmount(), "CANCELED", 0L));

        webhook(event(orderId, ",\"status\":\"DONE\""))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("IGNORED"));
        assertEquals(PaymentStatus.READY, payments.findById(payment.getPaymentId()).orElseThrow().getStatus());
    }

    @Test
    void ignoresUnknownOrdersOtherEventsAndMalformedIdsWithoutCallingToss() throws Exception {
        webhook(event("ORDER-NOTOURS0000", "")).andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("IGNORED"));
        webhook("{\"eventType\":\"DEPOSIT_CALLBACK\",\"data\":{\"orderId\":\"ORDER-NOTOURS0000\"}}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("IGNORED"));
        webhook(event("../../etc/passwd", "")).andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("IGNORED"));
        verify(gateway, never()).findByOrderId(anyString());
    }

    @Test
    void transientTossLookupFailureAsksTossToRetry() throws Exception {
        PaymentEntity payment = readyTossPayment();
        when(gateway.findByOrderId(payment.getOrderId())).thenThrow(new ApiException(
            HttpStatus.BAD_GATEWAY, "PAYMENT_LOOKUP_FAILED", "조회 실패"));
        webhook(event(payment.getOrderId(), "")).andExpect(status().isBadGateway());
        assertEquals(PaymentStatus.READY, payments.findById(payment.getPaymentId()).orElseThrow().getStatus());
    }

    private PaymentEntity readyTossPayment() {
        UserEntity guest = fixtures.user("결제자");
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(10);
        BookingEntity booking = fixtures.booking(guest, fixtures.room(100_000, 2, 1), checkIn, checkIn.plusDays(1),
            BookingStatus.PAYMENT_PENDING);
        return paymentService.prepare(guest.getUserId(), booking.getBookingId(), "TOSS");
    }

    private static String event(String orderId, String extra) {
        return "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"" + orderId + "\"" + extra + "}}";
    }

    private ResultActions webhook(String json) throws Exception {
        return mvc.perform(post("/api/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
