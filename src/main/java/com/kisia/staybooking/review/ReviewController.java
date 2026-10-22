package com.kisia.staybooking.review;

import com.kisia.staybooking.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 주소는 API 명세서(REV-01~04)와 화면(review.js)을 따른다.
@RestController
public class ReviewController {
    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    // 비회원도 볼 수 있다. 로그인한 상태면 내 후기에 owned=true가 붙는다.
    @GetMapping("/api/v1/accommodations/{accommodationId}/reviews")
    public ReviewListResponse list(@AuthenticationPrincipal AuthenticatedUser me,
                                   @PathVariable @Positive Long accommodationId,
                                   @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return reviews.list(accommodationId, me == null ? null : me.userId(), page, size);
    }

    @PostMapping("/api/v1/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                                 @Valid @RequestBody CreateRequest request) {
        return reviews.create(me.userId(), request.accommodationId(), request.bookingId(), request.rating(),
            request.content().trim(), request.imageFileId());
    }

    @PutMapping("/api/v1/reviews/{reviewId}")
    public ReviewResponse update(@AuthenticationPrincipal AuthenticatedUser me,
                                 @PathVariable @Positive Long reviewId,
                                 @Valid @RequestBody UpdateRequest request) {
        return reviews.update(me.userId(), reviewId, request.rating(), request.content().trim(),
            request.imageFileId());
    }

    @DeleteMapping("/api/v1/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable @Positive Long reviewId) {
        reviews.delete(me.userId(), reviewId);
    }

    // accommodationId와 bookingId 중 하나는 있어야 한다. 사진은 업로드 API로 먼저 올린 파일 번호.
    public record CreateRequest(@Positive Long accommodationId, @Positive Long bookingId,
                                @Min(1) @Max(5) int rating,
                                @NotBlank @Size(max = 1000) String content,
                                @Positive Long imageFileId) { }

    public record UpdateRequest(@Min(1) @Max(5) int rating,
                                @NotBlank @Size(max = 1000) String content,
                                @Positive Long imageFileId) { }
}
