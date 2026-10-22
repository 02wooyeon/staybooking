package com.kisia.staybooking;

import com.kisia.staybooking.auth.JwtTokenProvider;
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

@SpringBootTest
@AutoConfigureMockMvc
class AdminStatisticsApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;

    @Test
    void overviewIsAvailableOnlyToAdministrator() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity member = users.save(new UserEntity("statsmember" + tag, "hash", "Member",
            "statsmember" + tag + "@example.test"));
        UserEntity admin = users.save(new UserEntity("statsadmin" + tag, "hash", "Admin", null,
            "statsadmin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));

        mvc.perform(get("/api/v1/admin/statistics/overview"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/statistics/overview")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/statistics/overview")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeMembers").isNumber())
            .andExpect(jsonPath("$.unansweredQuestions").isNumber());
    }
}
