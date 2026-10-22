package com.kisia.staybooking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QnaSecurityTests {
    @Autowired private MockMvc mvc;

    @Test
    void publicListIsAnonymousButMineAndAdminRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/qna/public")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/qna/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/qna")).andExpect(status().isUnauthorized());
    }
}
