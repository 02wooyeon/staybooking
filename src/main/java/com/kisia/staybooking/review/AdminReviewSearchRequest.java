package com.kisia.staybooking.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 관리자 후기 검색 조건. deleted 를 비우면 공개·숨김 후기를 모두 조회한다.
public record AdminReviewSearchRequest(
    @Positive Long reviewId,
    @Positive Long userId,
    @Positive Long accommodationId,
    @Positive Long bookingId,
    Boolean deleted,
    @Min(1) @Max(5) Integer minRating,
    @Min(1) @Max(5) Integer maxRating,
    @Size(max = 200) String q,
    @Min(0) @Max(1000) Integer page,
    @Min(1) @Max(100) Integer size
) { }
