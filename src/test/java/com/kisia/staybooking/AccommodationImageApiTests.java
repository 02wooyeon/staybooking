package com.kisia.staybooking;

import java.time.LocalTime;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.accommodation.AccommodationStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
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
class AccommodationImageApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;

    @Test
    void administratorAttachesImageAndOnlyActiveAccommodationMakesItPublic() throws Exception {
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        UserEntity member = fixtures.user("회원");
        AccommodationEntity accommodation = accommodations.save(new AccommodationEntity(
            "Image stay " + System.nanoTime(), "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        rooms.save(new RoomEntity(accommodation, "Standard", 100_000, 2, 1));
        String root = "/api/v1/admin/accommodations/" + accommodation.getAccommodationId();
        String upload = mvc.perform(multipart("/api/v1/uploads/Images")
                .file(new MockMultipartFile("file", "stay.png", "image/png", UploadApiTests.png()))
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(upload, "$.fileId");
        String imageUrl = JsonPath.read(upload, "$.url");
        mvc.perform(get(imageUrl)).andExpect(status().isNotFound());

        String request = "{\"fileId\":" + id.longValue() + "}";
        mvc.perform(post(root + "/images").contentType(MediaType.APPLICATION_JSON).content(request)
                .header("Authorization", fixtures.bearer(member)))
            .andExpect(status().isForbidden());
        mvc.perform(post(root + "/images").contentType(MediaType.APPLICATION_JSON).content(request)
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").value(imageUrl));
        mvc.perform(post(root + "/images").contentType(MediaType.APPLICATION_JSON).content(request)
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isCreated());
        mvc.perform(get(root).header("Authorization", fixtures.bearer(admin)))
            .andExpect(jsonPath("$.images.length()").value(1))
            .andExpect(jsonPath("$.images[0].url").value(imageUrl));
        mvc.perform(get("/api/v1/accommodations/" + accommodation.getAccommodationId()))
            .andExpect(jsonPath("$.images[0]").value(imageUrl));
        mvc.perform(get("/api/v1/accommodations/search")
                .param("keyword", accommodation.getName()))
            .andExpect(jsonPath("$.content[0].mainImage").value(imageUrl));
        mvc.perform(get(imageUrl)).andExpect(status().isOk());
        mvc.perform(delete(imageUrl).header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isConflict());

        accommodation.update(accommodation.getName(), accommodation.getAddress(),
            accommodation.getCheckInTime(), accommodation.getCheckOutTime(),
            accommodation.getDescription(), AccommodationStatus.INACTIVE);
        accommodations.save(accommodation);
        mvc.perform(get(imageUrl)).andExpect(status().isNotFound());
        mvc.perform(get(imageUrl).header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk());

        mvc.perform(delete(root + "/images/" + id.longValue())
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isNoContent());
        mvc.perform(get(imageUrl)).andExpect(status().isNotFound());
    }
}
