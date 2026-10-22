package com.kisia.staybooking.common;

import java.io.IOException;
import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserStatus;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// 유효한 Bearer 토큰이면 인증 정보를 채운다. 토큰이 없거나 잘못되면 그대로 넘기고,
// 인증이 필요한 경로라면 SecurityConfig의 401 응답이 나간다.
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwt;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtTokenProvider jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                AuthenticatedUser user = jwt.parseAccessToken(header.substring(BEARER_PREFIX.length()));
                users.findById(user.userId())
                    .filter(current -> current.getStatus() == UserStatus.ACTIVE
                        && current.getRole() == user.role()
                        && current.getTokenVersion() == user.tokenVersion())
                    .ifPresent(current -> {
                        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                            user, null, List.of(new SimpleGrantedAuthority(current.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
