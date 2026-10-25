package com.kisia.staybooking;

import java.util.Set;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.review.ReviewEntity;
import com.kisia.staybooking.review.ReviewRepository;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class AdminReviewSearchApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private ReviewRepository reviews;

    @Test
    void searchFiltersByAccommodationRatingAndHiddenState() throws Exception {
        RoomEntity room = fixtures.room(100_000, 2, 1);
        Long accommodationId = room.getAccommodation().getAccommodationId();
        UserEntity guest = fixtures.user("홍길동");
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);

        ReviewEntity good = review(guest, room, 5, "조용하고 깨끗했습니다");
        ReviewEntity bad = review(guest, room, 2, "소음이 심했습니다");
        ReviewEntity hidden = review(guest, room, 1, "부적절한 내용");
        hidden.hide("욕설");
        reviews.save(hidden);

        // 관리자만 쓸 수 있다.
        mvc.perform(get("/api/v1/admin/reviews/search"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/reviews/search").header("Authorization", fixtures.bearer(guest)))
            .andExpect(status().isForbidden());

        // 숨김 여부를 비우면 숨긴 후기까지 함께 나온다.
        mvc.perform(get("/api/v1/admin/reviews/search")
                .param("accommodationId", accommodationId.toString())
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/admin/reviews/search")
                .param("accommodationId", accommodationId.toString()).param("deleted", "true")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].reviewId").value(hidden.getReviewId()))
            .andExpect(jsonPath("$.content[0].deletedReason").value("욕설"));

        // 평점 범위와 내용 검색
        mvc.perform(get("/api/v1/admin/reviews/search")
                .param("accommodationId", accommodationId.toString())
                .param("minRating", "4").param("maxRating", "5")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].reviewId").value(good.getReviewId()));
        mvc.perform(get("/api/v1/admin/reviews/search")
                .param("accommodationId", accommodationId.toString()).param("q", "소음")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].reviewId").value(bad.getReviewId()));

        // 잘못된 조건
        mvc.perform(get("/api/v1/admin/reviews/search")
                .param("minRating", "5").param("maxRating", "2")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_RATING_RANGE"));
        mvc.perform(get("/api/v1/admin/reviews/search").param("size", "101")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void detailShowsHiddenReviewWithReasonAndStayDates() throws Exception {
        RoomEntity room = fixtures.room(80_000, 2, 1);
        UserEntity guest = fixtures.user("김단비");
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        ReviewEntity review = review(guest, room, 3, "보통이었습니다");
        BookingEntity stay = review.getBooking();

        mvc.perform(get("/api/v1/admin/reviews/" + review.getReviewId())
                .header("Authorization", fixtures.bearer(guest)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reviews/" + review.getReviewId())
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewId").value(review.getReviewId()))
            .andExpect(jsonPath("$.bookingId").value(stay.getBookingId()))
            .andExpect(jsonPath("$.author").value("김*비"))
            .andExpect(jsonPath("$.deleted").value(false))
            .andExpect(jsonPath("$.images").isArray())
            .andExpect(jsonPath("$.checkInDate").value(stay.getCheckInDate().toString()))
            .andExpect(jsonPath("$.checkOutDate").value(stay.getCheckOutDate().toString()))
            // 숙박을 마친 예약이므로 DB 상태는 COMPLETED다 (fixtures가 JDBC로 바꾸어 Entity에는 반영되지 않는다).
            .andExpect(jsonPath("$.bookingStatus").value(BookingStatus.COMPLETED.name()));

        // 숨긴 뒤에도 관리자 상세에서는 사유와 함께 보인다. 공개 상세에서는 사라진다.
        review.hide("광고");
        reviews.save(review);
        mvc.perform(get("/api/v1/admin/reviews/" + review.getReviewId())
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true))
            .andExpect(jsonPath("$.deletedReason").value("광고"));
        mvc.perform(get("/api/v1/reviews/" + review.getReviewId())
                .header("Authorization", fixtures.bearer(guest)))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/admin/reviews/999999999")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isNotFound());
    }

    private ReviewEntity review(UserEntity guest, RoomEntity room, int rating, String content) {
        BookingEntity stay = fixtures.finishedStay(guest, room);
        return reviews.save(new ReviewEntity(guest, stay, rating, content, Set.of()));
    }
}
