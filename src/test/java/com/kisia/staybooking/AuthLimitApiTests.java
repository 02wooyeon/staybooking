package com.kisia.staybooking;

import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthLimitApiTests {
    private static final String PASSWORD = "Passw0rd!";
    // 테스트마다 다른 IP를 써서 서로의 잠금에 영향을 주지 않는다.
    private static final AtomicInteger IP = new AtomicInteger(10);

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;

    @Test
    void locksEmailAfterFiveFailuresEvenWithCorrectPassword() throws Exception {
        UserEntity user = user("Lock Test", "010-1111-2222");
        String ip = nextIp();
        for (int i = 0; i < 4; i++) {
            login(user.getEmail(), "Wrong-passw0rd", ip).andExpect(status().isUnauthorized());
        }
        // 한도 전에는 맞는 비밀번호로 로그인하면 실패 기록이 지워진다.
        login(user.getEmail(), PASSWORD, ip).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            login(user.getEmail(), "Wrong-passw0rd", ip).andExpect(status().isUnauthorized());
        }
        // 다른 IP에서 맞는 비밀번호를 넣어도 이메일 기준으로 잠겨 있다.
        login(user.getEmail(), PASSWORD, nextIp()).andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    void findEmailReturnsMaskedEmailsOnlyForMatchingActiveUser() throws Exception {
        UserEntity user = user("Find Me", "010-3333-4444");
        String ip = nextIp();
        String local = user.getEmail().substring(0, user.getEmail().indexOf('@'));

        findEmail("Find Me", "01033334444", ip).andExpect(status().isOk())
            .andExpect(jsonPath("$.emails.length()").value(1))
            .andExpect(jsonPath("$.emails[0]").value(local.substring(0, 2) + "***@ex***.test"));
        findEmail("Find Me", "010-9999-9999", ip).andExpect(status().isOk())
            .andExpect(jsonPath("$.emails.length()").value(0));
        findEmail("<script>", "010-3333-4444", ip).andExpect(status().isBadRequest());
    }

    @Test
    void findEmailIsRateLimitedPerIp() throws Exception {
        String ip = nextIp();
        for (int i = 0; i < 10; i++) {
            findEmail("Nobody", "010-5555-6666", ip).andExpect(status().isOk());
        }
        findEmail("Nobody", "010-5555-6666", ip).andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        findEmail("Nobody", "010-5555-6666", nextIp()).andExpect(status().isOk());
    }

    private UserEntity user(String name, String phone) {
        String email = TestFixtures.uniqueEmail("lim");
        return users.save(new UserEntity(email, passwords.encode(PASSWORD), name, null, email, phone,
            UserRole.ROLE_USER));
    }

    private static String nextIp() {
        return "10.0.0." + IP.incrementAndGet();
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(req -> { req.setRemoteAddr(ip); return req; })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions findEmail(String name, String phone, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/find-email").with(req -> { req.setRemoteAddr(ip); return req; })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"phone\":\"" + phone + "\"}"));
    }
}
