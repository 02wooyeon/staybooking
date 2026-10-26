package com.kisia.staybooking;

import java.time.LocalTime;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class AdminAccommodationValidationTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;

    @Test
    void accommodationMutationsRequireAdministratorRole() throws Exception {
        UserEntity member = fixtures.user("회원");
        UserEntity approver = fixtures.user("승인자", UserRole.ROLE_APPROVER);
        String body = "{\"name\":\"Authorized stay\",\"address\":\"Seoul\","
            + "\"checkInTime\":\"15:00\",\"checkOutTime\":\"11:00\",\"status\":\"ACTIVE\"}";
        mvc.perform(post("/api/v1/admin/accommodations")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/accommodations")
                .header("Authorization", fixtures.bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/accommodations")
                .header("Authorization", fixtures.bearer(approver))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateAndUpdateAccommodationAndRoomThroughHttp() throws Exception {
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        String bearer = fixtures.bearer(admin);
        String tag = Long.toString(System.nanoTime());
        String accommodationBody = "{\"name\":\"New stay " + tag + "\",\"address\":\"Seoul\","
            + "\"checkInTime\":\"15:00\",\"checkOutTime\":\"11:00\","
            + "\"description\":\"Welcome\",\"status\":\"ACTIVE\","
            + "\"amenities\":[\"무료 Wi-Fi\"],\"rules\":[\"금연\"]}";
        String created = mvc.perform(post("/api/v1/admin/accommodations").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(accommodationBody))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("New stay " + tag))
            .andReturn().getResponse().getContentAsString();
        Number accommodationId = JsonPath.read(created, "$.accommodationId");
        String base = "/api/v1/admin/accommodations/" + accommodationId.longValue();
        String createdRoom = mvc.perform(post(base + "/rooms").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("Standard")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Standard"))
            .andReturn().getResponse().getContentAsString();
        Number roomId = JsonPath.read(createdRoom, "$.roomId");

        mvc.perform(patch(base).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(accommodationBody.replace("Welcome", "Updated")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Updated"));
        mvc.perform(patch(base + "/rooms/" + roomId.longValue()).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("Deluxe")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Deluxe"));
        mvc.perform(get(base).header("Authorization", bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Updated"))
            .andExpect(jsonPath("$.amenities[0]").value("무료 Wi-Fi"))
            .andExpect(jsonPath("$.rooms[0].name").value("Deluxe"));
    }

    @Test
    void deactivatedRoomAndAccommodationDisappearFromPublicDetailButRemainAdminVisible() throws Exception {
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        String bearer = fixtures.bearer(admin);
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Status stay " + System.nanoTime(),
            "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 1));
        String base = "/api/v1/admin/accommodations/" + stay.getAccommodationId();
        String publicUrl = "/api/v1/accommodations/" + stay.getAccommodationId();

        mvc.perform(delete(base + "/rooms/" + room.getRoomId()).header("Authorization", bearer))
            .andExpect(status().isNoContent());
        mvc.perform(get(publicUrl)).andExpect(status().isOk())
            .andExpect(jsonPath("$.rooms.length()").value(0));
        mvc.perform(get(base).header("Authorization", bearer)).andExpect(status().isOk())
            .andExpect(jsonPath("$.rooms[0].status").value("INACTIVE"));
        mvc.perform(patch(base + "/rooms/" + room.getRoomId()).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("Standard")))
            .andExpect(status().isOk());
        mvc.perform(get(publicUrl)).andExpect(jsonPath("$.rooms.length()").value(1));

        mvc.perform(delete(base).header("Authorization", bearer)).andExpect(status().isNoContent());
        mvc.perform(get(publicUrl)).andExpect(status().isNotFound());
        mvc.perform(get(base).header("Authorization", bearer)).andExpect(status().isOk())
            .andExpect(jsonPath("$.accommodation.status").value("INACTIVE"));
    }

    @Test
    void missingAccommodationAndWrongRoomReturnNotFound() throws Exception {
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        String bearer = fixtures.bearer(admin);
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Stay " + System.nanoTime(),
            "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        AccommodationEntity other = accommodations.save(new AccommodationEntity("Other " + System.nanoTime(),
            "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 1));
        String accommodationBody = "{\"name\":\"Stay\",\"address\":\"Seoul\","
            + "\"checkInTime\":\"15:00\",\"checkOutTime\":\"11:00\",\"status\":\"ACTIVE\"}";
        String roomBody = roomBody("Standard");

        mvc.perform(patch("/api/v1/admin/accommodations/999999999")
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(accommodationBody))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/v1/admin/accommodations/999999999/rooms")
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(roomBody))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/accommodations/999999999")
                .header("Authorization", bearer))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/accommodations/" + other.getAccommodationId()
                + "/rooms/" + room.getRoomId())
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(roomBody))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/accommodations/" + other.getAccommodationId()
                + "/rooms/" + room.getRoomId()).header("Authorization", bearer))
            .andExpect(status().isNotFound());
    }

    @Test
    void duplicateRoomNameAndOversizedFieldsAreRejectedBeforeDatabaseWrites() throws Exception {
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        String bearer = fixtures.bearer(admin);
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Stay " + System.nanoTime(),
            "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity existing = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 1));
        RoomEntity other = rooms.save(new RoomEntity(stay, "Suite", 120_000, 3, 1));
        String base = "/api/v1/admin/accommodations/" + stay.getAccommodationId() + "/rooms";

        mvc.perform(post(base).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("Standard")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ROOM_NAME"));
        mvc.perform(patch(base + "/" + other.getRoomId()).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("Standard")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.fields[0]").value("name"));
        mvc.perform(post(base).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("x".repeat(101))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("name"));
        mvc.perform(post("/api/v1/admin/accommodations").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + "x".repeat(151)
                    + "\",\"address\":\"Seoul\",\"checkInTime\":\"15:00\","
                    + "\"checkOutTime\":\"11:00\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("name"));
        mvc.perform(post(base).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("Large")
                    .replace("\"status\":\"ACTIVE\"", "\"description\":\"" + "x".repeat(10001)
                        + "\",\"status\":\"ACTIVE\"")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("description"));
        mvc.perform(patch(base + "/" + existing.getRoomId()).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("Standard")))
            .andExpect(status().isOk());
    }

    private static String roomBody(String name) {
        return "{\"name\":\"" + name + "\",\"price\":100000,\"capacity\":2,"
            + "\"totalQuantity\":1,\"status\":\"ACTIVE\"}";
    }
}
