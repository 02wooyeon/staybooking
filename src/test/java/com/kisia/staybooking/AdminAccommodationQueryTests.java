package com.kisia.staybooking;

import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.accommodation.AccommodationStatus;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.room.RoomStatus;
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
class AdminAccommodationQueryTests {
    @Autowired private MockMvc mvc;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;

    @Test
    void adminCanFindInactiveAccommodationAndAllItsRooms() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity member = users.save(new UserEntity("amember" + tag, "hash", "Member",
            "amember" + tag + "@example.test"));
        UserEntity admin = users.save(new UserEntity("aadmin" + tag, "hash", "Admin", null,
            "aadmin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        AccommodationEntity inactive = new AccommodationEntity("Admin stay " + tag + "%",
            "Seoul " + tag, LocalTime.of(15, 0), LocalTime.of(11, 0));
        inactive.update(inactive.getName(), inactive.getAddress(), inactive.getCheckInTime(),
            inactive.getCheckOutTime(), "Archived property", AccommodationStatus.INACTIVE);
        accommodations.save(inactive);
        RoomEntity inactiveRoom = new RoomEntity(inactive, "Standard", 90_000, 2, 3);
        inactiveRoom.update("Standard", 90_000, 2, 3, "Closed room", RoomStatus.INACTIVE);
        rooms.save(inactiveRoom);

        mvc.perform(get("/api/v1/admin/accommodations/search"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/accommodations/search")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/accommodations/search")
                .param("status", "INACTIVE").param("keyword", tag + "%")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].accommodationId").value(inactive.getAccommodationId()))
            .andExpect(jsonPath("$.content[0].status").value("INACTIVE"));
        mvc.perform(get("/api/v1/admin/accommodations/" + inactive.getAccommodationId())
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accommodation.accommodationId").value(inactive.getAccommodationId()))
            .andExpect(jsonPath("$.rooms[0].roomId").value(inactiveRoom.getRoomId()))
            .andExpect(jsonPath("$.rooms[0].status").value("INACTIVE"))
            .andExpect(jsonPath("$.rooms[0].totalQuantity").value(3));
        mvc.perform(get("/api/v1/accommodations/" + inactive.getAccommodationId()))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/accommodations/search").param("size", "101")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isBadRequest());
    }
}
