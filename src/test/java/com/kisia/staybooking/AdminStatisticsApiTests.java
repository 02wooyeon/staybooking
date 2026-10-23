package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.ZoneOffset;

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

        mvc.perform(get("/api/v1/admin/statistics/payment-summary"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/statistics/payment-summary")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/statistics/payment-summary")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.grossPaidAmount").isNumber())
            .andExpect(jsonPath("$.approvedRefundAmount").isNumber())
            .andExpect(jsonPath("$.netPaidAmount").isNumber())
            .andExpect(jsonPath("$.requestedRefundAmount").isNumber());

        String todayUtc = LocalDate.now(ZoneOffset.UTC).toString();
        mvc.perform(get("/api/v1/admin/statistics/payment-summary")
                .param("from", todayUtc).param("to", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.netPaidAmount").isNumber());
        mvc.perform(get("/api/v1/admin/statistics/payment-summary")
                .param("from", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        mvc.perform(get("/api/v1/admin/statistics/payment-summary")
                .param("from", todayUtc).param("to", LocalDate.now(ZoneOffset.UTC).minusDays(1).toString())
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        mvc.perform(get("/api/v1/admin/statistics/payment-daily")
                .param("from", todayUtc).param("to", todayUtc))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/statistics/payment-daily")
                .param("from", todayUtc).param("to", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(member)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/statistics/payment-daily")
                .param("from", todayUtc).param("to", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date").value(todayUtc))
            .andExpect(jsonPath("$[0].netPaidAmount").isNumber());
        mvc.perform(get("/api/v1/admin/statistics/payment-daily")
                .param("from", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        mvc.perform(get("/api/v1/admin/statistics/payment-daily")
                .param("from", LocalDate.now(ZoneOffset.UTC).minusDays(31).toString()).param("to", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DATE_RANGE_TOO_LARGE"));

        mvc.perform(get("/api/v1/admin/statistics/booking-daily")
                .param("from", todayUtc).param("to", todayUtc))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/statistics/booking-daily")
                .param("from", todayUtc).param("to", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(member)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/statistics/booking-daily")
                .param("from", todayUtc).param("to", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date").value(todayUtc))
            .andExpect(jsonPath("$[0].createdBookings").isNumber())
            .andExpect(jsonPath("$[0].approvedBookings").isNumber())
            .andExpect(jsonPath("$[0].cancelledBookings").isNumber());
        mvc.perform(get("/api/v1/admin/statistics/booking-daily")
                .param("from", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        mvc.perform(get("/api/v1/admin/statistics/booking-daily")
                .param("from", LocalDate.now(ZoneOffset.UTC).minusDays(31).toString()).param("to", todayUtc)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DATE_RANGE_TOO_LARGE"));
    }
}
