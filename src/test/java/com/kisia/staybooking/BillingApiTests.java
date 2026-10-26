package com.kisia.staybooking;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.payment.BillingCardRepository;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class BillingApiTests {
    private static final String BILLING_KEY = "billing-key-secret-value";
    private static final String API = "/api/v1/payments/billing-keys";

    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private PaymentRepository payments;
    @Autowired private BillingCardRepository cards;
    @MockitoBean private TossPaymentGateway gateway;

    @BeforeEach
    void billingKeys() {
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.billingEnabled()).thenReturn(true);
    }

    @Test
    void registersCardWithoutExposingOrStoringThePlainBillingKey() throws Exception {
        UserEntity user = fixtures.user("카드회원");
        String customerKey = customerKey(user);

        // 다른 사람의 customerKey로 받은 authKey는 내 계정에 붙일 수 없다.
        register(user, "CUST-someoneelse", "auth_1").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BILLING_CUSTOMER_MISMATCH"));

        when(gateway.issueBillingKey("auth_1", customerKey)).thenReturn(
            new TossPaymentGateway.BillingIssue(BILLING_KEY, customerKey, "신한", "43301234****123*"));
        register(user, customerKey, "auth_1").andExpect(status().isCreated())
            .andExpect(jsonPath("$.cardCompany").value("신한카드"))
            .andExpect(jsonPath("$.cardNumber").value("43301234****123*"))
            .andExpect(jsonPath("$.billingKey").doesNotExist())
            .andExpect(jsonPath("$.customerKey").doesNotExist());

        String list = mvc.perform(get(API).header("Authorization", fixtures.bearer(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andReturn().getResponse().getContentAsString();
        assertFalse(list.contains(BILLING_KEY));
        // DB에도 암호문만 있다.
        String stored = cards.findByUserUserId(user.getUserId()).orElseThrow().getBillingKeyEncrypted();
        assertFalse(stored.contains(BILLING_KEY));
    }

    @Test
    void namesTheCardFromTheIssuerCodeWhenTossLeavesTheCompanyEmpty() throws Exception {
        UserEntity user = fixtures.user("농협회원");
        String customerKey = customerKey(user);
        when(gateway.issueBillingKey("auth_nh", customerKey)).thenReturn(new TossPaymentGateway.BillingIssue(
            BILLING_KEY, customerKey, null, null, new TossPaymentGateway.Card("91", "53651234****567*", "체크")));

        register(user, customerKey, "auth_nh").andExpect(status().isCreated())
            .andExpect(jsonPath("$.cardCompany").value("NH농협 체크카드"))
            .andExpect(jsonPath("$.cardNumber").value("53651234****567*"));
    }

    @Test
    void paysWithSavedCardOnlyAfterPasswordCheckAndTossConfirmation() throws Exception {
        UserEntity user = fixtures.user("결제회원");
        long cardId = registeredCard(user);
        BookingEntity booking = pendingBooking(user);
        when(gateway.chargeBillingKey(eq(BILLING_KEY), anyString(), anyString(), anyString(), anyLong()))
            .thenAnswer(call -> new TossPaymentGateway.Confirmation("pay_billing_1", call.getArgument(2),
                call.getArgument(4), "DONE", call.getArgument(4)));

        // 비밀번호가 틀리면 토스에 결제 요청 자체를 보내지 않는다.
        pay(user, cardId, booking.getBookingId(), "Wrong-passw0rd").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        verify(gateway, never()).chargeBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong());

        pay(user, cardId, booking.getBookingId(), TestFixtures.PASSWORD).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.amount").value(100_000));
        // 서버가 정한 금액으로만 결제를 요청한다.
        verify(gateway).chargeBillingKey(eq(BILLING_KEY), anyString(), anyString(), anyString(), eq(100_000L));
    }

    @Test
    void doesNotCompleteWhenTossReturnsADifferentAmount() throws Exception {
        UserEntity user = fixtures.user("금액회원");
        long cardId = registeredCard(user);
        BookingEntity booking = pendingBooking(user);
        when(gateway.chargeBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong()))
            .thenAnswer(call -> new TossPaymentGateway.Confirmation("pay_billing_2", call.getArgument(2),
                1L, "DONE", 1L));

        pay(user, cardId, booking.getBookingId(), TestFixtures.PASSWORD).andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("PAYMENT_CONFIRM_MISMATCH"));
        assertEquals(PaymentStatus.READY, payments.findAllByOrderByCreatedAtDesc().stream()
            .filter(p -> p.getBooking().getBookingId().equals(booking.getBookingId()))
            .findFirst().orElseThrow().getStatus());
    }

    @Test
    void othersCannotUseOrDeleteMyCardAndPasswordGuessingIsLimited() throws Exception {
        UserEntity owner = fixtures.user("카드주인");
        UserEntity other = fixtures.user("다른회원");
        long cardId = registeredCard(owner);
        BookingEntity othersBooking = pendingBooking(other);

        pay(other, cardId, othersBooking.getBookingId(), TestFixtures.PASSWORD).andExpect(status().isNotFound());
        mvc.perform(delete(API + "/" + cardId).header("Authorization", fixtures.bearer(other)))
            .andExpect(status().isNotFound());

        for (int i = 0; i < 5; i++) {
            pay(owner, cardId, othersBooking.getBookingId(), "Wrong-passw0rd").andExpect(status().isBadRequest());
        }
        pay(owner, cardId, othersBooking.getBookingId(), TestFixtures.PASSWORD)
            .andExpect(status().isTooManyRequests());

        mvc.perform(delete(API + "/" + cardId).header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isNoContent());
        mvc.perform(get(API).header("Authorization", fixtures.bearer(owner)))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void billingIsUnavailableWithoutIndividualIntegrationKeys() throws Exception {
        when(gateway.billingEnabled()).thenReturn(false);
        mvc.perform(post(API + "/customer").header("Authorization", fixtures.bearer(fixtures.user("위젯회원"))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("BILLING_NOT_CONFIGURED"));
    }

    private long registeredCard(UserEntity user) throws Exception {
        String customerKey = customerKey(user);
        when(gateway.issueBillingKey("auth_" + user.getUserId(), customerKey)).thenReturn(
            new TossPaymentGateway.BillingIssue(BILLING_KEY, customerKey, "KB국민", "94901234****567*"));
        String body = register(user, customerKey, "auth_" + user.getUserId()).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.billingCardId")).longValue();
    }

    private String customerKey(UserEntity user) throws Exception {
        String body = mvc.perform(post(API + "/customer").header("Authorization", fixtures.bearer(user)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.customerKey");
    }

    private BookingEntity pendingBooking(UserEntity user) {
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(10);
        return fixtures.booking(user, fixtures.room(100_000, 2, 1), checkIn, checkIn.plusDays(1),
            BookingStatus.PAYMENT_PENDING);
    }

    private ResultActions register(UserEntity user, String customerKey, String authKey) throws Exception {
        return mvc.perform(post(API).header("Authorization", fixtures.bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerKey\":\"" + customerKey + "\",\"authKey\":\"" + authKey + "\"}"));
    }

    private ResultActions pay(UserEntity user, long cardId, Long bookingId, String password) throws Exception {
        return mvc.perform(post(API + "/" + cardId + "/pay").header("Authorization", fixtures.bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bookingId\":" + bookingId + ",\"password\":\"" + password + "\"}"));
    }
}
