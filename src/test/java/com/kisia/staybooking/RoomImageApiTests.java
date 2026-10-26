package com.kisia.staybooking;

import java.time.LocalTime;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.room.RoomStatus;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class RoomImageApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;

    @Test
    void adminCanAttachRoomImageAndInactiveRoomDoesNotExposeIt() throws Exception {
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        UserEntity member = fixtures.user("회원");
        AccommodationEntity accommodation = accommodations.save(new AccommodationEntity(
            "Room image stay " + System.nanoTime(), "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        AccommodationEntity other = accommodations.save(new AccommodationEntity(
            "Other stay " + System.nanoTime(), "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(accommodation, "Standard", 100_000, 2, 1));
        String root = "/api/v1/admin/accommodations/" + accommodation.getAccommodationId();
        String roomImages = root + "/rooms/" + room.getRoomId() + "/images";
        String upload = mvc.perform(multipart("/api/v1/uploads/Images")
                .file(new MockMultipartFile("file", "room.png", "image/png", UploadApiTests.png()))
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(upload, "$.fileId");
        String imageUrl = JsonPath.read(upload, "$.url");
        String body = "{\"fileId\":" + id.longValue() + "}";

        mvc.perform(post(roomImages).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", fixtures.bearer(member)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/accommodations/" + other.getAccommodationId()
                + "/rooms/" + room.getRoomId() + "/images")
                .contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isNotFound());
        mvc.perform(post(roomImages).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").value(imageUrl));
        mvc.perform(get(root).header("Authorization", fixtures.bearer(admin)))
            .andExpect(jsonPath("$.rooms[0].images[0].url").value(imageUrl));
        mvc.perform(get("/api/v1/accommodations/" + accommodation.getAccommodationId()))
            .andExpect(jsonPath("$.rooms[0].image").value(imageUrl));
        mvc.perform(get(imageUrl)).andExpect(status().isOk());
        mvc.perform(delete(imageUrl).header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isConflict());

        room.update(room.getName(), room.getPrice(), room.getCapacity(), room.getTotalQuantity(),
            room.getDescription(), RoomStatus.INACTIVE);
        rooms.save(room);
        mvc.perform(get(imageUrl)).andExpect(status().isNotFound());
        mvc.perform(delete(roomImages + "/" + id.longValue())
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isNoContent());
        mvc.perform(get(root).header("Authorization", fixtures.bearer(admin)))
            .andExpect(jsonPath("$.rooms[0].images.length()").value(0));
    }
}
