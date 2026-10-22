package com.kisia.staybooking.accommodation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AdminAccommodationSearchRequest(
    AccommodationStatus status,
    @Size(max = 150) String keyword,
    @Min(0) @Max(1000) Integer page,
    @Min(1) @Max(100) Integer size
) { }
