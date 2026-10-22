package com.kisia.staybooking.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MemberSearchRequest(
    @Positive Long userId,
    UserRole role,
    UserStatus status,
    @Size(max = 255) String q,
    @Min(0) @Max(1000) Integer page,
    @Min(1) @Max(100) Integer size
) { }
