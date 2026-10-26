package com.kisia.staybooking;

import java.time.LocalTime;

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
        mvc.perform(patch(base + "/" + existing.getRoomId()).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("Standard")))
            .andExpect(status().isOk());
    }

    private static String roomBody(String name) {
        return "{\"name\":\"" + name + "\",\"price\":100000,\"capacity\":2,"
            + "\"totalQuantity\":1,\"status\":\"ACTIVE\"}";
    }
}
