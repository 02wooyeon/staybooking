package com.kisia.staybooking.accommodation;

// mainImage는 연결된 숙소 사진의 첫 공개 URL이며, 사진이 없으면 null이다.
public record AccommodationSummary(Long accommodationId, String name, String address, String description,
                                   String mainImage, long minPrice) {
}
