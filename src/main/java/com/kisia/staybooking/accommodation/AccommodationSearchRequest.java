package com.kisia.staybooking.accommodation;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

// 값이 없으면 null로 들어오고 기본값은 AccommodationService에서 채운다.
public record AccommodationSearchRequest(
    @Size(max = 50) String keyword,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
    @Min(1) @Max(20) Integer guests,
    @Min(0) @Max(1000) Integer page,
    @Min(1) @Max(50) Integer size
) {
}
