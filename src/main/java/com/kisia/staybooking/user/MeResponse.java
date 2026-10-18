package com.kisia.staybooking.user;

public record MeResponse(Long userId, String email, String name, String nickname, String phone, UserRole role,
                         long mileageBalance) {
}
