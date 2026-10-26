package com.kisia.staybooking.accommodation;

// mainImage는 숙소 대표 사진 주소이고, 사진이 없으면 null이다.
public record AccommodationSummary(Long accommodationId, String name, String address, String description,
                                   String mainImage, long minPrice) {
}
