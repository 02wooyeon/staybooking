package com.kisia.staybooking;

import com.kisia.staybooking.auth.AuthService;
import com.kisia.staybooking.auth.IssuedTokens;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.auth.LoginRequest;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminRoleAssignmentTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private AuthService auth;
    @Autowired private PasswordEncoder passwords;

    @Test
    void adminCanAssignAndRemoveApproverWithoutGrantingAdminRole() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("operator" + tag, "hash", "Admin", null,
            "operator" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        String email = "approver" + tag + "@example.test";
        UserEntity member = users.save(new UserEntity(email, passwords.encode("Passw0rd!"),
            "Member", email));
        String adminToken = jwt.createAccessToken(admin);
        IssuedTokens original = auth.login(new LoginRequest(email, "Passw0rd!"));
        String originalAccess = original.body().accessToken();

        mvc.perform(patch("/api/v1/admin/members/" + member.getUserId() + "/role")
                .header("Authorization", "Bearer " + originalAccess)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ROLE_APPROVER\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/members/" + member.getUserId() + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ROLE_ADMIN\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/members/" + admin.getUserId() + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ROLE_USER\"}"))
            .andExpect(status().isConflict());

        mvc.perform(patch("/api/v1/admin/members/" + member.getUserId() + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ROLE_APPROVER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ROLE_APPROVER"));
        assertEquals(UserRole.ROLE_APPROVER, users.findById(member.getUserId()).orElseThrow().getRole());
        mvc.perform(get("/api/v1/approver/bookings")
                .header("Authorization", "Bearer " + originalAccess))
            .andExpect(status().isUnauthorized());
        assertThrows(ApiException.class, () -> auth.reissue(original.refreshToken()));

        String approverAccess = auth.login(new LoginRequest(email, "Passw0rd!")).body().accessToken();
        mvc.perform(get("/api/v1/approver/bookings")
                .header("Authorization", "Bearer " + approverAccess))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/members/" + member.getUserId() + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ROLE_USER\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/approver/bookings")
                .header("Authorization", "Bearer " + approverAccess))
            .andExpect(status().isUnauthorized());
    }
}
