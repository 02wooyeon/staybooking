package com.kisia.staybooking.review;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.file.UploadResponse;

/**
 * 관리자 후기 상세. 목록({@link AdminReviewResponse})에 첨부 사진과 예약 숙박 정보를 더한다.
 * 목록과 같은 이유로 작성자 이메일·연락처 같은 개인정보는 넣지 않는다.
 */
public record AdminReviewDetailResponse(Long reviewId, Long bookingId, Long accommodationId,
                                        String accommodationName, Long userId, String author, int rating,
                                        String content, boolean deleted, String deletedReason,
                                        List<String> images, LocalDate checkInDate, LocalDate checkOutDate,
                                        BookingStatus bookingStatus, LocalDateTime createdAt,
                                        LocalDateTime updatedAt) {
    static AdminReviewDetailResponse from(ReviewEntity review) {
        BookingEntity booking = review.getBooking();
        List<String> images = review.getFiles().stream()
            .map(FileEntity::getFileId).map(UploadResponse::urlOf).toList();
        return new AdminReviewDetailResponse(review.getReviewId(), booking.getBookingId(),
            review.getAccommodation().getAccommodationId(), review.getAccommodation().getName(),
            review.getUser().getUserId(), ReviewResponse.author(review.getUser()), review.getRating(),
            review.getContent(), review.isDeleted(), review.getDeletedReason(), images,
            booking.getCheckInDate(), booking.getCheckOutDate(), booking.getStatus(),
            review.getCreatedAt(), review.getUpdatedAt());
    }
}
