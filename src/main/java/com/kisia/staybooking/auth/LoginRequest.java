package com.kisia.staybooking.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 255) String email,
    @NotBlank @Size(max = 100) String password
) {
}
