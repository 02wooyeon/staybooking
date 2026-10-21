package com.kisia.staybooking.user;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면과 권한 API를 바로 확인하기 위한 로컬 전용 계정이다.
 * mysql 프로필과 운영·배포 환경에서는 이 설정 자체가 로드되지 않는다.
 */
@Configuration
@Profile("local")
class LocalTestDataInitializer {
    private static final String PASSWORD = "Test!2345";

    @Bean
    ApplicationRunner createLocalTestUsers(UserRepository users, PasswordEncoder passwordEncoder) {
        return args -> createUsers(users, passwordEncoder);
    }

    @Transactional
    void createUsers(UserRepository users, PasswordEncoder passwordEncoder) {
        createIfAbsent(users, passwordEncoder, "user@staybooking.local", "테스트 사용자",
            "010-0000-0001", UserRole.ROLE_USER);
        createIfAbsent(users, passwordEncoder, "approver@staybooking.local", "테스트 승인자",
            "010-0000-0002", UserRole.ROLE_APPROVER);
        createIfAbsent(users, passwordEncoder, "admin@staybooking.local", "테스트 관리자",
            "010-0000-0003", UserRole.ROLE_ADMIN);
    }

    private static void createIfAbsent(UserRepository users, PasswordEncoder passwordEncoder,
                                       String email, String name, String phone, UserRole role) {
        if (users.existsByEmail(email)) {
            return;
        }
        users.save(new UserEntity(email, passwordEncoder.encode(PASSWORD), name, null, email, phone, role));
    }
}
