package com.kisia.staybooking.user;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.auth.RefreshTokenRepository;
import com.kisia.staybooking.common.PageResponse;
import com.kisia.staybooking.mileage.MileageHistoryResponse;
import com.kisia.staybooking.mileage.MileageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AdminMemberQueryService queries;
    private final MileageService mileage;

    public AdminMemberController(UserRepository users, RefreshTokenRepository refreshTokens,
                                 AdminMemberQueryService queries, MileageService mileage) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.queries = queries;
        this.mileage = mileage;
    }

    @GetMapping
    public List<MemberSummary> list() {
        return users.findAll().stream().map(MemberSummary::from).toList();
    }

    @GetMapping("/search")
    public PageResponse<MemberSummary> search(@Valid @ModelAttribute MemberSearchRequest request) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        String q = request.q() == null || request.q().isBlank() ? null : request.q().trim();
        var result = users.searchForAdmin(request.userId(), request.role(), request.status(), q,
            PageRequest.of(page, size));
        return new PageResponse<>(result.getContent().stream().map(MemberSummary::from).toList(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    // 회원 한 명의 예약·결제·활동 집계. 목록 요약(MemberSummary)에 없는 운영 판단 근거를 제공한다.
    @GetMapping("/{userId}")
    public AdminMemberDetail detail(@PathVariable @Positive Long userId) {
        return queries.detail(userId);
    }

    @GetMapping("/{userId}/mileage")
    public PageResponse<MileageHistoryResponse> mileageHistory(@PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mileage.historyOf(userId, page, size);
    }

    // 보상·회수 같은 운영 처리. 양수는 적립, 음수는 차감으로 기록한다.
    @PostMapping("/{userId}/mileage")
    public MileageHistoryResponse adjustMileage(@PathVariable @Positive Long userId,
                                                @Valid @RequestBody MileageAdjustRequest request) {
        return mileage.adjust(userId, request.amount(), request.description().trim());
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

    public record MileageAdjustRequest(@NotNull Long amount,
                                       @NotBlank @Size(max = 255) String description) { }
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
