package com.kisia.staybooking;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class QnaImageApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;

    @Test
    void publicQnaImageIsVisibleToEveryoneAndCannotBeReusedOrDeleted() throws Exception {
        UserEntity owner = fixtures.user("문의자");
        long fileId = upload(owner);
        String url = "/api/v1/uploads/Images/" + fileId;

        createQna(owner, false, fileId).andExpect(status().isCreated())
            .andExpect(jsonPath("$.image").value(url));
        mvc.perform(get(url)).andExpect(status().isOk());

        // 한 사진은 한 글에만 붙인다.
        createQna(owner, false, fileId).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FILE_IN_USE"));
        mvc.perform(delete(url).header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isConflict());
    }

    @Test
    void secretQnaImageIsOnlyForAuthorAndAdmin() throws Exception {
        UserEntity owner = fixtures.user("비밀문의");
        UserEntity other = fixtures.user("다른사람");
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        long fileId = upload(owner);
        String url = "/api/v1/uploads/Images/" + fileId;

        createQna(owner, true, fileId).andExpect(status().isCreated());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(url).header("Authorization", fixtures.bearer(other))).andExpect(status().isNotFound());
        mvc.perform(get(url).header("Authorization", fixtures.bearer(owner))).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization", fixtures.bearer(admin))).andExpect(status().isOk());
    }

    @Test
    void cannotAttachSomeoneElsesImageAndCanRemoveImageOnEdit() throws Exception {
        UserEntity owner = fixtures.user("작성자");
        UserEntity other = fixtures.user("남의사진");
        long othersFile = upload(other);
        createQna(owner, false, othersFile).andExpect(status().isNotFound());

        long fileId = upload(owner);
        String body = createQna(owner, false, fileId).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Number qnaId = JsonPath.read(body, "$.qnaId");
        mvc.perform(put("/api/v1/qna/" + qnaId).header("Authorization", fixtures.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(qnaJson(false, null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.image").doesNotExist());
        // 글에서 빼면 다시 올린 사람만 볼 수 있고 지울 수 있다.
        mvc.perform(get("/api/v1/uploads/Images/" + fileId)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/uploads/Images/" + fileId).header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isNoContent());
    }

    private long upload(UserEntity user) throws Exception {
        String body = mvc.perform(multipart("/api/v1/uploads/Images")
                .file(new MockMultipartFile("file", "q.png", "image/png", UploadApiTests.png()))
                .header("Authorization", fixtures.bearer(user)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.fileId")).longValue();
    }

    private ResultActions createQna(UserEntity user, boolean secret, Long fileId) throws Exception {
        return mvc.perform(post("/api/v1/qna").header("Authorization", fixtures.bearer(user))
            .contentType(MediaType.APPLICATION_JSON).content(qnaJson(secret, fileId)));
    }

    private static String qnaJson(boolean secret, Long fileId) {
        return "{\"category\":\"etc\",\"title\":\"사진 문의\",\"content\":\"사진을 첨부한 문의 내용입니다.\","
            + "\"secret\":" + secret + ",\"imageFileId\":" + fileId + "}";
    }
}
