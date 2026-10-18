package com.kisia.staybooking.auth;

// Refresh Token은 본문에 넣지 않고 HttpOnly 쿠키로만 내려준다.
public record TokenResponse(String tokenType, String accessToken, long expiresIn) {
}
