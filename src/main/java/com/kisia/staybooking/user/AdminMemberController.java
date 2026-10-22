package com.kisia.staybooking.user;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.auth.RefreshTokenRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;

    public AdminMemberController(UserRepository users, RefreshTokenRepository refreshTokens) {
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    @GetMapping
    public List<MemberSummary> list() {
        return users.findAll().stream().map(MemberSummary::from).toList();
    }

    @Transactional
    @PatchMapping("/{userId}/status")
    public MemberSummary changeStatus(@AuthenticationPrincipal AuthenticatedUser me,
                                      @PathVariable Long userId, @Valid @RequestBody StatusRequest request) {
        if (me.userId().equals(userId) && request.status() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Administrator cannot suspend own account");
        }
        UserEntity user = users.findByIdForUpdate(userId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        user.changeStatus(request.status());
        if (request.status() != UserStatus.ACTIVE) revokeTokens(userId);
        return MemberSummary.from(user);
    }

    @Transactional
    @PatchMapping("/{userId}/role")
    public MemberSummary changeRole(@AuthenticationPrincipal AuthenticatedUser me,
                                    @PathVariable Long userId, @Valid @RequestBody RoleRequest request) {
        if (me.userId().equals(userId)) {
            throw new IllegalStateException("Administrator cannot change own role");
        }
        UserEntity user = users.findByIdForUpdate(userId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        UserRole previous = user.getRole();
        user.changeAssignableRole(request.role());
        if (previous != user.getRole()) revokeTokens(userId);
        return MemberSummary.from(user);
    }

    private void revokeTokens(Long userId) {
        refreshTokens.revokeAllByUserId(userId, LocalDateTime.now(ZoneOffset.UTC));
    }

    public record StatusRequest(@NotNull UserStatus status) { }
    public record RoleRequest(@NotNull UserRole role) { }
    public record MemberSummary(Long userId, String loginId, String name, String nickname,
                                String email, UserRole role, UserStatus status, long mileageBalance) {
        static MemberSummary from(UserEntity user) {
            return new MemberSummary(user.getUserId(), user.getLoginId(), user.getName(),
                user.getNickname(), user.getEmail(), user.getRole(), user.getStatus(),
                user.getMileageBalance());
        }
    }
}
