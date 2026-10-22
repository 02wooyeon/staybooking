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
class AdminBookingSearchApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;

    @Test
    void searchRequiresAdministratorAndValidPageParameters() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity member = users.save(new UserEntity("bookingmember" + tag, "hash", "Member",
            "bookingmember" + tag + "@example.test"));
        UserEntity admin = users.save(new UserEntity("bookingadmin" + tag, "hash", "Admin", null,
            "bookingadmin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));

        mvc.perform(get("/api/v1/admin/bookings/search"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/bookings/search")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/bookings/search")
                .param("page", "0").param("size", "2")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2));
        mvc.perform(get("/api/v1/admin/bookings/search")
                .param("size", "101")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/bookings/search")
                .param("bookingId", "-1")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest());
    }
}
