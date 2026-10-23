package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.cancellation.CancellationRequestRepository;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
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
class CancellationRequestHistoryApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private CancellationRequestRepository requests;

    @Test
    void onlyBookingOwnerCanReadCancellationDecisionHistory() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity owner = users.save(new UserEntity("historyowner" + tag, "hash", "Owner",
            "historyowner" + tag + "@example.test"));
        UserEntity stranger = users.save(new UserEntity("historyother" + tag, "hash", "Other",
            "historyother" + tag + "@example.test"));
        UserEntity admin = users.save(new UserEntity("historyadmin" + tag, "hash", "Admin", null,
            "historyadmin" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 1));
        LocalDate checkIn = LocalDate.now(ZoneOffset.UTC).plusDays(5);
        BookingEntity booking = bookings.save(new BookingEntity(owner, room, checkIn,
            checkIn.plusDays(1), 1, 100_000, 0, LocalDateTime.now(ZoneOffset.UTC).plusDays(1)));
        CancellationRequestEntity request = new CancellationRequestEntity(booking, owner,
            CancellationReasonType.USER_FAULT, "Change of plans");
        request.reject(admin, LocalDateTime.now(ZoneOffset.UTC), "Policy exception");
        requests.save(request);
        String path = "/api/v1/bookings/" + booking.getBookingId() + "/cancellation-requests";

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + jwt.createAccessToken(stranger)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        mvc.perform(get(path).header("Authorization", "Bearer " + jwt.createAccessToken(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("REJECTED"))
            .andExpect(jsonPath("$[0].reasonType").value("USER_FAULT"))
            .andExpect(jsonPath("$[0].reasonDetail").value("Change of plans"))
            .andExpect(jsonPath("$[0].decisionReason").value("Policy exception"))
            .andExpect(jsonPath("$[0].requestedAt").isNotEmpty())
            .andExpect(jsonPath("$[0].decidedAt").isNotEmpty());
    }
}
