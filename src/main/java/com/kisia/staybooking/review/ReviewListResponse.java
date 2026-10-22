package com.kisia.staybooking.review;

import java.util.List;

// 평균 별점과 개수는 서버가 계산한다. (화면이 받은 목록으로 직접 계산하지 않게)
public record ReviewListResponse(double averageRating, long reviewCount, List<ReviewResponse> reviews,
                                 int page, int size, int totalPages) {
}
