package com.kisia.staybooking.user;

import java.time.LocalDate;

// createdAt은 화면(설정 > 내 정보)의 가입일 표시용으로 한국 날짜를 준다.
public record MeResponse(Long userId, String email, String name, String nickname, String phone, UserRole role,
                         long mileageBalance, LocalDate createdAt) {
}
