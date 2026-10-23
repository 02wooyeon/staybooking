package com.kisia.staybooking.review;

import java.time.LocalDateTime;

// 관리자 화면용. 숨긴 후기와 사유도 함께 보여 준다. 작성자 이메일 등 개인정보는 넣지 않는다.
public record AdminReviewResponse(Long reviewId, Long bookingId, Long accommodationId, String accommodationName,
                                  Long userId, String author, int rating, String content, boolean deleted,
                                  String deletedReason, LocalDateTime createdAt) {
    static AdminReviewResponse from(ReviewEntity review) {
        return new AdminReviewResponse(review.getReviewId(), review.getBooking().getBookingId(),
            review.getAccommodation().getAccommodationId(), review.getAccommodation().getName(),
            review.getUser().getUserId(), ReviewResponse.author(review.getUser()), review.getRating(),
            review.getContent(), review.isDeleted(), review.getDeletedReason(), review.getCreatedAt());
    }
}
