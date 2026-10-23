package com.kisia.staybooking;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class UploadApiTests {
    private static final String URL = "/api/v1/uploads/Images";

    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;

    @Test
    void ownerCanUploadViewAndDeleteButOthersCannotSeeUnlinkedImage() throws Exception {
        UserEntity owner = fixtures.user("업로더");
        UserEntity other = fixtures.user("다른사람");
        String body = upload(owner, "room.png", png())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mimeType").value("image/png"))
            .andExpect(jsonPath("$.filePath").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");

        mvc.perform(get(url).header("Authorization", fixtures.bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        // 후기에 연결되기 전 사진은 올린 사람만 볼 수 있다.
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(url).header("Authorization", fixtures.bearer(other))).andExpect(status().isNotFound());

        mvc.perform(delete(url).header("Authorization", fixtures.bearer(other))).andExpect(status().isNotFound());
        mvc.perform(delete(url).header("Authorization", fixtures.bearer(owner))).andExpect(status().isNoContent());
        mvc.perform(get(url).header("Authorization", fixtures.bearer(owner))).andExpect(status().isNotFound());
    }

    @Test
    void rejectsWebShellTricks() throws Exception {
        UserEntity user = fixtures.user("공격자");
        byte[] jsp = "<% Runtime.getRuntime().exec(request.getParameter(\"c\")); %>".getBytes(StandardCharsets.UTF_8);

        expectInvalid(upload(user, "shell.jsp.png", png()));          // 이중 확장자
        expectInvalid(upload(user, "shell.png", jsp));                // 확장자만 바꾼 웹쉘
        expectInvalid(upload(user, "shell.jsp", png()));              // 허용하지 않는 확장자
        expectInvalid(upload(user, "photo.jpg", png()));              // 확장자와 실제 형식 불일치
        expectInvalid(upload(user, "logo.svg",
            "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)));
        expectInvalid(upload(user, "shell.png\0.jsp", png()));        // 널 바이트
        expectInvalid(upload(user, "empty.png", new byte[0]));
    }

    @Test
    void reencodingRemovesCodeHiddenAfterImageData() throws Exception {
        UserEntity user = fixtures.user("업로더");
        byte[] hidden = "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8);
        byte[] polyglot = new byte[png().length + hidden.length];
        System.arraycopy(png(), 0, polyglot, 0, png().length);
        System.arraycopy(hidden, 0, polyglot, png().length, hidden.length);

        String url = JsonPath.read(upload(user, "photo.png", polyglot).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), "$.url");
        byte[] stored = mvc.perform(get(url).header("Authorization", fixtures.bearer(user)))
            .andReturn().getResponse().getContentAsByteArray();
        assertTrue(stored.length > 0);
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("<?php"));
    }

    @Test
    void uploadRequiresLogin() throws Exception {
        mvc.perform(multipart(URL).file(new MockMultipartFile("file", "a.png", "image/png", png())))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions upload(UserEntity user, String name, byte[] bytes) throws Exception {
        return mvc.perform(multipart(URL).file(new MockMultipartFile("file", name, "image/png", bytes))
            .header("Authorization", fixtures.bearer(user)));
    }

    private static void expectInvalid(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0xFF0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
