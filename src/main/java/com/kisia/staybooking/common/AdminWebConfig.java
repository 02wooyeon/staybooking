package com.kisia.staybooking.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 관리자 화면 파일(classpath:/admin-static/)을 내부 경로 /__admin/** 로 내보낸다.
 * 이 경로는 AdminHostRoutingFilter 가 관리자 도메인 요청을 forward 할 때만 쓰고,
 * 브라우저가 직접 요청하면 필터가 404로 막는다.
 * 일반 화면(classpath:/static/)은 Spring Boot 기본 설정이 그대로 내보낸다.
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(AdminHostRoutingFilter.INTERNAL_PREFIX + "/**")
            .addResourceLocations("classpath:/admin-static/");
    }
}
