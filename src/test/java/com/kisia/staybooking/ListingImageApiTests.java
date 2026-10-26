package com.kisia.staybooking;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.auth.AttemptLimiter;
import com.kisia.staybooking.room.RoomEntity;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class ListingImageApiTests {
    private static final String IMAGES = "/api/v1/uploads/Images/";

    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private AttemptLimiter limiter;

    @Test
    void adminAttachesAccommodationImagesShownPubliclyAndDetachDeletesFile() throws Exception {
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        RoomEntity room = fixtures.room(80_000, 2, 1);
        Long accommodationId = room.getAccommodation().getAccommodationId();
        long first = upload(admin);
        long second = upload(admin);

        attach(admin, accommodationId, first).andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].fileId").value(first));
        attach(admin, accommodationId, second).andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(2));

        // 사용자 화면: 상세는 순서대로 전부(첫 장이 대표), 비회원도 사진을 볼 수 있다.
        mvc.perform(get("/api/v1/accommodations/" + accommodationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.images[0]").value(IMAGES + first))
            .andExpect(jsonPath("$.images[1]").value(IMAGES + second));
        mvc.perform(get(IMAGES + first)).andExpect(status().isOk());
        // 관리자 상세에는 지울 때 쓸 fileId가 함께 온다.
        mvc.perform(get("/api/v1/admin/accommodations/" + accommodationId)
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(jsonPath("$.images[1].fileId").value(second));
        // 엔티티를 그대로 돌려주는 관리자 API에 사진(업로더 정보)이 섞여 나가지 않는다.
        mvc.perform(post("/api/v1/admin/accommodations/" + accommodationId + "/rooms")
                .header("Authorization", fixtures.bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Deluxe\",\"price\":1000,\"capacity\":2,\"totalQuantity\":1,\"status\":\"ACTIVE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.images").doesNotExist());

        // 같은 사진을 두 곳에 붙일 수 없다.
        attach(admin, accommodationId, first).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FILE_IN_USE"));
        // 숙소에 붙은 사진은 업로드 API로 지울 수 없고, 숙소에서 빼면 파일도 지워진다.
        mvc.perform(delete(IMAGES + first).header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/accommodations/" + accommodationId + "/images/" + first)
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isNoContent());
        mvc.perform(get(IMAGES + first).header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accommodations/" + accommodationId))
            .andExpect(jsonPath("$.images.length()").value(1));
    }

    @Test
    void roomImagesStayInsideTheirAccommodationAndShowOnDetail() throws Exception {
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        RoomEntity room = fixtures.room(90_000, 2, 1);
        RoomEntity otherRoom = fixtures.room(90_000, 2, 1);
        Long accommodationId = room.getAccommodation().getAccommodationId();
        long fileId = upload(admin);

        // 다른 숙소의 객실 번호로는 붙일 수 없다.
        attachToRoom(admin, accommodationId, otherRoom.getRoomId(), fileId).andExpect(status().isNotFound());
        attachToRoom(admin, accommodationId, room.getRoomId(), fileId).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/accommodations/" + accommodationId))
            .andExpect(jsonPath("$.rooms[0].image").value(IMAGES + fileId));
        mvc.perform(get("/api/v1/admin/accommodations/" + accommodationId)
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(jsonPath("$.rooms[0].images[0].fileId").value(fileId));
    }

    @Test
    void onlyAdminsCanAttachAndOnlyTheirOwnUploads() throws Exception {
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        UserEntity user = fixtures.user("회원");
        Long accommodationId = fixtures.room(70_000, 2, 1).getAccommodation().getAccommodationId();
        long usersFile = upload(user);

        attach(user, accommodationId, usersFile).andExpect(status().isForbidden());
        // 회원이 올린 비공개 사진을 관리자가 숙소에 붙여 공개로 바꿀 수 없다.
        attach(admin, accommodationId, usersFile).andExpect(status().isNotFound());
        mvc.perform(get(IMAGES + usersFile)).andExpect(status().isNotFound());
    }

    @Test
    void uploadsAreRateLimitedPerAccount() throws Exception {
        UserEntity user = fixtures.user("업로더");
        // 테스트 설정은 한도를 크게 두므로, 한도에 닿은 상태를 직접 만든다.
        limiter.recordFailure("upload:" + user.getUserId(), 1);
        mvc.perform(multipart("/api/v1/uploads/Images")
                .file(new MockMultipartFile("file", "a.png", "image/png", UploadApiTests.png()))
                .header("Authorization", fixtures.bearer(user)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_UPLOADS"));
        // 다른 계정에는 영향이 없다.
        upload(fixtures.user("다른업로더"));
    }

    private ResultActions attach(UserEntity actor, Long accommodationId, long fileId) throws Exception {
        return mvc.perform(post("/api/v1/admin/accommodations/" + accommodationId + "/images")
            .header("Authorization", fixtures.bearer(actor)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fileId\":" + fileId + "}"));
    }

    private ResultActions attachToRoom(UserEntity actor, Long accommodationId, Long roomId, long fileId)
            throws Exception {
        return mvc.perform(post("/api/v1/admin/accommodations/" + accommodationId + "/rooms/" + roomId + "/images")
            .header("Authorization", fixtures.bearer(actor)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fileId\":" + fileId + "}"));
    }

    private long upload(UserEntity user) throws Exception {
        String body = mvc.perform(multipart("/api/v1/uploads/Images")
                .file(new MockMultipartFile("file", "room.png", "image/png", UploadApiTests.png()))
                .header("Authorization", fixtures.bearer(user)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.fileId")).longValue();
    }
}
