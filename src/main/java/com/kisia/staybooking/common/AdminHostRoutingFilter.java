package com.kisia.staybooking.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 접속한 도메인(Host)으로 일반 화면과 관리자 화면을 나눈다. Nginx 없이 같은 WAS에서 도메인을 분리하기 위한 필터다.
 *
 * <ul>
 *   <li>관리자 도메인: /api/** 는 그대로 통과, 그 밖의 요청은 classpath:/admin-static/ 의 화면으로 보낸다.
 *       허용 IP 목록이 있으면 목록 밖의 접속은 막는다.</li>
 *   <li>그 밖의 도메인: 관리자·승인자 API(/api/v1/admin/**, /api/v1/approver/**)를 막는다.</li>
 *   <li>관리자 화면의 내부 경로(/__admin/**)로 직접 들어오는 요청은 도메인과 관계없이 막는다.</li>
 * </ul>
 *
 * 차단은 404로 응답해 관리자 기능의 존재를 드러내지 않는다.
 * Host 헤더는 클라이언트가 바꿔 보낼 수 있으므로 이 필터는 노출을 줄이는 장치이고,
 * 실제 권한 확인은 SecurityConfig(ROLE_ADMIN / ROLE_APPROVER)가 한다.
 * 관리자 도메인을 설정하지 않으면 이 필터는 아무것도 하지 않는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // Spring Security 필터보다 먼저 실행한다
public class AdminHostRoutingFilter extends OncePerRequestFilter {
    /** 관리자 화면 파일을 내보내는 내부 경로. AdminWebConfig 의 리소스 핸들러와 맞춘다. */
    static final String INTERNAL_PREFIX = "/__admin";

    private static final Logger log = LoggerFactory.getLogger(AdminHostRoutingFilter.class);
    private static final List<String> OPERATOR_API_PREFIXES = List.of("/api/v1/admin", "/api/v1/approver");

    private final String adminHost;
    private final List<IpAddressMatcher> allowedIps;

    public AdminHostRoutingFilter(@Value("${staybooking.admin.host:}") String adminHost,
                                  @Value("${staybooking.admin.allowed-ips:}") String allowedIps) {
        this.adminHost = adminHost.trim().toLowerCase(Locale.ROOT);
        this.allowedIps = Arrays.stream(allowedIps.split(","))
            .map(String::trim)
            .filter(ip -> !ip.isEmpty())
            .map(IpAddressMatcher::new) // 단일 IP 또는 CIDR(예: 10.10.10.0/24)
            .toList();
        if (this.adminHost.isEmpty()) {
            log.warn("staybooking.admin.host is not set; admin domain separation is disabled");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (adminHost.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        String rawPath = request.getRequestURI().substring(request.getContextPath().length());
        String path = rawPath.isEmpty() ? "/" : rawPath;

        if (startsWithSegment(path, INTERNAL_PREFIX)) {
            notFound(response);
            return;
        }

        boolean onAdminHost = adminHost.equals(request.getServerName().toLowerCase(Locale.ROOT));
        if (!onAdminHost) {
            if (OPERATOR_API_PREFIXES.stream().anyMatch(prefix -> startsWithSegment(path, prefix))) {
                notFound(response);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if (!allowedIps.isEmpty()
                && allowedIps.stream().noneMatch(matcher -> matcher.matches(request.getRemoteAddr()))) {
            log.warn("Blocked admin domain access from {}", request.getRemoteAddr());
            notFound(response);
            return;
        }

        if (startsWithSegment(path, "/api")) {
            chain.doFilter(request, response);
            return;
        }

        // 관리자 화면 파일은 영문 경로만 쓰므로 인코딩·상위 경로·경로 변수가 섞인 요청은 받지 않는다
        if (path.contains("%") || path.contains("..") || path.contains("\\") || path.contains(";")
                || path.contains("//")) {
            notFound(response);
            return;
        }
        String page = path.endsWith("/") ? path + "index.html" : path;
        request.getRequestDispatcher(INTERNAL_PREFIX + page).forward(request, response);
    }

    // "/api" 는 "/api", "/api/..." 와 맞고 "/apix" 와는 맞지 않는다
    private static boolean startsWithSegment(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static void notFound(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"NOT_FOUND\",\"message\":\"요청한 경로를 찾을 수 없습니다.\",\"fields\":[]}");
    }
}
