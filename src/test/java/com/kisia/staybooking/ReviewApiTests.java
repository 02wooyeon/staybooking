package com.kisia.staybooking;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class ReviewApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;

    @Test
    void onlyGuestsWhoFinishedTheirStayCanReview() throws Exception {
        RoomEntity room = fixtures.room(100_000, 2, 1);
        Long accommodationId = room.getAccommodation().getAccommodationId();
        UserEntity guest = fixtures.user("홍길동");
        LocalDate today = LocalDate.now(TestFixtures.KOREA);

        // 예약이 없거나, 승인됐지만 아직 숙박 전이거나, 승인 전이면 쓸 수 없다.
        create(guest, accommodationId, null, 5).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
        fixtures.booking(guest, room, today.plusDays(3), today.plusDays(4), BookingStatus.COMPLETED);
        fixtures.booking(guest, room, today.minusDays(5), today.minusDays(4), BookingStatus.PENDING);
        create(guest, accommodationId, null, 5).andExpect(status().isForbidden());

        BookingEntity stay = fixtures.finishedStay(guest, room);
        create(guest, accommodationId, null, 4).andExpect(status().isCreated())
            .andExpect(jsonPath("$.author").value("홍*동"))
            .andExpect(jsonPath("$.owned").value(true));
        // 같은 예약으로 두 번 쓸 수 없다.
        create(guest, null, stay.getBookingId(), 5).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_REVIEW"));
        create(guest, accommodationId, null, 5).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/accommodations/" + accommodationId + "/reviews"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewCount").value(1))
            .andExpect(jsonPath("$.averageRating").value(4.0))
            .andExpect(jsonPath("$.reviews[0].owned").value(false))
            .andExpect(jsonPath("$.reviews[0].content").value("깨끗하고 좋았어요"));
    }

    @Test
    void cannotReviewWithSomeoneElsesBooking() throws Exception {
        RoomEntity room = fixtures.room(100_000, 2, 1);
        UserEntity owner = fixtures.user("예약자");
        UserEntity attacker = fixtures.user("공격자");
        BookingEntity stay = fixtures.finishedStay(owner, room);

        create(attacker, null, stay.getBookingId(), 1).andExpect(status().isForbidden());
    }

    @Test
    void onlyWriterCanEditOrDeleteAndDeletedReviewDisappears() throws Exception {
        RoomEntity room = fixtures.room(100_000, 2, 1);
        Long accommodationId = room.getAccommodation().getAccommodationId();
        UserEntity writer = fixtures.user("작성자");
        UserEntity other = fixtures.user("다른사람");
        fixtures.finishedStay(writer, room);
        Integer reviewId = JsonPath.read(create(writer, accommodationId, null, 3)
            .andReturn().getResponse().getContentAsString(), "$.reviewId");

        String edit = "{\"rating\":5,\"content\":\"다시 생각해도 좋았어요\"}";
        mvc.perform(put("/api/v1/reviews/" + reviewId).header("Authorization", fixtures.bearer(other))
                .contentType(MediaType.APPLICATION_JSON).content(edit))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/reviews/" + reviewId).header("Authorization", fixtures.bearer(other)))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/reviews/" + reviewId).header("Authorization", fixtures.bearer(writer))
                .contentType(MediaType.APPLICATION_JSON).content(edit))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").value(5));

        mvc.perform(delete("/api/v1/reviews/" + reviewId).header("Authorization", fixtures.bearer(writer)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/accommodations/" + accommodationId + "/reviews"))
            .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void reviewPhotoBecomesPublicOnlyWhenLinkedAndCannotUseOthersFile() throws Exception {
        RoomEntity room = fixtures.room(100_000, 2, 1);
        Long accommodationId = room.getAccommodation().getAccommodationId();
        UserEntity writer = fixtures.user("작성자");
        UserEntity other = fixtures.user("다른사람");
        fixtures.finishedStay(writer, room);
        fixtures.finishedStay(other, room);
        Integer fileId = JsonPath.read(mvc.perform(multipart("/api/v1/uploads/Images")
                .file(new MockMultipartFile("file", "room.png", "image/png", UploadApiTests.png()))
                .header("Authorization", fixtures.bearer(writer)))
            .andReturn().getResponse().getContentAsString(), "$.fileId");

        // 남의 사진 번호로는 후기를 쓸 수 없다.
        mvc.perform(post("/api/v1/reviews").header("Authorization", fixtures.bearer(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accommodationId, null, 5).replace("}", ",\"imageFileId\":" + fileId + "}")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/uploads/Images/" + fileId)).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/reviews").header("Authorization", fixtures.bearer(writer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(accommodationId, null, 5).replace("}", ",\"imageFileId\":" + fileId + "}")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.image").value("/api/v1/uploads/Images/" + fileId));
        mvc.perform(get("/api/v1/uploads/Images/" + fileId)).andExpect(status().isOk());
        // 후기에 연결된 사진은 업로드 API로 바로 지울 수 없다.
        mvc.perform(delete("/api/v1/uploads/Images/" + fileId).header("Authorization", fixtures.bearer(writer)))
            .andExpect(status().isConflict());
    }

    @Test
    void onlyAdminCanHideReviewsWithReason() throws Exception {
        RoomEntity room = fixtures.room(100_000, 2, 1);
        Long accommodationId = room.getAccommodation().getAccommodationId();
        UserEntity writer = fixtures.user("작성자");
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        fixtures.finishedStay(writer, room);
        Integer reviewId = JsonPath.read(create(writer, accommodationId, null, 1)
            .andReturn().getResponse().getContentAsString(), "$.reviewId");

        mvc.perform(delete("/api/v1/admin/reviews/" + reviewId).param("reason", "욕설")
                .header("Authorization", fixtures.bearer(writer)))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/reviews/" + reviewId).header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/reviews/" + reviewId).param("reason", "욕설")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true))
            .andExpect(jsonPath("$.deletedReason").value("욕설"));
        mvc.perform(get("/api/v1/accommodations/" + accommodationId + "/reviews"))
            .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void rejectsInvalidReviewInput() throws Exception {
        UserEntity user = fixtures.user("작성자");
        mvc.perform(post("/api/v1/reviews").header("Authorization", fixtures.bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accommodationId\":1,\"rating\":6,\"content\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(body(1L, null, 5)))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accommodations/99999999/reviews")).andExpect(status().isNotFound());
    }

    private ResultActions create(UserEntity user, Long accommodationId, Long bookingId, int rating)
            throws Exception {
        return mvc.perform(post("/api/v1/reviews").header("Authorization", fixtures.bearer(user))
            .contentType(MediaType.APPLICATION_JSON).content(body(accommodationId, bookingId, rating)));
    }

    private static String body(Long accommodationId, Long bookingId, int rating) {
        return "{\"accommodationId\":" + accommodationId + ",\"bookingId\":" + bookingId
            + ",\"rating\":" + rating + ",\"content\":\"깨끗하고 좋았어요\"}";
    }
}
