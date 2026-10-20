package com.kisia.staybooking.auth;

import java.time.Duration;

public record IssuedTokens(TokenResponse body, String refreshToken, Duration refreshTokenTtl) {
}
