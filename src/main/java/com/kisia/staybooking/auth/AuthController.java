package com.kisia.staybooking.auth;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    // Refresh Token은 JS가 읽지 못하는 HttpOnly 쿠키로만 주고받는다. 이름·경로는 화면(api.js)과 맞춘다.
    static final String REFRESH_COOKIE = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService auth;
    private final boolean secureCookie;

    public AuthController(AuthService auth,
                          @Value("${staybooking.auth.cookie-secure:true}") boolean secureCookie) {
        this.auth = auth;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/check-email")
    public EmailCheckResponse checkEmail(@RequestParam @NotBlank @Size(max = 255) String email) {
        return auth.checkEmail(email);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return auth.signup(request);
    }

    // 접속 IP는 Tomcat이 본 주소를 쓴다. X-Forwarded-For는 클라이언트가 마음대로 넣을 수 있어 믿지 않는다.
    // (앞에 Nginx를 두면 server.forward-headers-strategy 설정으로 Nginx가 넣은 값만 쓰도록 바꾼다)
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest http) {
        return withRefreshCookie(auth.login(request, http.getRemoteAddr()));
    }

    // AUTH_006 아이디(이메일) 찾기. 결과는 일부를 가린 이메일만 준다.
    @PostMapping("/find-email")
    public FindEmailResponse findEmail(@Valid @RequestBody FindEmailRequest request, HttpServletRequest http) {
        return auth.findEmails(request.name(), request.phone(), http.getRemoteAddr());
    }

    // 실패하면 쓸모없어진 쿠키도 함께 지운다.
    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        try {
            return withRefreshCookie(auth.reissue(refreshToken));
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus())
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(IssuedTokens tokens) {
        ResponseCookie cookie = baseCookie(tokens.refreshToken()).maxAge(tokens.refreshTokenTtl()).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(tokens.body());
    }

    private ResponseCookie clearCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Strict")
            .path(REFRESH_COOKIE_PATH);
    }
}
