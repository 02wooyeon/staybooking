package com.kisia.staybooking.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Access Token은 Authorization 헤더로만 받으므로 세션을 쓰지 않는다.
    // 쿠키를 쓰는 reissue·logout은 Refresh Token 쿠키의 SameSite=Strict로 다른 사이트 요청(CSRF)을 막는다.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwt,
                                                   UserRepository users) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login",
                    "/api/v1/auth/reissue", "/api/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/check-email").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/accommodations/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/qna/mine").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/qna/public", "/api/v1/qna/{qnaId}").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/approver/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers("/api/**").authenticated()
                // 화면(static의 html·js·css)은 누구나 받을 수 있고, 로그인 확인은 각 화면이 API로 한다.
                .anyRequest().permitAll())
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, e) -> writeError(response,
                    HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."))
                .accessDeniedHandler((request, response, e) -> writeError(response,
                    HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.")))
            .addFilterBefore(new JwtAuthenticationFilter(jwt, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"fields\":[]}");
    }
}
