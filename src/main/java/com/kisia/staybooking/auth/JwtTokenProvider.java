package com.kisia.staybooking.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String ISSUER = "staybooking";
    private static final String ROLE_CLAIM = "role";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtTokenProvider(@Value("${staybooking.jwt.secret:}") String secret,
                            @Value("${staybooking.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl) {
        this.key = Keys.hmacShaKeyFor(secretBytes(secret));
        this.accessTokenTtl = accessTokenTtl;
    }

    // 비밀키가 없으면 실행할 때마다 임의 키를 만든다. 재시작하면 기존 Access Token은 모두 무효가 된다.
    private static byte[] secretBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("STAYBOOKING_JWT_SECRET is not set; using a random key for this run");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        return bytes;
    }

    public String createAccessToken(UserEntity user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(ISSUER)
            .subject(String.valueOf(user.getUserId()))
            .claim(ROLE_CLAIM, user.getRole().name())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtl)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    // 서명·만료·발급자가 맞지 않으면 JwtException을 던진다.
    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .requireIssuer(ISSUER)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        try {
            Long userId = Long.valueOf(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
            return new AuthenticatedUser(userId, role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("Invalid token claims", e);
        }
    }
}
