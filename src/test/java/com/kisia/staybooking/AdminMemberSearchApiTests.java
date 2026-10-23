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
class AdminMemberSearchApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;

    @Test
    void onlyAdministratorCanSearchMembersWithoutExposingPasswordHashes() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity member = users.save(new UserEntity("searchmember" + tag, "hash", "Member",
            "searchmember" + tag + "@example.test"));
        UserEntity admin = users.save(new UserEntity("searchadmin" + tag, "hash", "Admin", null,
            "searchadmin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));

        mvc.perform(get("/api/v1/admin/members/search"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/members/search")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/members/search")
                .param("q", member.getEmail()).param("role", "ROLE_USER")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].loginId").value(member.getLoginId()))
            .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.content[0].tokenVersion").doesNotExist());
        mvc.perform(get("/api/v1/admin/members/search")
                .param("size", "101")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest());
    }
}
