package com.kisia.staybooking;

import java.time.LocalDate;

import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class BookingQuoteApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;

    @Test
    void quoteUsesServerPriceAndMatchesCreatedBooking() throws Exception {
        RoomEntity room = fixtures.room(90_000, 2, 2);
        UserEntity guest = fixtures.user("예약자");
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(10);
        // 클라이언트가 금액을 보내도 무시하고 서버 가격으로 계산한다.
        String body = body(room, checkIn, checkIn.plusDays(3), 2).replace("}", ",\"totalAmount\":1}");

        quote(guest, body).andExpect(status().isOk())
            .andExpect(jsonPath("$.nights").value(3))
            .andExpect(jsonPath("$.nightlyPrice").value(90_000))
            .andExpect(jsonPath("$.totalAmount").value(270_000))
            .andExpect(jsonPath("$.availableCount").value(2))
            .andExpect(jsonPath("$.available").value(true));
        mvc.perform(post("/api/v1/bookings").header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalAmount").value(270_000));
    }

    @Test
    void quoteShowsSoldOutWithoutFailing() throws Exception {
        RoomEntity room = fixtures.room(90_000, 2, 1);
        UserEntity guest = fixtures.user("예약자");
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(10);
        fixtures.booking(fixtures.user("먼저 예약"), room, checkIn, checkIn.plusDays(1), BookingStatus.PENDING);

        quote(guest, body(room, checkIn, checkIn.plusDays(1), 1)).andExpect(status().isOk())
            .andExpect(jsonPath("$.availableCount").value(0))
            .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void rejectsInvalidQuoteRequests() throws Exception {
        RoomEntity room = fixtures.room(90_000, 2, 1);
        UserEntity guest = fixtures.user("예약자");
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(10);

        quote(guest, body(room, checkIn, checkIn, 1)).andExpect(status().isBadRequest());
        quote(guest, body(room, checkIn, checkIn.plusDays(1), 3)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/bookings/quote").contentType(MediaType.APPLICATION_JSON)
                .content(body(room, checkIn, checkIn.plusDays(1), 1)))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions quote(UserEntity user, String body) throws Exception {
        return mvc.perform(post("/api/v1/bookings/quote").header("Authorization", fixtures.bearer(user))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(RoomEntity room, LocalDate checkIn, LocalDate checkOut, int guests) {
        return "{\"roomId\":" + room.getRoomId() + ",\"checkIn\":\"" + checkIn + "\",\"checkOut\":\"" + checkOut
            + "\",\"guests\":" + guests + "}";
    }
}
