package com.kisia.staybooking.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 회원가입과 같은 이름·휴대폰 규칙
public record FindEmailRequest(
    @NotBlank @Pattern(regexp = "^[가-힣A-Za-z ]{2,20}$") String name,
    @NotBlank @Pattern(regexp = PasswordRules.PHONE_PATTERN) String phone
) {
}
