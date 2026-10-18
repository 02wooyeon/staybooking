package com.kisia.staybooking.auth;

import com.kisia.staybooking.user.UserRole;

public record AuthenticatedUser(Long userId, UserRole role) {
}
