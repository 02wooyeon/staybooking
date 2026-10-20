package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccommodationApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private String tag;
    private AccommodationEntity onCheon;
    private AccommodationEntity haeundae;
    private RoomEntity cheapRoom;
    private LocalDate checkIn;

    // 테스트마다 고유 태그를 주소에 넣어 다른 테스트 데이터와 섞이지 않게 검색한다.
    @BeforeEach
    void setUp() {
        tag = "t" + System.nanoTime();
        onCheon = accommodations.save(new AccommodationEntity("Oncheon Stay " + tag,
            "부산 동래구 온천동 " + tag, LocalTime.of(15, 0), LocalTime.of(11, 0)));
        haeundae = accommodations.save(new AccommodationEntity("Haeundae Hotel " + tag,
            "부산 해운대구 우동 " + tag, LocalTime.of(15, 0), LocalTime.of(11, 0)));
        AccommodationEntity closed = accommodations.save(new AccommodationEntity("Closed " + tag,
            "부산 동래구 " + tag, LocalTime.of(15, 0), LocalTime.of(11, 0)));
        jdbc.update("update accommodations set status = 'INACTIVE' where accommodation_id = ?",
            closed.getAccommodationId());

        cheapRoom = rooms.save(new RoomEntity(onCheon, "Standard", 80_000, 2, 1));
        rooms.save(new RoomEntity(onCheon, "Family", 150_000, 4, 1));
        rooms.save(new RoomEntity(haeundae, "Ocean", 200_000, 2, 1));
        rooms.save(new RoomEntity(closed, "Any", 10_000, 2, 1));
        checkIn = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(20);
    }

    @Test
    void listIsPublicAndHidesInactiveAccommodations() throws Exception {
        mvc.perform(get("/api/v1/accommodations").param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(50));
        mvc.perform(get("/api/v1/accommodations/search").param("keyword", tag))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].name", not(hasItem("Closed " + tag))))
            .andExpect(jsonPath("$.content[0].minPrice").value(80_000));
    }

    @Test
    void keywordMatchesNameOrAddressIgnoringCase() throws Exception {
        mvc.perform(get("/api/v1/accommodations/search").param("keyword", "온천동 " + tag))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].accommodationId").value(onCheon.getAccommodationId()));
        mvc.perform(get("/api/v1/accommodations/search").param("keyword", "HAEUNDAE HOTEL " + tag))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].accommodationId").value(haeundae.getAccommodationId()));
    }

    @Test
    void keywordIsTreatedAsPlainText() throws Exception {
        mvc.perform(get("/api/v1/accommodations/search").param("keyword", tag + "%"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/accommodations/search").param("keyword", "' OR '1'='1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void guestsFilterUsesRoomCapacity() throws Exception {
        mvc.perform(get("/api/v1/accommodations/search").param("keyword", tag).param("guests", "4"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].accommodationId").value(onCheon.getAccommodationId()))
            .andExpect(jsonPath("$.content[0].minPrice").value(150_000));
    }

    @Test
    void dateSearchSkipsFullyBookedRooms() throws Exception {
        UserEntity guest = users.save(new UserEntity("g" + tag.substring(tag.length() - 12), "hash", "Guest",
            tag + "@example.test"));
        bookings.save(new BookingEntity(guest, cheapRoom, checkIn, checkIn.plusDays(2), 1, 160_000, 0,
            LocalDateTime.now(ZoneOffset.UTC).plusHours(1)));

        // 싼 객실이 찼으므로 최저가는 다음 객실 가격이 된다.
        mvc.perform(get("/api/v1/accommodations/search").param("keyword", "온천동 " + tag)
                .param("checkIn", checkIn.toString()).param("checkOut", checkIn.plusDays(1).toString()))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].minPrice").value(150_000));
        // 2인이면서 그날 비어 있는 객실이 없는 숙소는 빠진다.
        mvc.perform(get("/api/v1/accommodations/search").param("keyword", "온천동 " + tag)
                .param("checkIn", checkIn.toString()).param("checkOut", checkIn.plusDays(1).toString())
                .param("guests", "2"))
            .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/accommodations/search").param("keyword", "온천동 " + tag)
                .param("checkIn", checkIn.plusDays(2).toString())
                .param("checkOut", checkIn.plusDays(3).toString()))
            .andExpect(jsonPath("$.content[0].minPrice").value(80_000));

        mvc.perform(get("/api/v1/accommodations/" + onCheon.getAccommodationId())
                .param("checkIn", checkIn.toString()).param("checkOut", checkIn.plusDays(1).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rooms[0].name").value("Standard"))
            .andExpect(jsonPath("$.rooms[0].availableCount").value(0))
            .andExpect(jsonPath("$.rooms[0].status").value("SOLD_OUT"))
            .andExpect(jsonPath("$.rooms[1].availableCount").value(1))
            .andExpect(jsonPath("$.rooms[1].status").value("AVAILABLE"));
    }

    @Test
    void rejectsInvalidSearchInput() throws Exception {
        LocalDate past = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        mvc.perform(get("/api/v1/accommodations/search")
                .param("checkIn", checkIn.toString()).param("checkOut", checkIn.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(get("/api/v1/accommodations/search").param("checkIn", checkIn.toString()))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accommodations/search")
                .param("checkIn", past.toString()).param("checkOut", checkIn.toString()))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accommodations/search").param("checkIn", "2026/12/01")
                .param("checkOut", "2026-12-02"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accommodations").param("size", "1000"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields[0]").value("size"));
        mvc.perform(get("/api/v1/accommodations").param("page", "abc"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accommodations/search").param("guests", "0"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void detailShowsRoomsAndHidesMissingOrInactive() throws Exception {
        mvc.perform(get("/api/v1/accommodations/" + onCheon.getAccommodationId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Oncheon Stay " + tag))
            .andExpect(jsonPath("$.rooms.length()").value(2))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.amenities.length()").value(0))
            .andExpect(jsonPath("$.rooms[0].price").value(80_000))
            .andExpect(jsonPath("$.rooms[0].maxGuests").value(2))
            .andExpect(jsonPath("$.rooms[0].availableCount").value(1))
            .andExpect(jsonPath("$.rooms[0].status").value("AVAILABLE"));

        mvc.perform(get("/api/v1/accommodations/99999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/accommodations/abc"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accommodations/-1"))
            .andExpect(status().isBadRequest());
    }
}
