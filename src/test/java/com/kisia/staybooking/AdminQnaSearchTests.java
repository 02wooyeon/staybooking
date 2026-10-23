package com.kisia.staybooking;

import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.qna.QnaAnswerEntity;
import com.kisia.staybooking.qna.QnaAnswerRepository;
import com.kisia.staybooking.qna.QnaEntity;
import com.kisia.staybooking.qna.QnaRepository;
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
class AdminQnaSearchTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private QnaRepository questions;
    @Autowired private QnaAnswerRepository answers;
    @Autowired private JwtTokenProvider jwt;

    @Test
    void filtersAnsweredHiddenAndAuthorWhileKeepingAdminAccess() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity member = users.save(new UserEntity("qmember" + tag, "hash", "Member",
            "qmember" + tag + "@example.test"));
        UserEntity admin = users.save(new UserEntity("qadmin" + tag, "hash", "Admin", null,
            "qadmin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        QnaEntity unanswered = questions.save(new QnaEntity(member, null, "booking",
            "Unique pending " + tag, "Help", true));
        QnaEntity answered = questions.save(new QnaEntity(member, null, "booking",
            "Unique answered " + tag, "Help", false));
        answers.save(new QnaAnswerEntity(answered, admin, "Resolved"));
        QnaEntity hidden = new QnaEntity(member, null, "booking", "Unique hidden " + tag,
            "Help", false);
        hidden.hide("Duplicate");
        questions.save(hidden);

        mvc.perform(get("/api/v1/admin/qna/search"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/qna/search")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/qna/search")
                .param("userId", member.getUserId().toString())
                .param("answered", "false").param("deleted", "false")
                .param("q", "PENDING " + tag)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].qnaId").value(unanswered.getQnaId()))
            .andExpect(jsonPath("$.content[0].answered").value(false));
        mvc.perform(get("/api/v1/admin/qna/search")
                .param("userId", member.getUserId().toString())
                .param("answered", "true").param("deleted", "false")
                .param("q", tag)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].qnaId").value(answered.getQnaId()));
        mvc.perform(get("/api/v1/admin/qna/search")
                .param("userId", member.getUserId().toString())
                .param("deleted", "true").param("q", tag)
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].deleted").value(true));
        mvc.perform(get("/api/v1/admin/qna/search")
                .param("size", "101")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest());
    }
}
