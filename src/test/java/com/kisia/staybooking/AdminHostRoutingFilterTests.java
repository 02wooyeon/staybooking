package com.kisia.staybooking;

import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminHostRoutingFilterTests {
    private static final String USER_SITE = "http://localhost";
    private static final String ADMIN_SITE = "http://admin.test";

    private static RequestPostProcessor rawUri(String uri) {
        return request -> {
            request.setRequestURI(uri);
            return request;
        };
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Nested
    @SpringBootTest(properties = "staybooking.admin.host=admin.test")
    @AutoConfigureMockMvc
    class Separated {
        @Autowired private MockMvc mvc;
        @Autowired private JwtTokenProvider jwt;
        @Autowired private UserRepository users;

        @Test
        void userDomainHidesOperatorApis() throws Exception {
            mvc.perform(get(USER_SITE + "/api/v1/admin/bookings/search"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            mvc.perform(get(USER_SITE + "/api/v1/admin/payments/reconciliation/search"))
                .andExpect(status().isNotFound());
            mvc.perform(get(USER_SITE + "/api/v1/approver/bookings"))
                .andExpect(status().isNotFound());
            mvc.perform(get(USER_SITE + "/api/v1/admin"))
                .andExpect(status().isNotFound());
        }

        @Test
        void userDomainKeepsOtherApis() throws Exception {
            mvc.perform(get(USER_SITE + "/api/v1/auth/check-email").param("email", "someone@example.test"))
                .andExpect(status().isOk());
        }

        @Test
        void internalAdminPathIsNeverReachableDirectly() throws Exception {
            mvc.perform(get(USER_SITE + "/__admin/index.html")).andExpect(status().isNotFound());
            mvc.perform(get(ADMIN_SITE + "/__admin/index.html")).andExpect(status().isNotFound());
        }

        @Test
        void adminDomainServesAdminPages() throws Exception {
            mvc.perform(get(ADMIN_SITE + "/"))
                .andExpect(forwardedUrl("/__admin/index.html"));
            mvc.perform(get(ADMIN_SITE + "/login.html"))
                .andExpect(forwardedUrl("/__admin/login.html"));
            mvc.perform(get(ADMIN_SITE + "/assets/js/admin.js"))
                .andExpect(forwardedUrl("/__admin/assets/js/admin.js"));
            // "/api" 로 시작하지만 API 경로가 아닌 것은 화면으로 취급한다
            mvc.perform(get(ADMIN_SITE + "/apix.html"))
                .andExpect(forwardedUrl("/__admin/apix.html"));
        }

        @Test
        void adminDomainPassesApisToSecurity() throws Exception {
            // 필터는 통과시키고, 토큰이 없으므로 Spring Security 가 401 을 준다
            mvc.perform(get(ADMIN_SITE + "/api/v1/admin/bookings/search"))
                .andExpect(status().isUnauthorized());
            mvc.perform(get(ADMIN_SITE + "/api/v1/admin/payments/reconciliation/search"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void validAdminTokenWorksOnlyOnAdminDomain() throws Exception {
            String tag = Long.toString(System.nanoTime());
            UserEntity admin = users.save(new UserEntity("host-admin-" + tag, "hash", "Admin", null,
                "host-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
            UserEntity member = users.save(new UserEntity("host-member-" + tag, "hash", "Member",
                "host-member-" + tag + "@example.test"));
            String authorization = "Bearer " + jwt.createAccessToken(admin);

            mvc.perform(get(USER_SITE + "/api/v1/admin/statistics/overview")
                    .header("Authorization", authorization))
                .andExpect(status().isNotFound());
            mvc.perform(get(ADMIN_SITE + "/api/v1/admin/statistics/overview")
                    .header("Authorization", authorization))
                .andExpect(status().isOk());
            mvc.perform(get(ADMIN_SITE + "/api/v1/admin/statistics/overview")
                    .header("Authorization", "Bearer " + jwt.createAccessToken(member)))
                .andExpect(status().isForbidden());
        }

        @Test
        void adminDomainRejectsSuspiciousPaths() throws Exception {
            mvc.perform(get(ADMIN_SITE + "/x").with(rawUri("/../application.properties")))
                .andExpect(status().isNotFound());
            mvc.perform(get(ADMIN_SITE + "/x").with(rawUri("/assets/%2e%2e/index.html")))
                .andExpect(status().isNotFound());
            mvc.perform(get(ADMIN_SITE + "/x").with(rawUri("/index.html;jsessionid=1")))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest(properties = {
        "staybooking.admin.host=admin.test",
        "staybooking.admin.allowed-ips=10.10.10.0/24"
    })
    @AutoConfigureMockMvc
    class IpRestricted {
        @Autowired private MockMvc mvc;

        @Test
        void adminDomainOnlyAcceptsAllowedIps() throws Exception {
            mvc.perform(get(ADMIN_SITE + "/").with(remoteAddr("203.0.113.7")))
                .andExpect(status().isNotFound());
            mvc.perform(get(ADMIN_SITE + "/").with(remoteAddr("10.10.10.5")))
                .andExpect(forwardedUrl("/__admin/index.html"));
        }

        @Test
        void userDomainIsNotIpRestricted() throws Exception {
            mvc.perform(get(USER_SITE + "/api/v1/auth/check-email").param("email", "someone@example.test")
                    .with(remoteAddr("203.0.113.7")))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest(properties = "staybooking.admin.host=")
    @AutoConfigureMockMvc
    class NotConfigured {
        @Autowired private MockMvc mvc;

        @Test
        void filterDoesNothingWithoutAdminHost() throws Exception {
            // 관리자 도메인을 설정하지 않으면 기존 동작 그대로 (서버 권한 확인만으로 401)
            mvc.perform(get(USER_SITE + "/api/v1/admin/bookings/search"))
                .andExpect(status().isUnauthorized());
        }
    }
}
