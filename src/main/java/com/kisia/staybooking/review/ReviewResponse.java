package com.kisia.staybooking.review;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.file.UploadResponse;
import com.kisia.staybooking.user.UserEntity;

// 필드 이름은 화면(review.js)과 맞춘다. 작성자는 닉네임이 없으면 이름 가운데를 가려서 보여 준다.
public record ReviewResponse(Long reviewId, Long accommodationId, String accommodationName, String author,
                             int rating, String content, String image, LocalDate createdAt,
                             LocalDate updatedAt, boolean owned) {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    static ReviewResponse from(ReviewEntity review, Long actorId) {
        String image = review.getFiles().stream().findFirst()
            .map(FileEntity::getFileId).map(UploadResponse::urlOf).orElse(null);
        LocalDateTime created = review.getCreatedAt();
        LocalDateTime updated = review.getUpdatedAt();
        boolean edited = created != null && updated != null && updated.isAfter(created.plusSeconds(1));
        return new ReviewResponse(review.getReviewId(), review.getAccommodation().getAccommodationId(),
            review.getAccommodation().getName(), author(review.getUser()), review.getRating(),
            review.getContent(), image, koreaDate(created), edited ? koreaDate(updated) : null,
            actorId != null && review.getUser().getUserId().equals(actorId));
    }

    static String author(UserEntity user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname();
        String name = user.getName();
        if (name.length() <= 1) return "*";
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    private static LocalDate koreaDate(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA).toLocalDate();
    }
}
