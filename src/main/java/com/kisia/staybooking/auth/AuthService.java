package com.kisia.staybooking.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String TOKEN_TYPE = "Bearer";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwt;
    private final Duration refreshTokenTtl;
    private final AttemptLimiter limiter;
    private final int maxFailuresPerEmail;
    private final int maxFailuresPerIp;
    private final SecureRandom random = new SecureRandom();
    // 없는 이메일로 로그인할 때도 비밀번호 비교 시간을 들여 계정 존재 여부가 응답 시간으로 드러나지 않게 한다.
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtTokenProvider jwt,
                       @Value("${staybooking.jwt.refresh-token-ttl:P14D}") Duration refreshTokenTtl,
                       AttemptLimiter limiter,
                       @Value("${staybooking.auth.max-login-failures-per-email:5}") int maxFailuresPerEmail,
                       @Value("${staybooking.auth.max-login-failures-per-ip:20}") int maxFailuresPerIp) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.refreshTokenTtl = refreshTokenTtl;
        this.limiter = limiter;
        this.maxFailuresPerEmail = maxFailuresPerEmail;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    // SIGNUP_005. 화면의 중복 확인 버튼용이며, 실제 중복은 가입할 때 다시 검사한다.
    @Transactional(readOnly = true)
    public EmailCheckResponse checkEmail(String email) {
        return new EmailCheckResponse(!users.existsByEmail(normalizeEmail(email)));
    }

    // 별도 아이디를 받지 않으므로 users.login_id에도 이메일을 넣는다.
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        List<String> invalid = new ArrayList<>();
        if (PasswordRules.containsEmailId(email, request.password())) {
            invalid.add("password");
        }
        if (!request.password().equals(request.passwordConfirm())) {
            invalid.add("passwordConfirm");
        }
        if (!invalid.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다.", invalid);
        }
        if (users.existsByEmail(email) || users.existsByLoginId(email)) {
            throw duplicateEmail();
        }
        UserEntity user = new UserEntity(email, passwordEncoder.encode(request.password()),
            request.name().trim(), null, email, request.phone().trim());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입한 경우
            throw duplicateEmail();
        }
        return new SignupResponse(user.getUserId(), user.getEmail());
    }

    @Transactional
    public IssuedTokens login(LoginRequest request) {
        return login(request, null);
    }

    // 같은 이메일로 여러 번 틀리거나(비밀번호 대입), 같은 IP에서 여러 계정을 돌아가며 틀리면 잠시 막는다.
    // 없는 이메일이어도 똑같이 세고 잠가서, 잠김 여부로 가입 여부를 알 수 없게 한다.
    @Transactional
    public IssuedTokens login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        String emailKey = "login-email:" + email;
        String ipKey = clientIp == null ? null : "login-ip:" + clientIp;
        if (limiter.isLocked(emailKey) || (ipKey != null && limiter.isLocked(ipKey))) {
            throw loginLocked();
        }
        Optional<UserEntity> found = users.findByEmail(email);
        String hash = found.map(UserEntity::getPasswordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !matches || found.get().getStatus() != UserStatus.ACTIVE) {
            limiter.recordFailure(emailKey, maxFailuresPerEmail);
            if (ipKey != null) limiter.recordFailure(ipKey, maxFailuresPerIp);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED",
                "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        limiter.reset(emailKey);
        return issueTokens(found.get());
    }

    // Refresh Token Rotation: 쓴 토큰은 폐기하고 새 토큰을 준다.
    // 이미 폐기된 토큰이 다시 오면 탈취로 보고 그 회원의 모든 토큰을 폐기한다.
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedTokens reissue(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        RefreshTokenEntity token = refreshTokens.findByTokenHashForUpdate(hash(rawRefreshToken))
            .orElseThrow(AuthService::invalidRefreshToken);
        UserEntity user = token.getUser();
        if (token.isRevoked()) {
            refreshTokens.revokeAllByUserId(user.getUserId(), nowUtc);
            throw invalidRefreshToken();
        }
        token.revoke(nowUtc);
        if (token.isExpired(nowUtc) || user.getStatus() != UserStatus.ACTIVE) {
            throw invalidRefreshToken();
        }
        return issueTokens(user);
    }

    // 토큰 존재 여부를 알려주지 않도록 결과와 상관없이 성공으로 응답한다.
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHashForUpdate(hash(rawRefreshToken))
            .ifPresent(token -> token.revoke(LocalDateTime.now(ZoneOffset.UTC)));
    }

    private IssuedTokens issueTokens(UserEntity user) {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        refreshTokens.save(new RefreshTokenEntity(user, hash(rawRefreshToken),
            nowUtc.plus(refreshTokenTtl), nowUtc));
        TokenResponse body = new TokenResponse(TOKEN_TYPE, jwt.createAccessToken(user),
            jwt.accessTokenTtlSeconds());
        return new IssuedTokens(body, rawRefreshToken, refreshTokenTtl);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // AUTH_006. 이름과 휴대폰 번호가 모두 맞는 정상 계정의 이메일을 일부 가려서 준다.
    // 같은 IP에서 짧은 시간에 여러 번 조회하면(남의 정보로 계정을 알아내려는 시도) 잠시 막는다.
    @Transactional(readOnly = true)
    public FindEmailResponse findEmails(String name, String phone, String clientIp) {
        String ipKey = "find-email-ip:" + clientIp;
        if (limiter.isLocked(ipKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "조회 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        limiter.recordFailure(ipKey, maxFailuresPerEmail * 2);
        String digits = phone.replaceAll("\\D", "");
        List<String> emails = users.findByNameAndStatus(name.trim(), UserStatus.ACTIVE).stream()
            .filter(user -> user.getPhone() != null && user.getPhone().replaceAll("\\D", "").equals(digits))
            .map(user -> maskEmail(user.getEmail()))
            .toList();
        return new FindEmailResponse(emails);
    }

    // guest@stay.test → gu***@st***.test
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String host = dot > 0 ? domain.substring(0, dot) : domain;
        String tld = dot > 0 ? domain.substring(dot) : "";
        return keepTwo(local) + "@" + keepTwo(host) + tld;
    }

    private static String keepTwo(String value) {
        return value.substring(0, Math.min(2, value.length())) + "***";
    }

    // 화면(login.js)이 LOGIN_LOCKED를 경고로 보여 준다.
    private static ApiException loginLocked() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
            "로그인 시도가 너무 많습니다. 15분 후 다시 시도해 주세요.");
    }

    private static ApiException duplicateEmail() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다.");
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "다시 로그인해 주세요.");
    }
}
