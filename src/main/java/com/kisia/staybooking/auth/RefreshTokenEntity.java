package com.kisia.staybooking.auth;

import java.time.LocalDateTime;
import java.util.Objects;

import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// docs/architecture/DATABASE.md의 refresh_tokens. 원문 토큰은 저장하지 않고 SHA-256 해시만 저장한다.
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_user", columnList = "user_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_refresh_tokens_hash", columnNames = "token_hash")
})
public class RefreshTokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_refresh_tokens_user"))
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(UserEntity user, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.user = Objects.requireNonNull(user);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long getRefreshTokenId() { return refreshTokenId; }
    public UserEntity getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime nowUtc) {
        return !expiresAt.isAfter(nowUtc);
    }

    public void revoke(LocalDateTime nowUtc) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(nowUtc);
        }
    }
}
