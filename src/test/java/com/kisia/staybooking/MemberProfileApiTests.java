package com.kisia.staybooking;

import java.time.LocalDate;

import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class MemberProfileApiTests {
    private static final String NEW_PASSWORD = "N3w-passw0rd";

    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private UserRepository users;

    @Test
    void updatesOnlyAllowedFieldsAndIgnoresRole() throws Exception {
        UserEntity user = fixtures.user("홍길동");
        mvc.perform(patch("/api/v1/members/me").header("Authorization", fixtures.bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"여행자\",\"phone\":\"010-9999-8888\",\"role\":\"ROLE_ADMIN\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("여행자"))
            .andExpect(jsonPath("$.phone").value("010-9999-8888"))
            .andExpect(jsonPath("$.role").value("ROLE_USER"));

        mvc.perform(patch("/api/v1/members/me").header("Authorization", fixtures.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"<script>\",\"phone\":\"123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void passwordChangeNeedsCurrentPasswordAndLogsOutOldTokens() throws Exception {
        UserEntity user = fixtures.user("홍길동");
        String oldToken = fixtures.bearer(user);

        changePassword(oldToken, "Wrong-passw0rd", NEW_PASSWORD).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        changePassword(oldToken, TestFixtures.PASSWORD, "weak").andExpect(status().isBadRequest());
        changePassword(oldToken, TestFixtures.PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        // 바꾸기 전에 받은 토큰은 더 이상 쓸 수 없다.
        mvc.perform(get("/api/v1/members/me").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        login(user.getEmail(), TestFixtures.PASSWORD).andExpect(status().isUnauthorized());
        login(user.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void withdrawalRemovesPersonalInfoAndBlocksLogin() throws Exception {
        UserEntity user = fixtures.user("홍길동");
        String token = fixtures.bearer(user);

        withdraw(token, "Wrong-passw0rd", true).andExpect(status().isBadRequest());
        withdraw(token, TestFixtures.PASSWORD, false).andExpect(status().isBadRequest());
        withdraw(token, TestFixtures.PASSWORD, true).andExpect(status().isNoContent());

        UserEntity saved = users.findById(user.getUserId()).orElseThrow();
        assertEquals(UserStatus.WITHDRAWN, saved.getStatus());
        assertEquals("탈퇴회원", saved.getName());
        assertNull(saved.getPhone());
        assertFalse(saved.getEmail().equals(user.getEmail()));
        mvc.perform(get("/api/v1/members/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        login(user.getEmail(), TestFixtures.PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void cannotWithdrawWithUpcomingBookingOrAsAdmin() throws Exception {
        RoomEntity room = fixtures.room(100_000, 2, 1);
        UserEntity guest = fixtures.user("예약자");
        LocalDate today = LocalDate.now(TestFixtures.KOREA);
        fixtures.booking(guest, room, today.plusDays(5), today.plusDays(6), BookingStatus.COMPLETED);
        withdraw(fixtures.bearer(guest), TestFixtures.PASSWORD, true).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("WITHDRAW_NOT_ALLOWED"));

        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        withdraw(fixtures.bearer(admin), TestFixtures.PASSWORD, true).andExpect(status().isConflict());
    }

    @Test
    void activitiesShowOnlyMyReviewsAndQuestions() throws Exception {
        RoomEntity room = fixtures.room(100_000, 2, 1);
        UserEntity me = fixtures.user("작성자");
        UserEntity other = fixtures.user("다른사람");
        fixtures.finishedStay(me, room);
        fixtures.finishedStay(other, room);
        Long accommodationId = room.getAccommodation().getAccommodationId();
        for (UserEntity writer : new UserEntity[] {me, other}) {
            mvc.perform(post("/api/v1/reviews").header("Authorization", fixtures.bearer(writer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accommodationId\":" + accommodationId + ",\"rating\":5,\"content\":\"좋아요\"}"))
                .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/qna").header("Authorization", fixtures.bearer(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"etc\",\"title\":\"문의합니다\",\"content\":\"체크인 시간을 알고 싶어요\",\"secret\":true}"))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/members/me/activities").header("Authorization", fixtures.bearer(me)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviews.length()").value(1))
            .andExpect(jsonPath("$.reviews[0].owned").value(true))
            .andExpect(jsonPath("$.qna.length()").value(1));
        mvc.perform(get("/api/v1/members/me/activities")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String token, String current,
                                                                             String next) throws Exception {
        return mvc.perform(patch("/api/v1/members/me").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions withdraw(String token, String password, boolean agree)
            throws Exception {
        return mvc.perform(delete("/api/v1/members/me").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"" + password + "\",\"agree\":" + agree + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
