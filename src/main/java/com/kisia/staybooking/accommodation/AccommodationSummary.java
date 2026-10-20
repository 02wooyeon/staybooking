package com.kisia.staybooking.accommodation;

// mainImage는 이미지 테이블이 생기기 전까지 null이다.
public record AccommodationSummary(Long accommodationId, String name, String address, String description,
                                   String mainImage, long minPrice) {
}
