package com.kisia.staybooking;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests {
    private static final String PASSWORD = "Passw0rd!";
    private static final String REFRESH_COOKIE = "refreshToken";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenProvider jwt;

    @Test
    void signupStoresBcryptHashAndUsesEmailAsLoginId() throws Exception {
        String email = newEmail();
        signup(email).andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(content().string(not(containsString(PASSWORD))));

        UserEntity saved = users.findByEmail(email).orElseThrow();
        assertEquals(email, saved.getLoginId());
        assertEquals(UserRole.ROLE_USER, saved.getRole());
        assertTrue(saved.getPasswordHash().startsWith("$2"));
    }

    @Test
    void ignoresRoleFieldInSignupRequest() throws Exception {
        String email = newEmail();
        String json = signupJson(email, PASSWORD, PASSWORD)
            .replace("\"agreeMarketing\"", "\"role\":\"ROLE_ADMIN\",\"agreeMarketing\"");
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated());

        assertEquals(UserRole.ROLE_USER, users.findByEmail(email).orElseThrow().getRole());
    }

    @Test
    void checkEmailAndDuplicateSignup() throws Exception {
        String email = newEmail();
        mvc.perform(get("/api/v1/auth/check-email").param("email", email))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true));
        signup(email).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/auth/check-email").param("email", email.toUpperCase()))
            .andExpect(jsonPath("$.available").value(false));
        signup(email.toUpperCase())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void invalidSignupInputListsOnlyFieldNames() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"not-an-email","password":"weakpass","passwordConfirm":"x","name":"<script>",
                 "phone":"123","agreeTerms":false,"agreePrivacy":true}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.fields", containsInAnyOrder("agreeTerms", "email", "name", "password", "phone")));
    }

    @Test
    void rejectsPasswordContainingEmailIdOrMismatchedConfirm() throws Exception {
        String email = newEmail();
        // 이메일 아이디(@ 앞부분)가 그대로 들어간 비밀번호
        String withEmailId = "A" + email.substring(0, email.indexOf('@')) + "1!";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(signupJson(email, withEmailId, withEmailId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields", containsInAnyOrder("password")));
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(signupJson(email, PASSWORD, PASSWORD + "x")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields", containsInAnyOrder("passwordConfirm")));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetSameResponse() throws Exception {
        String email = newEmail();
        signup(email).andExpect(status().isCreated());

        String wrongPassword = login(email, "Wrong0rd!").andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String unknown = login(newEmail(), PASSWORD).andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        assertEquals(wrongPassword, unknown);
    }

    @Test
    void suspendedUserCannotLogin() throws Exception {
        String email = newEmail();
        signup(email).andExpect(status().isCreated());
        jdbc.update("update users set status = ? where email = ?", UserStatus.SUSPENDED.name(), email);

        login(email, PASSWORD).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void suspensionRevokesTokensAndReactivationDoesNotReviveOldAccessToken() throws Exception {
        MvcResult session = signupAndLogin();
        String access = JsonPath.read(session.getResponse().getContentAsString(), "$.accessToken");
        String refresh = session.getResponse().getCookie(REFRESH_COOKIE).getValue();
        Long userId = jwt.parseAccessToken(access).userId();
        String email = users.findById(userId).orElseThrow().getEmail();
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("admin" + tag, "hash", "Admin", null,
            "admin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        String adminAccess = jwt.createAccessToken(admin);

        mvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/members/" + userId + "/status")
                .header("Authorization", "Bearer " + adminAccess)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isUnauthorized());
        reissue(refresh).andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/admin/members/" + userId + "/status")
                .header("Authorization", "Bearer " + adminAccess)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isUnauthorized());
        reissue(refresh).andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void accessTokenRoleMustMatchCurrentAccountRole() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity admin = users.save(new UserEntity("roleadmin" + tag, "hash", "Admin", null,
            "roleadmin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        String token = jwt.createAccessToken(admin);
        mvc.perform(get("/api/v1/admin/statistics/overview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        jdbc.update("update users set role = ? where user_id = ?", UserRole.ROLE_USER.name(), admin.getUserId());
        mvc.perform(get("/api/v1/admin/statistics/overview").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginPutsRefreshTokenOnlyInHttpOnlyStrictCookie() throws Exception {
        MvcResult result = signupAndLogin();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("refreshToken"));
        assertEquals("Bearer", JsonPath.read(body, "$.tokenType"));
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
        assertEquals("/api/v1/auth", cookie.getPath());
        assertTrue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE).contains("SameSite=Strict"));
    }

    @Test
    void meReturnsOwnInfoFromAccessToken() throws Exception {
        String email = newEmail();
        signup(email).andExpect(status().isCreated());
        String accessToken = JsonPath.read(login(email, PASSWORD).andReturn().getResponse().getContentAsString(),
            "$.accessToken");

        mvc.perform(get("/api/v1/members/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.name").value("홍길동"))
            .andExpect(jsonPath("$.role").value("ROLE_USER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void roleRulesApplyAndStaticPagesArePublic() throws Exception {
        String accessToken = JsonPath.read(signupAndLogin().getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/approver/bookings").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isForbidden());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/auth/login.html")).andExpect(status().isOk());
    }

    @Test
    void tamperedAccessTokenIsRejected() throws Exception {
        String accessToken = JsonPath.read(signupAndLogin().getResponse().getContentAsString(), "$.accessToken");
        String[] parts = accessToken.split("\\.");
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        // 서명은 그대로 두고 role 클레임만 ADMIN으로 바꾼 토큰
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
            .replace("ROLE_USER", "ROLE_ADMIN");
        String forged = parts[0] + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
            + "." + parts[2];
        // 서명 없이 alg=none으로 바꾼 토큰
        String unsigned = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
            + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".";

        mvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer " + forged))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer " + unsigned))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reissueRotatesCookieAndReuseRevokesWholeFamily() throws Exception {
        String first = signupAndLogin().getResponse().getCookie(REFRESH_COOKIE).getValue();

        MvcResult reissued = reissue(first).andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();
        String second = reissued.getResponse().getCookie(REFRESH_COOKIE).getValue();
        assertNotEquals(first, second);

        // 이미 쓴 토큰이 다시 오면 거절하고 쿠키를 지우며, 새로 받은 토큰까지 함께 폐기한다.
        reissue(first).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        reissue(second).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/reissue")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        String refreshToken = signupAndLogin().getResponse().getCookie(REFRESH_COOKIE).getValue();

        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(REFRESH_COOKIE, refreshToken)))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        reissue(refreshToken).andExpect(status().isUnauthorized());
        // 쿠키가 없거나 모르는 토큰이어도 같은 응답
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(REFRESH_COOKIE, "unknown")))
            .andExpect(status().isNoContent());
    }

    private MvcResult signupAndLogin() throws Exception {
        String email = newEmail();
        signup(email).andExpect(status().isCreated());
        return login(email, PASSWORD).andExpect(status().isOk()).andReturn();
    }

    private ResultActions signup(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content(signupJson(email, PASSWORD, PASSWORD)));
    }

    private static String signupJson(String email, String password, String confirm) {
        return """
            {"email":"%s","password":"%s","passwordConfirm":"%s","name":"홍길동","phone":"010-1234-5678",
             "agreeTerms":true,"agreePrivacy":true,"agreeMarketing":false}
            """.formatted(email, password, confirm);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions reissue(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/reissue").cookie(new Cookie(REFRESH_COOKIE, refreshToken)));
    }

    private static String newEmail() {
        return "user" + System.nanoTime() % 1_000_000 + "x" + SEQ.incrementAndGet() + "@example.test";
    }
}
