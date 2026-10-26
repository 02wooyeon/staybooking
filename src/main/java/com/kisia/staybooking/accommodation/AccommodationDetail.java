package com.kisia.staybooking.accommodation;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

// 필드 이름은 화면(accommodation-detail.js)과 맞춘다.
// amenities, rules 는 관리자 숙소 등록·수정 화면에서 저장한 값이다. images는 사진 주소 목록이고 첫 장이 대표 사진이다.
public record AccommodationDetail(
    Long accommodationId,
    String name,
    String address,
    String description,
    LocalTime checkInTime,
    LocalTime checkOutTime,
    BigDecimal latitude,
    BigDecimal longitude,
    AccommodationStatus status,
    List<String> amenities,
    List<String> rules,
    List<String> images,
    List<RoomSummary> rooms
) {
    // status는 availableCount가 0이면 SOLD_OUT, 아니면 AVAILABLE
    public record RoomSummary(Long roomId, String name, String description, String image, long price,
                              int maxGuests, int availableCount, String status) {
    }
}
