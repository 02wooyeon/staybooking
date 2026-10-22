package com.kisia.staybooking;

import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "staybooking.payment.demo-enabled=false")
@AutoConfigureMockMvc
class PaymentApiSafetyTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;

    @Test
    void paymentCompletionIsUnavailableWithoutGatewayOrLocalDemo() throws Exception {
        UserEntity user = users.save(new UserEntity("payment-safety", "hash", "Guest",
            "payment-safety@example.test"));
        String token = "Bearer " + jwt.createAccessToken(user);

        mvc.perform(get("/api/v1/payments/capabilities").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.demoEnabled").value(false));
        mvc.perform(post("/api/v1/payments/demo-complete").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_CONFIGURED"));
        mvc.perform(post("/api/v1/payments/complete").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"ORDER-X\",\"paymentKey\":\"FAKE\",\"amount\":1}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_CONFIGURED"));
    }
}
