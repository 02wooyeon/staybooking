package com.kisia.staybooking.auth;

import com.kisia.staybooking.user.UserRole;

public record AuthenticatedUser(Long userId, UserRole role, long tokenVersion) {
    public AuthenticatedUser(Long userId, UserRole role) {
        this(userId, role, 0);
    }
}
