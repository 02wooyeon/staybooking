package com.kisia.staybooking.auth;

import java.util.Locale;

// 회원가입과 비밀번호 변경이 같은 규칙을 쓰도록 모아 둔다. (화면 signup.js의 정규식과 같다)
public final class PasswordRules {
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,20}$";
    public static final String PHONE_PATTERN = "^01[016789]-?\\d{3,4}-?\\d{4}$";
    private static final int MIN_EMAIL_ID_IN_PASSWORD = 4;

    private PasswordRules() {
    }

    // 이메일 아이디(@ 앞부분, 4자 이상)가 비밀번호에 들어 있으면 안 된다.
    public static boolean containsEmailId(String email, String password) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String emailId = normalized.substring(0, Math.max(normalized.indexOf('@'), 0));
        return emailId.length() >= MIN_EMAIL_ID_IN_PASSWORD
            && password.toLowerCase(Locale.ROOT).contains(emailId);
    }
}
