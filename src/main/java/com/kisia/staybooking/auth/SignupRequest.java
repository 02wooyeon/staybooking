package com.kisia.staybooking.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// role, status 같은 권한 필드는 받지 않는다. (Mass Assignment 방지)
// 규칙은 화면(signup.js)과 같게 맞춘다. 이메일은 users.login_id(50자)에도 저장하므로 50자까지만 받는다.
public record SignupRequest(
    @NotBlank @Email @Size(max = 50) String email,
    @NotBlank @Pattern(regexp = PasswordRules.PATTERN) String password,
    @NotBlank String passwordConfirm,
    @NotBlank @Pattern(regexp = "^[가-힣A-Za-z ]{2,20}$") String name,
    @NotBlank @Pattern(regexp = PasswordRules.PHONE_PATTERN) String phone,
    @NotNull @AssertTrue Boolean agreeTerms,
    @NotNull @AssertTrue Boolean agreePrivacy,
    Boolean agreeMarketing
) {
}
