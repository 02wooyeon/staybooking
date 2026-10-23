package com.kisia.staybooking.user;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.kisia.staybooking.auth.PasswordRules;
import com.kisia.staybooking.auth.RefreshTokenRepository;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final BookingRepository bookings;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens, BookingRepository bookings,
                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.bookings = bookings;
        this.passwordEncoder = passwordEncoder;
    }

    // PROFILE_001. 토큰의 회원번호로만 조회하므로 다른 회원 정보는 볼 수 없다.
    // 탈퇴·정지된 계정은 토큰이 남아 있어도 로그인하지 않은 것으로 취급한다.
    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        return response(activeUser(userId));
    }

    // PROFILE_002. 비밀번호를 바꾸려면 현재 비밀번호를 다시 확인한다.
    // 비밀번호를 바꾸면 모든 기기의 로그인이 풀리므로 화면은 다시 로그인하게 해야 한다.
    @Transactional
    public MeResponse updateMe(Long userId, String nickname, String phone, String currentPassword,
                               String newPassword) {
        UserEntity user = users.findByIdForUpdate(userId)
            .filter(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(UserService::unauthorized);
        user.updateProfile(blankToNull(nickname), blankToNull(phone));
        if (newPassword != null && !newPassword.isEmpty()) {
            requirePassword(user, currentPassword, "currentPassword");
            List<String> invalid = new ArrayList<>();
            if (PasswordRules.containsEmailId(user.getEmail(), newPassword)
                    || passwordEncoder.matches(newPassword, user.getPasswordHash())) {
                invalid.add("newPassword");
            }
            if (!invalid.isEmpty()) throw invalidInput(invalid);
            user.changePassword(passwordEncoder.encode(newPassword));
            refreshTokens.revokeAllByUserId(userId, LocalDateTime.now(ZoneOffset.UTC));
        }
        return response(user);
    }

    // PROFILE_003. 진행 중인 예약이 있으면 탈퇴할 수 없다. 관리자 계정은 화면에서 탈퇴하지 않는다.
    @Transactional
    public void withdraw(Long userId, String password) {
        UserEntity user = users.findByIdForUpdate(userId)
            .filter(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(UserService::unauthorized);
        requirePassword(user, password, "password");
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            throw new ApiException(HttpStatus.CONFLICT, "WITHDRAW_NOT_ALLOWED", "관리자 계정은 탈퇴할 수 없습니다.");
        }
        if (hasOngoingBooking(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "WITHDRAW_NOT_ALLOWED",
                "진행 중인 예약이 있어 탈퇴할 수 없습니다. 예약을 취소하거나 이용 후 다시 시도해 주세요.");
        }
        // 아무도 알 수 없는 값으로 비밀번호를 덮어써 다시 로그인할 수 없게 한다.
        user.withdraw(passwordEncoder.encode(UUID.randomUUID().toString()));
        refreshTokens.revokeAllByUserId(userId, LocalDateTime.now(ZoneOffset.UTC));
    }

    private boolean hasOngoingBooking(Long userId) {
        LocalDate today = LocalDate.now(KOREA);
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        for (BookingEntity booking : bookings.findByUserUserIdOrderByCreatedAtDesc(userId)) {
            BookingStatus status = booking.getStatus();
            boolean unpaidButAlive = status == BookingStatus.PAYMENT_PENDING
                && booking.getPaymentExpiresAt().isAfter(nowUtc);
            boolean upcoming = (status == BookingStatus.PENDING || status == BookingStatus.COMPLETED)
                && !booking.getCheckOutDate().isBefore(today);
            if (unpaidButAlive || upcoming) return true;
        }
        return false;
    }

    private void requirePassword(UserEntity user, String raw, String field) {
        if (raw == null || raw.isEmpty() || !passwordEncoder.matches(raw, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "비밀번호가 올바르지 않습니다.",
                List.of(field));
        }
    }

    private UserEntity activeUser(Long userId) {
        return users.findById(userId)
            .filter(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(UserService::unauthorized);
    }

    private static MeResponse response(UserEntity user) {
        LocalDate joined = user.getCreatedAt() == null ? null
            : user.getCreatedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA).toLocalDate();
        return new MeResponse(user.getUserId(), user.getEmail(), user.getName(), user.getNickname(),
            user.getPhone(), user.getRole(), user.getMileageBalance(), joined);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }

    private static ApiException invalidInput(List<String> fields) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다.", fields);
    }
}
